package com.mastercompanion.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mastercompanion.MainActivity
import com.mastercompanion.MasterCompanionApp
import com.mastercompanion.R
import com.mastercompanion.data.audio.AudioPlayer
import com.mastercompanion.data.audio.JitterBuffer
import com.mastercompanion.data.audio.OpusAudioDecoder
import com.mastercompanion.data.audio.PacketParser
import com.mastercompanion.data.prefs.PreferencesRepository
import com.mastercompanion.domain.model.AudioCodec
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@AndroidEntryPoint
class AudioReceiverService : Service() {

    @Inject
    lateinit var audioPlayer: AudioPlayer

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var udpJob: Job? = null
    private var tcpJob: Job? = null
    private var statsJob: Job? = null

    private var udpSocket: DatagramSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private val jitterBuffer = JitterBuffer(bufferSizePackets = 2)

    private val tcpPacketsReceived = AtomicLong(0L)
    private var opusDecoder: OpusAudioDecoder? = null

    override fun onCreate() {
        super.onCreate()
        Timber.i("AudioReceiverService created")
        startForeground(NOTIFICATION_ID, buildNotification())
        audioPlayer.start()
        opusDecoder = OpusAudioDecoder(sampleRate = 48000, channels = 2).apply {
            init()
        }
        startReceivers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("AudioReceiverService started (Dual UDP/TCP receiver)")
        return START_STICKY
    }

    private fun startReceivers() {
        udpJob?.cancel()
        tcpJob?.cancel()
        statsJob?.cancel()

        serviceScope.launch {
            val port = preferencesRepository.audioPortFlow.first()
            startUdpReceiver(port)
            startTcpReceiver(port)
            startStatsMonitor()
        }
    }

    @Volatile
    private var lastSenderIp: String? = null
    @Volatile
    private var lastCodec = AudioCodec.PCM
    @Volatile
    private var lastPacketTime = 0L
    @Volatile
    private var isTcpTransportActive = false

    private fun startStatsMonitor() {
        statsJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val elapsedSinceLastPacket = System.currentTimeMillis() - lastPacketTime
                val isStreaming = lastPacketTime > 0 && elapsedSinceLastPacket < 3000

                val totalPackets = jitterBuffer.packetsReceived + tcpPacketsReceived.get()
                val latency = if (isStreaming) {
                    if (isTcpTransportActive) 10.0f else 20.0f * 2
                } else 0.0f

                audioPlayer.updateStats(
                    isReceiving = isStreaming,
                    codec = lastCodec,
                    packetsReceived = totalPackets,
                    packetsLost = if (isTcpTransportActive) 0L else jitterBuffer.packetsLost,
                    latencyMs = latency,
                    clientIp = if (isStreaming) lastSenderIp else null
                )
            }
        }
    }

    // ═══ 1. UDP Receiver (Wi-Fi LAN) ═══
    private fun startUdpReceiver(port: Int) {
        udpJob = serviceScope.launch {
            val buffer = ByteArray(8192)
            try {
                udpSocket = DatagramSocket(port).apply {
                    soTimeout = 2000
                    receiveBufferSize = 65536
                }
                Timber.i("AudioReceiver UDP socket bound on port $port")

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        udpSocket?.receive(packet)
                        lastPacketTime = System.currentTimeMillis()
                        lastSenderIp = packet.address?.hostAddress ?: "Wi-Fi LAN"
                        isTcpTransportActive = false

                        val audioPacket = PacketParser.parse(packet.data, packet.length)
                        if (audioPacket != null) {
                            lastCodec = audioPacket.codec
                            jitterBuffer.push(audioPacket)

                            var queued = jitterBuffer.pop()
                            while (queued != null) {
                                routeAudioPacket(queued.codec, queued.payload)
                                queued = jitterBuffer.pop()
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Expected periodic timeout during silence
                    } catch (e: Exception) {
                        if (isActive) Timber.e(e, "Error receiving UDP audio packet")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind AudioReceiver UDP socket on port $port")
            }
        }
    }

    // ═══ 2. TCP Receiver (USB ADB Forward & Direct Cable Stream) ═══
    private fun startTcpReceiver(port: Int) {
        tcpJob = serviceScope.launch {
            try {
                tcpServerSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Timber.i("AudioReceiver TCP ServerSocket listening on port $port")

                while (isActive) {
                    try {
                        val clientSocket: Socket = tcpServerSocket?.accept() ?: break
                        Timber.i("AudioReceiver TCP Client connected: ${clientSocket.inetAddress.hostAddress}")
                        handleTcpClient(clientSocket)
                    } catch (e: Exception) {
                        if (isActive) Timber.e(e, "TCP accept error")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to bind AudioReceiver TCP ServerSocket on port $port")
            }
        }
    }

    private suspend fun handleTcpClient(clientSocket: Socket) {
        serviceScope.launch {
            clientSocket.use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = 5000
                val dis = DataInputStream(socket.getInputStream())

                while (isActive && !socket.isClosed) {
                    try {
                        // Read 2-byte frame length prefix (Big-Endian)
                        val frameLen = dis.readUnsignedShort()
                        if (frameLen <= 0 || frameLen > 16384) {
                            Timber.w("Invalid TCP audio frame length: $frameLen")
                            break
                        }

                        val frameBytes = ByteArray(frameLen)
                        dis.readFully(frameBytes)

                        lastPacketTime = System.currentTimeMillis()
                        lastSenderIp = "${socket.inetAddress.hostAddress} (USB/TCP)"
                        isTcpTransportActive = true
                        tcpPacketsReceived.incrementAndGet()

                        val audioPacket = PacketParser.parse(frameBytes, frameLen)
                        if (audioPacket != null) {
                            lastCodec = audioPacket.codec
                            routeAudioPacket(audioPacket.codec, audioPacket.payload)
                        }
                    } catch (_: SocketTimeoutException) {
                        // Keep alive check
                    } catch (e: Exception) {
                        Timber.d("TCP streaming client disconnected: ${e.message}")
                        break
                    }
                }
            }
        }
    }

    private fun routeAudioPacket(codec: AudioCodec, payload: ByteArray) {
        when (codec) {
            AudioCodec.PCM -> {
                audioPlayer.writePcm(payload)
            }
            AudioCodec.OPUS -> {
                opusDecoder?.decode(payload) { decodedPcm ->
                    audioPlayer.writePcm(decodedPcm)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        udpJob?.cancel()
        tcpJob?.cancel()
        statsJob?.cancel()

        udpSocket?.close()
        udpSocket = null

        tcpServerSocket?.close()
        tcpServerSocket = null

        opusDecoder?.release()
        opusDecoder = null

        audioPlayer.stop()
        serviceScope.cancel()
        Timber.i("AudioReceiverService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, MasterCompanionApp.CHANNEL_AUDIO_RECEIVER)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("PC Audio Receiver Active (UDP/TCP :8421)")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1003
    }
}

