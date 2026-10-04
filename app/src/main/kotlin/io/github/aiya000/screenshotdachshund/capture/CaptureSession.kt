package io.github.aiya000.screenshotdachshund.capture

import io.github.aiya000.screenshotdachshund.image.PixelRows
import io.github.aiya000.screenshotdachshund.image.differingRows

/** What the service is asked to do next. */
sealed interface Command {
    /** Take one screenshot and report it with [CaptureSession.onScreenshot] or [CaptureSession.onScreenshotFailed]. */
    data object TakeScreenshot : Command

    /** Scroll the page once, wait for it to settle, then report with [CaptureSession.onSwipeFinished]. */
    data object Swipe : Command

    /** The capture is over; these are its pages, in order. */
    data class Finish(val pages: List<PixelRows>, val reason: FinishReason) : Command
}

enum class FinishReason {
    /** The user tapped stop. */
    Stopped,

    /** A screenshot came back identical to the one before: the page scrolls no further. */
    EndOfContent,

    /** As many pages as one capture may hold. */
    PageLimit,

    /** A screenshot failed more times in a row than is worth retrying. */
    CaptureFailed,
}

/**
 * The capture, as a state machine with no Android in it.
 *
 * The service feeds in what happened (a screenshot arrived, a swipe finished, the user
 * tapped stop) and carries out the one [Command] that comes back. Exactly one thing is
 * ever in flight: a screenshot or a swipe, never both. This is the part LongShot got
 * wrong -- its stop button took a screenshot while the periodic one was still running,
 * and the second of the two came back empty and took the whole capture down with it.
 * Here a stop is only remembered, and takes effect at the next safe point.
 */
class CaptureSession(
    private val maxPages: Int = 30,
    private val maxRetries: Int = 3,
) {

    private companion object {
        /** Up to this fraction of the rows may differ for two screenshots to be the same page. */
        const val SAME_PAGE_FRACTION = 20
    }

    private enum class State { Idle, Capturing, Scrolling, Finished }

    private var state = State.Idle
    private var stopRequested = false
    private var retries = 0
    private val collected = mutableListOf<PixelRows>()

    /** Whether any swipe so far was reported as having scrolled the app. */
    private var everScrolled = false

    /** The pages kept so far, in order. */
    val pages: List<PixelRows> get() = collected

    fun start(): Command {
        check(state == State.Idle) { "already started" }
        state = State.Capturing
        return Command.TakeScreenshot
    }

    fun onScreenshot(page: PixelRows): Command {
        check(state == State.Capturing) { "no screenshot was asked for in $state" }
        retries = 0
        val duplicate = collected.lastOrNull()?.let { isSamePage(it, page) } == true
        if (!duplicate) collected += page

        return when {
            stopRequested -> finish(FinishReason.Stopped)
            duplicate -> finish(FinishReason.EndOfContent)
            collected.size >= maxPages -> finish(FinishReason.PageLimit)
            else -> {
                state = State.Scrolling
                Command.Swipe
            }
        }
    }

    fun onScreenshotFailed(): Command {
        check(state == State.Capturing) { "no screenshot was asked for in $state" }
        if (stopRequested) return finish(FinishReason.Stopped)
        retries++
        if (retries > maxRetries) return finish(FinishReason.CaptureFailed)
        return Command.TakeScreenshot
    }

    /**
     * The swipe is done and the page has settled. [scrolled] says whether the app reported
     * a scroll for it. Once an app has reported scrolling, a swipe that scrolls nothing is
     * the end of the page, and the capture ends there without another screenshot: at the
     * end of a list Android stretches the content for a moment, and a page taken then
     * fits nowhere. An app that never reports scrolling is judged by its pictures instead.
     * A stop asked for meanwhile still gets its last screenshot.
     */
    fun onSwipeFinished(scrolled: Boolean = true): Command {
        check(state == State.Scrolling) { "no swipe was asked for in $state" }
        if (!scrolled && everScrolled) {
            return finish(if (stopRequested) FinishReason.Stopped else FinishReason.EndOfContent)
        }
        if (scrolled) everScrolled = true
        state = State.Capturing
        return Command.TakeScreenshot
    }

    /**
     * Remembers that the user wants to stop. Nothing happens right away: the screenshot
     * or swipe in flight is allowed to end, and the capture finishes with the next
     * screenshot, so that what was on screen when stop was tapped is the last page.
     */
    fun onStopRequested() {
        stopRequested = true
    }

    /**
     * Whether [page] shows the same thing as [previous]. Not pixel for pixel: the clock in
     * the status bar moves on, a spinner turns, and the page is still the same page. A
     * scroll, however small, changes every content row, so a handful of rows is the line.
     */
    private fun isSamePage(previous: PixelRows, page: PixelRows): Boolean =
        previous.differingRows(page) <= page.height / SAME_PAGE_FRACTION

    private fun finish(reason: FinishReason): Command.Finish {
        state = State.Finished
        return Command.Finish(collected.toList(), reason)
    }
}
