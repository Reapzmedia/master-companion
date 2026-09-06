package com.mastercompanion.ui.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Interactive scrubbable and clickable music progress timeline bar.
 *
 * Supports:
 * 1. Instant tap-to-seek anywhere along the bar.
 * 2. Smooth scrubbing / horizontal dragging with an enlarged glowing thumb.
 * 3. Immediate local visual update to eliminate perceived network lag.
 */
@Composable
fun InteractiveMusicProgressBar(
    progressFraction: Float,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    barHeight: Dp = 6.dp,
    touchTargetHeight: Dp = 32.dp,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.22f),
    thumbColor: Color = Color.White
) {
    var isDragging by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val effectiveFraction = if (isDragging) {
        scrubFraction.coerceIn(0f, 1f)
    } else {
        progressFraction.coerceIn(0f, 1f)
    }

    val thumbRadius by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 5.dp,
        animationSpec = tween(120),
        label = "thumb_radius"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(touchTargetHeight)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (size.width > 0 && durationMs > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetMs = (fraction * durationMs).toLong()
                        scrubFraction = fraction
                        onSeek(targetMs)
                    }
                }
            }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        if (size.width > 0) {
                            scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        if (durationMs > 0) {
                            val targetMs = (scrubFraction * durationMs).toLong()
                            onSeek(targetMs)
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (size.width > 0) {
                            scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(touchTargetHeight)
        ) {
            val width = size.width
            val height = size.height
            val trackHeightPx = barHeight.toPx()
            val thumbRadiusPx = thumbRadius.toPx()
            val topOffset = (height - trackHeightPx) / 2f
            val cornerRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

            // Inactive background track
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, topOffset),
                size = Size(width, trackHeightPx),
                cornerRadius = cornerRadius
            )

            // Active progress fill
            val activeWidth = width * effectiveFraction
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, topOffset),
                    size = Size(activeWidth, trackHeightPx),
                    cornerRadius = cornerRadius
                )
            }

            // Scrubbing thumb circle with subtle drop glow
            val thumbCenterX = activeWidth.coerceIn(thumbRadiusPx, width - thumbRadiusPx)
            val thumbCenterY = height / 2f

            if (isDragging) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.28f),
                    radius = thumbRadiusPx * 1.8f,
                    center = Offset(thumbCenterX, thumbCenterY)
                )
            }

            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(thumbCenterX, thumbCenterY)
            )
        }
    }
}
