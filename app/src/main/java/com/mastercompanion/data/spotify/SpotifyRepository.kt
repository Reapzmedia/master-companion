package com.mastercompanion.data.spotify

import com.mastercompanion.data.prefs.PreferencesRepository
import com.mastercompanion.data.spotify.dto.SpotifyDeviceDto
import com.mastercompanion.data.spotify.dto.SpotifyTransferBody
import com.mastercompanion.di.DefaultDispatcher
import com.mastercompanion.domain.model.SpotifyTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpotifyRepository @Inject constructor(
    private val spotifyApi: SpotifyApi,
    private val authManager: SpotifyAuthManager,
    private val preferencesRepository: PreferencesRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(defaultDispatcher)

    // Empty initial state when no active Spotify session
    private val _currentTrack = MutableStateFlow<SpotifyTrack?>(null)
    val currentTrack: StateFlow<SpotifyTrack?> = _currentTrack.asStateFlow()

    private val _isCurrentTrackLiked = MutableStateFlow(false)
    val isCurrentTrackLiked: StateFlow<Boolean> = _isCurrentTrackLiked.asStateFlow()

    private val _activeDeviceName = MutableStateFlow("")
    val activeDeviceName: StateFlow<String> = _activeDeviceName.asStateFlow()

    private val _activeDeviceId = MutableStateFlow<String?>(null)
    val activeDeviceId: StateFlow<String?> = _activeDeviceId.asStateFlow()

    private val _activeDeviceVolume = MutableStateFlow<Int?>(null)
    val activeDeviceVolume: StateFlow<Int?> = _activeDeviceVolume.asStateFlow()

    private val _nextQueuedTrack = MutableStateFlow<SpotifyTrack?>(null)
    val nextQueuedTrack: StateFlow<SpotifyTrack?> = _nextQueuedTrack.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<SpotifyDeviceDto>>(emptyList())
    val availableDevices: StateFlow<List<SpotifyDeviceDto>> = _availableDevices.asStateFlow()

    private val _isLibraryScopeMissing = MutableStateFlow(false)
    val isLibraryScopeMissing: StateFlow<Boolean> = _isLibraryScopeMissing.asStateFlow()

    private var lastCheckedTrackId: String? = null

    init {
        scope.launch {
            // Restore last played track from storage if idle
            if (_currentTrack.value == null) {
                val saved = preferencesRepository.getLastTrack()
                if (saved != null && _currentTrack.value == null) {
                    _currentTrack.value = saved
                }
            }
        }
        scope.launch {
            preferencesRepository.spotifyScopesFlow.collect { scopes ->
                if (scopes.contains("user-library-modify") && scopes.contains("user-library-read")) {
                    _isLibraryScopeMissing.value = false
                    lastCheckedTrackId = null
                }
            }
        }
        scope.launch {
            startPollingLoop()
        }
    }

    private suspend fun startPollingLoop() {
        while (scope.isActive) {
            val bearerToken = authManager.getValidBearerToken()

            if (bearerToken != null) {
                refreshPlaybackState(bearerToken)
            } else {
                _currentTrack.value = null
                _isCurrentTrackLiked.value = false
                _activeDeviceName.value = ""
                _activeDeviceVolume.value = null
                _nextQueuedTrack.value = null
            }

            // Adaptive polling rate: 1.5s if playing, 4s if idle/disconnected
            val delayMs = if (_currentTrack.value?.isPlaying == true) 1500L else 4000L
            delay(delayMs)
        }
    }

    private suspend fun refreshPlaybackState(bearerToken: String) {
        try {
            val response = spotifyApi.getPlaybackState(bearerToken)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                // Sync active playing device info & volume
                body.device?.let { dev ->
                    _activeDeviceName.value = dev.name
                    if (!dev.id.isNullOrBlank()) {
                        _activeDeviceId.value = dev.id
                    }
                    if (dev.volumePercent != null) {
                        _activeDeviceVolume.value = dev.volumePercent
                    }
                }

                // Sync shuffle & repeat states directly from active player
                body.shuffleState?.let { shuffle ->
                    _isShuffle.value = shuffle
                }
                body.repeatState?.let { rep ->
                    _repeatMode.value = when (rep.lowercase()) {
                        "track" -> 2
                        "context" -> 1
                        else -> 0
                    }
                }

                val item = body.item
                if (item != null) {
                    val artUrl = item.album?.images?.firstOrNull()?.url
                    val artistName = item.artists.joinToString(", ") { it.name }
                    val year = item.album?.releaseDate?.take(4) ?: ""

                    val (contextName, resolvedContextType) = resolveContext(body.context, item, bearerToken)

                    if (_currentTrack.value?.id != item.id) {
                        _nextQueuedTrack.value = null
                    }

                    _currentTrack.value = SpotifyTrack(
                        id = item.id,
                        title = item.name,
                        artist = artistName,
                        album = item.album?.name ?: "",
                        albumArtUrl = artUrl,
                        durationMs = item.durationMs,
                        progressMs = body.progressMs,
                        isPlaying = body.isPlaying,
                        isRecentFallback = false,
                        playlistContext = contextName,
                        contextType = resolvedContextType,
                        releaseYear = year,
                        timestamp = System.currentTimeMillis()
                    )

                    // Save as last track for future offline / standby starts
                    preferencesRepository.saveLastTrack(
                        trackId = item.id,
                        title = item.name,
                        artist = artistName,
                        album = item.album?.name ?: "",
                        artUrl = artUrl,
                        durationMs = item.durationMs
                    )

                    // Check if track is nearing its end (<= 25s remaining) -> fetch queue for Up-Next banner
                    val remainingMs = item.durationMs - body.progressMs
                    if (body.isPlaying && remainingMs in 1L..25000L && _nextQueuedTrack.value == null) {
                        fetchNextQueuedTrack(bearerToken)
                    }

                    checkLikedStatus(item.id, bearerToken)
                } else {
                    // Device connected/active but no queue, check recently played
                    fetchRecentlyPlayed(bearerToken)
                }
            } else if (response.code() == 204 || response.body() == null) {
                // Nothing actively playing, check recently played
                _activeDeviceVolume.value = null
                _activeDeviceName.value = ""
                fetchRecentlyPlayed(bearerToken)
            }
        } catch (e: Exception) {
            Timber.d("Polling Spotify playback: ${e.message}")
        }
    }


    private val playlistNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private suspend fun resolveContext(
        contextDto: com.mastercompanion.data.spotify.dto.SpotifyContextDto?,
        item: com.mastercompanion.data.spotify.dto.TrackDto,
        bearerToken: String
    ): Pair<String, String> {
        if (contextDto == null) {
            return Pair("Liked Songs", "collection")
        }

        val type = contextDto.type.lowercase().trim()
        val uri = contextDto.uri.trim()

        return when {
            type == "album" -> {
                val albumName = item.album?.name?.ifBlank { "Album" } ?: "Album"
                Pair(albumName, "album")
            }
            type == "artist" -> {
                val artistName = item.artists.firstOrNull()?.name?.ifBlank { "Artist" } ?: "Artist"
                Pair(artistName, "artist")
            }
            type == "collection" || uri.contains(":collection") || (uri.contains(":user:") && uri.endsWith(":collection")) -> {
                Pair("Liked Songs", "collection")
            }
            type == "show" || type == "episode" -> {
                val showName = item.album?.name?.ifBlank { "Podcast" } ?: "Podcast"
                Pair(showName, type)
            }
            type == "playlist" || uri.contains(":playlist:") -> {
                val playlistId = uri.substringAfterLast(":").substringBefore("?")
                if (playlistId.isNotBlank()) {
                    val cached = playlistNameCache[playlistId]
                    if (!cached.isNullOrBlank()) {
                        Pair(cached, "playlist")
                    } else {
                        val fetched = try {
                            val resp = spotifyApi.getPlaylist(bearerToken, playlistId)
                            if (resp.isSuccessful && !resp.body()?.name.isNullOrBlank()) {
                                val name = resp.body()!!.name
                                playlistNameCache[playlistId] = name
                                name
                            } else null
                        } catch (e: Exception) {
                            Timber.d("Failed to resolve playlist name for $playlistId: ${e.message}")
                            null
                        }
                        Pair(fetched ?: playlistNameCache[playlistId] ?: "Playlist", "playlist")
                    }
                } else {
                    Pair("Playlist", "playlist")
                }
            }
            else -> {
                val fallback = item.album?.name?.ifBlank { "Liked Songs" } ?: "Liked Songs"
                Pair(fallback, if (type.isNotBlank()) type else "playlist")
            }
        }
    }

    private suspend fun fetchRecentlyPlayed(bearerToken: String) {
        try {
            val recentResp = spotifyApi.getRecentlyPlayed(bearerToken, limit = 1)
            if (recentResp.isSuccessful && recentResp.body()?.items?.isNotEmpty() == true) {
                val item = recentResp.body()!!.items.first().track
                val artUrl = item.album?.images?.firstOrNull()?.url
                val artistName = item.artists.joinToString(", ") { it.name }
                val year = item.album?.releaseDate?.take(4) ?: ""
                val contextName = item.album?.name?.ifBlank { "Liked Songs" } ?: "Liked Songs"
                val resolvedContextType = if (item.album?.name.isNullOrBlank()) "playlist" else "album"

                val track = SpotifyTrack(
                    id = item.id,
                    title = item.name,
                    artist = artistName,
                    album = item.album?.name ?: "",
                    albumArtUrl = artUrl,
                    durationMs = item.durationMs,
                    progressMs = 0L,
                    isPlaying = false,
                    isRecentFallback = true,
                    playlistContext = contextName,
                    contextType = resolvedContextType,
                    releaseYear = year
                )
                _currentTrack.value = track
                preferencesRepository.saveLastTrack(
                    trackId = item.id,
                    title = item.name,
                    artist = artistName,
                    album = item.album?.name ?: "",
                    artUrl = artUrl,
                    durationMs = item.durationMs
                )
            } else {
                // Try user's saved tracks from library
                val savedResp = try {
                    spotifyApi.getUserSavedTracks(bearerToken, limit = 1)
                } catch (e: Exception) { null }

                if (savedResp != null && savedResp.isSuccessful && savedResp.body()?.items?.isNotEmpty() == true) {
                    val item = savedResp.body()!!.items.first().track
                    val artUrl = item.album?.images?.firstOrNull()?.url
                    val artistName = item.artists.joinToString(", ") { it.name }
                    val year = item.album?.releaseDate?.take(4) ?: ""

                    val track = SpotifyTrack(
                        id = item.id,
                        title = item.name,
                        artist = artistName,
                        album = item.album?.name ?: "",
                        albumArtUrl = artUrl,
                        durationMs = item.durationMs,
                        progressMs = 0L,
                        isPlaying = false,
                        isRecentFallback = true,
                        playlistContext = "Liked Songs",
                        contextType = "collection",
                        releaseYear = year
                    )
                    _currentTrack.value = track
                    preferencesRepository.saveLastTrack(
                        trackId = item.id,
                        title = item.name,
                        artist = artistName,
                        album = item.album?.name ?: "",
                        artUrl = artUrl,
                        durationMs = item.durationMs
                    )
                } else if (_currentTrack.value == null) {
                    _currentTrack.value = preferencesRepository.getLastTrack()
                }
            }
        } catch (e: Exception) {
            Timber.d("Error fetching recently played: ${e.message}")
            if (_currentTrack.value == null) {
                _currentTrack.value = preferencesRepository.getLastTrack()
            }
        }
    }

    private suspend fun resolveTargetDeviceId(bearerToken: String): String? {
        val curId = _activeDeviceId.value
        if (!curId.isNullOrBlank()) return curId

        return try {
            val devResp = spotifyApi.getAvailableDevices(bearerToken)
            if (devResp.isSuccessful) {
                val devs = devResp.body()?.devices.orEmpty()
                _availableDevices.value = devs
                val active = devs.firstOrNull { it.isActive }?.id
                val first = devs.firstOrNull()?.id
                val target = active ?: first
                if (target != null) {
                    _activeDeviceId.value = target
                }
                target
            } else null
        } catch (e: Exception) {
            Timber.d("Failed to resolve active Spotify device: ${e.message}")
            null
        }
    }

    private suspend fun fetchNextQueuedTrack(bearerToken: String) {
        try {
            val queueResp = spotifyApi.getQueue(bearerToken)
            if (queueResp.isSuccessful && queueResp.body() != null) {
                val nextItem = queueResp.body()!!.queue.firstOrNull()
                if (nextItem != null && nextItem.id.isNotBlank() && nextItem.id != _currentTrack.value?.id) {
                    _nextQueuedTrack.value = SpotifyTrack(
                        id = nextItem.id,
                        title = nextItem.name,
                        artist = nextItem.artists.joinToString(", ") { it.name },
                        album = nextItem.album?.name ?: "",
                        albumArtUrl = nextItem.album?.images?.firstOrNull()?.url,
                        durationMs = nextItem.durationMs,
                        progressMs = 0L,
                        isPlaying = false
                    )
                    Timber.d("Fetched upcoming next track: ${nextItem.name}")
                }
            }
        } catch (e: Exception) {
            Timber.d("Error fetching Spotify queue: ${e.message}")
        }
    }

    suspend fun togglePlayPause(): Result<Unit> {
        val current = _currentTrack.value ?: return Result.success(Unit)
        val bearerToken = authManager.getValidBearerToken()
        if (bearerToken == null) {
            // Local state toggle if not logged in
            _currentTrack.value = current.copy(isPlaying = !current.isPlaying)
            return Result.success(Unit)
        }

        val deviceId = resolveTargetDeviceId(bearerToken)

        return try {
            val resp = if (current.isPlaying) {
                spotifyApi.pause(bearerToken, deviceId = deviceId)
            } else {
                // If it's a fallback or paused track with 0 progress, specify track URI to play!
                if (current.isRecentFallback || current.progressMs <= 0L) {
                    val playBody = com.mastercompanion.data.spotify.dto.SpotifyPlayBody(
                        uris = listOf("spotify:track:${current.id}")
                    )
                    var r = spotifyApi.play(bearerToken, deviceId = deviceId, body = playBody)
                    if (!r.isSuccessful) {
                        // Fallback: try play without body
                        r = spotifyApi.play(bearerToken, deviceId = deviceId)
                    }
                    r
                } else {
                    var r = spotifyApi.play(bearerToken, deviceId = deviceId)
                    if (!r.isSuccessful && (r.code() == 404 || r.code() == 400)) {
                        val playBody = com.mastercompanion.data.spotify.dto.SpotifyPlayBody(
                            uris = listOf("spotify:track:${current.id}"),
                            positionMs = current.progressMs
                        )
                        r = spotifyApi.play(bearerToken, deviceId = deviceId, body = playBody)
                    }
                    r
                }
            }
            if (resp.isSuccessful) {
                _currentTrack.value = current.copy(isPlaying = !current.isPlaying)
                Result.success(Unit)
            } else {
                Timber.w("Spotify playback error: code ${resp.code()}")
                Result.failure(RuntimeException("Spotify playback error ${resp.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception in togglePlayPause")
            Result.failure(e)
        }
    }

    suspend fun skipNext(): Result<Unit> {
        val bearerToken = authManager.getValidBearerToken() ?: return Result.success(Unit)
        val deviceId = resolveTargetDeviceId(bearerToken)
        return try {
            val resp = spotifyApi.next(bearerToken, deviceId = deviceId)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Skip next failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun skipPrevious(): Result<Unit> {
        val bearerToken = authManager.getValidBearerToken() ?: return Result.success(Unit)
        val deviceId = resolveTargetDeviceId(bearerToken)
        return try {
            val resp = spotifyApi.previous(bearerToken, deviceId = deviceId)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Skip previous failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun seekTo(positionMs: Long): Result<Unit> {
        _currentTrack.value = _currentTrack.value?.copy(progressMs = positionMs)
        val bearerToken = authManager.getValidBearerToken() ?: return Result.success(Unit)
        val deviceId = resolveTargetDeviceId(bearerToken)
        return try {
            val resp = spotifyApi.seek(bearerToken, positionMs, deviceId = deviceId)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Seek failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun checkLikedStatus(trackId: String, bearerToken: String) {
        if (trackId.isBlank()) return
        if (trackId == lastCheckedTrackId) return
        try {
            val trackUri = "spotify:track:$trackId"
            var isLiked = false
            var checkSuccessful = false

            // 1. Try modern /v1/me/library/contains
            val libResp = try {
                spotifyApi.checkLibraryContains(bearerToken, trackUri)
            } catch (e: Exception) { null }

            if (libResp != null && libResp.isSuccessful) {
                isLiked = libResp.body()?.firstOrNull() ?: false
                checkSuccessful = true
                _isLibraryScopeMissing.value = false
            } else {
                // 2. Fallback to /v1/me/tracks/contains
                val tracksResp = try {
                    spotifyApi.checkUserSavedTracks(bearerToken, trackId)
                } catch (e: Exception) { null }

                if (tracksResp != null && tracksResp.isSuccessful) {
                    isLiked = tracksResp.body()?.firstOrNull() ?: false
                    checkSuccessful = true
                    _isLibraryScopeMissing.value = false
                } else if (libResp?.code() == 403 && tracksResp?.code() == 403) {
                    _isLibraryScopeMissing.value = true
                }
            }

            if (checkSuccessful) {
                lastCheckedTrackId = trackId
                _isCurrentTrackLiked.value = isLiked
            }
        } catch (e: Exception) {
            Timber.d("Error checking liked status: ${e.message}")
        }
    }

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(0) // 0 = off, 1 = context, 2 = track
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    suspend fun toggleShuffle(): Result<Unit> {
        val next = !_isShuffle.value
        _isShuffle.value = next
        val bearerToken = authManager.getValidBearerToken() ?: return Result.success(Unit)
        val deviceId = resolveTargetDeviceId(bearerToken)
        return try {
            val resp = spotifyApi.setShuffle(bearerToken, next, deviceId = deviceId)
            if (resp.isSuccessful) {
                Timber.i("Successfully toggled Spotify shuffle to $next")
                Result.success(Unit)
            } else {
                Timber.w("Spotify shuffle returned ${resp.code()}")
                if (resp.code() == 403) {
                    _isShuffle.value = !next // Revert on restriction
                }
                Result.failure(RuntimeException("Shuffle error ${resp.code()}"))
            }
        } catch (e: Exception) {
            _isShuffle.value = !next
            Result.failure(e)
        }
    }

    suspend fun toggleRepeat(): Result<Unit> {
        val currentMode = _repeatMode.value
        val nextMode = (currentMode + 1) % 3
        _repeatMode.value = nextMode
        val stateStr = when (nextMode) {
            1 -> "context"
            2 -> "track"
            else -> "off"
        }
        val bearerToken = authManager.getValidBearerToken() ?: return Result.success(Unit)
        val deviceId = resolveTargetDeviceId(bearerToken)
        return try {
            val resp = spotifyApi.setRepeat(bearerToken, stateStr, deviceId = deviceId)
            if (resp.isSuccessful) {
                Timber.i("Successfully toggled Spotify repeat to $stateStr")
                Result.success(Unit)
            } else {
                Timber.w("Spotify repeat returned ${resp.code()}")
                if (resp.code() == 403) {
                    _repeatMode.value = currentMode // Revert on restriction
                }
                Result.failure(RuntimeException("Repeat error ${resp.code()}"))
            }
        } catch (e: Exception) {
            _repeatMode.value = currentMode
            Result.failure(e)
        }
    }

    suspend fun toggleLike(): Result<Unit> {
        val track = _currentTrack.value ?: return Result.success(Unit)
        val trackId = track.id.takeIf { it.isNotBlank() } ?: return Result.success(Unit)
        val currentlyLiked = _isCurrentTrackLiked.value
        val newLiked = !currentlyLiked

        val bearerToken = authManager.getValidBearerToken()
        if (bearerToken == null) {
            return Result.failure(IllegalStateException("Not logged in to Spotify"))
        }

        // Optimistic instant UI update
        _isCurrentTrackLiked.value = newLiked

        return try {
            val trackUri = "spotify:track:$trackId"
            var success = false
            var bothForbidden = false

            if (newLiked) {
                // 1. Try modern PUT /v1/me/library
                val r1 = try {
                    spotifyApi.saveToLibrary(bearerToken, trackUri)
                } catch (e: Exception) { null }

                if (r1 != null && r1.isSuccessful) {
                    success = true
                } else {
                    // 2. Fallback to PUT /v1/me/tracks
                    val r2 = try {
                        spotifyApi.saveTrack(bearerToken, trackId)
                    } catch (e: Exception) { null }

                    if (r2 != null && r2.isSuccessful) {
                        success = true
                    } else if (r1?.code() == 403 && r2?.code() == 403) {
                        bothForbidden = true
                    }
                }
            } else {
                // 1. Try modern DELETE /v1/me/library
                val r1 = try {
                    spotifyApi.removeFromLibrary(bearerToken, trackUri)
                } catch (e: Exception) { null }

                if (r1 != null && r1.isSuccessful) {
                    success = true
                } else {
                    // 2. Fallback to DELETE /v1/me/tracks
                    val r2 = try {
                        spotifyApi.removeTrack(bearerToken, trackId)
                    } catch (e: Exception) { null }

                    if (r2 != null && r2.isSuccessful) {
                        success = true
                    } else if (r1?.code() == 403 && r2?.code() == 403) {
                        bothForbidden = true
                    }
                }
            }

            if (success) {
                Timber.i("Successfully toggled like for $trackId to $newLiked")
                lastCheckedTrackId = trackId
                _isCurrentTrackLiked.value = newLiked
                _isLibraryScopeMissing.value = false
                Result.success(Unit)
            } else {
                // Revert optimistic update
                _isCurrentTrackLiked.value = currentlyLiked
                if (bothForbidden) {
                    Timber.w("Spotify like 403 on both endpoints: missing scope")
                    _isLibraryScopeMissing.value = true
                }
                Result.failure(RuntimeException("Failed to update liked status"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception toggling like: ${e.message}")
            _isCurrentTrackLiked.value = currentlyLiked
            Result.failure(e)
        }
    }


    suspend fun setVolume(volumePercent: Int): Result<Unit> {
        val clamped = volumePercent.coerceIn(0, 100)
        _activeDeviceVolume.value = clamped // Immediate optimistic local update
        val bearerToken = authManager.getValidBearerToken() ?: return Result.success(Unit)
        return try {
            val resp = spotifyApi.setVolume(bearerToken, clamped)
            if (resp.isSuccessful) {
                Timber.d("Spotify volume set to $clamped% on ${_activeDeviceName.value}")
                Result.success(Unit)
            } else {
                Timber.w("Spotify setVolume returned ${resp.code()}")
                Result.failure(RuntimeException("Spotify set volume error ${resp.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception setting Spotify volume: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun adjustVolume(deltaPercent: Int): Result<Unit> {
        val current = _activeDeviceVolume.value ?: 50
        return setVolume(current + deltaPercent)
    }

    suspend fun fetchAvailableDevices(): List<SpotifyDeviceDto> {
        val bearerToken = authManager.getValidBearerToken() ?: return emptyList()
        return try {
            val devResp = spotifyApi.getAvailableDevices(bearerToken)
            if (devResp.isSuccessful) {
                val devs = devResp.body()?.devices.orEmpty()
                _availableDevices.value = devs
                devs.firstOrNull { it.isActive }?.let { activeDev ->
                    _activeDeviceId.value = activeDev.id
                    _activeDeviceName.value = activeDev.name
                }
                devs
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e("Failed to fetch Spotify devices: ${e.message}")
            emptyList()
        }
    }

    suspend fun transferPlaybackToDevice(deviceId: String, play: Boolean = true): Boolean {
        val bearerToken = authManager.getValidBearerToken() ?: return false
        return try {
            val body = SpotifyTransferBody(deviceIds = listOf(deviceId), play = play)
            val resp = spotifyApi.transferPlayback(bearerToken, body)
            if (resp.isSuccessful) {
                _activeDeviceId.value = deviceId
                val targetDev = _availableDevices.value.find { it.id == deviceId }
                if (targetDev != null) {
                    _activeDeviceName.value = targetDev.name
                }
                _availableDevices.value = _availableDevices.value.map { dev: SpotifyDeviceDto ->
                    dev.copy(isActive = dev.id == deviceId)
                }
                Timber.i("Successfully transferred Spotify playback to device $deviceId")
                delay(600L)
                refreshPlaybackState(bearerToken)
                true
            } else {
                Timber.e("Failed to transfer Spotify playback: code=${resp.code()}")
                false
            }
        } catch (e: Exception) {
            Timber.e("Exception transferring Spotify playback: ${e.message}")
            false
        }
    }
}

