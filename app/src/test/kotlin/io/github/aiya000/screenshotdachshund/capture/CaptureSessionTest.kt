package io.github.aiya000.screenshotdachshund.capture

import io.github.aiya000.screenshotdachshund.image.TestPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSessionTest {

    private fun page(scroll: Int) = TestPages.screenshot(height = 20, scroll = scroll)

    @Test
    fun `starting asks for the first screenshot`() {
        val session = CaptureSession()

        assertEquals(Command.TakeScreenshot, session.start())
    }

    @Test
    fun `a new page is kept and followed by a swipe, and a swipe by a screenshot`() {
        val session = CaptureSession()
        session.start()

        assertEquals(Command.Swipe, session.onScreenshot(page(0)))
        assertEquals(Command.TakeScreenshot, session.onSwipeFinished())
        assertEquals(Command.Swipe, session.onScreenshot(page(10)))
        assertEquals(2, session.pages.size)
    }

    @Test
    fun `a page identical to the one before ends the capture without keeping it`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(0))
        session.onSwipeFinished()

        val finish = session.onScreenshot(page(0)) as Command.Finish

        assertEquals(FinishReason.EndOfContent, finish.reason)
        assertEquals(1, finish.pages.size)
    }

    @Test
    fun `stop while a screenshot is in flight finishes with that screenshot included`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(0))
        session.onSwipeFinished() // TakeScreenshot is now in flight

        session.onStopRequested()
        val finish = session.onScreenshot(page(10)) as Command.Finish

        assertEquals(FinishReason.Stopped, finish.reason)
        assertEquals(2, finish.pages.size)
    }

    @Test
    fun `stop while scrolling takes one last screenshot after the swipe and then finishes`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(0)) // Swipe is now in flight

        session.onStopRequested()

        assertEquals(Command.TakeScreenshot, session.onSwipeFinished())
        val finish = session.onScreenshot(page(10)) as Command.Finish
        assertEquals(FinishReason.Stopped, finish.reason)
        assertEquals(listOf(0, 10), finish.pages.map { TestPages.rowIds(it).first() })
    }

    @Test
    fun `stop never leads to a further swipe even when the last page is a duplicate`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(0))
        session.onSwipeFinished()
        session.onStopRequested()

        val finish = session.onScreenshot(page(0)) as Command.Finish

        assertEquals(FinishReason.Stopped, finish.reason)
        assertEquals(1, finish.pages.size)
    }

    @Test
    fun `a failed screenshot is retried, and given up after the retries`() {
        val session = CaptureSession(maxRetries = 2)
        session.start()
        session.onScreenshot(page(0))
        session.onSwipeFinished()

        assertEquals(Command.TakeScreenshot, session.onScreenshotFailed())
        assertEquals(Command.TakeScreenshot, session.onScreenshotFailed())
        val finish = session.onScreenshotFailed() as Command.Finish

        assertEquals(FinishReason.CaptureFailed, finish.reason)
        assertEquals(1, finish.pages.size)
    }

    @Test
    fun `a success resets the retry count`() {
        val session = CaptureSession(maxRetries = 1)
        session.start()
        session.onScreenshotFailed()
        session.onScreenshot(page(0))
        session.onSwipeFinished()

        assertEquals(Command.TakeScreenshot, session.onScreenshotFailed())
    }

    @Test
    fun `a failed screenshot after stop finishes with what there is`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(0))
        session.onSwipeFinished()
        session.onStopRequested()

        val finish = session.onScreenshotFailed() as Command.Finish

        assertEquals(FinishReason.Stopped, finish.reason)
        assertEquals(1, finish.pages.size)
    }

    @Test
    fun `the page limit finishes the capture`() {
        val session = CaptureSession(maxPages = 2)
        session.start()
        session.onScreenshot(page(0))
        session.onSwipeFinished()

        val finish = session.onScreenshot(page(10)) as Command.Finish

        assertEquals(FinishReason.PageLimit, finish.reason)
        assertEquals(2, finish.pages.size)
    }

    @Test
    fun `the finish carries the very pages that were handed in`() {
        val session = CaptureSession()
        val first = page(0)
        session.start()
        session.onScreenshot(first)
        session.onSwipeFinished()
        session.onStopRequested()

        val finish = session.onScreenshot(page(0)) as Command.Finish

        assertSame(first, finish.pages.single())
    }

    @Test
    fun `nothing is accepted after the finish`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(0))
        session.onSwipeFinished()
        session.onStopRequested()
        session.onScreenshot(page(10))

        val thrown = runCatching { session.onSwipeFinished() }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }
}

class CaptureSessionNearDuplicateTest {

    private fun page(changedRows: Set<Int>) = TestPages.fromRowIds(
        (0 until 40).map { y -> if (y in changedRows) 5_000_000 + y else y },
    )

    @Test
    fun `a page that differs from the one before in a few rows only counts as the same page`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(emptySet()))
        session.onSwipeFinished()

        // Two rows of forty changed: a clock in the status bar, not a scroll.
        val finish = session.onScreenshot(page(setOf(2, 3))) as Command.Finish

        assertEquals(FinishReason.EndOfContent, finish.reason)
        assertEquals(1, finish.pages.size)
    }

    @Test
    fun `a page that differs in more rows is a new page`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(emptySet()))
        session.onSwipeFinished()

        assertEquals(Command.Swipe, session.onScreenshot(page(setOf(2, 3, 4))))
        assertEquals(2, session.pages.size)
    }
}

class CaptureSessionScrollEventTest {

    private fun page(scroll: Int) = TestPages.screenshot(height = 20, scroll = scroll)

    @Test
    fun `a swipe that scrolled nothing, in an app that did scroll before, ends the capture without another page`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(0))
        assertEquals(Command.TakeScreenshot, session.onSwipeFinished(scrolled = true))
        session.onScreenshot(page(10))

        val finish = session.onSwipeFinished(scrolled = false) as Command.Finish

        assertEquals(FinishReason.EndOfContent, finish.reason)
        assertEquals(2, finish.pages.size)
    }

    @Test
    fun `an app that never reports scrolling is judged by its pictures instead`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(0))

        assertEquals(Command.TakeScreenshot, session.onSwipeFinished(scrolled = false))
        session.onScreenshot(page(10))
        assertEquals(Command.TakeScreenshot, session.onSwipeFinished(scrolled = false))
    }

    @Test
    fun `a stop asked for before a swipe that scrolled nothing still finishes as stopped`() {
        val session = CaptureSession()
        session.start()
        session.onScreenshot(page(0))
        session.onSwipeFinished(scrolled = true)
        session.onScreenshot(page(10))
        session.onStopRequested()

        val finish = session.onSwipeFinished(scrolled = false) as Command.Finish

        assertEquals(FinishReason.Stopped, finish.reason)
        assertEquals(2, finish.pages.size)
    }
}
