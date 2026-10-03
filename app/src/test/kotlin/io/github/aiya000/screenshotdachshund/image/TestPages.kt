package io.github.aiya000.screenshotdachshund.image

/**
 * Synthetic pages for the tests. A row is identified by an integer id; every pixel of
 * the row is derived from that id, so two rows are equal exactly when their ids are.
 */
object TestPages {

    const val WIDTH = 8

    /** Every id handed out, so that [rowIds] can read a page back. */
    private val idsByPixel = HashMap<Int, Int>()

    fun pixelOf(id: Int): Int {
        val pixel = (id * 2654435761L).toInt() or 0xFF000000.toInt()
        idsByPixel[pixel] = id
        return pixel
    }

    /** A page whose row ids are given one by one. */
    fun fromRowIds(ids: List<Int>, width: Int = WIDTH): PixelRows {
        val pixels = IntArray(width * ids.size)
        ids.forEachIndexed { y, id ->
            for (x in 0 until width) pixels[y * width + x] = pixelOf(id)
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

    fun rowIds(page: PixelRows): List<Int> {
        val row = IntArray(page.width)
        return (0 until page.height).map { y ->
            page.copyRow(y, row)
            idsByPixel.getValue(row[0])
        }
    }
}
