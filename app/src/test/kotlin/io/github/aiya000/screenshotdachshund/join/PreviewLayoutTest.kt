package io.github.aiya000.screenshotdachshund.join

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewLayoutTest {

    /** Three pages of 100 rows; previews are a quarter size; drawn at twice the preview size. */
    private val model = CutModel(
        cuts = listOf(Cut(0, 0, 90), Cut(1, 30, 90), Cut(2, 30, 100)),
        pageHeights = listOf(100, 100, 100),
    )

    @Test
    fun `each cut is a segment as tall as its preview rows, scaled`() {
        val layout = PreviewLayout.of(model, sample = 4, scale = 2f)

        // 90/4 = 22 rows, (90/4 - 30/4) = 22 - 7 = 15 rows, (100/4 - 30/4) = 25 - 7 = 18 rows
        assertEquals(listOf(44, 30, 36), layout.segments.map { it.height })
        assertEquals(listOf(0, 44, 74), layout.segments.map { it.top })
        assertEquals(110, layout.totalHeight)
    }

    @Test
    fun `a seam lies where the segment above it ends`() {
        val layout = PreviewLayout.of(model, sample = 4, scale = 2f)

        assertEquals(listOf(44, 74), layout.seamTops)
    }

    @Test
    fun `a page's centre is the middle of its segment`() {
        val layout = PreviewLayout.of(model, sample = 4, scale = 2f)

        assertEquals(listOf(22, 59, 92), layout.segments.map { it.centre })
    }

    @Test
    fun `the segment remembers which rows of the preview it shows`() {
        val layout = PreviewLayout.of(model, sample = 4, scale = 2f)

        assertEquals(1, layout.segments[1].cutIndex)
        assertEquals(7, layout.segments[1].previewTop)
        assertEquals(15, layout.segments[1].previewRows)
    }
}
