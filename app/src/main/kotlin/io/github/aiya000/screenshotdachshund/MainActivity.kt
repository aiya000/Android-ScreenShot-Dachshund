package io.github.aiya000.screenshotdachshund

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aiya000.screenshotdachshund.service.DachshundService

/**
 * The front door: whether the service is on, the way into the system settings where it
 * is switched on, and a button that starts a capture for when the quick-settings tile
 * is not at hand. The capture itself happens in [DachshundService].
 */
class MainActivity : ComponentActivity() {

    private var serviceEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        serviceEnabled = serviceEnabled,
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onStartCapture = {
                            // The button floats over whatever comes to the front next,
                            // which should be the app to capture, so this one steps aside.
                            DachshundService.instance?.showStartButton()
                            moveTaskToBack(true)
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the system settings is the only way this can have changed.
        serviceEnabled = DachshundService.isEnabled(this) && DachshundService.instance != null
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
