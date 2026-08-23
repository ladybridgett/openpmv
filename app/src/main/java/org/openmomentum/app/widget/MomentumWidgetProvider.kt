package org.openmomentum.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.openmomentum.app.R
import org.openmomentum.app.model.HeadphoneState
import org.openmomentum.app.repository.MomentumRepository

class MomentumWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        val repository = MomentumRepository.get(context)
        update(context, repository.cachedState())
        repository.refresh { update(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in ACTIONS) return
        val pendingResult = goAsync()
        val repository = MomentumRepository.get(context)
        val callback: (HeadphoneState) -> Unit = {
            update(context, it)
            pendingResult.finish()
        }
        when (intent.action) {
            ACTION_ANC -> repository.setNoiseLevel(0, callback)
            ACTION_TRANSPARENCY -> repository.setNoiseLevel(100, callback)
            ACTION_OFF -> repository.turnOff(callback)
            else -> repository.refresh(callback)
        }
    }

    private fun update(context: Context, state: HeadphoneState) {
        val views = RemoteViews(context.packageName, R.layout.momentum_widget).apply {
            setTextViewText(R.id.widget_mode, state.error ?: state.noiseMode.displayName)
            setTextViewText(R.id.widget_battery, state.batteryPercent?.let { "$it%" } ?: "—%")
            setOnClickPendingIntent(R.id.widget_anc, actionIntent(context, ACTION_ANC, 1))
            setOnClickPendingIntent(R.id.widget_transparency, actionIntent(context, ACTION_TRANSPARENCY, 2))
            setOnClickPendingIntent(R.id.widget_off, actionIntent(context, ACTION_OFF, 3))
            setOnClickPendingIntent(R.id.widget_refresh, actionIntent(context, ACTION_REFRESH, 4))
        }
        val manager = AppWidgetManager.getInstance(context)
        manager.updateAppWidget(ComponentName(context, MomentumWidgetProvider::class.java), views)
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MomentumWidgetProvider::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val ACTION_ANC = "org.openmomentum.app.action.ANC"
        private const val ACTION_TRANSPARENCY = "org.openmomentum.app.action.TRANSPARENCY"
        private const val ACTION_OFF = "org.openmomentum.app.action.OFF"
        private const val ACTION_REFRESH = "org.openmomentum.app.action.REFRESH"
        private val ACTIONS = setOf(ACTION_ANC, ACTION_TRANSPARENCY, ACTION_OFF, ACTION_REFRESH)
    }
}
