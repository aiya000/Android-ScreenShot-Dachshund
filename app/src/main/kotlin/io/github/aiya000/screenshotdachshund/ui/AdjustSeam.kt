package io.github.aiya000.screenshotdachshund.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.aiya000.screenshotdachshund.R
import io.github.aiya000.screenshotdachshund.join.Adjusting
import io.github.aiya000.screenshotdachshund.join.Cut
import io.github.aiya000.screenshotdachshund.join.CutModel
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Adjusting one seam, shown the way the saved image will look around it: the page above
 * drawn down to where it ends, the page below drawn from where it starts, meeting at the
 * line across the middle. The buttons above the picture move the upper edge, the buttons
 * below it the lower edge, by one or ten rows; dragging the upper or lower half of the
 * picture moves that edge with the finger.
 *
 * The same screen trims the start and the end of the image: then only one page is drawn,
 * under the line for the start and above it for the end, with the other half left blank
 * and only that side's buttons shown.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AdjustSeamScreen(
    capture: LoadedCapture,
    model: CutModel,
    adjusting: Adjusting,
    onMoveUpperEdge: (Int) -> Unit,
    onMoveLowerEdge: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val upperCut: Cut? = when (adjusting) {
        is Adjusting.Seam -> model.cuts[adjusting.seam]
        Adjusting.End -> model.cuts.last()
        Adjusting.Start -> null
    }
    val lowerCut: Cut? = when (adjusting) {
        is Adjusting.Seam -> model.cuts[adjusting.seam + 1]
        Adjusting.Start -> model.cuts.first()
        Adjusting.End -> null
    }
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (adjusting) {
                            is Adjusting.Seam -> stringResource(R.string.adjust_title, adjusting.seam + 1)
                            Adjusting.Start -> stringResource(R.string.trim_start_title)
                            Adjusting.End -> stringResource(R.string.trim_end_title)
                        },
                    )
                },
                actions = {
                    TextButton(onClick = onDone, modifier = Modifier.testTag("adjust-done")) {
                        Text(stringResource(R.string.adjust_done))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (upperCut != null) {
                EdgeButtons(
                    label = stringResource(if (adjusting is Adjusting.End) R.string.trim_end_edge else R.string.adjust_upper_edge),
                    tag = "upper",
                    onMove = onMoveUpperEdge,
                )
            }
            SeamPicture(
                capture = capture,
                upperCut = upperCut,
                lowerCut = lowerCut,
                key = adjusting,
                onMoveUpperEdge = onMoveUpperEdge,
                onMoveLowerEdge = onMoveLowerEdge,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            if (lowerCut != null) {
                EdgeButtons(
                    label = stringResource(if (adjusting is Adjusting.Start) R.string.trim_start_edge else R.string.adjust_lower_edge),
                    tag = "lower",
                    onMove = onMoveLowerEdge,
                )
            }
        }
    }
}

/** ▲1 ▲10 ▼1 ▼10 for one edge. The `-down` tag is the ten-row step, which the device test presses. */
@Composable
private fun EdgeButtons(label: String, tag: String, onMove: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StepButton(tag = "$tag-up1", text = stringResource(R.string.adjust_up, 1), modifier = Modifier.weight(1f)) { onMove(-1) }
            StepButton(tag = "$tag-up", text = stringResource(R.string.adjust_up, 10), modifier = Modifier.weight(1f)) { onMove(-10) }
            StepButton(tag = "$tag-down1", text = stringResource(R.string.adjust_down, 1), modifier = Modifier.weight(1f)) { onMove(1) }
            StepButton(tag = "$tag-down", text = stringResource(R.string.adjust_down, 10), modifier = Modifier.weight(1f)) { onMove(10) }
        }
    }
}

@Composable
private fun StepButton(tag: String, text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.testTag(tag)) {
        Text(text)
    }
}

