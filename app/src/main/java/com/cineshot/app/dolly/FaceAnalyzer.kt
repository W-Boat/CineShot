package com.cineshot.app.dolly

import android.graphics.PointF
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.nio.ByteBuffer

/**
 * Wraps ML Kit Face Detection and emits the primary-face bounding box.
 *
 * Configured for speed (single-face, no landmarks, no classification)
 * because only the bounding-box size is needed for Dolly Zoom.
 */
class FaceAnalyzer {

    private val detector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setMinFaceSize(0.15f)   // ignore tiny/background faces
            .enableTracking()        // keep same face ID across frames
            .build()
        FaceDetection.getClient(opts)
    }

    /**
     * Synchronously detect the largest face in [imageProxy].
     *
     * The caller is responsible for closing [imageProxy] after this returns.
     *
     * @return The face bounding box (image coordinates), or null.
     */
    fun detect(imageProxy: ImageProxy): Rect? {
        val mediaImage = imageProxy.image ?: return null

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        val faces: List<Face> =
            com.google.android.gms.tasks.Tasks.await(detector.process(inputImage))

        if (faces.isEmpty()) return null

        // Pick the largest face (closest to camera → subject)
        return faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
            ?.boundingBox
    }
}
