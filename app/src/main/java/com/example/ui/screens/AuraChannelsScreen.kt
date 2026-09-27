package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.AuraSectionHeader
import com.example.ui.theme.*

@Composable
fun AuraChannelsScreen(
    modifier: Modifier = Modifier
) {
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

        Spacer(modifier = Modifier.height(AuraSpacing.XL))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AuraCrispWhite, shape = RoundedCornerShape(AuraSpacing.CornerRadiusMedium))
                .padding(AuraSpacing.L),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = DiscoveryViolet.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = "Channels",
                            tint = DiscoveryViolet,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(AuraSpacing.M))

                Text(
                    text = "No Active Channels",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AuraMidnight
                )

                Spacer(modifier = Modifier.height(AuraSpacing.S))

                Text(
                    text = "Aura Channels generate continuous media streams based on your taste profile and micro-preferences.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuraMutedSlate,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
