package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.SubtitlesStream
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
    var selectedSubtitle by mutableStateOf<SubtitlesStream?>(null)
    var lastSubtitle by mutableStateOf<SubtitlesStream?>(null)
    var attachedSubtitle by mutableStateOf<SubtitlesStream?>(null)
    var attachedSubtitleVideo by mutableStateOf<String?>(null)

    /** True bila audio diputar lewat manifest/stream adaptif (bukan mode langsung). */
    fun usesAdaptiveAudio(info: StreamInfo): Boolean = !forceDirectPlayback && (
        playbackManifest(info.isOngoingLive(), info.hlsUrl, info.dashMpdUrl) != PlaybackManifest.NONE ||
            quality?.deliveryMethod == DeliveryMethod.HLS || (quality == null && !info.hlsUrl.isNullOrBlank())
    )
    fun playQuality(player: ExoPlayer, context: Context, maxVideoHeight: Int, info: StreamInfo, stream: VideoStream?, audio: AudioStream?, position: Long, play: Boolean, forceDirect: Boolean = false) {
        OfflinePlaybackBridge.close()
        val isLive = info.isOngoingLive()
        val dashUrl = info.dashMpdUrl?.takeIf { it.isNotBlank() }
        val hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() }
            ?: stream?.takeIf { it.deliveryMethod == DeliveryMethod.HLS }?.content
        val manifest = if (forceDirect) PlaybackManifest.NONE else playbackManifest(isLive, hlsUrl, dashUrl, VideoRepository.preferLiveDash(info))
        val adaptive = manifest != PlaybackManifest.NONE
        android.util.Log.d("FlufMedia", "type=${info.streamType} dur=${info.duration} hls=${hlsUrl?.length ?: -1} dash=${dashUrl?.length ?: -1} manifest=$manifest live=$isLive")
        val wantedHeight = when {
            adaptive -> stream?.height?.takeIf { it > 0 } ?: maxVideoHeight
            stream == null -> maxVideoHeight
            else -> Int.MAX_VALUE
        }
        val parameters = player.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, wantedHeight)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, selectedSubtitle == null)
        if (stream != null) parameters.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        player.trackSelectionParameters = parameters.build()
        val factory = youTubeMediaSourceFactory(context)
        val mediaKey = videoKey(info.url)
        fun media(url: String, manifestMime: String? = null): MediaItem {
            val builder = MediaItem.Builder().setUri(url).setMediaId(mediaKey)
            if (manifestMime != null) builder.setMimeType(manifestMime)
            if (isLive) builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(5_000L).setMinPlaybackSpeed(.97f).setMaxPlaybackSpeed(1.03f).build())
            selectedSubtitle?.takeIf { it.isUrl && it.content.isNotBlank() }?.let { sub ->
                val mime = when (sub.format?.suffix?.lowercase()) { "vtt" -> MimeTypes.TEXT_VTT; "ttml" -> MimeTypes.APPLICATION_TTML; "srt" -> MimeTypes.APPLICATION_SUBRIP; else -> null }
                if (mime != null) builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.content)).setMimeType(mime).setLanguage(sub.languageTag).setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT).build()))
            }
            return builder.build()
        }
        fun plain(url: String): MediaItem = MediaItem.Builder().setUri(url).setMediaId(mediaKey).build()
        val source = when {
            manifest == PlaybackManifest.HLS -> factory.createMediaSource(media(hlsUrl!!, MimeTypes.APPLICATION_M3U8))
            manifest == PlaybackManifest.DASH -> factory.createMediaSource(media(dashUrl!!, MimeTypes.APPLICATION_MPD))
            stream != null && stream.deliveryMethod == DeliveryMethod.HLS -> factory.createMediaSource(media(stream.content, MimeTypes.APPLICATION_M3U8))
            stream != null && stream.isVideoOnly && audio != null -> MergingMediaSource(factory.createMediaSource(media(stream.content)), factory.createMediaSource(plain(audio.content)))
            stream != null && !stream.isVideoOnly -> factory.createMediaSource(media(stream.content))
            stream == null && hlsUrl != null -> factory.createMediaSource(media(hlsUrl, MimeTypes.APPLICATION_M3U8))
            else -> error("No playable video format is available.")
        }
        if (position > 0) player.setMediaSource(source, position) else player.setMediaSource(source)
        attachedSubtitle = selectedSubtitle
        attachedSubtitleVideo = mediaKey
        quality = stream
        forceDirectPlayback = forceDirect
        player.prepare()
        player.playWhenReady = play
    }
    fun changeSubtitle(player: ExoPlayer, context: Context, maxVideoHeight: Int, info: StreamInfo, subtitle: SubtitlesStream?) {
        val previous = selectedSubtitle
        try {
            if (subtitle == null || (attachedSubtitleVideo == videoKey(info.url) && attachedSubtitle == subtitle)) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle == null).build()
                selectedSubtitle = subtitle
            } else {
                val position = player.currentPosition.coerceAtLeast(0L)
                val playing = player.playWhenReady
                selectedSubtitle = subtitle
                playQuality(player, context, maxVideoHeight, info, quality, selectedAudio, position, playing, forceDirectPlayback)
            }
            if (subtitle != null) lastSubtitle = subtitle
        } catch (e: Exception) {
            selectedSubtitle = previous
            android.widget.Toast.makeText(context, "Captions failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

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

/** Terapkan urutan bahasa audio pilihan ke parameter pemilihan trek [player]. */
internal fun configurePreferredAudio(player: ExoPlayer, info: StreamInfo, preference: AudioPreference) {
    val languages = preferredAudioLanguages(preference, java.util.Locale.getDefault(), audioTracks(info)).toMutableList()
    val embeddedOriginal = VideoRepository.embeddedAudio(info).firstOrNull { it.metadata.type == AudioTrackType.ORIGINAL }
        ?.metadata?.locale?.toLanguageTag()
    if (!embeddedOriginal.isNullOrBlank() && languages.none { it.equals(embeddedOriginal, ignoreCase = true) }) languages += embeddedOriginal
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setPreferredAudioLanguages(*languages.toTypedArray()).build()
}

/** Sumber media satu Short. mediaId = videoKey, supaya item di daftar putar pemutar bisa dikenali. */
internal fun shortSource(context: Context, info: StreamInfo, stream: VideoStream?, audio: AudioStream?): MediaSource {
    val key = videoKey(info.url)
    val factory = youTubeMediaSourceFactory(context)
    fun item(url: String) = MediaItem.Builder().setUri(url).setMediaId(key).build()
    return when {
        stream != null && stream.deliveryMethod == DeliveryMethod.HLS -> youTubeHlsSource(context, stream.content, key)
        stream != null && stream.isVideoOnly && audio != null -> MergingMediaSource(factory.createMediaSource(item(stream.content)), factory.createMediaSource(MediaItem.fromUri(audio.content)))
        stream != null && !stream.isVideoOnly -> factory.createMediaSource(item(stream.content))
        stream == null && !info.hlsUrl.isNullOrBlank() -> youTubeHlsSource(context, info.hlsUrl, key)
        stream == null && !info.dashMpdUrl.isNullOrBlank() -> youTubeDashSource(context, info.dashMpdUrl, key)
        else -> error("No playable video format is available.")
    }
}

/** Tambahkan Short berikutnya ke daftar putar agar ExoPlayer mem-buffernya selagi Short sekarang diputar. */
internal fun preloadNext(player: ExoPlayer, context: Context, current: StreamInfo, next: StreamInfo, maxVideoHeight: Int) {
    if (player.playbackState == Player.STATE_IDLE || player.currentMediaItem?.mediaId != videoKey(current.url)) return
    val nextKey = videoKey(next.url)
    val at = player.currentMediaItemIndex + 1
    if (player.mediaItemCount > at) {
        if (player.getMediaItemAt(at).mediaId == nextKey) return
        player.removeMediaItems(at, player.mediaItemCount)
    }
    runCatching {
        val (stream, audio) = pickStreams(next, maxVideoHeight)
        player.addMediaSource(shortSource(context, next, stream, audio))
    }
}
