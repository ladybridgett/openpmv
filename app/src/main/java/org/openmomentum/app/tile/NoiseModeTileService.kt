package org.openmomentum.app.tile

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.openmomentum.app.model.HeadphoneState
import org.openmomentum.app.model.NoiseMode
import org.openmomentum.app.repository.MomentumRepository
import org.openmomentum.app.ui.MainActivity

class NoiseModeTileService : TileService() {
    private val repository by lazy { MomentumRepository.get(this) }

    override fun onStartListening() {
        super.onStartListening()
        render(repository.cachedState())
    }

    override fun onClick() {
        super.onClick()
        if (!hasBluetoothPermission()) {
            openApp()
            return
        }

        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            label = "Updating…"
            updateTile()
        }

        val current = repository.cachedState().noiseMode
        val callback: (HeadphoneState) -> Unit = { state ->
            render(state)
            TileService.requestListeningState(this, ComponentName(this, NoiseModeTileService::class.java))
        }
        when (current) {
            NoiseMode.OFF, NoiseMode.UNKNOWN -> repository.setNoiseLevel(0, callback)
            NoiseMode.ANC, NoiseMode.BALANCED, NoiseMode.ADAPTIVE -> repository.setNoiseLevel(100, callback)
            NoiseMode.TRANSPARENCY -> repository.turnOff(callback)
        }
    }

    private fun render(headphones: HeadphoneState) {
        qsTile?.apply {
            state = when {
                !headphones.reachable -> Tile.STATE_INACTIVE
                headphones.noiseMode == NoiseMode.OFF -> Tile.STATE_INACTIVE
                else -> Tile.STATE_ACTIVE
            }
            label = when {
                headphones.error != null -> "Momentum unavailable"
                !headphones.reachable -> "Momentum disconnected"
                else -> headphones.noiseMode.displayName
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = headphones.batteryPercent?.let { "MOMENTUM 4 · $it%" } ?: "MOMENTUM 4"
            }
            updateTile()
        }
    }

    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
