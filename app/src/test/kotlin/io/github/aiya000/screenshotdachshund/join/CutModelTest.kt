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
