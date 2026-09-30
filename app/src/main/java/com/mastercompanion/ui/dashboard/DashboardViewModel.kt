package com.mastercompanion.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercompanion.data.audio.AudioPlayer
import com.mastercompanion.data.battery.BatteryRepository
import com.mastercompanion.data.command.CommandExecutor
import com.mastercompanion.data.command.CommandLogEntry
import com.mastercompanion.data.prefs.PreferencesRepository
import com.mastercompanion.data.spotify.SpotifyAuthManager
import com.mastercompanion.data.spotify.SpotifyRepository
import com.mastercompanion.domain.model.AudioStreamState
import com.mastercompanion.domain.model.BatteryData
import com.mastercompanion.domain.model.CommandRequest
import com.mastercompanion.domain.model.SpotifyTrack
import com.mastercompanion.platform.root.RootShell
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryRepository: BatteryRepository,
    private val spotifyRepository: SpotifyRepository,
    private val audioPlayer: AudioPlayer,
    private val commandExecutor: CommandExecutor,
    private val preferencesRepository: PreferencesRepository,
    private val spotifyAuthManager: SpotifyAuthManager,
    private val rootShell: RootShell,
    private val lyricsRepository: com.mastercompanion.data.lyrics.LyricsRepository,
    private val commandBridgeServer: com.mastercompanion.server.CommandBridgeServer,
    private val calendarRepository: com.mastercompanion.data.calendar.CalendarRepository,
    private val updateRepository: com.mastercompanion.data.update.UpdateRepository
) : ViewModel() {

    // ═══ State Flows ═══
    val batteryData: StateFlow<BatteryData> = batteryRepository.batteryData
    val currentTrack: StateFlow<SpotifyTrack?> = spotifyRepository.currentTrack
    val audioStreamState: StateFlow<AudioStreamState> = audioPlayer.streamState
    val commandLogs: StateFlow<List<CommandLogEntry>> = commandExecutor.commandLogs
    val navigationEvents: SharedFlow<Int> = commandExecutor.navigationEvents
    val isServerRunning: StateFlow<Boolean> = commandBridgeServer.isRunning
    private val _isRootAvailable = MutableStateFlow(false)
    val isRootAvailable: StateFlow<Boolean> = _isRootAvailable.asStateFlow()

    private val _deviceIp = MutableStateFlow(resolveDeviceIp())
    val deviceIp: StateFlow<String> = _deviceIp.asStateFlow()

    private val _currentLyrics = MutableStateFlow<com.mastercompanion.domain.model.TrackLyrics?>(null)
    val currentLyrics: StateFlow<com.mastercompanion.domain.model.TrackLyrics?> = _currentLyrics.asStateFlow()
    private var lastLyricsKey: String? = null

    init {
        viewModelScope.launch {
            _isRootAvailable.value = rootShell.isRootAvailable()
        }
        viewModelScope.launch {
            currentTrack.collect { track ->
                if (track != null && track.title.isNotBlank() && track.artist.isNotBlank()) {
                    val key = "${track.artist.trim().lowercase()}_${track.title.trim().lowercase()}"
                    if (key != lastLyricsKey) {
                        lastLyricsKey = key
                        _currentLyrics.value = null
                        val fetched = lyricsRepository.getLyrics(
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            durationMs = track.durationMs
                        )
                        _currentLyrics.value = fetched
                    }
                } else {
                    lastLyricsKey = null
                    _currentLyrics.value = null
                }
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(3500)
            val shouldCheck = preferencesRepository.autoCheckUpdatesFlow.first()
            if (shouldCheck) {
                updateRepository.checkForUpdate()
            }
        }
    }

    val deviceRole: StateFlow<String> = preferencesRepository.deviceRoleFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, "UNSET")

    val authToken: StateFlow<String> = preferencesRepository.authTokenFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "master-companion-default-token")

    val chargeStopThreshold: StateFlow<Int> = preferencesRepository.chargeStopThresholdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 80)

    val chargeResumeThreshold: StateFlow<Int> = preferencesRepository.chargeResumeThresholdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 75)

    val pcIp: StateFlow<String> = preferencesRepository.pcIpFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "192.168.1.100")

    val pcMac: StateFlow<String> = preferencesRepository.pcMacFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "00:00:00:00:00:00")

    val spotifyClientId: StateFlow<String> = preferencesRepository.spotifyClientIdFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val isSpotifyConnected: StateFlow<Boolean> = preferencesRepository.spotifyAccessTokenFlow
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val whiteTheme: StateFlow<Boolean> = preferencesRepository.whiteThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val pixelShift: StateFlow<Boolean> = preferencesRepository.pixelShiftFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val clockStyle: StateFlow<Int> = preferencesRepository.clockStyleFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val musicLayout: StateFlow<Int> = preferencesRepository.musicLayoutFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val use24HourFormat: StateFlow<Boolean> = preferencesRepository.use24HourFormatFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val audioPassthroughEnabled: StateFlow<Boolean> = preferencesRepository.audioPassthroughEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val calendarEnabled: StateFlow<Boolean> = preferencesRepository.calendarEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val chargeLimitEnabled: StateFlow<Boolean> = preferencesRepository.chargeLimitEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoLaunchOnCharging: StateFlow<Boolean> = preferencesRepository.autoLaunchOnChargingFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoFullscreenClockEnabled: StateFlow<Boolean> = preferencesRepository.autoFullscreenClockFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val wolBroadcastIp: StateFlow<String> = preferencesRepository.wolBroadcastIpFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "192.168.1.255")

    val lyricsLayout: StateFlow<Int> = preferencesRepository.lyricsLayoutFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val autoCheckUpdates: StateFlow<Boolean> = preferencesRepository.autoCheckUpdatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val upcomingEvents: StateFlow<List<com.mastercompanion.domain.model.CalendarEvent>> = calendarRepository.events
    val latestReminder: StateFlow<com.mastercompanion.domain.model.CalendarEvent?> = calendarRepository.latestReminder
    val hasCalendarPermission: StateFlow<Boolean> = calendarRepository.hasPermission
    val syncedAccount: StateFlow<String?> = calendarRepository.syncedAccount

    fun refreshCalendar() {
        calendarRepository.refreshCalendar()
    }

    fun triggerGoogleSync() {
        calendarRepository.triggerGoogleSync()
    }

    fun getOpenCalendarIntent(): android.content.Intent {
        return calendarRepository.getOpenCalendarIntent()
    }

    fun setAutoLaunchOnCharging(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoLaunchOnCharging(enabled) }
    }

    fun setUse24HourFormat(use24: Boolean) {
        viewModelScope.launch { preferencesRepository.setUse24HourFormat(use24) }
    }

    fun setAudioPassthroughEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAudioPassthroughEnabled(enabled) }
    }

    fun setCalendarEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setCalendarEnabled(enabled) }
    }

    fun setChargeLimitEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setChargeLimitEnabled(enabled) }
    }

    fun setWhiteTheme(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setWhiteTheme(enabled) }
    }

    fun setPixelShift(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPixelShift(enabled) }
    }

    fun setAutoFullscreenClock(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoFullscreenClock(enabled) }
    }

    fun setClockStyle(styleIndex: Int) {
        viewModelScope.launch { preferencesRepository.setClockStyle(styleIndex) }
    }

    fun setMusicLayout(layoutIndex: Int) {
        viewModelScope.launch { preferencesRepository.setMusicLayout(layoutIndex) }
    }

    fun startServer() {
        commandBridgeServer.start(webPort = 8060, bridgePort = 8420)
    }

    fun stopServer() {
        commandBridgeServer.stop()
    }

    private fun resolveDeviceIp(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in java.util.Collections.list(interfaces)) {
                for (addr in java.util.Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.isNotBlank() && ip != "127.0.0.1") return ip
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    // ═══ Battery Actions ═══
    fun toggleChargeLimit() {
        viewModelScope.launch {
            batteryRepository.toggleChargeLimit()
        }
    }

    // ═══ Spotify Actions ═══
    fun togglePlayPause() {
        viewModelScope.launch {
            spotifyRepository.togglePlayPause()
        }
    }

    fun skipNext() {
        viewModelScope.launch {
            spotifyRepository.skipNext()
        }
    }

    fun skipPrevious() {
        viewModelScope.launch {
            spotifyRepository.skipPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch {
            spotifyRepository.seekTo(positionMs)
        }
    }

    val isCurrentTrackLiked: StateFlow<Boolean> = spotifyRepository.isCurrentTrackLiked
    val isLibraryScopeMissing: StateFlow<Boolean> = spotifyRepository.isLibraryScopeMissing
    val isShuffle: StateFlow<Boolean> = spotifyRepository.isShuffle
    val repeatMode: StateFlow<Int> = spotifyRepository.repeatMode
    val activeDeviceName: StateFlow<String> = spotifyRepository.activeDeviceName
    val spotifyVolume: StateFlow<Int?> = spotifyRepository.activeDeviceVolume
    val nextQueuedTrack: StateFlow<SpotifyTrack?> = spotifyRepository.nextQueuedTrack
    val availableDevices: StateFlow<List<com.mastercompanion.data.spotify.dto.SpotifyDeviceDto>> = spotifyRepository.availableDevices

    fun fetchAvailableDevices() {
        viewModelScope.launch {
            spotifyRepository.fetchAvailableDevices()
        }
    }

    fun transferPlayback(deviceId: String) {
        viewModelScope.launch {
            spotifyRepository.transferPlaybackToDevice(deviceId)
        }
    }

    private val _volumeHudVisible = MutableStateFlow(false)
    val volumeHudVisible: StateFlow<Boolean> = _volumeHudVisible.asStateFlow()

    private var volumeHudJob: kotlinx.coroutines.Job? = null

    fun triggerVolumeHud() {
        volumeHudJob?.cancel()
        _volumeHudVisible.value = true
        volumeHudJob = viewModelScope.launch {
            kotlinx.coroutines.delay(5000L)
            _volumeHudVisible.value = false
        }
    }

    fun setSpotifyVolume(volumePercent: Int) {
        viewModelScope.launch {
            spotifyRepository.setVolume(volumePercent)
        }
    }

    fun adjustSpotifyVolume(deltaPercent: Int) {
        viewModelScope.launch {
            spotifyRepository.adjustVolume(deltaPercent)
            triggerVolumeHud()
        }
    }


    fun toggleLike() {
        viewModelScope.launch {
            spotifyRepository.toggleLike()
        }
    }

    fun toggleShuffle() {
        viewModelScope.launch {
            spotifyRepository.toggleShuffle()
        }
    }

    fun toggleRepeat() {
        viewModelScope.launch {
            spotifyRepository.toggleRepeat()
        }
    }

    fun connectSpotify() {
        viewModelScope.launch {
            spotifyAuthManager.startAuthFlow()
        }
    }

    fun disconnectSpotify() {
        viewModelScope.launch {
            preferencesRepository.clearSpotifyTokens()
        }
    }

    // ═══ Audio Actions ═══
    fun setAudioVolume(volume: Float) {
        audioPlayer.setVolume(volume)
    }

    // ═══ Bridge Actions ═══
    fun sendWol(mac: String? = null, broadcastIp: String? = null) {
        viewModelScope.launch {
            val targetMac = mac?.takeIf { it.isNotBlank() } ?: pcMac.value
            val targetBroadcast = broadcastIp?.takeIf { it.isNotBlank() } ?: wolBroadcastIp.value
            commandExecutor.execute(
                CommandRequest(
                    action = "wol",
                    params = buildMap {
                        if (targetMac.isNotBlank()) put("mac", targetMac)
                        if (targetBroadcast.isNotBlank()) put("broadcast", targetBroadcast)
                    }
                )
            )
        }
    }

    fun updateWolBroadcastIp(broadcastIp: String) {
        viewModelScope.launch {
            preferencesRepository.setWolBroadcastIp(broadcastIp)
        }
    }

    fun setLyricsLayout(layoutIndex: Int) {
        viewModelScope.launch {
            preferencesRepository.setLyricsLayout(layoutIndex)
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoCheckUpdates(enabled)
        }
    }

    fun executeBridgeAction(action: String, params: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            commandExecutor.execute(CommandRequest(action, params))
        }
    }

    fun regenerateAuthToken() {
        viewModelScope.launch {
            preferencesRepository.generateNewAuthToken()
        }
    }

    fun updateChargeThresholds(stopAt: Int, resumeAt: Int) {
        viewModelScope.launch {
            preferencesRepository.setChargeThresholds(stopAt, resumeAt)
        }
    }

    fun updatePcNetwork(ip: String, mac: String) {
        viewModelScope.launch {
            preferencesRepository.setPcNetworkDetails(ip, mac)
        }
    }

    fun updateSpotifyClientId(clientId: String) {
        viewModelScope.launch {
            preferencesRepository.setSpotifyClientId(clientId)
        }
    }

    val updateStatus: StateFlow<com.mastercompanion.data.update.UpdateStatus> = updateRepository.updateStatus

    fun checkForUpdate() {
        viewModelScope.launch {
            updateRepository.checkForUpdate()
        }
    }

    fun downloadAndInstallUpdate(url: String) {
        viewModelScope.launch {
            updateRepository.downloadAndInstall(url)
        }
    }

    fun dismissUpdate() {
        updateRepository.dismissUpdate()
    }
}
