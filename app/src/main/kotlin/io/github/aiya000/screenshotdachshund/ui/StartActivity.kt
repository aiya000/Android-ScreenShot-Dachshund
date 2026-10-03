package io.github.aiya000.screenshotdachshund.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import io.github.aiya000.screenshotdachshund.MainActivity
import io.github.aiya000.screenshotdachshund.R
import io.github.aiya000.screenshotdachshund.service.DachshundService

/**
 * An invisible activity that does one thing and leaves: it asks the service to float
 * its start button. The quick-settings tile goes through it because starting an activity
 * is the way a tile closes the panel.
 */
class StartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = DachshundService.instance
        if (service == null) {
            Toast.makeText(this, getString(R.string.service_not_enabled), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            service.showStartButton()
        }
        finish()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, StartActivity::class.java)
    }
}
