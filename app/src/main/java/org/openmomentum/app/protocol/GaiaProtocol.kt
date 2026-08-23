package org.openmomentum.app.protocol

data class GaiaPacket(
    val vendorId: Int,
    val commandId: Int,
    val payload: ByteArray = byteArrayOf(),
) {
    fun encode(): ByteArray = byteArrayOf(
        (vendorId ushr 8).toByte(), vendorId.toByte(),
        (commandId ushr 8).toByte(), commandId.toByte(),
    ) + payload

    companion object {
        fun decode(data: ByteArray): GaiaPacket {
            require(data.size >= 4) { "GAIA packet is shorter than four bytes" }
            return GaiaPacket(
                vendorId = data.u16(0),
                commandId = data.u16(2),
                payload = data.copyOfRange(4, data.size),
            )
        }
    }
}

object GaiaSpp {
    fun frame(packet: GaiaPacket): ByteArray {
        val body = packet.encode()
        val payloadLength = body.size - 4
        require(payloadLength <= 0xffff)
        return byteArrayOf(
            0xff.toByte(), 0x03,
            (payloadLength ushr 8).toByte(), payloadLength.toByte(),
        ) + body
    }
}

class GaiaSppDeframer {
    private var buffer = byteArrayOf()

    fun ingest(incoming: ByteArray): List<GaiaPacket> {
        buffer += incoming
        val packets = mutableListOf<GaiaPacket>()
        while (true) {
            var sync = 0
            while (sync + 1 < buffer.size &&
                !(buffer[sync] == 0xff.toByte() && buffer[sync + 1] == 0x03.toByte())) {
                sync++
            }
            if (sync > 0) buffer = buffer.copyOfRange(sync, buffer.size)
            if (buffer.size < 4) break

            val payloadLength = buffer.u16(2)
            val frameLength = 8 + payloadLength
            if (buffer.size < frameLength) break

            packets += GaiaPacket.decode(buffer.copyOfRange(4, frameLength))
            buffer = buffer.copyOfRange(frameLength, buffer.size)
        }
        return packets
    }
}

internal fun ByteArray.u16(offset: Int): Int =
    ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)
