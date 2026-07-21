package com.cineshot.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cineshot.app.camera.CameraController
import com.cineshot.app.databinding.ActivityMainBinding
import com.cineshot.app.dolly.DollyZoomController
import com.cineshot.app.dolly.FaceAnalyzer
import com.cineshot.app.engine.CinematicPresets
import com.cineshot.app.engine.MotionController
import com.cineshot.app.gl.VirtualViewport
import com.cineshot.app.recorder.MediaSaver
import com.cineshot.app.recorder.VideoEncoder
import com.cineshot.app.stabilizer.StabilizerController
import com.cineshot.app.ui.FilmGateScreen
import com.cineshot.app.ui.SplashScreen
import com.cineshot.app.ui.theme.CineShotTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController
    private lateinit var motionController: MotionController
    private lateinit var dollyController: DollyZoomController
    private lateinit var stabilizerController: StabilizerController
    private var pendingSurfaceTexture: SurfaceTexture? = null

    // Recording
    private var videoEncoder: VideoEncoder? = null
    private var isRecording = false
    private var isPortrait = true
    private var recordingStartMs = 0L
    private var recordingTimeSec = 0
    private val recordingTimer = Handler(Looper.getMainLooper())
    private val recordingTick = object : Runnable {
        override fun run() {
            if (isRecording) {
                recordingTimeSec = ((System.currentTimeMillis() - recordingStartMs) / 1000).toInt()
                refreshCompose()
                recordingTimer.postDelayed(this, 500)
            }
        }
    }

    // Compose state snapshots (updated by controllers, read by Compose)
    private var composeScale = 1f
    private var composeStabLabel = "关"
    private var composeDollyActive = false
    private var composeDollyIntensity = 0.8f
    private var showSplash = true

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 10
        private val TIME_FMT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        cameraController = CameraController(this)

        // ── Motion controller ──────────────────────────────────────
        motionController = MotionController(
            viewportProvider = { binding.glSurfaceView.virtualViewport },
            viewportConsumer = { vp ->
                binding.glSurfaceView.virtualViewport = vp
                composeScale = vp.scale
            }
        )

        // ── Dolly Zoom ─────────────────────────────────────────────
        val faceAnalyzer = FaceAnalyzer()
        dollyController = DollyZoomController { dollyVp ->
            val cur = binding.glSurfaceView.virtualViewport
            binding.glSurfaceView.virtualViewport = cur.copy(scale = dollyVp.scale)
            composeScale = dollyVp.scale
        }

        // ── Stabiliser ─────────────────────────────────────────────
        stabilizerController = StabilizerController(sensorManager) { stabVp ->
            val cur = binding.glSurfaceView.virtualViewport
            binding.glSurfaceView.virtualViewport = cur.copy(
                offsetX = stabVp.offsetX,
                offsetY = stabVp.offsetY,
                roll = stabVp.roll
            )
        }

        cameraController.onImageAvailable = { imageProxy ->
            try {
                val faceBox = faceAnalyzer.detect(imageProxy)
                if (faceBox != null) {
                    dollyController.onFaceDetected(
                        faceBox.width().toFloat(),
                        faceBox.height().toFloat()
                    )
                }
            } finally {
                imageProxy.close()
            }
        }

        binding.glSurfaceView.onCameraTextureReady = { _, surfaceTexture ->
            pendingSurfaceTexture = surfaceTexture
            tryStartCamera()
        }

        // ── Compose UI ─────────────────────────────────────────────
        binding.composeView.setContent {
            CineShotTheme {
                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    FilmGateScreen(
                        isRecording = isRecording,
                        recordingTimeSec = recordingTimeSec,
                        viewportScale = composeScale,
                        dollyIntensity = composeDollyIntensity,
                        stabilizerLabel = composeStabLabel,
                        dollyActive = composeDollyActive,
                        onRecordToggle = { toggleRecording() },
                        onPresetSelect = { idx -> executePreset(idx) },
                        onDollyIntensity = { v ->
                            composeDollyIntensity = v
                            dollyController.intensity = v
                        },
                        onVertigoToggle = {
                            if (dollyController.isVertigoActive) {
                                dollyController.stopVertigo()
                                composeDollyActive = false
                            } else {
                                dollyController.recalibrateReference()
                                dollyController.startVertigo()
                                composeDollyActive = true
                            }
                        },
                        onStabilizerCycle = {
                            composeStabLabel = stabilizerController.cycleStrength().label
                        },
                        onGrainToggle = { /* no-op — handled inside Compose */ },
                        onLeakToggle = { /* no-op — handled inside Compose */ }
                    )
                }
            }
        }

        // ── Permissions ────────────────────────────────────────────
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onResume() {
        super.onResume()
        stabilizerController.start()
    }

    override fun onPause() {
        stabilizerController.stop()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tryStartCamera()
            } else {
                Toast.makeText(this, "需要相机权限", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        if (isRecording) stopRecording()
        recordingTimer.removeCallbacks(recordingTick)
        dollyController.reset()
        motionController.cancel()
        cameraController.stop()
        super.onDestroy()
    }

    // ── Recording ─────────────────────────────────────────────────────

    private fun toggleRecording() {
        if (isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val shader = binding.glSurfaceView.cineRenderer.shaderProgram ?: run {
            Toast.makeText(this, "GL 未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        val quad = binding.glSurfaceView.cineRenderer.quad ?: return

        val (w, h) = if (isPortrait) 1080 to 1920 else 1920 to 1080
        val outputFile = File(
            cacheDir.resolve("videos"),
            "CINESHOT_${TIME_FMT.format(Date())}.mp4"
        ).also { it.parentFile?.mkdirs() }

        videoEncoder = VideoEncoder().also { enc ->
            enc.onComplete = { file -> onRecordingComplete(file) }
        }

        binding.glSurfaceView.queueEvent {
            videoEncoder?.start(outputFile, w, h, 8_000_000, shader, quad)
            binding.glSurfaceView.videoEncoder = videoEncoder
            runOnUiThread {
                isRecording = true
                recordingStartMs = System.currentTimeMillis()
                recordingTimeSec = 0
                recordingTimer.post(recordingTick)
                refreshCompose()
            }
        }
    }

    private fun stopRecording() {
        binding.glSurfaceView.queueEvent {
            videoEncoder?.stop()
            binding.glSurfaceView.videoEncoder = null
            videoEncoder = null
        }
        isRecording = false
        recordingTimer.removeCallbacks(recordingTick)
        refreshCompose()
    }

    private fun onRecordingComplete(file: File) {
        val uri = MediaSaver.saveToGallery(this, file)
        runOnUiThread {
            if (uri != null) {
                Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show()
                val share = MediaSaver.createShareIntent(uri)
                startActivity(Intent.createChooser(share, "分享视频"))
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_LONG).show()
            }
            file.delete()
        }
    }

    // ── Presets ───────────────────────────────────────────────────────

    private fun executePreset(index: Int) {
        val move = when (index) {
            0 -> CinematicPresets.PUSH_IN
            1 -> CinematicPresets.PULL_OUT
            2 -> CinematicPresets.PARALLAX_PAN
            3 -> CinematicPresets.CRANE_UP
            4 -> CinematicPresets.RESET
            else -> return
        }
        motionController.execute(move)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    /** Re-set Compose content to reflect changed state. */
    private fun refreshCompose() {
        binding.composeView.setContent {
            CineShotTheme {
                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    FilmGateScreen(
                        isRecording = isRecording,
                        recordingTimeSec = recordingTimeSec,
                        viewportScale = composeScale,
                        dollyIntensity = composeDollyIntensity,
                        stabilizerLabel = composeStabLabel,
                        dollyActive = composeDollyActive,
                        onRecordToggle = { toggleRecording() },
                        onPresetSelect = { idx -> executePreset(idx) },
                        onDollyIntensity = { v ->
                            composeDollyIntensity = v
                            dollyController.intensity = v
                        },
                        onVertigoToggle = {
                            if (dollyController.isVertigoActive) {
                                dollyController.stopVertigo()
                                composeDollyActive = false
                            } else {
                                dollyController.recalibrateReference()
                                dollyController.startVertigo()
                                composeDollyActive = true
                            }
                            refreshCompose()
                        },
                        onStabilizerCycle = {
                            composeStabLabel = stabilizerController.cycleStrength().label
                            refreshCompose()
                        },
                        onGrainToggle = { refreshCompose() },
                        onLeakToggle = { refreshCompose() }
                    )
                }
            }
        }
    }

    private fun tryStartCamera() {
        if (!allPermissionsGranted()) return
        val st = pendingSurfaceTexture ?: return
        cameraController.start(st)
    }

    private fun allPermissionsGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}
