package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * State pilihan trek (kualitas video, audio) dan status fallback audio pada pemutar utama.
 */
@Stable
internal class PlaybackTrackState {
    var qualityMode by mutableStateOf("auto")
    var quality by mutableStateOf<VideoStream?>(null)
    var selectedAudio by mutableStateOf<AudioStream?>(null)
    var forceDirectPlayback by mutableStateOf(false)
    var manualAudioOverride by mutableStateOf(false)
    var attemptedAudioFallback by mutableStateOf<String?>(null)

    /** True bila audio diputar lewat manifest/stream adaptif (bukan mode langsung). */
    fun usesAdaptiveAudio(info: StreamInfo): Boolean = !forceDirectPlayback && (
        playbackManifest(info.isOngoingLive(), info.hlsUrl, info.dashMpdUrl) != PlaybackManifest.NONE ||
            quality?.deliveryMethod == DeliveryMethod.HLS || (quality == null && !info.hlsUrl.isNullOrBlank())
    )
}

/** Pilih stream video/audio awal untuk [info]; video null berarti pemutaran adaptif. */
internal fun pickStreams(info: StreamInfo, maxVideoHeight: Int): Pair<VideoStream?, AudioStream?> {
    val options = qualityOptions(info)
    val audio = selectAudioTrack(audioTracks(info), PlayerSettings.audioPreference, java.util.Locale.getDefault())
    fun heightOf(stream: VideoStream): Int =
        stream.height.takeIf { it > 0 } ?: (stream.resolution.filter(Char::isDigit).toIntOrNull() ?: 0)
    val adaptive = !info.hlsUrl.isNullOrBlank() || !info.dashMpdUrl.isNullOrBlank()
    // A combined/progressive video has fixed audio; use a separate video stream when available.
    val separate = if (audio != null) options.filter { it.isVideoOnly } else emptyList()
    val candidates = separate.ifEmpty { options }
    val initial = if (adaptive) null else candidates.firstOrNull { heightOf(it) in 1..maxVideoHeight } ?: candidates.lastOrNull()
    if (initial == null && !adaptive) error("No playable video format is available.")
    return initial to audio
}
