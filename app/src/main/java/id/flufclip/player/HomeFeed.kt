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
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Satu chip kategori Home dari server; endpoint-nya harus dipakai apa adanya. */
internal data class HomeChip(
    val title: String,
    val token: String? = null,
    val params: String? = null,
    val selected: Boolean = false
) {
    val hasEndpoint: Boolean get() = !token.isNullOrBlank() || !params.isNullOrBlank()
}

internal data class HomeFeedPage(
    val items: List<StreamInfoItem>,
    val continuationToken: String?,
    val chips: List<HomeChip> = emptyList()
)

/** Metadata kartu Langganan yang tidak muat di StreamInfoItem. Teks diambil apa adanya dari server. */
internal data class VideoMeta(
    val viewsText: String = "",
    val publishedText: String = "",
    val watchingText: String = "",
    val membersOnly: Boolean = false,
    val verified: Boolean = false,
    val live: Boolean = false,
    val progressPercent: Int = -1,
    val resumePositionMs: Long = 0L,
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val playlistVideoCountText: String? = null,
    val isPlaylist: Boolean = false,
)

internal data class ShortsCard(
    val videoId: String,
    val title: String,
    val viewsText: String,
    val thumbnailUrl: String
)

internal data class SubsChannel(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val url: String
)

internal sealed interface SubsEntry {
    data class Video(val item: StreamInfoItem, val meta: VideoMeta) : SubsEntry
    data class ShortsShelf(val items: List<ShortsCard>) : SubsEntry
}

internal data class SubsPage(
    val entries: List<SubsEntry>,
    val continuationToken: String?
)

/**
 * Pengambil Beranda personal resmi YouTube melalui InnerTube API (`FEwhat_to_watch`).
 * Digunakan ketika akun pengguna sudah login sehingga susunan video mengikuti rekomendasi
 * server YouTube (Two-Tower DNN & MMoE Multitask). Jika tidak login atau server mengembalikan
 * `feedNudgeRenderer`, aplikasi secara aman fallback ke feed Kiosk/pencarian lokal.
 *
 * Chip kategori (Game, Musik, Live, ...) juga berasal dari respons server. Jangan memakai
 * pencarian kata untuk chip: endpoint tiap chip adalah continuation/params milik YouTube.
 */
