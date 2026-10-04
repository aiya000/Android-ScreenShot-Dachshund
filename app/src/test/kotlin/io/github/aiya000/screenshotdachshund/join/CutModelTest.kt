package io.github.aiya000.screenshotdachshund.join

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CutModelTest {

    /** Three pages of 100 rows, joined with the usual overlaps. */
    private val model = CutModel(
        cuts = listOf(Cut(0, 0, 90), Cut(1, 30, 90), Cut(2, 30, 100)),
        pageHeights = listOf(100, 100, 100),
    )

    @Test
    fun `a model has one seam fewer than it has cuts`() {
        assertEquals(2, model.seams)
        assertEquals(0, CutModel(listOf(Cut(0, 0, 100)), listOf(100)).seams)
    }

    @Test
    fun `the joined height is the rows of every cut`() {
        assertEquals(90 + 60 + 70, model.joinedHeight)
    }

    @Test
    fun `a seam sits where the cut above it ends in the joined image`() {
        assertEquals(90, model.seamRow(0))
        assertEquals(150, model.seamRow(1))
    }

    @Test
    fun `moving the upper edge down keeps more of the page above the seam`() {
        val moved = model.moveUpperEdge(seam = 0, rows = 5)

        assertEquals(Cut(0, 0, 95), moved.cuts[0])
        assertEquals(model.cuts[1], moved.cuts[1])
    }

    @Test
    fun `moving the upper edge up keeps less`() {
        assertEquals(Cut(0, 0, 80), model.moveUpperEdge(0, -10).cuts[0])
    }

    @Test
    fun `the upper edge stops at the bottom of its page and one row under its top`() {
        assertEquals(Cut(0, 0, 100), model.moveUpperEdge(0, 500).cuts[0])
        assertEquals(Cut(0, 0, 1), model.moveUpperEdge(0, -500).cuts[0])
    }

    @Test
    fun `moving the lower edge down keeps less of the page below the seam`() {
        val moved = model.moveLowerEdge(seam = 0, rows = 5)

        assertEquals(Cut(1, 35, 90), moved.cuts[1])
        assertEquals(model.cuts[0], moved.cuts[0])
    }

    @Test
    fun `moving the lower edge up keeps more`() {
        assertEquals(Cut(1, 20, 90), model.moveLowerEdge(0, -10).cuts[1])
    }

    @Test
    fun `the lower edge stops at the top of its page and one row over its bottom`() {
        assertEquals(Cut(1, 0, 90), model.moveLowerEdge(0, -500).cuts[1])
        assertEquals(Cut(1, 89, 90), model.moveLowerEdge(0, 500).cuts[1])
    }

    @Test
    fun `a move leaves the original alone`() {
        model.moveUpperEdge(0, 5)
        model.moveLowerEdge(1, 5)

        assertEquals(Cut(0, 0, 90), model.cuts[0])
        assertEquals(Cut(2, 30, 100), model.cuts[2])
    }

    @Test
    fun `a seam that does not exist is refused`() {
        val thrown = runCatching { model.moveUpperEdge(2, 1) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `the edges of a seam are the rows the user sees`() {
        assertEquals(90, model.upperEdge(0))
        assertEquals(30, model.lowerEdge(0))
    }
}

class CutModelRemoveTest {

    private val model = CutModel(
        cuts = listOf(Cut(0, 0, 90), Cut(1, 30, 90), Cut(2, 30, 100)),
        pageHeights = listOf(100, 100, 100),
    )

    @Test
    fun `removing a middle cut leaves its neighbours as they are`() {
        val removed = model.removeCut(1)

        assertEquals(listOf(Cut(0, 0, 90), Cut(2, 30, 100)), removed.cuts)
        assertEquals(model.pageHeights, removed.pageHeights)
    }

    @Test
    fun `removing the first cut makes the next one start at the top of its page`() {
        assertEquals(listOf(Cut(1, 0, 90), Cut(2, 30, 100)), model.removeCut(0).cuts)
    }

    @Test
    fun `removing the last cut makes the one before it end at the bottom of its page`() {
        assertEquals(listOf(Cut(0, 0, 90), Cut(1, 30, 100)), model.removeCut(2).cuts)
    }

    @Test
    fun `the last cut cannot be removed`() {
        val single = CutModel(listOf(Cut(0, 0, 100)), listOf(100))

        val thrown = runCatching { single.removeCut(0) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `a cut that does not exist is refused`() {
        val thrown = runCatching { model.removeCut(3) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `a removal leaves the original alone`() {
        model.removeCut(1)

        assertEquals(3, model.cuts.size)
    }

    @Test
    fun `the lower edge of a seam can be set outright, for a join worked out afresh`() {
        val set = model.withLowerEdge(seam = 0, fromRow = 50)

        assertEquals(Cut(1, 50, 90), set.cuts[1])
        assertEquals(Cut(1, 89, 90), model.withLowerEdge(0, 500).cuts[1])
        assertEquals(Cut(1, 0, 90), model.withLowerEdge(0, -5).cuts[1])
    }
}

class RejoinSeamTest {

    private val height = 40
    private val top = 5
    private val bottom = 3
    private val edge = FixedEdge(top, bottom)

    private fun shot(scroll: Int) =
        io.github.aiya000.screenshotdachshund.image.TestPages.screenshot(height, scroll, top, bottom)

    @Test
    fun `two pages that become neighbours are joined by their own overlap`() {
        val pages = listOf(shot(0), shot(12), shot(24))
        val model = CutModel(Joiner.layout(pages, edge), pages.map { it.height })

        val rejoined = model.removeCut(1).rejoinSeam(0, pages, edge)

        assertEquals(Cut(2, (height - bottom) - 24, height), rejoined.cuts[1])
    }

    @Test
    fun `two pages that do not overlap keep all of their content`() {
        val pages = listOf(shot(0), shot(12), shot(200))
        val model = CutModel(Joiner.layout(pages, edge), pages.map { it.height })

        val rejoined = model.removeCut(1).rejoinSeam(0, pages, edge)

        assertEquals(Cut(2, top, height), rejoined.cuts[1])
    }
}

class RejoinSeamTrailingBarTest {

    private val height = 40
    private val top = 5
    private val bottom = 3
    private val edge = FixedEdge(top, bottom)

    private fun shot(scroll: Int) =
        io.github.aiya000.screenshotdachshund.image.TestPages.screenshot(height, scroll, top, bottom)

    @Test
    fun `a bar at the bottom of the page above is cut away when the seam is joined afresh`() {
        val first = io.github.aiya000.screenshotdachshund.image.TestPages.screenshotWithBottomBar(height, 0, top, bottom, bar = 4)
        val pages = listOf(first, shot(9), shot(18))
        val model = CutModel(Joiner.layout(pages, edge), pages.map { it.height })

        val rejoined = model.removeCut(1).rejoinSeam(0, pages, edge)

        val toRow = (height - bottom) - 4
        assertEquals(listOf(Cut(0, 0, toRow), Cut(2, toRow - 18, height)), rejoined.cuts)
    }

    @Test
    fun `the upper edge of a seam can be set outright too`() {
        val model = CutModel(listOf(Cut(0, 0, 90), Cut(1, 30, 90)), listOf(100, 100))

        assertEquals(Cut(0, 0, 70), model.withUpperEdge(seam = 0, toRow = 70).cuts[0])
        assertEquals(Cut(0, 0, 100), model.withUpperEdge(0, 500).cuts[0])
        assertEquals(Cut(0, 0, 1), model.withUpperEdge(0, -5).cuts[0])
    }
}

/**
 * The very top of the first page and the very bottom of the last page can be trimmed: the
 * image starts and ends where the user says, the way a seam's edges do, one side each.
 */
class CutModelTrimTest {

    private val model = CutModel(
        cuts = listOf(Cut(0, 0, 90), Cut(1, 30, 90), Cut(2, 30, 100)),
        pageHeights = listOf(100, 100, 100),
    )

    @Test
    fun `the image starts where the first cut starts and ends where the last cut ends`() {
        assertEquals(0, model.start)
        assertEquals(100, model.end)
    }

    @Test
    fun `moving the start down trims the top of the image`() {
        val moved = model.moveStart(rows = 50)

        assertEquals(Cut(0, 50, 90), moved.cuts[0])
        assertEquals(model.cuts.drop(1), moved.cuts.drop(1))
        assertEquals(model.joinedHeight - 50, moved.joinedHeight)
    }

    @Test
    fun `the start stops at the top of its page and one row over the first cut's end`() {
        assertEquals(Cut(0, 0, 90), model.moveStart(-500).cuts[0])
        assertEquals(Cut(0, 89, 90), model.moveStart(500).cuts[0])
    }

    @Test
    fun `moving the end up trims the bottom of the image`() {
        val moved = model.moveEnd(rows = -50)

        assertEquals(Cut(2, 30, 50), moved.cuts[2])
        assertEquals(model.cuts.dropLast(1), moved.cuts.dropLast(1))
        assertEquals(model.joinedHeight - 50, moved.joinedHeight)
    }

    @Test
    fun `the end stops at the bottom of its page and one row under the last cut's start`() {
        assertEquals(Cut(2, 30, 100), model.moveEnd(500).cuts[2])
        assertEquals(Cut(2, 30, 31), model.moveEnd(-500).cuts[2])
    }

    @Test
    fun `a single cut can be trimmed at both ends`() {
        val single = CutModel(listOf(Cut(0, 0, 100)), listOf(100))

        assertEquals(Cut(0, 10, 80), single.moveStart(10).moveEnd(-20).cuts[0])
    }

    @Test
    fun `a trim leaves the original alone`() {
        model.moveStart(10)
        model.moveEnd(-10)

        assertEquals(Cut(0, 0, 90), model.cuts[0])
        assertEquals(Cut(2, 30, 100), model.cuts[2])
    }
}

class AdjustingTest {

    private val model = CutModel(
        cuts = listOf(Cut(0, 0, 90), Cut(1, 30, 90), Cut(2, 30, 100)),
        pageHeights = listOf(100, 100, 100),
    )

    @Test
    fun `a seam's edges move the cuts around it`() {
        assertEquals(Cut(0, 0, 95), model.moveUpperEdge(Adjusting.Seam(0), 5).cuts[0])
        assertEquals(Cut(1, 35, 90), model.moveLowerEdge(Adjusting.Seam(0), 5).cuts[1])
    }

    @Test
    fun `the start of the image is a lower edge, and has no upper edge`() {
        assertEquals(Cut(0, 20, 90), model.moveLowerEdge(Adjusting.Start, 20).cuts[0])
        assertEquals(model, model.moveUpperEdge(Adjusting.Start, 20))
    }

    @Test
    fun `the end of the image is an upper edge, and has no lower edge`() {
        assertEquals(Cut(2, 30, 80), model.moveUpperEdge(Adjusting.End, -20).cuts[2])
        assertEquals(model, model.moveLowerEdge(Adjusting.End, -20))
    }
}
