package id.flufclip.player

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.roundToLong

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
    PREPARING("Preparing download"), TRANSFERRING("Downloading"), PROCESSING("Processing video/audio"),
    COPYING("Saving file"), VERIFYING("Verifying file"), COMPLETED("Downloaded"), CANCELLED("Canceled"), FAILED("Failed")
}

internal data class DownloadSignal(
    val phase: DownloadPhase,
    val percent: Int? = null,
    val etaSeconds: Long? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val totalEstimated: Boolean = false,
    val newDestination: Boolean = false,
)

internal data class DownloadQualityOption(
    val height: Int,
    val label: String,
    val containerLabel: String,
    val resolutionHeight: Int?,
    val fps: Int,
    val estimatedBytes: Long?,
    val isAudioOnly: Boolean = false,
)

private val DownloadPercent = Regex("\\[download]\\s+(\\d+(?:\\.\\d+)?)%")
private val DownloadBytesMarker = Regex("\\|bytes=(\\d+)/(\\d+)/(\\d+)\\|")
private val DownloadTotalSize = Regex("\\bof\\s+(~?)(\\d+(?:\\.\\d+)?)\\s*(KiB|MiB|GiB|TiB|KB|MB|GB|TB|B)\\b")

private fun sizeUnitMultiplier(unit: String): Double = when (unit) {
    "KiB" -> 1024.0
    "MiB" -> 1024.0 * 1024.0
    "GiB" -> 1024.0 * 1024.0 * 1024.0
    "TiB" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
    "KB" -> 1000.0
    "MB" -> 1000.0 * 1000.0
    "GB" -> 1000.0 * 1000.0 * 1000.0
    "TB" -> 1000.0 * 1000.0 * 1000.0 * 1000.0
    else -> 1.0
}

/** Do not treat the library's cached percent/FFmpeg 99 as end-to-end progress. */
internal fun downloadSignal(line: String, eta: Long): DownloadSignal? = when {
    line.startsWith("[Merger]") || line.startsWith("[ExtractAudio]") || line.startsWith("[VideoConvertor]") || line.trimStart().startsWith("size=") -> DownloadSignal(DownloadPhase.PROCESSING)
    line.startsWith("[download] Destination:") -> DownloadSignal(DownloadPhase.TRANSFERRING, newDestination = true)
    line.startsWith("[download]") -> {
        val percentFloat = DownloadPercent.find(line)?.groupValues?.get(1)?.toFloatOrNull()
        val percent = percentFloat?.toInt()?.coerceIn(0, 100)
        val marker = DownloadBytesMarker.find(line)
        var downloadedBytes: Long? = null
        var totalBytes: Long? = null
        var totalEstimated = false
        if (marker != null) {
            val rawDownloaded = marker.groupValues[1].toLongOrNull()
            val exactTotal = marker.groupValues[2].toLongOrNull()?.takeIf { it > 0L }
            val estTotal = marker.groupValues[3].toLongOrNull()?.takeIf { it > 0L }
            totalBytes = exactTotal ?: estTotal
            totalEstimated = exactTotal == null && estTotal != null
            downloadedBytes = rawDownloaded?.takeIf { it > 0L || (percent != null && totalBytes != null) }
        } else {
            val sizeMatch = DownloadTotalSize.find(line)
            if (sizeMatch != null) {
                val tilde = sizeMatch.groupValues[1]
                val value = sizeMatch.groupValues[2].toDoubleOrNull()
                val unit = sizeMatch.groupValues[3]
                if (value != null && value > 0.0) {
                    val parsedTotal = (value * sizeUnitMultiplier(unit)).roundToLong().takeIf { it > 0L }
                    totalBytes = parsedTotal
                    totalEstimated = tilde == "~"
                    if (parsedTotal != null && percentFloat != null) {
                        downloadedBytes = ((parsedTotal.toDouble() * percentFloat.coerceIn(0f, 100f)) / 100.0).roundToLong()
                    }
                }
            }
        }
        DownloadSignal(
            phase = DownloadPhase.TRANSFERRING,
            percent = percent,
            etaSeconds = eta.takeIf { it >= 0 && line.contains("ETA") },
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            totalEstimated = totalEstimated,
        )
    }
    else -> null
}

