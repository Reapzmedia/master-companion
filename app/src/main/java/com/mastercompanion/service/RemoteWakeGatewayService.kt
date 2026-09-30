package com.mastercompanion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mastercompanion.R
import com.mastercompanion.data.battery.BatteryRepository
import com.mastercompanion.data.network.LanPcScanner
import com.mastercompanion.data.network.WolSender
import com.mastercompanion.data.remote.RemoteVaultRepository
import com.mastercompanion.data.remote.model.CommandProgressStatus
import com.mastercompanion.data.remote.model.DeviceRole
import com.mastercompanion.data.remote.model.HostPresence
import com.mastercompanion.data.remote.model.PcPowerStatus
import com.mastercompanion.data.remote.model.WakeCommand
import com.mastercompanion.di.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RemoteWakeGatewayService : LifecycleService() {

    @Inject
    lateinit var remoteVaultRepository: RemoteVaultRepository

    @Inject
    lateinit var wolSender: WolSender

    @Inject
    lateinit var lanPcScanner: LanPcScanner

    @Inject
    lateinit var batteryRepository: BatteryRepository

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private var wakeLock: PowerManager.WakeLock? = null
    private var activeProbingJob: Job? = null
    private var heartbeatJob: Job? = null

    companion object {
        const val CHANNEL_ID = "remote_wake_gateway_channel"
        const val NOTIFICATION_ID = 8425
        const val ACTION_START = "com.mastercompanion.action.START_GATEWAY"
        const val ACTION_STOP = "com.mastercompanion.action.STOP_GATEWAY"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Desk Gateway Active"))

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MasterCompanion:RemoteWakeGateway")

        startGatewayEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun startGatewayEngine() {
        // 1. Periodic presence & battery heartbeat (every 60 seconds)
        heartbeatJob = lifecycleScope.launch(ioDispatcher) {
            while (isActive) {
                try {
                    val state = remoteVaultRepository.vaultState.value
                    if (state.role == DeviceRole.HOST && state.vaultId.isNotBlank()) {
                        val battery = batteryRepository.batteryData.value
                        val presence = HostPresence(
                            deviceId = Build.MODEL + "_" + Build.ID,
                            deviceName = "${Build.MANUFACTURER.capitalize()} ${Build.MODEL}",
                            isOnline = true,
                            batteryLevel = battery.level,
                            batteryStatus = battery.powerSource,
                            temperatureC = battery.temperatureCelsius,
                            localIp = wolSender.detectLocalIpAddress() ?: "",
                            lastHeartbeat = System.currentTimeMillis()
                        )
                        remoteVaultRepository.updateHostPresence(presence)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Error sending gateway heartbeat")
                }
                delay(60_000L)
            }
        }

        // 2. Real-time command dispatch listener
        lifecycleScope.launch(ioDispatcher) {
            var lastHandledCommandId = ""

            remoteVaultRepository.vaultState.collectLatest { state ->
                if (state.role != DeviceRole.HOST || state.vaultId.isBlank()) return@collectLatest

                val cmd = state.latestCommand ?: return@collectLatest
                if (cmd.commandId == lastHandledCommandId) return@collectLatest
                if (cmd.status != CommandProgressStatus.PENDING) return@collectLatest

                lastHandledCommandId = cmd.commandId
                handleIncomingCommand(cmd, state.targetPc?.macAddress, state.targetPc?.broadcastIp, state.targetPc?.lastKnownIp)
            }
        }
    }

    private fun handleIncomingCommand(
        cmd: WakeCommand,
        targetMac: String?,
        broadcastIp: String?,
        targetIp: String?
    ) {
        when (cmd.action) {
            "WAKE" -> {
                activeProbingJob?.cancel()
                activeProbingJob = lifecycleScope.launch(ioDispatcher) {
                    executeWakeSequence(cmd, targetMac, broadcastIp, targetIp)
                }
            }
            "REFRESH_STATUS" -> {
                lifecycleScope.launch(ioDispatcher) {
                    executeStatusCheck(cmd, targetIp)
                }
            }
        }
    }

    private suspend fun executeWakeSequence(
        cmd: WakeCommand,
        targetMac: String?,
        broadcastIp: String?,
        targetIp: String?
    ) {
        val mac = targetMac?.ifBlank { null } ?: return
        val bcast = broadcastIp?.ifBlank { "192.168.1.255" } ?: "192.168.1.255"
        val ip = targetIp?.ifBlank { null }

        Timber.i("Executing WoL wake sequence for MAC $mac to broadcast $bcast")

        // Acquire wake lock for up to 45 seconds during probing
        wakeLock?.acquire(45_000L)
        try {
            // Step 1: Mark BROADCASTED and fire WoL Magic Packet bursts
            remoteVaultRepository.updateCommandStatus(cmd.commandId, CommandProgressStatus.BROADCASTED)
            val sendSuccess = wolSender.sendWol(mac, bcast)
            if (!sendSuccess) {
                Timber.w("WoL broadcast packet failed to transmit")
            }

            // Step 2: Probe TCP port 445 on PC IP if known
            if (!ip.isNullOrBlank()) {
                remoteVaultRepository.updateCommandStatus(cmd.commandId, CommandProgressStatus.VERIFYING)

                val startTime = System.currentTimeMillis()
                var isOnline = false
                val maxAttempts = 45 // 45 seconds max

                for (attempt in 1..maxAttempts) {
                    delay(1000L)
                    val online = lanPcScanner.verifyPcOnline(ip, timeoutMs = 700)
                    if (online) {
                        isOnline = true
                        val durationMs = System.currentTimeMillis() - cmd.dispatchedAt
                        Timber.i("Target PC $ip confirmed ONLINE in ${durationMs}ms!")
                        remoteVaultRepository.updateCommandStatus(
                            commandId = cmd.commandId,
                            status = CommandProgressStatus.ONLINE,
                            durationMs = durationMs
                        )
                        break
                    }
                }

                if (!isOnline) {
                    Timber.w("Target PC $ip did not respond within 45 seconds")
                    remoteVaultRepository.updateCommandStatus(
                        commandId = cmd.commandId,
                        status = CommandProgressStatus.TIMEOUT,
                        errorMessage = "PC did not respond within 45s (Check BIOS WoL)"
                    )
                }
            } else {
                // If IP unknown, mark ONLINE after broadcast
                remoteVaultRepository.updateCommandStatus(
                    commandId = cmd.commandId,
                    status = CommandProgressStatus.ONLINE,
                    durationMs = 500L
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error executing wake sequence")
            remoteVaultRepository.updateCommandStatus(
                commandId = cmd.commandId,
                status = CommandProgressStatus.FAILED,
                errorMessage = e.message
            )
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private suspend fun executeStatusCheck(cmd: WakeCommand, targetIp: String?) {
        try {
            val ip = targetIp ?: ""
            val isOnline = if (ip.isNotBlank()) lanPcScanner.verifyPcOnline(ip, timeoutMs = 800) else false
            val battery = batteryRepository.batteryData.value

            val presence = HostPresence(
                deviceId = Build.MODEL + "_" + Build.ID,
                deviceName = "${Build.MANUFACTURER.capitalize()} ${Build.MODEL}",
                isOnline = true,
                batteryLevel = battery.level,
                batteryStatus = battery.powerSource,
                temperatureC = battery.temperatureCelsius,
                localIp = wolSender.detectLocalBroadcastAddresses().firstOrNull() ?: "",
                lastHeartbeat = System.currentTimeMillis()
            )
            remoteVaultRepository.updateHostPresence(presence)

            val currentPc = remoteVaultRepository.vaultState.value.targetPc
            if (currentPc != null) {
                val updatedStatus = if (isOnline) PcPowerStatus.ONLINE else PcPowerStatus.OFFLINE
                remoteVaultRepository.updateTargetPc(currentPc.copy(status = updatedStatus))
            }

            remoteVaultRepository.updateCommandStatus(cmd.commandId, CommandProgressStatus.ONLINE)
        } catch (e: Exception) {
            Timber.w(e, "Error executing status check")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeProbingJob?.cancel()
        heartbeatJob?.cancel()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        // Mark host offline
        lifecycleScope.launch(ioDispatcher) {
            try {
                val current = remoteVaultRepository.vaultState.value.hostPresence
                if (current != null) {
                    remoteVaultRepository.updateHostPresence(current.copy(isOnline = false))
                }
            } catch (_: Exception) {}
        }
        Timber.i("RemoteWakeGatewayService stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote Wake Gateway",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Desk Host listening for remote wake requests"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Master Companion Gateway")
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
