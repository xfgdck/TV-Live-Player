package top.xiaofeigun.tvliveplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.xiaofeigun.tvliveplayer.data.preferences.AppPreferences
import top.xiaofeigun.tvliveplayer.domain.model.Channel
import top.xiaofeigun.tvliveplayer.domain.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Page {
    PLAYER,
    CATEGORY,
    ICON,
    SETTINGS,
    SOURCE_MGMT,
    UPDATE
}

data class PlayerUiState(
    val channels: List<Channel> = emptyList(),
    val allCategories: List<String> = emptyList(),
    val currentChannel: Channel? = null,
    val currentIndex: Int = 0,
    val isFavorite: Boolean = false,
    val navStack: List<Page> = listOf(Page.PLAYER),
    val categoryChannels: List<Channel> = emptyList(),
    val selectedCategoryIndex: Int = 0,
    val selectedChannelIndex: Int = 0,
    val showOsd: Boolean = false,
    val osdText: String = "",
    val osdCategory: String = "",
    val showExitConfirm: Boolean = false,
    val videoPaused: Boolean = false,
    val enteredByOk: Boolean = false,
    val channelsLoaded: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: ChannelRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        loadChannels()
    }

    private fun loadChannels() {
        viewModelScope.launch {
            repository.getAllChannels().collect { list ->
                val stateVal = _state.value
                val cats = repository.getAllCategoriesWithFavorite().first()

                if (!stateVal.channelsLoaded) {
                    val lastId = appPreferences.getLastChannelId()
                    val targetChannel = if (lastId != null) {
                        list.find { it.id == lastId } ?: list.firstOrNull()
                    } else {
                        list.firstOrNull()
                    }
                    val idx = if (targetChannel != null) list.indexOf(targetChannel) else 0
                    val isFav = if (targetChannel != null) repository.isFavorite(targetChannel.id) else false

                    _state.update {
                        it.copy(
                            channels = list,
                            allCategories = cats,
                            currentChannel = targetChannel,
                            currentIndex = idx,
                            isFavorite = isFav,
                            channelsLoaded = true
                        )
                    }
                    if (targetChannel != null) {
                        appPreferences.setLastChannelId(targetChannel.id)
                    }
                } else {
                    val currentId = stateVal.currentChannel?.id
                    val channelStillExists = currentId != null && list.any { it.id == currentId }

                    if (list.isEmpty()) {
                        _state.update {
                            it.copy(
                                channels = list,
                                allCategories = cats,
                                currentChannel = null,
                                currentIndex = 0,
                                isFavorite = false
                            )
                        }
                    } else if (!channelStillExists) {
                        val newChannel = list.first()
                        val newIdx = 0
                        val newIsFav = repository.isFavorite(newChannel.id)
                        _state.update {
                            it.copy(
                                channels = list,
                                allCategories = cats,
                                currentChannel = newChannel,
                                currentIndex = newIdx,
                                isFavorite = newIsFav,
                                channelsLoaded = true
                            )
                        }
                        saveLastChannel(newChannel)
                    } else {
                        val isFav = repository.isFavorite(currentId!!)
                        _state.update {
                            it.copy(
                                channels = list,
                                allCategories = cats,
                                isFavorite = isFav
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveLastChannel(channel: Channel) {
        appPreferences.setLastChannelId(channel.id)
    }

    fun playChannel(channel: Channel) {
        val idx = _state.value.channels.indexOfFirst { it.id == channel.id }
        if (idx >= 0) {
            _state.update {
                it.copy(
                    currentChannel = channel,
                    currentIndex = idx,
                    isFavorite = channel.isFavorite
                )
            }
        } else {
            _state.update { it.copy(currentChannel = channel, isFavorite = channel.isFavorite) }
        }
        saveLastChannel(channel)
    }

    fun nextChannel() {
        val s = _state.value
        val list = s.channels
        if (list.isEmpty()) return
        val newIdx = (s.currentIndex + 1) % list.size
        val ch = list[newIdx]
        viewModelScope.launch {
            val isFav = repository.isFavorite(ch.id)
            _state.update {
                it.copy(
                    currentChannel = ch,
                    currentIndex = newIdx,
                    isFavorite = isFav
                )
            }
            saveLastChannel(ch)
        }
    }

    fun previousChannel() {
        val s = _state.value
        val list = s.channels
        if (list.isEmpty()) return
        val newIdx = (s.currentIndex - 1 + list.size) % list.size
        val ch = list[newIdx]
        viewModelScope.launch {
            val isFav = repository.isFavorite(ch.id)
            _state.update {
                it.copy(
                    currentChannel = ch,
                    currentIndex = newIdx,
                    isFavorite = isFav
                )
            }
            saveLastChannel(ch)
        }
    }

    fun selectCategory(index: Int) {
        val cats = _state.value.allCategories
        if (index < 0 || index >= cats.size) return
        val cat = cats[index]
        viewModelScope.launch {
            val channels = repository.getChannelsByCategory(cat).first()
            _state.update {
                it.copy(
                    selectedCategoryIndex = index,
                    categoryChannels = channels,
                    selectedChannelIndex = 0
                )
            }
        }
    }

    fun focusCategoryChannel(index: Int) {
        val chs = _state.value.categoryChannels
        if (index < 0 || index >= chs.size) return
        _state.update { it.copy(selectedChannelIndex = index) }
    }

    fun confirmCategorySelect() {
        val chs = _state.value.categoryChannels
        val idx = _state.value.selectedChannelIndex
        if (chs.isNotEmpty() && idx < chs.size) {
            playChannel(chs[idx])
        }
        _state.update { it.copy(navStack = ensureSingleLevel1Path(listOf(Page.PLAYER))) }
    }

    fun resetToPlayer() {
        _state.update {
            it.copy(
                navStack = ensureSingleLevel1Path(listOf(Page.PLAYER)),
                videoPaused = false,
                enteredByOk = false
            )
        }
    }

    fun cancelCategorySelect() {
        _state.update { it.copy(navStack = ensureSingleLevel1Path(listOf(Page.PLAYER))) }
    }

    fun enterCategorySelect() {
        val s = _state.value
        val curCat = s.currentChannel?.category ?: s.allCategories.firstOrNull() ?: return
        val catIdx = s.allCategories.indexOf(curCat).coerceAtLeast(0)
        viewModelScope.launch {
            val channels = repository.getChannelsByCategory(curCat).first()
            val chIdx = channels.indexOfFirst { it.id == s.currentChannel?.id }.coerceAtLeast(0)
            _state.update {
                val cur = it.navStack
                val newStack = if (cur.size >= 2 && cur[1] in listOf(Page.ICON, Page.CATEGORY)) {
                    cur.toMutableList().apply { this[1] = Page.CATEGORY }
                } else {
                    listOf(Page.PLAYER, Page.CATEGORY)
                }
                it.copy(
                    navStack = ensureSingleLevel1Path(newStack),
                    selectedCategoryIndex = catIdx,
                    categoryChannels = channels,
                    selectedChannelIndex = chIdx
                )
            }
        }
    }

    /** Ensure Level 1 pages (ICON/CATEGORY) are mutually exclusive — only one at position 1 */
    private fun ensureSingleLevel1Path(stack: List<Page>): List<Page> {
        val l1Indices = stack.mapIndexedNotNull { i, p -> if (p == Page.ICON || p == Page.CATEGORY) i else null }
        if (l1Indices.size <= 1) return stack
        // Keep the last Level 1 entry, remove the rest
        val keep = l1Indices.last()
        return stack.filterIndexed { i, _ -> i == keep || (i !in l1Indices) }
    }

    fun pushPage(page: Page) {
        _state.update { s ->
            val stack = s.navStack.toMutableList()
            if (page == Page.ICON || page == Page.CATEGORY) {
                if (stack.size >= 2 && stack[1] in listOf(Page.ICON, Page.CATEGORY)) {
                    stack[1] = page
                } else if (stack.size == 1) {
                    stack.add(page)
                }
            } else {
                stack.add(page)
            }
            s.copy(navStack = ensureSingleLevel1Path(stack))
        }
    }

    fun popPage(): Boolean {
        val s = _state.value
        if (s.navStack.size <= 1) {
            if (s.navStack.last() == Page.PLAYER) {
                _state.update { it.copy(showExitConfirm = true) }
            }
            return false
        }
        _state.update { it.copy(navStack = ensureSingleLevel1Path(it.navStack.dropLast(1))) }
        return true
    }

    fun dismissExitConfirm() {
        _state.update { it.copy(showExitConfirm = false) }
    }

    fun toggleFavorite() {
        val channel = _state.value.currentChannel ?: return
        toggleFavorite(channel.id)
    }

    fun toggleFavorite(channelId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(channelId)
            val state = _state.value
            val isFav = state.currentChannel?.let { repository.isFavorite(it.id) } ?: false
            val cat = state.allCategories.getOrNull(state.selectedCategoryIndex)
            val updatedChannels = if (cat != null) repository.getChannelsByCategory(cat).first() else state.categoryChannels
            _state.update {
                it.copy(categoryChannels = updatedChannels, isFavorite = isFav)
            }
        }
    }

    fun deleteCategoryChannel(index: Int) {
        val chs = _state.value.categoryChannels
        if (index < 0 || index >= chs.size) return
        viewModelScope.launch {
            repository.deleteChannel(chs[index])
            val cat = _state.value.allCategories.getOrNull(_state.value.selectedCategoryIndex)
            if (cat != null) {
                val updated = repository.getChannelsByCategory(cat).first()
                val newIdx = if (index >= updated.size) updated.size - 1 else index
                _state.update { it.copy(categoryChannels = updated, selectedChannelIndex = newIdx.coerceAtLeast(0)) }
            }
            refreshAll()
        }
    }

    fun moveCategoryChannel(index: Int, targetCategory: String) {
        val chs = _state.value.categoryChannels
        if (index < 0 || index >= chs.size) return
        viewModelScope.launch {
            repository.updateChannel(chs[index].copy(category = targetCategory))
            val cat = _state.value.allCategories.getOrNull(_state.value.selectedCategoryIndex)
            if (cat != null) {
                val updated = repository.getChannelsByCategory(cat).first()
                val newIdx = if (index >= updated.size) updated.size - 1 else index
                _state.update { it.copy(categoryChannels = updated, selectedChannelIndex = newIdx.coerceAtLeast(0)) }
            }
            refreshAll()
        }
    }

    fun deleteCategory(category: String) {
        viewModelScope.launch {
            val channels = repository.getChannelsByCategory(category).first()
            channels.forEach { repository.deleteChannel(it) }
            val updatedCats = repository.getAllCategoriesWithFavorite().first()
            val oldIndex = _state.value.selectedCategoryIndex
            val newCatIndex = if (updatedCats.isEmpty()) 0 else oldIndex.coerceAtMost(updatedCats.size - 1)
            val newCat = updatedCats.getOrNull(newCatIndex)
            val newChannels = if (newCat != null) repository.getChannelsByCategory(newCat).first() else emptyList()
            _state.update {
                it.copy(
                    allCategories = updatedCats,
                    selectedCategoryIndex = newCatIndex,
                    categoryChannels = newChannels,
                    selectedChannelIndex = 0
                )
            }
            refreshAll()
        }
    }

    fun refreshCategoryChannels(category: String) {
        viewModelScope.launch {
            val channels = repository.getChannelsByCategory(category).first()
            _state.update { it.copy(categoryChannels = channels) }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            val cats = repository.getAllCategoriesWithFavorite().first()
            _state.update { it.copy(allCategories = cats) }
        }
    }
}
