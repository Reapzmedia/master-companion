package com.mastercompanion.ui.home

import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import com.mastercompanion.data.spotify.dto.SpotifyDeviceDto
import com.mastercompanion.ui.home.lyrics.LyricsLayoutMode
import com.mastercompanion.ui.home.lyrics.LyricsLayoutSelectorDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import com.mastercompanion.domain.model.SpotifyTrack
import com.mastercompanion.domain.model.TrackLyrics
import com.mastercompanion.ui.theme.MasterCompanionTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/**
 * Universal Music Player Screen supporting:
 * 1. 5 Layouts: Standard (Spotify Car View), Vinyl Turntable, Synced Lyrics, Minimal Standby Clock, and Full-Bleed Artwork.
 * 2. Smooth 600ms Crossfade transitions on album art change.
 * 3. Adaptive orientation: Landscape Standby & Bespoke Mobile Portrait.
 * 4. Interactive volume pop-up slider controlling Android media volume.
 * 5. Anti-burn-in OLED background & White Theme support.
 */
@Composable
fun MusicPage(
    track: SpotifyTrack?,
    lyrics: TrackLyrics? = null,
    nextTrack: SpotifyTrack? = null,
    volumeHudVisible: Boolean = false,
    isLiked: Boolean = false,
    isShuffle: Boolean = false,
    repeatMode: Int = 0,
    isLibraryScopeMissing: Boolean = false,
    initialLayout: MusicPlayerLayout = MusicPlayerLayout.STANDARD,
    whiteTheme: Boolean = false,
    spotifyVolume: Int? = null,
    activeDeviceName: String = "",
    availableDevices: List<SpotifyDeviceDto> = emptyList(),
    onFetchDevices: () -> Unit = {},
    onSelectDevice: (String) -> Unit = {},
    lyricsLayout: Int = 0,
    onLyricsLayoutChanged: (Int) -> Unit = {},
    onSetSpotifyVolume: (Int) -> Unit = {},
    onPlayPauseToggle: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onConnectSpotify: () -> Unit = {},
    onLayoutChanged: (MusicPlayerLayout) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDevicePickerDialog by remember { mutableStateOf(false) }

    val handleOpenDevices = {
        showDevicePickerDialog = true
        onFetchDevices()
        onOpenDevices()
    }

    if (showDevicePickerDialog) {
        SpotifyDevicePickerDialog(
            devices = availableDevices,
            activeDeviceName = activeDeviceName,
            onSelectDevice = { targetDeviceId ->
                onSelectDevice(targetDeviceId)
                showDevicePickerDialog = false
            },
            onRefresh = onFetchDevices,
            onDismiss = { showDevicePickerDialog = false },
            whiteTheme = whiteTheme
        )
    }

    if (track == null) {
        MusicPageEmptyState(
            onConnectSpotify = onConnectSpotify,
            onOpenDevices = handleOpenDevices,
            modifier = modifier
        )
        return
    }

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var showVolumePopup by remember { mutableStateOf(false) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var pendingVolumeToSend by remember { mutableStateOf<Int?>(null) }
    var lastVolumeInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 5-second inactivity timer to automatically dismiss music / volume bar
    LaunchedEffect(showVolumePopup, lastVolumeInteractionTime, isDraggingSlider) {
        if (showVolumePopup && !isDraggingSlider) {
            val mark = lastVolumeInteractionTime
            delay(5000L)
            if (lastVolumeInteractionTime == mark && !isDraggingSlider) {
                showVolumePopup = false
            }
        }
    }

    val toggleVolumePopup = {
        showVolumePopup = !showVolumePopup
        lastVolumeInteractionTime = System.currentTimeMillis()
    }

    var volumeProgress by remember {
        mutableFloatStateOf(
            if (spotifyVolume != null) {
                (spotifyVolume.toFloat() / 100f).coerceIn(0f, 1f)
            } else {
                audioManager?.let {
                    val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                    val cur = it.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    if (max > 0) cur / max else 0.5f
                } ?: 0.5f
            }
        )
    }

    // Synchronize remote Spotify active playback device volume into local slider state
    LaunchedEffect(spotifyVolume) {
        if (!isDraggingSlider && spotifyVolume != null) {
            volumeProgress = (spotifyVolume.toFloat() / 100f).coerceIn(0f, 1f)
        }
    }

    // Debounced Spotify Web API volume updates during user dragging
    LaunchedEffect(pendingVolumeToSend) {
        val vol = pendingVolumeToSend ?: return@LaunchedEffect
        delay(80L)
        onSetSpotifyVolume(vol)
    }

    val allLayouts = MusicPlayerLayout.values()
    var layoutMode by remember { mutableStateOf(initialLayout) }

    var showScopeMissingDialog by remember { mutableStateOf(false) }

    val handleToggleLike = {
        onToggleLike()
    }


    fun cycleLayout() {
        val nextIndex = (layoutMode.ordinal + 1) % allLayouts.size
        layoutMode = allLayouts[nextIndex]
        onLayoutChanged(layoutMode)
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Smooth continuous local playback progression
    var interpolatedProgressMs by remember(track.id, track.progressMs, track.isPlaying) {
        mutableLongStateOf(track.progressMs)
    }
    LaunchedEffect(track.id, track.isPlaying, track.progressMs) {
        if (track.isPlaying) {
            val startLocal = System.currentTimeMillis()
            val startProgress = track.progressMs
            while (true) {
                val elapsed = System.currentTimeMillis() - startLocal
                interpolatedProgressMs = (startProgress + elapsed).coerceAtMost(track.durationMs)
                delay(200L)
            }
        } else {
            interpolatedProgressMs = track.progressMs
        }
    }

    val rawProgressFraction = if (track.durationMs > 0) {
        (interpolatedProgressMs.toFloat() / track.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val progressFraction by animateFloatAsState(
        targetValue = rawProgressFraction,
        animationSpec = tween(durationMillis = 200, easing = androidx.compose.animation.core.LinearEasing),
        label = "SeekbarProgressSmooth"
    )

    // Extract dynamic theme color directly from actual album art RGB values
    var dynamicThemeColor by remember(track.albumArtUrl) { mutableStateOf<Color?>(null) }
    LaunchedEffect(track.albumArtUrl) {
        if (!track.albumArtUrl.isNullOrBlank()) {
            val bmp = loadBitmapFromUrl(context, track.albumArtUrl)
            if (bmp != null) {
                val extracted = extractVibrantRgbColor(bmp)
                dynamicThemeColor = extracted
            }
        } else {
            dynamicThemeColor = null
        }
    }

    val themeAccent = dynamicThemeColor ?: if (whiteTheme) Color(0xFF0284C7) else Color(0xFF1DB954)

    val baseBgColor = if (whiteTheme) Color(0xFFF8F9FA) else Color.Black

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBgColor)
    ) {
        // ═══ 1. Animated Crossfade Background on Album Art Change ═══
        if (layoutMode != MusicPlayerLayout.FULL_BLEED) {
            Crossfade(
                targetState = track.albumArtUrl,
                animationSpec = tween(durationMillis = 800),
                label = "BackgroundCrossfade"
            ) { artUrl ->
                if (!artUrl.isNullOrBlank() && !whiteTheme) {
                    var isLoaded by remember(artUrl) { mutableStateOf(false) }
                    val bgFadeAlpha by animateFloatAsState(
                        targetValue = if (isLoaded) 0.65f else 0.0f,
                        animationSpec = tween(durationMillis = 800),
                        label = "bgFadeAlpha"
                    )
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artUrl)
                            .crossfade(true)
                            .crossfade(800)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onSuccess = { isLoaded = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(50.dp)
                            .graphicsLayer(alpha = bgFadeAlpha)
                    )

                    // Atmospheric radial dark vignette
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Black.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(baseBgColor)
                    )
                }
            }
        }

        // ═══ 2. Content Layouts (5 Styles) ═══
        when (layoutMode) {
            MusicPlayerLayout.STANDARD -> {
                if (isLandscape) {
                    LandscapeStandardView(
                        track = track,
                        progressFraction = progressFraction,
                        isLiked = isLiked,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        activeDeviceName = activeDeviceName,
                        availableDevices = availableDevices,
                        onFetchDevices = onFetchDevices,
                        onSelectDevice = onSelectDevice,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onSeek = onSeek,
                        onToggleShuffle = onToggleShuffle,
                        onToggleLike = handleToggleLike,
                        onToggleRepeat = onToggleRepeat,
                        onOpenDevices = handleOpenDevices,
                        onToggleLyrics = { layoutMode = MusicPlayerLayout.LYRICS },
                        onCycleLayout = { cycleLayout() },
                        onToggleVolume = toggleVolumePopup,
                        whiteTheme = whiteTheme
                    )
                } else {
                    PortraitStandardView(
                        track = track,
                        progressFraction = progressFraction,
                        isLiked = isLiked,
                        isShuffle = isShuffle,
                        repeatMode = repeatMode,
                        activeDeviceName = activeDeviceName,
                        availableDevices = availableDevices,
                        onFetchDevices = onFetchDevices,
                        onSelectDevice = onSelectDevice,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onSeek = onSeek,
                        onToggleShuffle = onToggleShuffle,
                        onToggleLike = handleToggleLike,
                        onToggleRepeat = onToggleRepeat,
                        onOpenDevices = handleOpenDevices,
                        onToggleLyrics = { layoutMode = MusicPlayerLayout.LYRICS },
                        onCycleLayout = { cycleLayout() },
                        onToggleVolume = toggleVolumePopup
                    )
                }
            }

            MusicPlayerLayout.VINYL -> {
                VinylStandbyView(
                    track = track,
                    progressFraction = progressFraction,
                    isLiked = isLiked,
                    onPlayPauseToggle = onPlayPauseToggle,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onSeek = onSeek,
                    onToggleLike = handleToggleLike,
                    onCycleLayout = { cycleLayout() },
                    onToggleVolume = toggleVolumePopup,
                    whiteTheme = whiteTheme
                )
            }

            MusicPlayerLayout.LYRICS -> {
                LyricsModeView(
                    track = track.copy(progressMs = interpolatedProgressMs),
                    lyrics = lyrics,
                    progressFraction = progressFraction,
                    isLandscape = isLandscape,
                    isLiked = isLiked,
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    dynamicThemeColor = themeAccent,
                    lyricsLayout = lyricsLayout,
                    onLyricsLayoutChanged = onLyricsLayoutChanged,
                    onSeekToTimestamp = onSeek,
                    onCloseLyrics = { layoutMode = MusicPlayerLayout.STANDARD },
                    onPlayPauseToggle = onPlayPauseToggle,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onToggleLike = handleToggleLike,
                    onToggleRepeat = onToggleRepeat,
                    onOpenDevices = handleOpenDevices,
                    activeDeviceName = activeDeviceName,
                    whiteTheme = whiteTheme
                )
            }

            MusicPlayerLayout.MINIMAL -> {
                MinimalStandbyView(
                    track = track,
                    progressFraction = progressFraction,
                    onCloseMinimal = { layoutMode = MusicPlayerLayout.STANDARD }
                )
            }

            MusicPlayerLayout.FULL_BLEED -> {
                FullBleedArtworkView(
                    track = track,
                    progressFraction = progressFraction,
                    isLiked = isLiked,
                    onPlayPauseToggle = onPlayPauseToggle,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onSeek = onSeek,
                    onToggleLike = handleToggleLike,
                    onCycleLayout = { cycleLayout() },
                    onToggleVolume = toggleVolumePopup
                )
            }
        }

        // ═══ Up Next Song Floating Corner Banner ═══
        val remainingMs = (track.durationMs - interpolatedProgressMs).coerceAtLeast(0L)
        val showUpNext = track.isPlaying && remainingMs in 1L..25000L && nextTrack != null

        UpNextBanner(
            visible = showUpNext,
            nextTrack = nextTrack,
            onSkipNext = onSkipNext,
            whiteTheme = whiteTheme,
            modifier = Modifier
                .align(if (isLandscape) Alignment.TopEnd else Alignment.TopCenter)
                .padding(top = if (isLandscape) 28.dp else 48.dp, end = if (isLandscape) 36.dp else 0.dp)
        )

        // ═══ Interactive Pop-up Volume Slider Bar ═══
        AnimatedVisibility(
            visible = showVolumePopup || volumeHudVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(if (isLandscape) Alignment.BottomEnd else Alignment.BottomCenter)
                .padding(end = if (isLandscape) 48.dp else 0.dp, bottom = 60.dp)
        ) {

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E212B).copy(alpha = 0.95f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .width(260.dp)
            ) {
                // Active Device Label (Spotify Connect indicator)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { handleOpenDevices() }
                    ) {
                        val isSpotifyActive = activeDeviceName.isNotBlank() || spotifyVolume != null
                        Icon(
                            imageVector = if (isSpotifyActive) Icons.Filled.Cast else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (isSpotifyActive) Color(0xFF1DB954) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (activeDeviceName.isNotBlank()) activeDeviceName.uppercase() else "DEVICE AUDIO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = if (isSpotifyActive) Color(0xFF1DB954) else Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "${(volumeProgress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (volumeProgress == 0f) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Slider(
                        value = volumeProgress,
                        onValueChange = { newVol ->
                            isDraggingSlider = true
                            lastVolumeInteractionTime = System.currentTimeMillis()
                            volumeProgress = newVol
                            val volPercent = (newVol * 100).toInt().coerceIn(0, 100)
                            if (spotifyVolume != null || activeDeviceName.isNotBlank()) {
                                pendingVolumeToSend = volPercent
                            } else {
                                audioManager?.let {
                                    val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    it.setStreamVolume(AudioManager.STREAM_MUSIC, (newVol * max).toInt(), 0)
                                }
                            }
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            lastVolumeInteractionTime = System.currentTimeMillis()
                            val finalVol = (volumeProgress * 100).toInt().coerceIn(0, 100)
                            if (spotifyVolume != null || activeDeviceName.isNotBlank()) {
                                pendingVolumeToSend = null
                                onSetSpotifyVolume(finalVol)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF1DB954),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ═══ Missing Scope Permission Reconnect Dialog ═══
        if (showScopeMissingDialog) {
            AlertDialog(
                onDismissRequest = { showScopeMissingDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Spotify Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (whiteTheme) Color(0xFF111827) else Color.White
                    )
                },
                text = {
                    Text(
                        text = "To save songs to your Liked Songs library, Spotify requires library permissions.\n\nTap 'Reconnect Spotify' to approve library access in Spotify (your active music won't stop playing).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (whiteTheme) Color(0xFF4B5563) else Color.White.copy(alpha = 0.8f)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showScopeMissingDialog = false
                            onConnectSpotify()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                    ) {
                        Text("Reconnect Spotify", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showScopeMissingDialog = false }) {
                        Text("Cancel", color = if (whiteTheme) Color.Black else Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = if (whiteTheme) Color.White else Color(0xFF1E212B),
                titleContentColor = if (whiteTheme) Color.Black else Color.White
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// SPOTIFY DEVICE BROADCAST MENU (Menu to select device to play on)
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun SpotifyDeviceBroadcastMenu(
    availableDevices: List<SpotifyDeviceDto>,
    activeDeviceName: String,
    onFetchDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onOpenFullDevicePicker: () -> Unit,
    whiteTheme: Boolean = false,
    iconTint: Color = Color.White.copy(alpha = 0.7f),
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = {
                onFetchDevices()
                expanded = true
            },
            modifier = Modifier.size(iconSize + 16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Cast,
                contentDescription = "Broadcast to Device",
                tint = if (activeDeviceName.isNotBlank()) Color(0xFF1DB954) else iconTint,
                modifier = Modifier.size(iconSize)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(if (whiteTheme) Color(0xFFF8FAFC) else Color(0xFF161924))
                .border(
                    1.dp,
                    if (whiteTheme) Color(0xFFCBD5E1) else Color.White.copy(alpha = 0.12f),
                    RoundedCornerShape(16.dp)
                )
                .widthIn(min = 240.dp, max = 300.dp)
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "SELECT PLAYBACK DEVICE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFF1DB954)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Cast,
                        contentDescription = null,
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {},
                enabled = false
            )

            HorizontalDivider(color = if (whiteTheme) Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.08f))

            if (availableDevices.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = "No Spotify devices found",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (whiteTheme) Color(0xFF334155) else Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Open Spotify on PC/phone to connect",
                                fontSize = 11.sp,
                                color = if (whiteTheme) Color(0xFF64748B) else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    },
                    onClick = { onFetchDevices() }
                )
            } else {
                availableDevices.forEach { dev ->
                    val isActive = dev.isActive ||
                            (activeDeviceName.isNotBlank() && dev.name.equals(activeDeviceName, ignoreCase = true))

                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = dev.name.ifBlank { "Unnamed Device" },
                                    fontSize = 13.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isActive) Color(0xFF1DB954) else if (whiteTheme) Color(0xFF0F172A) else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isActive) "Listening on this device" else "Play here (${dev.type.ifBlank { "Speaker" }})",
                                    fontSize = 10.sp,
                                    color = if (isActive) Color(0xFF1DB954) else if (whiteTheme) Color(0xFF64748B) else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = getDeviceIcon(dev.type),
                                contentDescription = dev.type,
                                tint = if (isActive) Color(0xFF1DB954) else if (whiteTheme) Color(0xFF64748B) else Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (isActive) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Active",
                                    tint = Color(0xFF1DB954),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        onClick = {
                            dev.id?.let { id -> onSelectDevice(id) }
                            expanded = false
                        }
                    )
                }
            }

            HorizontalDivider(color = if (whiteTheme) Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.08f))

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Scan for Devices",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (whiteTheme) Color(0xFF0F172A) else Color.White
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = if (whiteTheme) Color(0xFF64748B) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = { onFetchDevices() }
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = "Device Settings Dialog...",
                        fontSize = 12.sp,
                        color = Color(0xFF38BDF8)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.DevicesOther,
                        contentDescription = "More",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onOpenFullDevicePicker()
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 1. LANDSCAPE STANDARD VIEW (Spotify Standby Car View)
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun LandscapeStandardView(
    track: SpotifyTrack,
    progressFraction: Float,
    isLiked: Boolean,
    isShuffle: Boolean,
    repeatMode: Int,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepeat: () -> Unit,
    onOpenDevices: () -> Unit,
    onToggleLyrics: () -> Unit,
    onCycleLayout: () -> Unit,
    onToggleVolume: () -> Unit,
    activeDeviceName: String = "",
    availableDevices: List<SpotifyDeviceDto> = emptyList(),
    onFetchDevices: () -> Unit = {},
    onSelectDevice: (String) -> Unit = {},
    whiteTheme: Boolean = false
) {
    val textPrimary = if (whiteTheme) Color(0xFF111827) else Color.White
    val textSecondary = if (whiteTheme) Color(0xFF4B5563) else Color.White.copy(alpha = 0.72f)
    val textMuted = if (whiteTheme) Color(0xFF9CA3AF) else Color.White.copy(alpha = 0.65f)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Album Artwork with Floating Lyrics Button
        Box(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .aspectRatio(1f)
        ) {
            Crossfade(
                targetState = track.albumArtUrl,
                animationSpec = tween(600),
                label = "LandscapeAlbumArt"
            ) { artUrl ->
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artUrl)
                        .crossfade(true)
                        .crossfade(600)
                        .build(),
                    contentDescription = track.album,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (whiteTheme) Color(0xFFE2E8F0) else Color(0xFF1E1E1E))
                )
            }

            // Lyrics Toggle Button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onToggleLyrics() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.FormatQuote,
                    contentDescription = "Lyrics",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(44.dp))

        // Right: Track Metadata, Playback Controls, Progress & Actions
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Title & Artist with Layout Switcher button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title.ifBlank { "Unknown Track" },
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${track.artist.ifBlank { "Unknown Artist" }} • ${track.album}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onToggleLyrics) {
                        Icon(
                            imageVector = Icons.Filled.FormatQuote,
                            contentDescription = "Lyrics Mode",
                            tint = textMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onCycleLayout) {
                        Icon(
                            imageVector = Icons.Filled.DashboardCustomize,
                            contentDescription = "Switch Layout",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Progress Bar & Primary Playback Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InteractiveMusicProgressBar(
                    progressFraction = progressFraction,
                    durationMs = track.durationMs,
                    onSeek = onSeek,
                    activeColor = textPrimary,
                    inactiveColor = textPrimary.copy(alpha = 0.2f),
                    thumbColor = textPrimary
                )


                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(track.progressMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                        color = textMuted
                    )
                    val remainingMs = (track.durationMs - track.progressMs).coerceAtLeast(0L)
                    Text(
                        text = "-${formatTime(remainingMs)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 13.sp),
                        color = textMuted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = textPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(36.dp))

                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(textPrimary)
                    ) {
                        Crossfade(
                            targetState = track.isPlaying,
                            animationSpec = tween(180),
                            label = "StandardPlayPauseCrossfade"
                        ) { isPlaying ->
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (whiteTheme) Color.White else Color.Black,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(36.dp))

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next Track",
                            tint = textPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            // Secondary Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) Color(0xFF1DB954) else textMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like Track",
                        tint = if (isLiked) Color(0xFF1DB954) else textMuted,
                        modifier = Modifier.size(26.dp)
                    )
                }

                SpotifyDeviceBroadcastMenu(
                    availableDevices = availableDevices,
                    activeDeviceName = activeDeviceName,
                    onFetchDevices = onFetchDevices,
                    onSelectDevice = onSelectDevice,
                    onOpenFullDevicePicker = onOpenDevices,
                    whiteTheme = whiteTheme,
                    iconTint = textMuted,
                    iconSize = 24.dp
                )

                IconButton(onClick = onToggleRepeat) {
                    Icon(
                        imageVector = if (repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode > 0) Color(0xFF1DB954) else textMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onOpenDevices() }
                ) {
                    Icon(
                        imageVector = if (activeDeviceName.isNotBlank()) Icons.Filled.Cast else Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Active Device",
                        tint = if (activeDeviceName.isNotBlank()) Color(0xFF1DB954) else textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (activeDeviceName.isNotBlank()) "PLAYING ON: ${activeDeviceName.uppercase()}" else "DEVICES & AUDIO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.1.sp
                        ),
                        color = if (activeDeviceName.isNotBlank()) Color(0xFF1DB954) else textSecondary
                    )
                }

                IconButton(onClick = onToggleVolume) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 2. BESPOKE VERTICAL (PORTRAIT) MOBILE VIEW
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun PortraitStandardView(
    track: SpotifyTrack,
    progressFraction: Float,
    isLiked: Boolean,
    isShuffle: Boolean,
    repeatMode: Int,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepeat: () -> Unit,
    onOpenDevices: () -> Unit,
    onToggleLyrics: () -> Unit,
    onCycleLayout: () -> Unit,
    onToggleVolume: () -> Unit,
    activeDeviceName: String = "",
    availableDevices: List<SpotifyDeviceDto> = emptyList(),
    onFetchDevices: () -> Unit = {},
    onSelectDevice: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Top Playlist Context Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenDevices() }
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = track.contextHeader,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = track.playlistContext.ifBlank { "Liked Songs" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onToggleLyrics) {
                    Icon(
                        imageVector = Icons.Filled.FormatQuote,
                        contentDescription = "Lyrics Mode",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onCycleLayout) {
                    Icon(
                        imageVector = Icons.Filled.DashboardCustomize,
                        contentDescription = "Switch Layout",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 2. Square Album Art (Padded)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1E1E))
        ) {
            Crossfade(
                targetState = track.albumArtUrl,
                animationSpec = tween(600),
                label = "PortraitAlbumArt"
            ) { artUrl ->
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(artUrl)
                        .crossfade(true)
                        .crossfade(600)
                        .build(),
                    contentDescription = track.album,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. Track Details & Like Button + Broadcast Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.ifBlank { "Unknown Track" },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SpotifyDeviceBroadcastMenu(
                    availableDevices = availableDevices,
                    activeDeviceName = activeDeviceName,
                    onFetchDevices = onFetchDevices,
                    onSelectDevice = onSelectDevice,
                    onOpenFullDevicePicker = onOpenDevices,
                    whiteTheme = false,
                    iconTint = Color.White.copy(alpha = 0.85f),
                    iconSize = 24.dp
                )

                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFF1DB954) else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // 4. Progress Seek Bar
        Column(modifier = Modifier.fillMaxWidth()) {
            InteractiveMusicProgressBar(
                progressFraction = progressFraction,
                durationMs = track.durationMs,
                onSeek = onSeek,
                activeColor = Color.White,
                inactiveColor = Color.White.copy(alpha = 0.25f),
                thumbColor = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(track.progressMs),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                val remainingMs = (track.durationMs - track.progressMs).coerceAtLeast(0L)
                Text(
                    text = "-${formatTime(remainingMs)}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        // 5. Primary Playback Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) Color(0xFF1DB954) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = onPlayPauseToggle,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Crossfade(
                    targetState = track.isPlaying,
                    animationSpec = tween(180),
                    label = "PortraitPlayPauseCrossfade"
                ) { isPlaying ->
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            IconButton(
                onClick = onSkipNext,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = onToggleRepeat) {
                Icon(
                    imageVector = if (repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode > 0) Color(0xFF1DB954) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 6. Footer (Device indicator & Volume)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onOpenDevices() }
            ) {
                Icon(
                    imageVector = Icons.Filled.Cast,
                    contentDescription = null,
                    tint = if (activeDeviceName.isNotBlank()) Color(0xFF1DB954) else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (activeDeviceName.isNotBlank()) "PLAYING ON: ${activeDeviceName.uppercase()}" else "DEVICES & AUDIO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (activeDeviceName.isNotBlank()) Color(0xFF1DB954) else Color.White.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onToggleVolume) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Volume",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 3. VINYL TURNTABLE STANDBY VIEW
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun VinylStandbyView(
    track: SpotifyTrack,
    progressFraction: Float,
    isLiked: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onCycleLayout: () -> Unit,
    onToggleVolume: () -> Unit,
    whiteTheme: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "VinylRotation"
    )

    val isPlaying = track.isPlaying
    val textPrimary = if (whiteTheme) Color(0xFF111827) else Color.White
    val textSecondary = if (whiteTheme) Color(0xFF4B5563) else Color.White.copy(alpha = 0.72f)
    val textMuted = if (whiteTheme) Color(0xFF9CA3AF) else Color.White.copy(alpha = 0.65f)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Realistic Spinning Vinyl Record
        Box(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            // Vinyl Disc Outer Body - 60 FPS GPU hardware layer rotation
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (isPlaying) rotationAngle else 0f
                    }
                    .clip(CircleShape)
                    .background(Color(0xFF0C0D11))
                    .border(2.dp, Color(0xFF1F232F), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Microgroove rings Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val r = size.width / 2f
                    val ringFractions = listOf(0.92f, 0.84f, 0.76f, 0.68f, 0.60f)
                    ringFractions.forEach { frac ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.04f),
                            radius = r * frac,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
                        )
                    }
                }

                // Center Album Artwork Label Sticker
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFFE2E8F0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = track.albumArtUrl,
                        animationSpec = tween(600),
                        label = "VinylLabelArt"
                    ) { artUrl ->
                        val context = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(artUrl)
                                .crossfade(true)
                                .crossfade(600)
                                .build(),
                            contentDescription = track.album,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Center spindle hole
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0C0D11))
                            .border(1.5.dp, Color(0xFF94A3B8), CircleShape)
                    )
                }
            }

            // Standby Speed Badge "33⅓ RPM • STEREO"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "33⅓ RPM • HI-FI STEREO",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFFF59E0B)
                )
            }
        }

        Spacer(modifier = Modifier.width(44.dp))

        // Right Column: Title, Artist, Seekbar, Transport Controls
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Header Row with Layout Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title.ifBlank { "Unknown Track" },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${track.artist.ifBlank { "Unknown Artist" }} • ${track.album}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onCycleLayout) {
                    Icon(
                        imageVector = Icons.Filled.DashboardCustomize,
                        contentDescription = "Switch Layout",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Seekbar and Transport Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InteractiveMusicProgressBar(
                    progressFraction = progressFraction,
                    durationMs = track.durationMs,
                    onSeek = onSeek,
                    activeColor = textPrimary,
                    inactiveColor = textPrimary.copy(alpha = 0.2f),
                    thumbColor = textPrimary
                )


                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(track.progressMs),
                        fontSize = 13.sp,
                        color = textMuted
                    )
                    val remainingMs = (track.durationMs - track.progressMs).coerceAtLeast(0L)
                    Text(
                        text = "-${formatTime(remainingMs)}",
                        fontSize = 13.sp,
                        color = textMuted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = textPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(36.dp))

                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(textPrimary)
                    ) {
                        Crossfade(
                            targetState = track.isPlaying,
                            animationSpec = tween(180),
                            label = "VinylPlayPauseCrossfade"
                        ) { isPlaying ->
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = if (whiteTheme) Color.White else Color.Black,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(36.dp))

                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = textPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFF1DB954) else textMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onToggleVolume) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 4. FULL-BLEED ARTWORK STANDBY VIEW
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun FullBleedArtworkView(
    track: SpotifyTrack,
    progressFraction: Float,
    isLiked: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onCycleLayout: () -> Unit,
    onToggleVolume: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Edge-to-edge album artwork with 800ms crossfade
        val context = LocalContext.current
        Crossfade(
            targetState = track.albumArtUrl,
            animationSpec = tween(800),
            label = "FullBleedBackground"
        ) { artUrl ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artUrl)
                    .crossfade(true)
                    .crossfade(800)
                    .build(),
                contentDescription = track.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dark gradient overlay bottom & vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        // Top-right layout switcher
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
        ) {
            IconButton(
                onClick = onCycleLayout,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Filled.DashboardCustomize,
                    contentDescription = "Switch Layout",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Bottom floating playback container
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = track.title.ifBlank { "Unknown Track" },
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${track.artist.ifBlank { "Unknown Artist" }} • ${track.album}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(18.dp))

            InteractiveMusicProgressBar(
                progressFraction = progressFraction,
                durationMs = track.durationMs,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth(0.85f),
                activeColor = Color.White,
                inactiveColor = Color.White.copy(alpha = 0.3f),
                thumbColor = Color.White
            )


            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(track.progressMs),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
                val remainingMs = (track.durationMs - track.progressMs).coerceAtLeast(0L)
                Text(
                    text = "-${formatTime(remainingMs)}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleLike) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isLiked) Color(0xFF1DB954) else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                ) {
                    Crossfade(
                        targetState = track.isPlaying,
                        animationSpec = tween(180),
                        label = "FullBleedPlayPauseCrossfade"
                    ) { isPlaying ->
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = onToggleVolume) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// 5. SYNCHRONIZED LYRICS VIEW (5 Bespoke Layout Faces)
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun LyricsModeView(
    track: SpotifyTrack,
    lyrics: TrackLyrics?,
    progressFraction: Float,
    isLandscape: Boolean,
    onSeekToTimestamp: (Long) -> Unit,
    onCloseLyrics: () -> Unit,
    isLiked: Boolean = false,
    isShuffle: Boolean = false,
    repeatMode: Int = 0,
    dynamicThemeColor: Color? = null,
    lyricsLayout: Int = 0,
    onLyricsLayoutChanged: (Int) -> Unit = {},
    onPlayPauseToggle: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    activeDeviceName: String = "",
    whiteTheme: Boolean = false
) {
    val accent = dynamicThemeColor ?: if (whiteTheme) Color(0xFF0284C7) else Color(0xFF1DB954)
    val lyricsTextColor = if (whiteTheme) Color(0xFF111827) else Color.White
    val lyricsSubColor = if (whiteTheme) Color(0xFF4B5563) else Color.White.copy(alpha = 0.75f)

    val currentMode = remember(lyricsLayout) { LyricsLayoutMode.fromIndex(lyricsLayout) }
    var showLayoutSelector by remember { mutableStateOf(false) }

    if (showLayoutSelector) {
        LyricsLayoutSelectorDialog(
            currentLayoutIndex = lyricsLayout,
            onSelectLayout = {
                onLyricsLayoutChanged(it)
                showLayoutSelector = false
            },
            onDismiss = { showLayoutSelector = false },
            whiteTheme = whiteTheme
        )
    }

    if (isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 16.dp)
        ) {
            // Header Bar: Playlist Context + Layout Selector + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clickable { onCloseLyrics() }
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Playlist",
                        tint = lyricsTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = track.contextHeader,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                fontSize = 10.sp
                            ),
                            color = lyricsSubColor
                        )
                        Text(
                            text = track.playlistContext.ifBlank { "Liked Songs" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = lyricsTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenDevices,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (whiteTheme) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f))
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cast,
                            contentDescription = "Cast Devices",
                            tint = if (activeDeviceName.isNotBlank()) accent else lyricsTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { showLayoutSelector = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (whiteTheme) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f))
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DashboardCustomize,
                            contentDescription = "Lyrics Layout Mode",
                            tint = lyricsTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onCloseLyrics,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (whiteTheme) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f))
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Exit Lyrics",
                            tint = lyricsTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (currentMode) {
                LyricsLayoutMode.CLASSIC_SPLIT -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactLyricsPlayerCard(
                            track = track,
                            progressFraction = progressFraction,
                            isLiked = isLiked,
                            isShuffle = isShuffle,
                            repeatMode = repeatMode,
                            accent = accent,
                            lyricsTextColor = lyricsTextColor,
                            lyricsSubColor = lyricsSubColor,
                            whiteTheme = whiteTheme,
                            onPlayPauseToggle = onPlayPauseToggle,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleShuffle = onToggleShuffle,
                            onToggleLike = onToggleLike,
                            onToggleRepeat = onToggleRepeat,
                            onCloseLyrics = onCloseLyrics,
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.42f)
                        )

                        Spacer(modifier = Modifier.width(28.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.58f)
                        ) {
                            LyricsView(
                                lyrics = lyrics,
                                progressMs = track.progressMs,
                                whiteTheme = whiteTheme,
                                albumArtUrl = track.albumArtUrl,
                                trackTitle = track.title,
                                trackArtist = track.artist,
                                dynamicColor = dynamicThemeColor,
                                textAlign = TextAlign.Start,
                                onSeekToTimestamp = onSeekToTimestamp
                            )
                        }
                    }
                }

                LyricsLayoutMode.SWAPPED_SPLIT -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.58f)
                        ) {
                            LyricsView(
                                lyrics = lyrics,
                                progressMs = track.progressMs,
                                whiteTheme = whiteTheme,
                                albumArtUrl = track.albumArtUrl,
                                trackTitle = track.title,
                                trackArtist = track.artist,
                                dynamicColor = dynamicThemeColor,
                                textAlign = TextAlign.Start,
                                onSeekToTimestamp = onSeekToTimestamp
                            )
                        }

                        Spacer(modifier = Modifier.width(28.dp))

                        CompactLyricsPlayerCard(
                            track = track,
                            progressFraction = progressFraction,
                            isLiked = isLiked,
                            isShuffle = isShuffle,
                            repeatMode = repeatMode,
                            accent = accent,
                            lyricsTextColor = lyricsTextColor,
                            lyricsSubColor = lyricsSubColor,
                            whiteTheme = whiteTheme,
                            onPlayPauseToggle = onPlayPauseToggle,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleShuffle = onToggleShuffle,
                            onToggleLike = onToggleLike,
                            onToggleRepeat = onToggleRepeat,
                            onCloseLyrics = onCloseLyrics,
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.42f)
                        )
                    }
                }

                LyricsLayoutMode.TOP_BANNER -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        CompactLyricsBanner(
                            track = track,
                            progressFraction = progressFraction,
                            isLiked = isLiked,
                            accent = accent,
                            lyricsTextColor = lyricsTextColor,
                            lyricsSubColor = lyricsSubColor,
                            whiteTheme = whiteTheme,
                            onPlayPauseToggle = onPlayPauseToggle,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleLike = onToggleLike,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            LyricsView(
                                lyrics = lyrics,
                                progressMs = track.progressMs,
                                whiteTheme = whiteTheme,
                                albumArtUrl = track.albumArtUrl,
                                trackTitle = track.title,
                                trackArtist = track.artist,
                                dynamicColor = dynamicThemeColor,
                                textAlign = TextAlign.Center,
                                onSeekToTimestamp = onSeekToTimestamp
                            )
                        }
                    }
                }

                LyricsLayoutMode.FULLSCREEN_CENTER,
                LyricsLayoutMode.FULLSCREEN_LEFT -> {
                    val isCenter = currentMode == LyricsLayoutMode.FULLSCREEN_CENTER
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LyricsView(
                            lyrics = lyrics,
                            progressMs = track.progressMs,
                            whiteTheme = whiteTheme,
                            albumArtUrl = track.albumArtUrl,
                            trackTitle = track.title,
                            trackArtist = track.artist,
                            dynamicColor = dynamicThemeColor,
                            textAlign = if (isCenter) TextAlign.Center else TextAlign.Start,
                            onSeekToTimestamp = onSeekToTimestamp,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 60.dp)
                        )

                        FloatingLyricsPlayerBar(
                            track = track,
                            progressFraction = progressFraction,
                            isLiked = isLiked,
                            accent = accent,
                            lyricsTextColor = lyricsTextColor,
                            lyricsSubColor = lyricsSubColor,
                            whiteTheme = whiteTheme,
                            onPlayPauseToggle = onPlayPauseToggle,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleLike = onToggleLike,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }
    } else {
        // Vertical Portrait Mode
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Portrait Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.contextHeader,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = lyricsSubColor
                    )
                    Text(
                        text = track.playlistContext.ifBlank { "Liked Songs" },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = lyricsTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showLayoutSelector = true }) {
                        Icon(
                            imageVector = Icons.Filled.DashboardCustomize,
                            contentDescription = "Lyrics Layout Mode",
                            tint = lyricsTextColor
                        )
                    }
                    IconButton(onClick = onCloseLyrics) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = lyricsTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (currentMode) {
                LyricsLayoutMode.TOP_BANNER -> {
                    CompactLyricsBanner(
                        track = track,
                        progressFraction = progressFraction,
                        isLiked = isLiked,
                        accent = accent,
                        lyricsTextColor = lyricsTextColor,
                        lyricsSubColor = lyricsSubColor,
                        whiteTheme = whiteTheme,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onToggleLike = onToggleLike,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LyricsView(
                        lyrics = lyrics,
                        progressMs = track.progressMs,
                        whiteTheme = whiteTheme,
                        albumArtUrl = track.albumArtUrl,
                        trackTitle = track.title,
                        trackArtist = track.artist,
                        dynamicColor = dynamicThemeColor,
                        textAlign = TextAlign.Center,
                        onSeekToTimestamp = onSeekToTimestamp,
                        modifier = Modifier.weight(1f)
                    )
                }

                LyricsLayoutMode.FULLSCREEN_CENTER,
                LyricsLayoutMode.FULLSCREEN_LEFT -> {
                    val isCenter = currentMode == LyricsLayoutMode.FULLSCREEN_CENTER
                    Box(modifier = Modifier.weight(1f)) {
                        LyricsView(
                            lyrics = lyrics,
                            progressMs = track.progressMs,
                            whiteTheme = whiteTheme,
                            albumArtUrl = track.albumArtUrl,
                            trackTitle = track.title,
                            trackArtist = track.artist,
                            dynamicColor = dynamicThemeColor,
                            textAlign = if (isCenter) TextAlign.Center else TextAlign.Start,
                            onSeekToTimestamp = onSeekToTimestamp,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 64.dp)
                        )
                        FloatingLyricsPlayerBar(
                            track = track,
                            progressFraction = progressFraction,
                            isLiked = isLiked,
                            accent = accent,
                            lyricsTextColor = lyricsTextColor,
                            lyricsSubColor = lyricsSubColor,
                            whiteTheme = whiteTheme,
                            onPlayPauseToggle = onPlayPauseToggle,
                            onSkipNext = onSkipNext,
                            onSkipPrevious = onSkipPrevious,
                            onToggleLike = onToggleLike,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        )
                    }
                }

                else -> {
                    // CLASSIC_SPLIT & SWAPPED_SPLIT in Portrait: Compact banner on top, lyrics on bottom
                    CompactLyricsBanner(
                        track = track,
                        progressFraction = progressFraction,
                        isLiked = isLiked,
                        accent = accent,
                        lyricsTextColor = lyricsTextColor,
                        lyricsSubColor = lyricsSubColor,
                        whiteTheme = whiteTheme,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onSkipNext = onSkipNext,
                        onSkipPrevious = onSkipPrevious,
                        onToggleLike = onToggleLike,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LyricsView(
                        lyrics = lyrics,
                        progressMs = track.progressMs,
                        whiteTheme = whiteTheme,
                        albumArtUrl = track.albumArtUrl,
                        trackTitle = track.title,
                        trackArtist = track.artist,
                        dynamicColor = dynamicThemeColor,
                        textAlign = TextAlign.Start,
                        onSeekToTimestamp = onSeekToTimestamp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Left/Right Column Player Card for Split Lyrics layouts.
 */
@Composable
private fun CompactLyricsPlayerCard(
    track: SpotifyTrack,
    progressFraction: Float,
    isLiked: Boolean,
    isShuffle: Boolean,
    repeatMode: Int,
    accent: Color,
    lyricsTextColor: Color,
    lyricsSubColor: Color,
    whiteTheme: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleRepeat: () -> Unit,
    onCloseLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val availableH = maxHeight
        val artSize = if (availableH < 280.dp) 85.dp else if (availableH < 340.dp) 100.dp else 115.dp
        val titleSize = if (availableH < 280.dp) 16.sp else 18.sp
        val artistSize = if (availableH < 280.dp) 13.sp else 14.sp
        val albumSize = if (availableH < 280.dp) 10.sp else 11.sp
        val vSpacing = if (availableH < 280.dp) 3.dp else 6.dp
        val ctrlBtnSize = if (availableH < 280.dp) 28.dp else 32.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            // Album Cover with rounded corners + Persistent Lyrics Toggle Button
            Box(modifier = Modifier.size(artSize)) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.albumArtUrl)
                        .crossfade(true)
                        .crossfade(600)
                        .build(),
                    contentDescription = track.album,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (whiteTheme) Color(0xFFE2E8F0) else Color(0xFF1E1E1E))
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(if (artSize < 100.dp) 26.dp else 32.dp)
                        .clip(CircleShape)
                        .background(if (whiteTheme) Color(0xFFE0F2FE) else Color.Black.copy(alpha = 0.75f))
                        .border(1.5.dp, accent, CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onCloseLyrics() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatQuote,
                        contentDescription = "Exit Lyrics",
                        tint = accent,
                        modifier = Modifier.size(if (artSize < 100.dp) 13.dp else 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(vSpacing + 2.dp))

            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🎵",
                    fontSize = (titleSize.value - 2).sp,
                    modifier = Modifier.padding(end = 5.dp)
                )
                Text(
                    text = track.title.ifBlank { "Unknown Title" },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = titleSize
                    ),
                    color = lyricsTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(vSpacing))

            // Artist
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "👤",
                    fontSize = (artistSize.value - 2).sp,
                    modifier = Modifier.padding(end = 5.dp)
                )
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = artistSize,
                        fontWeight = FontWeight.Medium
                    ),
                    color = lyricsSubColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(vSpacing))

            // Album & Year
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "💿",
                    fontSize = (albumSize.value - 1).sp,
                    modifier = Modifier.padding(end = 5.dp)
                )
                val albumYear = if (track.releaseYear.isNotBlank()) {
                    "${track.album} • ${track.releaseYear}"
                } else {
                    track.album.ifBlank { "Album" }
                }
                Text(
                    text = albumYear,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = albumSize),
                    color = lyricsSubColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(vSpacing + 2.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.size(ctrlBtnSize)
                ) {
                    Text(text = if (isLiked) "❤️" else "🤍", fontSize = 16.sp)
                }

                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(ctrlBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) accent else lyricsSubColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(ctrlBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = lyricsTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier.size(ctrlBtnSize + 4.dp)
                ) {
                    Crossfade(
                        targetState = track.isPlaying,
                        animationSpec = tween(180),
                        label = "LyricsPlayPauseCrossfade"
                    ) { isPlaying ->
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(ctrlBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = lyricsTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier.size(ctrlBtnSize)
                ) {
                    Icon(
                        imageVector = if (repeatMode == 2) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode > 0) accent else lyricsSubColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onCloseLyrics,
                    modifier = Modifier.size(ctrlBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Exit Lyrics",
                        tint = lyricsSubColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(vSpacing))

            // Progress Bar & Timestamps
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(track.progressMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = lyricsSubColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = accent,
                    trackColor = if (whiteTheme) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatTime(track.durationMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = lyricsSubColor
                )
            }
        }
    }
}

