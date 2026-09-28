package com.example.ui.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.Channel
import com.example.data.ChannelKind
import com.example.data.MediaItem
import com.example.ui.components.AuraMediaThumbnail
import com.example.ui.components.AuraSectionHeader
import com.example.ui.components.ChannelPreviewPool
import com.example.ui.theme.*

@Composable
fun AuraChannelsScreen(
    viewModel: ChannelViewModel,
    onMediaSelect: (MediaItem, List<MediaItem>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val selectedFilterType by viewModel.selectedFilterType.collectAsStateWithLifecycle()
    val previews by viewModel.channelPreviews.collectAsStateWithLifecycle()
    val slideshowDelaySec by viewModel.slideshowDelaySeconds.collectAsStateWithLifecycle()

    val previewPool = remember { ChannelPreviewPool(maxPlayers = 3) }

    DisposableEffect(Unit) {
        onDispose {
            previewPool.releaseAll()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraBackground)
            .statusBarsPadding()
            .padding(AuraSpacing.M)
    ) {
        AuraSectionHeader(
            title = "Aura Channels",
            subtitle = "Continuous personalized station stream"
        )

        Spacer(modifier = Modifier.height(AuraSpacing.S))

        // Media Type Selector Row (Videos vs Photos)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.S)
        ) {
            listOf("VIDEOS" to "Videos", "PHOTOS" to "Photos").forEach { (typeKey, label) ->
                val isTypeSelected = selectedFilterType == typeKey
                Surface(
                    onClick = { viewModel.setFilterType(typeKey) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isTypeSelected) AuraPurple else Color.White,
                    border = if (isTypeSelected) null else androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isTypeSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isTypeSelected) Color.White else AuraMutedSlate
                        )
                    }
                }
            }
        }

        // Photo Slideshow Duration Slider Control (Shown when Photos mode is active)
        if (selectedFilterType == "PHOTOS") {
            Spacer(modifier = Modifier.height(AuraSpacing.S))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Photo Duration / Slideshow Delay",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuraMidnight
                        )
                        Text(
                            text = "$slideshowDelaySec ${if (slideshowDelaySec == 1) "second" else "seconds"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DiscoveryViolet
                        )
                    }

                    Slider(
                        value = slideshowDelaySec.toFloat(),
                        onValueChange = { viewModel.setSlideshowDelaySec(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = DiscoveryViolet,
                            activeTrackColor = DiscoveryViolet,
                            inactiveTrackColor = AuraSubtleBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("slideshow_delay_slider")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AuraSpacing.M))

        // Vertical Lean-Back Live Channel Browser
        val listState = rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (previews.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AuraCrispWhite, shape = RoundedCornerShape(AuraSpacing.CornerRadiusMedium))
                        .padding(AuraSpacing.L),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Loading Channels",
                            tint = DiscoveryViolet,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(AuraSpacing.M))
                        Text(
                            text = "Loading Channel Stream...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AuraMidnight
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(AuraSpacing.M),
                    contentPadding = PaddingValues(bottom = AuraSpacing.XL)
                ) {
                    itemsIndexed(previews, key = { _, preview -> preview.channel.id }) { index, preview ->
                        val isVisibleInViewport = remember(index) {
                            derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val visibleItems = layoutInfo.visibleItemsInfo
                                visibleItems.any { it.index == index }
                            }
                        }

                        ChannelPreviewCard(
                            channelPreview = preview,
                            isSelected = selectedChannel.id == preview.channel.id,
                            previewPool = previewPool,
                            isVisibleInViewport = isVisibleInViewport.value,
                            onClick = { channel, previewItem, fullList ->
                                viewModel.selectChannel(channel)
                                if (previewItem != null && fullList.isNotEmpty()) {
                                    onMediaSelect(previewItem, fullList)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ChannelPreviewCard(
    channelPreview: ChannelPreviewState,
    isSelected: Boolean,
    previewPool: ChannelPreviewPool,
    isVisibleInViewport: Boolean,
    onClick: (Channel, MediaItem?, List<MediaItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val channel = channelPreview.channel
    val item = channelPreview.candidateItem
    val fullList = channelPreview.fullItems

    val isVideo = item != null && (item.mediaType.equals("VIDEO", ignoreCase = true) || item.mediaType.startsWith("VIDEO", ignoreCase = true))

    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }

    DisposableEffect(channel.id, isVisibleInViewport, item?.id) {
        if (isVisibleInViewport && isVideo && item != null) {
            val player = previewPool.acquirePlayer(context, channel.id, item)
            if (player != null) {
                activePlayer = player
                val listener = object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        isPlayerReady = true
                    }
                }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                    previewPool.releasePlayer(channel.id)
                    activePlayer = null
                    isPlayerReady = false
                }
            } else {
                onDispose { }
            }
        } else {
            previewPool.releasePlayer(channel.id)
            activePlayer = null
            isPlayerReady = false
            onDispose { }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) DiscoveryViolet else AuraSubtleBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable {
                previewPool.releaseAll()
                onClick(channel, item, fullList)
            }
            .background(Color.Black)
    ) {
        // 1. Static Fallback Image
        if (item != null) {
            AuraMediaThumbnail(
                itemId = item.id,
                mediaType = item.mediaType,
                imageUrl = item.imageUrl,
                uriPath = item.uriPath,
                title = item.title,
                modifier = Modifier.fillMaxSize(),
                locationTag = "channel_preview_${channel.id}"
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No Media in Channel", style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
        }

        // 2. Live Motion Player View
        val currentP = activePlayer
        if (isVideo && currentP != null && isVisibleInViewport) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = currentP
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { view ->
                    view.player = currentP
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. TOP OVERLAY: Channel Title Badge (Prominent Top Banner)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                        endY = 140f
                    )
                )
                .padding(horizontal = AuraSpacing.M, vertical = 12.dp)
                .semantics { contentDescription = "Channel ${channel.title}" },
            contentAlignment = Alignment.TopStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (channel.isDefault) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Default Station",
                            tint = Color.Yellow,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Station",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DiscoveryViolet.copy(alpha = 0.85f),
                    modifier = Modifier.height(26.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (channel.channelKind == ChannelKind.SEARCH_SEEDED) "Search Seeded" else "Station",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 4. BOTTOM OVERLAY: Preview Item Info & Tune-In Play Button
        if (item != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 140f
                        )
                    )
                    .padding(AuraSpacing.M),
                contentAlignment = Alignment.BottomStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Preview: ${item.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = "${item.genre} • ${if (isVideo) "Live Video Preview" else "Photo"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Tune In Channel",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}
