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
    private const val ENTRIES_KEY = "entries"
    private const val LIMIT = 40

    fun read(prefs: SharedPreferences): List<StreamInfoItem> = runCatching {
        val items = JSONArray(prefs.getString(KEY, "[]"))
        (0 until minOf(items.length(), LIMIT)).mapNotNull { index ->
            decodeStreamItem(items.optJSONObject(index))
        }
    }.getOrDefault(emptyList())

    fun readEntries(prefs: SharedPreferences): List<HomeEntry> = runCatching {
        val raw = prefs.getString(ENTRIES_KEY, null) ?: return@runCatching read(prefs).map { HomeEntry.Video(it) }
        val entries = JSONArray(raw)
        (0 until minOf(entries.length(), LIMIT)).mapNotNull { index ->
            val json = entries.optJSONObject(index) ?: return@mapNotNull null
            when (json.optString("kind")) {
                "shorts_shelf" -> {
                    val cardsJson = json.optJSONArray("cards") ?: return@mapNotNull null
                    val cards = (0 until cardsJson.length()).mapNotNull { cIdx ->
                        val c = cardsJson.optJSONObject(cIdx) ?: return@mapNotNull null
                        val videoId = c.optString("videoId").takeIf { it.length == 11 } ?: return@mapNotNull null
                        ShortsCard(
                            videoId = videoId,
                            title = c.optString("title"),
                            viewsText = c.optString("viewsText"),
                            thumbnailUrl = c.optString("thumbnailUrl"),
                            badgeText = c.optString("badgeText"),
                            sequenceParams = c.optString("sequenceParams")
                        )
                    }
                    if (cards.isEmpty()) null else HomeEntry.ShortsShelf(cards, json.optString("title").ifBlank { "Shorts" })
                }
                else -> decodeStreamItem(json)?.let { HomeEntry.Video(it) }
            }
        }
    }.getOrElse { read(prefs).map { HomeEntry.Video(it) } }

    private fun decodeStreamItem(json: JSONObject?): StreamInfoItem? {
        if (json == null) return null
        val url = json.optString("url")
        val title = json.optString("title")
        if (url.isBlank() || title.isBlank()) return null
        val type = runCatching { StreamType.valueOf(json.optString("type")) }.getOrDefault(StreamType.VIDEO_STREAM)
        // Siaran live cepat berakhir; entri lama yang masih bertanda live tidak dipakai dari cache.
        if (type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM) return null
        return StreamInfoItem(json.optInt("service", 0), url, title, type).apply {
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

    private fun encodeStreamItem(item: StreamInfoItem): JSONObject {
        val meta = HomeFeed.metaOf(item.url)
        return JSONObject().put("service", item.serviceId).put("url", item.url).put("title", item.name)
            .put("type", item.streamType.name).put("uploader", item.uploaderName.orEmpty())
            .put("channel", item.uploaderUrl.orEmpty()).put("views", item.viewCount).put("duration", item.duration)
            .put("date", item.textualUploadDate.orEmpty()).put("thumbnail", item.thumbnails.lastOrNull()?.url.orEmpty())
            .put("avatar", item.uploaderAvatars.lastOrNull()?.url.orEmpty())
            .put("progress", meta?.progressPercent ?: -1)
            .put("resume_ms", meta?.resumePositionMs ?: 0L)
            .put("views_text", meta?.viewsText.orEmpty())
    }

    fun save(prefs: SharedPreferences, items: List<StreamInfoItem>) {
        if (items.isEmpty()) return
        val json = JSONArray()
        items.filterNot { it.isOngoingLive() }.take(LIMIT).forEach { item ->
            json.put(encodeStreamItem(item))
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    fun saveEntries(prefs: SharedPreferences, entries: List<HomeEntry>) {
        if (entries.isEmpty()) return
        val videos = entries.filterIsInstance<HomeEntry.Video>().map { it.item }
        val itemsJson = JSONArray()
        videos.filterNot { it.isOngoingLive() }.take(LIMIT).forEach { itemsJson.put(encodeStreamItem(it)) }

        val entriesJson = JSONArray()
        entries.take(LIMIT).forEach { entry ->
            when (entry) {
                is HomeEntry.Video -> if (!entry.item.isOngoingLive()) {
                    entriesJson.put(encodeStreamItem(entry.item).put("kind", "video"))
                }
                is HomeEntry.ShortsShelf -> {
                    val cardsJson = JSONArray()
                    entry.items.forEach { c ->
                        cardsJson.put(
                            JSONObject()
                                .put("videoId", c.videoId)
                                .put("title", c.title)
                                .put("viewsText", c.viewsText)
                                .put("thumbnailUrl", c.thumbnailUrl)
                                .put("badgeText", c.badgeText)
                                .put("sequenceParams", c.sequenceParams)
                        )
                    }
                    entriesJson.put(
                        JSONObject()
                            .put("kind", "shorts_shelf")
                            .put("title", entry.title)
                            .put("cards", cardsJson)
                    )
                }
            }
        }
        prefs.edit()
            .putString(KEY, itemsJson.toString())
            .putString(ENTRIES_KEY, entriesJson.toString())
            .apply()
    }

    fun clear(prefs: SharedPreferences) { prefs.edit().remove(KEY).remove(ENTRIES_KEY).apply() }
}
