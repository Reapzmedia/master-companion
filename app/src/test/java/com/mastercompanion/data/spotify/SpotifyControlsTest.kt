package com.mastercompanion.data.spotify

import com.mastercompanion.data.spotify.dto.SpotifyDevicesResponse
import com.mastercompanion.data.spotify.dto.SpotifyPlayBody
import com.mastercompanion.data.spotify.dto.SpotifyQueueResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyControlsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Test
    fun `deserialize Spotify Queue response with currently playing and upcoming queue items`() {
        val rawJson = """
            {
              "currently_playing": {
                "id": "current_track_1",
                "name": "Current Track Playing",
                "duration_ms": 210000,
                "artists": [{"name": "Current Artist"}],
                "album": {
                  "name": "Current Album",
                  "images": [{"url": "https://example.com/art1.jpg"}]
                }
              },
              "queue": [
                {
                  "id": "next_track_2",
                  "name": "Upcoming Next Track",
                  "duration_ms": 195000,
                  "artists": [{"name": "Next Artist 1"}, {"name": "Next Artist 2"}],
                  "album": {
                    "name": "Next Album",
                    "images": [{"url": "https://example.com/art2.jpg"}]
                  }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<SpotifyQueueResponse>(rawJson)

        assertNotNull(response.currentlyPlaying)
        assertEquals("current_track_1", response.currentlyPlaying?.id)
        assertEquals("Current Track Playing", response.currentlyPlaying?.name)

        assertEquals(1, response.queue.size)
        val nextItem = response.queue.first()
        assertEquals("next_track_2", nextItem.id)
        assertEquals("Upcoming Next Track", nextItem.name)
        assertEquals(2, nextItem.artists.size)
        assertEquals("Next Artist 1", nextItem.artists[0].name)
        assertEquals("Next Artist 2", nextItem.artists[1].name)
        assertEquals("https://example.com/art2.jpg", nextItem.album?.images?.firstOrNull()?.url)
    }

    @Test
    fun `deserialize Spotify Devices and select active or fallback device`() {
        val rawJson = """
            {
              "devices": [
                {
                  "id": "dev_inactive_phone",
                  "is_active": false,
                  "name": "Pixel 7 Pro",
                  "type": "Smartphone",
                  "volume_percent": 60,
                  "supports_volume": true
                },
                {
                  "id": "dev_active_pc",
                  "is_active": true,
                  "name": "Main Gaming PC",
                  "type": "Computer",
                  "volume_percent": 75,
                  "supports_volume": true
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<SpotifyDevicesResponse>(rawJson)
        assertEquals(2, response.devices.size)

        val activeDevice = response.devices.firstOrNull { it.isActive }
        assertNotNull(activeDevice)
        assertEquals("dev_active_pc", activeDevice?.id)
        assertEquals("Main Gaming PC", activeDevice?.name)

        // If no active device, fallback to first
        val fallbackDevice = response.devices.firstOrNull()
        assertEquals("dev_inactive_phone", fallbackDevice?.id)
    }

    @Test
    fun `serialize SpotifyPlayBody with track URIs for resuming stopped playback`() {
        val body = SpotifyPlayBody(
            uris = listOf("spotify:track:7a3LWj5xSFhFRYmztS8wgK"),
            positionMs = 15000L
        )

        val encoded = json.encodeToString(body)
        assertTrue(encoded.contains("spotify:track:7a3LWj5xSFhFRYmztS8wgK"))
        assertTrue(encoded.contains("15000"))

        val decoded = json.decodeFromString<SpotifyPlayBody>(encoded)
        assertEquals(listOf("spotify:track:7a3LWj5xSFhFRYmztS8wgK"), decoded.uris)
        assertEquals(15000L, decoded.positionMs)
    }

    @Test
    fun `interactive seekbar calculation accurately maps touch fractions to track timestamps`() {
        val trackDurationMs = 240000L // 4 minutes

        fun calculateSeekMs(fraction: Float, durationMs: Long): Long {
            return (fraction.coerceIn(0f, 1f) * durationMs).toLong()
        }

        // Tap start
        assertEquals(0L, calculateSeekMs(0f, trackDurationMs))

        // Tap 25%
        assertEquals(60000L, calculateSeekMs(0.25f, trackDurationMs))

        // Tap middle (50%)
        assertEquals(120000L, calculateSeekMs(0.50f, trackDurationMs))

        // Tap 75%
        assertEquals(180000L, calculateSeekMs(0.75f, trackDurationMs))

        // Tap end (100%)
        assertEquals(240000L, calculateSeekMs(1.0f, trackDurationMs))

        // Edge case: Negative drag clamp
        assertEquals(0L, calculateSeekMs(-0.1f, trackDurationMs))

        // Edge case: Beyond end drag clamp
        assertEquals(240000L, calculateSeekMs(1.2f, trackDurationMs))
    }

    @Test
    fun `hardware volume delta stepping stays within 0 to 100 range`() {
        fun adjustVolume(current: Int?, delta: Int): Int {
            val base = current ?: 50
            return (base + delta).coerceIn(0, 100)
        }

        // Standard step up
        assertEquals(55, adjustVolume(50, 5))

        // Standard step down
        assertEquals(45, adjustVolume(50, -5))

        // Upper clamp
        assertEquals(100, adjustVolume(98, 5))

        // Lower clamp
        assertEquals(0, adjustVolume(2, -5))

        // Null default
        assertEquals(55, adjustVolume(null, 5))
    }
}
