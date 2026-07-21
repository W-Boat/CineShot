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
import com.cineshot.app.engine.CinematicPresets
import com.cineshot.app.engine.MotionController
import com.cineshot.app.gl.VirtualViewport

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController
    private lateinit var motionController: MotionController
    private var pendingSurfaceTexture: SurfaceTexture? = null

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraController = CameraController(this)

        // Motion controller bridges the GLSurfaceView viewport
        motionController = MotionController(
            viewportProvider = { binding.glSurfaceView.virtualViewport },
            viewportConsumer = { vp -> binding.glSurfaceView.virtualViewport = vp }
        )

        // Wire: once GL is ready → start CameraX
        binding.glSurfaceView.onCameraTextureReady = { _, surfaceTexture ->
            pendingSurfaceTexture = surfaceTexture
            tryStartCamera()
        }

        // ── Preset buttons ──────────────────────────────────────────
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

        // ── Permissions ─────────────────────────────────────────────
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
