package com.monkeysee.app.rtsp

import android.util.Log
import java.nio.ByteBuffer

class RtpPacketizer(
    private val ssrc: Int = (Math.random() * Int.MAX_VALUE).toInt(),
    private val payloadType: Int = 96 // H.264
) {
    private var sequenceNumber: Int = (Math.random() * 65535).toInt()
    private var timestamp: Long = 0

    companion object {
        private const val TAG = "RtpPacketizer"
        private const val RTP_VERSION = 2
        private const val MTU = 1400 // Maximum packet size
        private const val RTP_HEADER_SIZE = 12
        private const val FU_A_HEADER_SIZE = 2
        private const val MAX_PAYLOAD_SIZE = MTU - RTP_HEADER_SIZE - FU_A_HEADER_SIZE
    }

    fun packetize(nalUnit: ByteArray, timestampUs: Long): List<ByteArray> {
        timestamp = (timestampUs * 90) / 1000 // Convert to 90kHz clock

        // Remove start code if present (0x00000001 or 0x000001)
        var offset = 0
        if (nalUnit.size >= 4 &&
            nalUnit[0] == 0.toByte() &&
            nalUnit[1] == 0.toByte() &&
            nalUnit[2] == 0.toByte() &&
            nalUnit[3] == 1.toByte()) {
            offset = 4
        } else if (nalUnit.size >= 3 &&
            nalUnit[0] == 0.toByte() &&
            nalUnit[1] == 0.toByte() &&
            nalUnit[2] == 1.toByte()) {
            offset = 3
        }

        val nalData = nalUnit.copyOfRange(offset, nalUnit.size)

        return if (nalData.size <= MTU - RTP_HEADER_SIZE) {
            // Single NAL unit packet
            listOf(createSingleNalPacket(nalData, true))
        } else {
            // Fragmented unit (FU-A)
            createFragmentedPackets(nalData)
        }
    }

    private fun createSingleNalPacket(nalData: ByteArray, marker: Boolean): ByteArray {
        val packet = ByteArray(RTP_HEADER_SIZE + nalData.size)
        val buffer = ByteBuffer.wrap(packet)

        // RTP header
        writeRtpHeader(buffer, marker)

        // NAL unit
        buffer.put(nalData)

        sequenceNumber = (sequenceNumber + 1) and 0xFFFF
        return packet
    }

    private fun createFragmentedPackets(nalData: ByteArray): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()
        val nalHeader = nalData[0]
        val nalType = nalHeader.toInt() and 0x1F
        val nri = nalHeader.toInt() and 0x60

        var offset = 1 // Skip NAL header
        var first = true

        while (offset < nalData.size) {
            val remainingSize = nalData.size - offset
            val payloadSize = minOf(remainingSize, MAX_PAYLOAD_SIZE)
            val isLast = (offset + payloadSize) >= nalData.size

            val packet = ByteArray(RTP_HEADER_SIZE + FU_A_HEADER_SIZE + payloadSize)
            val buffer = ByteBuffer.wrap(packet)

            // RTP header
            writeRtpHeader(buffer, isLast)

            // FU indicator (F=0, NRI from original, Type=28 for FU-A)
            val fuIndicator = (nri or 28).toByte()
            buffer.put(fuIndicator)

            // FU header
            val startBit = if (first) 0x80 else 0
            val endBit = if (isLast) 0x40 else 0
            val fuHeader = (startBit or endBit or nalType).toByte()
            buffer.put(fuHeader)

            // Payload
            buffer.put(nalData, offset, payloadSize)

            packets.add(packet)
            sequenceNumber = (sequenceNumber + 1) and 0xFFFF

            offset += payloadSize
            first = false
        }

        Log.d(TAG, "Fragmented NAL unit into ${packets.size} packets")
        return packets
    }

    private fun writeRtpHeader(buffer: ByteBuffer, marker: Boolean) {
        // Byte 0: V(2), P(1), X(1), CC(4)
        val byte0 = (RTP_VERSION shl 6).toByte()
        buffer.put(byte0)

        // Byte 1: M(1), PT(7)
        val markerBit = if (marker) 0x80 else 0x00
        val byte1 = (markerBit or payloadType).toByte()
        buffer.put(byte1)

        // Bytes 2-3: Sequence number
        buffer.putShort(sequenceNumber.toShort())

        // Bytes 4-7: Timestamp
        buffer.putInt(timestamp.toInt())

        // Bytes 8-11: SSRC
        buffer.putInt(ssrc)
    }

    fun updateTimestamp(timestampUs: Long) {
        timestamp = (timestampUs * 90) / 1000
    }

    fun getSequenceNumber(): Int = sequenceNumber
    fun getTimestamp(): Long = timestamp
    fun getSsrc(): Int = ssrc
}