/**
 * Top horizontal banner layout for LyricsLayoutMode.TOP_BANNER
 */
@Composable
private fun CompactLyricsBanner(
    track: SpotifyTrack,
    progressFraction: Float,
    isLiked: Boolean,
    accent: Color,
    lyricsTextColor: Color,
    lyricsSubColor: Color,
    whiteTheme: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bannerBg = if (whiteTheme) Color(0xFFF1F5F9) else Color(0xFF13151D).copy(alpha = 0.9f)
    val bannerBorder = if (whiteTheme) Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.12f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bannerBg)
            .border(1.dp, bannerBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.albumArtUrl)
                        .crossfade(true)
                        .crossfade(400)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title.ifBlank { "Unknown Title" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = lyricsTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist.ifBlank { "Unknown Artist" },
                        fontSize = 12.sp,
                        color = lyricsSubColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onToggleLike, modifier = Modifier.size(32.dp)) {
                    Text(text = if (isLiked) "❤️" else "🤍", fontSize = 14.sp)
                }
                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Prev",
                        tint = lyricsTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent)
                ) {
                    Icon(
                        imageVector = if (track.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onSkipNext, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = lyricsTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = if (whiteTheme) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.15f)
        )
    }
}

/**
 * Floating bottom playback pill for Fullscreen Lyrics layouts.
 */
