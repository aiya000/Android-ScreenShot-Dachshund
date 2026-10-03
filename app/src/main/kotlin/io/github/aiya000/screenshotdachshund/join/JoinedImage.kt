package io.github.aiya000.screenshotdachshund.join

import io.github.aiya000.screenshotdachshund.image.PixelRows

/**
 * The joined image: the cuts of the pages one under another, read row by row without
 * ever being held whole.
 *
 * Pages are fetched through [loadPage] as the rows are read, and only the page being
 * read is kept, so a capture of many pages needs the memory of one of them.
 */
class JoinedImage(
    override val width: Int,
    cuts: List<Cut>,
    private val loadPage: (Int) -> PixelRows,
) : PixelRows {

    constructor(pages: List<PixelRows>, cuts: List<Cut>) :
        this(pages.firstOrNull()?.width ?: 0, cuts, { pages[it] })

    /** A cut of no rows would share its start with the next one and be read in its place. */
    private val cuts: List<Cut> = cuts.filter { it.height > 0 }

    override val height: Int = this.cuts.sumOf { it.height }

    /** Where each cut starts in the joined image. */
    private val starts: IntArray = IntArray(this.cuts.size).also { starts ->
        var y = 0
        this.cuts.forEachIndexed { i, cut ->
            starts[i] = y
            y += cut.height
        }
    }

    private var loadedIndex = -1
    private var loaded: PixelRows? = null

    override fun copyRow(y: Int, out: IntArray) {
        val (page, row) = locate(y)
        page.copyRow(row, out)
    }

    override fun rowHash(y: Int): Long {
        val (page, row) = locate(y)
        return page.rowHash(row)
    }

    private fun locate(y: Int): Pair<PixelRows, Int> {
        require(y in 0 until height) { "row $y of $height" }
        var i = starts.binarySearch(y)
        if (i < 0) i = -i - 2
        val cut = cuts[i]
        return page(cut.pageIndex) to cut.fromRow + (y - starts[i])
    }

    private fun page(index: Int): PixelRows {
        if (loadedIndex != index) {
            loaded = loadPage(index)
            loadedIndex = index
        }
        return loaded!!
    }
}
