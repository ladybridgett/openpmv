package org.openmomentum.app.repository

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.openmomentum.app.bluetooth.MomentumClient
import org.openmomentum.app.integration.IntegrationUpdater
import org.openmomentum.app.model.HeadphoneState
import org.openmomentum.app.state.MomentumPreferences
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MomentumRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val client = MomentumClient(appContext)
    private val preferences = MomentumPreferences(appContext)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun cachedState(): HeadphoneState = preferences.load()

    fun refresh(callback: (HeadphoneState) -> Unit = {}) = runOperation(client::readState, callback)

    fun setNoiseLevel(level: Int, callback: (HeadphoneState) -> Unit = {}) =
        runOperation({ client.setNoiseLevel(level) }, callback)

    fun turnOff(callback: (HeadphoneState) -> Unit = {}) =
        runOperation(client::turnNoiseControlOff, callback)

    fun markDisconnected() {
        val state = preferences.load().copy(
            reachable = false,
            updatedAtMillis = System.currentTimeMillis(),
            error = null,
        )
        preferences.save(state)
        mainHandler.post { IntegrationUpdater.publish(appContext, state) }
    }

    private fun runOperation(
        operation: () -> HeadphoneState,
        callback: (HeadphoneState) -> Unit,
    ) {
        executor.execute {
            val state = try {
                operation()
            } catch (error: Exception) {
                val previous = preferences.load()
                previous.copy(
                    reachable = false,
                    updatedAtMillis = System.currentTimeMillis(),
                    error = error.message ?: error.javaClass.simpleName,
                )
            }
            preferences.save(state)
            mainHandler.post {
                IntegrationUpdater.publish(appContext, state)
                callback(state)
            }
        }
    }

    companion object {
        @Volatile private var instance: MomentumRepository? = null

        fun get(context: Context): MomentumRepository = instance ?: synchronized(this) {
            instance ?: MomentumRepository(context).also { instance = it }
        }
    }
}
