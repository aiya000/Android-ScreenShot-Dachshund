package io.github.aiya000.screenshotdachshund.image

/**
 * Synthetic pages for the tests. A row is identified by an integer id; the pixels of the
 * row are derived from that id and vary across the row, so two rows look alike exactly
 * when their ids are equal, and rows of different ids are far apart by any measure.
 */
object TestPages {

    const val WIDTH = 32

    /** The first pixel of a row of id [id]: the id itself in the colour channels, which is what [rowIds] reads back. */
    fun pixelOf(id: Int): Int = (0xFF shl 24) or (id and 0xFFFFFF)

    /**
     * A pixel of row [id] at column [x]: a strong, id-specific pattern across the row,
     * well mixed so that no two ids come out alike by accident (a plain multiply did:
     * neighbouring ids were shifted copies of each other).
     */
    fun pixelAt(id: Int, x: Int): Int {
        var h = id.toLong() * -0x61c8864680b583ebL + (x / 2 + 1).toLong() * -0x40a7b892e31b1a47L
        h = h xor (h ushr 31)
        h *= -0x6b2fb644ecceee15L
        h = h xor (h ushr 29)
        val grey = (h and 0xFF).toInt()
        return (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
    }

    /** A page whose row ids are given one by one. */
    fun fromRowIds(ids: List<Int>, width: Int = WIDTH): PixelRows {
        val pixels = IntArray(width * ids.size)
        ids.forEachIndexed { y, id ->
            pixels[y * width] = pixelOf(id)
            for (x in 1 until width) pixels[y * width + x] = pixelAt(id, x)
        }
        return ArrayPixelRows(width, ids.size, pixels)
    }

    /**
     * A screenshot of a scrolling page: [top] fixed rows (the status bar and a toolbar),
     * [bottom] fixed rows (the navigation bar), and in between the content rows starting
     * at content row [scroll]. Content row n has id n, so a page scrolled by d shares
     * (contentHeight - d) rows with the page before it.
     */
    fun screenshot(
        height: Int,
        scroll: Int,
        top: Int = 0,
        bottom: Int = 0,
        contentId: (Int) -> Int = { it },
    ): PixelRows {
        val ids = (0 until height).map { y ->
            when {
                y < top -> 1_000_000 + y
                y >= height - bottom -> 2_000_000 + y
                else -> contentId(scroll + (y - top))
            }
        }
        return fromRowIds(ids)
    }

    /** [page] with every pixel nudged a little, the way a resampled scroll or a recompression leaves it. */
    fun nudged(page: PixelRows, by: Int = 3): PixelRows {
        val pixels = IntArray(page.width * page.height)
        val row = IntArray(page.width)
        for (y in 0 until page.height) {
            page.copyRow(y, row)
            for (x in 0 until page.width) {
                val grey = ((row[x] and 0xFF) + by).coerceIn(0, 255)
                pixels[y * page.width + x] = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
            }
        }
        return ArrayPixelRows(page.width, page.height, pixels)
    }

    fun rowIds(page: PixelRows): List<Int> {
        val row = IntArray(page.width)
        return (0 until page.height).map { y ->
            page.copyRow(y, row)
            row[0] and 0xFFFFFF
        }
    }
}
