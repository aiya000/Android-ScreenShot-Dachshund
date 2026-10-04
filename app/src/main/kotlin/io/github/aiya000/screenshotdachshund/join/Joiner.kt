package io.github.aiya000.screenshotdachshund.join

import io.github.aiya000.screenshotdachshund.image.PixelRows
import io.github.aiya000.screenshotdachshund.image.RowSketch
import io.github.aiya000.screenshotdachshund.image.contentEquals
import kotlin.math.max
import kotlin.math.min

/** The rows `[fromRow, toRow)` of page [pageIndex] that go into the joined image. */
data class Cut(val pageIndex: Int, val fromRow: Int, val toRow: Int) {
    val height: Int get() = toRow - fromRow
}

/** Where a page stops being shown ([toRow], a row of that page) and where the page after it starts ([fromRow], a row of its own). */
data class Seam(val toRow: Int, val fromRow: Int)

/**
 * Works out how the pages of one capture fit together.
 *
 * Each page overlaps the one before it by however much the swipe did not scroll. That
 * overlap is found by taking bands of rows from the content of the later page and looking
 * for rows that look the same in the earlier one; the distance between the two places is
 * how far the content moved. Every distance a band proposes is then scored over the whole
 * of the overlap it implies, and the distance under which the largest share of rows agree
 * is the one taken, provided enough of them do. A share rather than a count: a count
 * favours small distances, whose overlaps are longer, and joined a settings list on top
 * of itself.
 *
 * Rows are compared by their [RowSketch], not pixel for pixel: a browser scrolls by
 * fractions of a pixel and resamples the page, an image loads in a moment later, and the
 * rows of two screenshots of the same content are rarely identical. Only rows that show
 * something count towards a score; a blank row is alike to blank rows at any distance and
 * says nothing about where the page is.
 */
object Joiner {

    /** How many rows a probe band has, at most. */
    private const val BAND_ROWS = 24

    /** Over how many places of the content the bands are spread. */
    private const val BANDS = 8

    /** How much of a band has to be alike for the band to propose a distance. */
    private const val BAND_ALIKE_FRACTION = 0.75

    /**
     * How much of the overlap's textured rows have to agree for a distance to be believed.
     * A real join scores two thirds and up on a settings list and on a browser page full
     * of images; a wrong candidate on a stretched end-of-list page reached a third.
     */
    private const val ACCEPT_FRACTION = 0.5

    /** And at least this many of them, whatever the fraction. */
    private const val ACCEPT_ROWS = 8

    fun layout(pages: List<PixelRows>, fixed: FixedEdge = FixedEdges.detect(pages)): List<Cut> {
        if (pages.isEmpty()) return emptyList()
        val cuts = mutableListOf(Cut(0, 0, pages[0].height))
        for (i in 1 until pages.size) {
            val prev = pages[cuts.last().pageIndex]
            val next = pages[i]
            if (next.contentEquals(prev)) continue
            // Nothing new on this page (the clock moved on, the list did not): the page
            // before keeps its bottom bar and this one is left out
            val seam = seam(prev, next, fixed) ?: continue
            cuts[cuts.lastIndex] = cuts.last().copy(toRow = seam.toRow)
            cuts += Cut(i, seam.fromRow, next.height)
        }
        return cuts
    }

    /**
     * Where [prev] hands over to [next], or null when [next] shows nothing that [prev] did
     * not already show.
     *
     * [prev] stops at the bottom of its content, unless the bottom of its content is
     * something [next] does not have at the same place: a browser's URL bar sits at the
     * bottom of the first page and is gone from the page after it, once the page has
     * scrolled. Then [prev] stops where its rows stop being alike to their counterparts in
     * [next], and [next] starts right under that, where the content under the bar is to
     * be seen. Either way the two meet at a row they share, so the image reads on without
     * a step.
     */
    fun seam(prev: PixelRows, next: PixelRows, fixed: FixedEdge): Seam? {
        val contentEnd = prev.height - fixed.bottom
        val distance = findOverlap(prev, next, fixed) ?: return Seam(contentEnd, fixed.top)
        if (distance <= 0 || contentEnd - distance < fixed.top) return null
        val toRow = agreeingEnd(prev, next, distance, fixed.top + distance, contentEnd)
        return Seam(toRow, toRow - distance)
    }

    /**
     * One past the lowest row of [prev] in `[from, contentEnd)` that shows something and
     * is alike to its counterpart in [next], [distance] rows up. Rows under it are a bar
     * that only [prev] has, or blank rows that look the same on both pages anyway and so
     * may as well come from [next]. [contentEnd] when no row agrees.
     */
    private fun agreeingEnd(prev: PixelRows, next: PixelRows, distance: Int, from: Int, contentEnd: Int): Int {
        // (A counterpart has to exist: the pages of one capture are the same height, but a
        // shorter next page would end sooner)
        var r = min(contentEnd, next.height + distance) - 1
        while (r >= from) {
            val sketch = prev.rowSketch(r)
            if (RowSketch.textured(sketch) && RowSketch.alike(sketch, next.rowSketch(r - distance))) return r + 1
            r--
        }
        return contentEnd
    }

    /**
     * How many rows the content moved up between [prev] and [next], or null when nothing
     * of [next]'s content is to be seen in [prev] with any confidence.
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
        var bestShare = 0.0
        for (distance in candidates.sorted()) {
            val (agreeing, textured) = agreement(prev, next, distance, contentTop, contentEnd)
            // Too short an overlap says nothing: a share of a handful of rows is noise
            if (textured < band) continue
            val share = agreeing.toDouble() / textured
            if (agreeing >= ACCEPT_ROWS && share >= ACCEPT_FRACTION && share > bestShare) {
                bestShare = share
                best = distance
            }
        }
        return best
    }

    /**
     * Over the rows of [next]'s content that would lie on [prev] when moved by [distance]:
     * how many textured rows agree, and how many textured rows there are.
     */
    private fun agreement(prev: PixelRows, next: PixelRows, distance: Int, contentTop: Int, contentEnd: Int): Pair<Int, Int> {
        var agreeing = 0
        var textured = 0
        var r = contentTop
        while (r + distance < contentEnd) {
            val sketch = next.rowSketch(r)
            if (RowSketch.textured(sketch)) {
                textured++
                if (RowSketch.alike(sketch, prev.rowSketch(r + distance))) agreeing++
            }
            r++
        }
        return agreeing to textured
    }

    /**
     * The first band of [next]'s content at or after [from] whose rows are not all alike. A
     * blank band would match blank rows anywhere, so it is skipped.
     */
    private fun probeStart(next: PixelRows, from: Int, contentEnd: Int, band: Int): Int? {
        var s = from
        while (s + band <= contentEnd) {
            val first = next.rowSketch(s)
            for (k in 1 until band) {
                if (!RowSketch.alike(next.rowSketch(s + k), first)) return s
            }
            s++
        }
        return null
    }

    private fun bandMatches(prev: PixelRows, y: Int, next: PixelRows, s: Int, band: Int): Boolean {
        val needed = max(1, (band * BAND_ALIKE_FRACTION).toInt())
        var alike = 0
        for (k in 0 until band) {
            if (RowSketch.alike(prev.rowSketch(y + k), next.rowSketch(s + k))) alike++
        }
        return alike >= needed
    }
}
