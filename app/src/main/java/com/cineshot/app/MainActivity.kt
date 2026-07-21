package com.cineshot.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
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
import com.cineshot.app.recorder.MediaSaver
import com.cineshot.app.recorder.VideoEncoder
import com.cineshot.app.stabilizer.StabilizerController
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
    private var isPortrait = true  // default: portrait

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

        // ── Motion controller (preset moves) ──────────────────────────
        motionController = MotionController(
            viewportProvider = { binding.glSurfaceView.virtualViewport },
            viewportConsumer = { vp -> binding.glSurfaceView.virtualViewport = vp }
        )

        // ── Dolly Zoom controller (scale only) ────────────────────────
        val faceAnalyzer = FaceAnalyzer()
        dollyController = DollyZoomController { dollyVp ->
            val cur = binding.glSurfaceView.virtualViewport
            binding.glSurfaceView.virtualViewport = cur.copy(scale = dollyVp.scale)
        }

        // ── Stabiliser (offset + roll only) ───────────────────────────
        stabilizerController = StabilizerController(sensorManager) { stabVp ->
            val cur = binding.glSurfaceView.virtualViewport
            binding.glSurfaceView.virtualViewport = cur.copy(
                offsetX = stabVp.offsetX,
                offsetY = stabVp.offsetY,
                roll = stabVp.roll
            )
        }

        // Pipe CameraX frames → ML Kit → DollyZoomController
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

        // Wire: GL ready → start CameraX
        binding.glSurfaceView.onCameraTextureReady = { _, surfaceTexture ->
            pendingSurfaceTexture = surfaceTexture
            tryStartCamera()
        }

        // ── Preset buttons ────────────────────────────────────────────
        binding.btnPushIn.setOnClickListener {
            motionController.execute(CinematicPresets.PUSH_IN)
        }
        binding.btnPullOut.setOnClickListener {
            motionController.execute(CinematicPresets.PULL_OUT)
        }
        binding.btnParallaxPan.setOnClickListener {
            motionController.execute(CinematicPresets.PARALLAX_PAN)
        }
        binding.btnCraneUp.setOnClickListener {
            motionController.execute(CinematicPresets.CRANE_UP)
        }
        binding.btnReset.setOnClickListener {
            motionController.execute(CinematicPresets.RESET)
        }

        // ── Dolly Zoom UI ─────────────────────────────────────────────
        binding.dollyIntensitySeekbar.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    s: android.widget.SeekBar?, progress: Int, fromUser: Boolean
                ) { dollyController.intensity = progress / 100f }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            }
        )
        binding.dollyIntensitySeekbar.progress = 80

        binding.btnVertigo.setOnClickListener {
            if (dollyController.isVertigoActive) {
                dollyController.stopVertigo()
                binding.btnVertigo.text = "眩晕"
            } else {
                dollyController.recalibrateReference()
                dollyController.startVertigo()
                binding.btnVertigo.text = "停止"
            }
        }

        // ── Stabiliser UI ─────────────────────────────────────────────
        binding.btnStabilizer.text = stabilizerController.strength.label
        binding.btnStabilizer.setOnClickListener {
            val newStrength = stabilizerController.cycleStrength()
            binding.btnStabilizer.text = newStrength.label
        }

        // ── Record button ─────────────────────────────────────────────
        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else startRecording()
        }

        // ── Orientation toggle ────────────────────────────────────────
        binding.btnOrientation.setOnClickListener {
            isPortrait = !isPortrait
            binding.btnOrientation.text = if (isPortrait) "竖屏" else "横屏"
        }

        // ── Permissions ───────────────────────────────────────────────
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
        dollyController.reset()
        motionController.cancel()
        cameraController.stop()
        super.onDestroy()
    }

    // ── Recording ─────────────────────────────────────────────────────

    private fun startRecording() {
        val shader = binding.glSurfaceView.cineRenderer.shaderProgram ?: run {
            Toast.makeText(this, "GL 未就绪", Toast.LENGTH_SHORT).show()
            return
        }
        val quad = binding.glSurfaceView.cineRenderer.quad ?: return

        // 1080p; swap dimensions if landscape
        val (w, h) = if (isPortrait) 1080 to 1920 else 1920 to 1080

        val outputFile = File(
            cacheDir.resolve("videos"),
            "CINESHOT_${TIME_FMT.format(Date())}.mp4"
        ).also { it.parentFile?.mkdirs() }

        videoEncoder = VideoEncoder().also { enc ->
            enc.onComplete = { file -> onRecordingComplete(file) }
        }

        // Must init the shared EGL context on the GL thread.
        binding.glSurfaceView.queueEvent {
            videoEncoder?.start(outputFile, w, h, 8_000_000, shader, quad)
            binding.glSurfaceView.videoEncoder = videoEncoder
            runOnUiThread {
                isRecording = true
                binding.btnRecord.text = "■ 停止"
                binding.recordingIndicator.visibility = android.view.View.VISIBLE
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
        binding.btnRecord.text = "● 录制"
        binding.recordingIndicator.visibility = android.view.View.GONE
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
            // Clean up temp
            file.delete()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun tryStartCamera() {
        if (!allPermissionsGranted()) return
        val st = pendingSurfaceTexture ?: return
        cameraController.start(st)
    }

    private fun allPermissionsGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}
