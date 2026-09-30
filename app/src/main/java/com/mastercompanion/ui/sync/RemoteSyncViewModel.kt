package com.mastercompanion.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mastercompanion.data.network.LanPcScanner
import com.mastercompanion.data.network.WolSender
import com.mastercompanion.data.remote.RemoteVaultRepository
import com.mastercompanion.data.remote.model.CommandProgressStatus
import com.mastercompanion.data.remote.model.DeviceRole
import com.mastercompanion.data.remote.model.DiscoveredPc
import com.mastercompanion.data.remote.model.TargetPc
import com.mastercompanion.data.remote.model.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RemoteSyncViewModel @Inject constructor(
    private val remoteVaultRepository: RemoteVaultRepository,
    private val lanPcScanner: LanPcScanner,
    private val wolSender: WolSender
) : ViewModel() {

    val vaultState: StateFlow<VaultState> = remoteVaultRepository.vaultState

    private val _discoveredPcs = MutableStateFlow<List<DiscoveredPc>>(emptyList())
    val discoveredPcs: StateFlow<List<DiscoveredPc>> = _discoveredPcs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshCooldown = MutableStateFlow(0)
    val refreshCooldown: StateFlow<Int> = _refreshCooldown.asStateFlow()

    private val _stopwatchSeconds = MutableStateFlow(0.0f)
    val stopwatchSeconds: StateFlow<Float> = _stopwatchSeconds.asStateFlow()

    private var stopwatchJob: Job? = null

    init {
        // Observe command changes to run live stopwatch on Waker device
        viewModelScope.launch {
            vaultState.collect { state ->
                val cmd = state.latestCommand
                if (cmd != null && (cmd.status == CommandProgressStatus.PENDING ||
                            cmd.status == CommandProgressStatus.BROADCASTED ||
                            cmd.status == CommandProgressStatus.VERIFYING)) {
                    if (stopwatchJob == null || stopwatchJob?.isActive == false) {
                        startStopwatch(cmd.dispatchedAt)
                    }
                } else {
                    stopwatchJob?.cancel()
                    stopwatchJob = null
                    if (cmd?.status == CommandProgressStatus.ONLINE && cmd.bootDurationMs != null) {
                        _stopwatchSeconds.value = (cmd.bootDurationMs / 1000f)
                    }
                }
            }
        }
    }

    fun initAsHost() {
        viewModelScope.launch {
            remoteVaultRepository.initAsHost()
        }
    }

    fun pairAsWaker(rawKeyOrPayload: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val result = remoteVaultRepository.pairAsWaker(rawKeyOrPayload)
            if (result.isSuccess) {
                onResult(true, "Paired successfully with Desk Host!")
            } else {
                val err = result.exceptionOrNull()?.message ?: "Pairing failed. Check key & internet."
                onResult(false, err)
            }
        }
    }

    fun setRole(role: DeviceRole) {
        viewModelScope.launch {
            if (role == DeviceRole.HOST) {
                initAsHost()
            } else if (role == DeviceRole.WAKER) {
                remoteVaultRepository.setDeviceRole(DeviceRole.WAKER)
            }
        }
    }

    fun scanForPcs() {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredPcs.value = emptyList()

        viewModelScope.launch {
            try {
                lanPcScanner.scanSubnet().collect { pc ->
                    val current = _discoveredPcs.value.toMutableList()
                    if (current.none { it.macAddress == pc.macAddress }) {
                        current.add(pc)
                        _discoveredPcs.value = current
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Error scanning LAN for PCs")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun selectDiscoveredPc(pc: DiscoveredPc) {
        viewModelScope.launch {
            val current = vaultState.value.targetPc ?: TargetPc()
            val derivedBcast = if (pc.ipAddress.isNotBlank()) {
                wolSender.calculateSubnetBroadcast(pc.ipAddress) ?: "192.168.1.255"
            } else "192.168.1.255"
            val updated = current.copy(
                name = pc.name,
                macAddress = pc.macAddress,
                lastKnownIp = pc.ipAddress,
                broadcastIp = derivedBcast
            )
            remoteVaultRepository.updateTargetPc(updated)
        }
    }

    fun updatePcManually(name: String, mac: String, broadcastIp: String, ip: String?) {
        viewModelScope.launch {
            val updated = TargetPc(
                name = name.ifBlank { "Main PC" },
                macAddress = mac.trim().uppercase(),
                broadcastIp = broadcastIp.ifBlank { "192.168.1.255" }.trim(),
                lastKnownIp = ip?.ifBlank { null }?.trim()
            )
            remoteVaultRepository.updateTargetPc(updated)
        }
    }

    fun dispatchWake() {
        viewModelScope.launch {
            remoteVaultRepository.dispatchWakeCommand()
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _isRefreshing.value = true
            remoteVaultRepository.requestStatusRefresh()
            delay(500L)
            _isRefreshing.value = false
        }
    }

    private fun startStopwatch(dispatchedAt: Long) {
        stopwatchJob?.cancel()
        stopwatchJob = viewModelScope.launch {
            while (isActive) {
                val elapsedMs = System.currentTimeMillis() - dispatchedAt
                _stopwatchSeconds.value = (elapsedMs / 100L) / 10f
                delay(100L)
            }
        }
    }

    fun testLocalWol(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val pc = vaultState.value.targetPc
            val mac = pc?.macAddress?.ifBlank { null }
            if (mac.isNullOrBlank()) {
                onResult(false, "No MAC address configured! Select a PC above or use Edit Manual.")
                return@launch
            }
            val bcast = pc.broadcastIp.ifBlank { "192.168.1.255" }
            val success = wolSender.sendWol(mac, bcast)
            if (success) {
                onResult(true, "WoL Magic Packet sent to $mac on LAN!")
            } else {
                onResult(false, "Failed to broadcast WoL packet")
            }
        }
    }

    fun unlink() {
        viewModelScope.launch {
            remoteVaultRepository.unlinkVault()
        }
    }
}
