package io.github.aiya000.screenshotdachshund.join

/**
 * The cuts of a capture as something the user can move.
 *
 * Between two cuts that follow each other there is a seam, and a seam has two edges the
 * user may drag: where the page above stops being shown (its cut's `toRow`), and where the
 * page below starts (its cut's `fromRow`). Both are rows of their own page, and both move
 * "down" with a positive number of rows. A cut always keeps at least one row.
 */
data class CutModel(
    val cuts: List<Cut>,
    val pageHeights: List<Int>,
) {

    /** How many seams there are: one between each pair of cuts. */
    val seams: Int get() = (cuts.size - 1).coerceAtLeast(0)

    val joinedHeight: Int get() = cuts.sumOf { it.height }

    /** The row of the joined image at which seam [seam] lies: the first row of the cut below it. */
    fun seamRow(seam: Int): Int {
        requireSeam(seam)
        return cuts.take(seam + 1).sumOf { it.height }
    }

    /** Where the page above seam [seam] stops being shown, as a row of that page. */
    fun upperEdge(seam: Int): Int {
        requireSeam(seam)
        return cuts[seam].toRow
    }

    /** Where the page below seam [seam] starts being shown, as a row of that page. */
    fun lowerEdge(seam: Int): Int {
        requireSeam(seam)
        return cuts[seam + 1].fromRow
    }

    fun moveUpperEdge(seam: Int, rows: Int): CutModel {
        requireSeam(seam)
        val cut = cuts[seam]
        val toRow = (cut.toRow + rows).coerceIn(cut.fromRow + 1, pageHeights[cut.pageIndex])
        return copy(cuts = cuts.toMutableList().also { it[seam] = cut.copy(toRow = toRow) })
    }

    fun moveLowerEdge(seam: Int, rows: Int): CutModel {
        requireSeam(seam)
        val cut = cuts[seam + 1]
        val fromRow = (cut.fromRow + rows).coerceIn(0, cut.toRow - 1)
        return copy(cuts = cuts.toMutableList().also { it[seam + 1] = cut.copy(fromRow = fromRow) })
    }

    private fun requireSeam(seam: Int) {
        require(seam in 0 until seams) { "seam $seam of $seams" }
    }
}
