package id.flufclip.player

internal enum class PlaybackManifest { HLS, DASH, NONE }

/** YouTube live DASH timestamps can differ from the segment wall clock by minutes. */
internal fun playbackManifest(isLive: Boolean, hlsUrl: String?, dashUrl: String?, preferLiveDash: Boolean = false): PlaybackManifest = when {
    isLive && preferLiveDash && !dashUrl.isNullOrBlank() -> PlaybackManifest.DASH
    isLive && !hlsUrl.isNullOrBlank() -> PlaybackManifest.HLS
    !dashUrl.isNullOrBlank() -> PlaybackManifest.DASH
    else -> PlaybackManifest.NONE
}

/** A short rolling playlist is not a usable DVR, even when the DASH manifest offers one. */
internal fun preferDashForDvr(hlsSeconds: Double?, dashSeconds: Double?): Boolean =
    hlsSeconds != null && hlsSeconds > 0 && hlsSeconds <= 15 && dashSeconds != null && dashSeconds >= 60

internal fun hlsWindowSeconds(playlist: String): Double? {
    if (!playlist.startsWith("#EXTM3U")) return null
    val durations = Regex("(?m)^#EXTINF:([0-9]+(?:\\.[0-9]+)?)").findAll(playlist)
        .mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
    return durations.takeIf { it.isNotEmpty() }?.sum()
}

internal fun dashWindowSeconds(manifestHeader: String): Double? {
    val value = Regex("timeShiftBufferDepth=[\"']([^\"']+)[\"']").find(manifestHeader)?.groupValues?.get(1) ?: return null
    return try { java.time.Duration.parse(value).toMillis() / 1000.0 } catch (_: Exception) { null }
}
