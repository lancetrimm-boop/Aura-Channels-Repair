package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.mutableStateMapOf
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.MediaItem

/**
 * UI-scoped Shared Preview Player Pool for Aura Channels.
 * Strict cap on active preview players (Default: 3 active players).
 * Automatically recycles ExoPlayer instances as channel cards enter/leave visibility.
 */
@OptIn(UnstableApi::class)
class ChannelPreviewPool(
    private var maxPlayers: Int = 3,
    private val playerFactory: ((Context) -> ExoPlayer)? = null
) {
    private val activePlayers = mutableStateMapOf<String, ExoPlayer>()
    private val activeMediaItemIds = mutableMapOf<String, String>()
    private val activeViews = mutableMapOf<String, PlayerView>()
    private val idlePlayers = mutableListOf<ExoPlayer>()
    private val accessOrder = mutableListOf<String>()

    @Synchronized
    fun getActivePlayer(channelId: String): ExoPlayer? = activePlayers[channelId]

    fun setMaxPlayers(max: Int) {
        maxPlayers = max.coerceAtLeast(1)
        trimToMax()
    }

    fun getMaxPlayers(): Int = maxPlayers

    fun getActivePlayerCount(): Int = activePlayers.size

    fun getIdlePlayerCount(): Int = idlePlayers.size

    @Synchronized
    fun acquirePlayer(
        context: Context,
        channelId: String,
        mediaItem: MediaItem
    ): ExoPlayer? {
        val isVideo = mediaItem.mediaType.equals("VIDEO", ignoreCase = true) || mediaItem.mediaType.startsWith("VIDEO", ignoreCase = true)
        if (!isVideo) {
            return null // Photos never allocate ExoPlayer
        }

        val uriString = mediaItem.uriPath.ifEmpty { mediaItem.imageUrl }
        if (uriString.isEmpty()) return null

        val poolKey = channelId

        if (activePlayers.containsKey(poolKey)) {
            accessOrder.remove(poolKey)
            accessOrder.add(poolKey)
            val existing = activePlayers[poolKey]
            val boundItemId = activeMediaItemIds[poolKey]

            if (boundItemId != mediaItem.id) {
                // Candidate media item changed for this channel! Explicitly re-bind player.
                try {
                    val uri = Uri.parse(uriString)
                    existing?.apply {
                        stop()
                        clearMediaItems()
                        setMediaItem(Media3Item.fromUri(uri))
                        volume = 0f
                        repeatMode = Player.REPEAT_MODE_ONE
                        prepare()
                        playWhenReady = true
                    }
                    activeMediaItemIds[poolKey] = mediaItem.id
                } catch (e: Exception) {
                    Log.e("ChannelPreviewPool", "Failed to rebind player for $channelId", e)
                }
            } else {
                existing?.playWhenReady = true
            }
            return existing
        }

        trimToMax()

        val player = try {
            if (idlePlayers.isNotEmpty()) {
                idlePlayers.removeAt(0)
            } else if (activePlayers.size < maxPlayers) {
                createPlayer(context)
            } else {
                val keyToEvict = accessOrder.firstOrNull() ?: return null
                accessOrder.remove(keyToEvict)
                activeMediaItemIds.remove(keyToEvict)
                val view = activeViews.remove(keyToEvict)
                if (view != null) {
                    Log.d("AuraPreviewTrace", "DETACH $keyToEvict / view=${view.hashCode()}")
                    view.player = null
                }
                val evicted = activePlayers.remove(keyToEvict)
                evicted?.apply {
                    Log.d("AuraPreviewTrace", "EVICT $keyToEvict / player=${this.hashCode()}")
                    Log.d("AuraPreviewTrace", "RELEASE $keyToEvict / player=${this.hashCode()}")
                    stop()
                    clearMediaItems()
                    idlePlayers.add(this)
                }
                if (idlePlayers.isNotEmpty()) idlePlayers.removeAt(0) else return null
            }
        } catch (e: Exception) {
            Log.e("ChannelPreviewPool", "Failed to instantiate player for $channelId", e)
            return null
        }

        return try {
            val uri = Uri.parse(uriString)
            player.apply {
                stop()
                clearMediaItems()
                setMediaItem(Media3Item.fromUri(uri))
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
            activePlayers[poolKey] = player
            activeMediaItemIds[poolKey] = mediaItem.id
            accessOrder.add(poolKey)
            Log.d("AuraPreviewTrace", "ACQUIRE $channelId / media=${mediaItem.id} / player=${player.hashCode()}")
            player
        } catch (e: Exception) {
            Log.e("ChannelPreviewPool", "Failed to prepare preview for $channelId", e)
            null
        }
    }

    private fun createPlayer(context: Context): ExoPlayer {
        return if (playerFactory != null) {
            playerFactory.invoke(context)
        } else {
            ExoPlayer.Builder(context).build().apply {
                volume = 0f // Muted preview
                repeatMode = Player.REPEAT_MODE_ONE // Looping preview
            }
        }
    }

    @Synchronized
    fun bindView(channelId: String, playerView: PlayerView) {
        val oldView = activeViews[channelId]
        if (oldView != null && oldView !== playerView) {
            Log.d("AuraPreviewTrace", "DETACH $channelId / view=${oldView.hashCode()}")
            oldView.player = null
        }
        activeViews[channelId] = playerView
        val player = activePlayers[channelId]
        if (playerView.player !== player) {
            playerView.player = player
            if (player != null) {
                Log.d("AuraPreviewTrace", "ATTACH $channelId / view=${playerView.hashCode()} / player=${player.hashCode()}")
            } else {
                Log.d("AuraPreviewTrace", "DETACH $channelId / view=${playerView.hashCode()}")
            }
        }
    }

    @Synchronized
    fun unbindView(channelId: String, playerView: PlayerView? = null) {
        val viewToDetach = if (playerView != null) {
            if (activeViews[channelId] === playerView) activeViews.remove(channelId) else playerView
        } else {
            activeViews.remove(channelId)
        }
        if (viewToDetach != null) {
            Log.d("AuraPreviewTrace", "DETACH $channelId / view=${viewToDetach.hashCode()}")
            viewToDetach.player = null
        }
    }

    @Synchronized
    fun releasePlayer(channelId: String) {
        // Hard Invariant: Synchronously detach PlayerView FIRST before adding ExoPlayer to idlePlayers
        val view = activeViews.remove(channelId)
        if (view != null) {
            Log.d("AuraPreviewTrace", "DETACH $channelId / view=${view.hashCode()}")
            view.player = null
        }

        val player = activePlayers.remove(channelId)
        activeMediaItemIds.remove(channelId)
        accessOrder.remove(channelId)
        player?.apply {
            Log.d("AuraPreviewTrace", "RELEASE $channelId / player=${this.hashCode()}")
            stop()
            clearMediaItems()
            idlePlayers.add(this)
        }
    }

    @Synchronized
    fun pauseAll() {
        activePlayers.values.forEach { it.playWhenReady = false }
    }

    @Synchronized
    fun releaseAll() {
        // Hard Invariant: Detach all views first upon pool cleanup
        activeViews.values.forEach { view ->
            view.player = null
        }
        activeViews.clear()

        activePlayers.values.forEach {
            it.stop()
            it.release()
        }
        activePlayers.clear()
        activeMediaItemIds.clear()
        accessOrder.clear()

        idlePlayers.forEach {
            it.stop()
            it.release()
        }
        idlePlayers.clear()
    }

    private fun trimToMax() {
        while (activePlayers.size > maxPlayers && accessOrder.isNotEmpty()) {
            val keyToEvict = accessOrder.removeAt(0)
            // Hard Invariant: Synchronously detach PlayerView FIRST before adding ExoPlayer to idlePlayers
            val view = activeViews.remove(keyToEvict)
            view?.player = null

            activeMediaItemIds.remove(keyToEvict)
            val evicted = activePlayers.remove(keyToEvict)
            evicted?.apply {
                stop()
                clearMediaItems()
                idlePlayers.add(this)
            }
        }
    }
}
