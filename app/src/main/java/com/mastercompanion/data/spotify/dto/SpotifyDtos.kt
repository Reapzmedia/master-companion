package com.mastercompanion.data.spotify.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("scope") val scope: String = "",
    @SerialName("expires_in") val expiresIn: Long = 3600L,
    @SerialName("refresh_token") val refreshToken: String? = null
)

@Serializable
data class SpotifyContextDto(
    @SerialName("type") val type: String = "",
    @SerialName("href") val href: String = "",
    @SerialName("uri") val uri: String = ""
)

@Serializable
data class SpotifyPlaylistSimpleDto(
    @SerialName("name") val name: String = ""
)

@Serializable
data class CurrentlyPlayingResponse(
    @SerialName("context") val context: SpotifyContextDto? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("progress_ms") val progressMs: Long = 0L,
    @SerialName("item") val item: TrackDto? = null
)

@Serializable
data class PlaybackStateResponse(
    @SerialName("device") val device: SpotifyDeviceDto? = null,
    @SerialName("repeat_state") val repeatState: String? = null,
    @SerialName("shuffle_state") val shuffleState: Boolean? = null,
    @SerialName("context") val context: SpotifyContextDto? = null,
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("progress_ms") val progressMs: Long = 0L,
    @SerialName("item") val item: TrackDto? = null
)

@Serializable
data class SpotifyDeviceDto(
    @SerialName("id") val id: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("name") val name: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("volume_percent") val volumePercent: Int? = null,
    @SerialName("supports_volume") val supportsVolume: Boolean = true
)

@Serializable
data class RecentlyPlayedResponse(
    @SerialName("items") val items: List<PlayHistoryItemDto> = emptyList()
)

@Serializable
data class PlayHistoryItemDto(
    @SerialName("track") val track: TrackDto,
    @SerialName("played_at") val playedAt: String = ""
)

@Serializable
data class TrackDto(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("artists") val artists: List<ArtistDto> = emptyList(),
    @SerialName("album") val album: AlbumDto? = null
)

@Serializable
data class ArtistDto(
    @SerialName("name") val name: String = ""
)

@Serializable
data class AlbumDto(
    @SerialName("name") val name: String = "",
    @SerialName("images") val images: List<ImageDto> = emptyList(),
    @SerialName("release_date") val releaseDate: String = ""
)

@Serializable
data class ImageDto(
    @SerialName("url") val url: String,
    @SerialName("height") val height: Int? = null,
    @SerialName("width") val width: Int? = null
)

@Serializable
data class SpotifyDevicesResponse(
    @SerialName("devices") val devices: List<SpotifyDeviceDto> = emptyList()
)

@Serializable
data class SpotifyQueueResponse(
    @SerialName("currently_playing") val currentlyPlaying: TrackDto? = null,
    @SerialName("queue") val queue: List<TrackDto> = emptyList()
)

@Serializable
data class SpotifyPlayBody(
    @SerialName("context_uri") val contextUri: String? = null,
    @SerialName("uris") val uris: List<String>? = null,
    @SerialName("position_ms") val positionMs: Long? = null
)

@Serializable
data class SpotifyTransferBody(
    @SerialName("device_ids") val deviceIds: List<String>,
    @SerialName("play") val play: Boolean = true
)

@Serializable
data class SavedTrackItemDto(
    @SerialName("track") val track: TrackDto
)

@Serializable
data class SavedTracksResponse(
    @SerialName("items") val items: List<SavedTrackItemDto> = emptyList()
)


