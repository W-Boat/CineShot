package com.cineshot.app.recorder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import com.cineshot.app.gl.FullScreenQuad
import com.cineshot.app.gl.ShaderProgram
import com.cineshot.app.gl.VirtualViewport
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes the GL-rendered scene to H.264 .mp4 via MediaCodec + MediaMuxer.
 *
 * Lifecycle (call from GL thread):
 *   1. [start]         — create codec, muxer, shared EGL context.
 *   2. [beginFrame]    — switch to encoder EGL surface.
 *   3. [endFrame]      — swap buffers → feed encoder.
 *   4. [stop]          — signal EOS, finalise muxer.
 *
 * A dedicated drain-thread reads encoder output and writes it into
 * the muxer so the GL thread is never blocked on I/O.
 */
class VideoEncoder {

    companion object {
        private const val TAG = "VideoEncoder"
        private const val MIME = "video/avc"
        private const val FRAME_RATE = 60
        private const val I_FRAME_INTERVAL_S = 1
    }

    // ── Encoder state ─────────────────────────────────────────────────

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var trackIndex = -1
    private var muxerStarted = false
    @Volatile private var isRecording = false

    // EGL
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var parentContext: EGLContext = EGL14.EGL_NO_CONTEXT

    // Drain thread
    private var drainThread: Thread? = null
    @Volatile private var drainRunning = false

    // Output
    private var outputFile: File? = null
    private var encoderWidth = 0
    private var encoderHeight = 0

    // Reusable rendering state
    private var shader: ShaderProgram? = null
    private var fullQuad: FullScreenQuad? = null

    /** Called on completion with the output file (on drain thread). */
    var onComplete: ((File) -> Unit)? = null

    // ── Public API ────────────────────────────────────────────────────

    fun start(
        output: File,
        width: Int,
        height: Int,
        bitrate: Int = 8_000_000,
        shader: ShaderProgram,
        fullQuad: FullScreenQuad
    ) {
        if (isRecording) return
        this.shader = shader
        this.fullQuad = fullQuad
        outputFile = output
        encoderWidth = width
        encoderHeight = height

        // 1. MediaCodec
        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_S)
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }

        codec = MediaCodec.createEncoderByType(MIME)
        codec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec!!.createInputSurface()
        codec!!.start()

        // 2. MediaMuxer
        muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxerStarted = false

        // 3. Capture parent EGL context (must be called from GL thread)
        parentContext = EGL14.eglGetCurrentContext()
        eglDisplay = EGL14.eglGetCurrentDisplay()

        // 4. Create shared EGL context
        val attribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)?.let { surf ->
                // match config of current surface
                val value = IntArray(1)
                EGL14.eglQuerySurface(eglDisplay, surf, EGL14.EGL_CONFIG_ID, value, 0)
                val configs = arrayOfNulls<EGLConfig>(1)
                val num = IntArray(1)
                EGL14.eglChooseConfig(eglDisplay,
                    intArrayOf(EGL14.EGL_CONFIG_ID, value[0], EGL14.EGL_NONE), 0,
                    configs, 0, 1, num, 0)
                configs[0]
            } ?: let {
                // fallback: pick first available config
                val configs = arrayOfNulls<EGLConfig>(1)
                val num = IntArray(1)
                EGL14.eglChooseConfig(eglDisplay, intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE
                ), 0, configs, 0, 1, num, 0)
                configs[0]
            },
            parentContext,
            attribs, 0
        )

        // 5. Create EGL surface wrapping MediaCodec input surface
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)?.let {
                val configs = arrayOfNulls<EGLConfig>(1)
                val num = IntArray(1)
                val value = IntArray(1)
                EGL14.eglQuerySurface(eglDisplay, it, EGL14.EGL_CONFIG_ID, value, 0)
                EGL14.eglChooseConfig(eglDisplay,
                    intArrayOf(EGL14.EGL_CONFIG_ID, value[0], EGL14.EGL_NONE), 0,
                    configs, 0, 1, num, 0)
                configs[0]
            } ?: let {
                val configs = arrayOfNulls<EGLConfig>(1)
                val num = IntArray(1)
                EGL14.eglChooseConfig(eglDisplay, intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, 4,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE
                ), 0, configs, 0, 1, num, 0)
                configs[0]
            },
            inputSurface,
            intArrayOf(EGL14.EGL_NONE), 0
        )

        isRecording = true

        // 6. Start drain thread
        drainRunning = true
        drainThread = Thread({ drainLoop() }, "VideoEncoder-drain").apply { start() }

        Log.d(TAG, "Recording started: ${width}x${height} @ $FRAME_RATE fps → ${output.name}")
    }

    /** Switch to encoder EGL context + surface.  Call from GL thread. */
    fun beginFrame(): Boolean {
        if (!isRecording) return false
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        GLES20.glViewport(0, 0, encoderWidth, encoderHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        return true
    }

    /** Swap → deliver frame to encoder.  Call from GL thread after rendering. */
    fun endFrame() {
        if (!isRecording) return
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        // Restore parent context + surface for GLSurfaceView
        EGL14.eglMakeCurrent(
            eglDisplay,
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW),
            EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW),
            parentContext
        )
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        Log.d(TAG, "Stopping encoder...")

        // Signal EOS to codec
        codec?.signalEndOfInputStream()

        // Wait for drain thread
        drainRunning = false
        drainThread?.join(3000L)

        releaseEGL()
        codec?.stop()
        codec?.release()
        codec = null
        inputSurface?.release()
        inputSurface = null
        muxer?.stop()
        muxer?.release()
        muxer = null

        outputFile?.let { onComplete?.invoke(it) }
        Log.d(TAG, "Recording finished")
    }

    val recording: Boolean get() = isRecording

    // ── Internals ─────────────────────────────────────────────────────

    private fun drainLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (drainRunning || !isRecording) {
            val codec = this.codec ?: break
            val muxer = this.muxer ?: break

            val index = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                index >= 0 -> {
                    val buf = codec.getOutputBuffer(index) ?: continue
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 &&
                        bufferInfo.size > 0 && muxerStarted) {
                        buf.position(bufferInfo.offset)
                        buf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, buf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(index, false)
                }
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!drainRunning && !isRecording) break
                }
            }

            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                Log.d(TAG, "EOS received")
                break
            }
        }
    }

    private fun releaseEGL() {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            eglContext = EGL14.EGL_NO_CONTEXT
        }
    }
}