/**
 * The seam as it will be: the upper page's rows up to its edge end at the middle line,
 * the lower page's rows from its edge start right under it. What is above the line is
 * the last of the page above, what is below it the first of the page below. A side
 * without a cut -- above the start of the image, below its end -- stays blank, and a
 * drag anywhere on the picture then moves the one edge there is.
 */
@Composable
private fun SeamPicture(
    capture: LoadedCapture,
    upperCut: Cut?,
    lowerCut: Cut?,
    key: Any,
    onMoveUpperEdge: (Int) -> Unit,
    onMoveLowerEdge: (Int) -> Unit,
    modifier: Modifier,
) {
    val upper = upperCut?.let { capture.previews[it.pageIndex] }
    val lower = lowerCut?.let { capture.previews[it.pageIndex] }
    val upperImage = upper?.asImageBitmap()
    val lowerImage = lower?.asImageBitmap()
    // every page is as wide as the screen, so any preview says how the picture is scaled
    val previewWidth = capture.previews.first().width
    val seamColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .testTag("seam-picture")
            .pointerInput(key) {
                // Which edge a drag moves is decided where the finger lands, and kept for the
                // whole drag: a finger that crosses the middle line goes on moving the same edge
                var upperHalf = true
                detectVerticalDragGestures(
                    onDragStart = { start ->
                        upperHalf = when {
                            upperCut == null -> false
                            lowerCut == null -> true
                            else -> start.y < size.height / 2f
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    // The picture is drawn at this many page rows per pixel on screen
                    val rowsPerPixel = previewWidth.toFloat() * capture.sample / size.width
                    // The content follows the finger, as a scroll does: dragging up slides the
                    // page up, which shows rows further down it, so the edge moves down the page
                    val rows = (-dragAmount * rowsPerPixel).roundToInt()
                    if (upperHalf) onMoveUpperEdge(rows) else onMoveLowerEdge(rows)
                }
            },
    ) {
        val scale = size.width / previewWidth
        val seamY = size.height / 2f
        val visibleRows = ceil(seamY / scale).toInt() + 1

        // the page above, ending at the line
        if (upperCut != null && upper != null && upperImage != null) {
            val upperEdge = upperCut.toRow / capture.sample
            val upperTop = (upperEdge - visibleRows).coerceAtLeast(0)
            if (upperEdge > upperTop) {
                val rows = upperEdge - upperTop
                drawImage(
                    image = upperImage,
                    srcOffset = IntOffset(0, upperTop),
                    srcSize = IntSize(upper.width, rows),
                    dstOffset = IntOffset(0, (seamY - rows * scale).roundToInt()),
                    dstSize = IntSize(size.width.roundToInt(), (rows * scale).roundToInt()),
                )
            }
        }

        // the page below, starting at the line
        if (lowerCut != null && lower != null && lowerImage != null) {
            val lowerEdge = lowerCut.fromRow / capture.sample
            val lowerBottom = (lowerEdge + visibleRows).coerceAtMost(lower.height)
            if (lowerBottom > lowerEdge) {
                val rows = lowerBottom - lowerEdge
                drawImage(
                    image = lowerImage,
                    srcOffset = IntOffset(0, lowerEdge),
                    srcSize = IntSize(lower.width, rows),
                    dstOffset = IntOffset(0, seamY.roundToInt()),
                    dstSize = IntSize(size.width.roundToInt(), (rows * scale).roundToInt()),
                )
            }
        }

        // A hairline, so that a seam can be judged to the row; the short bars at the ends
        // make it easy to spot without covering anything in the middle
        drawLine(
            color = seamColor,
            start = Offset(0f, seamY),
            end = Offset(size.width, seamY),
            strokeWidth = 1f,
        )
        val bar = 10.dp.toPx()
        for (x in listOf(0f, size.width - bar)) {
            drawLine(
                color = seamColor,
                start = Offset(x, seamY),
                end = Offset(x + bar, seamY),
                strokeWidth = 3.dp.toPx(),
            )
        }
    }
}
