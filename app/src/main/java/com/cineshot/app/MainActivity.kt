package com.cineshot.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.SensorManager
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
import com.cineshot.app.stabilizer.StabilizerController

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController
    private lateinit var motionController: MotionController
    private lateinit var dollyController: DollyZoomController
    private lateinit var stabilizerController: StabilizerController
    private var pendingSurfaceTexture: SurfaceTexture? = null

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 10
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
                ) {
                    dollyController.intensity = progress / 100f
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
            }
        )
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

        // ── Stabiliser UI ─────────────────────────────────────────────
        binding.btnStabilizer.text = stabilizerController.strength.label
        binding.btnStabilizer.setOnClickListener {
            val newStrength = stabilizerController.cycleStrength()
            binding.btnStabilizer.text = newStrength.label
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
