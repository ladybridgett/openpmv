package org.openmomentum.app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import org.openmomentum.app.model.HeadphoneState
import org.openmomentum.app.model.NoiseMode
import org.openmomentum.app.protocol.GaiaPacket
import org.openmomentum.app.protocol.GaiaSpp
import org.openmomentum.app.protocol.GaiaSppDeframer
import org.openmomentum.app.protocol.MomentumProtocol
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MomentumClient(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)

    @SuppressLint("MissingPermission")
    fun readState(): HeadphoneState = withSession { session ->
        val battery = MomentumProtocol.parseBattery(
            session.exchange(MomentumProtocol.query(MomentumProtocol.BATTERY), MomentumProtocol.BATTERY_RESPONSE),
        )
        val ancEnabled = MomentumProtocol.parseBoolean(
            session.exchange(MomentumProtocol.query(MomentumProtocol.GET_ANC_ENABLED), MomentumProtocol.GET_ANC_ENABLED_RESPONSE),
        )
        val ancModes = session.exchange(
            MomentumProtocol.query(MomentumProtocol.GET_ANC_MODES),
            MomentumProtocol.GET_ANC_MODES_RESPONSE,
        )
        val adaptive = MomentumProtocol.parseAdaptiveEnabled(ancModes)
        val level = MomentumProtocol.parseTransparency(
            session.exchange(
                MomentumProtocol.query(MomentumProtocol.GET_TRANSPARENCY_LEVEL),
                MomentumProtocol.GET_TRANSPARENCY_LEVEL_RESPONSE,
            ),
        )
        HeadphoneState(
            reachable = true,
            batteryPercent = battery,
            noiseMode = NoiseMode.resolve(ancEnabled, adaptive, level),
            transparencyLevel = level,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    fun setNoiseLevel(level: Int): HeadphoneState = withSession { session ->
        val safeLevel = level.coerceIn(0, 100)
        session.write(
            MomentumProtocol.write(MomentumProtocol.SET_TRANSPARENT_HEARING, 0),
            MomentumProtocol.SET_TRANSPARENT_HEARING_RESPONSE,
        )
        session.write(
            MomentumProtocol.write(MomentumProtocol.SET_ANC_ENABLED, 1),
            MomentumProtocol.SET_ANC_ENABLED_RESPONSE,
        )
        session.write(
            MomentumProtocol.write(
                MomentumProtocol.SET_ANC_MODE,
                MomentumProtocol.ANC_MODE_ADAPTIVE,
                0,
            ),
            MomentumProtocol.SET_ANC_MODE_RESPONSE,
        )
        session.write(
            MomentumProtocol.write(MomentumProtocol.SET_TRANSPARENCY_LEVEL, safeLevel),
            MomentumProtocol.SET_TRANSPARENCY_LEVEL_RESPONSE,
        )
        readState(session)
    }

    fun turnNoiseControlOff(): HeadphoneState = withSession { session ->
        session.write(
            MomentumProtocol.write(MomentumProtocol.SET_TRANSPARENT_HEARING, 0),
            MomentumProtocol.SET_TRANSPARENT_HEARING_RESPONSE,
        )
        session.write(
            MomentumProtocol.write(MomentumProtocol.SET_ANC_ENABLED, 0),
            MomentumProtocol.SET_ANC_ENABLED_RESPONSE,
        )
        readState(session)
    }

    private fun readState(session: Session): HeadphoneState {
        val battery = MomentumProtocol.parseBattery(
            session.exchange(MomentumProtocol.query(MomentumProtocol.BATTERY), MomentumProtocol.BATTERY_RESPONSE),
        )
        val ancEnabled = MomentumProtocol.parseBoolean(
            session.exchange(MomentumProtocol.query(MomentumProtocol.GET_ANC_ENABLED), MomentumProtocol.GET_ANC_ENABLED_RESPONSE),
        )
        val adaptive = MomentumProtocol.parseAdaptiveEnabled(
            session.exchange(MomentumProtocol.query(MomentumProtocol.GET_ANC_MODES), MomentumProtocol.GET_ANC_MODES_RESPONSE),
        )
        val level = MomentumProtocol.parseTransparency(
            session.exchange(
                MomentumProtocol.query(MomentumProtocol.GET_TRANSPARENCY_LEVEL),
                MomentumProtocol.GET_TRANSPARENCY_LEVEL_RESPONSE,
            ),
        )
        return HeadphoneState(
            reachable = true,
            batteryPercent = battery,
            noiseMode = NoiseMode.resolve(ancEnabled, adaptive, level),
            transparencyLevel = level,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    @SuppressLint("MissingPermission")
    private fun <T> withSession(block: (Session) -> T): T {
        requirePermission()
        val adapter = bluetoothManager.adapter ?: throw MomentumException("Bluetooth is unavailable")
        if (!adapter.isEnabled) throw MomentumException("Bluetooth is turned off")
        val device = findHeadset(adapter)
        val socket = device.createRfcommSocketToServiceRecord(UUID.fromString(MomentumProtocol.SERVICE_UUID))
        connectWithTimeout(socket)
        return Session(socket).use(block)
    }

    @SuppressLint("MissingPermission")
    private fun findHeadset(adapter: BluetoothAdapter): BluetoothDevice {
        val matching = adapter.bondedDevices.filter {
            it.name?.contains("MOMENTUM 4", ignoreCase = true) == true
        }
        return when (matching.size) {
            0 -> throw MomentumException("MOMENTUM 4 is not paired")
            1 -> matching.single()
            else -> throw MomentumException("More than one MOMENTUM 4 is paired")
        }
    }

    private fun requirePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            throw MomentumException("Nearby devices permission is required")
        }
    }

    private fun connectWithTimeout(socket: BluetoothSocket) {
        val closer = Executors.newSingleThreadScheduledExecutor()
        val timeout = closer.schedule({ runCatching { socket.close() } }, CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        try {
            socket.connect()
        } catch (error: IOException) {
            throw MomentumException(
                "Could not open the Momentum control channel. Close Smart Control and try again.",
                error,
            )
        } finally {
            timeout.cancel(false)
            closer.shutdownNow()
        }
    }

    private class Session(private val socket: BluetoothSocket) : Closeable {
        private val input = socket.inputStream
        private val output = socket.outputStream
        private val deframer = GaiaSppDeframer()

        fun exchange(request: GaiaPacket, expectedResponse: Int): GaiaPacket {
            output.write(GaiaSpp.frame(request))
            output.flush()
            val failureResponse = MomentumProtocol.failureFor(expectedResponse)
            val deadline = SystemClock.elapsedRealtime() + RESPONSE_TIMEOUT_MS
            val chunk = ByteArray(512)

            while (SystemClock.elapsedRealtime() < deadline) {
                val available = input.available()
                if (available == 0) {
                    SystemClock.sleep(20)
                    continue
                }
                val count = input.read(chunk, 0, minOf(chunk.size, available))
                if (count < 0) throw MomentumException("The Momentum control channel closed")
                for (packet in deframer.ingest(chunk.copyOf(count))) {
                    if (packet.vendorId != MomentumProtocol.VENDOR_ID) continue
                    when (packet.commandId) {
                        expectedResponse -> return packet
                        failureResponse -> {
                            val status = packet.payload.firstOrNull()?.toInt()?.and(0xff)
                            throw MomentumException("The headphones rejected command 0x${request.commandId.toString(16)}${status?.let { " (status $it)" } ?: ""}")
                        }
                    }
                }
            }
            throw MomentumException("The headphones did not answer command 0x${request.commandId.toString(16)}")
        }

        fun write(request: GaiaPacket, expectedResponse: Int) {
            exchange(request, expectedResponse)
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val RESPONSE_TIMEOUT_MS = 5_000L
    }
}

class MomentumException(message: String, cause: Throwable? = null) : Exception(message, cause)
