package com.mastercompanion.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import androidx.compose.ui.platform.LocalContext
import com.mastercompanion.data.update.UpdateStatus
import com.mastercompanion.ui.audio.AudioPage
import com.mastercompanion.ui.home.HomePage
import com.mastercompanion.ui.home.MusicPage
import com.mastercompanion.ui.home.MusicPlayerLayout
import com.mastercompanion.ui.settings.SettingsPage
import com.mastercompanion.ui.system.SystemPage
import com.mastercompanion.ui.home.SpotifyDevicePickerDialog
import com.mastercompanion.ui.theme.MasterCompanionTheme
import com.mastercompanion.ui.update.UpdateDialog
import kotlinx.coroutines.launch

/**
 * Multi-page HorizontalPager container enabling smooth swiping between:
 * - Page 0: Settings & Desk Control Hub (LEFTMOST PAGE)
 * - Page 1: Standby Clock (5 styles, 12h/24h) + Google Calendar & Latest Reminder + Battery Telemetry (HOME)
 * - Page 2: Dedicated Fullscreen Spotify Player (5 layouts: Standard, Vinyl, Lyrics, Minimal, Full-Bleed)
 * - Page 3: PC Low-Latency UDP Audio Receiver
 * - Page 4: System Status, Root Diagnostics & HTTP Command Logs
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardHost(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    // Initial page set to 1 (HomePage) with Settings 1 swipe to the left (Page 0)
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()

    // Observe state from ViewModel
    val batteryData by viewModel.batteryData.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val audioStreamState by viewModel.audioStreamState.collectAsStateWithLifecycle()
    val commandLogs by viewModel.commandLogs.collectAsStateWithLifecycle()
    val isRootAvailable by viewModel.isRootAvailable.collectAsStateWithLifecycle()

    val authToken by viewModel.authToken.collectAsStateWithLifecycle()
    val chargeStopThreshold by viewModel.chargeStopThreshold.collectAsStateWithLifecycle()
    val chargeResumeThreshold by viewModel.chargeResumeThreshold.collectAsStateWithLifecycle()
    val pcIp by viewModel.pcIp.collectAsStateWithLifecycle()
    val pcMac by viewModel.pcMac.collectAsStateWithLifecycle()
    val spotifyClientId by viewModel.spotifyClientId.collectAsStateWithLifecycle()
    val isSpotifyConnected by viewModel.isSpotifyConnected.collectAsStateWithLifecycle()

    val whiteTheme by viewModel.whiteTheme.collectAsStateWithLifecycle()
    val pixelShift by viewModel.pixelShift.collectAsStateWithLifecycle()
    val clockStyle by viewModel.clockStyle.collectAsStateWithLifecycle()
    val musicLayout by viewModel.musicLayout.collectAsStateWithLifecycle()
    val use24Hour by viewModel.use24HourFormat.collectAsStateWithLifecycle()
    val audioPassthroughEnabled by viewModel.audioPassthroughEnabled.collectAsStateWithLifecycle()
    val chargeLimitEnabled by viewModel.chargeLimitEnabled.collectAsStateWithLifecycle()
    val autoLaunchOnCharging by viewModel.autoLaunchOnCharging.collectAsStateWithLifecycle()
    val autoFullscreenClockEnabled by viewModel.autoFullscreenClockEnabled.collectAsStateWithLifecycle()
    val calendarEnabled by viewModel.calendarEnabled.collectAsStateWithLifecycle()
    val upcomingEvents by viewModel.upcomingEvents.collectAsStateWithLifecycle()
    val latestReminder by viewModel.latestReminder.collectAsStateWithLifecycle()
    val hasCalendarPermission by viewModel.hasCalendarPermission.collectAsStateWithLifecycle()
    val isServerRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
    val deviceIp by viewModel.deviceIp.collectAsStateWithLifecycle()
    val currentLyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val isLiked by viewModel.isCurrentTrackLiked.collectAsStateWithLifecycle()
    val isLibraryScopeMissing by viewModel.isLibraryScopeMissing.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val syncedAccount by viewModel.syncedAccount.collectAsStateWithLifecycle()
    val spotifyVolume by viewModel.spotifyVolume.collectAsStateWithLifecycle()
    val activeDeviceName by viewModel.activeDeviceName.collectAsStateWithLifecycle()
    val availableDevices by viewModel.availableDevices.collectAsStateWithLifecycle()
    val nextQueuedTrack by viewModel.nextQueuedTrack.collectAsStateWithLifecycle()
    val volumeHudVisible by viewModel.volumeHudVisible.collectAsStateWithLifecycle()

    val wolBroadcastIp by viewModel.wolBroadcastIp.collectAsStateWithLifecycle()
    val lyricsLayout by viewModel.lyricsLayout.collectAsStateWithLifecycle()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsStateWithLifecycle()
    val updateStatus by viewModel.updateStatus.collectAsStateWithLifecycle()

    var isFullscreenClock by remember { mutableStateOf(false) }
    var showHostDeviceDialog by remember { mutableStateOf(false) }

    if (showHostDeviceDialog) {
        SpotifyDevicePickerDialog(
            devices = availableDevices,
            activeDeviceName = activeDeviceName,
            onSelectDevice = { targetDeviceId ->
                viewModel.transferPlayback(targetDeviceId)
                showHostDeviceDialog = false
            },
            onRefresh = { viewModel.fetchAvailableDevices() },
            onDismiss = { showHostDeviceDialog = false },
            whiteTheme = whiteTheme
        )
    }

    val context = LocalContext.current
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.refreshCalendar()
        }
    }

    // Listen for remote navigate commands from AHK / HTTP bridge
    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { targetPage ->
            val clampedPage = targetPage.coerceIn(0, 4)
            pagerState.animateScrollToPage(clampedPage)
        }
    }

    MasterCompanionTheme(whiteTheme = whiteTheme) {
        val rootBg = if (whiteTheme) Color(0xFFF8F9FA) else Color.Black
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(rootBg)
        ) {
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> {
                        // ═══ Page 0: Leftmost Settings & Control Hub ═══
                        SettingsPage(
                            authToken = authToken,
                            chargeStopThreshold = chargeStopThreshold,
                            chargeResumeThreshold = chargeResumeThreshold,
                            pcIp = pcIp,
                            pcMac = pcMac,
                            wolBroadcastIp = wolBroadcastIp,
                            spotifyClientId = spotifyClientId,
                            isSpotifyConnected = isSpotifyConnected,
                            isLibraryScopeMissing = isLibraryScopeMissing,
                            whiteTheme = whiteTheme,
                            pixelShift = pixelShift,
                            isServerRunning = isServerRunning,
                            deviceIp = deviceIp,
                            use24Hour = use24Hour,
                            audioPassthroughEnabled = audioPassthroughEnabled,
                            chargeLimitEnabled = chargeLimitEnabled,
                            calendarEnabled = calendarEnabled,
                            hasCalendarPermission = hasCalendarPermission,
                            syncedAccount = syncedAccount,
                            clockStyleIndex = clockStyle,
                            autoLaunchOnCharging = autoLaunchOnCharging,
                            autoFullscreenClockEnabled = autoFullscreenClockEnabled,
                            autoCheckUpdates = autoCheckUpdates,
                            onSaveThresholds = { stop, resume -> viewModel.updateChargeThresholds(stop, resume) },
                            onRegenerateToken = { viewModel.regenerateAuthToken() },
                            onSavePcNetwork = { ip, mac -> viewModel.updatePcNetwork(ip, mac) },
                            onSaveWolBroadcastIp = { viewModel.updateWolBroadcastIp(it) },
                            onSaveSpotifyClientId = { viewModel.updateSpotifyClientId(it) },
                            onConnectSpotify = { viewModel.connectSpotify() },
                            onDisconnectSpotify = { viewModel.disconnectSpotify() },
                            onToggleWhiteTheme = { viewModel.setWhiteTheme(it) },
                            onTogglePixelShift = { viewModel.setPixelShift(it) },
                            onToggle24Hour = { viewModel.setUse24HourFormat(it) },
                            onToggleAudioPassthrough = { viewModel.setAudioPassthroughEnabled(it) },
                            onToggleChargeLimit = { viewModel.setChargeLimitEnabled(it) },
                            onToggleCalendar = { viewModel.setCalendarEnabled(it) },
                            onToggleAutoLaunchOnCharging = { viewModel.setAutoLaunchOnCharging(it) },
                            onToggleAutoFullscreenClock = { viewModel.setAutoFullscreenClock(it) },
                            onToggleAutoCheckUpdates = { viewModel.setAutoCheckUpdates(it) },
                            onCheckForUpdates = { viewModel.checkForUpdate() },
                            onClockStyleChanged = { viewModel.setClockStyle(it) },
                            onRequestCalendarPermission = {
                                calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                            },
                            onRefreshCalendar = { viewModel.triggerGoogleSync() },
                            onStartServer = { viewModel.startServer() },
                            onStopServer = { viewModel.stopServer() }
                        )
                    }
                    1 -> {
                        // ═══ Page 1: Standby Clock (5 styles, 12h/24h) + Google Calendar & Reminders + Battery ═══
                        HomePage(
                            batteryData = batteryData,
                            pcMac = pcMac,
                            pcIp = pcIp,
                            wolBroadcastIp = wolBroadcastIp,
                            whiteTheme = whiteTheme,
                            pixelShiftEnabled = pixelShift,
                            use24Hour = use24Hour,
                            calendarEnabled = calendarEnabled,
                            autoFullscreenClockEnabled = autoFullscreenClockEnabled,
                            initialClockStyle = clockStyle,
                            latestReminder = latestReminder,
                            upcomingEvents = upcomingEvents,
                            hasCalendarPermission = hasCalendarPermission,
                            syncedAccount = syncedAccount,
                            activeDeviceName = activeDeviceName,
                            onOpenDevices = {
                                viewModel.fetchAvailableDevices()
                                showHostDeviceDialog = true
                            },
                            onToggleChargeLimit = { viewModel.toggleChargeLimit() },
                            onSendWol = { mac, _, broadcastIp -> viewModel.sendWol(mac, broadcastIp) },
                            onSavePcNetwork = { ip, mac -> viewModel.updatePcNetwork(ip, mac) },
                            onSaveWolBroadcast = { viewModel.updateWolBroadcastIp(it) },
                            onClockStyleChanged = { viewModel.setClockStyle(it) },
                            onToggleCalendar = { viewModel.setCalendarEnabled(it) },
                            onFullscreenChanged = { isFullscreenClock = it },
                            onRequestCalendarPermission = {
                                calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                            },
                            onTriggerCalendarSync = { viewModel.triggerGoogleSync() },
                            onOpenCalendar = {
                                val intent = viewModel.getOpenCalendarIntent()
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                    2 -> {
                        // ═══ Page 2: Dedicated Spotify Standby Player (5 layouts + LRCLIB Lyrics) ═══
                        MusicPage(
                            track = currentTrack,
                            lyrics = currentLyrics,
                            nextTrack = nextQueuedTrack,
                            volumeHudVisible = volumeHudVisible,
                            isLiked = isLiked,
                            isShuffle = isShuffle,
                            repeatMode = repeatMode,
                            isLibraryScopeMissing = isLibraryScopeMissing,
                            whiteTheme = whiteTheme,
                            initialLayout = MusicPlayerLayout.values().getOrElse(musicLayout) { MusicPlayerLayout.STANDARD },
                            spotifyVolume = spotifyVolume,
                            activeDeviceName = activeDeviceName,
                            availableDevices = availableDevices,
                            onFetchDevices = { viewModel.fetchAvailableDevices() },
                            onSelectDevice = { viewModel.transferPlayback(it) },
                            lyricsLayout = lyricsLayout,
                            onLyricsLayoutChanged = { viewModel.setLyricsLayout(it) },
                            onSetSpotifyVolume = { viewModel.setSpotifyVolume(it) },
                            onPlayPauseToggle = { viewModel.togglePlayPause() },
                            onSkipNext = { viewModel.skipNext() },
                            onSkipPrevious = { viewModel.skipPrevious() },
                            onSeek = { viewModel.seekTo(it) },
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onToggleRepeat = { viewModel.toggleRepeat() },
                            onToggleLike = { viewModel.toggleLike() },
                            onConnectSpotify = { viewModel.connectSpotify() },
                            onLayoutChanged = { viewModel.setMusicLayout(it.ordinal) }
                        )
                    }
                    3 -> {
                        // ═══ Page 3: PC Low-Latency UDP Audio Receiver ═══
                        AudioPage(
                            streamState = audioStreamState,
                            deviceIp = deviceIp,
                            onVolumeChange = { viewModel.setAudioVolume(it) }
                        )
                    }
                    4 -> {
                        // ═══ Page 4: System Status, Root Diagnostics & HTTP Command Logs ═══
                        SystemPage(
                            isRooted = isRootAvailable,
                            commandLogs = commandLogs,
                            onTriggerTestCommand = { action -> viewModel.executeBridgeAction(action) }
                        )
                    }
                }
            }

            // ═══ Bottom Navigation Dots Indicator ═══
            AnimatedVisibility(
                visible = !(isFullscreenClock && pagerState.currentPage == 1),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dotActiveColor = if (whiteTheme) Color.Black.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.9f)
                    val dotInactiveColor = if (whiteTheme) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.25f)

                    repeat(5) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (isSelected) 22.dp else 6.dp,
                            label = "dot_width"
                        )

                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(if (isSelected) dotActiveColor else dotInactiveColor)
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        )
                    }
                }
            }

            // ═══ OTA Update Modal Dialog ═══
            if (updateStatus !is UpdateStatus.Idle) {
                UpdateDialog(
                    status = updateStatus,
                    onDownloadAndInstall = { url -> viewModel.downloadAndInstallUpdate(url) },
                    onDismiss = { viewModel.dismissUpdate() },
                    onCheckAgain = { viewModel.checkForUpdate() },
                    whiteTheme = whiteTheme
                )
            }
        }
    }
}


