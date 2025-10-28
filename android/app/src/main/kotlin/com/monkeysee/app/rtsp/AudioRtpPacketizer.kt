package com.monkeysee.app.rtsp

import android.util.Log
import java.nio.ByteBuffer

class AudioRtpPacketizer(
    private val ssrc: Int = (Math.random() * Int.MAX_VALUE).toInt(),
    private val payloadType: Int = 97, // AAC
    private val sampleRate: Int = 48000
) {
    private var sequenceNumber: Int = (Math.random() * 65535).toInt()
    private var timestamp: Long = 0

    companion object {
        private const val TAG = "AudioRtpPacketizer"
        private const val RTP_VERSION = 2
        private const val RTP_HEADER_SIZE = 12
        private const val AAC_HEADER_SIZE = 4 // AU-header-section
        private const val MAX_PAYLOAD_SIZE = 1400 - RTP_HEADER_SIZE - AAC_HEADER_SIZE
    }

    /**
     * Packetize AAC audio frame into RTP packet
     * AAC-hbr mode (RFC 3640)
     */
    fun packetize(aacFrame: ByteArray, timestampUs: Long): ByteArray {
        // Update timestamp (convert from microseconds to audio clock rate)
        timestamp = (timestampUs * sampleRate) / 1_000_000

        val frameSize = aacFrame.size

        // For AAC-hbr, we include AU-headers
        // AU-header: 16 bits = size (13 bits) + index (3 bits)
        val auHeaderSize = (frameSize shl 3) // size in bits, shifted left 3 for index=0

        val totalSize = RTP_HEADER_SIZE + AAC_HEADER_SIZE + aacFrame.size
        val packet = ByteArray(totalSize)
        val buffer = ByteBuffer.wrap(packet)

        // RTP header
        writeRtpHeader(buffer, true) // marker=true for audio frames

        // AU-header-section (RFC 3640 section 3.2.1)
        // AU-headers-length (16 bits) = number of bits in AU-headers
        buffer.putShort(16) // 16 bits for one AU-header

        // AU-header (16 bits)
        buffer.putShort(auHeaderSize.toShort())

        // AAC payload
        buffer.put(aacFrame)

        sequenceNumber = (sequenceNumber + 1) and 0xFFFF

        Log.d(TAG, "Packetized AAC frame: ${aacFrame.size} bytes, seq=$sequenceNumber, ts=$timestamp")

        return packet
    }

    /**
     * Packetize AAC configuration (AudioSpecificConfig)
     * Sent once at stream start
     */
    fun packetizeConfig(config: ByteArray, timestampUs: Long): ByteArray {
        timestamp = (timestampUs * sampleRate) / 1_000_000

        val packet = ByteArray(RTP_HEADER_SIZE + config.size)
        val buffer = ByteBuffer.wrap(packet)

        // RTP header with marker bit set
        writeRtpHeader(buffer, true)

        // AAC config as payload
        buffer.put(config)

        sequenceNumber = (sequenceNumber + 1) and 0xFFFF

        Log.d(TAG, "Packetized AAC config: ${config.size} bytes")

        return packet
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

    fun getSequenceNumber(): Int = sequenceNumber
    fun getTimestamp(): Long = timestamp
    fun getSsrc(): Int = ssrc
}
