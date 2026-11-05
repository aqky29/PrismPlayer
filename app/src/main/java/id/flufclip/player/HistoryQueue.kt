package id.flufclip.player

/** Keep the currently playing item in the list even when playback began outside a playlist. */
internal fun <T> enqueueHistoryVideo(
    currentKey: String,
    queue: List<T>,
    current: T,
    candidate: T,
    playNext: Boolean,
    key: (T) -> String,
): List<T> {
    val originalIndex = queue.indexOfFirst { key(it) == currentKey }
    val base = if (originalIndex < 0) listOf(current) else queue
    val candidateKey = key(candidate)
    if (candidateKey == currentKey) return base
    val withoutCandidate = base.filterNot { key(it) == candidateKey }
    val index = withoutCandidate.indexOfFirst { key(it) == currentKey }
    return withoutCandidate.toMutableList().apply { add(if (playNext) index + 1 else size, candidate) }
}
