package io.github.aiya000.screenshotdachshund.image

import android.graphics.Bitmap

/** A software bitmap seen as rows. The bitmap is read, never changed. */
class BitmapPixelRows(private val bitmap: Bitmap) : PixelRows {

    override val width: Int = bitmap.width
    override val height: Int = bitmap.height

    private val hashes = LongArray(height)
    private val hashed = BooleanArray(height)
    private val sketches = arrayOfNulls<IntArray>(height)
    private val scratch = IntArray(width)

    override fun copyRow(y: Int, out: IntArray) {
        bitmap.getPixels(out, 0, width, 0, y, width, 1)
    }

    override fun rowHash(y: Int): Long {
        if (!hashed[y]) read(y)
        return hashes[y]
    }

    override fun rowSketch(y: Int): IntArray {
        if (sketches[y] == null) read(y)
        return sketches[y]!!
    }

    /** One pass over the row for both its hash and its sketch. */
    private fun read(y: Int) {
        synchronized(scratch) {
            copyRow(y, scratch)
            hashes[y] = hashRow(scratch, width)
            hashed[y] = true
            sketches[y] = RowSketch.of(scratch, width)
        }
    }

    /** Every row hash at once, for a page that is about to be let go of. */
    fun allRowHashes(): LongArray = LongArray(height) { rowHash(it) }

    /** Every row sketch at once, for a page that is about to be let go of. */
    fun allRowSketches(): Array<IntArray> = Array(height) { rowSketch(it) }
}
