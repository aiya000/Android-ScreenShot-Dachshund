package io.github.aiya000.screenshotdachshund.join

import io.github.aiya000.screenshotdachshund.image.TestPages
import org.junit.Assert.assertEquals
import org.junit.Test

class FixedEdgesTest {

    @Test
    fun `the rows that never change are the fixed edges`() {
        val a = TestPages.screenshot(height = 40, scroll = 0, top = 5, bottom = 3)
        val b = TestPages.screenshot(height = 40, scroll = 12, top = 5, bottom = 3)

        assertEquals(FixedEdge(top = 5, bottom = 3), FixedEdges.detect(listOf(a, b)))
    }

    @Test
    fun `a single page has no fixed edges`() {
        val a = TestPages.screenshot(height = 40, scroll = 0, top = 5, bottom = 3)

        assertEquals(FixedEdge(top = 0, bottom = 0), FixedEdges.detect(listOf(a)))
    }

    @Test
    fun `the edges are the smallest ones over all neighbouring pairs`() {
        val a = TestPages.screenshot(height = 40, scroll = 0, top = 5, bottom = 3)
        val b = TestPages.screenshot(height = 40, scroll = 12, top = 5, bottom = 3)
        // The third page has a smaller toolbar and no navigation bar.
        val c = TestPages.screenshot(height = 40, scroll = 24, top = 2, bottom = 0)

        assertEquals(FixedEdge(top = 2, bottom = 0), FixedEdges.detect(listOf(a, b, c)))
    }

    @Test
    fun `a later pair with bigger bars does not undo a smaller edge found earlier`() {
        val small = TestPages.screenshot(height = 40, scroll = 0, top = 2, bottom = 0)
        val a = TestPages.screenshot(height = 40, scroll = 12, top = 5, bottom = 3)
        val b = TestPages.screenshot(height = 40, scroll = 24, top = 5, bottom = 3)

        assertEquals(FixedEdge(top = 2, bottom = 0), FixedEdges.detect(listOf(small, a, b)))
    }

    @Test
    fun `uniform content cannot pass for a fixed edge beyond the cap`() {
        // Every content row is blank, so all 40 rows are equal between the two pages.
        val a = TestPages.screenshot(height = 40, scroll = 0) { 0 }
        val b = TestPages.screenshot(height = 40, scroll = 12) { 0 }

        val edge = FixedEdges.detect(listOf(a, b))

        assertEquals(FixedEdges.topCap(40), edge.top)
        assertEquals(FixedEdges.bottomCap(40), edge.bottom)
    }

    @Test
    fun `the caps leave most of the page as content`() {
        assertEquals(13, FixedEdges.topCap(40))
        assertEquals(10, FixedEdges.bottomCap(40))
    }
}

class FixedEdgesAtLeastTest {

    @Test
    fun `the system bars are fixed even where their pixels change`() {
        // A translucent navigation bar: the content shows through, so no bottom row is
        // the same on both pages, and the clock keeps only two rows of the status bar fixed.
        val a = TestPages.screenshot(height = 40, scroll = 0, top = 2, bottom = 0)
        val b = TestPages.screenshot(height = 40, scroll = 12, top = 2, bottom = 0)

        val edge = FixedEdges.detect(listOf(a, b), atLeast = FixedEdge(top = 5, bottom = 3))

        assertEquals(FixedEdge(top = 5, bottom = 3), edge)
    }

    @Test
    fun `a detected edge bigger than the system bars is kept`() {
        val a = TestPages.screenshot(height = 40, scroll = 0, top = 8, bottom = 4)
        val b = TestPages.screenshot(height = 40, scroll = 12, top = 8, bottom = 4)

        assertEquals(FixedEdge(top = 8, bottom = 4), FixedEdges.detect(listOf(a, b), atLeast = FixedEdge(5, 3)))
    }

    @Test
    fun `a single page still honours the system bars`() {
        val a = TestPages.screenshot(height = 40, scroll = 0)

        assertEquals(FixedEdge(5, 3), FixedEdges.detect(listOf(a), atLeast = FixedEdge(5, 3)))
    }
}
