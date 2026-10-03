package io.github.aiya000.screenshotdachshund.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.aiya000.screenshotdachshund.R
import io.github.aiya000.screenshotdachshund.join.CutModel
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
            /** The seam being adjusted, or null. */
            val adjusting: Int?,
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
                    onAdjust = { seam -> update { it.copy(adjusting = seam) } },
                    onAdjustDone = { update { it.copy(adjusting = null) } },
                    onMoveUpperEdge = { seam, rows -> update { it.copy(model = it.model.moveUpperEdge(seam, rows)) } },
                    onMoveLowerEdge = { seam, rows -> update { it.copy(model = it.model.moveLowerEdge(seam, rows)) } },
                )
            }
        }

        thread(name = "load") {
            val result = runCatching { Joining.load(files) }
            runOnUiThread {
                state = result.fold(
                    onSuccess = { State.Ready(it, it.model, adjusting = null, savedAs = null, saving = false) },
                    onFailure = {
                        Log.w(TAG, "load failed", it)
                        State.Failed(getString(R.string.join_failed))
                    },
                )
            }
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
        onAdjust: (Int) -> Unit,
        onAdjustDone: () -> Unit,
        onMoveUpperEdge: (Int, Int) -> Unit,
        onMoveLowerEdge: (Int, Int) -> Unit,
    ) {
        Scaffold(
            // testTagsAsResourceId: the device tests reach the buttons by these tags
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            topBar = { TopAppBar(title = { Text(stringResource(R.string.edit_title)) }) },
            bottomBar = {
                if (state is State.Ready && state.adjusting == null) {
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
                        JoinedPreview(state.capture, state.model, onAdjust)
                        val seam = state.adjusting
                        if (seam != null) {
                            AdjustSeamDialog(
                                capture = state.capture,
                                model = state.model,
                                seam = seam,
                                onMoveUpperEdge = { rows -> onMoveUpperEdge(seam, rows) },
                                onMoveLowerEdge = { rows -> onMoveLowerEdge(seam, rows) },
                                onDone = onAdjustDone,
                            )
                        }
                    }
                }
            }
        }
    }

    /** The joined image, drawn from the page previews cut by cut, with an Adjust button at every seam. */
    @Composable
    private fun JoinedPreview(capture: LoadedCapture, model: CutModel, onAdjust: (Int) -> Unit) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("joined"),
        ) {
            itemsIndexed(model.cuts) { index, cut ->
                val preview = capture.previews[cut.pageIndex]
                val top = cut.fromRow / capture.sample
                val bottom = (cut.toRow / capture.sample).coerceAtMost(preview.height)
                if (bottom > top) {
                    Image(
                        painter = BitmapPainter(
                            preview.asImageBitmap(),
                            srcOffset = IntOffset(0, top),
                            srcSize = IntSize(preview.width, bottom - top),
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (index < model.seams) {
                    SeamBar(seam = index, onAdjust = onAdjust)
                }
            }
        }
    }

    companion object {
        private const val TAG = "Dachshund"
        private const val EXTRA_DIR = "dir"

        fun intent(context: Context, files: CaptureFiles): Intent =
            Intent(context, EditActivity::class.java).putExtra(EXTRA_DIR, files.dir.path)
    }
}
