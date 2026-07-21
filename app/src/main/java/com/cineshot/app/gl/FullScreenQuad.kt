package com.cineshot.app.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * A full-screen quad (two triangles) drawn with a given shader program.
 * The quad covers clip-space coordinates from (-1,-1) to (1,1).
 */
class FullScreenQuad {

    private val vertexBuffer: FloatBuffer

    // 4 vertices × (2 pos + 2 tex) = 16 floats
    private val vertexCount = 4
    private val stride = 4 * 4 // 4 floats × 4 bytes

    init {
        // pos (x,y) + tex (u,v) interleaved
        val vertices = floatArrayOf(
            // x,   y,     u,  v
            -1f,  1f,   0f, 1f,  // top-left
            -1f, -1f,   0f, 0f,  // bottom-left
             1f,  1f,   1f, 1f,  // top-right
             1f, -1f,   1f, 0f   // bottom-right
        )
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.flip()
    }

    fun draw(shader: ShaderProgram) {
        val posHandle = shader.getAttribLocation("aPosition")
        val texHandle = shader.getAttribLocation("aTextureCoord")

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)
        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }
}
