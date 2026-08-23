package org.openmomentum.app.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.openmomentum.app.model.HeadphoneState
import org.openmomentum.app.repository.MomentumRepository

class MomentumActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val result = goAsync()
        val repository = MomentumRepository.get(context)
        val done: (HeadphoneState) -> Unit = { result.finish() }
        when (intent.action) {
            ACTION_ANC -> repository.setNoiseLevel(0, done)
            ACTION_TRANSPARENCY -> repository.setNoiseLevel(100, done)
            ACTION_OFF -> repository.turnOff(done)
        }
    }

    companion object {
        const val ACTION_ANC = "org.openmomentum.app.notification.ANC"
        const val ACTION_TRANSPARENCY = "org.openmomentum.app.notification.TRANSPARENCY"
        const val ACTION_OFF = "org.openmomentum.app.notification.OFF"
        private val ACTIONS = setOf(ACTION_ANC, ACTION_TRANSPARENCY, ACTION_OFF)
    }
}
