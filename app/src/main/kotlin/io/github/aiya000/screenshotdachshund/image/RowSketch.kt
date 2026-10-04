package io.github.aiya000.screenshotdachshund.image

import kotlin.math.abs

/**
 * A row boiled down to a few brightness samples, so that two rows can be called alike
 * when they show the same thing even if their pixels are not identical.
 *
 * A browser scrolls by fractions of a device pixel and resamples the page, an image loads
 * in a little later, a shadow moves: the rows of two screenshots of the same content are
 * rarely equal pixel for pixel. Comparing sketches instead of hashes is what lets the
 * overlap of two pages be found on such pages. The hash stays for what has to be exact,
 * such as telling two screenshots of the very same screen apart from a scroll.
 */
object RowSketch {

    /**
     * How many samples a sketch has: the row is cut into this many slices, each averaged.
     * Sixteen was too few: on a settings list every "icon and two lines of text" row
     * looked like every other, and the pages were joined on top of each other.
     */
    const val SAMPLES = 64

    /**
     * Up to this mean difference of brightness (of 255) two sketches are alike. Tuned on
     * a settings list and on a browser page full of images: a resampled scroll stays
     * under it, a different row of text does not.
     */
    private const val ALIKE_MEAN_DIFF = 6

    /** A sketch whose samples spread at least this much shows something, not a blank row. */
    private const val TEXTURED_SPREAD = 16

    /** The mean brightness of each of [SAMPLES] slices of the first [width] pixels. */
    fun of(pixels: IntArray, width: Int): IntArray {
        val sketch = IntArray(SAMPLES)
        for (i in 0 until SAMPLES) {
            val from = width * i / SAMPLES
            val to = (width * (i + 1) / SAMPLES).coerceAtLeast(from + 1).coerceAtMost(width)
            var sum = 0L
            for (x in from until to) sum += brightness(pixels[x])
            sketch[i] = (sum / (to - from)).toInt()
        }
        return sketch
    }

    fun alike(a: IntArray, b: IntArray): Boolean {
        var diff = 0
        for (i in a.indices) diff += abs(a[i] - b[i])
        return diff <= ALIKE_MEAN_DIFF * a.size
    }

    /** Whether the row shows something: a blank row is alike to blank rows anywhere and says nothing about where a page is. */
    fun textured(sketch: IntArray): Boolean {
        var min = 255
        var max = 0
        for (v in sketch) {
            if (v < min) min = v
            if (v > max) max = v
        }
        return max - min >= TEXTURED_SPREAD
    }

    /** The brightness of an ARGB pixel, 0..255, weighted the way the eye weighs the channels. */
    private fun brightness(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
