package io.github.aiya000.screenshotdachshund.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.aiya000.screenshotdachshund.ui.StartActivity

/**
 * The quick-settings tile. Tapping it closes the panel and floats the start button over
 * the app that was on screen, which is where the capture is wanted.
 *
 * The tile is always drawn as inactive: it has no on/off state of its own, a tap simply
 * starts something, and a highlighted tile would read as "something is switched on".
 */
class CaptureTile : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = StartActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
