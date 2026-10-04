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
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import io.github.aiya000.screenshotdachshund.join.CutModel
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Adjusting one seam, shown the way the saved image will look around it: the page above
 * drawn down to where it ends, the page below drawn from where it starts, meeting at the
 * line across the middle. The buttons above the picture move the upper edge, the buttons
 * below it the lower edge, by one or ten rows; dragging the upper or lower half of the
 * picture moves that edge with the finger.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AdjustSeamScreen(
    capture: LoadedCapture,
    model: CutModel,
    seam: Int,
    onMoveUpperEdge: (Int) -> Unit,
    onMoveLowerEdge: (Int) -> Unit,
    onDone: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adjust_title, seam + 1)) },
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
            EdgeButtons(
                label = stringResource(R.string.adjust_upper_edge),
                tag = "upper",
                onMove = onMoveUpperEdge,
            )
            SeamPicture(
                capture = capture,
                model = model,
                seam = seam,
                onMoveUpperEdge = onMoveUpperEdge,
                onMoveLowerEdge = onMoveLowerEdge,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            EdgeButtons(
                label = stringResource(R.string.adjust_lower_edge),
                tag = "lower",
                onMove = onMoveLowerEdge,
            )
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
 * the last of the page above, what is below it the first of the page below.
 */
@Composable
private fun SeamPicture(
    capture: LoadedCapture,
    model: CutModel,
    seam: Int,
    onMoveUpperEdge: (Int) -> Unit,
    onMoveLowerEdge: (Int) -> Unit,
    modifier: Modifier,
) {
    val upperCut = model.cuts[seam]
    val lowerCut = model.cuts[seam + 1]
    val upper = capture.previews[upperCut.pageIndex]
    val lower = capture.previews[lowerCut.pageIndex]
    val upperImage = upper.asImageBitmap()
    val lowerImage = lower.asImageBitmap()
    val seamColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .testTag("seam-picture")
            .pointerInput(seam) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    // The picture is drawn at this many page rows per pixel on screen
                    val rowsPerPixel = upper.width.toFloat() * capture.sample / size.width
                    // The content follows the finger, as a scroll does: dragging up slides the
                    // page up, which shows rows further down it, so the edge moves down the page
                    val rows = (-dragAmount * rowsPerPixel).roundToInt()
                    if (change.position.y < size.height / 2f) onMoveUpperEdge(rows) else onMoveLowerEdge(rows)
                }
            },
    ) {
        val scale = size.width / upper.width
        val seamY = size.height / 2f
        val visibleRows = ceil(seamY / scale).toInt() + 1

        // the page above, ending at the line
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

        // the page below, starting at the line
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

        drawLine(
            color = seamColor,
            start = Offset(0f, seamY),
            end = Offset(size.width, seamY),
            strokeWidth = 2.dp.toPx(),
        )
        // a faint hint on the line's ends, so it reads as the seam even over a dark page
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = Offset(0f, seamY + 2.dp.toPx()),
            end = Offset(size.width, seamY + 2.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
        )
    }
}
