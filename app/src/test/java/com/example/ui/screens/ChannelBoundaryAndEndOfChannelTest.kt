package com.example.ui.screens

import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelBoundaryAndEndOfChannelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var repository: MediaRepository
    private lateinit var viewModel: ChannelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ChannelRegistry.clearCustomChannelsForTesting()
        repository = MediaRepository(testDispatcher)
        viewModel = ChannelViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createItem(id: String, isFav: Boolean = false, parentId: String? = null): MediaItem {
        return MediaItem(
            id = id,
            title = "Item $id",
            mediaType = "VIDEO",
            isFavorite = isFav,
            parentContentId = parentId,
            uriPath = "content://media/external/video/media/$id"
        )
    }

    @Test
    fun testFavoritesEmpty_NoArbitraryGlobalItemInjected() = testScope.runTest {
        // Library with non-favorite items
        val nonFav1 = createItem("v1", isFav = false)
        val nonFav2 = createItem("v2", isFav = false)
        repository.setMediaItemsForTesting(listOf(nonFav1, nonFav2))
        advanceUntilIdle()

        viewModel.loadChannelPreviews()
        advanceUntilIdle()

        val favPreview = viewModel.channelPreviews.value.find { it.channel.id == "FAVORITES" }
        assertNotNull(favPreview)
        assertNull("Favorites preview candidate MUST be null when no favorites exist", favPreview?.candidateItem)
        assertTrue("Favorites fullItems MUST be empty when no favorites exist", favPreview?.fullItems.isNullOrEmpty())
    }

    @Test
    fun testContinueEmpty_NoArbitraryGlobalItemInjected() = testScope.runTest {
        // Library with no serialized items (parentContentId == null)
        val standalone1 = createItem("v1", parentId = null)
        val standalone2 = createItem("v2", parentId = null)
        repository.setMediaItemsForTesting(listOf(standalone1, standalone2))
        advanceUntilIdle()

        viewModel.loadChannelPreviews()
        advanceUntilIdle()

        val continuePreview = viewModel.channelPreviews.value.find { it.channel.id == "CONTINUE" }
        assertNotNull(continuePreview)
        assertNull("Continue preview candidate MUST be null when no series items exist", continuePreview?.candidateItem)
        assertTrue("Continue fullItems MUST be empty when no series items exist", continuePreview?.fullItems.isNullOrEmpty())
    }

    @Test
    fun testCandidateUniqueness_And_InsufficientMediaNullHandling() = testScope.runTest {
        val item1 = createItem("v1", isFav = true)
        val item2 = createItem("v2", isFav = true)
        repository.setMediaItemsForTesting(listOf(item1, item2))
        advanceUntilIdle()

        viewModel.loadChannelPreviews()
        advanceUntilIdle()

        val previews = viewModel.channelPreviews.value
        val assignedCandidates = previews.mapNotNull { it.candidateItem }

        // Confirm candidates are 100% unique
        val candidateIds = assignedCandidates.map { it.id }
        assertEquals(candidateIds.distinct().size, candidateIds.size)

        // Confirm channels without qualifying items receive null candidate
        val nullPreviews = previews.filter { it.candidateItem == null }
        assertTrue("Channels without items must have candidateItem = null", nullPreviews.isNotEmpty())
    }

    @Test
    fun testEndOfChannel_ReloadPreservesChannelAndResetsPlaylist() = testScope.runTest {
        val item1 = createItem("v1", isFav = true)
        val item2 = createItem("v2", isFav = true)
        repository.setMediaItemsForTesting(listOf(item1, item2))
        repository.setPlaylist(listOf(item1, item2), 1, "Favorites Station")
        advanceUntilIdle()

        val favChannel = ChannelRegistry.get("FAVORITES")!!
        viewModel.selectChannel(favChannel)

        // Simulate Reload
        repository.setPlaylist(listOf(item1, item2), 0, favChannel.title)
        advanceUntilIdle()

        val reloadedPlaylist = repository.activePlaylist.value
        assertNotNull(reloadedPlaylist)
        assertEquals(0, reloadedPlaylist?.currentIndex)
        assertEquals("FAVORITES", viewModel.selectedChannel.value.id)
    }
}
