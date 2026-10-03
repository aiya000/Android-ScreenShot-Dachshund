package io.github.aiya000.screenshotdachshund.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelRowsTest {

    @Test
    fun `equal rows hash alike and different rows do not`() {
        val page = TestPages.fromRowIds(listOf(1, 2, 1, 3))

        assertEquals(page.rowHash(0), page.rowHash(2))
        assertNotEquals(page.rowHash(0), page.rowHash(1))
        assertNotEquals(page.rowHash(1), page.rowHash(3))
    }

    @Test
    fun `a single differing pixel changes the row hash`() {
        val a = IntArray(8) { 0x11223344 }
        val b = a.copyOf().also { it[5] = 0x11223345 }

        assertNotEquals(hashRow(a, 8), hashRow(b, 8))
    }

    @Test
    fun `copyRow hands back the pixels of that row`() {
        val page = TestPages.fromRowIds(listOf(7, 8))
        val out = IntArray(TestPages.WIDTH)

        page.copyRow(1, out)

        assertTrue(out.all { it == TestPages.pixelOf(8) })
    }

    @Test
    fun `contentEquals is true only for the same rows in the same order`() {
        val a = TestPages.fromRowIds(listOf(1, 2, 3))
        val same = TestPages.fromRowIds(listOf(1, 2, 3))
        val reordered = TestPages.fromRowIds(listOf(3, 2, 1))
        val shorter = TestPages.fromRowIds(listOf(1, 2))

        assertTrue(a.contentEquals(same))
        assertFalse(a.contentEquals(reordered))
        assertFalse(a.contentEquals(shorter))
    }
}

class DifferingRowsTest {

    @Test
    fun `differingRows counts the rows whose hashes differ`() {
        val a = TestPages.fromRowIds(listOf(1, 2, 3, 4))
        val b = TestPages.fromRowIds(listOf(1, 9, 3, 8))

        assertEquals(2, a.differingRows(b))
        assertEquals(0, a.differingRows(a))
    }

    @Test
    fun `images of different sizes differ in every row`() {
        val a = TestPages.fromRowIds(listOf(1, 2, 3))
        val b = TestPages.fromRowIds(listOf(1, 2))

        assertEquals(Int.MAX_VALUE, a.differingRows(b))
    }
}
