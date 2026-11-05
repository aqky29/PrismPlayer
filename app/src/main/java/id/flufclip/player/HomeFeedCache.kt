package id.flufclip.player

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** A small last-known-good snapshot; it contains no playback URLs or account data. */
internal object HomeFeedCache {
    private const val KEY = "items"
    private const val LIMIT = 40

    fun read(prefs: SharedPreferences): List<StreamInfoItem> = runCatching {
        val items = JSONArray(prefs.getString(KEY, "[]"))
        (0 until minOf(items.length(), LIMIT)).mapNotNull { index ->
            val json = items.optJSONObject(index) ?: return@mapNotNull null
            val url = json.optString("url")
            val title = json.optString("title")
            if (url.isBlank() || title.isBlank()) return@mapNotNull null
            val type = runCatching { StreamType.valueOf(json.optString("type")) }.getOrDefault(StreamType.VIDEO_STREAM)
            // Siaran live cepat berakhir; entri lama yang masih bertanda live tidak dipakai dari cache.
            if (type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM) return@mapNotNull null
            StreamInfoItem(json.optInt("service", 0), url, title, type).apply {
                uploaderName = json.optString("uploader")
                uploaderUrl = json.optString("channel")
                viewCount = json.optLong("views", -1)
                duration = json.optLong("duration", -1)
                textualUploadDate = json.optString("date")
                json.optString("thumbnail").takeIf { it.isNotBlank() }?.let {
                    thumbnails = listOf(Image(it, -1, -1, Image.ResolutionLevel.UNKNOWN))
                }
                json.optString("avatar").takeIf { it.isNotBlank() }?.let {
                    uploaderAvatars = listOf(Image(it, -1, -1, Image.ResolutionLevel.UNKNOWN))
                    val ch = json.optString("channel")
                    if (ch.isNotBlank()) VideoRepository.cacheAvatar(ch, it)
                }
                val progress = json.optInt("progress", -1)
                val resumeMs = json.optLong("resume_ms", 0L)
                if (progress in 0..100 || resumeMs > 0L) {
                    HomeFeed.rememberMeta(url, VideoMeta(
                        viewsText = json.optString("views_text"),
                        publishedText = json.optString("date"),
                        progressPercent = progress,
                        resumePositionMs = resumeMs
                    ))
                }
            }
        }
    }.getOrDefault(emptyList())

    fun save(prefs: SharedPreferences, items: List<StreamInfoItem>) {
        if (items.isEmpty()) return
        val json = JSONArray()
        items.filterNot { it.isOngoingLive() }.take(LIMIT).forEach { item ->
            val meta = HomeFeed.metaOf(item.url)
            json.put(JSONObject().put("service", item.serviceId).put("url", item.url).put("title", item.name)
                .put("type", item.streamType.name).put("uploader", item.uploaderName.orEmpty())
                .put("channel", item.uploaderUrl.orEmpty()).put("views", item.viewCount).put("duration", item.duration)
                .put("date", item.textualUploadDate.orEmpty()).put("thumbnail", item.thumbnails.lastOrNull()?.url.orEmpty())
                .put("avatar", item.uploaderAvatars.lastOrNull()?.url.orEmpty())
                .put("progress", meta?.progressPercent ?: -1)
                .put("resume_ms", meta?.resumePositionMs ?: 0L)
                .put("views_text", meta?.viewsText.orEmpty()))
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun clear(prefs: SharedPreferences) { prefs.edit().remove(KEY).apply() }
}
