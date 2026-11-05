package id.flufclip.player

/** Display metadata, independent of online stream extraction and signed playback URLs. */
data class DownloadVideo(
    val sourceUrl: String = "",
    val title: String = "",
    val uploader: String = "",
    val channelUrl: String = "",
    val thumbnail: String = "",
    val avatar: String = "",
    val durationMs: Long = 0,
    val views: Long = -1,
    val likes: Long = -1,
    val subscribers: Long = -1,
    val description: String = "",
)

enum class DownloadPhase(val label: String) {
    PREPARING("Preparing download"), TRANSFERRING("Downloading"), PROCESSING("Memproses video/audio"),
    COPYING("Saving file"), VERIFYING("Verifying file"), COMPLETED("Downloaded"), CANCELLED("Canceled"), FAILED("Failed")
}

internal data class DownloadSignal(val phase: DownloadPhase, val percent: Int? = null, val etaSeconds: Long? = null)

private val DownloadPercent = Regex("\\[download]\\s+(\\d+(?:\\.\\d+)?)%")

/** Do not treat the library's cached percent/FFmpeg 99 as end-to-end progress. */
internal fun downloadSignal(line: String, eta: Long): DownloadSignal? = when {
    line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") || line.startsWith("[VideoConvertor]") || line.trimStart().startsWith("size=") -> DownloadSignal(DownloadPhase.PROCESSING)
    line.startsWith("[download]") -> {
        val percent = DownloadPercent.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.toInt()?.coerceIn(0, 100)
        DownloadSignal(DownloadPhase.TRANSFERRING, percent, eta.takeIf { it >= 0 && line.contains("ETA") })
    }
    else -> null
}

internal fun downloadResumePosition(position: Long, duration: Long): Long = when {
    position < 0 -> 0
    duration > 0 && position >= (duration - 1500).coerceAtLeast(0) -> 0
    else -> position
}
