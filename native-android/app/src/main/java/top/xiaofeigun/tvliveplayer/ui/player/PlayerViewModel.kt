package top.xiaofeigun.tvliveplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.xiaofeigun.tvliveplayer.data.preferences.AppPreferences
import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import top.xiaofeigun.tvliveplayer.domain.usecase.PrepareChannelDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Page {
    PLAYER,
    CATEGORY,
    ICON,
    SETTINGS,
    SOURCE_MGMT,
    UPDATE;

    /** Level 1 浮层（ICON / CATEGORY），二者互斥 */
    val isLevel1: Boolean get() = this == CATEGORY || this == ICON

    /** 以独立 Fragment 呈现的页面 */
    val isFragmentPage: Boolean get() = this == SETTINGS || this == SOURCE_MGMT || this == UPDATE
}

data class PlayerUiState(
    val channels: List<Channel> = emptyList(),
    val allCategories: List<String> = emptyList(),
    val currentChannel: Channel? = null,
    val currentCategory: String = "",
    val navStack: List<Page> = listOf(Page.PLAYER),
    /** 抽屉当前分类，与 [categoryChannels] 总是成对更新 */
    val selectedCategory: String = "",
    val categoryChannels: List<Channel> = emptyList(),
    val selectedChannelIndex: Int = 0,
    val showExitConfirm: Boolean = false,
    val channelsLoaded: Boolean = false
) {
    val topPage: Page get() = navStack.last()
    val isFavorite: Boolean get() = currentChannel?.isFavorite == true
    val selectedCategoryIndex: Int get() = allCategories.indexOf(selectedCategory).coerceAtLeast(0)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: ChannelRepository,
    private val appPreferences: AppPreferences,
    private val prepareChannelData: PrepareChannelDataUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** 抽屉请求展示的分类；其频道列表由 Room Flow 驱动，增删改后自动刷新 */
    private val requestedCategory = MutableStateFlow<String?>(null)

    /** 打开抽屉时需要定位的频道，待对应分类的频道列表到达后消费 */
    private var pendingFocusChannelId: String? = null

    init {
        observeChannels()
        observeCategoryChannels()
    }

    private fun observeChannels() {
        viewModelScope.launch {
            prepareChannelData()
            repository.getAllChannels().collect { list ->
                onChannelsChanged(list, ChannelRepository.categoriesOf(list))
            }
        }
    }

    private fun onChannelsChanged(list: List<Channel>, cats: List<String>) {
        val s = _state.value
        // 刷新当前频道实例（收藏状态等可能已变化）；当前频道被删除时回退到首个频道
        val current = s.currentChannel?.let { cur -> list.find { it.id == cur.id } }
        val target = if (!s.channelsLoaded) {
            appPreferences.getLastChannelId()?.let { id -> list.find { it.id == id } } ?: list.firstOrNull()
        } else {
            current ?: list.firstOrNull()
        }
        val category = when {
            target == null -> ""
            current == null -> target.category
            s.currentCategory == ChannelRepository.FAVORITE_CATEGORY && !target.isFavorite -> target.category
            else -> s.currentCategory.ifBlank { target.category }
        }

        _state.update {
            it.copy(
                channels = list,
                allCategories = cats,
                currentChannel = target,
                currentCategory = category,
                channelsLoaded = true
            )
        }

        // 抽屉分类被清空/合并后消失时，就近选择相邻分类
        val requested = requestedCategory.value
        if (requested != null && requested !in cats) {
            requestedCategory.value = cats.getOrNull(s.selectedCategoryIndex.coerceAtMost(cats.size - 1))
        }

        if (target != null && target.id != s.currentChannel?.id) {
            appPreferences.setLastChannelId(target.id)
        }
    }

    private fun observeCategoryChannels() {
        viewModelScope.launch {
            requestedCategory
                .flatMapLatest { cat ->
                    if (cat == null) emptyFlow() else repository.getChannelsByCategory(cat).map { cat to it }
                }
                .collect { (cat, channels) ->
                    val focusId = pendingFocusChannelId
                    pendingFocusChannelId = null
                    _state.update { st ->
                        val focusIdx = focusId?.let { id -> channels.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
                        val idx = focusIdx ?: if (cat == st.selectedCategory) st.selectedChannelIndex else 0
                        st.copy(
                            selectedCategory = cat,
                            categoryChannels = channels,
                            selectedChannelIndex = idx.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
                        )
                    }
                }
        }
    }

    private fun playChannel(channel: Channel, category: String) {
        _state.update { it.copy(currentChannel = channel, currentCategory = category) }
        appPreferences.setLastChannelId(channel.id)
    }

    fun nextChannel() = switchChannel(1)

    fun previousChannel() = switchChannel(-1)

    private fun switchChannel(delta: Int) {
        val s = _state.value
        val list = s.channels
        if (list.isEmpty()) return
        val curIdx = list.indexOfFirst { it.id == s.currentChannel?.id }.coerceAtLeast(0)
        val ch = list[(curIdx + delta).mod(list.size)]
        playChannel(ch, ch.category)
    }

    // ---------------- 选台抽屉 ----------------

    fun enterCategorySelect() {
        val s = _state.value
        val cat = s.currentCategory.takeIf { it in s.allCategories }
            ?: s.currentChannel?.category?.takeIf { it in s.allCategories }
            ?: s.allCategories.firstOrNull()
            ?: return
        val currentId = s.currentChannel?.id
        if (cat == s.selectedCategory && cat == requestedCategory.value) {
            // 分类未变，列表已就绪，直接定位
            val idx = s.categoryChannels.indexOfFirst { it.id == currentId }
            _state.update { it.copy(selectedChannelIndex = if (idx >= 0) idx else it.selectedChannelIndex) }
        } else {
            pendingFocusChannelId = currentId
            requestedCategory.value = cat
        }
        pushPage(Page.CATEGORY)
    }

    fun selectCategory(index: Int) {
        val cat = _state.value.allCategories.getOrNull(index) ?: return
        requestedCategory.value = cat
    }

    fun focusCategoryChannel(index: Int) {
        if (index !in _state.value.categoryChannels.indices) return
        _state.update { it.copy(selectedChannelIndex = index) }
    }

    fun confirmCategorySelect() {
        val s = _state.value
        s.categoryChannels.getOrNull(s.selectedChannelIndex)?.let { playChannel(it, s.selectedCategory) }
        _state.update { it.copy(navStack = listOf(Page.PLAYER)) }
    }

    // ---------------- 导航栈 ----------------

    fun pushPage(page: Page) {
        _state.update { it.copy(navStack = pushed(it.navStack, page)) }
    }

    fun popPage() {
        _state.update { if (it.navStack.size > 1) it.copy(navStack = it.navStack.dropLast(1)) else it }
    }

    /**
     * 统一返回逻辑（遥控器返回 / Esc / 手势返回 / 触屏返回按钮）。
     * @return true 表示应退出应用
     */
    fun onBack(): Boolean {
        val s = _state.value
        if (s.navStack.size > 1) {
            popPage()
            return false
        }
        if (s.showExitConfirm) return true
        _state.update { it.copy(showExitConfirm = true) }
        return false
    }

    fun dismissExitConfirm() {
        _state.update { it.copy(showExitConfirm = false) }
    }

    // ---------------- 收藏与频道管理 ----------------

    fun toggleFavorite(channelId: String? = _state.value.currentChannel?.id) {
        channelId ?: return
        viewModelScope.launch { repository.toggleFavorite(channelId) }
    }

    /** 频道操作一律按 id，菜单弹出期间列表变化也不会作用到其它频道 */
    fun deleteChannel(channelId: String) {
        viewModelScope.launch { repository.deleteChannel(channelId) }
    }

    fun moveChannel(channelId: String, targetCategory: String) {
        viewModelScope.launch { repository.moveChannel(channelId, targetCategory) }
    }

    fun mergeCategory(from: String, to: String) {
        viewModelScope.launch { repository.mergeCategory(from, to) }
    }

    fun deleteCategory(category: String) {
        viewModelScope.launch { repository.deleteCategory(category) }
    }

    companion object {
        /**
         * 入栈规则：已在栈中的页面回退到该页（不重复叠加）；
         * Level 1 页面（ICON / CATEGORY）互斥，始终位于栈的第 2 层。
         */
        internal fun pushed(stack: List<Page>, page: Page): List<Page> {
            val idx = stack.indexOf(page)
            return when {
                idx >= 0 -> stack.subList(0, idx + 1).toList()
                page.isLevel1 -> listOf(Page.PLAYER, page)
                else -> stack + page
            }
        }
    }
}
