package io.github.aiya000.screenshotdachshund.storage

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a finished capture into the shared pictures, under
 * `Pictures/ScreenShot-Dachshund/`, through the media store. Scoped storage owns the
 * file: no storage permission is asked for, and the gallery sees it at once.
 */
object SavedImages {

    const val FOLDER = "ScreenShot-Dachshund"

    /** Copies [png] into the shared pictures. Returns the name written, or null where it could not be. */
    fun save(context: Context, png: File): String? {
        val name = "Dachshund_${timestamp()}.png"
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            // Kept out of the gallery until the bytes are actually there.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                png.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("no output stream")

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            name
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
}