internal fun formatTransferProgressMessage(
    label: String,
    percent: Int?,
    downloadedBytes: Long?,
    totalBytes: Long?,
    totalEstimated: Boolean,
    formatSize: (Long) -> String,
): String {
    val sizePart = when {
        downloadedBytes != null && totalBytes != null && totalBytes > 0L -> {
            val prefix = if (totalEstimated) "~" else ""
            "${formatSize(downloadedBytes)} / $prefix${formatSize(totalBytes)}"
        }
        downloadedBytes != null && downloadedBytes > 0L -> formatSize(downloadedBytes)
        totalBytes != null && totalBytes > 0L -> {
            val prefix = if (totalEstimated) "~" else ""
            "$prefix${formatSize(totalBytes)}"
        }
        else -> null
    }
    val text = listOfNotNull(label, percent?.let { "$it%" }, sizePart).joinToString(" · ")
    return if (percent == null && sizePart == null && !text.endsWith("…")) "$text…" else text
}

internal fun streamHeight(stream: VideoStream): Int =
    stream.height.takeIf { it > 0 } ?: stream.resolution.filter(Char::isDigit).toIntOrNull() ?: 0

internal fun buildVideoItagItem(
    itagId: Int,
    hasAudio: Boolean,
    mediaFormat: MediaFormat,
    resolution: String,
    width: Int = 0,
    height: Int = 0,
    fps: Int = 0,
    bitrateBps: Int = 0,
    contentLength: Long = -1L,
): ItagItem = ItagItem(
    itagId,
    if (hasAudio) ItagItem.ItagType.VIDEO else ItagItem.ItagType.VIDEO_ONLY,
    mediaFormat,
    resolution,
).apply {
    if (width > 0) this.width = width
    if (height > 0) this.height = height
    if (fps > 0) this.fps = fps
    if (bitrateBps > 0) this.bitrate = bitrateBps
    if (contentLength > 0L) this.contentLength = contentLength
}

internal fun buildAudioItagItem(
    itagId: Int,
    mediaFormat: MediaFormat,
    avgBitrateKbps: Int,
    bitrateBps: Int = 0,
    contentLength: Long = -1L,
): ItagItem = ItagItem(
    itagId,
    ItagItem.ItagType.AUDIO,
    mediaFormat,
    avgBitrateKbps,
).apply {
    if (bitrateBps > 0) this.bitrate = bitrateBps
    if (contentLength > 0L) this.contentLength = contentLength
}

private fun videoBitrateBps(stream: VideoStream): Int =
    stream.bitrate.takeIf { it > 0 }
        ?: stream.itagItem?.bitrate?.takeIf { it > 0 }
        ?: 0

private fun audioBitrateBps(stream: AudioStream): Int =
    stream.bitrate.takeIf { it > 0 }
        ?: stream.itagItem?.bitrate?.takeIf { it > 0 }
        ?: stream.averageBitrate.takeIf { it > 0 }?.let { if (it < 10_000) it * 1000 else it }
        ?: 0

internal fun estimateVideoStreamBytes(stream: VideoStream, durationSeconds: Long): Long? {
    val contentLength = stream.itagItem?.contentLength ?: -1L
    if (contentLength > 0L) return contentLength
    val bitrateBps = videoBitrateBps(stream)
    if (bitrateBps <= 0 || durationSeconds <= 0L) return null
    return (bitrateBps.toLong() * durationSeconds) / 8L
}

internal fun estimateAudioStreamBytes(stream: AudioStream, durationSeconds: Long): Long? {
    val contentLength = stream.itagItem?.contentLength ?: -1L
    if (contentLength > 0L) return contentLength
    val bitrateBps = audioBitrateBps(stream)
    if (bitrateBps <= 0 || durationSeconds <= 0L) return null
    return (bitrateBps.toLong() * durationSeconds) / 8L
}

