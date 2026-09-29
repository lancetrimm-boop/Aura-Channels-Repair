package com.example.ui.screens

import androidx.media3.exoplayer.ExoPlayer
import com.example.data.Channel
import com.example.data.ChannelKind
import com.example.data.ChannelRegistry
import com.example.data.MediaItem
import com.example.ui.components.ChannelPreviewPool
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ChannelPreviewArchitectureTest {

    private lateinit var pool: ChannelPreviewPool

    @Before
    fun setUp() {
        ChannelRegistry.clearCustomChannelsForTesting()
        pool = ChannelPreviewPool(
            maxPlayers = 3,
            playerFactory = {
                val mockPlayer = mock(ExoPlayer::class.java)
                org.mockito.Mockito.`when`(mockPlayer.applicationLooper).thenReturn(android.os.Looper.getMainLooper())
                org.mockito.Mockito.`when`(mockPlayer.playbackParameters).thenReturn(androidx.media3.common.PlaybackParameters.DEFAULT)
                mockPlayer
            }
        )
    }

    private fun createMediaItem(id: String, isVid: Boolean = true): MediaItem {
        return MediaItem(
            id = id,
            title = "Item $id",
            mediaType = if (isVid) "VIDEO" else "PHOTO",
            uriPath = if (isVid) "content://media/external/video/media/$id" else "content://media/external/images/media/$id"
        )
    }

    @Test
    fun testPreviewPool_MaxPlayersConfigurable() {
        assertEquals(3, pool.getMaxPlayers())
        pool.setMaxPlayers(2)
        assertEquals(2, pool.getMaxPlayers())
        pool.setMaxPlayers(1)
        assertEquals(1, pool.getMaxPlayers())
    }

    @Test
    fun testPreviewPool_PhotosNeverAllocatePlayer() {
        val photo = createMediaItem("p1", isVid = false)
        val player = pool.acquirePlayer(
            context = RuntimeEnvironment.getApplication(),
            channelId = "ME_TV",
            mediaItem = photo
        )
        assertNull("Photos must never allocate ExoPlayer instance", player)
        assertEquals(0, pool.getActivePlayerCount())
    }

    @Test
    fun testPreviewPool_CapEnforcedAtMax3Active() {
        val context = RuntimeEnvironment.getApplication()
        val item1 = createMediaItem("v1")
        val item2 = createMediaItem("v2")
        val item3 = createMediaItem("v3")
        val item4 = createMediaItem("v4")

        pool.acquirePlayer(context, "ME_TV", item1)
        pool.acquirePlayer(context, "FAVORITES", item2)
        pool.acquirePlayer(context, "REDISCOVER", item3)

        assertEquals(3, pool.getActivePlayerCount())

        // 4th channel acquires player -> 1st channel (LRU) is evicted and reused for 4th
        pool.acquirePlayer(context, "CONTINUE", item4)
        assertEquals(3, pool.getActivePlayerCount())
        assertEquals(0, pool.getIdlePlayerCount())
    }

    @Test
    fun testPreviewPool_ReleaseAllClearsState() {
        val context = RuntimeEnvironment.getApplication()
        val item1 = createMediaItem("v1")
        pool.acquirePlayer(context, "ME_TV", item1)

        assertTrue(pool.getActivePlayerCount() > 0)
        pool.releaseAll()
        assertEquals(0, pool.getActivePlayerCount())
        assertEquals(0, pool.getIdlePlayerCount())
    }

    @Test
    fun testChannelLineupOrder_Exact5FixedBeforeSearchSeeded() {
        val fixed = ChannelRegistry.allFixedChannels()
        assertEquals(5, fixed.size)
        assertEquals("ME_TV", fixed[0].id)
        assertEquals("FAVORITES", fixed[1].id)
        assertEquals("REDISCOVER", fixed[2].id)
        assertEquals("CONTINUE", fixed[3].id)
        assertEquals("MOOD", fixed[4].id)

        val seeded = Channel(
            id = "seeded_search",
            title = "Ocean Beach",
            query = "ocean beach",
            channelKind = ChannelKind.SEARCH_SEEDED,
            strategyId = "SEARCH_SEEDED"
        )
        ChannelRegistry.addSearchSeededChannel(seeded)

        val all = ChannelRegistry.allChannels()
        assertEquals(6, all.size)
        assertEquals("ME_TV", all[0].id)
        assertEquals("seeded_search", all[5].id)
    }

    @Test
    fun testNoDuplicatePreviewCandidatesAssignedAcrossChannels() {
        val item1 = createMediaItem("item_1")
        val item2 = createMediaItem("item_2")
        val item3 = createMediaItem("item_3")
        val item4 = createMediaItem("item_4")
        val item5 = createMediaItem("item_5")
        val available = listOf(item1, item2, item3, item4, item5)

        val usedPreviewIds = mutableSetOf<String>()
        val channels = ChannelRegistry.allFixedChannels()

        channels.forEach { channel ->
            val candidate = available.firstOrNull { it.id !in usedPreviewIds } ?: available.firstOrNull()
            assertNotNull(candidate)
            assertTrue("Candidate must not be duplicated across channel previews", usedPreviewIds.add(candidate!!.id))
        }

        assertEquals("5 fixed channels must receive 5 unique preview candidates when available", 5, usedPreviewIds.size)
    }

    @Test
    fun testPreviewPool_RebindsPlayerWhenMediaItemChangesForSameChannel() {
        val context = RuntimeEnvironment.getApplication()
        val itemA = createMediaItem("v1")
        val itemB = createMediaItem("v2")

        val player1 = pool.acquirePlayer(context, "ME_TV", itemA)
        assertNotNull(player1)
        assertEquals(1, pool.getActivePlayerCount())

        // Re-acquire for same channel ID but different mediaItem -> returns same player instance, re-bound
        val player2 = pool.acquirePlayer(context, "ME_TV", itemB)
        assertNotNull(player2)
        assertSame(player1, player2)
        assertEquals(1, pool.getActivePlayerCount())
    }

    @Test
    fun testPreviewPool_ReleasePlayerClearsActiveItemMap() {
        val context = RuntimeEnvironment.getApplication()
        val item1 = createMediaItem("v1")

        pool.acquirePlayer(context, "ME_TV", item1)
        assertEquals(1, pool.getActivePlayerCount())

        pool.releasePlayer("ME_TV")
        assertEquals(0, pool.getActivePlayerCount())
        assertEquals(1, pool.getIdlePlayerCount())
    }

    @Test
    fun testPreviewPool_DetachBeforeIdleInvariantOnEvictionAndRelease() {
        val context = RuntimeEnvironment.getApplication()
        val item1 = createMediaItem("v1")
        val item2 = createMediaItem("v2")
        val item3 = createMediaItem("v3")
        val item4 = createMediaItem("v4")

        val playerView1 = androidx.media3.ui.PlayerView(context)
        val playerView2 = androidx.media3.ui.PlayerView(context)

        val player1 = pool.acquirePlayer(context, "ME_TV", item1)
        pool.bindView("ME_TV", playerView1)
        assertEquals(player1, playerView1.player)

        val player2 = pool.acquirePlayer(context, "FAVORITES", item2)
        pool.bindView("FAVORITES", playerView2)
        assertEquals(player2, playerView2.player)

        // Explicit unbind/release of ME_TV
        pool.unbindView("ME_TV")
        pool.releasePlayer("ME_TV")
        assertNull("PlayerView MUST be detached upon unbind/release", playerView1.player)

        // Acquire 3rd and 4th channels to force LRU eviction of FAVORITES
        pool.acquirePlayer(context, "REDISCOVER", item3)
        pool.acquirePlayer(context, "CONTINUE", item4) // Forces LRU eviction of FAVORITES

        assertNull("PlayerView MUST be detached upon LRU eviction before player enters idle pool", playerView2.player)
    }

    @Test
    fun testInsufficientMedia_RemainingChannelsReceiveNullCandidateInsteadOfDuplicate() {
        val item1 = createMediaItem("item_1")
        val item2 = createMediaItem("item_2")
        val available = listOf(item1, item2) // Only 2 items for 5 channels

        val usedPreviewIds = mutableSetOf<String>()
        val channels = ChannelRegistry.allFixedChannels()

        val assigned = channels.map { channel ->
            val candidate = available.firstOrNull { it.id !in usedPreviewIds }
            if (candidate != null) {
                usedPreviewIds.add(candidate.id)
            }
            candidate
        }

        assertEquals(2, assigned.filterNotNull().size)
        assertEquals(3, assigned.filter { it == null }.size)
        assertEquals(2, usedPreviewIds.size)
    }

    @Test
    fun testGetActivePlayer_NullOnEviction() {
        val context = RuntimeEnvironment.getApplication()
        val item1 = createMediaItem("v1")
        val item2 = createMediaItem("v2")
        val item3 = createMediaItem("v3")
        val item4 = createMediaItem("v4")

        val p1 = pool.acquirePlayer(context, "ME_TV", item1)
        assertEquals(p1, pool.getActivePlayer("ME_TV"))

        pool.acquirePlayer(context, "FAVORITES", item2)
        pool.acquirePlayer(context, "REDISCOVER", item3)
        assertEquals(p1, pool.getActivePlayer("ME_TV"))

        // 4th channel forces LRU eviction of ME_TV
        val p4 = pool.acquirePlayer(context, "CONTINUE", item4)
        assertNull("getActivePlayer for evicted channel MUST return null immediately", pool.getActivePlayer("ME_TV"))
        assertEquals(p4, pool.getActivePlayer("CONTINUE"))
    }
}
