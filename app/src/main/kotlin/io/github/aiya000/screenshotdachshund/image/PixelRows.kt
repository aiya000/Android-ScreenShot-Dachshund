package io.github.aiya000.screenshotdachshund.image

/**
 * An image read one row at a time.
 *
 * Every image the app works with -- a screenshot, a page kept on disk, the joined
 * result -- is seen through this, so that the joining logic is plain Kotlin and can be
 * tested on the JVM, and so that a result taller than any single bitmap could be can
 * still be written out row by row.
 *
 * Pixels are ARGB packed into an Int, the way `android.graphics.Bitmap.getPixels` hands
 * them out.
 */
interface PixelRows {
    val width: Int
    val height: Int

    /** Copies row [y] into `out[0 until width]`. */
    fun copyRow(y: Int, out: IntArray)

    /** A 64-bit hash of row [y]. Two rows with the same pixels hash alike. */
    fun rowHash(y: Int): Long

    /** The [RowSketch] of row [y]: a few brightness samples, for telling rows alike that are not identical. */
    fun rowSketch(y: Int): IntArray
}

/** The FNV-1a hash of the first [width] pixels, which is cheap and good enough to tell rows apart. */
fun hashRow(pixels: IntArray, width: Int): Long {
    var hash = -0x340d631b7bdddcdbL // FNV offset basis
    for (x in 0 until width) {
        hash = (hash xor pixels[x].toLong()) * 0x100000001b3L
    }
    return hash
}

/** True when both images have the same size and every row hashes alike. */
fun PixelRows.contentEquals(other: PixelRows): Boolean = differingRows(other) == 0

/**
 * How many rows differ between the two images, or [Int.MAX_VALUE] when they are not even
 * the same size.
 */
fun PixelRows.differingRows(other: PixelRows): Int {
    if (width != other.width || height != other.height) return Int.MAX_VALUE
    var n = 0
    for (y in 0 until height) {
        if (rowHash(y) != other.rowHash(y)) n++
    }
    return n
}

/** An image held in memory as one array, row-major. */
class ArrayPixelRows(
    override val width: Int,
    override val height: Int,
    private val pixels: IntArray,
) : PixelRows {

    init {
        require(pixels.size >= width * height) { "${pixels.size} pixels for ${width}x$height" }
    }

    private val hashes = LongArray(height)
    private val hashed = BooleanArray(height)
    private val sketches = arrayOfNulls<IntArray>(height)

    override fun copyRow(y: Int, out: IntArray) {
        System.arraycopy(pixels, y * width, out, 0, width)
    }

    override fun rowHash(y: Int): Long {
        if (!hashed[y]) {
            hashes[y] = hashRow(pixels.copyOfRange(y * width, (y + 1) * width), width)
            hashed[y] = true
        }
        return hashes[y]
    }

    override fun rowSketch(y: Int): IntArray =
        sketches[y] ?: RowSketch.of(pixels.copyOfRange(y * width, (y + 1) * width), width).also { sketches[y] = it }
}
