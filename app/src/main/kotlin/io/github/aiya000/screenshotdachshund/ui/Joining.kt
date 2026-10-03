package io.github.aiya000.screenshotdachshund.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.aiya000.screenshotdachshund.image.PngWriter
import io.github.aiya000.screenshotdachshund.image.StoredPage
import io.github.aiya000.screenshotdachshund.join.FixedEdge
import io.github.aiya000.screenshotdachshund.join.FixedEdges
import io.github.aiya000.screenshotdachshund.join.JoinedImage
import io.github.aiya000.screenshotdachshund.join.Joiner
import io.github.aiya000.screenshotdachshund.storage.CaptureFiles
import java.io.File

/** Turns the pages of a capture into its joined image on disk, and makes a preview of it. */
object Joining {

    /** The joined PNG of [files], written if it is not there yet. */
    fun join(files: CaptureFiles): File {
        if (files.joined.exists()) return files.joined
        val pages = files.pageFiles().map { StoredPage.load(it) }
        check(pages.isNotEmpty()) { "no pages in ${files.dir}" }
        // The status bar and the navigation bar are never content, whatever their pixels do
        val bars = files.readInsets()?.let { (top, bottom) -> FixedEdge(top, bottom) } ?: FixedEdge(0, 0)
        val cuts = Joiner.layout(pages, FixedEdges.detect(pages, atLeast = bars))
        val image = JoinedImage(pages.first().width, cuts) { pages[it].decode() }
        val tmp = File(files.dir, "joined.tmp")
        tmp.outputStream().buffered().use { PngWriter.write(image, it) }
        check(tmp.renameTo(files.joined)) { "cannot rename ${tmp.name}" }
        return files.joined
    }

    /**
     * A bitmap of [png] small enough to show: at most [maxWidth] wide and [maxHeight]
     * tall, scaled down by a power of two. A joined image can be far taller than any
     * texture may be.
     */
    fun preview(png: File, maxWidth: Int = 1440, maxHeight: Int = 8192): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(png.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxWidth || bounds.outHeight / sample > maxHeight) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(png.path, options)
    }
}
