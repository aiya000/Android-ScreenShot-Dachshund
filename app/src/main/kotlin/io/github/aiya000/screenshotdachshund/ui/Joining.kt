package io.github.aiya000.screenshotdachshund.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.aiya000.screenshotdachshund.image.PngWriter
import io.github.aiya000.screenshotdachshund.image.StoredPage
import io.github.aiya000.screenshotdachshund.join.CutModel
import io.github.aiya000.screenshotdachshund.join.FixedEdge
import io.github.aiya000.screenshotdachshund.join.FixedEdges
import io.github.aiya000.screenshotdachshund.join.JoinedImage
import io.github.aiya000.screenshotdachshund.join.Joiner
import io.github.aiya000.screenshotdachshund.storage.CaptureFiles
import java.io.File

/** The pages of a capture, the cuts worked out for them, and small copies of the pages to show. */
class LoadedCapture(
    val pages: List<StoredPage>,
    val model: CutModel,
    /** The bars at the top and bottom of every page, as the layout saw them. */
    val edges: FixedEdge,
    /** Each page scaled down by [sample], in capture order. */
    val previews: List<Bitmap>,
    val sample: Int,
)

/** Turns the pages of a capture into cuts, previews and, when asked, the joined PNG on disk. */
object Joining {

    /** Reads the pages back, works out where they overlap, and makes a preview of each. */
    fun load(files: CaptureFiles, previewWidth: Int = 720): LoadedCapture {
        val pages = files.pageFiles().map { StoredPage.load(it) }
        check(pages.isNotEmpty()) { "no pages in ${files.dir}" }

        // The status bar and the navigation bar are never content, whatever their pixels do
        val bars = files.readInsets()?.let { (top, bottom) -> FixedEdge(top, bottom) } ?: FixedEdge(0, 0)
        val edges = FixedEdges.detect(pages, atLeast = bars)
        val cuts = Joiner.layout(pages, edges)
        val model = CutModel(cuts, pages.map { it.height })

        var sample = 1
        while (pages.first().width / sample > previewWidth) sample *= 2
        val previews = pages.map { page ->
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(page.file.path, options) ?: error("cannot decode ${page.file.name}")
        }
        return LoadedCapture(pages, model, edges, previews, sample)
    }

    /** Writes the joined image of [pages] under [cuts] to disk, one row at a time, and returns it. */
    fun write(files: CaptureFiles, pages: List<StoredPage>, model: CutModel): File {
        val image = JoinedImage(pages.first().width, model.cuts) { pages[it].decode() }
        val tmp = File(files.dir, "joined.tmp")
        tmp.outputStream().buffered().use { PngWriter.write(image, it) }
        files.joined.delete()
        check(tmp.renameTo(files.joined)) { "cannot rename ${tmp.name}" }
        return files.joined
    }
}
