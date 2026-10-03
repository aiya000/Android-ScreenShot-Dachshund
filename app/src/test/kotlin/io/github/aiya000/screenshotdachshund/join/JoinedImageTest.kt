package io.github.aiya000.screenshotdachshund.join

import io.github.aiya000.screenshotdachshund.image.TestPages
import org.junit.Assert.assertEquals
import org.junit.Test

class JoinedImageTest {

    private val height = 40
    private val top = 5
    private val bottom = 3

    private fun shot(scroll: Int) = TestPages.screenshot(height, scroll, top, bottom)

    @Test
    fun `the joined rows are the cut rows of each page in order`() {
        val pages = listOf(shot(0), shot(12), shot(24))
        val cuts = Joiner.layout(pages, FixedEdge(top, bottom))

        val joined = JoinedImage(pages, cuts)

        val expected = (0 until top).map { 1_000_000 + it } +
            (0 until 24 + (height - top - bottom)).toList() +
            (height - bottom until height).map { 2_000_000 + it }
        assertEquals(expected, TestPages.rowIds(joined))
        assertEquals(TestPages.WIDTH, joined.width)
        assertEquals(expected.size, joined.height)
    }

    @Test
    fun `a page loader is asked for one page at a time, in order`() {
        val pages = listOf(shot(0), shot(12))
        val cuts = Joiner.layout(pages, FixedEdge(top, bottom))
        val asked = mutableListOf<Int>()
        val joined = JoinedImage(TestPages.WIDTH, cuts) { index ->
            asked += index
            pages[index]
        }

        TestPages.rowIds(joined)

        assertEquals(listOf(0, 1), asked)
    }
}

class JoinedImageEmptyCutTest {

    @Test
    fun `a cut of no rows is skipped rather than read`() {
        val pages = listOf(
            io.github.aiya000.screenshotdachshund.image.TestPages.fromRowIds((0 until 40).toList()),
            io.github.aiya000.screenshotdachshund.image.TestPages.fromRowIds((100 until 140).toList()),
        )
        val cuts = listOf(Cut(0, 0, 10), Cut(1, 40, 40), Cut(1, 5, 40))

        val joined = JoinedImage(pages, cuts)

        assertEquals(45, joined.height)
        assertEquals(
            (0 until 10).toList() + (105 until 140).toList(),
            io.github.aiya000.screenshotdachshund.image.TestPages.rowIds(joined),
        )
    }
}
