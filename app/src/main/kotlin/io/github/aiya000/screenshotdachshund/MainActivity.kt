package io.github.aiya000.screenshotdachshund

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import io.github.aiya000.screenshotdachshund.service.AccessibilitySettings
import io.github.aiya000.screenshotdachshund.service.DachshundService

/**
 * The front door: whether the service is on, the way into the system settings where it
 * is switched on, and a button that starts a capture for when the quick-settings tile
 * is not at hand. The capture itself happens in [DachshundService].
 */
class MainActivity : ComponentActivity() {

    private var serviceEnabled by mutableStateOf(false)

    /** Whether the user has put the "switch it on" question aside for now. */
    private var promptDismissed by mutableStateOf(false)

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        // testTagsAsResourceId: the device tests reach the buttons by these tags
                        .semantics { testTagsAsResourceId = true },
                ) {
                    HomeScreen(
                        serviceEnabled = serviceEnabled,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onStartCapture = {
                            // The button floats over whatever comes to the front next,
                            // which should be the app to capture, so this one steps aside.
                            DachshundService.instance?.showStartButton()
                            moveTaskToBack(true)
                        },
                    )
                    if (!serviceEnabled && !promptDismissed) {
                        ServiceOffDialog(
                            onOpen = ::openAccessibilitySettings,
                            onLater = { promptDismissed = true },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the system settings is the only way this can have changed.
        serviceEnabled = DachshundService.isEnabled(this) && DachshundService.instance != null
    }

    private fun openAccessibilitySettings() {
        if (!AccessibilitySettings.open(this)) {
            Toast.makeText(this, getString(R.string.settings_not_opened), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shown whenever the app opens with the service off: nothing can be captured until it
     * is on, and the button goes to the very switch.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun ServiceOffDialog(onOpen: () -> Unit, onLater: () -> Unit) {
        AlertDialog(
            onDismissRequest = onLater,
            // A dialog is a window of its own, so the device tests need this here too
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            title = { Text(stringResource(R.string.prompt_title)) },
            text = { Text(stringResource(R.string.prompt_message)) },
            confirmButton = {
                TextButton(onClick = onOpen, modifier = Modifier.testTag("prompt-open")) {
                    Text(stringResource(R.string.prompt_open))
                }
            },
            dismissButton = {
                TextButton(onClick = onLater, modifier = Modifier.testTag("prompt-later")) {
                    Text(stringResource(R.string.prompt_later))
                }
            },
        )
    }

    @Composable
    private fun HomeScreen(
        serviceEnabled: Boolean,
        onOpenAccessibilitySettings: () -> Unit,
        onStartCapture: () -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The window is drawn edge to edge; keep the text out from under the bars
                .safeDrawingPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(text = stringResource(R.string.home_intro))
            Text(
                text = stringResource(
                    if (serviceEnabled) R.string.service_status_on else R.string.service_status_off,
                ),
                modifier = Modifier.testTag("service-status"),
            )
            OutlinedButton(
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.open_accessibility_settings))
            }
            Button(
                onClick = onStartCapture,
                enabled = serviceEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start-capture"),
            ) {
                Text(stringResource(R.string.start_capture))
            }
            Text(
                text = stringResource(R.string.home_tile_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
