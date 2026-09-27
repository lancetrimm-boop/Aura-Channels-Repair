package com.example.data

/**
 * Immutable domain model representing a personalized discovery Channel.
 */
data class Channel(
    val id: String,
    val title: String,
    val query: String,
    val referenceMediaIds: List<String> = emptyList()
)

/**
 * UI State for an active Channel stream.
 */
sealed class ChannelState {
    object Idle : ChannelState()
    data class Loading(val channel: Channel) : ChannelState()
    data class Success(
        val channel: Channel,
        val items: List<MediaItem>
    ) : ChannelState()
    data class Error(
        val channel: Channel,
        val message: String
    ) : ChannelState()
}
