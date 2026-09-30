package com.mastercompanion.data.remote.model

import kotlinx.serialization.Serializable

enum class DeviceRole {
    UNSET,
    HOST,
    WAKER
}

enum class PcPowerStatus {
    OFFLINE,
    WAKING,
    ONLINE
}

enum class CommandProgressStatus {
    IDLE,
    PENDING,
    BROADCASTED,
    VERIFYING,
    ONLINE,
    TIMEOUT,
    FAILED
}

@Serializable
data class TargetPc(
    val name: String = "Main Gaming Rig",
    val macAddress: String = "",
    val broadcastIp: String = "192.168.1.255",
    val port: Int = 9,
    val lastKnownIp: String? = null,
    val status: PcPowerStatus = PcPowerStatus.OFFLINE,
    val lastBootDurationMs: Long? = null,
    val lastBootTimestamp: Long? = null
)

@Serializable
data class HostPresence(
    val deviceId: String = "",
    val deviceName: String = "Desk Companion",
    val isOnline: Boolean = false,
    val batteryLevel: Int = 0,
    val batteryStatus: String = "AC",
    val temperatureC: Float = 0f,
    val localIp: String = "",
    val lastHeartbeat: Long = 0L
)

@Serializable
data class WakeCommand(
    val commandId: String = "",
    val action: String = "WAKE",
    val dispatchedAt: Long = 0L,
    val broadcastedAt: Long? = null,
    val completedAt: Long? = null,
    val status: CommandProgressStatus = CommandProgressStatus.PENDING,
    val bootDurationMs: Long? = null,
    val errorMessage: String? = null
)

data class DiscoveredPc(
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val isOnline: Boolean = true
)

data class VaultState(
    val vaultId: String = "",
    val shortKey: String = "",
    val role: DeviceRole = DeviceRole.UNSET,
    val isPaired: Boolean = false,
    val hostPresence: HostPresence? = null,
    val targetPc: TargetPc? = null,
    val latestCommand: WakeCommand? = null,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
)