@Composable
private fun FloatingLyricsPlayerBar(
    track: SpotifyTrack,
    progressFraction: Float,
    isLiked: Boolean,
    accent: Color,
    lyricsTextColor: Color,
    lyricsSubColor: Color,
    whiteTheme: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barBg = if (whiteTheme) Color.White.copy(alpha = 0.92f) else Color(0xFF13151D).copy(alpha = 0.88f)
    val barBorder = if (whiteTheme) Color(0xFFCBD5E1) else Color.White.copy(alpha = 0.15f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(barBg)
            .border(1.dp, barBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.albumArtUrl)
                        .crossfade(true)
                        .crossfade(300)
                        .build(),
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title.ifBlank { "Unknown Title" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = lyricsTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist.ifBlank { "Unknown Artist" },
                        fontSize = 11.sp,
                        color = lyricsSubColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onToggleLike, modifier = Modifier.size(28.dp)) {
                    Text(text = if (isLiked) "❤️" else "🤍", fontSize = 13.sp)
                }
                IconButton(onClick = onSkipPrevious, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Prev",
                        tint = lyricsTextColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accent)
                ) {
                    Icon(
                        imageVector = if (track.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onSkipNext, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = lyricsTextColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(2.5.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = if (whiteTheme) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.15f)
        )
    }
}


// ═══════════════════════════════════════════════════════════════════
// 4. MINIMAL STANDBY CLOCK VIEW (Desk Companion)
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun MinimalStandbyView(
    track: SpotifyTrack,
    progressFraction: Float,
    onCloseMinimal: () -> Unit
) {
    val currentTime = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onCloseMinimal() }
            .padding(48.dp)
    ) {
        // Hero Clock Centered
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-2).sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "${track.title} • ${track.artist}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .width(280.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }

        Text(
            text = "TAP ANYWHERE TO EXPAND",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = Color.White.copy(alpha = 0.4f),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Formats milliseconds into M:SS timestamp string */
private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

/**
 * Empty Standby State displayed when no media is actively playing or connected.
 * Adheres strictly to the OLED black Standby aesthetic without artificial cards or glass boxes.
 */
@Composable
fun MusicPageEmptyState(
    onConnectSpotify: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Subtle radial vignette
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF18221B).copy(alpha = 0.35f),
                            Color.Black
                        ),
                        radius = 800f
                    )
                )
        )

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Vinyl / Music Emblem
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141416)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E1E22)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(48.dp))

                // Action Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Standby Media Player",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nothing is currently playing. Start playback on Spotify Connect or tap below to authenticate.",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = Color.White.copy(alpha = 0.65f)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onConnectSpotify,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954),
                                contentColor = Color.Black
                            ),
                            shape = CircleShape,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Connect Spotify Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }

                        Button(
                            onClick = onOpenDevices,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1B1E2B),
                                contentColor = Color.White
                            ),
                            shape = CircleShape,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cast,
                                contentDescription = null,
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Select Device",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Portrait
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF141416)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Standby Media Player",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Nothing currently playing.\nConnect to Spotify or play on your PC.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConnectSpotify,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1DB954),
                            contentColor = Color.Black
                        ),
                        shape = CircleShape,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connect Spotify",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Button(
                        onClick = onOpenDevices,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1B1E2B),
                            contentColor = Color.White
                        ),
                        shape = CircleShape,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cast,
                            contentDescription = null,
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Devices",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// COMPOSE PREVIEWS
// ═══════════════════════════════════════════════════════════════════