private fun pickBestAudioForDownload(audios: List<AudioStream>, durationSeconds: Long): AudioStream? =
    audios.maxWithOrNull(
        compareBy<AudioStream> { audioBitrateBps(it) }
            .thenBy { if (estimateAudioStreamBytes(it, durationSeconds) != null) 1 else 0 }
            .thenBy { it.itagItem?.contentLength ?: -1L }
    )

private fun pickBestVideoForCap(
    videos: List<VideoStream>,
    maxHeight: Int,
    hasAudioStreams: Boolean,
    durationSeconds: Long,
): VideoStream? {
    val scoped = if (maxHeight > 0) videos.filter { streamHeight(it) in 1..maxHeight }
    else videos.filter { streamHeight(it) > 0 }
    val pool = scoped.ifEmpty { videos }
    return pool.maxWithOrNull(
        compareBy<VideoStream> { streamHeight(it) }
            .thenBy { if (hasAudioStreams && it.isVideoOnly) 1 else 0 }
            .thenBy { it.fps.coerceAtLeast(0) }
            .thenBy { if (estimateVideoStreamBytes(it, durationSeconds) != null) 1 else 0 }
            .thenBy { videoBitrateBps(it) }
            .thenBy { it.itagItem?.contentLength ?: -1L }
    )
}

private fun estimateCombinedVideoBytes(
    video: VideoStream?,
    bestAudio: AudioStream?,
    hasAudioStreams: Boolean,
    durationSeconds: Long,
): Long? {
    if (video == null) return null
    val videoBytes = estimateVideoStreamBytes(video, durationSeconds) ?: return null
    if (!video.isVideoOnly || !hasAudioStreams) return videoBytes
    val audioBytes = bestAudio?.let { estimateAudioStreamBytes(it, durationSeconds) } ?: return null
    return videoBytes + audioBytes
}

internal fun buildDownloadQualityOptions(info: StreamInfo): List<DownloadQualityOption> {
    val videos = (info.videoOnlyStreams + info.videoStreams).filter {
        it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.HLS)
    }
    val audios = info.audioStreams.filter {
        it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.HLS)
    }
    val hasAdaptiveOrManifest = videos.isNotEmpty() || !info.hlsUrl.isNullOrBlank() || !info.dashMpdUrl.isNullOrBlank()
    val bestAudio = pickBestAudioForDownload(audios, info.duration)
    val heights = videos.map(::streamHeight).filter { it > 0 }.distinct().sortedDescending()
    val options = mutableListOf<DownloadQualityOption>()

    if (hasAdaptiveOrManifest) {
        val bestVideo = pickBestVideoForCap(videos, 0, audios.isNotEmpty(), info.duration)
        options += DownloadQualityOption(
            height = 0,
            label = "Best quality",
            containerLabel = "MP4",
            resolutionHeight = bestVideo?.let(::streamHeight)?.takeIf { it > 0 },
            fps = bestVideo?.fps?.takeIf { it > 30 } ?: 0,
            estimatedBytes = estimateCombinedVideoBytes(bestVideo, bestAudio, audios.isNotEmpty(), info.duration),
        )
    }
    heights.forEach { height ->
        val chosen = pickBestVideoForCap(videos, height, audios.isNotEmpty(), info.duration)
        options += DownloadQualityOption(
            height = height,
            label = "${height}p",
            containerLabel = "MP4",
            resolutionHeight = height,
            fps = chosen?.fps?.takeIf { it > 30 } ?: 0,
            estimatedBytes = estimateCombinedVideoBytes(chosen, bestAudio, audios.isNotEmpty(), info.duration),
        )
    }
    if (audios.isNotEmpty() || hasAdaptiveOrManifest) {
        options += DownloadQualityOption(
            height = -1,
            label = "Audio only",
            containerLabel = "MP3",
            resolutionHeight = null,
            fps = 0,
            estimatedBytes = bestAudio?.let { estimateAudioStreamBytes(it, info.duration) },
            isAudioOnly = true,
        )
    }
    return options
}

internal fun downloadResumePosition(position: Long, duration: Long): Long = when {
    position < 0 -> 0
    duration > 0 && position >= (duration - 1500).coerceAtLeast(0) -> 0
    else -> position
}
