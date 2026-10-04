package io.github.aiya000.screenshotdachshund.join

/**
 * Whether leaving the edit screen now would lose work: nothing has been saved yet
 * ([saved] is null), or the cuts changed since the last save. Moving an edge and moving
 * it back is no change.
 */
fun unsavedWork(saved: CutModel?, current: CutModel): Boolean = saved != current
