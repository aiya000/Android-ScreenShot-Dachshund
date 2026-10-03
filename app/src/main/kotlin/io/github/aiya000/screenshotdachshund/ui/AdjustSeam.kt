package io.github.aiya000.screenshotdachshund.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.aiya000.screenshotdachshund.R
import io.github.aiya000.screenshotdachshund.join.CutModel
import kotlin.math.roundToInt

/** How many rows a tap on the fine buttons moves an edge. */
private const val STEP_ROWS = 10

/** A thin bar drawn at a seam of the joined image, with the way into adjusting it. */
@Composable
fun SeamBar(seam: Int, onAdjust: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = { onAdjust(seam) },
            modifier = Modifier.testTag("adjust-$seam"),
        ) {
            Text(stringResource(R.string.adjust_seam))
        }
    }
}

/**
 * Adjusting one seam: the page above it around the row where it stops, and the page below
 * it around the row where it starts, each with the edge drawn across it. Dragging a picture
 * moves its edge with the finger; the buttons move it by a fixed number of rows.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AdjustSeamDialog(
    capture: LoadedCapture,
    model: CutModel,
    seam: Int,
    onMoveUpperEdge: (Int) -> Unit,
    onMoveLowerEdge: (Int) -> Unit,
    onDone: () -> Unit,
) {
    val upperCut = model.cuts[seam]
    val lowerCut = model.cuts[seam + 1]
    AlertDialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            // A dialog is a window of its own, so the device tests need this here too
            .semantics { testTagsAsResourceId = true },
        title = { Text(stringResource(R.string.adjust_title, seam + 1)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EdgeEditor(
                    label = stringResource(R.string.adjust_upper_edge),
                    tag = "upper",
                    capture = capture,
                    pageIndex = upperCut.pageIndex,
                    edgeRow = upperCut.toRow,
                    onMove = onMoveUpperEdge,
                )
                EdgeEditor(
                    label = stringResource(R.string.adjust_lower_edge),
                    tag = "lower",
                    capture = capture,
                    pageIndex = lowerCut.pageIndex,
                    edgeRow = lowerCut.fromRow,
                    onMove = onMoveLowerEdge,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDone, modifier = Modifier.testTag("adjust-done")) {
                Text(stringResource(R.string.adjust_done))
            }
        },
    )
}

@Composable
private fun EdgeEditor(
    label: String,
    tag: String,
    capture: LoadedCapture,
    pageIndex: Int,
    edgeRow: Int,
    onMove: (Int) -> Unit,
) {
    val preview = capture.previews[pageIndex]
    val image = preview.asImageBitmap()
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .testTag("edge-$tag")
                .pointerInput(pageIndex) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        // The picture is drawn at this many page rows per pixel on screen
                        val rowsPerPixel = preview.width.toFloat() * capture.sample / size.width
                        onMove((dragAmount * rowsPerPixel).roundToInt())
                    }
                },
        ) {
            val scale = size.width / preview.width
            val edgeY = size.height / 2f
            // The preview row of the edge sits at the middle of the picture
            val edgePreviewRow = edgeRow / capture.sample
            val topPreviewRow = edgePreviewRow - (edgeY / scale).roundToInt()
            val visibleRows = (size.height / scale).roundToInt() + 1
            val srcTop = topPreviewRow.coerceIn(0, maxOf(0, preview.height - 1))
            val srcBottom = (topPreviewRow + visibleRows).coerceIn(srcTop, preview.height)
            val dstTop = (srcTop - topPreviewRow) * scale
            clipRect {
                drawImage(
                    image = image,
                    srcOffset = IntOffset(0, srcTop),
                    srcSize = IntSize(preview.width, srcBottom - srcTop),
                    dstOffset = IntOffset(0, dstTop.roundToInt()),
                    dstSize = IntSize(size.width.roundToInt(), ((srcBottom - srcTop) * scale).roundToInt()),
                )
            }
            drawLine(
                color = Color(0xFFE53935),
                start = Offset(0f, edgeY),
                end = Offset(size.width, edgeY),
                strokeWidth = 3.dp.toPx(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            Button(onClick = { onMove(-STEP_ROWS) }, modifier = Modifier.testTag("$tag-up")) {
                Text(stringResource(R.string.adjust_up, STEP_ROWS))
            }
            Button(onClick = { onMove(STEP_ROWS) }, modifier = Modifier.testTag("$tag-down")) {
                Text(stringResource(R.string.adjust_down, STEP_ROWS))
            }
        }
    }
}
