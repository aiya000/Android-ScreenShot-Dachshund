package io.github.aiya000.screenshotdachshund.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CaptureFilesSweepTest {

    private val root: File = Files.createTempDirectory("captures").toFile()

    private fun capture(name: String): File = File(root, name).apply {
        mkdirs()
        File(this, "page-0001.png").writeText("page")
    }

    private fun remaining(): List<String> = root.listFiles().orEmpty().map { it.name }.sorted()

    @Test
    fun `every capture folder goes but the kept ones`() {
        capture("capture-1")
        val kept = capture("capture-2")
        capture("capture-3")

        CaptureFiles.sweep(root, keep = listOf(kept))

        assertEquals(listOf("capture-2"), remaining())
        assertTrue(File(kept, "page-0001.png").exists())
    }

    @Test
    fun `with nothing to keep, every capture folder goes`() {
        capture("capture-1")
        capture("capture-2")

        CaptureFiles.sweep(root, keep = emptyList())

        assertEquals(emptyList<String>(), remaining())
    }

    @Test
    fun `things that are not capture folders are left alone`() {
        capture("capture-1")
        File(root, "notes.txt").writeText("x")
        File(root, "other").mkdirs()

        CaptureFiles.sweep(root, keep = emptyList())

        assertEquals(listOf("notes.txt", "other"), remaining())
    }

    @Test
    fun `a kept folder given by another path to the same place is still kept`() {
        val kept = capture("capture-1")

        CaptureFiles.sweep(root, keep = listOf(File(kept.path + File.separator + "." )))

        assertEquals(listOf("capture-1"), remaining())
    }

    @Test
    fun `a root that does not exist is nothing to sweep`() {
        CaptureFiles.sweep(File(root, "missing"), keep = emptyList())
    }
}
