package io.github.aiya000.screenshotdachshund.join

import io.github.aiya000.screenshotdachshund.image.PixelRows
import io.github.aiya000.screenshotdachshund.image.contentEquals
import kotlin.math.max
import kotlin.math.min

/** The rows `[fromRow, toRow)` of page [pageIndex] that go into the joined image. */
data class Cut(val pageIndex: Int, val fromRow: Int, val toRow: Int) {
    val height: Int get() = toRow - fromRow
}

/**
 * Works out how the pages of one capture fit together.
 *
 * Each page overlaps the one before it by however much the swipe did not scroll. That
 * overlap is found by taking bands of rows from the content of the later page and looking
 * for the same rows in the earlier one; the distance between the two places is how far
 * the content moved. Every distance a band proposes is then scored over the whole of the
 * content, and the distance under which the most rows agree is the one taken. A band on
 * its own can be fooled -- by a status bar whose clock moved on, so that the bar is not
 * quite fixed, or by a blank stretch that looks the same anywhere -- but a wrong distance
 * agrees with only a few rows, and the right one with almost all of them.
 */
object Joiner {

    /** How many rows a probe band has, at most. */
    private const val BAND_ROWS = 24

    /** Over how many places of the content the bands are spread. */
    private const val BANDS = 8

    fun layout(pages: List<PixelRows>, fixed: FixedEdge = FixedEdges.detect(pages)): List<Cut> {
        if (pages.isEmpty()) return emptyList()
        val cuts = mutableListOf(Cut(0, 0, pages[0].height))
        for (i in 1 until pages.size) {
            val prev = pages[cuts.last().pageIndex]
            val next = pages[i]
            if (next.contentEquals(prev)) continue

            val contentEnd = prev.height - fixed.bottom
            val overlap = findOverlap(prev, next, fixed)
            val fromRow = if (overlap == null) fixed.top else max(fixed.top, contentEnd - overlap)
            // Nothing new on this page (the clock moved on, the list did not): the page
            // before keeps its bottom bar and this one is left out
            if (fromRow >= contentEnd) continue
            cuts[cuts.lastIndex] = cuts.last().copy(toRow = contentEnd)
            cuts += Cut(i, fromRow, next.height)
        }
        return cuts
    }

    /**
     * How many rows the content moved up between [prev] and [next], or null when nothing
     * of [next]'s content is still to be seen in [prev].
     */
    fun findOverlap(prev: PixelRows, next: PixelRows, fixed: FixedEdge): Int? {
        val contentTop = fixed.top
        val contentEnd = min(prev.height, next.height) - fixed.bottom
        val contentRows = contentEnd - contentTop
        if (contentRows <= 0) return null
        val band = min(BAND_ROWS, max(1, contentRows / 4))

        val candidates = HashSet<Int>()
        val step = max(band, contentRows / BANDS)
        var start = contentTop
        while (start + band <= contentEnd) {
            val probe = probeStart(next, start, contentEnd, band) ?: break
            var y = probe
            while (y + band <= contentEnd) {
                if (bandMatches(prev, y, next, probe, band)) candidates += y - probe
                y++
            }
            start = max(probe + 1, start + step)
        }

        var best: Int? = null
        var bestScore = band - 1
        for (distance in candidates.sorted()) {
            val score = agreeingRows(prev, next, distance, contentTop, contentEnd)
            if (score > bestScore) {
                bestScore = score
                best = distance
            }
        }
        return best
    }

    /** How many content rows of [next] agree with [prev] when the content moved by [distance]. */
    private fun agreeingRows(prev: PixelRows, next: PixelRows, distance: Int, contentTop: Int, contentEnd: Int): Int {
        var n = 0
        var r = contentTop
        while (r + distance < contentEnd) {
            if (next.rowHash(r) == prev.rowHash(r + distance)) n++
            r++
        }
        return n
    }

    /**
     * The first band of [next]'s content at or after [from] whose rows are not all alike. A
     * blank band would match blank rows anywhere, so it is skipped.
     */
    private fun probeStart(next: PixelRows, from: Int, contentEnd: Int, band: Int): Int? {
        var s = from
        while (s + band <= contentEnd) {
            val first = next.rowHash(s)
            for (k in 1 until band) {
                if (next.rowHash(s + k) != first) return s
            }
            s++
        }
        return null
    }

    private fun bandMatches(prev: PixelRows, y: Int, next: PixelRows, s: Int, band: Int): Boolean {
        for (k in 0 until band) {
            if (prev.rowHash(y + k) != next.rowHash(s + k)) return false
        }
        return true
    }
}
