package com.wangyg.tvliveplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wangyg.tvliveplayer.data.preferences.AppPreferences
import com.wangyg.tvliveplayer.domain.model.Channel
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Screen {
    PLAYER,
    CATEGORY_SELECT,
    ICON_SELECT,
    MENU,
    SETTINGS,
    CHANNEL_EDIT,
    ADD_SOURCE,
    UPDATE
}

data class PlayerUiState(
    val channels: List<Channel> = emptyList(),
    val allCategories: List<String> = emptyList(),
    val currentChannel: Channel? = null,
    val currentIndex: Int = 0,
    val isFavorite: Boolean = false,
    val screenStack: List<Screen> = listOf(Screen.PLAYER),
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
                    val isFav = if (currentId != null) repository.isFavorite(currentId) else false
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
    }

    fun resetToPlayer() {
        _state.update {
            it.copy(
                screenStack = listOf(Screen.PLAYER),
                videoPaused = false,
                enteredByOk = false
            )
        }
    }

    fun cancelCategorySelect() {
        _state.update { it.copy(screenStack = listOf(Screen.PLAYER)) }
    }

    fun enterCategorySelect() {
        val s = _state.value
        val curCat = s.currentChannel?.category ?: s.allCategories.firstOrNull() ?: return
        val catIdx = s.allCategories.indexOf(curCat).coerceAtLeast(0)
        viewModelScope.launch {
            val channels = repository.getChannelsByCategory(curCat).first()
            val chIdx = channels.indexOfFirst { it.id == s.currentChannel?.id }.coerceAtLeast(0)
            _state.update {
                it.copy(
                    screenStack = listOf(Screen.CATEGORY_SELECT),
                    selectedCategoryIndex = catIdx,
                    categoryChannels = channels,
                    selectedChannelIndex = chIdx
                )
            }
        }
    }

    fun openMenu(fromOk: Boolean) {
        if (fromOk) {
            _state.update { it.copy(videoPaused = true, enteredByOk = true) }
        }
        _state.update { it.copy(screenStack = _state.value.screenStack + Screen.MENU) }
    }

    fun closeMenu() {
        val s = _state.value
        _state.update {
            it.copy(
                screenStack = listOf(Screen.PLAYER),
                videoPaused = false,
                enteredByOk = false
            )
        }
    }

    fun navigateTo(screen: Screen) {
        val stack = _state.value.screenStack
        if (screen == Screen.SETTINGS || screen == Screen.CHANNEL_EDIT) {
            _state.update { it.copy(videoPaused = true) }
        }
        _state.update { it.copy(screenStack = stack + screen) }
    }

    fun navigateBack(): Boolean {
        val stack = _state.value.screenStack.toMutableList()
        if (stack.size <= 1) {
            if (stack.last() == Screen.PLAYER) {
                _state.update { it.copy(showExitConfirm = true) }
                return false
            }
            return false
        }
        val removed = stack.removeLast()
        val newScreen = stack.last()
        val isPaused = _state.value.videoPaused
        if (newScreen == Screen.PLAYER && _state.value.enteredByOk) {
            _state.update {
                it.copy(
                    screenStack = stack,
                    videoPaused = false,
                    enteredByOk = false
                )
            }
        } else {
            _state.update {
                it.copy(
                    screenStack = stack,
                    videoPaused = if (newScreen == Screen.PLAYER) false else isPaused
                )
            }
        }
        return true
    }

    fun dismissExitConfirm() {
        _state.update { it.copy(showExitConfirm = false) }
    }

    fun toggleFavorite() {
        val channel = _state.value.currentChannel ?: return
        viewModelScope.launch {
            val isFav = repository.toggleFavorite(channel.id)
            _state.update { it.copy(isFavorite = isFav) }
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