@Preview(
    name = "1. Landscape Standard Preview",
    device = "spec:width=892dp,height=412dp,dpi=560,isRound=false",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
fun MusicPageLandscapePreview() {
    MasterCompanionTheme {
        MusicPage(
            track = SpotifyTrack(
                title = "Black and Yellow",
                artist = "Wiz Khalifa",
                album = "Rolling Papers",
                albumArtUrl = "https://i.scdn.co/image/ab67616d0000b2738b556f0827299a4e320f8c05",
                durationMs = 218000L,
                progressMs = 35000L,
                isPlaying = true
            )
        )
    }
}

@Preview(
    name = "2. Vertical Portrait Preview",
    device = "spec:width=412dp,height=892dp,dpi=560,isRound=false",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
fun MusicPagePortraitPreview() {
    MasterCompanionTheme {
        MusicPage(
            track = SpotifyTrack(
                title = "Black and Yellow",
                artist = "Wiz Khalifa",
                album = "Rolling Papers",
                albumArtUrl = "https://i.scdn.co/image/ab67616d0000b2738b556f0827299a4e320f8c05",
                durationMs = 218000L,
                progressMs = 35000L,
                isPlaying = true
            )
        )
    }
}

@Preview(
    name = "3. Synced Lyrics Mode Preview",
    device = "spec:width=892dp,height=412dp,dpi=560,isRound=false",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
fun MusicPageLyricsPreview() {
    MasterCompanionTheme {
        MusicPage(
            track = SpotifyTrack(
                title = "Intro",
                artist = "Sickick",
                album = "Talksick 3",
                releaseYear = "2016",
                playlistContext = "Liked Songs",
                albumArtUrl = "https://i.scdn.co/image/ab67616d0000b2734190c1cb279b940e4ba77579",
                durationMs = 294000L,
                progressMs = 51000L,
                isPlaying = true
            ),
            lyrics = TrackLyrics.MockSickickIntro,
            initialLayout = MusicPlayerLayout.LYRICS
        )
    }
}
