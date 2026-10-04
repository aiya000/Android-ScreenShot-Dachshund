package io.github.aiya000.screenshotdachshund.join

import kotlin.math.roundToInt

/**
 * Where everything lands on screen when the joined image is drawn from scaled-down page
 * previews: one segment per cut, stacked with nothing between them, so that the picture is
 * the saved image and the controls beside it can be put level with the seams and the pages.
 *
 * [sample] is how many page rows one preview row stands for; [scale] is how many screen
 * pixels one preview row is drawn as. All results are screen pixels.
 */
class PreviewLayout private constructor(
    val segments: List<Segment>,
    val totalHeight: Int,
) {

    /** The cut [cutIndex] drawn from preview row [previewTop] for [previewRows] rows, at [top] for [height] pixels. */
    data class Segment(
        val cutIndex: Int,
        val previewTop: Int,
        val previewRows: Int,
        val top: Int,
        val height: Int,
    ) {
        val centre: Int get() = top + height / 2
    }

    /** The screen row of each seam: where the segment above it ends. */
    val seamTops: List<Int> get() = segments.dropLast(1).map { it.top + it.height }

    companion object {
        fun of(model: CutModel, sample: Int, scale: Float): PreviewLayout {
            val segments = mutableListOf<Segment>()
            var y = 0
            model.cuts.forEachIndexed { index, cut ->
                val previewTop = cut.fromRow / sample
                val previewRows = (cut.toRow / sample - previewTop).coerceAtLeast(0)
                val height = (previewRows * scale).roundToInt()
                segments += Segment(index, previewTop, previewRows, y, height)
                y += height
            }
            return PreviewLayout(segments, y)
        }
    }
}
