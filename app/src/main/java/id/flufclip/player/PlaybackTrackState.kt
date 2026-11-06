package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.schabi.newpipe.extractor.stream.AudioStream
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
}
