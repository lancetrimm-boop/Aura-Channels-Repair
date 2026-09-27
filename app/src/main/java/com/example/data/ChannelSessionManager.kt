package com.example.data

import com.example.data.intelligence.*
import com.example.data.semantic.SemanticInput
import com.example.data.semantic.SemanticRepresentationType
import com.example.data.semantic.EmbeddingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Orchestration boundary for active Channel candidate retrieval and ranking.
 * Composes existing production intelligence core and router primitives without creating a parallel pipeline.
 */
class ChannelSessionManager {

    private val _channelState = MutableStateFlow<ChannelState>(ChannelState.Idle)
    val channelState: StateFlow<ChannelState> = _channelState.asStateFlow()

    suspend fun selectChannel(
        channel: Channel,
        repository: MediaRepository,
        filterType: String = "VIDEOS"
    ) = withContext(Dispatchers.Default) {
        _channelState.value = ChannelState.Loading(channel)

        try {
            val core = repository.intelligenceCore
            if (core == null) {
                _channelState.value = ChannelState.Error(channel, "Intelligence engine not ready")
                return@withContext
            }

            // Resolve referenceMediaIds to visual vectors if present
            val queryVectors = mutableListOf<FloatArray>()
            if (channel.referenceMediaIds.isNotEmpty()) {
                val semanticRepo = repository.semanticRepresentationRepository
                val provider = repository.mobileCLIPProvider

                for (refId in channel.referenceMediaIds) {
                    val stored = if (provider != null && semanticRepo != null) {
                        semanticRepo.getSpecificRepresentation(refId, SemanticRepresentationType.VISUAL, provider.descriptor)?.vector
                    } else null

                    if (stored != null) {
                        queryVectors.add(stored)
                    } else {
                        val refItem = repository.getMediaItemById(refId)
                        val uri = refItem?.uriPath?.ifEmpty { refItem.imageUrl } ?: ""
                        val ctx = repository.applicationContext
                        if (uri.isNotEmpty() && ctx != null && provider != null && provider.isReady()) {
                            val bitmap = com.example.util.MediaThumbnailFetcher.getThumbnail(ctx, uri)
                            if (bitmap != null) {
                                val result = provider.generateEmbedding(refId, SemanticInput.ExplicitBitmap(bitmap), "channel_ref")
                                if (result is EmbeddingResult.Success) {
                                    queryVectors.add(result.representation.vector)
                                }
                            }
                        }
                    }
                }
            }

            val request = IntelligenceRequest(
                mode = IntelligenceMode.SEARCH,
                filterType = filterType,
                query = channel.query.ifBlank { null },
                queryVectors = queryVectors.ifEmpty { null },
                limit = 40,
                tasteDNA = repository.tasteDNA.value,
                profile = repository.preferenceProfile.value,
                stats = repository.intelligenceStats.value,
                creatorProfiles = repository.creatorProfiles.value,
                comparisonCounts = repository.getComparisonCounts(),
                skipPersistence = true
            )

            DecisionTraceCollector.logEvent(
                request.requestId,
                com.example.ui.models.TraceEventType.CHANNEL_RETRIEVAL_START,
                detail = "Channel: ${channel.title}",
                metadata = mapOf("channel_id" to channel.id, "query" to channel.query)
            )

            val response = core.processRequest(request)
            if (response.isSuccess) {
                val rankedItems = response.candidates.map { it.item }
                _channelState.value = ChannelState.Success(channel, rankedItems)
            } else {
                _channelState.value = ChannelState.Error(channel, response.errorMessage ?: "Channel retrieval failed")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _channelState.value = ChannelState.Error(channel, e.message ?: "Channel calculation error")
        }
    }
}
