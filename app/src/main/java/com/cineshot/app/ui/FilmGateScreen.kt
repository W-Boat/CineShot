package com.cineshot.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cineshot.app.ui.theme.FilmBlack
import com.cineshot.app.ui.theme.FilmCream
import com.cineshot.app.ui.theme.FilmTan

/**
 * Main viewfinder screen — the core shooting experience.
 *
 * Layers (bottom to top):
 *   1. GLSurfaceView (rendered behind this composable)
 *   2. ViewfinderOverlay (brackets / sprockets / grain)
 *   3. Top status bar (rec time, params)
 *   4. ModeDial (bottom-centre rotary selector)
 *   5. ShutterButton (right side)
 *
 * All controls pass events up via lambdas.
 */
@Composable
fun FilmGateScreen(
    // ── State from host ────────────────────────────────────────────
    isRecording: Boolean,
    recordingTimeSec: Int,
    viewportScale: Float,
    dollyIntensity: Float,
    stabilizerLabel: String,
    dollyActive: Boolean,

    // ── Callbacks ──────────────────────────────────────────────────
    onRecordToggle: () -> Unit,
    onPresetSelect: (Int) -> Unit,
    onDollyIntensity: (Float) -> Unit,
    onVertigoToggle: () -> Unit,
    onStabilizerCycle: () -> Unit,
    onGrainToggle: () -> Unit,
    onLeakToggle: () -> Unit
) {
    var showGrain by remember { mutableStateOf(true) }
    var showLeak by remember { mutableStateOf(false) }

    val presets = remember {
        listOf(
            PresetEntry("推近", "慢推·呼吸"),
            PresetEntry("拉远", "后撤·释然"),
            PresetEntry("环绕", "甩镜·心动"),
            PresetEntry("摇臂", "升格·凝望"),
            PresetEntry("复位", "定格·永恒")
        )
    }
    var selectedPreset by remember { mutableStateOf(0) }
    var zoomValue by remember { mutableFloatStateOf(1f) }

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Layer 2: Viewfinder overlay ────────────────────────────
        ViewfinderOverlay(showGrain = showGrain, showLeak = showLeak)

        // ── Layer 3: Top info bar ──────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 52.dp, start = 20.dp, end = 20.dp)
        ) {
            // Recording indicator
            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "●",
                        style = TextStyle(
                            fontSize = 18.sp,
                            color = androidx.compose.ui.graphics.Color(0xFFFF4444)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTime(recordingTimeSec),
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            color = FilmCream,
                            letterSpacing = 2.sp
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Parameter readouts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ParamDisplay("ZOOM", zoomValue, suffix = "×")
                ParamDisplay("STAB", when {
                    stabilizerLabel == "关" -> 0f
                    stabilizerLabel == "呼吸感" -> 0.55f
                    else -> 0.92f
                }, format = "%.0f", suffix = "%")
            }
        }

        // ── Layer 4: Mode dial (bottom centre) ─────────────────────
        ModeDial(
            presets = presets,
            selectedIndex = selectedPreset,
            onSelect = { idx ->
                selectedPreset = idx
                onPresetSelect(idx)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // ── Layer 5: Shutter button (lower right) ──────────────────
        ShutterButton(
            isRecording = isRecording,
            onClick = onRecordToggle,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = 100.dp)
        )

        // ── Zoom ring gesture zone (left edge) ─────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(48.dp)
                .height(220.dp)
                .padding(start = 8.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        zoomValue = (zoomValue - dragAmount * 0.003f)
                            .coerceIn(0.5f, 2.0f)
                    }
                }
        )

        // ── Zoom ring visual (left edge ticks) ─────────────────────
        ZoomRing(
            value = zoomValue,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 2.dp)
        )

        // ── Mini control toggles (top right) ───────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 56.dp, end = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            MiniToggle("颗粒", showGrain) {
                showGrain = it; onGrainToggle()
            }
            MiniToggle("漏光", showLeak) {
                showLeak = it; onLeakToggle()
            }
        }
    }
}

@Composable
private fun MiniToggle(label: String, active: Boolean, onToggle: (Boolean) -> Unit) {
    val newState = !active
    Text(
        text = "$label ${if (active) "ON" else "OFF"}",
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = if (active) FilmCream else FilmTan.copy(alpha = 0.4f),
            letterSpacing = 1.sp
        ),
        modifier = Modifier.padding(vertical = 2.dp)
    )
    // Simple touch toggle — in production use a switch or gesture
}

private fun formatTime(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
