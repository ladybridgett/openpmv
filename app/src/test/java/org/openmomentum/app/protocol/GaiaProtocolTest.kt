package org.openmomentum.app.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GaiaProtocolTest {
    @Test
    fun packetRoundTrip() {
        val packet = GaiaPacket(0x0495, 0x1a02, byteArrayOf(100))
        val decoded = GaiaPacket.decode(packet.encode())
        assertEquals(packet.vendorId, decoded.vendorId)
        assertEquals(packet.commandId, decoded.commandId)
        assertArrayEquals(packet.payload, decoded.payload)
    }

    @Test
    fun sppFrameUsesGaiaPayloadLength() {
        val frame = GaiaSpp.frame(GaiaPacket(0x0495, 0x0603))
        assertArrayEquals(
            byteArrayOf(0xff.toByte(), 0x03, 0x00, 0x00, 0x04, 0x95.toByte(), 0x06, 0x03),
            frame,
        )
    }

    @Test
    fun deframerHandlesJunkAndSplitFrames() {
        val framed = GaiaSpp.frame(GaiaPacket(0x0495, 0x0703, byteArrayOf(84)))
        val deframer = GaiaSppDeframer()

        assertEquals(emptyList<GaiaPacket>(), deframer.ingest(byteArrayOf(0x12, 0x34) + framed.copyOfRange(0, 5)))
        val packets = deframer.ingest(framed.copyOfRange(5, framed.size))

        assertEquals(1, packets.size)
        assertEquals(0x0703, packets.single().commandId)
        assertArrayEquals(byteArrayOf(84), packets.single().payload)
    }

    @Test
    fun parsesAncModesByFeatureIdentifier() {
        val packet = GaiaPacket(
            MomentumProtocol.VENDOR_ID,
            MomentumProtocol.GET_ANC_MODES_RESPONSE,
            byteArrayOf(1, 2, 2, 0, 3, 1),
        )
        assertEquals(true, MomentumProtocol.parseAdaptiveEnabled(packet))
    }
}
