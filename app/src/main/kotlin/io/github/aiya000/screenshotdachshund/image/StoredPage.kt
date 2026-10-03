package io.github.aiya000.screenshotdachshund.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * A captured page that lives on disk as a PNG, with its row hashes kept in memory.
 *
 * The hashes are all the capture and the joining logic ever look at, so the pixels
 * themselves are decoded only when the joined image is finally written, one page at a
 * time. A capture of many pages therefore costs the memory of its hashes, not of its
 * bitmaps.
 */
class StoredPage(
    val file: File,
    override val width: Int,
    override val height: Int,
    private val hashes: LongArray,
) : PixelRows {

    init {
        require(hashes.size == height) { "${hashes.size} hashes for $height rows" }
    }

    override fun rowHash(y: Int): Long = hashes[y]

    /** Not for reading pixels; [decode] gives a page that can. */
    override fun copyRow(y: Int, out: IntArray) {
        throw UnsupportedOperationException("decode() the page to read its pixels")
    }

    /** The page's pixels, freshly decoded. The result is let go of when the caller is done. */
    fun decode(): BitmapPixelRows {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = BitmapFactory.decodeFile(file.path, options)
            ?: throw IllegalStateException("cannot decode ${file.name}")
        return BitmapPixelRows(bitmap)
    }

    companion object {
        /** Writes [bitmap] to [file] as a PNG and remembers its hashes. */
        fun store(bitmap: Bitmap, file: File): StoredPage {
            file.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "cannot write ${file.name}" }
            }
            val rows = BitmapPixelRows(bitmap)
            return StoredPage(file, bitmap.width, bitmap.height, rows.allRowHashes())
        }

        /** Reads a page back from disk, hashing it once. */
        fun load(file: File): StoredPage {
            val rows = decodeFile(file)
            return StoredPage(file, rows.width, rows.height, rows.allRowHashes())
        }

        private fun decodeFile(file: File): BitmapPixelRows {
            val bitmap = BitmapFactory.decodeFile(file.path)
                ?: throw IllegalStateException("cannot decode ${file.name}")
            return BitmapPixelRows(bitmap)
        }
    }
}
