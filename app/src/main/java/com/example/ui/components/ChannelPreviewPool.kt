package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
    private val activePlayers = mutableMapOf<String, ExoPlayer>()
    private val idlePlayers = mutableListOf<ExoPlayer>()
    private val accessOrder = mutableListOf<String>()

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
            existing?.playWhenReady = true
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
                val evicted = activePlayers.remove(keyToEvict)
                evicted?.apply {
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
            accessOrder.add(poolKey)
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
    fun releasePlayer(channelId: String) {
        val player = activePlayers.remove(channelId)
        accessOrder.remove(channelId)
        player?.apply {
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
        activePlayers.values.forEach {
            it.stop()
            it.release()
        }
        activePlayers.clear()
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
            val evicted = activePlayers.remove(keyToEvict)
            evicted?.apply {
                stop()
                clearMediaItems()
                idlePlayers.add(this)
            }
        }
    }
}
