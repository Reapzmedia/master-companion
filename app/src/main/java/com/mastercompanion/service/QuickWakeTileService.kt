package com.mastercompanion.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.mastercompanion.data.remote.RemoteVaultRepository
import com.mastercompanion.data.remote.model.CommandProgressStatus
import com.mastercompanion.data.remote.model.PcPowerStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class QuickWakeTileService : TileService() {

    @Inject
    lateinit var remoteVaultRepository: RemoteVaultRepository

    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope?.launch {
            remoteVaultRepository.vaultState.collectLatest { state ->
                val tile = qsTile ?: return@collectLatest
                val pc = state.targetPc
                val cmd = state.latestCommand

                val isWaking = cmd?.status == CommandProgressStatus.PENDING ||
                        cmd?.status == CommandProgressStatus.BROADCASTED ||
                        cmd?.status == CommandProgressStatus.VERIFYING
                val isOnline = pc?.status == PcPowerStatus.ONLINE || cmd?.status == CommandProgressStatus.ONLINE

                when {
                    isWaking -> {
                        tile.state = Tile.STATE_ACTIVE
                        tile.label = "Waking PC..."
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            tile.subtitle = pc?.name ?: "Target PC"
                        }
                    }
                    isOnline -> {
                        tile.state = Tile.STATE_ACTIVE
                        tile.label = "PC Online"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            tile.subtitle = pc?.name ?: "Ready"
                        }
                    }
                    else -> {
                        tile.state = Tile.STATE_INACTIVE
                        tile.label = "Wake PC"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            tile.subtitle = pc?.name ?: "Tap to wake"
                        }
                    }
                }
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        scope?.cancel()
        scope = null
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return

        tile.state = Tile.STATE_ACTIVE
        tile.label = "Waking..."
        tile.updateTile()

        scope?.launch(Dispatchers.IO) {
            try {
                remoteVaultRepository.dispatchWakeCommand()
                Timber.i("Quick Settings tile dispatched wake command")
            } catch (e: Exception) {
                Timber.e(e, "Error dispatching wake from tile")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope?.cancel()
        scope = null
    }
}
