package id.flufclip.player

import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

internal data class EmbeddedAudioTrack(val video: VideoStream, val metadata: AudioTrackMetadata)

internal data class YtDlpFormats(
    val progressive: List<VideoStream>,
    val videoOnly: List<VideoStream>,
    val audio: List<AudioStream>,
    val embeddedAudio: List<EmbeddedAudioTrack>,
)

/** Parse the original yt-dlp JSON so language fields survive both separated and combined formats. */
internal fun parseYtDlpFormats(formats: JSONArray?): YtDlpFormats {
    val progressive = mutableListOf<VideoStream>()
    val videoOnly = mutableListOf<VideoStream>()
    val audio = mutableListOf<AudioStream>()
    val embedded = mutableListOf<EmbeddedAudioTrack>()
    fun field(obj: JSONObject, key: String): String? =
        if (obj.isNull(key)) null else obj.optString(key).takeIf { it.isNotBlank() }
    for (index in 0 until (formats?.length() ?: 0)) {
        val format = formats?.optJSONObject(index) ?: continue
        val url = field(format, "url") ?: continue
        val isHls = url.contains("/api/manifest/") || url.contains(".m3u8")
        val vcodec = field(format, "vcodec")
        val acodec = field(format, "acodec")
        val hasVideo = vcodec != null && vcodec != "none"
        val hasAudio = acodec != null && acodec != "none"
        val id = field(format, "format_id") ?: "ytdlp-$index"
        val itagId = id.substringBefore('-').toIntOrNull() ?: -1
        val height = format.optInt("height", 0)
        val width = format.optInt("width", 0)
        val fps = format.optInt("fps", 0)
        val contentLength = format.optLong("filesize", -1L).takeIf { it > 0L }
            ?: format.optLong("filesize_approx", -1L).takeIf { it > 0L }
            ?: -1L
        val resolution = if (height > 0) "${height}p" else VideoStream.RESOLUTION_UNKNOWN
        if (hasVideo) {
            val vbrKbps = format.optDouble("vbr", 0.0).takeIf { it > 0.0 } ?: format.optDouble("tbr", 0.0)
            val bitrateBps = (vbrKbps * 1000.0).toInt().coerceAtLeast(0)
            val itagItem = buildVideoItagItem(
                itagId = itagId,
                hasAudio = hasAudio,
                mediaFormat = MediaFormat.MPEG_4,
                resolution = resolution,
                width = width,
                height = height,
                fps = fps,
                bitrateBps = bitrateBps,
                contentLength = contentLength,
            )
            val video = VideoStream.Builder().setId(id).setContent(url, true).setIsVideoOnly(!hasAudio)
                .setResolution(resolution).setMediaFormat(MediaFormat.MPEG_4)
                .setDeliveryMethod(if (isHls) DeliveryMethod.HLS else DeliveryMethod.PROGRESSIVE_HTTP)
                .setItagItem(itagItem).build()
            if (hasAudio) {
                progressive += video
                val metadata = ytDlpAudioMetadata(format)
                if (metadata.locale != null) embedded += EmbeddedAudioTrack(video, metadata)
            } else videoOnly += video
        } else if (hasAudio) {
            val abrKbps = format.optDouble("abr", 0.0).takeIf { it > 0.0 } ?: format.optDouble("tbr", 0.0)
            val avgKbps = abrKbps.toInt().takeIf { it > 0 } ?: AudioStream.UNKNOWN_BITRATE
            val bitrateBps = (abrKbps * 1000.0).toInt().coerceAtLeast(0)
            val itagItem = buildAudioItagItem(
                itagId = itagId,
                mediaFormat = MediaFormat.M4A,
                avgBitrateKbps = avgKbps,
                bitrateBps = bitrateBps,
                contentLength = contentLength,
            )
            audio += AudioStream.Builder().setId(id).setContent(url, true)
                .setAverageBitrate(avgKbps)
                .setMediaFormat(MediaFormat.M4A).setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .setItagItem(itagItem)
                .withAudioMetadata(ytDlpAudioMetadata(format)).build()
        }
    }
    return YtDlpFormats(progressive, videoOnly, audio, embedded)
}
