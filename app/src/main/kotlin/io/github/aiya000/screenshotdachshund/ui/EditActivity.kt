package io.github.aiya000.screenshotdachshund.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.aiya000.screenshotdachshund.R
import io.github.aiya000.screenshotdachshund.join.Adjusting
import io.github.aiya000.screenshotdachshund.join.CutModel
import io.github.aiya000.screenshotdachshund.join.PreviewLayout
import io.github.aiya000.screenshotdachshund.join.moveLowerEdge
import io.github.aiya000.screenshotdachshund.join.moveUpperEdge
import io.github.aiya000.screenshotdachshund.join.rejoinSeam
import io.github.aiya000.screenshotdachshund.join.removeCut
import io.github.aiya000.screenshotdachshund.storage.CaptureFiles
import io.github.aiya000.screenshotdachshund.storage.SavedImages
import kotlin.concurrent.thread

/**
 * The edit screen: the joined capture, with its seams to adjust, and a button to save it into
 * the shared pictures.
 *
 * The capture is kept as pages and cuts, not as one picture, so a seam can be moved and the
 * result drawn again at once; the joined PNG is only written when the user saves.
 */
class EditActivity : ComponentActivity() {

    private sealed interface State {
        data object Loading : State

        data class Ready(
            val capture: LoadedCapture,
            val model: CutModel,
            /** The seam, or end of the image, being adjusted, or null. */
            val adjusting: Adjusting?,
            /** The cut whose page the user is being asked whether to delete, or null. */
            val deleting: Int?,
            val savedAs: String?,
            val saving: Boolean,
        ) : State

        data class Failed(val message: String) : State
    }

    private var state by mutableStateOf<State>(State.Loading)
    private lateinit var files: CaptureFiles

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dir = intent.getStringExtra(EXTRA_DIR)
        if (dir == null) {
            finish()
            return
        }
        files = CaptureFiles.open(dir)

        setContent {
            MaterialTheme {
                EditScreen(
                    state = state,
                    onSave = ::save,
                    onAdjust = { target -> update { it.copy(adjusting = target) } },
                    onAdjustDone = { update { it.copy(adjusting = null) } },
                    onMoveUpperEdge = { target, rows -> update { it.copy(model = it.model.moveUpperEdge(target, rows)) } },
                    onMoveLowerEdge = { target, rows -> update { it.copy(model = it.model.moveLowerEdge(target, rows)) } },
                    onDelete = { cut -> update { it.copy(deleting = cut) } },
                    onDeleteCancelled = { update { it.copy(deleting = null) } },
                    onDeleteConfirmed = ::deleteConfirmed,
                )
            }
        }

