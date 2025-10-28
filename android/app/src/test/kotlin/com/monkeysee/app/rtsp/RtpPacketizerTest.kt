package com.monkeysee.app.rtsp

import org.junit.Test
import org.junit.Assert.*

class RtpPacketizerTest {

    @Test
    fun `single NAL unit packet for small frame`() {
        val packetizer = RtpPacketizer(ssrc = 0x12345678, payloadType = 96)

        // Small NAL unit with start code
        val nalUnit = byteArrayOf(
            0x00, 0x00, 0x00, 0x01, // Start code
            0x65, // NAL header (IDR frame)
            0x11, 0x22, 0x33, 0x44  // Payload
        )

        val packets = packetizer.packetize(nalUnit, 1000000) // 1 second timestamp

        // Should create single packet
        assertEquals(1, packets.size)

        val packet = packets[0]

        // Check RTP header (12 bytes)
        assertEquals(0x80.toByte(), packet[0]) // V=2, P=0, X=0, CC=0
        assertTrue((packet[1].toInt() and 0x80) != 0) // Marker bit should be set for last packet
        assertEquals(96, packet[1].toInt() and 0x7F) // Payload type

        // Check SSRC (bytes 8-11)
        val ssrc = ((packet[8].toInt() and 0xFF) shl 24) or
                   ((packet[9].toInt() and 0xFF) shl 16) or
                   ((packet[10].toInt() and 0xFF) shl 8) or
                   (packet[11].toInt() and 0xFF)
        assertEquals(0x12345678, ssrc)

        // Check payload contains NAL unit (without start code)
        assertEquals(0x65, packet[12]) // NAL header
        assertEquals(0x11, packet[13])
        assertEquals(0x22, packet[14])
        assertEquals(0x33, packet[15])
        assertEquals(0x44, packet[16])
    }

    @Test
    fun `fragmented packets for large frame`() {
        val packetizer = RtpPacketizer(ssrc = 0xAABBCCDD, payloadType = 96)

        // Create large NAL unit that requires fragmentation (>1400 bytes)
        val largePayload = ByteArray(2000) { it.toByte() }
        val nalUnit = ByteArray(4 + 1 + largePayload.size)
        nalUnit[0] = 0x00
        nalUnit[1] = 0x00
        nalUnit[2] = 0x00
        nalUnit[3] = 0x01
        nalUnit[4] = 0x65 // IDR frame NAL header
        System.arraycopy(largePayload, 0, nalUnit, 5, largePayload.size)

        val packets = packetizer.packetize(nalUnit, 2000000)

        // Should be fragmented into multiple packets
        assertTrue(packets.size > 1)

        // Check first packet has FU-A indicator and start bit
        val firstPacket = packets[0]
        assertEquals(0x7C, firstPacket[12].toInt() and 0xFF) // FU indicator (NRI=3, Type=28)
        assertEquals(0x85.toByte(), firstPacket[13]) // FU header with start bit and type 5

        // Check middle packets (if any) have no start/end bits
        if (packets.size > 2) {
            for (i in 1 until packets.size - 1) {
                val middlePacket = packets[i]
                assertEquals(0x05, middlePacket[13].toInt() and 0x1F) // Only type, no S or E bit
            }
        }

        // Check last packet has end bit
        val lastPacket = packets.last()
        assertEquals(0x45, lastPacket[13].toInt() and 0xFF) // FU header with end bit
        assertTrue((lastPacket[1].toInt() and 0x80) != 0) // Marker bit set on last packet
    }

    @Test
    fun `timestamp conversion to 90kHz clock`() {
        val packetizer = RtpPacketizer()

        val nalUnit = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65)

        // 1 second in microseconds = 1,000,000
        // Should convert to 90,000 in 90kHz clock
        packetizer.packetize(nalUnit, 1_000_000)
        assertEquals(90_000L, packetizer.getTimestamp())

        // 2 seconds
        packetizer.packetize(nalUnit, 2_000_000)
        assertEquals(180_000L, packetizer.getTimestamp())
    }

    @Test
    fun `sequence number increments`() {
        val packetizer = RtpPacketizer()

        val nalUnit = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x11)

        val initialSeq = packetizer.getSequenceNumber()

        // Send one packet
        packetizer.packetize(nalUnit, 1000)
        val afterFirstSeq = packetizer.getSequenceNumber()

        assertEquals(initialSeq + 1, afterFirstSeq and 0xFFFF)

        // Send another
        packetizer.packetize(nalUnit, 2000)
        val afterSecondSeq = packetizer.getSequenceNumber()

        assertEquals(afterFirstSeq + 1, afterSecondSeq and 0xFFFF)
    }

    @Test
    fun `start code variations handled correctly`() {
        val packetizer = RtpPacketizer()

        // 4-byte start code
        val nalUnit1 = byteArrayOf(0x00, 0x00, 0x00, 0x01, 0x65, 0x11)
        val packets1 = packetizer.packetize(nalUnit1, 1000)
        assertTrue(packets1[0][12] == 0x65.toByte()) // NAL header preserved

        // 3-byte start code
        val nalUnit2 = byteArrayOf(0x00, 0x00, 0x01, 0x65, 0x22)
        val packets2 = packetizer.packetize(nalUnit2, 2000)
        assertTrue(packets2[0][12] == 0x65.toByte()) // NAL header preserved

        // No start code
        val nalUnit3 = byteArrayOf(0x65, 0x33)
        val packets3 = packetizer.packetize(nalUnit3, 3000)
        assertTrue(packets3[0][12] == 0x65.toByte()) // NAL header preserved
    }

    @Test
    fun `packet size does not exceed MTU`() {
        val packetizer = RtpPacketizer()

        // Create very large NAL unit
        val largeNalUnit = ByteArray(10000)
        largeNalUnit[0] = 0x00
        largeNalUnit[1] = 0x00
        largeNalUnit[2] = 0x00
        largeNalUnit[3] = 0x01
        largeNalUnit[4] = 0x65

        val packets = packetizer.packetize(largeNalUnit, 1000)

        // Every packet should be <= 1400 bytes (MTU)
        for (packet in packets) {
            assertTrue("Packet size ${packet.size} exceeds MTU", packet.size <= 1400)
        }
    }
}
