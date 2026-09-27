package com.example.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChannelViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val sessionManager = ChannelSessionManager()
    val channelState: StateFlow<ChannelState> = sessionManager.channelState

    val defaultChannels = listOf(
        Channel(id = "vibrant_cinema", title = "Vibrant Cinema", query = "Cinematic vibrant dramatic lighting"),
        Channel(id = "action_motion", title = "Action & Motion", query = "High motion dynamic action clips"),
        Channel(id = "calm_atmospheric", title = "Calm & Atmospheric", query = "Atmospheric serene peaceful landscapes")
    )

    private val _selectedChannel = MutableStateFlow<Channel>(defaultChannels[0])
    val selectedChannel: StateFlow<Channel> = _selectedChannel.asStateFlow()

    private val _selectedFilterType = MutableStateFlow<String>("VIDEOS")
    val selectedFilterType: StateFlow<String> = _selectedFilterType.asStateFlow()

    init {
        // Asynchronously load the initial channel after ViewModel creation
        selectChannel(defaultChannels[0])
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
        selectChannel(_selectedChannel.value)
    }

    fun refreshActiveChannel() {
        val current = _selectedChannel.value
        selectChannel(current)
    }
}
