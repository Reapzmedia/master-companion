package com.mastercompanion.data.spotify

import com.mastercompanion.data.spotify.dto.CurrentlyPlayingResponse
import com.mastercompanion.data.spotify.dto.PlaybackStateResponse
import com.mastercompanion.data.spotify.dto.RecentlyPlayedResponse
import com.mastercompanion.data.spotify.dto.SpotifyPlaylistSimpleDto
import com.mastercompanion.data.spotify.dto.SavedTracksResponse
import com.mastercompanion.data.spotify.dto.SpotifyDevicesResponse
import com.mastercompanion.data.spotify.dto.SpotifyPlayBody
import com.mastercompanion.data.spotify.dto.SpotifyQueueResponse
import com.mastercompanion.data.spotify.dto.SpotifyTokenResponse
import com.mastercompanion.data.spotify.dto.SpotifyTransferBody
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class SpotifyTrackIdsBody(
    val ids: List<String>
)

@Serializable
data class SpotifyUrisBody(
    val uris: List<String>
)

@Serializable
data class SpotifyLibraryCheck(
    val id: String,
    val in_library: Boolean
)

interface SpotifyApi {

    @GET("v1/me/player")
    suspend fun getPlaybackState(
        @Header("Authorization") bearerToken: String
    ): Response<PlaybackStateResponse>

    @GET("v1/me/player/currently-playing")
    suspend fun getCurrentlyPlaying(
        @Header("Authorization") bearerToken: String
    ): Response<CurrentlyPlayingResponse>

    @GET("v1/playlists/{playlist_id}")
    suspend fun getPlaylist(
        @Header("Authorization") bearerToken: String,
        @Path("playlist_id") playlistId: String,
        @Query("fields") fields: String = "name"
    ): Response<SpotifyPlaylistSimpleDto>

    @GET("v1/me/player/recently-played")
    suspend fun getRecentlyPlayed(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 1
    ): Response<RecentlyPlayedResponse>

    @GET("v1/me/tracks")
    suspend fun getUserSavedTracks(
        @Header("Authorization") bearerToken: String,
        @Query("limit") limit: Int = 1
    ): Response<SavedTracksResponse>

    @GET("v1/me/player/devices")
    suspend fun getAvailableDevices(
        @Header("Authorization") bearerToken: String
    ): Response<SpotifyDevicesResponse>

    @GET("v1/me/player/queue")
    suspend fun getQueue(
        @Header("Authorization") bearerToken: String
    ): Response<SpotifyQueueResponse>

    @PUT("v1/me/player")
    suspend fun transferPlayback(
        @Header("Authorization") bearerToken: String,
        @Body body: SpotifyTransferBody
    ): Response<ResponseBody>

    @PUT("v1/me/player/volume")
    suspend fun setVolume(
        @Header("Authorization") bearerToken: String,
        @Query("volume_percent") volumePercent: Int,
        @Query("device_id") deviceId: String? = null
    ): Response<ResponseBody>

    @PUT("v1/me/player/play")
    suspend fun play(
        @Header("Authorization") bearerToken: String,
        @Query("device_id") deviceId: String? = null,
        @Body body: SpotifyPlayBody? = null
    ): Response<ResponseBody>

    @PUT("v1/me/player/pause")
    suspend fun pause(
        @Header("Authorization") bearerToken: String,
        @Query("device_id") deviceId: String? = null
    ): Response<ResponseBody>

    @POST("v1/me/player/next")
    suspend fun next(
        @Header("Authorization") bearerToken: String,
        @Query("device_id") deviceId: String? = null
    ): Response<ResponseBody>

    @POST("v1/me/player/previous")
    suspend fun previous(
        @Header("Authorization") bearerToken: String,
        @Query("device_id") deviceId: String? = null
    ): Response<ResponseBody>

    @PUT("v1/me/player/seek")
    suspend fun seek(
        @Header("Authorization") bearerToken: String,
        @Query("position_ms") positionMs: Long,
        @Query("device_id") deviceId: String? = null
    ): Response<ResponseBody>

    @PUT("v1/me/player/shuffle")
    suspend fun setShuffle(
        @Header("Authorization") bearerToken: String,
        @Query("state") state: Boolean,
        @Query("device_id") deviceId: String? = null
    ): Response<ResponseBody>

    @PUT("v1/me/player/repeat")
    suspend fun setRepeat(
        @Header("Authorization") bearerToken: String,
        @Query("state") state: String, // "track", "context", "off"
        @Query("device_id") deviceId: String? = null
    ): Response<ResponseBody>

    // ═══ Modern Library (Liked Songs) API ═══
    @GET("v1/me/library/contains")
    suspend fun checkLibraryContains(
        @Header("Authorization") bearerToken: String,
        @Query("uris") uris: String
    ): Response<List<Boolean>>

    @PUT("v1/me/library")
    suspend fun saveToLibrary(
        @Header("Authorization") bearerToken: String,
        @Query("uris") uris: String
    ): Response<ResponseBody>

    @HTTP(method = "DELETE", path = "v1/me/library", hasBody = false)
    suspend fun removeFromLibrary(
        @Header("Authorization") bearerToken: String,
        @Query("uris") uris: String
    ): Response<ResponseBody>

    // Legacy Fallbacks
    @GET("v1/me/tracks/contains")
    suspend fun checkUserSavedTracks(
        @Header("Authorization") bearerToken: String,
        @Query("ids") ids: String
    ): Response<List<Boolean>>

    @PUT("v1/me/tracks")
    suspend fun saveTrack(
        @Header("Authorization") bearerToken: String,
        @Query("ids") ids: String
    ): Response<ResponseBody>

    @HTTP(method = "DELETE", path = "v1/me/tracks", hasBody = false)
    suspend fun removeTrack(
        @Header("Authorization") bearerToken: String,
        @Query("ids") ids: String
    ): Response<ResponseBody>


    // ═══ Token Accounts API ═══
    @FormUrlEncoded
    @POST("https://accounts.spotify.com/api/token")
    suspend fun exchangePkceCode(
        @Field("client_id") clientId: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") codeVerifier: String
    ): Response<SpotifyTokenResponse>

    @FormUrlEncoded
    @POST("https://accounts.spotify.com/api/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): Response<SpotifyTokenResponse>
}
