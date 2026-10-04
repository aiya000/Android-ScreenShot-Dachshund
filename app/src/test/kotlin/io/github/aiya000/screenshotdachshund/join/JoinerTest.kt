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


class JoinerToleranceTest {

    private val height = 60
    private val top = 5
    private val bottom = 3
    private val edge = FixedEdge(top, bottom)

    private fun shot(scroll: Int) =
        io.github.aiya000.screenshotdachshund.image.TestPages.screenshot(height, scroll, top, bottom)

    @Test
    fun `an overlap is found although every row was nudged, as a resampled scroll leaves it`() {
        val a = shot(0)
        val b = io.github.aiya000.screenshotdachshund.image.TestPages.nudged(shot(12), by = 4)

        assertEquals(12, Joiner.findOverlap(a, b, edge))
    }

    @Test
    fun `an overlap is found although a block of rows changed, as a late-loading image leaves it`() {
        // Content rows 20..29 look different on the second page: an image came in.
        val a = shot(0)
        val b = io.github.aiya000.screenshotdachshund.image.TestPages.screenshot(
            height, 12, top, bottom,
        ) { if (it in 20..29) 9_000_000 + it else it }

        assertEquals(12, Joiner.findOverlap(a, b, edge))
    }

    @Test
    fun `too few agreeing rows is no overlap at all`() {
        // Only content rows 0..3 are shared; everything else on the second page is new.
        val a = shot(0)
        val b = io.github.aiya000.screenshotdachshund.image.TestPages.screenshot(
            height, 12, top, bottom,
        ) { if (it in 12..15) it else 9_000_000 + it }

        assertNull(Joiner.findOverlap(a, b, edge))
    }
}

/**
 * Firefox keeps its URL bar at the bottom of the screen and hides it once the page
 * scrolls, so the first page carries a bar at the bottom of its content that no later
 * page has. The join has to cut the first page above that bar and take the rows under it
 * from the next page, where they are visible.
 */
class JoinerTrailingBarTest {

    private val height = 40
    private val top = 5
    private val bottom = 3
    private val edge = FixedEdge(top, bottom)
    private val contentEnd = height - bottom

    private fun shot(scroll: Int, contentId: (Int) -> Int = { it }) =
        TestPages.screenshot(height, scroll, top, bottom, contentId)

    private fun barred(scroll: Int, bar: Int, contentId: (Int) -> Int = { it }) =
        TestPages.screenshotWithBottomBar(height, scroll, top, bottom, bar, contentId)

    @Test
    fun `a bar only the first page has at its bottom is cut away, and the next page starts under it`() {
        val cuts = Joiner.layout(listOf(barred(0, bar = 6), shot(12)), edge)

        assertEquals(
            listOf(
                Cut(0, 0, contentEnd - 6),
                Cut(1, (contentEnd - 6) - 12, height),
            ),
            cuts,
        )
    }

    @Test
    fun `the seams after the bar are cut where they always were`() {
        val cuts = Joiner.layout(listOf(barred(0, bar = 6), shot(12), shot(24)), edge)

        assertEquals(
            listOf(
                Cut(0, 0, contentEnd - 6),
                Cut(1, (contentEnd - 6) - 12, contentEnd),
                Cut(2, contentEnd - 12, height),
            ),
            cuts,
        )
    }

    @Test
    fun `a blank row under the bar does not pass for content that agrees`() {
        // The bar has a blank row of padding above and below its 4 textured rows, and the
        // page happens to be blank where that padding lies (content rows 26 and 31), so
        // those two rows are alike on both pages without saying anything.
        val padded: (Int) -> Int = { if (it == 26 || it == 31) TestPages.BLANK else it }
        val first = TestPages.fromRowIds(
            (0 until height).map { y ->
                when {
                    y < top -> 1_000_000 + y
                    y >= contentEnd -> 2_000_000 + y
                    y in 32..35 -> 5_000_000 + y
                    else -> padded(y - top)
                }
            },
        )

        val cuts = Joiner.layout(listOf(first, shot(12, padded)), edge)

        assertEquals(listOf(Cut(0, 0, 31), Cut(1, 31 - 12, height)), cuts)
    }

    @Test
    fun `rows that differ in the middle of the overlap do not move the cut`() {
        // Content rows 20..29 look different on the second page: an image came in.
        val cuts = Joiner.layout(listOf(shot(0), shot(12) { if (it in 20..29) 9_000_000 + it else it }), edge)

        assertEquals(listOf(Cut(0, 0, contentEnd), Cut(1, contentEnd - 12, height)), cuts)
    }

    @Test
    fun `a page that scrolled nothing is still left out, bar or no bar`() {
        val cuts = Joiner.layout(listOf(barred(0, bar = 6), barred(0, bar = 6) { if (it == 3) 9 else it }), edge)

        assertEquals(listOf(Cut(0, 0, height)), cuts)
    }
}
