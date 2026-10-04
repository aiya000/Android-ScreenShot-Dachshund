package io.github.aiya000.screenshotdachshund.service

/**
 * Reads the system's list of enabled accessibility services, which is one string of
 * `package/class` entries joined by colons. A class may be written relative to its
 * package (`package/.Class`), the way the settings app sometimes writes it.
 */
object EnabledServices {

    /** Whether [component] (`package/fully.qualified.Class`) is in [setting]. */
    fun lists(setting: String?, component: String): Boolean {
        if (setting.isNullOrEmpty()) return false
        return setting.split(':').any { expand(it.trim()) == component }
    }

    private fun expand(entry: String): String {
        val slash = entry.indexOf('/')
        if (slash < 0) return entry
        val pkg = entry.substring(0, slash)
        val cls = entry.substring(slash + 1)
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else entry
    }
}
