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
import com.cineshot.app.gl.VirtualViewport

/**
 * Entry point — hosts [com.cineshot.app.gl.CineGLSurfaceView] and
 * orchestrates the CameraX → OpenGL pipeline.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController
    private var pendingSurfaceTexture: SurfaceTexture? = null

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraController = CameraController(this)

        // GLSurfaceView notifies us once the OES texture + SurfaceTexture are ready
        binding.glSurfaceView.onCameraTextureReady = { _, surfaceTexture ->
            pendingSurfaceTexture = surfaceTexture
            tryStartCamera()
        }

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
        // else: wait for GLSurfaceView → onCameraTextureReady → tryStartCamera
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

    // ---- Public API for cinematic controls (future use) ----

    fun setVirtualViewport(offsetX: Float, offsetY: Float, scale: Float, roll: Float) {
        binding.glSurfaceView.virtualViewport = VirtualViewport(
            offsetX = offsetX,
            offsetY = offsetY,
            scale = scale,
            roll = roll
        )
    }
}
