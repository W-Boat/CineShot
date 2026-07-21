package com.cineshot.app.recorder

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

/**
 * Saves the recorded .mp4 to the system MediaStore (gallery)
 * and offers a share [Intent].
 */
object MediaSaver {

    private const val TAG = "MediaSaver"

    /**
     * Copy the temp video file into MediaStore so it appears in the gallery.
     *
     * @return The content URI of the saved video, or null on failure.
     */
    fun saveToGallery(context: Context, tempFile: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, tempFile)
            } else {
                saveLegacy(context, tempFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save video", e)
            null
        }
    }

    /**
     * Build a share intent for the given video URI.
     */
    fun createShareIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ── Internals ─────────────────────────────────────────────────────

    private fun saveViaMediaStore(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "CineShot_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CineShot")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return null

        context.contentResolver.openOutputStream(uri)?.use { out ->
            FileInputStream(file).use { inp -> inp.copyTo(out) }
        }

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)

        Log.d(TAG, "Saved to gallery: $uri")
        return uri
    }

    private fun saveLegacy(context: Context, file: File): Uri? {
        val dest = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "CineShot/CineShot_${System.currentTimeMillis()}.mp4"
        )
        dest.parentFile?.mkdirs()
        file.copyTo(dest, overwrite = true)

        // Notify gallery
        val uri = Uri.fromFile(dest)
        context.sendBroadcast(
            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
        )
        Log.d(TAG, "Saved legacy: $uri")
        return uri
    }
}
