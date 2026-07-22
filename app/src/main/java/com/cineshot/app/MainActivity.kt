package com.cineshot.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.atech.glcamera.views.GLCameraView
import com.cineshot.app.databinding.ActivityMainBinding
import com.cineshot.app.ui.FilmGateScreen
import com.cineshot.app.ui.SplashScreen
import com.cineshot.app.ui.theme.CineShotTheme
import java.io.File

/**
 * CineShot — rebuilt on the opencamera native engine.
 *
 * Bottom layer:   [GLCameraView] — Camera2 + C++ OpenGL ES
 * Top layer:      Compose UI — viewfinder overlay, mode dial, shutter
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraView: GLCameraView

    private var showSplash by mutableStateOf(true)
    private var isRecording by mutableStateOf(false)

    companion object {
        private const val REQUEST_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraView = binding.glCameraView

        // ── Compose UI overlay ────────────────────────────────────
        binding.composeView.setContent {
            CineShotTheme {
                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    FilmGateScreen(
                        isRecording = isRecording,
                        recordingTimeSec = 0,
                        viewportScale = 1f,
                        dollyIntensity = 0f,
                        stabilizerLabel = "关",
                        dollyActive = false,
                        onRecordToggle = { toggleRecording() },
                        onPresetSelect = { /* TODO: rewire cinematic engine */ },
                        onDollyIntensity = { /* TODO: rewire Dolly Zoom */ },
                        onVertigoToggle = { /* TODO: rewire vertigo */ },
                        onStabilizerCycle = { /* TODO: rewire stabilizer */ },
                        onGrainToggle = { /* handled inside Compose */ },
                        onLeakToggle = { /* handled inside Compose */ }
                    )
                }
            }
        }

        // ── Permissions ───────────────────────────────────────────
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_PERMISSIONS)
        } else {
            cameraView.onResume()
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            cameraView.onResume()
        }
    }

    override fun onPause() {
        cameraView.onPause()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (allPermissionsGranted()) {
                cameraView.onResume()
            } else {
                Toast.makeText(this, "需要相机和存储权限", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // ── Recording ─────────────────────────────────────────────────────

    private fun toggleRecording() {
        if (isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: cacheDir
        dir.mkdirs()
        val outputFile = File(dir, "CineShot_${System.currentTimeMillis()}.mp4")
        cameraView.setOuputMP4File(outputFile)
        cameraView.setrecordFinishedListnener { file ->
            runOnUiThread {
                Toast.makeText(this, "已保存: ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
        cameraView.changeRecordingState(true)
        isRecording = true
        Toast.makeText(this, "开始录制", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        isRecording = false
        cameraView.changeRecordingState(false)
        Toast.makeText(this, "录制完成", Toast.LENGTH_SHORT).show()
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun allPermissionsGranted(): Boolean =
        REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
}
