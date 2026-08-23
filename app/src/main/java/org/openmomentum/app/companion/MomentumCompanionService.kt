package org.openmomentum.app.companion

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Handler
import android.os.Looper
import org.openmomentum.app.repository.MomentumRepository

class MomentumCompanionService : CompanionDeviceService() {
    private val repository by lazy { MomentumRepository.get(this) }
    private val handler = Handler(Looper.getMainLooper())

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED,
            DevicePresenceEvent.EVENT_BT_CONNECTED,
            -> refreshAfterConnection()
            DevicePresenceEvent.EVENT_BT_DISCONNECTED -> repository.markDisconnected()
        }
    }

    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) = refreshAfterConnection()

    @Suppress("DEPRECATION")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) = repository.markDisconnected()

    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(address: String) = refreshAfterConnection()

    @Suppress("DEPRECATION")
    override fun onDeviceDisappeared(address: String) = repository.markDisconnected()

    private fun refreshAfterConnection() {
        handler.postDelayed({ repository.refresh() }, CONNECTION_SETTLE_MS)
    }

    companion object {
        private const val CONNECTION_SETTLE_MS = 750L
    }
}
