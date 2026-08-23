package org.openmomentum.app.integration

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import org.openmomentum.app.model.HeadphoneState
import org.openmomentum.app.tile.NoiseModeTileService
import org.openmomentum.app.widget.MomentumWidgetProvider

object IntegrationUpdater {
    fun publish(context: Context, state: HeadphoneState) {
        val appContext = context.applicationContext
        MomentumNotification.publish(appContext, state)
        MomentumWidgetProvider.updateAll(appContext, state)
        TileService.requestListeningState(
            appContext,
            ComponentName(appContext, NoiseModeTileService::class.java),
        )
    }
}
