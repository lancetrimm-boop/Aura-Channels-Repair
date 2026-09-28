package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChannelPreviewState(
    val channel: Channel,
    val candidateItem: MediaItem?,
    val fullItems: List<MediaItem>
)

class ChannelViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val programmer = ChannelProgrammer()
    private val sessionManager = ChannelSessionManager(programmer)
    val channelState: StateFlow<ChannelState> = sessionManager.channelState

    val defaultChannels: List<Channel> get() = ChannelRegistry.allFixedChannels()

    val allChannels: List<Channel> get() = ChannelRegistry.allChannels()

    private val _selectedChannel = MutableStateFlow<Channel>(ChannelRegistry.defaultChannel())
    val selectedChannel: StateFlow<Channel> = _selectedChannel.asStateFlow()

    private val _selectedFilterType = MutableStateFlow<String>("VIDEOS")
    val selectedFilterType: StateFlow<String> = _selectedFilterType.asStateFlow()

    private val _channelPreviews = MutableStateFlow<List<ChannelPreviewState>>(emptyList())
    val channelPreviews: StateFlow<List<ChannelPreviewState>> = _channelPreviews.asStateFlow()

    val slideshowDelaySeconds: StateFlow<Int> = repository.slideshowDelaySeconds

    init {
        // Observe media items changes and refresh channel previews
        viewModelScope.launch {
            repository.mediaItems.collect {
                loadChannelPreviews()
            }
        }
        selectChannel(ChannelRegistry.defaultChannel())
    }

    fun loadChannelPreviews() {
        viewModelScope.launch(Dispatchers.Default) {
            val available = repository.mediaItems.value
            val filter = _selectedFilterType.value
            val context = ChannelProgrammingContext(
                availableMedia = available,
                filterType = filter,
                tasteDNA = repository.tasteDNA.value
            )

            val usedPreviewIds = mutableSetOf<String>()

            val states = allChannels.map { channel ->
                val programmed = programmer.programChannel(channel, context, limit = 20)
                val candidate = programmed.firstOrNull { it.id !in usedPreviewIds } ?: programmed.firstOrNull()
                if (candidate != null) {
                    usedPreviewIds.add(candidate.id)
                }

                val adjustedFullItems = if (candidate != null && programmed.contains(candidate)) {
                    listOf(candidate) + programmed.filter { it.id != candidate.id }
                } else programmed

                ChannelPreviewState(
                    channel = channel,
                    candidateItem = candidate,
                    fullItems = adjustedFullItems
                )
            }
            _channelPreviews.value = states
        }
    }

    fun selectChannel(channel: Channel) {
        _selectedChannel.value = channel
        viewModelScope.launch {
            sessionManager.selectChannel(channel, repository, _selectedFilterType.value)
        }
    }

    fun setFilterType(filterType: String) {
        if (_selectedFilterType.value == filterType) return
        _selectedFilterType.value = filterType
        loadChannelPreviews()
        selectChannel(_selectedChannel.value)
    }

    fun setSlideshowDelaySec(sec: Int) {
        repository.slideshowDelaySec = sec
    }

    fun refreshActiveChannel() {
        val current = _selectedChannel.value
        selectChannel(current)
        loadChannelPreviews()
    }
}
