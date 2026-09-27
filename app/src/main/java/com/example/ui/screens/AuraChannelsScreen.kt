package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Channel
import com.example.data.ChannelState
import com.example.data.MediaItem
import com.example.ui.components.AuraMediaThumbnail
import com.example.ui.components.AuraSectionHeader
import com.example.ui.theme.*

@Composable
fun AuraChannelsScreen(
    viewModel: ChannelViewModel,
    onMediaSelect: (MediaItem, List<MediaItem>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val channelState by viewModel.channelState.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val selectedFilterType by viewModel.selectedFilterType.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AuraBackground)
            .statusBarsPadding()
            .padding(AuraSpacing.M)
    ) {
        AuraSectionHeader(
            title = "Aura Channels",
            subtitle = "Continuous personalized stream"
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

        Spacer(modifier = Modifier.height(AuraSpacing.S))

        // Channel Selector Pill Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.S)
        ) {
            viewModel.defaultChannels.forEach { channel ->
                val isSelected = selectedChannel.id == channel.id
                Surface(
                    onClick = { viewModel.selectChannel(channel) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) DiscoveryViolet else Color.White,
                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, AuraSubtleBorder),
                    modifier = Modifier.height(36.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else AuraMutedSlate
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AuraSpacing.M))

        // Stream Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (val state = channelState) {
                is ChannelState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = DiscoveryViolet)
                            Spacer(modifier = Modifier.height(AuraSpacing.M))
                            Text(
                                text = "Tune in to ${state.channel.title}...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AuraMutedSlate
                            )
                        }
                    }
                }
                is ChannelState.Success -> {
                    if (state.items.isEmpty()) {
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
                                    contentDescription = "Empty Channel",
                                    tint = DiscoveryViolet,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.M))
                                Text(
                                    text = "No Media Matched",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AuraMidnight
                                )
                                Spacer(modifier = Modifier.height(AuraSpacing.XS))
                                Text(
                                    text = "Scan or import media to build your channel stream.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AuraMutedSlate,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(bottom = AuraSpacing.L),
                            horizontalArrangement = Arrangement.spacedBy(AuraSpacing.S),
                            verticalArrangement = Arrangement.spacedBy(AuraSpacing.S),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.items, key = { it.id }) { item ->
                                Surface(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(AuraSpacing.CornerRadiusSmall))
                                        .clickable { onMediaSelect(item, state.items) },
                                    color = AuraSubtleBorder
                                ) {
                                    AuraMediaThumbnail(
                                        itemId = item.id,
                                        mediaType = item.mediaType,
                                        imageUrl = item.imageUrl,
                                        uriPath = item.uriPath,
                                        title = item.title,
                                        modifier = Modifier.fillMaxSize(),
                                        locationTag = "channel_grid"
                                    )
                                }
                            }
                        }
                    }
                }
                is ChannelState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AuraCrispWhite, shape = RoundedCornerShape(AuraSpacing.CornerRadiusMedium))
                            .padding(AuraSpacing.L),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Channel Retrieval Failed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(AuraSpacing.XS))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = AuraMutedSlate,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(AuraSpacing.M))
                            Button(
                                onClick = { viewModel.refreshActiveChannel() },
                                colors = ButtonDefaults.buttonColors(containerColor = DiscoveryViolet)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry")
                                Spacer(modifier = Modifier.width(AuraSpacing.XS))
                                Text("Retry")
                            }
                        }
                    }
                }
                ChannelState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
