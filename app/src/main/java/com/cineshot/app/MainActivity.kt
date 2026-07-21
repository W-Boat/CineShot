package com.cineshot.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController
    private lateinit var motionController: MotionController
    private lateinit var dollyController: DollyZoomController
    private var pendingSurfaceTexture: SurfaceTexture? = null

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraController = CameraController(this)

        // ── Motion controller (preset moves) ──────────────────────────
        motionController = MotionController(
            viewportProvider = { binding.glSurfaceView.virtualViewport },
            viewportConsumer = { vp -> binding.glSurfaceView.virtualViewport = vp }
        )

        // ── Dolly Zoom controller ─────────────────────────────────────
        val faceAnalyzer = FaceAnalyzer()
        dollyController = DollyZoomController { vp ->
            binding.glSurfaceView.virtualViewport = vp
        }

        // Pipe CameraX image frames → ML Kit → DollyZoomController
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
                ) {
                    dollyController.intensity = progress / 100f
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            }
        )
        // Default intensity 80%
        binding.dollyIntensitySeekbar.progress = 80

        binding.btnVertigo.setOnClickListener {
            if (dollyController.isVertigoActive) {
                dollyController.stopVertigo()
                binding.btnVertigo.text = "眩晕变焦"
            } else {
                dollyController.recalibrateReference()
                dollyController.startVertigo()
                binding.btnVertigo.text = "停止"
            }
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
        dollyController.reset()
        motionController.cancel()
        cameraController.stop()
        super.onDestroy()
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
