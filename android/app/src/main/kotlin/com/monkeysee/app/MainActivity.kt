package com.monkeysee.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import androidx.camera.view.PreviewView
import com.monkeysee.app.rtsp.RtspServer
import java.net.NetworkInterface
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var startStopButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var urlText: TextView

    private lateinit var cameraExecutor: ExecutorService
    private var rtspServer: RtspServer? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var isStreaming = false

    companion object {
        private const val TAG = "MonkeySee"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val RTSP_PORT = 8554
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        startStopButton = findViewById(R.id.startStopButton)
        statusText = findViewById(R.id.statusText)
        urlText = findViewById(R.id.urlText)

        cameraExecutor = Executors.newSingleThreadExecutor()

        startStopButton.setOnClickListener {
            if (isStreaming) {
                stopStreaming()
            } else {
                startStreaming()
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        updateUrlText()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.camera_permission_required),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun startStreaming() {
        try {
            rtspServer = RtspServer(RTSP_PORT)
            rtspServer?.start()

            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                rtspServer?.sendFrame(imageProxy)
                imageProxy.close()
            }

            isStreaming = true
            startStopButton.text = getString(R.string.stop_streaming)
            statusText.text = getString(R.string.streaming)

            Log.i(TAG, "Streaming started on port $RTSP_PORT")
            Toast.makeText(this, "Streaming started", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming", e)
            Toast.makeText(this, "Failed to start streaming: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopStreaming() {
        imageAnalysis?.clearAnalyzer()
        rtspServer?.stop()
        rtspServer = null

        isStreaming = false
        startStopButton.text = getString(R.string.start_streaming)
        statusText.text = getString(R.string.stopped)

        Log.i(TAG, "Streaming stopped")
        Toast.makeText(this, "Streaming stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUrlText() {
        val ipAddress = getIPAddress()
        val url = "rtsp://$ipAddress:$RTSP_PORT/camera"
        urlText.text = getString(R.string.rtsp_url) + " $url"
    }

    private fun getIPAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.address.size == 4) {
                        return address.hostAddress ?: "unknown"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get IP address", e)
        }
        return "unknown"
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        cameraExecutor.shutdown()
    }
}
