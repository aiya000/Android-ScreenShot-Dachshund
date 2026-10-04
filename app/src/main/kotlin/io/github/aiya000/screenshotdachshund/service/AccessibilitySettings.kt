package io.github.aiya000.screenshotdachshund.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/** The ways into the system settings where the service is switched on. */
object AccessibilitySettings {

    /**
     * Opens the settings as close to the service's own switch as the system allows: the
     * accessibility list landed on this service (the row is highlighted), or the plain list
     * where the settings app ignores the request. Returns false only where not even the
     * plain list could be opened.
     *
     * The service's own page (`android.settings.ACCESSIBILITY_DETAILS_SETTINGS`) is not
     * tried: starting it needs `OPEN_ACCESSIBILITY_DETAILS_SETTINGS`, a permission only
     * system apps hold, so an app can never get there directly.
     */
    fun open(context: Context): Boolean {
        for (intent in candidates(context)) {
            if (intent.resolveActivity(context.packageManager) == null) continue
            val opened = runCatching { context.startActivity(intent) }.isSuccess
            if (opened) return true
        }
        return false
    }

    private fun candidates(context: Context): List<Intent> {
        val component = ComponentName(context, DachshundService::class.java)
        val flattened = component.flattenToString()
        val args = Bundle().apply { putString(FRAGMENT_ARGS_KEY, flattened) }
        return listOf(
            // The accessibility list, asked to land on this service
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(FRAGMENT_ARGS_KEY, flattened)
                .putExtra(SHOW_FRAGMENT_ARGS, args),
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    private const val FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
    private const val SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
}
