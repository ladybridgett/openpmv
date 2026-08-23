package org.openmomentum.app.state

import android.content.Context
import org.openmomentum.app.model.HeadphoneState
import org.openmomentum.app.model.NoiseMode

class MomentumPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("momentum_state", Context.MODE_PRIVATE)

    fun load(): HeadphoneState = HeadphoneState(
        reachable = preferences.getBoolean(KEY_REACHABLE, false),
        batteryPercent = preferences.getInt(KEY_BATTERY, -1).takeIf { it >= 0 },
        noiseMode = runCatching {
            NoiseMode.valueOf(preferences.getString(KEY_MODE, NoiseMode.UNKNOWN.name)!!)
        }.getOrDefault(NoiseMode.UNKNOWN),
        transparencyLevel = preferences.getInt(KEY_LEVEL, -1).takeIf { it >= 0 },
        updatedAtMillis = preferences.getLong(KEY_UPDATED, 0L),
        error = preferences.getString(KEY_ERROR, null),
    )

    fun save(state: HeadphoneState) {
        preferences.edit()
            .putBoolean(KEY_REACHABLE, state.reachable)
            .putInt(KEY_BATTERY, state.batteryPercent ?: -1)
            .putString(KEY_MODE, state.noiseMode.name)
            .putInt(KEY_LEVEL, state.transparencyLevel ?: -1)
            .putLong(KEY_UPDATED, state.updatedAtMillis)
            .putString(KEY_ERROR, state.error)
            .apply()
    }

    companion object {
        private const val KEY_REACHABLE = "reachable"
        private const val KEY_BATTERY = "battery"
        private const val KEY_MODE = "mode"
        private const val KEY_LEVEL = "level"
        private const val KEY_UPDATED = "updated"
        private const val KEY_ERROR = "error"
    }
}
