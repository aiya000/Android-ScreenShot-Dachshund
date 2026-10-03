package io.github.aiya000.screenshotdachshund.join

import io.github.aiya000.screenshotdachshund.image.PixelRows

/** How many rows at the top and at the bottom of every page are not part of the scrolling content. */
data class FixedEdge(val top: Int, val bottom: Int)

/**
 * Finds the status bar, toolbar and navigation bar without being told about them: they
 * are the rows that stay the same from one page to the next while the content between
 * them moves.
 */
object FixedEdges {

    /**
     * A page that is blank where it scrolls would look fixed all the way down, so the
     * edges are capped: the top may take at most a third of the page, the bottom a quarter.
     */
    fun topCap(height: Int): Int = height / 3
    fun bottomCap(height: Int): Int = height / 4

    /**
     * [atLeast] is what the system itself says is not content: the status bar and the
     * navigation bar, from the window insets. They are fixed even where their pixels are
     * not -- a clock moves on, and a gesture bar is translucent with the page showing
     * through -- so they are taken as a floor under whatever the pixels reveal.
     */
    fun detect(pages: List<PixelRows>, atLeast: FixedEdge = FixedEdge(0, 0)): FixedEdge {
        if (pages.isEmpty()) return atLeast
        val height = pages.minOf { it.height }
        val floor = FixedEdge(
            top = atLeast.top.coerceIn(0, topCap(height)),
            bottom = atLeast.bottom.coerceIn(0, bottomCap(height)),
        )
        if (pages.size < 2) return floor
        // Each pair can only make the edge smaller: the count stops at the limit the
        // pairs before it left.
        var top = topCap(height)
        var bottom = bottomCap(height)
        for (i in 1 until pages.size) {
            top = equalLeadingRows(pages[i - 1], pages[i], top)
            bottom = equalTrailingRows(pages[i - 1], pages[i], bottom)
        }
        return FixedEdge(maxOf(top, floor.top), maxOf(bottom, floor.bottom))
    }

    private fun equalLeadingRows(a: PixelRows, b: PixelRows, limit: Int): Int {
        var n = 0
        while (n < limit && a.rowHash(n) == b.rowHash(n)) n++
        return n
    }

    private fun equalTrailingRows(a: PixelRows, b: PixelRows, limit: Int): Int {
        var n = 0
        while (n < limit && a.rowHash(a.height - 1 - n) == b.rowHash(b.height - 1 - n)) n++
        return n
    }
}
