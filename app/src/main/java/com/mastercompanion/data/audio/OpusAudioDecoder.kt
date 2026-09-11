package com.mastercompanion.data.audio

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import timber.log.Timber

/**
 * Low-latency Opus hardware/software decoder utilizing Android MediaCodec ("OMX.google.opus.decoder").
 * Configures the standard Opus identification header (CSD-0, CSD-1, CSD-2) for 48kHz stereo decoding.
 */
class OpusAudioDecoder(
    private val sampleRate: Int = 48000,
    private val channels: Int = 2
) {
    private var codec: MediaCodec? = null
    private var isInitialized = false

    fun init(): Boolean {
        return try {
            val format = MediaFormat.createAudioFormat("audio/opus", sampleRate, channels)

            // CSD-0: Standard Opus identification header (19 bytes)
            val csd0 = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("OpusHead".toByteArray(Charsets.US_ASCII)) // 8 bytes magic signature
                put(1) // Version
                put(channels.toByte()) // Channel count (2)
                putShort(0) // Pre-skip
                putInt(sampleRate) // Original input sample rate (48000)
                putShort(0) // Output gain (0 dB)
                put(0) // Channel mapping family (0 = mono/stereo)
            }.array()
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0))

            // CSD-1: Pre-skip in nanoseconds
            val csd1 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(0L).array()
            format.setByteBuffer("csd-1", ByteBuffer.wrap(csd1))

            // CSD-2: Seek pre-roll in nanoseconds
            val csd2 = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(0L).array()
            format.setByteBuffer("csd-2", ByteBuffer.wrap(csd2))

            codec = MediaCodec.createDecoderByType("audio/opus").apply {
                configure(format, null, null, 0)
                start()
            }
            isInitialized = true
            Timber.i("MediaCodec Opus decoder initialized successfully ($sampleRate Hz, $channels ch)")
            true
        } catch (e: Exception) {
            Timber.w(e, "Failed to initialize MediaCodec Opus decoder")
            isInitialized = false
            false
        }
    }

    fun decode(opusData: ByteArray, onPcmDecoded: (ByteArray) -> Unit) {
        val decoder = codec ?: return
        if (!isInitialized) return

        try {
            val inIndex = decoder.dequeueInputBuffer(5000L) // 5ms timeout
            if (inIndex >= 0) {
                val inBuffer = decoder.getInputBuffer(inIndex)
                inBuffer?.clear()
                inBuffer?.put(opusData)
                decoder.queueInputBuffer(inIndex, 0, opusData.size, 0L, 0)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outIndex = decoder.dequeueOutputBuffer(bufferInfo, 5000L)
            while (outIndex >= 0) {
                val outBuffer = decoder.getOutputBuffer(outIndex)
                if (outBuffer != null && bufferInfo.size > 0) {
                    outBuffer.position(bufferInfo.offset)
                    outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    val pcmBytes = ByteArray(bufferInfo.size)
                    outBuffer.get(pcmBytes)
                    onPcmDecoded(pcmBytes)
                }
                decoder.releaseOutputBuffer(outIndex, false)
                outIndex = decoder.dequeueOutputBuffer(bufferInfo, 0L)
            }
        } catch (e: Exception) {
            Timber.e(e, "Opus decode error")
        }
    }

    fun release() {
        try {
            codec?.stop()
            codec?.release()
        } catch (e: Exception) {
            Timber.e(e, "Error releasing Opus decoder")
        } finally {
            codec = null
            isInitialized = false
        }
    }
}
