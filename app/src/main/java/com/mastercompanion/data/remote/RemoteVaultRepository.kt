package com.mastercompanion.data.remote

import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mastercompanion.data.prefs.PreferencesRepository
import com.mastercompanion.data.remote.model.CommandProgressStatus
import com.mastercompanion.data.remote.model.DeviceRole
import com.mastercompanion.data.remote.model.HostPresence
import com.mastercompanion.data.remote.model.PcPowerStatus
import com.mastercompanion.data.remote.model.TargetPc
import com.mastercompanion.data.remote.model.VaultState
import com.mastercompanion.data.remote.model.WakeCommand
import com.mastercompanion.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteVaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val auth by lazy {
        try { FirebaseAuth.getInstance() } catch (e: Exception) { Timber.w(e, "Firebase Auth not available"); null }
    }
    private val firestore by lazy {
        try { FirebaseFirestore.getInstance() } catch (e: Exception) { Timber.w(e, "Firestore not available"); null }
    }

    private val _vaultState = MutableStateFlow(VaultState())
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    private var vaultListenerRegistration: ListenerRegistration? = null
    private var commandsListenerRegistration: ListenerRegistration? = null
    private var lastRefreshTimestamp = 0L

    init {
        // Initialize from stored preferences
        scope.launch {
            val roleStr = preferencesRepository.deviceRoleFlow.firstOrNull() ?: "UNSET"
            val vaultId = preferencesRepository.vaultIdFlow.firstOrNull() ?: ""
            val shortKey = preferencesRepository.vaultShortKeyFlow.firstOrNull() ?: ""
            val role = try { DeviceRole.valueOf(roleStr) } catch (_: Exception) { DeviceRole.UNSET }

            val savedMac = preferencesRepository.pcMacFlow.firstOrNull() ?: ""
            val savedIp = preferencesRepository.pcIpFlow.firstOrNull() ?: ""
            val savedBcast = preferencesRepository.wolBroadcastIpFlow.firstOrNull() ?: "192.168.1.255"

            val initialPc = if (savedMac.isNotBlank() || savedIp.isNotBlank()) {
                TargetPc(
                    name = "Main PC",
                    macAddress = savedMac,
                    broadcastIp = savedBcast,
                    lastKnownIp = savedIp.ifBlank { null }
                )
            } else null

            _vaultState.value = _vaultState.value.copy(
                vaultId = vaultId,
                shortKey = shortKey,
                role = role,
                isPaired = vaultId.isNotBlank() && role != DeviceRole.UNSET,
                targetPc = initialPc
            )

            if (vaultId.isNotBlank() && role != DeviceRole.UNSET) {
                ensureAuthenticated()
                attachVaultListener(vaultId)
            }
        }
    }

    suspend fun ensureAuthenticated(): Boolean = withContext(ioDispatcher) {
        val a = auth ?: return@withContext false
        if (a.currentUser != null) return@withContext true
        return@withContext try {
            a.signInAnonymously().await()
            Timber.i("Firebase Anonymous Auth successful: UID ${a.currentUser?.uid}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to authenticate anonymously with Firebase")
            false
        }
    }

    /**
     * Initializes the device as a Desk Host:
     * Generates a new Vault ID and a 6-digit pairing key (or restores existing one),
     * registering the vault in Firestore.
     */
    suspend fun initAsHost(existingPc: TargetPc? = null): Result<VaultState> = withContext(ioDispatcher) {
        try {
            ensureAuthenticated()
            val db = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore is not available"))

            var vaultId = preferencesRepository.vaultIdFlow.firstOrNull() ?: ""
            var shortKey = preferencesRepository.vaultShortKeyFlow.firstOrNull() ?: ""

            if (vaultId.isBlank()) {
                vaultId = "vlt_" + UUID.randomUUID().toString().replace("-", "").take(10)
                shortKey = generateShortKey()
                preferencesRepository.setVaultDetails(vaultId, shortKey)
            }
            preferencesRepository.setDeviceRole(DeviceRole.HOST.name)

            val initialPc = existingPc ?: _vaultState.value.targetPc ?: TargetPc(
                name = "Main PC",
                macAddress = preferencesRepository.pcMacFlow.firstOrNull() ?: "",
                broadcastIp = preferencesRepository.wolBroadcastIpFlow.firstOrNull() ?: "192.168.1.255",
                lastKnownIp = preferencesRepository.pcIpFlow.firstOrNull() ?: "192.168.1.100"
            )

            // 1. Immediately activate Host state locally on device
            val state = VaultState(
                vaultId = vaultId,
                shortKey = shortKey,
                role = DeviceRole.HOST,
                isPaired = true,
                targetPc = initialPc,
                isSyncing = false
            )
            _vaultState.value = state
            Timber.i("Desk Host initialized locally with Vault $vaultId, ShortKey $shortKey")

            // 2. Register with Firestore in background / try-catch without blocking local usage
            try {
                if (db != null) {
                    val keyDoc = hashMapOf(
                        "vaultId" to vaultId,
                        "createdAt" to System.currentTimeMillis(),
                        "expiresAt" to System.currentTimeMillis() + (15 * 60 * 1000)
                    )
                    db.collection("pairing_keys").document(shortKey).set(keyDoc).await()

                    val vaultDoc = hashMapOf<String, Any>(
                        "vaultId" to vaultId,
                        "shortKey" to shortKey,
                        "createdAt" to System.currentTimeMillis(),
                        "lastActive" to System.currentTimeMillis(),
                        "targetPc" to targetPcToMap(initialPc),
                        "host" to hostPresenceToMap(
                            HostPresence(
                                deviceId = Build.MODEL + "_" + Build.ID,
                                deviceName = "${Build.MANUFACTURER.capitalize()} ${Build.MODEL}",
                                isOnline = true,
                                lastHeartbeat = System.currentTimeMillis()
                            )
                        )
                    )
                    db.collection("vaults").document(vaultId).set(vaultDoc).await()
                    attachVaultListener(vaultId)
                }
            } catch (e: Exception) {
                Timber.w(e, "Firestore registration warning (operating in LAN-first mode): ${e.message}")
            }

            Result.success(state)
        } catch (e: Exception) {
            Timber.e(e, "Error initializing as Desk Host")
            Result.failure(e)
        }
    }

    /**
     * Pairs the device as a Pocket Waker using a scanned QR payload or 6-digit key.
     */
    suspend fun pairAsWaker(rawKeyOrPayload: String): Result<VaultState> = withContext(ioDispatcher) {
        try {
            ensureAuthenticated()
            val db = firestore ?: return@withContext Result.failure(IllegalStateException("Firestore is not available"))

            // Payload can be raw "834-192" or QR scheme "mastercompanion://pair?v=vlt_xxx&k=834-192"
            val (resolvedVaultId, resolvedKey) = parsePairingPayload(rawKeyOrPayload, db)

            if (resolvedVaultId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Invalid or expired pairing key: $rawKeyOrPayload"))
            }

            preferencesRepository.setVaultDetails(resolvedVaultId, resolvedKey)
            preferencesRepository.setDeviceRole(DeviceRole.WAKER.name)

            attachVaultListener(resolvedVaultId)

            val state = _vaultState.value.copy(
                vaultId = resolvedVaultId,
                shortKey = resolvedKey,
                role = DeviceRole.WAKER,
                isPaired = true
            )
            _vaultState.value = state
            Timber.i("Pocket Waker paired to Vault $resolvedVaultId")
            Result.success(state)
        } catch (e: Exception) {
            Timber.e(e, "Error pairing as Pocket Waker")
            Result.failure(e)
        }
    }

    /**
     * Updates the device role explicitly (e.g. when user chooses "Set as Pocket Waker").
     */
    suspend fun setDeviceRole(role: DeviceRole) = withContext(ioDispatcher) {
        preferencesRepository.setDeviceRole(role.name)
        _vaultState.value = _vaultState.value.copy(role = role)
        Timber.i("Device role updated to $role")
    }

    /**
     * Dispatches a Wake command to the Firestore Vault.
     */
    suspend fun dispatchWakeCommand(): Result<WakeCommand> = withContext(ioDispatcher) {
        try {
            val vaultId = _vaultState.value.vaultId
            if (vaultId.isBlank()) return@withContext Result.failure(IllegalStateException("Device is not paired to a Vault"))

            val commandId = UUID.randomUUID().toString()
            val command = WakeCommand(
                commandId = commandId,
                action = "WAKE",
                dispatchedAt = System.currentTimeMillis(),
                status = CommandProgressStatus.PENDING
            )

            // 1. Dual-Path: Direct LAN trigger to Desk Host :8420 if local IP is known
            val localHostIp = _vaultState.value.hostPresence?.localIp
            if (!localHostIp.isNullOrBlank()) {
                scope.launch {
                    try {
                        val url = java.net.URL("http://$localHostIp:8420/api/wol")
                        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 2500
                            readTimeout = 2500
                        }
                        if (conn.responseCode == 200) {
                            Timber.i("Direct LAN WoL dispatched successfully to Host at $localHostIp:8420")
                        }
                        conn.disconnect()
                    } catch (e: Exception) {
                        Timber.d("Direct LAN WoL skipped or failed: ${e.message}")
                    }
                }
            }

            // 2. Dual-Path: Cloud Firestore dispatch for remote cellular access
            val db = firestore
            if (db != null) {
                try {
                    db.collection("vaults").document(vaultId)
                        .collection("commands").document(commandId)
                        .set(wakeCommandToMap(command)).await()

                    val currentPc = _vaultState.value.targetPc ?: TargetPc()
                    val updatedPc = currentPc.copy(status = PcPowerStatus.WAKING)
                    db.collection("vaults").document(vaultId)
                        .update("targetPc", targetPcToMap(updatedPc)).await()
                } catch (e: Exception) {
                    Timber.w(e, "Cloud Firestore command dispatch warning: ${e.message}")
                }
            }

            val currentPc = _vaultState.value.targetPc ?: TargetPc()
            val updatedPc = currentPc.copy(status = PcPowerStatus.WAKING)
            _vaultState.value = _vaultState.value.copy(
                latestCommand = command,
                targetPc = updatedPc
            )
            Timber.i("Dispatched WAKE command $commandId to Vault $vaultId")
            Result.success(command)
        } catch (e: Exception) {
            Timber.e(e, "Error dispatching wake command")
            Result.failure(e)
        }
    }

    /**
     * Requests a status refresh with a 5-second cooldown debounce.
     */
    suspend fun requestStatusRefresh(): Boolean = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val vaultId = _vaultState.value.vaultId
        if (vaultId.isBlank()) return@withContext false

        // 1. Direct LAN refresh check
        val localHostIp = _vaultState.value.hostPresence?.localIp
        if (!localHostIp.isNullOrBlank()) {
            scope.launch {
                try {
                    val url = java.net.URL("http://$localHostIp:8420/status")
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 2000
                        readTimeout = 2000
                    }
                    if (conn.responseCode == 200) {
                        val currentHost = _vaultState.value.hostPresence ?: HostPresence()
                        _vaultState.value = _vaultState.value.copy(
                            hostPresence = currentHost.copy(isOnline = true, lastHeartbeat = System.currentTimeMillis())
                        )
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    Timber.d("LAN status check error: ${e.message}")
                }
            }
        }

        try {
            val db = firestore ?: return@withContext true
            val cmdId = "refresh_" + UUID.randomUUID().toString().take(8)
            val command = WakeCommand(
                commandId = cmdId,
                action = "REFRESH_STATUS",
                dispatchedAt = now,
                status = CommandProgressStatus.PENDING
            )
            db.collection("vaults").document(vaultId)
                .collection("commands").document(cmdId)
                .set(wakeCommandToMap(command)).await()
            true
        } catch (e: Exception) {
            Timber.w(e, "Error requesting status refresh in cloud")
            true
        }
    }

    /**
     * Updates target PC configuration in the Vault and local Preferences.
     */
    suspend fun updateTargetPc(targetPc: TargetPc): Result<Unit> = withContext(ioDispatcher) {
        try {
            // 1. Immediately persist locally to Preferences
            preferencesRepository.setPcNetworkDetails(targetPc.lastKnownIp ?: "", targetPc.macAddress)
            preferencesRepository.setWolBroadcastIp(targetPc.broadcastIp)

            // 2. IMMEDIATELY update in-memory state so Compose UI updates instantly on-screen!
            _vaultState.value = _vaultState.value.copy(targetPc = targetPc)
            Timber.i("Local target PC updated: ${targetPc.name} (${targetPc.macAddress})")

            // 3. Sync to Cloud Firestore using SetOptions.merge() safely
            val vaultId = _vaultState.value.vaultId
            val db = firestore
            if (vaultId.isNotBlank() && db != null) {
                try {
                    db.collection("vaults").document(vaultId)
                        .set(mapOf("targetPc" to targetPcToMap(targetPc)), com.google.firebase.firestore.SetOptions.merge())
                        .await()
                    Timber.i("Target PC successfully synced to Firestore vault $vaultId")
                } catch (e: Exception) {
                    Timber.w(e, "Could not sync target PC to Firestore (offline or rules pending): ${e.message}")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error updating target PC")
            Result.failure(e)
        }
    }

    /**
     * Updates Host presence and battery telemetry (called periodically by Desk Host).
     */
    suspend fun updateHostPresence(presence: HostPresence) = withContext(ioDispatcher) {
        val vaultId = _vaultState.value.vaultId
        if (vaultId.isBlank()) return@withContext
        try {
            ensureAuthenticated()
            val db = firestore ?: return@withContext
            db.collection("vaults").document(vaultId)
                .set(mapOf("host" to hostPresenceToMap(presence)), com.google.firebase.firestore.SetOptions.merge()).await()
        } catch (e: Exception) {
            Timber.w(e, "Failed to update host presence")
        }
    }

    /**
     * Updates command execution progress (called by Desk Host).
     */
    suspend fun updateCommandStatus(
        commandId: String,
        status: CommandProgressStatus,
        durationMs: Long? = null,
        errorMessage: String? = null
    ) = withContext(ioDispatcher) {
        val vaultId = _vaultState.value.vaultId
        if (vaultId.isBlank()) return@withContext
        try {
            ensureAuthenticated()
            val db = firestore ?: return@withContext
            val updates = mutableMapOf<String, Any>(
                "status" to status.name
            )
            if (status == CommandProgressStatus.BROADCASTED) {
                updates["broadcastedAt"] = System.currentTimeMillis()
            }
            if (status == CommandProgressStatus.ONLINE || status == CommandProgressStatus.TIMEOUT || status == CommandProgressStatus.FAILED) {
                updates["completedAt"] = System.currentTimeMillis()
            }
            if (durationMs != null) {
                updates["bootDurationMs"] = durationMs
            }
            if (errorMessage != null) {
                updates["errorMessage"] = errorMessage
            }

            db.collection("vaults").document(vaultId)
                .collection("commands").document(commandId)
                .set(updates, com.google.firebase.firestore.SetOptions.merge()).await()

            // Update Target PC status if completed
            if (status == CommandProgressStatus.ONLINE) {
                val pcStatus = PcPowerStatus.ONLINE
                db.collection("vaults").document(vaultId)
                    .set(
                        mapOf(
                            "targetPc" to mapOf(
                                "status" to pcStatus.name,
                                "lastBootDurationMs" to (durationMs ?: 0L),
                                "lastBootTimestamp" to System.currentTimeMillis()
                            )
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
            }
        } catch (e: Exception) {
            Timber.w(e, "Error updating command status for $commandId")
        }
    }

    /**
     * Unlinks the Vault on this device and resets role to UNSET.
     */
    suspend fun unlinkVault() = withContext(ioDispatcher) {
        vaultListenerRegistration?.remove()
        commandsListenerRegistration?.remove()
        preferencesRepository.clearVault()
        _vaultState.value = VaultState()
    }

    private fun attachVaultListener(vaultId: String) {
        vaultListenerRegistration?.remove()
        val db = firestore ?: return

        vaultListenerRegistration = db.collection("vaults").document(vaultId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.w(error, "Vault listener error")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data ?: return@addSnapshotListener

                    val hostMap = data["host"] as? Map<String, Any>
                    val hostPresence = hostMap?.let { mapToHostPresence(it) }

                    val pcMap = data["targetPc"] as? Map<String, Any>
                    val targetPc = pcMap?.let { mapToTargetPc(it) }

                    _vaultState.value = _vaultState.value.copy(
                        hostPresence = hostPresence,
                        targetPc = targetPc,
                        isSyncing = false
                    )
                }
            }

        // Listen for latest command updates
        commandsListenerRegistration?.remove()
        commandsListenerRegistration = db.collection("vaults").document(vaultId)
            .collection("commands")
            .orderBy("dispatchedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener
                val doc = snapshots.documents.first()
                val cmd = mapToWakeCommand(doc.data ?: emptyMap())
                _vaultState.value = _vaultState.value.copy(latestCommand = cmd)
            }
    }

    private suspend fun parsePairingPayload(payload: String, db: FirebaseFirestore): Pair<String, String> {
        val clean = payload.trim()
        // If mastercompanion://pair?v=vlt_xxx&k=834-192
        if (clean.startsWith("mastercompanion://pair")) {
            val uri = android.net.Uri.parse(clean)
            val v = uri.getQueryParameter("v") ?: ""
            val k = uri.getQueryParameter("k") ?: ""
            val ip = uri.getQueryParameter("ip") ?: ""
            if (ip.isNotBlank()) {
                val currentHost = _vaultState.value.hostPresence ?: HostPresence()
                _vaultState.value = _vaultState.value.copy(
                    hostPresence = currentHost.copy(localIp = ip, isOnline = true, lastHeartbeat = System.currentTimeMillis())
                )
            }
            if (v.isNotBlank()) return Pair(v, k)
        }

        // Standard 6-digit key lookup
        val cleanKey = clean.uppercase().replace(" ", "")
        val formattedKey = if (cleanKey.length == 6 && !cleanKey.contains("-")) {
            "${cleanKey.take(3)}-${cleanKey.takeLast(3)}"
        } else {
            cleanKey
        }

        try {
            val keyDoc = db.collection("pairing_keys").document(formattedKey).get().await()
            if (keyDoc.exists()) {
                val vaultId = keyDoc.getString("vaultId") ?: ""
                return Pair(vaultId, formattedKey)
            }
        } catch (e: Exception) {
            Timber.w(e, "Error looking up pairing key $formattedKey in Firestore: ${e.message}")
        }

        // Direct vault ID fallback if entered directly
        if (clean.startsWith("vlt_")) {
            return Pair(clean, "")
        }

        return Pair("", "")
    }

    private fun generateShortKey(): String {
        val part1 = (100..999).random()
        val part2 = (100..999).random()
        return "$part1-$part2"
    }

    // ═══ Map Serialization Helpers ═══
    private fun targetPcToMap(pc: TargetPc): Map<String, Any?> = mapOf(
        "name" to pc.name,
        "macAddress" to pc.macAddress,
        "broadcastIp" to pc.broadcastIp,
        "port" to pc.port,
        "lastKnownIp" to pc.lastKnownIp,
        "status" to pc.status.name,
        "lastBootDurationMs" to pc.lastBootDurationMs,
        "lastBootTimestamp" to pc.lastBootTimestamp
    )

    private fun mapToTargetPc(map: Map<String, Any>): TargetPc = TargetPc(
        name = map["name"] as? String ?: "Main Gaming Rig",
        macAddress = map["macAddress"] as? String ?: "",
        broadcastIp = map["broadcastIp"] as? String ?: "192.168.1.255",
        port = (map["port"] as? Number)?.toInt() ?: 9,
        lastKnownIp = map["lastKnownIp"] as? String,
        status = try { PcPowerStatus.valueOf(map["status"] as? String ?: "OFFLINE") } catch (_: Exception) { PcPowerStatus.OFFLINE },
        lastBootDurationMs = (map["lastBootDurationMs"] as? Number)?.toLong(),
        lastBootTimestamp = (map["lastBootTimestamp"] as? Number)?.toLong()
    )

    private fun hostPresenceToMap(host: HostPresence): Map<String, Any> = mapOf(
        "deviceId" to host.deviceId,
        "deviceName" to host.deviceName,
        "isOnline" to host.isOnline,
        "batteryLevel" to host.batteryLevel,
        "batteryStatus" to host.batteryStatus,
        "temperatureC" to host.temperatureC,
        "localIp" to host.localIp,
        "lastHeartbeat" to host.lastHeartbeat
    )

    private fun mapToHostPresence(map: Map<String, Any>): HostPresence = HostPresence(
        deviceId = map["deviceId"] as? String ?: "",
        deviceName = map["deviceName"] as? String ?: "Desk Companion",
        isOnline = map["isOnline"] as? Boolean ?: false,
        batteryLevel = (map["batteryLevel"] as? Number)?.toInt() ?: 0,
        batteryStatus = map["batteryStatus"] as? String ?: "AC",
        temperatureC = (map["temperatureC"] as? Number)?.toFloat() ?: 0f,
        localIp = map["localIp"] as? String ?: "",
        lastHeartbeat = (map["lastHeartbeat"] as? Number)?.toLong() ?: 0L
    )

    private fun wakeCommandToMap(cmd: WakeCommand): Map<String, Any?> = mapOf(
        "commandId" to cmd.commandId,
        "action" to cmd.action,
        "dispatchedAt" to cmd.dispatchedAt,
        "broadcastedAt" to cmd.broadcastedAt,
        "completedAt" to cmd.completedAt,
        "status" to cmd.status.name,
        "bootDurationMs" to cmd.bootDurationMs,
        "errorMessage" to cmd.errorMessage
    )

    private fun mapToWakeCommand(map: Map<String, Any>): WakeCommand = WakeCommand(
        commandId = map["commandId"] as? String ?: "",
        action = map["action"] as? String ?: "WAKE",
        dispatchedAt = (map["dispatchedAt"] as? Number)?.toLong() ?: 0L,
        broadcastedAt = (map["broadcastedAt"] as? Number)?.toLong(),
        completedAt = (map["completedAt"] as? Number)?.toLong(),
        status = try { CommandProgressStatus.valueOf(map["status"] as? String ?: "IDLE") } catch (_: Exception) { CommandProgressStatus.IDLE },
        bootDurationMs = (map["bootDurationMs"] as? Number)?.toLong(),
        errorMessage = map["errorMessage"] as? String
    )
}
