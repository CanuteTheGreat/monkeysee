package com.monkeysee.app.codec

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import java.nio.ByteBuffer

class AudioEncoder(
    private val sampleRate: Int = 48000,
    private val channelCount: Int = 2,
    private val bitrate: Int = 128_000
) {
    private var encoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private var captureThread: Thread? = null

    companion object {
        private const val TAG = "AudioEncoder"
        private const val MIME_TYPE = "audio/mp4a-latm" // AAC
        private const val PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC
    }

    interface EncodedAudioCallback {
        fun onEncodedAudio(data: ByteArray, timestamp: Long)
        fun onAudioConfig(config: ByteArray) // AAC config (AudioSpecificConfig)
    }

    private var callback: EncodedAudioCallback? = null

    fun setCallback(callback: EncodedAudioCallback) {
        this.callback = callback
    }

    fun start() {
        if (isRunning) {
            Log.w(TAG, "Audio encoder already running")
            return
        }

        try {
            // Configure audio format
            val format = MediaFormat.createAudioFormat(MIME_TYPE, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, PROFILE)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            // Create and configure encoder
            encoder = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            // Calculate buffer size for AudioRecord
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                if (channelCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val bufferSize = minBufferSize * 2

            // Create AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                if (channelCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord initialization failed")
            }

            audioRecord?.startRecording()
            isRunning = true

            Log.i(TAG, "Audio encoder started: ${sampleRate}Hz, ${channelCount}ch, ${bitrate}bps")

            // Start capture thread
            captureThread = Thread {
                captureAndEncode()
            }.apply {
                name = "AudioCapture"
                start()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio encoder", e)
            stop()
            throw e
        }
    }

    fun stop() {
        if (!isRunning) return

        isRunning = false

        try {
            captureThread?.interrupt()
            captureThread?.join(1000)
            captureThread = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            encoder?.stop()
            encoder?.release()
            encoder = null

            Log.i(TAG, "Audio encoder stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio encoder", e)
        }
    }

    private fun captureAndEncode() {
        val codec = encoder ?: return
        val record = audioRecord ?: return

        val bufferSize = 4096
        val audioData = ByteArray(bufferSize)

        var totalFrames = 0L
        val frameDurationUs = 1_000_000L * bufferSize / (sampleRate * channelCount * 2) // 16-bit = 2 bytes

        while (isRunning) {
            try {
                // Read audio data
                val readBytes = record.read(audioData, 0, audioData.size)

                if (readBytes > 0) {
                    // Get input buffer
                    val inputBufferId = codec.dequeueInputBuffer(10_000)
                    if (inputBufferId >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferId)
                        inputBuffer?.let { buffer ->
                            buffer.clear()
                            buffer.put(audioData, 0, readBytes)

                            val presentationTimeUs = totalFrames * frameDurationUs
                            codec.queueInputBuffer(
                                inputBufferId,
                                0,
                                readBytes,
                                presentationTimeUs,
                                0
                            )
                            totalFrames++
                        }
                    }

                    // Get encoded output
                    val bufferInfo = MediaCodec.BufferInfo()
                    var outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 0)

                    while (outputBufferId >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)
                        outputBuffer?.let { buffer ->
                            if (bufferInfo.size > 0) {
                                val encodedData = ByteArray(bufferInfo.size)
                                buffer.position(bufferInfo.offset)
                                buffer.get(encodedData)

                                callback?.onEncodedAudio(encodedData, bufferInfo.presentationTimeUs)
                            }
                        }

                        codec.releaseOutputBuffer(outputBufferId, false)
                        outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 0)
                    }

                    // Handle format change (for AAC config)
                    if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val outputFormat = codec.outputFormat
                        Log.i(TAG, "Output format changed: $outputFormat")

                        // Extract AAC config (csd-0)
                        if (outputFormat.containsKey("csd-0")) {
                            val csd = outputFormat.getByteBuffer("csd-0")
                            csd?.let {
                                val configData = ByteArray(it.remaining())
                                it.get(configData)
                                callback?.onAudioConfig(configData)
                                Log.d(TAG, "AAC config: ${configData.size} bytes")
                            }
                        }
                    }
                }

            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error in capture loop", e)
            }
        }
    }

    fun isInitialized(): Boolean = isRunning

    fun getSampleRate(): Int = sampleRate
    fun getChannelCount(): Int = channelCount
    fun getBitrate(): Int = bitrate
}
