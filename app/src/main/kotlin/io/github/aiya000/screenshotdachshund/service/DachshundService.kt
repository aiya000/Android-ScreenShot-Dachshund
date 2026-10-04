package io.github.aiya000.screenshotdachshund.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import io.github.aiya000.screenshotdachshund.R
import io.github.aiya000.screenshotdachshund.capture.CaptureSession
import io.github.aiya000.screenshotdachshund.capture.Command
import io.github.aiya000.screenshotdachshund.capture.FinishReason
import io.github.aiya000.screenshotdachshund.image.StoredPage
import io.github.aiya000.screenshotdachshund.storage.CaptureFiles
import io.github.aiya000.screenshotdachshund.ui.EditActivity
import java.util.concurrent.Executors

/**
 * The capture itself, as an accessibility service.
 *
 * It is one because the two things a scrolling screenshot needs are both abilities only
 * an accessibility service has: [takeScreenshot], which asks for no consent dialog, and
 * [dispatchGesture], which scrolls the app underneath. What to do when is decided by
 * [CaptureSession]; this class only carries its commands out and reports back.
 *
 * The service reads nothing from the screen: it listens for scroll events only, and
 * cannot retrieve window content.
 */
class DachshundService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Screenshots are encoded and written on this thread, off the main one. */
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var windowManager: WindowManager
    private var overlay: CaptureOverlay? = null

    private var session: CaptureSession? = null
    private var files: CaptureFiles? = null
    private var pageCount = 0

    /** Set by the scroll event the swipe causes; its absence, once seen, is the end of the page. */
    private var scrolledSinceSwipe = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WindowManager::class.java)
        instance = this
        Log.d(TAG, "service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "service unbound")
        instance = null
        abandon()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "service destroyed")
        instance = null
        abandon()
        worker.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) scrolledSinceSwipe = true
    }

    override fun onInterrupt() = Unit

    // ---- entry points ----

    /** Floats the start button over whatever is on screen. A no-op while a capture runs. */
    fun showStartButton() {
        mainHandler.post {
            if (session != null) return@post
            ensureOverlay().showIdle()
        }
    }

    // ---- the overlay ----

    private fun ensureOverlay(): CaptureOverlay {
        overlay?.let { return it }
        val view = CaptureOverlay(
            context = this,
            onStart = ::beginCapture,
            onDismiss = ::removeOverlay,
            onStop = ::requestStop,
        )
        windowManager.addView(view, overlayLayoutParams())
        overlay = view
        return view
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
    }

    private fun overlayLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            // Only the bar itself takes touches; the app underneath keeps the rest.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        val margin = (16 * resources.displayMetrics.density).toInt()
        x = margin
        y = margin * 6
        title = "ScreenShot Dachshund"
    }

    // ---- the capture ----

    private fun beginCapture() {
        if (session != null) return
        val session = CaptureSession()
        this.session = session
        files = CaptureFiles.create(this).also { recordSystemBars(it) }
        pageCount = 0
        overlay?.showCapturing()
        perform(session.start())
    }

    /**
     * Writes down how tall the status bar and the navigation bar are, for the join: they
     * are in every screenshot, and the gesture bar is translucent, so the pixels alone
     * would not tell that they are not part of the page.
     */
    private fun recordSystemBars(files: CaptureFiles) {
        val insets = windowManager.currentWindowMetrics.windowInsets
            .getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        runCatching { files.writeInsets(insets.top, insets.bottom) }
            .onFailure { Log.w(TAG, "insets not recorded", it) }
    }

    private fun requestStop() {
        Log.d(TAG, "stop requested")
        session?.onStopRequested()
        overlay?.showStopping()
    }

    private fun perform(command: Command) {
        when (command) {
            Command.TakeScreenshot -> takePage()
            Command.Swipe -> swipe()
            is Command.Finish -> finish(command)
        }
    }

    private fun takePage() {
        // The bar must not be in the picture; a frame or two for it to go.
        overlay?.hide()
        mainHandler.postDelayed({ takeScreenshotNow() }, HIDE_SETTLE_MS)
    }

    private fun takeScreenshotNow() {
        val session = session ?: return
        val files = files ?: return
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            worker,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    // The buffer is hardware backed and has to be released; reading pixels
                    // needs a software bitmap, hence the copy.
                    val bitmap = screenshot.hardwareBuffer.use { buffer ->
                        Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    }
                    if (bitmap == null) {
                        Log.w(TAG, "screenshot could not be copied")
                        mainHandler.post { perform(session.onScreenshotFailed()) }
                        return
                    }
                    val page = runCatching { StoredPage.store(bitmap, files.pageFile(pageCount)) }
                    bitmap.recycle()
                    mainHandler.post {
                        page.onSuccess {
                            pageCount++
                            perform(session.onScreenshot(it))
                        }.onFailure {
                            Log.w(TAG, "page could not be stored", it)
                            perform(session.onScreenshotFailed())
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot failed: $errorCode")
                    mainHandler.post { perform(session.onScreenshotFailed()) }
                }
            },
        )
    }

    private fun swipe() {
        overlay?.show()
        scrolledSinceSwipe = false

        val bounds = windowManager.currentWindowMetrics.bounds
        val x = bounds.exactCenterX()
        val from = bounds.height() * SWIPE_FROM
        val to = bounds.height() * SWIPE_TO

        // A plain drag lets go at full speed and the list keeps flinging, by an amount
        // nothing can predict. So the finger slides, then rests for a moment before it
        // lifts: the release velocity is nothing, and the page stops where the drag ended.
        val drag = GestureDescription.StrokeDescription(
            Path().apply { moveTo(x, from); lineTo(x, to) },
            0,
            SWIPE_MS,
            true,
        )
        val rest = drag.continueStroke(
            Path().apply { moveTo(x, to); lineTo(x, to + 1f) },
            SWIPE_MS,
            REST_MS,
            false,
        )
        val gesture = GestureDescription.Builder().addStroke(drag).addStroke(rest).build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = afterSwipe()
                override fun onCancelled(gestureDescription: GestureDescription?) = afterSwipe()
            },
            mainHandler,
        )
        if (!dispatched) afterSwipe()
    }

    private fun afterSwipe() {
        // Give the list time to settle before the next picture.
        mainHandler.postDelayed({
            Log.d(TAG, "swipe done, scrolled=$scrolledSinceSwipe")
            session?.let { perform(it.onSwipeFinished(scrolled = scrolledSinceSwipe)) }
        }, SETTLE_MS)
    }

    private fun finish(finish: Command.Finish) {
        val files = files ?: return
        this.session = null
        this.files = null
        Log.d(TAG, "finished: ${finish.reason}, ${finish.pages.size} pages, ${files.dir.name}")

        // Pages the session dropped (a duplicate at the end) are still on disk.
        val kept = finish.pages.mapNotNull { (it as? StoredPage)?.file }.toSet()
        files.pageFiles().filter { it !in kept }.forEach { it.delete() }

        if (finish.pages.isEmpty()) {
            removeOverlay()
            files.delete()
            toast(
                if (finish.reason == FinishReason.CaptureFailed) getString(R.string.capture_failed)
                else getString(R.string.capture_nothing),
            )
            return
        }

        // The overlay is still up while the activity starts: a visible window of this
        // app is one of the things that lets a service start an activity at all.
        //
        // An edit screen may still be open from the capture before: with NEW_TASK alone
        // the system would only bring that task back as it was, and this capture would
        // never be shown. CLEAR_TOP and SINGLE_TOP hand the running screen this intent
        // instead (EditActivity.onNewIntent), so it switches to the new capture.
        startActivity(
            EditActivity.intent(this, files).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            ),
        )
        removeOverlay()
    }

    /** Drops whatever capture is running, for when the service goes away. */
    private fun abandon() {
        mainHandler.removeCallbacksAndMessages(null)
        removeOverlay()
        session = null
        files?.delete()
        files = null
    }

    private fun toast(message: String) {
        mainHandler.post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val TAG = "Dachshund"

        /** How long the bar is given to disappear before a screenshot. */
        private const val HIDE_SETTLE_MS = 150L

        /**
         * The swipe, as fractions of the screen height, and its timing. It is kept short:
         * a tap on the bar while a gesture is being dispatched is handed to the app
         * underneath along with the gesture, so the less of each cycle the swipe takes,
         * the less often a stop goes astray.
         */
        private const val SWIPE_FROM = 0.72f
        private const val SWIPE_TO = 0.28f
        private const val SWIPE_MS = 400L
        private const val REST_MS = 120L

        /** How long the page is given to settle after the swipe, overscroll stretch included. */
        private const val SETTLE_MS = 1000L

        /** The running service, so that the app and the tile can reach it. */
        @Volatile
        var instance: DachshundService? = null
            private set

        /** Whether the user has switched the service on in the accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            val self = ComponentName(context, DachshundService::class.java)
            return EnabledServices.lists(enabled, self.flattenToString())
        }
    }
}