        thread(name = "load") {
            val result = runCatching { Joining.load(files) }
            runOnUiThread {
                state = result.fold(
                    onSuccess = { State.Ready(it, it.model, adjusting = null, deleting = null, savedAs = null, saving = false) },
                    onFailure = {
                        Log.w(TAG, "load failed", it)
                        State.Failed(getString(R.string.join_failed))
                    },
                )
            }
        }
    }

    /**
     * Drops the page the user confirmed. The two pages that become neighbours are joined
     * afresh from their own overlap, since the cut between them was worked out against the
     * page that is now gone.
     */
    private fun deleteConfirmed() {
        update { ready ->
            val index = ready.deleting ?: return@update ready
            if (ready.model.cuts.size < 2) return@update ready.copy(deleting = null)
            var model = ready.model.removeCut(index)
            if (index in 1 until model.cuts.size) {
                model = model.rejoinSeam(index - 1, ready.capture.pages, ready.capture.edges)
            }
            ready.copy(model = model, deleting = null)
        }
    }

    private fun update(change: (State.Ready) -> State.Ready) {
        val ready = state as? State.Ready ?: return
        state = change(ready)
    }

    private fun save() {
        val ready = state as? State.Ready ?: return
        if (ready.saving) return
        state = ready.copy(saving = true)
        val model = ready.model
        thread(name = "save") {
            val name = runCatching { Joining.write(files, ready.capture.pages, model) }
                .onFailure { Log.w(TAG, "join failed", it) }
                .getOrNull()
                ?.let { SavedImages.save(this, it) }
            runOnUiThread {
                update { it.copy(saving = false, savedAs = name ?: it.savedAs) }
                Toast.makeText(
                    this,
                    if (name == null) getString(R.string.save_failed) else getString(R.string.saved_to, name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    @Composable
    private fun EditScreen(
        state: State,
        onSave: () -> Unit,
        onAdjust: (Adjusting) -> Unit,
        onAdjustDone: () -> Unit,
        onMoveUpperEdge: (Adjusting, Int) -> Unit,
        onMoveLowerEdge: (Adjusting, Int) -> Unit,
        onDelete: (Int) -> Unit,
        onDeleteCancelled: () -> Unit,
        onDeleteConfirmed: () -> Unit,
    ) {
        if (state is State.Ready && state.adjusting != null) {
            val target = state.adjusting
            AdjustSeamScreen(
                capture = state.capture,
                model = state.model,
                adjusting = target,
                onMoveUpperEdge = { rows -> onMoveUpperEdge(target, rows) },
                onMoveLowerEdge = { rows -> onMoveLowerEdge(target, rows) },
                onDone = onAdjustDone,
            )
            return
        }
        Scaffold(
            // testTagsAsResourceId: the device tests reach the buttons by these tags
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            topBar = { TopAppBar(title = { Text(stringResource(R.string.edit_title)) }) },
            bottomBar = {
                if (state is State.Ready) {
                    Button(
                        onClick = onSave,
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("save"),
                    ) {
                        Text(
                            when {
                                state.saving -> stringResource(R.string.saving)
                                state.savedAs != null -> stringResource(R.string.save_again)
                                else -> stringResource(R.string.save)
                            },
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                when (state) {
                    State.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.joining),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }

                    is State.Failed -> Text(state.message, modifier = Modifier.padding(24.dp))

                    is State.Ready -> {
                        JoinedPreview(
                            capture = state.capture,
                            model = state.model,
                            onAdjust = onAdjust,
                            onDelete = onDelete,
                            canDelete = state.model.cuts.size > 1,
                        )
                        val deleting = state.deleting
                        if (deleting != null) {
                            DeletePageDialog(
                                pageNumber = deleting + 1,
                                onCancel = onDeleteCancelled,
                                onConfirm = onDeleteConfirmed,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * The joined image exactly as it will be saved -- the cuts stacked with nothing between
     * them -- and, beside it, an Adjust button level with each seam, a Delete button level
     * with each page, and at the very top and the very bottom a button to trim where the
     * image starts and where it ends. A thin line across the image marks each seam.
     */
    @Composable
    private fun JoinedPreview(
        capture: LoadedCapture,
        model: CutModel,
        onAdjust: (Adjusting) -> Unit,
        onDelete: (Int) -> Unit,
        canDelete: Boolean,
    ) {
        val density = LocalDensity.current
        val controlsWidth = 104.dp
        val buttonHeight = 40.dp
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("joined"),
        ) {
            val imageWidth = maxWidth - controlsWidth
            val previewWidth = capture.previews.first().width
            val scale = with(density) { imageWidth.toPx() } / previewWidth
            val layout = PreviewLayout.of(model, capture.sample, scale)
            val totalHeight = with(density) { layout.totalHeight.toDp() }
            val buttonHeightPx = with(density) { buttonHeight.roundToPx() }
            val seamColor = MaterialTheme.colorScheme.primary

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(imageWidth)
                        .height(totalHeight),
                ) {
                    for (segment in layout.segments) {
                        if (segment.previewRows <= 0) continue
                        val preview = capture.previews[model.cuts[segment.cutIndex].pageIndex]
                        Image(
                            painter = BitmapPainter(
                                preview.asImageBitmap(),
                                srcOffset = IntOffset(0, segment.previewTop),
                                srcSize = IntSize(preview.width, segment.previewRows),
                            ),
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .offset { IntOffset(0, segment.top) }
                                .width(imageWidth)
                                .height(with(density) { segment.height.toDp() }),
                        )
                    }
                    Canvas(modifier = Modifier.matchParentSize()) {
                        // a hairline: the seam is marked, not covered
                        for (y in layout.seamTops) {
                            drawLine(
                                color = seamColor,
                                start = Offset(0f, y.toFloat()),
                                end = Offset(size.width, y.toFloat()),
                                strokeWidth = 1f,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .width(controlsWidth)
                        .height(totalHeight),
                ) {
                    if (canDelete) {
                        for (segment in layout.segments) {
                            val y = (segment.centre - buttonHeightPx / 2).coerceIn(0, maxOf(0, layout.totalHeight - buttonHeightPx))
                            TextButton(
                                onClick = { onDelete(segment.cutIndex) },
                                modifier = Modifier
                                    .offset { IntOffset(0, y) }
                                    .width(controlsWidth)
                                    .height(buttonHeight)
                                    .testTag("delete-${segment.cutIndex}"),
                            ) {
                                Text(stringResource(R.string.delete_page))
                            }
                        }
                    }
                    layout.seamTops.forEachIndexed { seam, seamTop ->
                        val y = (seamTop - buttonHeightPx / 2).coerceIn(0, maxOf(0, layout.totalHeight - buttonHeightPx))
                        OutlinedButton(
                            onClick = { onAdjust(Adjusting.Seam(seam)) },
                            modifier = Modifier
                                .offset { IntOffset(0, y) }
                                .width(controlsWidth)
                                .height(buttonHeight)
                                .testTag("adjust-$seam"),
                        ) {
                            Text(stringResource(R.string.adjust_seam))
                        }
                    }
                    // The ends of the image: level with its top and with its bottom. Their
                    // labels are two words, so the button's own padding is tightened to fit them
                    val trimPadding = PaddingValues(horizontal = 8.dp)
                    OutlinedButton(
                        onClick = { onAdjust(Adjusting.Start) },
                        contentPadding = trimPadding,
                        modifier = Modifier
                            .width(controlsWidth)
                            .height(buttonHeight)
                            .testTag("trim-start"),
                    ) {
                        Text(stringResource(R.string.trim_start), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onAdjust(Adjusting.End) },
                        contentPadding = trimPadding,
                        modifier = Modifier
                            .offset { IntOffset(0, maxOf(0, layout.totalHeight - buttonHeightPx)) }
                            .width(controlsWidth)
                            .height(buttonHeight)
                            .testTag("trim-end"),
                    ) {
                        Text(stringResource(R.string.trim_end), maxLines = 1)
                    }
                }
            }
        }
    }

    /** The question before a page goes: nothing happens until the user says yes. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun DeletePageDialog(pageNumber: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
        AlertDialog(
            onDismissRequest = onCancel,
            // A dialog is a window of its own, so the device tests need this here too
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text(stringResource(R.string.delete_title, pageNumber)) },
            text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = {
                TextButton(onClick = onConfirm, modifier = Modifier.testTag("delete-confirm")) {
                    Text(stringResource(R.string.delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel, modifier = Modifier.testTag("delete-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    companion object {
        private const val TAG = "Dachshund"
        private const val EXTRA_DIR = "dir"

        fun intent(context: Context, files: CaptureFiles): Intent =
            Intent(context, EditActivity::class.java).putExtra(EXTRA_DIR, files.dir.path)
    }
}
