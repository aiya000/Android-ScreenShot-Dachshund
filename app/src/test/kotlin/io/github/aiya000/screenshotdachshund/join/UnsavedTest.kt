package io.github.aiya000.screenshotdachshund.join

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsavedTest {

    private val model = CutModel(
        cuts = listOf(Cut(0, 0, 90), Cut(1, 30, 100)),
        pageHeights = listOf(100, 100),
    )

    @Test
    fun `a capture that was never saved is unsaved work`() {
        assertTrue(unsavedWork(saved = null, current = model))
    }

    @Test
    fun `a capture saved as it is now is not`() {
        assertFalse(unsavedWork(saved = model, current = model))
    }

    @Test
    fun `an edge moved since the save is unsaved work`() {
        assertTrue(unsavedWork(saved = model, current = model.moveUpperEdge(0, 5)))
        assertTrue(unsavedWork(saved = model, current = model.moveStart(1)))
    }

    @Test
    fun `an edge moved and moved back is not`() {
        assertFalse(unsavedWork(saved = model, current = model.moveUpperEdge(0, 5).moveUpperEdge(0, -5)))
    }
}
