package io.github.aiya000.screenshotdachshund.image

import android.graphics.Bitmap

/** A software bitmap seen as rows. The bitmap is read, never changed. */
class BitmapPixelRows(private val bitmap: Bitmap) : PixelRows {

    override val width: Int = bitmap.width
    override val height: Int = bitmap.height

    private val hashes = LongArray(height)
    private val hashed = BooleanArray(height)
    private val scratch = IntArray(width)

    override fun copyRow(y: Int, out: IntArray) {
        bitmap.getPixels(out, 0, width, 0, y, width, 1)
    }

    override fun rowHash(y: Int): Long {
        if (!hashed[y]) {
            synchronized(scratch) {
                copyRow(y, scratch)
                hashes[y] = hashRow(scratch, width)
            }
            hashed[y] = true
        }
        return hashes[y]
    }

    /** Every row hash at once, for a page that is about to be let go of. */
    fun allRowHashes(): LongArray = LongArray(height) { rowHash(it) }
}
