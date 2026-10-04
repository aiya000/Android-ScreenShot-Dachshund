package io.github.aiya000.screenshotdachshund.storage

import android.content.Context
import java.io.File

/**
 * Where one capture keeps its files while it is being taken and edited: a folder of its
 * own under the app's cache, holding the pages as `page-0001.png`, `page-0002.png`, ...
 * and the joined result as `joined.png`.
 *
 * Nothing in here is the user's: the cache may be cleared by the system at any time, and
 * the result the user keeps is what [SavedImages] writes to the shared pictures.
 */
class CaptureFiles(val dir: File) {

    val joined: File get() = File(dir, JOINED_NAME)

    /** The system bars' heights, "<top> <bottom>", as the service measured them when capturing. */
    private val insetsFile: File get() = File(dir, INSETS_NAME)

    fun writeInsets(top: Int, bottom: Int) {
        insetsFile.writeText("$top $bottom")
    }

    /** The system bars' heights the service recorded, or null for a capture without them. */
    fun readInsets(): Pair<Int, Int>? {
        if (!insetsFile.exists()) return null
        val parts = insetsFile.readText().trim().split(' ')
        if (parts.size != 2) return null
        val top = parts[0].toIntOrNull() ?: return null
        val bottom = parts[1].toIntOrNull() ?: return null
        return top to bottom
    }

    fun pageFile(index: Int): File = File(dir, "page-%04d.png".format(index + 1))

    /** The page files that exist, in capture order. */
    fun pageFiles(): List<File> =
        dir.listFiles { f -> f.name.startsWith("page-") && f.name.endsWith(".png") }
            .orEmpty()
            .sortedBy { it.name }

    fun delete() {
        dir.deleteRecursively()
    }

    companion object {
        private const val JOINED_NAME = "joined.png"
        private const val INSETS_NAME = "insets.txt"
        private const val DIR_PREFIX = "capture-"

        /**
         * A new, empty folder for a capture starting now. The folders of earlier captures
         * are swept away first, except [keep]: the ones an edit screen still shows, which
         * Save still needs. Nothing else ever removes them, and a day of captures would
         * otherwise fill the cache.
         */
        fun create(context: Context, keep: Collection<File> = emptyList()): CaptureFiles {
            val root = File(context.cacheDir, "captures").apply { mkdirs() }
            sweep(root, keep)
            val dir = File(root, "$DIR_PREFIX${System.currentTimeMillis()}").apply { mkdirs() }
            return CaptureFiles(dir)
        }

        /** Deletes every capture folder under [root] but those in [keep]. */
        fun sweep(root: File, keep: Collection<File>) {
            val kept = keep.map { it.canonicalFile }.toSet()
            root.listFiles { f -> f.isDirectory && f.name.startsWith(DIR_PREFIX) }
                .orEmpty()
                .filter { it.canonicalFile !in kept }
                .forEach { it.deleteRecursively() }
        }

        fun open(path: String): CaptureFiles = CaptureFiles(File(path))
    }
}
