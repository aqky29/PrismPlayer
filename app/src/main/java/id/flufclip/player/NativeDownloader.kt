package id.flufclip.player

import androidx.annotation.Keep
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

internal sealed interface RustDownloadPlan {
    data class CombinedMp4(val videoUrl: String) : RustDownloadPlan
    data class MuxMp4(val videoUrl: String, val audioUrl: String) : RustDownloadPlan
}

/**
 * Selects a direct ISOBMFF (MP4 + M4A) download plan when it matches the requested quality
 * without downgrading resolution compared to non-MP4 streams (e.g. 1440p/4K WebM).
 */
internal fun selectRustDirectDownloadPlan(
    info: StreamInfo,
    maxHeight: Int,
    audioOnly: Boolean,
): RustDownloadPlan? {
    if (audioOnly || info.isOngoingLive()) return null

    val allVideos = (info.videoOnlyStreams + info.videoStreams).filter {
        it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.HLS)
    }
    val allAudios = info.audioStreams.filter {
        it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.HLS)
    }
    val bestOverall = pickBestVideoForCap(allVideos, maxHeight, allAudios.isNotEmpty(), info.duration) ?: return null
    val targetHeight = streamHeight(bestOverall)

    val mp4Videos = (info.videoOnlyStreams + info.videoStreams).filter {
        it.isUrl &&
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
            it.format == MediaFormat.MPEG_4
    }
    val m4aAudios = info.audioStreams.filter {
        it.isUrl &&
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
            it.format == MediaFormat.M4A
    }

    val chosenVideo = pickBestVideoForCap(mp4Videos, maxHeight, m4aAudios.isNotEmpty(), info.duration) ?: return null
    if (targetHeight > 0 && streamHeight(chosenVideo) < targetHeight) return null

    if (!chosenVideo.isVideoOnly) {
        return RustDownloadPlan.CombinedMp4(chosenVideo.content)
    }
    val chosenAudio = pickBestAudioForDownload(m4aAudios, info.duration) ?: return null
    return RustDownloadPlan.MuxMp4(chosenVideo.content, chosenAudio.content)
}

@Keep
internal interface DownloadProgressCallback {
    fun onProgress(downloadedBytes: Long, totalBytes: Long, etaSeconds: Long)
}

internal object NativeDownloader {
    val isAvailable: Boolean = runCatching {
        System.loadLibrary("fluf_native")
        true
    }.getOrDefault(false)

    @JvmStatic external fun nativeVersion(): String
    @JvmStatic external fun nativeCreateCancelToken(): Long
    @JvmStatic external fun nativeCancel(token: Long)
    @JvmStatic external fun nativeDestroyCancelToken(token: Long)

    @JvmStatic
    external fun nativeDownloadFile(
        url: String,
        headerPairs: Array<String>,
        destPath: String,
        cancelToken: Long,
        listener: DownloadProgressCallback?,
    ): Long

    @JvmStatic
    external fun nativeMuxMp4(
        videoPath: String,
        audioPath: String?,
        destPath: String,
        cancelToken: Long,
    ): Long

    @JvmStatic
    external fun nativeMuxMp4ToFd(
        videoPath: String,
        audioPath: String?,
        destFd: Int,
        cancelToken: Long,
    ): Long

    fun flattenHeaders(headers: Map<String, String>): Array<String> {
        val pairs = ArrayList<String>(headers.size * 2)
        headers.forEach { (k, v) ->
            pairs.add(k)
            pairs.add(v)
        }
        return pairs.toTypedArray()
    }
}
