package com.mastercompanion.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mastercompanion.domain.model.SpotifyTrack

/**
 * Sleek corner floating banner displaying the upcoming next track
 * when the current track is within its final 25 seconds.
 *
 * Tapping the banner triggers an immediate skip to the next track.
 */
@Composable
fun UpNextBanner(
    visible: Boolean,
    nextTrack: SpotifyTrack?,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    whiteTheme: Boolean = false
) {
    AnimatedVisibility(
        visible = visible && nextTrack != null,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (nextTrack == null) return@AnimatedVisibility

        val cardBg = if (whiteTheme) Color.White.copy(alpha = 0.94f) else Color(0xFF141414).copy(alpha = 0.92f)
        val textPrimary = if (whiteTheme) Color(0xFF111827) else Color.White
        val textSecondary = if (whiteTheme) Color(0xFF6B7280) else Color.White.copy(alpha = 0.7f)
        val borderColor = if (whiteTheme) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.15f)

        Box(
            modifier = Modifier
                .shadow(12.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onSkipNext() }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .widthIn(min = 200.dp, max = 280.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Mini Album Art
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(nextTrack.albumArtUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = nextTrack.album,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (whiteTheme) Color(0xFFE5E7EB) else Color(0xFF262626))
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Text details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1DB954))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "UP NEXT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = Color(0xFF1DB954)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = nextTrack.title.ifBlank { "Next Track" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = nextTrack.artist.ifBlank { "Unknown Artist" },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Skip icon indicator
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Skip to Up Next",
                    tint = if (whiteTheme) Color(0xFF374151) else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
