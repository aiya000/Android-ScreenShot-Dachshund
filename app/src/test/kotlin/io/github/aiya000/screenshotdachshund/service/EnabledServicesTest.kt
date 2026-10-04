package io.github.aiya000.screenshotdachshund.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnabledServicesTest {

    private val ours = "io.github.aiya000.screenshotdachshund/io.github.aiya000.screenshotdachshund.service.DachshundService"

    @Test
    fun `an unset setting means off`() {
        assertFalse(EnabledServices.lists(null, ours))
        assertFalse(EnabledServices.lists("", ours))
    }

    @Test
    fun `the service alone, or among others, means on`() {
        assertTrue(EnabledServices.lists(ours, ours))
        assertTrue(EnabledServices.lists("com.example/.Other:$ours:com.other/.Svc", ours))
    }

    @Test
    fun `another app's service of the same name does not count`() {
        assertFalse(EnabledServices.lists("com.example/io.github.aiya000.screenshotdachshund.service.DachshundService", ours))
    }

    @Test
    fun `the debug build's service is not the release build's`() {
        val debug = "io.github.aiya000.screenshotdachshund.debug/io.github.aiya000.screenshotdachshund.service.DachshundService"

        assertFalse(EnabledServices.lists(debug, ours))
        assertTrue(EnabledServices.lists(debug, debug))
    }

    @Test
    fun `the short form the settings app writes is understood`() {
        // The settings app may write the class relative to the package
        assertTrue(EnabledServices.lists("io.github.aiya000.screenshotdachshund/.service.DachshundService", ours))
    }
}
