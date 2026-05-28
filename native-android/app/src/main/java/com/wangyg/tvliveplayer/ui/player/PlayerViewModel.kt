package com.wangyg.tvliveplayer.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wangyg.tvliveplayer.domain.model.Channel
import com.wangyg.tvliveplayer.domain.repository.ChannelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: ChannelRepository
) : ViewModel() {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _currentCategory = MutableStateFlow("")
    val currentCategory: StateFlow<String> = _currentCategory.asStateFlow()

    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel.asStateFlow()

    private val _showOsd = MutableStateFlow(false)
    val showOsd: StateFlow<Boolean> = _showOsd.asStateFlow()

    private var currentIndex = 0

    init {
        viewModelScope.launch {
            repository.getAllChannels().collect { list ->
                _channels.value = list
                val cats = list.map { it.category }.distinct()
                _categories.value = cats
                if (_currentChannel.value == null && list.isNotEmpty()) {
                    currentIndex = 0
                    _currentChannel.value = list[0]
                }
            }
        }
    }

    fun playChannel(channel: Channel) {
        val idx = _channels.value.indexOfFirst { it.id == channel.id }
        if (idx >= 0) currentIndex = idx
        _currentChannel.value = channel
    }

    fun playChannelByName(name: String) {
        val channel = _channels.value.find { it.name == name }
        channel?.let { playChannel(it) }
    }

    fun nextChannel() {
        val list = _channels.value
        if (list.isEmpty()) return
        currentIndex = (currentIndex + 1) % list.size
        _currentChannel.value = list[currentIndex]
    }

    fun previousChannel() {
        val list = _channels.value
        if (list.isEmpty()) return
        currentIndex = (currentIndex - 1 + list.size) % list.size
        _currentChannel.value = list[currentIndex]
    }

    fun nextCategory() {
        val list = _channels.value
        val cats = _categories.value
        if (list.isEmpty() || cats.isEmpty()) return
        val curCat = _currentChannel.value?.category ?: cats[0]
        val catIdx = cats.indexOf(curCat)
        if (catIdx < 0) return
        val nextCat = cats[(catIdx + 1) % cats.size]
        val firstInCat = list.indexOfFirst { it.category == nextCat }
        if (firstInCat >= 0) {
            currentIndex = firstInCat
            _currentChannel.value = list[currentIndex]
        }
    }

    fun previousCategory() {
        val list = _channels.value
        val cats = _categories.value
        if (list.isEmpty() || cats.isEmpty()) return
        val curCat = _currentChannel.value?.category ?: cats[0]
        val catIdx = cats.indexOf(curCat)
        if (catIdx < 0) return
        val prevCat = cats[(catIdx - 1 + cats.size) % cats.size]
        val firstInCat = list.indexOfFirst { it.category == prevCat }
        if (firstInCat >= 0) {
            currentIndex = firstInCat
            _currentChannel.value = list[currentIndex]
        }
    }

    fun toggleOsd() {
        _showOsd.value = !_showOsd.value
    }

    fun hideOsd() {
        _showOsd.value = false
    }
}
