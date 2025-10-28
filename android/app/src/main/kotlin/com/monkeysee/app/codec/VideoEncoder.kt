package com.monkeysee.app.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int = 2_000_000,
    private val fps: Int = 30
) {
    private var encoder: MediaCodec? = null
    private var isRunning = false
    private var frameCount = 0L
    private val nanosPerFrame = 1_000_000_000L / fps

    companion object {
        private const val TAG = "VideoEncoder"
        private const val MIME_TYPE = "video/avc"
        private const val I_FRAME_INTERVAL = 2 // seconds
    }

    interface EncodedFrameCallback {
        fun onEncodedFrame(data: ByteArray, timestamp: Long, isKeyFrame: Boolean)
        fun onSpsNalu(sps: ByteArray)
        fun onPpsNalu(pps: ByteArray)
    }

    private var callback: EncodedFrameCallback? = null

    fun setCallback(callback: EncodedFrameCallback) {
        this.callback = callback
    }

    fun start() {
        if (isRunning) {
            Log.w(TAG, "Encoder already running")
            return
        }

        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
            }

            encoder = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            isRunning = true
            frameCount = 0
            Log.i(TAG, "Encoder started: ${width}x${height} @ ${fps}fps, ${bitrate}bps")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start encoder", e)
            throw e
        }
    }

    fun stop() {
        if (!isRunning) return

        try {
            encoder?.apply {
                stop()
                release()
            }
            encoder = null
            isRunning = false
            Log.i(TAG, "Encoder stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping encoder", e)
        }
    }

    fun encodeFrame(imageProxy: ImageProxy) {
        val codec = encoder ?: run {
            Log.w(TAG, "Encoder not initialized")
            return
        }

        if (!isRunning) return

        try {
            // Get input buffer
            val inputBufferId = codec.dequeueInputBuffer(10_000)
            if (inputBufferId >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferId)
                inputBuffer?.let { buffer ->
                    // Convert ImageProxy to YUV420
                    val yuvData = imageProxyToNV21(imageProxy)
                    buffer.clear()
                    buffer.put(yuvData)

                    val presentationTimeUs = frameCount * nanosPerFrame / 1000
                    codec.queueInputBuffer(
                        inputBufferId,
                        0,
                        yuvData.size,
                        presentationTimeUs,
                        0
                    )
                    frameCount++
                }
            }

            // Get encoded output
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 0)

            while (outputBufferId >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferId)
                outputBuffer?.let { buffer ->
                    processEncodedData(buffer, bufferInfo)
                }

                codec.releaseOutputBuffer(outputBufferId, false)
                outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 0)
            }

            if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val format = codec.outputFormat
                Log.i(TAG, "Output format changed: $format")
                extractSpsAndPps(format)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error encoding frame", e)
        }
    }

    private fun processEncodedData(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (bufferInfo.size == 0) return

        val data = ByteArray(bufferInfo.size)
        buffer.position(bufferInfo.offset)
        buffer.get(data)

        val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

        // Parse NAL units
        parseNalUnits(data, bufferInfo.presentationTimeUs, isKeyFrame)
    }

    private fun parseNalUnits(data: ByteArray, timestamp: Long, isKeyFrame: Boolean) {
        var offset = 0

        while (offset < data.size - 4) {
            // Find NAL unit start code (0x00 0x00 0x00 0x01)
            if (data[offset] == 0.toByte() &&
                data[offset + 1] == 0.toByte() &&
                data[offset + 2] == 0.toByte() &&
                data[offset + 3] == 1.toByte()) {

                offset += 4

                if (offset >= data.size) break

                val nalType = (data[offset].toInt() and 0x1F)

                // Find next start code or end of data
                var nextStart = offset + 1
                while (nextStart < data.size - 3) {
                    if (data[nextStart] == 0.toByte() &&
                        data[nextStart + 1] == 0.toByte() &&
                        data[nextStart + 2] == 0.toByte() &&
                        data[nextStart + 3] == 1.toByte()) {
                        break
                    }
                    nextStart++
                }

                val nalData = data.copyOfRange(offset - 4, minOf(nextStart, data.size))

                when (nalType) {
                    7 -> { // SPS
                        callback?.onSpsNalu(nalData)
                        Log.d(TAG, "SPS NAL: ${nalData.size} bytes")
                    }
                    8 -> { // PPS
                        callback?.onPpsNalu(nalData)
                        Log.d(TAG, "PPS NAL: ${nalData.size} bytes")
                    }
                    5 -> { // IDR frame
                        callback?.onEncodedFrame(nalData, timestamp, true)
                        Log.d(TAG, "IDR frame: ${nalData.size} bytes")
                    }
                    1 -> { // P frame
                        callback?.onEncodedFrame(nalData, timestamp, false)
                    }
                }

                offset = nextStart
            } else {
                offset++
            }
        }

        // If no start codes found, send entire buffer
        if (offset == 0) {
            callback?.onEncodedFrame(data, timestamp, isKeyFrame)
        }
    }

    private fun extractSpsAndPps(format: MediaFormat) {
        try {
            if (format.containsKey("csd-0")) {
                val sps = format.getByteBuffer("csd-0")
                sps?.let {
                    val spsData = ByteArray(it.remaining())
                    it.get(spsData)
                    callback?.onSpsNalu(spsData)
                    Log.d(TAG, "Extracted SPS: ${spsData.size} bytes")
                }
            }

            if (format.containsKey("csd-1")) {
                val pps = format.getByteBuffer("csd-1")
                pps?.let {
                    val ppsData = ByteArray(it.remaining())
                    it.get(ppsData)
                    callback?.onPpsNalu(ppsData)
                    Log.d(TAG, "Extracted PPS: ${ppsData.size} bytes")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting SPS/PPS", e)
        }
    }

    private fun imageProxyToNV21(image: ImageProxy): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // Y plane
        yBuffer.get(nv21, 0, ySize)

        // VU plane (NV21 format)
        val uvPixelStride = image.planes[1].pixelStride
        if (uvPixelStride == 1) {
            // Already interleaved, but need to swap U and V
            vBuffer.get(nv21, ySize, vSize)
        } else {
            // Need to interleave V and U
            val vByteArray = ByteArray(vSize)
            val uByteArray = ByteArray(uSize)
            vBuffer.get(vByteArray)
            uBuffer.get(uByteArray)

            var uvOffset = ySize
            for (i in 0 until vSize step uvPixelStride) {
                nv21[uvOffset++] = vByteArray[i]
                nv21[uvOffset++] = uByteArray[i]
            }
        }

        return nv21
    }

    fun isInitialized(): Boolean = isRunning
}
