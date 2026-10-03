package io.github.aiya000.screenshotdachshund.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.aiya000.screenshotdachshund.R

/**
 * The small bar the service floats over the app being captured: a start button and a
 * dismiss button before the capture, a stop button during it.
 *
 * It is plain views rather than Compose because it lives in a service window, where
 * there is no lifecycle owner for a composition to hang off.
 */
@SuppressLint("ViewConstructor")
class CaptureOverlay(
    context: Context,
    private val onStart: () -> Unit,
    private val onDismiss: () -> Unit,
    private val onStop: () -> Unit,
) : LinearLayout(context) {

    private val primary = pill(0xFFC98A4B.toInt())
    private val secondary = pill(0xFF5B5B5B.toInt())

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(primary, pillParams())
        addView(secondary, pillParams())
        showIdle()
    }

    fun showIdle() {
        primary.text = context.getString(R.string.overlay_start)
        primary.setOnClickListener { onStart() }
        secondary.text = context.getString(R.string.overlay_dismiss)
        secondary.setOnClickListener { onDismiss() }
        secondary.visibility = View.VISIBLE
        visibility = View.VISIBLE
    }

    fun showCapturing() {
        primary.text = context.getString(R.string.overlay_stop)
        primary.setOnClickListener { onStop() }
        secondary.visibility = View.GONE
        visibility = View.VISIBLE
    }

    fun showStopping() {
        primary.text = context.getString(R.string.overlay_stopping)
        primary.setOnClickListener(null)
        secondary.visibility = View.GONE
        visibility = View.VISIBLE
    }

    /**
     * Taken out of sight for a screenshot. Only the pixels go: the bar keeps its window
     * and its touches, so a tap that lands at this moment still stops the capture instead
     * of falling through to the app underneath and opening something there.
     */
    fun hide() {
        alpha = 0f
    }

    fun show() {
        alpha = 1f
    }

    private fun pill(color: Int): TextView = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        val pad = dp(14)
        setPadding(pad * 2, pad, pad * 2, pad)
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(24).toFloat()
        }
        isClickable = true
    }

    private fun pillParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
        marginStart = dp(8)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
