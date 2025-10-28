package com.monkeysee.app.rtsp

import android.util.Log
import androidx.camera.core.ImageProxy
import com.monkeysee.app.StreamingConfig
import com.monkeysee.app.codec.VideoEncoder
import kotlinx.coroutines.*
import java.io.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer

class RtspServer(
    private val port: Int,
    private var config: StreamingConfig = StreamingConfig.DEFAULT
) {

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var udpSocket: DatagramSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sessionId: String? = null
    private var cseq = 0

    private var videoEncoder: VideoEncoder? = null
    private var rtpPacketizer: RtpPacketizer? = null
    private var clientAddress: InetAddress? = null
    private var clientRtpPort: Int = 0

    private var spsData: ByteArray? = null
    private var ppsData: ByteArray? = null

    // Quality change callback
    var onQualityChangeRequested: ((QualityPreset) -> Unit)? = null

    companion object {
        private const val TAG = "RtspServer"
    }

    fun start() {
        if (isRunning) {
            Log.w(TAG, "Server already running")
            return
        }

        // Initialize video encoder with configured quality
        videoEncoder = VideoEncoder(
            width = config.videoWidth,
            height = config.videoHeight,
            bitrate = config.videoBitrate,
            fps = config.videoFps
        ).apply {
            setCallback(object : VideoEncoder.EncodedFrameCallback {
                override fun onEncodedFrame(data: ByteArray, timestamp: Long, isKeyFrame: Boolean) {
                    sendRtpPackets(data, timestamp)
                }

                override fun onSpsNalu(sps: ByteArray) {
                    spsData = sps
                    Log.d(TAG, "Received SPS: ${sps.size} bytes")
                }

                override fun onPpsNalu(pps: ByteArray) {
                    ppsData = pps
                    Log.d(TAG, "Received PPS: ${pps.size} bytes")
                }
            })
            start()
        }

        Log.i(TAG, "Streaming configuration: ${config.getDescription()}")

        rtpPacketizer = RtpPacketizer()

        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                udpSocket = DatagramSocket()
                Log.i(TAG, "RTSP server started on port $port")

                while (isRunning) {
                    try {
                        val client = serverSocket?.accept()
                        client?.let {
                            Log.i(TAG, "Client connected from ${it.inetAddress}")
                            clientAddress = it.inetAddress
                            clientSocket = it
                            handleClient(it)
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            videoEncoder?.stop()
            videoEncoder = null
            clientSocket?.close()
            serverSocket?.close()
            udpSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        scope.cancel()
        Log.i(TAG, "RTSP server stopped")
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        try {
            val input = BufferedReader(InputStreamReader(client.getInputStream()))
            val output = BufferedWriter(OutputStreamWriter(client.getOutputStream()))

            while (isRunning && !client.isClosed) {
                val request = readRequest(input) ?: break
                Log.d(TAG, "Received request: $request")

                when {
                    request.startsWith("OPTIONS") -> handleOptions(output, request)
                    request.startsWith("DESCRIBE") -> handleDescribe(output, request)
                    request.startsWith("SETUP") -> handleSetup(output, request)
                    request.startsWith("PLAY") -> handlePlay(output, request)
                    request.startsWith("SET_PARAMETER") -> handleSetParameter(output, request)
                    request.startsWith("TEARDOWN") -> handleTeardown(output, request)
                    else -> sendResponse(output, "400 Bad Request", cseq)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client", e)
        } finally {
            client.close()
        }
    }

    private fun readRequest(input: BufferedReader): String? {
        val builder = StringBuilder()
        var line: String?

        try {
            line = input.readLine()
            if (line == null) return null

            builder.append(line).append("\r\n")

            // Read headers
            while (true) {
                line = input.readLine()
                if (line.isNullOrEmpty()) break
                builder.append(line).append("\r\n")

                // Extract CSeq
                if (line.startsWith("CSeq:")) {
                    cseq = line.substring(5).trim().toIntOrNull() ?: 0
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading request", e)
            return null
        }

        return builder.toString()
    }

    private fun handleOptions(output: BufferedWriter, request: String) {
        val response = buildString {
            append("RTSP/1.0 200 OK\r\n")
            append("CSeq: $cseq\r\n")
            append("Public: OPTIONS, DESCRIBE, SETUP, PLAY, SET_PARAMETER, TEARDOWN\r\n")
            append("\r\n")
        }
        output.write(response)
        output.flush()
    }

    private fun handleDescribe(output: BufferedWriter, request: String) {
        val sdp = buildString {
            append("v=0\r\n")
            append("o=- 0 0 IN IP4 127.0.0.1\r\n")
            append("s=MonkeySee Camera\r\n")
            append("c=IN IP4 0.0.0.0\r\n")
            append("t=0 0\r\n")

            // Video track (H.264)
            append("m=video 0 RTP/AVP 96\r\n")
            append("a=rtpmap:96 H264/90000\r\n")
            append("a=fmtp:96 packetization-mode=1\r\n")
            append("b=AS:${config.videoBitrate / 1000}\r\n") // Bandwidth in kbps
            append("a=control:track0\r\n")

            // Audio track (AAC)
            append("m=audio 0 RTP/AVP 97\r\n")
            append("a=rtpmap:97 MPEG4-GENERIC/${config.audioSampleRate}/${config.audioChannels}\r\n")
            append("a=fmtp:97 streamtype=5;profile-level-id=1;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3\r\n")
            append("b=AS:${config.audioBitrate / 1000}\r\n") // Bandwidth in kbps
            append("a=control:track1\r\n")
        }

        val response = buildString {
            append("RTSP/1.0 200 OK\r\n")
            append("CSeq: $cseq\r\n")
            append("Content-Type: application/sdp\r\n")
            append("Content-Length: ${sdp.length}\r\n")
            append("\r\n")
            append(sdp)
        }

        output.write(response)
        output.flush()
    }

    private fun handleSetup(output: BufferedWriter, request: String) {
        sessionId = System.currentTimeMillis().toString()

        // Parse client_port from Transport header
        val transportLine = request.lines().find { it.startsWith("Transport:") }
        transportLine?.let { line ->
            val portMatch = Regex("client_port=(\\d+)-(\\d+)").find(line)
            portMatch?.let {
                clientRtpPort = it.groupValues[1].toInt()
                Log.d(TAG, "Client RTP port: $clientRtpPort")
            }
        }

        // If no client port specified, use default
        if (clientRtpPort == 0) {
            clientRtpPort = 5000
        }

        val response = buildString {
            append("RTSP/1.0 200 OK\r\n")
            append("CSeq: $cseq\r\n")
            append("Session: $sessionId\r\n")
            append("Transport: RTP/AVP;unicast;client_port=$clientRtpPort-${clientRtpPort + 1}\r\n")
            append("\r\n")
        }

        output.write(response)
        output.flush()
    }

    private fun handlePlay(output: BufferedWriter, request: String) {
        val response = buildString {
            append("RTSP/1.0 200 OK\r\n")
            append("CSeq: $cseq\r\n")
            append("Session: $sessionId\r\n")
            append("Range: npt=0.000-\r\n")
            append("\r\n")
        }

        output.write(response)
        output.flush()
    }

    private fun handleSetParameter(output: BufferedWriter, request: String) {
        // Parse the request body for quality parameter
        // Expected format: "quality: low|medium|high|ultra"
        val lines = request.lines()
        var qualityValue: String? = null

        for (line in lines) {
            if (line.startsWith("quality:", ignoreCase = true)) {
                qualityValue = line.substring(8).trim()
                break
            }
        }

        if (qualityValue != null) {
            val preset = when (qualityValue.lowercase()) {
                "low" -> QualityPreset.LOW
                "medium" -> QualityPreset.MEDIUM
                "high" -> QualityPreset.HIGH
                "ultra" -> QualityPreset.ULTRA
                else -> null
            }

            if (preset != null) {
                Log.i(TAG, "Quality change requested: $qualityValue")
                updateQuality(preset)
                sendResponse(output, "200 OK", cseq)
                return
            }
        }

        // If we get here, the parameter was invalid or missing
        sendResponse(output, "451 Parameter Not Understood", cseq)
    }

    private fun handleTeardown(output: BufferedWriter, request: String) {
        sendResponse(output, "200 OK", cseq)
        clientSocket?.close()
    }

    private fun sendResponse(output: BufferedWriter, status: String, cseq: Int) {
        val response = buildString {
            append("RTSP/1.0 $status\r\n")
            append("CSeq: $cseq\r\n")
            append("\r\n")
        }
        output.write(response)
        output.flush()
    }

    /**
     * Update the streaming quality dynamically
     */
    fun updateQuality(preset: QualityPreset) {
        Log.i(TAG, "Updating quality to: ${preset.name}")

        // Update config
        val newConfig = StreamingConfig.fromPreset(preset)
        config = newConfig

        // Restart video encoder with new settings
        scope.launch(Dispatchers.IO) {
            try {
                // Stop old encoder
                videoEncoder?.stop()

                // Create new encoder with updated config
                videoEncoder = VideoEncoder(
                    width = config.videoWidth,
                    height = config.videoHeight,
                    bitrate = config.videoBitrate,
                    fps = config.videoFps
                ).apply {
                    setCallback(object : VideoEncoder.EncodedFrameCallback {
                        override fun onEncodedFrame(data: ByteArray, timestamp: Long, isKeyFrame: Boolean) {
                            sendRtpPackets(data, timestamp)
                        }

                        override fun onSpsNalu(sps: ByteArray) {
                            spsData = sps
                            Log.d(TAG, "Received SPS: ${sps.size} bytes")
                        }

                        override fun onPpsNalu(pps: ByteArray) {
                            ppsData = pps
                            Log.d(TAG, "Received PPS: ${pps.size} bytes")
                        }
                    })
                    start()
                }

                Log.i(TAG, "Video encoder restarted with: ${config.getDescription()}")

                // Notify callback if set
                onQualityChangeRequested?.invoke(preset)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating quality", e)
            }
        }
    }

    fun sendFrame(imageProxy: ImageProxy) {
        videoEncoder?.encodeFrame(imageProxy)
    }

    private fun sendRtpPackets(nalData: ByteArray, timestampUs: Long) {
        val packetizer = rtpPacketizer ?: return
        val socket = udpSocket ?: return
        val address = clientAddress ?: return

        if (clientRtpPort == 0) return

        try {
            val packets = packetizer.packetize(nalData, timestampUs)

            for (packet in packets) {
                val datagramPacket = DatagramPacket(
                    packet,
                    packet.size,
                    address,
                    clientRtpPort
                )
                socket.send(datagramPacket)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending RTP packets", e)
        }
    }
}
