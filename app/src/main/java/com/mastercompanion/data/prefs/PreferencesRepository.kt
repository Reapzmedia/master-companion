package com.mastercompanion.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "master_companion_prefs")

@Singleton
class PreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // ═══ Keys ═══
    companion object {
        val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        val KEY_CHARGE_LIMIT_ENABLED = booleanPreferencesKey("charge_limit_enabled")
        val KEY_CHARGE_STOP_THRESHOLD = intPreferencesKey("charge_stop_threshold")
        val KEY_CHARGE_RESUME_THRESHOLD = intPreferencesKey("charge_resume_threshold")
        val KEY_PC_IP = stringPreferencesKey("pc_ip")
        val KEY_PC_MAC = stringPreferencesKey("pc_mac")
        val KEY_SPOTIFY_CLIENT_ID = stringPreferencesKey("spotify_client_id")
        val KEY_SPOTIFY_CODE_VERIFIER = stringPreferencesKey("spotify_code_verifier")
        val KEY_SPOTIFY_ACCESS_TOKEN = stringPreferencesKey("spotify_access_token")
        val KEY_SPOTIFY_REFRESH_TOKEN = stringPreferencesKey("spotify_refresh_token")
        val KEY_SPOTIFY_EXPIRES_AT = longPreferencesKey("spotify_expires_at")
        val KEY_SPOTIFY_SCOPES = stringPreferencesKey("spotify_scopes")
        val KEY_COMMAND_PORT = intPreferencesKey("command_port")
        val KEY_AUDIO_PORT = intPreferencesKey("audio_port")
        val KEY_WHITE_THEME = booleanPreferencesKey("white_theme")
        val KEY_PIXEL_SHIFT = booleanPreferencesKey("pixel_shift")
        val KEY_CLOCK_STYLE = intPreferencesKey("clock_style")
        val KEY_MUSIC_LAYOUT = intPreferencesKey("music_layout")
        val KEY_SERVER_ENABLED = booleanPreferencesKey("server_enabled")
        val KEY_USE_24_HOUR_FORMAT = booleanPreferencesKey("use_24_hour_format")
        val KEY_AUDIO_PASSTHROUGH_ENABLED = booleanPreferencesKey("audio_passthrough_enabled")
        val KEY_CALENDAR_ENABLED = booleanPreferencesKey("calendar_enabled")
        val KEY_AUTO_LAUNCH_ON_CHARGING = booleanPreferencesKey("auto_launch_on_charging")
        val KEY_AUTO_FULLSCREEN_CLOCK = booleanPreferencesKey("auto_fullscreen_clock")
        val KEY_WOL_BROADCAST_IP = stringPreferencesKey("wol_broadcast_ip")
        val KEY_LYRICS_LAYOUT = intPreferencesKey("lyrics_layout")
        val KEY_AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val KEY_LAST_TRACK_ID = stringPreferencesKey("last_track_id")
        val KEY_LAST_TRACK_TITLE = stringPreferencesKey("last_track_title")
        val KEY_LAST_TRACK_ARTIST = stringPreferencesKey("last_track_artist")
        val KEY_LAST_TRACK_ALBUM = stringPreferencesKey("last_track_album")
        val KEY_LAST_TRACK_ART_URL = stringPreferencesKey("last_track_art_url")
        val KEY_LAST_TRACK_DURATION_MS = longPreferencesKey("last_track_duration_ms")
        val KEY_DEVICE_ROLE = stringPreferencesKey("device_role")
        val KEY_VAULT_ID = stringPreferencesKey("vault_id")
        val KEY_VAULT_SHORT_KEY = stringPreferencesKey("vault_short_key")
        val KEY_LEGACY_KTOR_ENABLED = booleanPreferencesKey("legacy_ktor_enabled")
    }



    // ═══ Flows ═══
    val authTokenFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_AUTH_TOKEN] ?: "master-companion-default-token"
    }

    val chargeLimitEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CHARGE_LIMIT_ENABLED] ?: true
    }

    val chargeStopThresholdFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CHARGE_STOP_THRESHOLD] ?: 80
    }

    val chargeResumeThresholdFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CHARGE_RESUME_THRESHOLD] ?: 75
    }

    val pcIpFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_PC_IP] ?: "192.168.1.100"
    }

    val pcMacFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_PC_MAC] ?: "00:00:00:00:00:00"
    }

    val spotifyClientIdFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SPOTIFY_CLIENT_ID] ?: "5871091323a84960a2ee5b9d6cb2644f"
    }

    val spotifyAccessTokenFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SPOTIFY_ACCESS_TOKEN]
    }

    val spotifyRefreshTokenFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_SPOTIFY_REFRESH_TOKEN]
    }

    val spotifyExpiresAtFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_SPOTIFY_EXPIRES_AT] ?: 0L
    }

    val spotifyScopesFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SPOTIFY_SCOPES] ?: ""
    }

    val commandPortFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_COMMAND_PORT] ?: 8420
    }

    val audioPortFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_PORT] ?: 8421
    }

    val whiteThemeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_WHITE_THEME] ?: false
    }

    val pixelShiftFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PIXEL_SHIFT] ?: true
    }

    val clockStyleFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CLOCK_STYLE] ?: 0
    }

    val musicLayoutFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_MUSIC_LAYOUT] ?: 0
    }

    val serverEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_ENABLED] ?: true
    }

    val use24HourFormatFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_USE_24_HOUR_FORMAT] ?: true
    }

    val audioPassthroughEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_PASSTHROUGH_ENABLED] ?: true
    }

    val calendarEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_CALENDAR_ENABLED] ?: true
    }

    val autoLaunchOnChargingFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_LAUNCH_ON_CHARGING] ?: true
    }

    val autoFullscreenClockFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_FULLSCREEN_CLOCK] ?: true
    }

    // ═══ Updaters ═══
    suspend fun setAuthToken(token: String) {
        dataStore.edit { it[KEY_AUTH_TOKEN] = token }
    }

    suspend fun generateNewAuthToken(): String {
        val newToken = UUID.randomUUID().toString().replace("-", "")
        setAuthToken(newToken)
        return newToken
    }

    suspend fun setChargeLimitEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CHARGE_LIMIT_ENABLED] = enabled }
    }

    suspend fun setChargeThresholds(stopAt: Int, resumeAt: Int) {
        dataStore.edit {
            it[KEY_CHARGE_STOP_THRESHOLD] = stopAt
            it[KEY_CHARGE_RESUME_THRESHOLD] = resumeAt
        }
    }

    suspend fun setPcNetworkDetails(ip: String, mac: String) {
        dataStore.edit {
            it[KEY_PC_IP] = ip
            it[KEY_PC_MAC] = mac
        }
    }

    suspend fun setSpotifyClientId(clientId: String) {
        dataStore.edit { it[KEY_SPOTIFY_CLIENT_ID] = clientId }
    }

    suspend fun saveSpotifyTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long, scope: String? = null) {
        val expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000L)
        dataStore.edit { prefs ->
            prefs[KEY_SPOTIFY_ACCESS_TOKEN] = accessToken
            if (refreshToken != null) {
                prefs[KEY_SPOTIFY_REFRESH_TOKEN] = refreshToken
            }
            prefs[KEY_SPOTIFY_EXPIRES_AT] = expiresAt
            if (!scope.isNullOrBlank()) {
                prefs[KEY_SPOTIFY_SCOPES] = scope
            }
        }
    }

    suspend fun saveSpotifyCodeVerifier(verifier: String) {
        dataStore.edit { it[KEY_SPOTIFY_CODE_VERIFIER] = verifier }
    }

    suspend fun getSpotifyCodeVerifier(): String? {
        return dataStore.data.map { it[KEY_SPOTIFY_CODE_VERIFIER] }.firstOrNull()
    }

    suspend fun clearSpotifyTokens() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_SPOTIFY_ACCESS_TOKEN)
            prefs.remove(KEY_SPOTIFY_REFRESH_TOKEN)
            prefs.remove(KEY_SPOTIFY_EXPIRES_AT)
            prefs.remove(KEY_SPOTIFY_SCOPES)
        }
    }

    suspend fun setWhiteTheme(enabled: Boolean) {
        dataStore.edit { it[KEY_WHITE_THEME] = enabled }
    }

    suspend fun setPixelShift(enabled: Boolean) {
        dataStore.edit { it[KEY_PIXEL_SHIFT] = enabled }
    }

    suspend fun setClockStyle(styleIndex: Int) {
        dataStore.edit { it[KEY_CLOCK_STYLE] = styleIndex }
    }

    suspend fun setMusicLayout(layoutIndex: Int) {
        dataStore.edit { it[KEY_MUSIC_LAYOUT] = layoutIndex }
    }

    suspend fun setServerEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SERVER_ENABLED] = enabled }
    }

    suspend fun setUse24HourFormat(use24: Boolean) {
        dataStore.edit { it[KEY_USE_24_HOUR_FORMAT] = use24 }
    }

    suspend fun setAudioPassthroughEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_AUDIO_PASSTHROUGH_ENABLED] = enabled }
    }

    suspend fun setCalendarEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_CALENDAR_ENABLED] = enabled }
    }

    suspend fun setAutoLaunchOnCharging(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_LAUNCH_ON_CHARGING] = enabled }
    }

    suspend fun setAutoFullscreenClock(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_FULLSCREEN_CLOCK] = enabled }
    }

    val wolBroadcastIpFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_WOL_BROADCAST_IP] ?: "192.168.1.255"
    }

    val lyricsLayoutFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_LYRICS_LAYOUT] ?: 0
    }

    val autoCheckUpdatesFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_CHECK_UPDATES] ?: true
    }

    suspend fun setWolBroadcastIp(broadcastIp: String) {
        dataStore.edit { it[KEY_WOL_BROADCAST_IP] = broadcastIp.trim() }
    }

    suspend fun setLyricsLayout(layoutIndex: Int) {
        dataStore.edit { it[KEY_LYRICS_LAYOUT] = layoutIndex }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun saveLastTrack(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        artUrl: String?,
        durationMs: Long
    ) {
        dataStore.edit {
            it[KEY_LAST_TRACK_ID] = trackId
            it[KEY_LAST_TRACK_TITLE] = title
            it[KEY_LAST_TRACK_ARTIST] = artist
            it[KEY_LAST_TRACK_ALBUM] = album
            if (artUrl != null) it[KEY_LAST_TRACK_ART_URL] = artUrl
            it[KEY_LAST_TRACK_DURATION_MS] = durationMs
        }
    }

    suspend fun getLastTrack(): com.mastercompanion.domain.model.SpotifyTrack? {
        val prefs = dataStore.data.firstOrNull() ?: return null
        val id = prefs[KEY_LAST_TRACK_ID] ?: return null
        if (id.isBlank()) return null
        return com.mastercompanion.domain.model.SpotifyTrack(
            id = id,
            title = prefs[KEY_LAST_TRACK_TITLE] ?: "Unknown Track",
            artist = prefs[KEY_LAST_TRACK_ARTIST] ?: "Unknown Artist",
            album = prefs[KEY_LAST_TRACK_ALBUM] ?: "",
            albumArtUrl = prefs[KEY_LAST_TRACK_ART_URL],
            durationMs = prefs[KEY_LAST_TRACK_DURATION_MS] ?: 0L,
            progressMs = 0L,
            isPlaying = false,
            isRecentFallback = true,
            playlistContext = prefs[KEY_LAST_TRACK_ALBUM]?.ifBlank { "Liked Songs" } ?: "Liked Songs"
        )
    }

    val deviceRoleFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_ROLE] ?: "UNSET"
    }

    val vaultIdFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_VAULT_ID] ?: ""
    }

    val vaultShortKeyFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_VAULT_SHORT_KEY] ?: ""
    }

    val legacyKtorEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LEGACY_KTOR_ENABLED] ?: false
    }

    suspend fun setDeviceRole(role: String) {
        dataStore.edit { it[KEY_DEVICE_ROLE] = role }
    }

    suspend fun setVaultDetails(vaultId: String, shortKey: String) {
        dataStore.edit {
            it[KEY_VAULT_ID] = vaultId
            it[KEY_VAULT_SHORT_KEY] = shortKey
        }
    }

    suspend fun clearVault() {
        dataStore.edit {
            it.remove(KEY_VAULT_ID)
            it.remove(KEY_VAULT_SHORT_KEY)
            it[KEY_DEVICE_ROLE] = "UNSET"
        }
    }

    suspend fun setLegacyKtorEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LEGACY_KTOR_ENABLED] = enabled }
    }
}

