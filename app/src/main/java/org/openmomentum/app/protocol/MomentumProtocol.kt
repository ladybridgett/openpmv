package org.openmomentum.app.protocol

object MomentumProtocol {
    const val SERVICE_UUID = "a2129ff3-081b-4c45-8afe-469d9c4842ec"
    const val VENDOR_ID = 0x0495

    const val BATTERY = 0x0603
    const val BATTERY_RESPONSE = 0x0703

    const val SET_ANC_MODE = 0x1a00
    const val SET_ANC_MODE_RESPONSE = 0x1b00
    const val GET_ANC_MODES = 0x1a01
    const val GET_ANC_MODES_RESPONSE = 0x1b01
    const val SET_TRANSPARENCY_LEVEL = 0x1a02
    const val SET_TRANSPARENCY_LEVEL_RESPONSE = 0x1b02
    const val GET_TRANSPARENCY_LEVEL = 0x1a03
    const val GET_TRANSPARENCY_LEVEL_RESPONSE = 0x1b03
    const val SET_ANC_ENABLED = 0x1a04
    const val SET_ANC_ENABLED_RESPONSE = 0x1b04
    const val GET_ANC_ENABLED = 0x1a05
    const val GET_ANC_ENABLED_RESPONSE = 0x1b05

    const val SET_TRANSPARENT_HEARING = 0x1804
    const val SET_TRANSPARENT_HEARING_RESPONSE = 0x1904

    const val ANC_MODE_ADAPTIVE = 3

    fun query(command: Int) = GaiaPacket(VENDOR_ID, command)

    fun write(command: Int, vararg payload: Int) = GaiaPacket(
        VENDOR_ID,
        command,
        payload.map { it.coerceIn(0, 255).toByte() }.toByteArray(),
    )

    fun parseBoolean(packet: GaiaPacket): Boolean {
        require(packet.payload.isNotEmpty()) { "Missing boolean response" }
        val value = packet.payload[0].toInt() and 0xff
        require(value <= 1) { "Invalid boolean response: $value" }
        return value == 1
    }

    fun parseBattery(packet: GaiaPacket): Int {
        require(packet.payload.isNotEmpty()) { "Missing battery response" }
        return (packet.payload[0].toInt() and 0xff).also {
            require(it in 0..100) { "Invalid battery percentage: $it" }
        }
    }

    fun parseTransparency(packet: GaiaPacket): Int {
        require(packet.payload.isNotEmpty()) { "Missing transparency response" }
        return (packet.payload[0].toInt() and 0xff).also {
            require(it in 0..100) { "Invalid transparency level: $it" }
        }
    }

    fun parseAdaptiveEnabled(packet: GaiaPacket): Boolean {
        require(packet.payload.size >= 2 && packet.payload.size % 2 == 0) {
            "Malformed ANC modes response"
        }
        for (offset in packet.payload.indices step 2) {
            val mode = packet.payload[offset].toInt() and 0xff
            val state = packet.payload[offset + 1].toInt() and 0xff
            if (mode == ANC_MODE_ADAPTIVE) {
                require(state <= 1) { "Invalid adaptive ANC state: $state" }
                return state == 1
            }
        }
        throw IllegalArgumentException("Adaptive ANC state is absent")
    }

    fun failureFor(successResponse: Int): Int = successResponse or 0x0080
}