internal object HomeFeed {
    private const val HOST = "https://www.youtube.com"
    private const val AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val safe = if (permitsAccountHeaders(request.url)) request else request.newBuilder().apply {
                sensitiveAccountHeaders.forEach { removeHeader(it) }
            }.build()
            chain.proceed(safe)
        }.build()
    private val jsonType = "application/json".toMediaType()
    private val versionRegex = Regex("\"INNERTUBE_CONTEXT_CLIENT_VERSION\":\"([^\"]+)\"")
    @Volatile private var clientVersion: String? = null
    private val metaByUrl = java.util.concurrent.ConcurrentHashMap<String, VideoMeta>()
    private val channelIdByUrl = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val discoveredChannels = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    internal fun metaOf(url: String): VideoMeta? {
        metaByUrl[url]?.let { return it }
        val vid = videoKey(url)
        if (vid.length == 11) {
            metaByUrl["$HOST/watch?v=$vid"]?.let { return it }
        }
        return null
    }

    internal fun rememberMeta(url: String, meta: VideoMeta) {
        metaByUrl[url] = meta
        val vid = videoKey(url)
        if (vid.length == 11) {
            metaByUrl["$HOST/watch?v=$vid"] = meta
        }
    }

    internal fun channelIdOf(urlOrId: String?): String? {
        if (urlOrId.isNullOrBlank()) return null
        val trimmed = urlOrId.trim()
        if (trimmed.startsWith("UC") && trimmed.length >= 20) return trimmed
        Regex("/channel/(UC[A-Za-z0-9_-]{20,})").find(trimmed)?.groupValues?.get(1)?.let { return it }
        return channelIdByUrl[trimmed]
    }

    internal fun pickThumbnailUrl(sources: JSONArray?, preferredWidth: Int = 720): String? {
        if (sources == null || sources.length() == 0) return null
        var bestUrl: String? = null
        var bestDiff = Int.MAX_VALUE
        for (i in 0 until sources.length()) {
            val obj = sources.optJSONObject(i) ?: continue
            val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
            val w = obj.optInt("width", 0)
            if (w <= 0) {
                if (bestUrl == null) bestUrl = url
                continue
            }
            val diff = kotlin.math.abs(w - preferredWidth)
            if (diff < bestDiff) {
                bestDiff = diff
                bestUrl = url
            }
        }
        return bestUrl ?: sources.optJSONObject(sources.length() - 1)?.optString("url")
    }

    private val lang: String get() = Locale.getDefault().toLanguageTag().substringBefore('-').ifBlank { "en" }
    private val country: String get() = Locale.getDefault().country.ifBlank { "US" }

    private fun context(version: String): JSONObject =
        JSONObject().put("client", JSONObject().put("clientName", "WEB").put("clientVersion", version).put("hl", lang).put("gl", country))

    private suspend fun getVersion(): String = withContext(Dispatchers.IO) {
        clientVersion?.let { return@withContext it }
        try {
            val req = Request.Builder().url("$HOST?hl=$lang&gl=$country").header("User-Agent", AGENT).build()
            val html = client.newCall(req).execute().use { it.body?.string().orEmpty() }
            val v = versionRegex.find(html)?.groupValues?.get(1) ?: "2.20261030.00.00"
            clientVersion = v
            v
        } catch (_: Exception) {
            "2.20261030.00.00"
        }
    }

    suspend fun start(expectedRevision: Int = YouTubeAuth.sessionRevision): HomeFeedPage = withContext(Dispatchers.IO) {
        val response = accountRequest("browse", JSONObject().put("browseId", "FEwhat_to_watch"), expectedRevision)
        parsePage(response, isContinuation = false)
    }

    suspend fun subscriptions(expectedRevision: Int = YouTubeAuth.sessionRevision): SubsPage = withContext(Dispatchers.IO) {
        val response = accountRequest("browse", JSONObject().put("browseId", "FEsubscriptions"), expectedRevision)
        parseSubs(response, isContinuation = false)
    }

    internal suspend fun historyResponse(continuation: String? = null, expectedRevision: Int = YouTubeAuth.sessionRevision): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject()
        if (continuation == null) body.put("browseId", "FEhistory") else body.put("continuation", continuation)
        accountRequest("browse", body, expectedRevision)
    }

    /** Account-only requests must never fall back to a signed-out response. */
    internal suspend fun accountRequest(endpoint: String, fields: JSONObject, expectedRevision: Int? = null): JSONObject {
        check(YouTubeAuth.isLoggedIn) { "Sign in to use your YouTube library" }
        val revision = YouTubeAuth.sessionRevision
        check(expectedRevision == null || expectedRevision == revision) { "Account changed" }
        val headers = YouTubeAuth.buildAuthHeaders()
        check(headers.isNotEmpty() && revision == YouTubeAuth.sessionRevision && YouTubeAuth.isLoggedIn) { "Account changed" }
        return withContext(Dispatchers.IO) {
        val version = getVersion()
        check(revision == YouTubeAuth.sessionRevision && YouTubeAuth.isLoggedIn) { "Account changed" }
        val body = JSONObject().put("context", context(version))
        fields.keys().forEach { body.put(it, fields.get(it)) }
        val response = JSONObject(execute("$HOST/youtubei/v1/$endpoint?prettyPrint=false", body.toString(), version, headers))
        check(revision == YouTubeAuth.sessionRevision && YouTubeAuth.isLoggedIn) { "Account changed" }
        response.optJSONObject("error")?.let { error(it.optString("message", "YouTube request failed")) }
        response
        }
    }

    /** Public or signed-in request (e.g., public playlists VLPL... or channel shelves). */
    internal suspend fun publicOrAccountRequest(endpoint: String, fields: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val headers = if (YouTubeAuth.isLoggedIn) YouTubeAuth.buildAuthHeaders().takeIf { it.isNotEmpty() } else null
        val version = getVersion()
        val body = JSONObject().put("context", context(version))
        fields.keys().forEach { body.put(it, fields.get(it)) }
        val response = JSONObject(execute("$HOST/youtubei/v1/$endpoint?prettyPrint=false", body.toString(), version, headers))
        response.optJSONObject("error")?.let { error(it.optString("message", "YouTube request failed")) }
        response
    }

    /**
     * Scan a channel's home browse response for playlist shelves (`shelfRenderer` with `VLPL...`
     * endpoint or `lockupViewModel` playlists) and register their video IDs so History entries
     * from that channel can be associated with their playlist.
     */
    internal suspend fun discoverChannelPlaylists(
        channelUrlOrId: String,
        prefs: android.content.SharedPreferences? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val channelId = channelIdOf(channelUrlOrId) ?: return@withContext false
        if (!discoveredChannels.add(channelId)) return@withContext false
        val json = try {
            publicOrAccountRequest("browse", JSONObject().put("browseId", channelId))
        } catch (_: Exception) {
            discoveredChannels.remove(channelId)
            return@withContext false
        }
        var foundAny = false
        val channelTitle = json.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")?.optString("title").orEmpty()
        fun scan(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) scan(node.opt(i))
                is JSONObject -> {
                    node.optJSONObject("shelfRenderer")?.let { shelf ->
                        val browseId = shelf.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                        val pid = browseId.removePrefix("VL").takeIf { it.startsWith("PL") || it.startsWith("OL") }
                        val shelfTitle = text(shelf.optJSONObject("title"))
                        if (!pid.isNullOrBlank() && shelfTitle.isNotBlank()) {
                            val videoIds = mutableListOf<String>()
                            fun collectIds(sub: Any?) {
                                when (sub) {
                                    is JSONArray -> for (i in 0 until sub.length()) collectIds(sub.opt(i))
                                    is JSONObject -> {
                                        sub.optJSONObject("lockupViewModel")?.let { lk ->
                                            val cid = lk.optString("contentId")
                                            if (cid.length == 11) videoIds.add(cid)
                                        }
                                        sub.optJSONObject("videoRenderer")?.optString("videoId")?.takeIf { it.length == 11 }?.let { videoIds.add(it) }
                                        sub.optJSONObject("gridVideoRenderer")?.optString("videoId")?.takeIf { it.length == 11 }?.let { videoIds.add(it) }
                                        for (k in sub.keys()) collectIds(sub.opt(k))
                                    }
                                }
                            }
                            collectIds(shelf.opt("content"))
                            if (videoIds.isNotEmpty()) {
                                val countLabel = "${videoIds.distinct().size} videos"
                                WatchHistory.rememberPlaylistVideos(
                                    prefs,
                                    PlaylistRef(pid, shelfTitle, countLabel, channelTitle),
                                    videoIds
                                )
                                foundAny = true
                            }
                        }
                    }
                    node.optJSONObject("lockupViewModel")?.let { lockup ->
                        if (lockup.optString("contentType") in setOf("LOCKUP_CONTENT_TYPE_PLAYLIST", "LOCKUP_CONTENT_TYPE_PODCAST")) {
                            if (parseLockup(lockup) != null) foundAny = true
                        }
                    }
                    for (k in node.keys()) {
                        if (k != "trackingParams" && k != "frameworkUpdates") scan(node.opt(k))
                    }
                }
            }
        }
        scan(json.opt("contents"))
        foundAny
    }

    suspend fun moreSubscriptions(token: String, expectedRevision: Int = YouTubeAuth.sessionRevision): SubsPage = withContext(Dispatchers.IO) {
        val response = accountRequest("browse", JSONObject().put("continuation", token), expectedRevision)
        parseSubs(response, isContinuation = true)
    }

    /** Daftar channel yang diikuti (FEchannels), dipakai rail kiri layar Langganan. */
    suspend fun channels(expectedRevision: Int = YouTubeAuth.sessionRevision): List<SubsChannel> = withContext(Dispatchers.IO) {
        val response = accountRequest("browse", JSONObject().put("browseId", "FEchannels"), expectedRevision)
        parseChannels(response)
    }

    /**
     * Video khusus member sebuah channel lewat playlist otomatis `UUMO<channelId tanpa UC>`.
     * Playlist inilah yang dipakai tab Membership halaman channel, dan isinya tidak dibatasi
     * 112 item feed Langganan (respons WEB FEsubscriptions tidak membawa chip Membership).
     */
    suspend fun membershipVideos(channelId: String, expectedRevision: Int = YouTubeAuth.sessionRevision): List<SubsEntry.Video> = withContext(Dispatchers.IO) {
        val clean = channelId.trim()
        if (!clean.startsWith("UC") || clean.length <= 2) return@withContext emptyList()
        val body = JSONObject()
            .put("browseId", "VLUUMO" + clean.removePrefix("UC"))
        parseMembershipPlaylist(accountRequest("browse", body, expectedRevision))
    }

    private fun parseMembershipPlaylist(json: JSONObject): List<SubsEntry.Video> {
        val content = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")?.optJSONObject("content")
            ?: return emptyList()
        val videos = mutableListOf<SubsEntry.Video>()
        val seen = mutableSetOf<String>()

        fun visit(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
                is JSONObject -> {
                    node.optJSONObject("lockupViewModel")?.let { lockup ->
                        parseLockup(lockup)?.let { parsed ->
                            // Seluruh isi playlist UUMO adalah konten member, termasuk item yang metadata
                            // badge-nya tidak menyertakan "Members only".
                            if (seen.add(parsed.item.url)) videos.add(SubsEntry.Video(parsed.item, parsed.meta.copy(membersOnly = true)))
                        }
                    }
                    for (key in node.keys()) if (key != "lockupViewModel") visit(node.opt(key))
                }
            }
        }

        visit(content)
        return videos
    }

    suspend fun more(token: String, expectedRevision: Int = YouTubeAuth.sessionRevision): HomeFeedPage = withContext(Dispatchers.IO) {
        val response = accountRequest("browse", JSONObject().put("continuation", token), expectedRevision)
        parsePage(response, isContinuation = true)
    }

    /** Muat feed milik satu chip. Token/params diambil dari respons chip, bukan dibuat sendiri. */
    suspend fun chip(chip: HomeChip, expectedRevision: Int = YouTubeAuth.sessionRevision): HomeFeedPage = withContext(Dispatchers.IO) {
        val body = JSONObject()
        when {
            !chip.token.isNullOrBlank() -> body.put("continuation", chip.token)
            !chip.params.isNullOrBlank() -> body.put("browseId", "FEwhat_to_watch").put("params", chip.params)
            else -> return@withContext HomeFeedPage(emptyList(), null, emptyList())
        }
        parsePage(accountRequest("browse", body, expectedRevision), isContinuation = true)
    }

    private fun parsePage(json: JSONObject, isContinuation: Boolean): HomeFeedPage {
        val items = mutableListOf<StreamInfoItem>()
        val seen = mutableSetOf<String>()
        val holder = TokenHolder()
        val roots = if (isContinuation) continuationItems(json) ?: initialContents(json) else initialContents(json)
        if (roots != null) collect(roots, items, seen, holder)
        val next = directContinuationToken(roots) ?: findContinuationToken(json)
        return HomeFeedPage(items, next, collectChips(json))
    }

    private fun directContinuationToken(node: Any?): String? {
        if (node !is JSONArray) return null
        for (i in 0 until node.length()) {
            val token = node.optJSONObject(i)?.optJSONObject("continuationItemRenderer")
                ?.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")?.optString("token")
            if (!token.isNullOrBlank()) return token
        }
        return null
    }

    private fun initialContents(json: JSONObject): JSONArray? =
        json.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")?.optJSONObject("richGridRenderer")
            ?.optJSONArray("contents")

    private fun continuationItems(json: JSONObject): JSONArray? {
        val actions = json.optJSONArray("onResponseReceivedActions")
        if (actions != null) {
            for (i in 0 until actions.length()) {
                val action = actions.optJSONObject(i) ?: continue
                action.optJSONObject("reloadContinuationItemsCommand")?.optJSONArray("continuationItems")?.let { return it }
                action.optJSONObject("appendContinuationItemsAction")?.optJSONArray("continuationItems")?.let { return it }
            }
        }
        return json.optJSONObject("continuationContents")
            ?.optJSONObject("richGridContinuation")?.optJSONArray("contents")
            ?: json.optJSONObject("continuationContents")
                ?.optJSONObject("sectionListContinuation")?.optJSONArray("contents")
    }

    private class TokenHolder(var value: String? = null)

    private fun collect(node: Any?, items: MutableList<StreamInfoItem>, seen: MutableSet<String>, token: TokenHolder) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), items, seen, token)
            is JSONObject -> {
                node.optJSONObject("continuationItemRenderer")?.let { continuation ->
                    continuation.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")?.optString("token")
                        ?.takeIf { it.isNotBlank() && token.value == null }
                        ?.let { token.value = it }
                }
                node.optJSONObject("richItemRenderer")?.optJSONObject("content")?.let { addContent(it, items, seen) }
                node.optJSONObject("lockupViewModel")?.let { addLockup(it, items, seen) }
                node.optJSONObject("videoRenderer")?.let { addVideo(it, items, seen) }
                for (key in node.keys()) {
                    when (key) {
                        "continuationItemRenderer", "richItemRenderer", "lockupViewModel", "videoRenderer" -> Unit
                        else -> collect(node.opt(key), items, seen, token)
                    }
                }
            }
        }
    }

    private fun addContent(content: JSONObject, items: MutableList<StreamInfoItem>, seen: MutableSet<String>) {
        val item = content.optJSONObject("videoRenderer")?.let { parseVideoRenderer(it) }
            ?: content.optJSONObject("lockupViewModel")?.let { parseLockupViewModel(it) }
        addItem(item, items, seen)
    }

    private fun addLockup(lockup: JSONObject, items: MutableList<StreamInfoItem>, seen: MutableSet<String>) =
        addItem(parseLockupViewModel(lockup), items, seen)

    private fun addVideo(video: JSONObject, items: MutableList<StreamInfoItem>, seen: MutableSet<String>) =
        addItem(parseVideoRenderer(video), items, seen)

    private fun addItem(item: StreamInfoItem?, items: MutableList<StreamInfoItem>, seen: MutableSet<String>) {
        if (item == null) return
        if (seen.add(item.url)) items.add(item)
    }

    private fun findContinuationToken(node: Any?): String? {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) findContinuationToken(node.opt(i))?.let { return it }
            is JSONObject -> {
                node.optJSONObject("continuationItemRenderer")?.let { continuation ->
                    val token = continuation.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")?.optString("token")
                    if (!token.isNullOrBlank()) return token
                }
                for (key in node.keys()) findContinuationToken(node.opt(key))?.let { return it }
            }
        }
        return null
    }

    /** Langganan mempertahankan urutan section server: video lockup dan shelf Shorts. */
    private fun parseSubs(json: JSONObject, isContinuation: Boolean): SubsPage {
        val roots = if (isContinuation) continuationItems(json) ?: initialContents(json) else initialContents(json)
        val entries = mutableListOf<SubsEntry>()
        val seenVideos = mutableSetOf<String>()
        val seenShorts = mutableSetOf<String>()
        var next: String? = null

        fun addVideo(lockup: JSONObject) {
            val parsed = parseLockup(lockup) ?: return
            if (seenVideos.add(parsed.item.url)) entries.add(SubsEntry.Video(parsed.item, parsed.meta))
        }

        fun addShorts(cards: List<ShortsCard>) {
            val fresh = cards.filter { seenShorts.add(it.videoId) }
            if (fresh.isNotEmpty()) entries.add(SubsEntry.ShortsShelf(fresh))
        }

        fun addContent(content: JSONObject) {
            content.optJSONObject("lockupViewModel")?.let { addVideo(it) }
            content.optJSONObject("shortsLockupViewModel")?.let { card ->
                parseShortsCard(card)?.let { addShorts(listOf(it)) }
            }
        }

        fun visitShelf(shelf: JSONObject) {
            val contents = shelf.optJSONArray("contents") ?: return
            val shorts = mutableListOf<ShortsCard>()
            for (i in 0 until contents.length()) {
                val content = contents.optJSONObject(i)?.optJSONObject("richItemRenderer")?.optJSONObject("content") ?: continue
                val card = content.optJSONObject("shortsLockupViewModel")
                if (card != null) {
                    parseShortsCard(card)?.let { shorts.add(it) }
                } else {
                    if (shorts.isNotEmpty()) { addShorts(shorts.toList()); shorts.clear() }
                    addContent(content)
                }
            }
            if (shorts.isNotEmpty()) addShorts(shorts.toList())
        }

        if (roots != null) {
            for (i in 0 until roots.length()) {
                val node = roots.optJSONObject(i) ?: continue
                node.optJSONObject("continuationItemRenderer")?.optJSONObject("continuationEndpoint")
                    ?.optJSONObject("continuationCommand")?.optString("token")
                    ?.takeIf { it.isNotBlank() && next == null }
                    ?.let { next = it }
                node.optJSONObject("richItemRenderer")?.optJSONObject("content")?.let { addContent(it) }
                node.optJSONObject("richSectionRenderer")?.optJSONObject("content")?.let { content ->
                    content.optJSONObject("richShelfRenderer")?.let { visitShelf(it) }
                    content.optJSONObject("shelfRenderer")?.let { visitShelf(it) }
                }
            }
        }
        return SubsPage(entries, next ?: findContinuationToken(json))
    }

    internal fun parseShortsCard(shorts: JSONObject): ShortsCard? {
        val reel = shorts.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("reelWatchEndpoint")
        val videoId = reel?.optString("videoId").orEmpty().takeIf { it.length == 11 } ?: return null
        val overlay = shorts.optJSONObject("overlayMetadata")
        val title = overlay?.optJSONObject("primaryText")?.optString("content").orEmpty()
        val views = overlay?.optJSONObject("secondaryText")?.optString("content").orEmpty()
        val thumb = absoluteUrl(
            shorts.optJSONObject("thumbnailViewModel")?.optJSONObject("thumbnailViewModel")
                ?.optJSONObject("image")?.optJSONArray("sources")
                ?.let { sources -> sources.optJSONObject(sources.length() - 1)?.optString("url") }
        ).orEmpty()
        return ShortsCard(videoId, title, views, thumb)
    }

    private fun parseChannels(json: JSONObject): List<SubsChannel> {
        val channels = mutableListOf<SubsChannel>()

        fun visit(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
                is JSONObject -> {
                    node.optJSONObject("channelRenderer")?.let { channel ->
                        val id = channel.optString("channelId")
                        val name = text(channel.optJSONObject("title"))
                        if (id.isNotBlank() && name.isNotBlank()) {
                            val avatar = absoluteUrl(
                                channel.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                    ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
                            )
                            val url = channel.optJSONObject("navigationEndpoint")
                                ?.optJSONObject("browseEndpoint")?.optString("canonicalBaseUrl")
                                ?.takeIf { it.isNotBlank() }
                                ?.let { if (it.startsWith("/")) "$HOST$it" else it }
                                ?: "$HOST/channel/$id"
                            channels.add(SubsChannel(id, name, avatar, url))
                        }
                    }
                    for (key in node.keys()) if (key != "channelRenderer") visit(node.opt(key))
                }
            }
        }

        visit(json)
        return channels.distinctBy { it.id }
    }

    private fun collectChips(json: JSONObject): List<HomeChip> {
        val chips = mutableListOf<HomeChip>()
        fun visit(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
                is JSONObject -> {
                    node.optJSONObject("chipCloudChipRenderer")?.let { chip ->
                        val title = text(chip.optJSONObject("text"))
                        if (title.isNotBlank()) {
                            val nav = chip.optJSONObject("navigationEndpoint")
                            val token = nav?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }
                            val params = nav?.optJSONObject("browseEndpoint")?.optString("params")?.takeIf { it.isNotBlank() }
                            chips.add(HomeChip(title, token, params, chip.optBoolean("isSelected")))
                        }
                    }
                    for (key in node.keys()) {
                        if (key == "chipCloudChipRenderer") continue
                        visit(node.opt(key))
                    }
                }
            }
        }
        visit(json)
        return chips.distinctBy { it.title }
    }

    internal fun parseVideoRenderer(videoRenderer: JSONObject): StreamInfoItem? {
        val videoId = videoRenderer.optString("videoId")
        if (videoId.length != 11) return null

        val title = text(videoRenderer.optJSONObject("title"))
        val uploader = text(videoRenderer.optJSONObject("ownerText"))
        val ownerBrowse = videoRenderer.optJSONObject("ownerText")
            ?.optJSONArray("runs")?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
        val browseChannelId = ownerBrowse?.optString("browseId")?.takeIf { it.startsWith("UC") }
        val channelUrl = ownerBrowse
            ?.optString("canonicalBaseUrl")
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("/")) "$HOST$it" else it }
            ?: browseChannelId?.let { "$HOST/channel/$it" }
        if (channelUrl != null && browseChannelId != null) {
            channelIdByUrl[channelUrl] = browseChannelId
        }

        val durationSec = parseDuration(videoRenderer.optJSONObject("lengthText")?.optString("simpleText").orEmpty())
        val viewsStr = videoRenderer.optJSONObject("shortViewCountText")?.optString("simpleText")
            ?: videoRenderer.optJSONObject("viewCountText")?.optString("simpleText").orEmpty()
        val uploadDate = videoRenderer.optJSONObject("publishedTimeText")?.optString("simpleText").orEmpty()
        val thumbUrl = pickThumbnailUrl(videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails"), 720)
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val avatarUrl = videoRenderer.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?.let { thumbs -> thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") }
        if (channelUrl != null && !avatarUrl.isNullOrBlank()) {
            VideoRepository.cacheAvatar(channelUrl, avatarUrl)
        }

        var videoProgress = -1
        val overlays = videoRenderer.optJSONArray("thumbnailOverlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val p = overlays.optJSONObject(i)
                    ?.optJSONObject("thumbnailOverlayProgressBarRenderer")
                    ?.optInt("startPercent", -1) ?: continue
                if (p >= 0) { videoProgress = p; break }
            }
        }
        val navEndpoint = videoRenderer.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
        val navStartSeconds = navEndpoint?.optLong("startTimeSeconds", 0L) ?: 0L
        val calculatedSeconds = if (navStartSeconds > 0L) navStartSeconds else if (videoProgress in 1..98 && durationSec > 0L) {
            (videoProgress / 100.0 * durationSec).toLong()
        } else 0L

        val canonicalUrl = "$HOST/watch?v=$videoId"
        val meta = VideoMeta(
            viewsText = viewsStr,
            publishedText = uploadDate,
            progressPercent = videoProgress,
            resumePositionMs = calculatedSeconds * 1000L
        )
        rememberMeta(canonicalUrl, meta)

        return StreamInfoItem(
            ServiceList.YouTube.serviceId,
            canonicalUrl,
            title,
            StreamType.VIDEO_STREAM
        ).apply {
            uploaderName = uploader
            uploaderUrl = channelUrl
            duration = durationSec
            viewCount = parseViews(viewsStr)
            textualUploadDate = uploadDate
            if (thumbUrl.isNotBlank()) thumbnails = listOf(Image(thumbUrl, 1280, 720, Image.ResolutionLevel.HIGH))
            if (!avatarUrl.isNullOrBlank()) uploaderAvatars = listOf(Image(avatarUrl, 88, 88, Image.ResolutionLevel.LOW))
        }
    }

    private data class ParsedLockup(val item: StreamInfoItem, val meta: VideoMeta)

    internal fun parseLockupViewModel(lockup: JSONObject): StreamInfoItem? = parseLockup(lockup)?.item

    internal fun historyProgress(lockup: JSONObject): Float? =
        progressPercent(lockup.optJSONObject("contentImage")).takeIf { it in 0..100 }?.div(100f)

    private fun parseLockup(lockup: JSONObject): ParsedLockup? {
        val contentType = lockup.optString("contentType")
        val isPlaylistLockup = contentType in setOf("LOCKUP_CONTENT_TYPE_PLAYLIST", "LOCKUP_CONTENT_TYPE_PODCAST")
        if (contentType != "LOCKUP_CONTENT_TYPE_VIDEO" && !isPlaylistLockup) return null

        val metadata = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = metadata.optJSONObject("title")?.optString("content").orEmpty()
        if (title.isBlank()) return null

        val onTapCmd = lockup.optJSONObject("rendererContext")
            ?.optJSONObject("commandContext")?.optJSONObject("onTap")
            ?.optJSONObject("innertubeCommand")
        val watchEndpoint = onTapCmd?.optJSONObject("watchEndpoint")
        val browseEndpoint = onTapCmd?.optJSONObject("browseEndpoint")
        var startSeconds = watchEndpoint?.optLong("startTimeSeconds", 0L) ?: 0L

        var playlistId: String? = if (isPlaylistLockup) {
            lockup.optString("contentId").removePrefix("VL").takeIf { it.isNotBlank() }
                ?: watchEndpoint?.optString("playlistId")?.removePrefix("VL")?.takeIf { it.isNotBlank() }
                ?: browseEndpoint?.optString("browseId")?.removePrefix("VL")?.takeIf { it.isNotBlank() }
        } else {
            watchEndpoint?.optString("playlistId")?.removePrefix("VL")?.takeIf { it.isNotBlank() }
        }

        val referencedVideoIds = LinkedHashSet<String>()
        watchEndpoint?.optString("videoId")?.takeIf { it.length == 11 }?.let { referencedVideoIds.add(it) }

        var uploader = ""
        var channelUrl: String? = null
        var channelBrowseId: String? = null
        var viewsText = ""
        var publishedText = ""
        var watchingText = ""
        var membersOnly = false
        var verified = false

        val rows = metadata.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
        if (rows != null) {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                row.optJSONArray("badges")?.let { badges ->
                    for (b in 0 until badges.length()) {
                        val badge = badges.optJSONObject(b)?.optJSONObject("badgeViewModel")?.optString("badgeText").orEmpty()
                        if (badge.contains("Members", ignoreCase = true)) membersOnly = true
                    }
                }
                val parts = row.optJSONArray("metadataParts") ?: continue
                for (j in 0 until parts.length()) {
                    val textObj = parts.optJSONObject(j)?.optJSONObject("text") ?: continue
                    val value = textObj.optString("content")
                    if (value.isBlank()) continue

                    val cmdRuns = textObj.optJSONArray("commandRuns")
                    if (cmdRuns != null) {
                        for (c in 0 until cmdRuns.length()) {
                            val cmd = cmdRuns.optJSONObject(c)?.optJSONObject("onTap")?.optJSONObject("innertubeCommand") ?: continue
                            cmd.optJSONObject("watchEndpoint")?.let { we ->
                                we.optString("videoId").takeIf { it.length == 11 }?.let { referencedVideoIds.add(it) }
                                val s = we.optLong("startTimeSeconds", 0L)
                                if (s > 0L && startSeconds <= 0L) startSeconds = s
                                if (playlistId == null) {
                                    playlistId = we.optString("playlistId").removePrefix("VL").takeIf { it.isNotBlank() }
                                }
                            }
                            cmd.optJSONObject("browseEndpoint")?.let { be ->
                                val bid = be.optString("browseId")
                                if (bid.startsWith("UC") && channelBrowseId == null) channelBrowseId = bid
                                if (bid.startsWith("VL") && playlistId == null) playlistId = bid.removePrefix("VL")
                                if (channelUrl == null) {
                                    channelUrl = be.optString("canonicalBaseUrl")
                                        .takeIf { it.isNotBlank() }
                                        ?.let { if (it.startsWith("/")) "$HOST$it" else it }
                                }
                            }
                        }
                    }
                    textObj.optJSONArray("attachmentRuns")?.let { runs ->
                        for (r in 0 until runs.length()) {
                            val sources = runs.optJSONObject(r)?.optJSONObject("element")?.optJSONObject("type")
                                ?.optJSONObject("imageType")?.optJSONObject("image")?.optJSONArray("sources") ?: continue
                            for (s in 0 until sources.length()) {
                                val name = sources.optJSONObject(s)?.optJSONObject("clientResource")?.optString("imageName").orEmpty()
                                if (name.contains("CHECK", ignoreCase = true)) verified = true
                            }
                        }
                    }
                    if (uploader.isBlank() && i == 0) {
                        uploader = value.substringBefore(" · ").trim()
                    }
                    if (i == 0) continue

                    // Teks server mengikuti bahasa akun (mis. "40K views", "Streamed 2 days ago",
                    // "1.4K watching"), jadi klasifikasi memakai penanda yang sama di beberapa bahasa.
                    val lower = value.lowercase(Locale.ROOT)
                    when {
                        lower.contains("watching") || lower.contains("menonton") -> watchingText = value
                        lower.contains("view") || lower.contains("ditonton") || lower.contains("tayang") -> viewsText = value
                        lower.contains(" ago") || lower.contains("yang lalu") || lower.contains("streamed") ||
                            lower.contains("ditayangkan") || lower.contains("premiered") || lower.contains("updated") -> publishedText = value
                        j == 0 && viewsText.isBlank() && !isPlaylistLockup -> viewsText = value
                        publishedText.isBlank() && !isPlaylistLockup -> publishedText = value
                    }
                }
            }
        }

        val avatar = metadata.optJSONObject("image")?.optJSONObject("decoratedAvatarViewModel")
        if (uploader.isBlank()) {
            uploader = avatar?.optString("a11yLabel").orEmpty()
                .removePrefix("Buka channel ")
                .removePrefix("Go to channel ")
        }
        val avatarBrowse = avatar?.optJSONObject("rendererContext")?.optJSONObject("commandContext")
            ?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
            ?.optJSONObject("browseEndpoint")
        if (channelBrowseId == null) {
            channelBrowseId = avatarBrowse?.optString("browseId")?.takeIf { it.startsWith("UC") }
        }
        if (channelUrl == null) {
            channelUrl = avatarBrowse?.optString("canonicalBaseUrl")
                ?.takeIf { it.isNotBlank() }
                ?.let { if (it.startsWith("/")) "$HOST$it" else it }
                ?: channelBrowseId?.let { "$HOST/channel/$it" }
        }
        if (channelUrl != null && channelBrowseId != null) {
            channelIdByUrl[channelUrl] = channelBrowseId
        }

        val contentImage = lockup.optJSONObject("contentImage")
        val primaryThumbModel = contentImage?.optJSONObject("thumbnailViewModel")
            ?: contentImage?.optJSONObject("collectionThumbnailViewModel")
                ?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailViewModel")
        val thumbUrl = absoluteUrl(
            pickThumbnailUrl(
                primaryThumbModel?.optJSONObject("image")?.optJSONArray("sources"),
                720
            )
        ).orEmpty()
        if (thumbUrl.isNotBlank()) {
            Regex("/vi/([A-Za-z0-9_-]{11})/").find(thumbUrl)?.groupValues?.get(1)?.let { referencedVideoIds.add(it) }
        }
        val avatarImageObj = metadata.optJSONObject("image")
        val avatarDeco = avatarImageObj?.optJSONObject("decoratedAvatarViewModel")
        val avatarObj = avatarDeco?.optJSONObject("avatar")?.optJSONObject("avatarViewModel")
            ?: avatarImageObj?.optJSONObject("avatarViewModel")
        val avatarSources = avatarObj?.optJSONObject("image")?.optJSONArray("sources")
            ?: avatarImageObj?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val avatarUrl = absoluteUrl(
            avatarSources?.let { sources ->
                sources.optJSONObject(sources.length() - 1)?.optString("url")
            }
        )
        if (channelUrl != null && !avatarUrl.isNullOrBlank()) {
            VideoRepository.cacheAvatar(channelUrl, avatarUrl)
        }

        val badgeText = firstBadgeText(contentImage)
        val progress = progressPercent(contentImage)

        if (isPlaylistLockup) {
            val pid = playlistId ?: return null
            val firstVid = referencedVideoIds.firstOrNull()
            val itemUrl = if (firstVid != null) "$HOST/watch?v=$firstVid&list=$pid" else "$HOST/playlist?list=$pid"
            val countLabel = badgeText?.takeIf { it.isNotBlank() } ?: "Playlist"
            if (referencedVideoIds.isNotEmpty()) {
                val ref = PlaylistRef(pid, title, countLabel, uploader)
                referencedVideoIds.forEach { vid -> WatchHistory.rememberPlaylistRef(vid, ref) }
            }
            val meta = VideoMeta(
                viewsText = countLabel,
                publishedText = publishedText,
                membersOnly = membersOnly,
                verified = verified,
                progressPercent = progress,
                playlistId = pid,
                playlistTitle = title,
                playlistVideoCountText = countLabel,
                isPlaylist = true
            )
            val item = StreamInfoItem(
                ServiceList.YouTube.serviceId,
                itemUrl,
                title,
                StreamType.VIDEO_STREAM
            ).apply {
                uploaderName = uploader
                uploaderUrl = channelUrl
                duration = 0L
                viewCount = -1L
                textualUploadDate = publishedText
                if (thumbUrl.isNotBlank()) thumbnails = listOf(Image(thumbUrl, 1280, 720, Image.ResolutionLevel.HIGH))
                if (!avatarUrl.isNullOrBlank()) uploaderAvatars = listOf(Image(avatarUrl, 88, 88, Image.ResolutionLevel.LOW))
            }
            metaByUrl[itemUrl] = meta
            return ParsedLockup(item, meta)
        }

        val videoId = lockup.optString("contentId").takeIf { it.length == 11 }
            ?: referencedVideoIds.firstOrNull()
            ?: return null

        // Teks "watching/menonton" juga bisa muncul pada replay live yang sudah selesai.
        // Hanya penanda live eksplisit yang boleh mengubah tipe item menjadi LIVE_STREAM.
        var isLive = badgeText.equals("LIVE", ignoreCase = true)
        if (!isLive) {
            isLive = avatar?.optJSONObject("liveData")?.optString("liveBadgeText")
                .equals("LIVE", ignoreCase = true)
        }
        val durationSec = if (isLive) 0L else parseDuration(badgeText.orEmpty())
        val rememberedPlaylist = WatchHistory.playlistRefForVideoId(videoId)
        val resolvedPlaylistId = playlistId ?: rememberedPlaylist?.playlistId
        val resolvedPlaylistTitle = rememberedPlaylist?.playlistTitle
        val resolvedPlaylistCount = rememberedPlaylist?.playlistVideoCount

        val itemUrl = "$HOST/watch?v=$videoId"
        val item = StreamInfoItem(
            ServiceList.YouTube.serviceId,
            itemUrl,
            title,
            if (isLive) StreamType.LIVE_STREAM else StreamType.VIDEO_STREAM
        ).apply {
            uploaderName = uploader
            uploaderUrl = channelUrl
            duration = durationSec
            viewCount = if (viewsText.isBlank()) -1L else parseViews(viewsText)
            textualUploadDate = publishedText
            if (thumbUrl.isNotBlank()) thumbnails = listOf(Image(thumbUrl, 1280, 720, Image.ResolutionLevel.HIGH))
            if (!avatarUrl.isNullOrBlank()) uploaderAvatars = listOf(Image(avatarUrl, 88, 88, Image.ResolutionLevel.LOW))
        }
        val calculatedSeconds = if (startSeconds > 0L) startSeconds else if (progress in 1..98 && durationSec > 0L) {
            (progress / 100.0 * durationSec).toLong()
        } else 0L

        val meta = VideoMeta(
            viewsText = viewsText,
            publishedText = publishedText,
            watchingText = watchingText,
            membersOnly = membersOnly,
            verified = verified,
            live = isLive,
            progressPercent = progress,
            resumePositionMs = calculatedSeconds * 1000L,
            playlistId = resolvedPlaylistId,
            playlistTitle = resolvedPlaylistTitle,
            playlistVideoCountText = resolvedPlaylistCount,
            isPlaylist = false
        )
        rememberMeta(itemUrl, meta)
        return ParsedLockup(item, meta)
    }

    private fun progressPercent(contentImage: JSONObject?): Int {
        val thumbModel = contentImage?.optJSONObject("thumbnailViewModel")
            ?: contentImage?.optJSONObject("collectionThumbnailViewModel")
                ?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailViewModel")
        val overlays = thumbModel?.optJSONArray("overlays") ?: return -1
        for (i in 0 until overlays.length()) {
            val progress = overlays.optJSONObject(i)
                ?.optJSONObject("thumbnailBottomOverlayViewModel")
                ?.optJSONObject("progressBar")
                ?.optJSONObject("thumbnailOverlayProgressBarViewModel")
                ?.optInt("startPercent", -1) ?: continue
            if (progress >= 0) return progress
        }
        return -1
    }

    private fun firstBadgeText(contentImage: JSONObject?): String? {
        val thumbModel = contentImage?.optJSONObject("thumbnailViewModel")
            ?: contentImage?.optJSONObject("collectionThumbnailViewModel")
                ?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailViewModel")
        val overlays = thumbModel?.optJSONArray("overlays") ?: return null
        for (i in 0 until overlays.length()) {
            val overlay = overlays.optJSONObject(i) ?: continue
            val bottomBadges = overlay.optJSONObject("thumbnailBottomOverlayViewModel")?.optJSONArray("badges")
            if (bottomBadges != null) {
                for (j in 0 until bottomBadges.length()) {
                    val badge = bottomBadges.optJSONObject(j)
                        ?.optJSONObject("thumbnailBadgeViewModel")
                        ?.optString("text")
                    if (!badge.isNullOrBlank()) return badge
                }
            }
            val overlayBadges = overlay.optJSONObject("thumbnailOverlayBadgeViewModel")?.optJSONArray("thumbnailBadges")
            if (overlayBadges != null) {
                for (j in 0 until overlayBadges.length()) {
                    val badge = overlayBadges.optJSONObject(j)
                        ?.optJSONObject("thumbnailBadgeViewModel")
                        ?.optString("text")
                    if (!badge.isNullOrBlank()) return badge
                }
            }
        }
        return null
    }

    /** URL thumbnail channel dikirim protokol-relatif ("//yt3..."); Coil butuh skema penuh. */
    private fun absoluteUrl(url: String?): String? =
        url?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("//")) "https:$it" else it }

    private fun text(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().trim()
    }

    private fun parseDuration(text: String): Long {
        if (text.isBlank()) return 0L
        val parts = text.replace('.', ':').split(':')
        var sec = 0L
        for (p in parts) {
            sec = sec * 60 + (p.toLongOrNull() ?: 0L)
        }
        return sec
    }

    private fun parseViews(text: String): Long {
        val clean = text.replace(",", ".").lowercase(Locale.ROOT)
        val num = clean.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return -1L
        return when {
            clean.contains("jt") || clean.contains("m") -> (num * 1_000_000).toLong()
            clean.contains("rb") || clean.contains("k") -> (num * 1_000).toLong()
            clean.contains("miliar") || clean.contains("b") -> (num * 1_000_000_000).toLong()
            else -> num.toLong()
        }
    }

    private fun execute(url: String, body: String, version: String, accountHeaders: Map<String, String>? = null): String {
        val builder = Request.Builder().url(url).header("User-Agent", AGENT)
            .header("Accept-Language", "$lang-$country,$lang;q=0.9,en;q=0.8")
            .post(body.toRequestBody(jsonType))
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", version)

        if (accountHeaders != null) {
            accountHeaders.forEach { (k, v) -> builder.header(k, v) }
        }

        return client.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) {
                val errBody = it.body?.string().orEmpty()
                android.util.Log.e("FlufHomeFeed", "HTTP ${it.code} for $url: $errBody")
                error("YouTube browse error: ${it.code}")
            }
            it.body?.string().orEmpty().ifBlank { error("Empty browse response") }
        }
    }
}
