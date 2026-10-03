package io.github.aiya000.screenshotdachshund.join

import io.github.aiya000.screenshotdachshund.image.TestPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JoinerTest {

    private val height = 40
    private val top = 5
    private val bottom = 3
    private val edge = FixedEdge(top, bottom)

    private fun shot(scroll: Int, contentId: (Int) -> Int = { it }) =
        TestPages.screenshot(height, scroll, top, bottom, contentId)

    @Test
    fun `the overlap of two pages is how far the content moved`() {
        assertEquals(12, Joiner.findOverlap(shot(0), shot(12), edge))
        assertEquals(1, Joiner.findOverlap(shot(0), shot(1), edge))
        assertEquals(24, Joiner.findOverlap(shot(0), shot(24), edge))
    }

    @Test
    fun `an overlap shorter than the probe band is not found`() {
        // 32 content rows, so the band is 8 rows; a scroll of 25 leaves only 7 in common.
        assertNull(Joiner.findOverlap(shot(0), shot(25), edge))
    }

    @Test
    fun `content that moved out of sight entirely has no overlap`() {
        assertNull(Joiner.findOverlap(shot(0), shot(32), edge))
        assertNull(Joiner.findOverlap(shot(0), shot(500), edge))
    }

    @Test
    fun `a blank band at the top of the next page is not used as the probe`() {
        // Content rows 20..29 are blank (all the same), the rest are distinct.
        val blank: (Int) -> Int = { if (it in 20..29) 0 else it }

        assertEquals(20, Joiner.findOverlap(shot(0, blank), shot(20, blank), edge))
    }

    @Test
    fun `the longest match wins where a band repeats`() {
        // Content rows 10..13 are repeated at 30..33.
        val repeating: (Int) -> Int = { if (it in 30..33) it - 20 else it }

        assertEquals(10, Joiner.findOverlap(shot(0, repeating), shot(10, repeating), edge))
    }

    @Test
    fun `a single page is used whole`() {
        assertEquals(listOf(Cut(0, 0, height)), Joiner.layout(listOf(shot(0)), edge))
    }

    @Test
    fun `the first page keeps the top bar, the last keeps the bottom bar, the overlap is cut once`() {
        val cuts = Joiner.layout(listOf(shot(0), shot(12)), edge)

        assertEquals(
            listOf(
                Cut(0, 0, height - bottom),
                Cut(1, (height - bottom) - 12, height),
            ),
            cuts,
        )
    }

    @Test
    fun `middle pages lose both bars`() {
        val cuts = Joiner.layout(listOf(shot(0), shot(12), shot(24)), edge)

        assertEquals(
            listOf(
                Cut(0, 0, height - bottom),
                Cut(1, (height - bottom) - 12, height - bottom),
                Cut(2, (height - bottom) - 12, height),
            ),
            cuts,
        )
    }

    @Test
    fun `a page without overlap contributes all of its content`() {
        val cuts = Joiner.layout(listOf(shot(0), shot(100)), edge)

        assertEquals(Cut(1, top, height), cuts[1])
    }

    @Test
    fun `a page identical to the one before is dropped`() {
        val cuts = Joiner.layout(listOf(shot(0), shot(0), shot(12)), edge)

        assertEquals(listOf(0, 2), cuts.map { it.pageIndex })
        assertEquals((height - bottom) - 12, cuts[1].fromRow)
    }

    @Test
    fun `the default edges are detected from the pages themselves`() {
        val cuts = Joiner.layout(listOf(shot(0), shot(12)))

        assertEquals(Cut(1, (height - bottom) - 12, height), cuts[1])
    }

    @Test
    fun `no pages give no cuts`() {
        assertEquals(emptyList<Cut>(), Joiner.layout(emptyList(), edge))
    }
}

class JoinerStatusBarTest {

    private val height = 40
    private val bottom = 3

    /**
     * A page whose status bar has a clock: rows 0..4 are the bar, and row 2 changes with
     * every page, the way a clock does. So only rows 0 and 1 look fixed.
     */
    private fun shotWithClock(scroll: Int, tick: Int, contentId: (Int) -> Int = { it }) =
        io.github.aiya000.screenshotdachshund.image.TestPages.fromRowIds(
            (0 until height).map { y ->
                when {
                    y == 2 -> 3_000_000 + tick
                    y < 5 -> 1_000_000 + y
                    y >= height - bottom -> 2_000_000 + y
                    else -> contentId(scroll + (y - 5))
                }
            },
        )

    @Test
    fun `a status bar whose clock changed is not taken for the overlap`() {
        val a = shotWithClock(scroll = 0, tick = 1)
        val b = shotWithClock(scroll = 12, tick = 2)
        val edge = FixedEdges.detect(listOf(a, b))

        assertEquals(FixedEdge(top = 2, bottom = 3), edge)
        assertEquals(12, Joiner.findOverlap(a, b, edge))
    }

    @Test
    fun `a blank run under a changing clock does not pass for an overlap of nothing`() {
        // Content rows 0..9 and 12..21 are blank, so the rows right under the status bar
        // look alike on both pages even though the page scrolled by 12.
        val blank: (Int) -> Int = { if (it in 0..9 || it in 12..21) 0 else it }
        val a = shotWithClock(scroll = 0, tick = 1, contentId = blank)
        val b = shotWithClock(scroll = 12, tick = 2, contentId = blank)

        assertEquals(12, Joiner.findOverlap(a, b, FixedEdges.detect(listOf(a, b))))
    }
}

class JoinerNothingNewTest {

    private val height = 40

    /** A page scrolled by [scroll] whose status-bar clock reads [tick]; no bottom bar. */
    private fun shot(scroll: Int, tick: Int) =
        io.github.aiya000.screenshotdachshund.image.TestPages.fromRowIds(
            (0 until height).map { y ->
                when {
                    y == 2 -> 3_000_000 + tick
                    y < 5 -> 1_000_000 + y
                    else -> scroll + (y - 5)
                }
            },
        )

    @Test
    fun `a page that scrolled nothing contributes nothing and leaves the page before it whole`() {
        val cuts = Joiner.layout(listOf(shot(0, 1), shot(0, 2)))

        assertEquals(listOf(Cut(0, 0, height)), cuts)
    }

    @Test
    fun `the page after a page that scrolled nothing still joins onto the one before`() {
        val cuts = Joiner.layout(listOf(shot(0, 1), shot(0, 2), shot(12, 3)))

        assertEquals(listOf(Cut(0, 0, height), Cut(2, height - 12, height)), cuts)
    }
}
