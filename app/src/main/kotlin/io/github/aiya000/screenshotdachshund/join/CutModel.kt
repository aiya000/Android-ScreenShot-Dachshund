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

/**
 * The model without cut [index]: the page is dropped from the joined image. A new first cut
 * starts at the top of its page and a new last cut ends at the bottom of its page, since the
 * bars they used to lose to a neighbour are now the edges of the whole image. Two cuts that
 * become neighbours are left as they were; whether they overlap is for the caller to work
 * out with the pages themselves.
 */
fun CutModel.removeCut(index: Int): CutModel {
    require(cuts.size > 1) { "the last cut cannot be removed" }
    require(index in cuts.indices) { "cut $index of ${cuts.size}" }
    val remaining = cuts.toMutableList().also { it.removeAt(index) }
    if (index == 0) {
        remaining[0] = remaining[0].copy(fromRow = 0)
    }
    if (index == cuts.lastIndex) {
        val last = remaining.last()
        remaining[remaining.lastIndex] = last.copy(toRow = pageHeights[last.pageIndex])
    }
    return copy(cuts = remaining)
}

/** The model with the upper edge of seam [seam] put at [toRow] of its page, kept inside the cut. */
fun CutModel.withUpperEdge(seam: Int, toRow: Int): CutModel {
    val current = upperEdge(seam)
    return moveUpperEdge(seam, toRow - current)
}

/** The model with the lower edge of seam [seam] put at [fromRow] of its page, kept inside the cut. */
fun CutModel.withLowerEdge(seam: Int, fromRow: Int): CutModel {
    val current = lowerEdge(seam)
    return moveLowerEdge(seam, fromRow - current)
}

/**
 * The model with seam [seam] joined afresh from the pages themselves: where the page above
 * stops and where the page below starts are decided the way the first layout decided them.
 * For two pages that became neighbours when the one between them was removed. A page
 * below that shows nothing new leaves the seam as it is.
 */
fun CutModel.rejoinSeam(seam: Int, pages: List<io.github.aiya000.screenshotdachshund.image.PixelRows>, fixed: FixedEdge): CutModel {
    val prev = pages[cuts[seam].pageIndex]
    val next = pages[cuts[seam + 1].pageIndex]
    val joined = Joiner.seam(prev, next, fixed) ?: return this
    return withUpperEdge(seam, joined.toRow).withLowerEdge(seam, joined.fromRow)
}
