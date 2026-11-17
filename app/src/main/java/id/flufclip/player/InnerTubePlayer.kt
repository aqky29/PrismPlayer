package id.flufclip.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Video tidak dapat diputar karena batasan akun/tier, bukan karena kesalahan jaringan. */
class PlaybackUnavailableException(message: String) : Exception(message)

/**
 * Resolver player InnerTube ber-autentikasi untuk video khusus member (dan fallback saat
 * NewPipe gagal). Client WEB dengan user-agent Safari mengembalikan HLS untuk sesi login,
 * sehingga ExoPlayer bisa memutar replay membership tanpa solver `n`/SABR.
 */
object InnerTubePlayer {
    private const val HOST = "https://www.youtube.com"
    internal const val AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)"
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).addNetworkInterceptor(accountHeaderInterceptor).build()
    private val jsonType = "application/json".toMediaType()
    private val versionRegex = Regex("\"INNERTUBE_CONTEXT_CLIENT_VERSION\":\"([^\"]+)\"")
    @Volatile private var clientVersion: String? = null

    private val lang: String get() = Locale.getDefault().toLanguageTag().substringBefore('-').ifBlank { "en" }
    private val country: String get() = Locale.getDefault().country.ifBlank { "US" }

    private suspend fun getVersion(): String = withContext(Dispatchers.IO) {
        clientVersion?.let { return@withContext it }
        try {
            val req = Request.Builder().url("$HOST?hl=$lang&gl=$country").header("User-Agent", AGENT).build()
            val html = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            val version = versionRegex.find(html)?.groupValues?.get(1) ?: "2.20261030.00.00"
            clientVersion = version
            version
        } catch (_: Exception) {
            "2.20261030.00.00"
        }
    }

    private fun context(version: String): JSONObject =
        JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", version)
                .put("userAgent", AGENT)
                .put("hl", lang)
                .put("gl", country)
        )

    private fun videoId(url: String): String? =
        Regex("(?:v=|youtu\\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)

    internal fun videoIdOf(url: String): String? = videoId(url)

    private class NextEntry(val json: String, val at: Long)
    private const val NEXT_TTL_MS = 10 * 60 * 1000L
    // Respons `next` dipakai bersama oleh rekomendasi (`related`) dan tombol Ask; kunci memuat status login karena isinya berbeda.
    private val nextCache = object : LinkedHashMap<String, NextEntry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NextEntry>): Boolean = size > 4
    }

    /** JSON mentah `next` untuk satu video (cache 10 menit, 4 video). [refresh] melewati cache. */
    internal suspend fun nextRaw(videoId: String, refresh: Boolean = false): String = withContext(Dispatchers.IO) {
        val key = "$videoId|${YouTubeAuth.sessionRevision}|${YouTubeAuth.isLoggedIn}"
        if (!refresh) synchronized(nextCache) { nextCache[key] }?.takeIf { System.currentTimeMillis() - it.at < NEXT_TTL_MS }?.let { return@withContext it.json }
        val version = getVersion()
        val body = JSONObject()
            .put("context", context(version))
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val text = execute("$HOST/youtubei/v1/next?prettyPrint=false", body.toString(), version)
        synchronized(nextCache) { nextCache[key] = NextEntry(text, System.currentTimeMillis()) }
        text
    }

    /** POST ke `/youtubei/v1/...` dengan konteks klien WEB yang sama dengan panggilan lain; [fields] digabung ke body. */
    internal suspend fun postRaw(route: String, fields: Map<String, Any>): String = withContext(Dispatchers.IO) {
        val version = getVersion()
        execute("$HOST$route?prettyPrint=false", JSONObject(fields).put("context", context(version)).toString(), version)
    }

    internal data class TrackingResponse(val tracking: JSONObject, val clientVersion: String)

    internal suspend fun playbackTracking(videoId: String, playlistId: String? = null): TrackingResponse = withContext(Dispatchers.IO) {
        val version = getVersion()
        val body = JSONObject().put("context", context(version)).put("videoId", videoId)
            .put("contentCheckOk", true).put("racyCheckOk", true)
        if (!playlistId.isNullOrBlank()) {
            body.put("playlistId", playlistId.removePrefix("VL"))
        }
        val tracking = JSONObject(execute("$HOST/youtubei/v1/player?prettyPrint=false", body.toString(), version))
            .optJSONObject("playbackTracking") ?: JSONObject()
        TrackingResponse(tracking, version)
    }

    /**
     * Resolve a playlist queue from `/youtubei/v1/next` (`playlistPanelRenderer`), used for Radio/Mix
     * playlists (`RD...`) or when a `watch?v=...&list=...` link is opened directly.
     */
    internal suspend fun playlistPanel(videoId: String?, playlistId: String): AccountPlaylistPage? = withContext(Dispatchers.IO) {
        val cleanPlaylistId = playlistId.removePrefix("VL").trim()
        if (cleanPlaylistId.isBlank()) return@withContext null
        val version = getVersion()
        val body = JSONObject()
            .put("context", context(version))
            .put("playlistId", cleanPlaylistId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        if (!videoId.isNullOrBlank() && videoId.length == 11) {
            body.put("videoId", videoId)
        }
        val json = JSONObject(execute("$HOST/youtubei/v1/next?prettyPrint=false", body.toString(), version))
        val panel = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnWatchNextResults")
            ?.optJSONObject("playlist")
            ?.optJSONObject("playlist") ?: return@withContext null
        val panelTitle = panel.optString("title").ifBlank { text(panel.optJSONObject("titleText")) }.ifBlank { "Playlist" }
        val owner = text(panel.optJSONObject("ownerName")).ifBlank { text(panel.optJSONObject("shortBylineText")) }
        val contents = panel.optJSONArray("contents") ?: return@withContext null
        val videos = mutableListOf<WatchedVideo>()
        for (i in 0 until contents.length()) {
            val renderer = contents.optJSONObject(i)?.optJSONObject("playlistPanelVideoRenderer") ?: continue
            val vid = renderer.optString("videoId").takeIf { it.length == 11 } ?: continue
            val vTitle = text(renderer.optJSONObject("title"))
            val vOwner = text(renderer.optJSONObject("shortBylineText")).ifBlank { text(renderer.optJSONObject("longBylineText")) }.ifBlank { owner }
            val durSec = parseDuration(text(renderer.optJSONObject("lengthText")))
            val thumbs = renderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
            videos.add(
                WatchedVideo(
                    url = "$HOST/watch?v=$vid",
                    title = vTitle.ifBlank { "Video" },
                    uploader = vOwner,
                    thumbnail = thumb,
                    position = 0L,
                    duration = durSec.coerceAtLeast(0L) * 1000L,
                    playlistId = cleanPlaylistId,
                    playlistTitle = panelTitle,
                    playlistVideoCount = "${contents.length()} videos"
                )
            )
        }
        if (videos.isEmpty()) return@withContext null
        val countLabel = "${videos.size} videos"
        WatchHistory.rememberPlaylistVideos(
            null,
            PlaylistRef(cleanPlaylistId, panelTitle, countLabel, owner),
            videos.map { it.url }
        )
        AccountPlaylistPage(
            videos = videos,
            continuation = null,
            header = AccountPlaylistHeader(
                owner = owner,
                privacy = "",
                count = countLabel,
                views = "",
                backgroundArgb = null,
                backgroundImage = videos.first().thumbnail.takeIf { it.isNotBlank() },
                title = panelTitle,
                thumbnail = videos.first().thumbnail
            )
        )
    }

    suspend fun resolve(url: String): StreamInfo = withContext(Dispatchers.IO) {
        val id = videoId(url) ?: throw IllegalArgumentException("Unrecognized video URL")
        val canonicalUrl = "$HOST/watch?v=$id"
        val version = getVersion()
        val body = JSONObject()
            .put("context", context(version))
            .put("videoId", id)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
        val json = JSONObject(execute("$HOST/youtubei/v1/player?prettyPrint=false", body.toString(), version))

        val status = json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
        if (status != "OK") {
            val reason = json.optJSONObject("playabilityStatus")?.optString("reason").orEmpty()
            throw PlaybackUnavailableException(reason.ifBlank { "Video cannot be played ($status)" })
        }

        val details = json.optJSONObject("videoDetails") ?: JSONObject()
        val title = details.optString("title").ifBlank { "Video" }
        val liveNow = details.optBoolean("isLive") ||
            json.optJSONObject("microformat")?.optJSONObject("playerMicroformatRenderer")
                ?.optJSONObject("liveBroadcastDetails")?.optBoolean("isLiveNow") == true
        val wasLive = details.optBoolean("isLiveContent")
        val streamType = when {
            liveNow -> StreamType.LIVE_STREAM
            wasLive -> StreamType.POST_LIVE_STREAM
            else -> StreamType.VIDEO_STREAM
        }
        val info = StreamInfo(
            ServiceList.YouTube.serviceId,
            canonicalUrl,
            canonicalUrl,
            streamType,
            id,
            title,
            0
        ).apply {
            uploaderName = details.optString("author")
            val channelId = details.optString("channelId")
            if (channelId.isNotBlank()) uploaderUrl = "$HOST/channel/$channelId"
            viewCount = details.optString("viewCount").toLongOrNull() ?: -1L
            duration = if (liveNow) 0L else details.optLong("lengthSeconds")
            val thumbs = details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
            if (thumb.isNotBlank()) thumbnails = listOf(Image(thumb, 1280, 720, Image.ResolutionLevel.HIGH))
        }

        val streaming = json.optJSONObject("streamingData")
            ?: throw PlaybackUnavailableException("YouTube did not return streaming data for this video.")
        val hls = streaming.optString("hlsManifestUrl")
        if (hls.isNotBlank()) info.hlsUrl = hls
        val dash = streaming.optString("dashManifestUrl")
        if (dash.isNotBlank()) info.dashMpdUrl = dash

        val progressive = mutableListOf<VideoStream>()
        val videoOnly = mutableListOf<VideoStream>()
        val audio = mutableListOf<AudioStream>()
        var nSolverWorks: Boolean? = null

        fun solve(rawUrl: String): String {
            if (hls.isNotBlank()) return rawUrl
            if (!rawUrl.contains("n=")) return rawUrl
            if (nSolverWorks == false) return rawUrl
            return try {
                val solved = YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(id, rawUrl)
                nSolverWorks = true
                solved
            } catch (_: Exception) {
                nSolverWorks = false
                rawUrl
            }
        }

        fun handle(formats: JSONArray?) {
            if (hls.isNotBlank()) return
            if (formats == null) return
            for (i in 0 until formats.length()) {
                val format = formats.optJSONObject(i) ?: continue
                val rawUrl = format.optString("url")
                if (rawUrl.isBlank()) continue
                val mime = format.optString("mimeType")
                if (mime.isBlank()) continue
                val contentUrl = solve(rawUrl)
                val formatId = format.optString("itag").takeIf { it.isNotBlank() } ?: "innertube-$i"
                val itagId = format.optInt("itag", -1)
                val contentLength = format.optString("contentLength").toLongOrNull() ?: -1L
                val bitrateBps = format.optInt("bitrate", 0).takeIf { it > 0 }
                    ?: format.optInt("averageBitrate", 0).coerceAtLeast(0)
                val formatType = if (mime.contains("webm")) MediaFormat.WEBM else MediaFormat.MPEG_4
                when {
                    mime.startsWith("video/") -> {
                        val hasAudio = format.has("audioQuality") || mime.contains("mp4a") || mime.contains("opus")
                        val resolution = format.optString("qualityLabel").ifBlank {
                            val height = format.optInt("height")
                            if (height > 0) "${height}p" else VideoStream.RESOLUTION_UNKNOWN
                        }
                        val itagItem = buildVideoItagItem(
                            itagId = itagId,
                            hasAudio = hasAudio,
                            mediaFormat = formatType,
                            resolution = resolution,
                            width = format.optInt("width"),
                            height = format.optInt("height"),
                            fps = format.optInt("fps"),
                            bitrateBps = bitrateBps,
                            contentLength = contentLength,
                        )
                        val stream = VideoStream.Builder()
                            .setId(formatId)
                            .setContent(contentUrl, true)
                            .setIsVideoOnly(!hasAudio)
                            .setResolution(resolution)
                            .setMediaFormat(formatType)
                            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                            .setItagItem(itagItem)
                            .build()
                        if (hasAudio) progressive.add(stream) else videoOnly.add(stream)
                    }
                    mime.startsWith("audio/") -> {
                        val audioFormat = if (mime.contains("webm")) MediaFormat.WEBMA_OPUS else MediaFormat.M4A
                        val avgKbps = ((format.optInt("averageBitrate").takeIf { it > 0 } ?: bitrateBps) / 1000)
                            .takeIf { it > 0 } ?: AudioStream.UNKNOWN_BITRATE
                        val itagItem = buildAudioItagItem(
                            itagId = itagId,
                            mediaFormat = audioFormat,
                            avgBitrateKbps = avgKbps,
                            bitrateBps = bitrateBps,
                            contentLength = contentLength,
                        )
                        audio.add(
                            AudioStream.Builder()
                                .setId(formatId)
                                .setContent(contentUrl, true)
                                .setAverageBitrate(avgKbps)
                                .setMediaFormat(audioFormat)
                                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                                .setItagItem(itagItem)
                                .withAudioMetadata(innerTubeAudioMetadata(format))
                                .build()
                        )
                    }
                }
            }
        }

        handle(streaming.optJSONArray("formats"))
        handle(streaming.optJSONArray("adaptiveFormats"))

        info.videoStreams = progressive.distinctBy { it.content }
        info.videoOnlyStreams = videoOnly.distinctBy { it.content }
        info.audioStreams = audio.distinctBy { it.content }

        if (info.hlsUrl.isNullOrBlank() && info.dashMpdUrl.isNullOrBlank() && info.videoStreams.isEmpty() && info.videoOnlyStreams.isEmpty() && info.audioStreams.isEmpty()) {
            throw PlaybackUnavailableException("YouTube did not return a playable format for this video.")
        }
        info
    }

    /** Rekomendasi watch page dari `/next`; dipakai karena StreamInfo hasil yt-dlp tidak punya relatedItems. */
    suspend fun related(videoId: String, limit: Int = 20): List<StreamInfoItem> = withContext(Dispatchers.IO) {
        val json = JSONObject(nextRaw(videoId))
        val out = mutableListOf<StreamInfoItem>()
        val seen = mutableSetOf<String>()

        fun accept(item: StreamInfoItem?) {
            if (item != null && seen.add(item.url)) out.add(item)
        }

        fun visit(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
                is JSONObject -> {
                    node.optJSONObject("lockupViewModel")?.let { accept(HomeFeed.parseLockupViewModel(it)) }
                    node.optJSONObject("compactVideoRenderer")?.let { accept(parseCompactVideoRenderer(it)) }
                    for (key in node.keys()) {
                        if (key == "lockupViewModel" || key == "compactVideoRenderer") continue
                        visit(node.opt(key))
                    }
                }
            }
        }
        visit(json)
        out.take(limit)
    }

    private fun parseCompactVideoRenderer(video: JSONObject): StreamInfoItem? {
        val id = video.optString("videoId").takeIf { it.length == 11 } ?: return null
        val title = text(video.optJSONObject("title"))
        if (title.isBlank()) return null
        val byline = video.optJSONObject("shortBylineText") ?: video.optJSONObject("longBylineText")
        val runs = byline?.optJSONArray("runs")
        val uploader = runs?.optJSONObject(0)?.optString("text").orEmpty()
        val channelUrl = runs?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            ?.optString("canonicalBaseUrl")
            ?.let { if (it.startsWith("/")) "$HOST$it" else it }
        val thumbUrl = video.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
        val avatarUrl = video.optJSONObject("channelThumbnail")?.optJSONArray("thumbnails")
            ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
        val live = isLiveRenderer(video)
        val duration = if (live) 0L else parseDuration(video.optJSONObject("lengthText")?.optString("simpleText").orEmpty())
        val views = video.optJSONObject("shortViewCountText")?.optString("simpleText")
            ?: video.optJSONObject("viewCountText")?.optString("simpleText")

        return StreamInfoItem(
            ServiceList.YouTube.serviceId,
            "$HOST/watch?v=$id",
            title,
            if (live) StreamType.LIVE_STREAM else StreamType.VIDEO_STREAM
        ).apply {
            uploaderName = uploader
            uploaderUrl = channelUrl
            this.duration = duration
            viewCount = parseViews(views)
            textualUploadDate = video.optJSONObject("publishedTimeText")?.optString("simpleText").orEmpty()
            if (!thumbUrl.isNullOrBlank()) thumbnails = listOf(Image(thumbUrl, 1280, 720, Image.ResolutionLevel.HIGH))
            if (!avatarUrl.isNullOrBlank()) uploaderAvatars = listOf(Image(avatarUrl, 88, 88, Image.ResolutionLevel.LOW))
        }
    }

    /** Badge LIVE dari renderer ringkas; dipakai agar item live tidak bergantung pada heuristik teks. */
    private fun isLiveRenderer(video: JSONObject): Boolean {
        val badges = video.optJSONArray("badges")
        if (badges != null) for (i in 0 until badges.length()) {
            val badge = badges.optJSONObject(i)?.optJSONObject("metadataBadgeRenderer") ?: continue
            if (badge.optString("label").equals("LIVE", true) ||
                badge.optString("style").equals("BADGE_STYLE_TYPE_LIVE_NOW", true)
            ) return true
        }
        val overlays = video.optJSONArray("thumbnailOverlays")
        if (overlays != null) for (i in 0 until overlays.length()) {
            val status = overlays.optJSONObject(i)?.optJSONObject("thumbnailOverlayTimeStatusRenderer") ?: continue
            if (status.optString("style").equals("LIVE", true)) return true
        }
        return false
    }

    private fun text(obj: JSONObject?): String {
        if (obj == null) return ""
        obj.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = obj.optJSONArray("runs") ?: return ""
        return buildString {
            for (i in 0 until runs.length()) append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }.trim()
    }

    private fun parseDuration(text: String): Long {
        if (text.isBlank()) return 0L
        val parts = text.replace('.', ':').split(':')
        var sec = 0L
        for (part in parts) sec = sec * 60 + (part.toLongOrNull() ?: 0L)
        return sec
    }

    private fun parseViews(text: String?): Long {
        if (text.isNullOrBlank()) return -1L
        val clean = text.lowercase(Locale.ROOT)
        val number = Regex("[0-9]+(?:[.,][0-9]+)?").find(clean)?.value?.replace(',', '.')?.toDoubleOrNull() ?: return -1L
        return when {
            clean.contains("miliar") || clean.contains("b") -> (number * 1_000_000_000).toLong()
            clean.contains("jt") || clean.contains("m") -> (number * 1_000_000).toLong()
            clean.contains("rb") || clean.contains("k") -> (number * 1_000).toLong()
            else -> number.toLong()
        }
    }

    private fun execute(url: String, body: String, version: String): String {
        val builder = Request.Builder().url(url)
            .header("User-Agent", AGENT)
            .header("Accept-Language", "$lang-$country,$lang;q=0.9,en;q=0.8")
            .header("Content-Type", "application/json")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", version)
            .post(body.toRequestBody(jsonType))

        YouTubeAuth.buildAuthHeaders().forEach { (key, value) -> builder.header(key, value) }
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("YouTube player error: ${response.code}")
            response.body?.string().orEmpty().ifBlank { throw IllegalStateException("Empty player response") }
        }
    }
}
