package io.github.aiya000.screenshotdachshund.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aiya000.screenshotdachshund.R
import io.github.aiya000.screenshotdachshund.storage.CaptureFiles
import io.github.aiya000.screenshotdachshund.storage.SavedImages
import kotlin.concurrent.thread

/**
 * The edit screen: the joined capture, and a button to save it into the shared pictures.
 *
 * It joins the pages itself when it opens, so that the wait has a screen to show on;
 * adjusting the cuts and dropping pages come later and live here too.
 */
class EditActivity : ComponentActivity() {

    private sealed interface State {
        data object Joining : State
        data class Ready(val preview: Bitmap, val savedAs: String?, val saving: Boolean) : State
        data class Failed(val message: String) : State
    }

    private var state by mutableStateOf<State>(State.Joining)
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
                EditScreen(state = state, onSave = ::save)
            }
        }

        thread(name = "join") {
            val result = runCatching {
                val joined = Joining.join(files)
                Joining.preview(joined) ?: error("no preview")
            }
            runOnUiThread {
                state = result.fold(
                    onSuccess = { State.Ready(it, savedAs = null, saving = false) },
                    onFailure = {
                        Log.w(TAG, "join failed", it)
                        State.Failed(getString(R.string.join_failed))
                    },
                )
            }
        }
    }

    private fun save() {
        val ready = state as? State.Ready ?: return
        if (ready.saving) return
        state = ready.copy(saving = true)
        thread(name = "save") {
            val name = SavedImages.save(this, files.joined)
            runOnUiThread {
                val current = state as? State.Ready ?: return@runOnUiThread
                state = current.copy(saving = false, savedAs = name ?: current.savedAs)
                Toast.makeText(
                    this,
                    if (name == null) getString(R.string.save_failed) else getString(R.string.saved_to, name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun EditScreen(state: State, onSave: () -> Unit) {
        Scaffold(
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
                    State.Joining -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.joining),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }

                    is State.Failed -> Text(state.message, modifier = Modifier.padding(24.dp))

                    is State.Ready -> Image(
                        bitmap = state.preview.asImageBitmap(),
                        contentDescription = stringResource(R.string.joined_image),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .testTag("joined"),
                    )
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
