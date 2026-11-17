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
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.net.URLDecoder
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

internal sealed interface HomeEntry {
    data class Video(val item: StreamInfoItem) : HomeEntry
    data class ShortsShelf(
        val items: List<ShortsCard>,
        val title: String = "Shorts",
    ) : HomeEntry
}

internal data class HomeFeedPage(
    val items: List<StreamInfoItem>,
    val continuationToken: String?,
    val chips: List<HomeChip> = emptyList(),
    val entries: List<HomeEntry> = items.map { HomeEntry.Video(it) },
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
    val isMix: Boolean = false,
)

data class ShortsCard(
    val videoId: String,
    val title: String,
    val viewsText: String,
    val thumbnailUrl: String,
    val badgeText: String = "",
    val sequenceParams: String = "",
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
    private class SimpleLruCache<K, V>(private val maxSize: Int) {
        private val map = object : java.util.LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
                return size > maxSize
            }
        }

        @Synchronized
        fun get(key: K): V? = map[key]

        @Synchronized
        fun put(key: K, value: V) {
            map[key] = value
        }

        @Synchronized
        fun remove(key: K): V? = map.remove(key)
    }

    private val metaByUrl = SimpleLruCache<String, VideoMeta>(500)
    private val channelIdByUrl = SimpleLruCache<String, String>(500)
    private val discoveredChannels = SimpleLruCache<String, Boolean>(500)

    internal fun metaOf(url: String): VideoMeta? {
        metaByUrl.get(url)?.let { return it }
        val vid = videoKey(url)
        if (vid.length == 11) {
            metaByUrl.get("$HOST/watch?v=$vid")?.let { return it }
        }
        return null
    }

    internal fun rememberMeta(url: String, meta: VideoMeta) {
        metaByUrl.put(url, meta)
        val vid = videoKey(url)
        if (vid.length == 11) {
            metaByUrl.put("$HOST/watch?v=$vid", meta)
        }
    }

    internal fun channelIdOf(urlOrId: String?): String? {
        if (urlOrId.isNullOrBlank()) return null
        val trimmed = urlOrId.trim()
        if (trimmed.startsWith("UC") && trimmed.length >= 20) return trimmed
        Regex("/channel/(UC[A-Za-z0-9_-]{20,})").find(trimmed)?.groupValues?.get(1)?.let { return it }
        return channelIdByUrl.get(trimmed)
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
        val alreadyDiscovered = synchronized(discoveredChannels) {
            if (discoveredChannels.get(channelId) == true) true
            else {
                discoveredChannels.put(channelId, true)
                false
            }
        }
        if (alreadyDiscovered) return@withContext false
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

    /**
     * Fetches channel header metadata, Home shelves, tab endpoints (`Home`, `Posts`, search),
     * and membership surfaces in a single `browse` call.
     */
    suspend fun fetchChannelBundle(channelId: String): ChannelFetchBundle? = withContext(Dispatchers.IO) {
        val clean = channelId.trim()
        if (!clean.startsWith("UC") || clean.length <= 2) return@withContext null
        val channelJson = publicOrAccountRequest("browse", JSONObject().put("browseId", clean))
        val membershipUi = parseChannelMembershipUi(clean, channelJson)
        val uiData = parseChannelUiData(channelJson)
        ChannelFetchBundle(uiData = uiData, membershipUi = membershipUi)
    }

    /**
     * Fetches channel membership header (`Join` / `See perks`), `recognitionShelfRenderer`
     * ("Our members"), and—when the signed-in account has access—the live YouTube `Membership`
     * tab sections (member tier header, perks, highlighted & recent carousels).
     */
    suspend fun fetchChannelMembershipUi(channelId: String): ChannelMembershipUiData? =
        fetchChannelBundle(channelId)?.membershipUi

    private suspend fun parseChannelMembershipUi(clean: String, channelJson: JSONObject): ChannelMembershipUiData {

        var headerButtonLabel = "Join"
        var sponsorParams: String? = null
        fun scanSponsorButton(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) scanSponsorButton(node.opt(i))
                is JSONObject -> {
                    val bvm = node.optJSONObject("buttonViewModel")
                    if (bvm != null && (bvm.optString("iconName").contains("SPONSOR", ignoreCase = true) || bvm.optString("targetId").contains("sponsor", ignoreCase = true))) {
                        val title = bvm.optString("title").trim()
                        if (title.isNotBlank()) headerButtonLabel = title
                        val be = bvm.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("browseEndpoint")
                        if (be != null && sponsorParams == null) {
                            sponsorParams = be.optString("params").takeIf { it.isNotBlank() }
                        }
                    }
                    for (k in node.keys()) scanSponsorButton(node.opt(k))
                }
            }
        }
        scanSponsorButton(channelJson.optJSONObject("header"))

        var ourMembersTitle: String? = null
        var ourMembersSubtitle: String? = null
        val ourMembersAvatars = mutableListOf<String>()
        fun scanRecognition(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) scanRecognition(node.opt(i))
                is JSONObject -> {
                    val rec = node.optJSONObject("recognitionShelfRenderer")
                    if (rec != null) {
                        ourMembersTitle = text(rec.optJSONObject("title")).ifBlank { "Our members" }
                        ourMembersSubtitle = text(rec.optJSONObject("subtitle")).ifBlank { "Thank you, channel members!" }
                        val avatars = rec.optJSONArray("avatars")
                        if (avatars != null) {
                            for (i in 0 until avatars.length()) {
                                val thumbs = avatars.optJSONObject(i)?.optJSONArray("thumbnails")
                                val u = absoluteUrl(thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url"))
                                if (!u.isNullOrBlank()) ourMembersAvatars.add(u)
                            }
                        }
                    }
                    for (k in node.keys()) if (k != "recognitionShelfRenderer") scanRecognition(node.opt(k))
                }
            }
        }
        scanRecognition(channelJson.optJSONObject("contents"))

        var membershipTabParams: String? = null
        val tabs = channelJson.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
        if (tabs != null) {
            for (i in 0 until tabs.length()) {
                val tr = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                val title = tr.optString("title")
                val be = tr.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")
                val params = be?.optString("params").orEmpty()
                if (title.equals("Membership", ignoreCase = true) || title.equals("Memberships", ignoreCase = true)) {
                    if (params.isNotBlank()) membershipTabParams = params
                    break
                }
            }
        }

        val isMemberHeader = headerButtonLabel.equals("See perks", ignoreCase = true) ||
            headerButtonLabel.equals("Upgrade", ignoreCase = true)

        var tierTitle: String? = null
        var statusLabel: String? = if (isMemberHeader) "Member" else null
        var tenureLabel: String? = null
        var memberAvatarUrl: String? = null
        val perks = mutableListOf<ChannelPerkItem>()
        val highlighted = mutableListOf<StreamInfoItem>()
        val recentVideos = mutableListOf<StreamInfoItem>()

        if (membershipTabParams != null) {
            val memJson = runCatching {
                publicOrAccountRequest("browse", JSONObject().put("browseId", clean).put("params", membershipTabParams))
            }.getOrNull()
            if (memJson != null) {
                fun collectShelfStreams(container: Any?, target: MutableList<StreamInfoItem>) {
                    val seenUrls = target.mapTo(HashSet()) { it.url }
                    fun visitStream(n: Any?) {
                        when (n) {
                            is JSONArray -> for (i in 0 until n.length()) visitStream(n.opt(i))
                            is JSONObject -> {
                                n.optJSONObject("lockupViewModel")?.let { lk ->
                                    parseLockup(lk)?.let { parsed ->
                                        rememberMeta(parsed.item.url, parsed.meta.copy(membersOnly = true))
                                        if (seenUrls.add(parsed.item.url)) target.add(parsed.item)
                                    }
                                }
                                val vr = n.optJSONObject("videoRenderer") ?: n.optJSONObject("gridVideoRenderer")
                                if (vr != null) {
                                    parseVideoRenderer(vr)?.let { item ->
                                        val existing = metaOf(item.url)
                                        if (existing != null) rememberMeta(item.url, existing.copy(membersOnly = true))
                                        if (seenUrls.add(item.url)) target.add(item)
                                    }
                                }
                                for (k in n.keys()) {
                                    if (k != "lockupViewModel" && k != "videoRenderer" && k != "gridVideoRenderer") {
                                        visitStream(n.opt(k))
                                    }
                                }
                            }
                        }
                    }
                    visitStream(container)
                }

                fun extractMemberMetadata(n: Any?) {
                    when (n) {
                        is JSONArray -> for (i in 0 until n.length()) extractMemberMetadata(n.opt(i))
                        is JSONObject -> {
                            n.optJSONObject("shelfRenderer")?.let { shelf ->
                                val st = text(shelf.optJSONObject("title"))
                                val dest = when {
                                    st.contains("highlight", ignoreCase = true) -> highlighted
                                    st.contains("recent", ignoreCase = true) || st.contains("video", ignoreCase = true) -> recentVideos
                                    else -> null
                                }
                                if (dest != null) collectShelfStreams(shelf.optJSONObject("content"), dest)
                            }
                            n.optJSONObject("sponsorshipsTierRenderer")?.let { tr ->
                                if (tierTitle == null) tierTitle = text(tr.optJSONObject("title")).ifBlank { null }
                            }
                            n.optJSONObject("sponsorshipsHeaderRenderer")?.let { hr ->
                                if (tierTitle == null) tierTitle = text(hr.optJSONObject("title")).ifBlank { null }
                                if (memberAvatarUrl == null) {
                                    val thumbs = hr.optJSONObject("avatar")?.optJSONArray("thumbnails")
                                    memberAvatarUrl = absoluteUrl(thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url"))
                                }
                            }
                            n.optJSONObject("sponsorshipsPerkRenderer")?.let { pr ->
                                val t = text(pr.optJSONObject("title"))
                                if (t.isNotBlank() && perks.none { it.title == t }) {
                                    val kind = if (t.contains("post", ignoreCase = true)) "posts" else "videos"
                                    perks.add(ChannelPerkItem(t, kind))
                                }
                            }
                            for (k in n.keys()) {
                                if (k != "shelfRenderer") extractMemberMetadata(n.opt(k))
                            }
                        }
                    }
                }
                extractMemberMetadata(memJson.optJSONObject("contents"))

                // Also scan textual EML models inside the first member header section (tier/months/perks)
                val firstSection = memJson.optJSONObject("contents")
                    ?.optJSONObject("twoColumnBrowseResultsRenderer")
                    ?.optJSONArray("tabs")
                    ?.let { arr ->
                        (0 until arr.length()).asSequence()
                            .mapNotNull { arr.optJSONObject(it)?.optJSONObject("tabRenderer") }
                            .firstOrNull { it.optBoolean("selected") }
                    }
                    ?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")
                    ?.optJSONObject(0)
                if (firstSection != null) {
                    val strings = mutableListOf<String>()
                    fun gatherStrings(v: Any?) {
                        when (v) {
                            is JSONArray -> for (i in 0 until v.length()) gatherStrings(v.opt(i))
                            is JSONObject -> for (k in v.keys()) {
                                if (k == "content" || k == "simpleText" || k == "text") {
                                    val s = v.optString(k).trim()
                                    if (s.length in 3..64 && !s.startsWith("http")) strings.add(s)
                                } else {
                                    gatherStrings(v.opt(k))
                                }
                            }
                        }
                    }
                    gatherStrings(firstSection)
                    for (s in strings) {
                        if (tierTitle == null && s.startsWith("#")) tierTitle = s
                        else if (statusLabel == null && s.equals("Member", ignoreCase = true)) statusLabel = "Member"
                        else if (tenureLabel == null && Regex("\\d+\\s+(month|months|year|years|day|days)", RegexOption.IGNORE_CASE).matches(s)) {
                            tenureLabel = s
                        } else if ((s.contains("Members-only", ignoreCase = true) || s.contains("Loyalty", ignoreCase = true) || s.contains("emoji", ignoreCase = true)) && perks.none { it.title == s }) {
                            perks.add(ChannelPerkItem(s, if (s.contains("post", ignoreCase = true)) "posts" else "videos"))
                        }
                    }
                }
            }
        }

        return ChannelMembershipUiData(
            isJoinedMember = isMemberHeader || membershipTabParams != null,
            headerButtonLabel = if (isMemberHeader || membershipTabParams != null) "See perks" else headerButtonLabel,
            memberTierTitle = tierTitle,
            memberStatusLabel = statusLabel,
            memberTenureLabel = tenureLabel,
            memberAvatarUrl = memberAvatarUrl,
            ourMembersTitle = ourMembersTitle,
            ourMembersSubtitle = ourMembersSubtitle,
            ourMembersAvatars = ourMembersAvatars.distinct().take(12),
            perks = perks.distinctBy { it.title }.take(6),
            highlighted = highlighted,
            recentVideos = recentVideos
        )
    }

    internal fun parseChannelUiData(channelJson: JSONObject): ChannelUiData {
        val header = channelJson.optJSONObject("header")
        val pageHeader = header?.optJSONObject("pageHeaderRenderer")
            ?.optJSONObject("content")?.optJSONObject("pageHeaderViewModel")
        val c4Header = header?.optJSONObject("c4TabbedHeaderRenderer")

        var handle: String? = null
        var subscriberCountText: String? = null
        var videoCountText: String? = null

        fun inspectMetaPart(raw: String) {
            val parts = raw.split("·", "•").map { it.trim() }.filter { it.isNotBlank() }
            for (part in parts) {
                when {
                    part.startsWith("@") && handle == null -> handle = part
                    (part.contains("subscriber", ignoreCase = true) ||
                        part.contains("pelanggan", ignoreCase = true)) && subscriberCountText == null -> {
                        subscriberCountText = part
                    }
                    Regex("\\d[\\d.,KkMmBbJtRb\\s]*\\bvideos?\\b", RegexOption.IGNORE_CASE).containsMatchIn(part) &&
                        videoCountText == null -> {
                        videoCountText = part
                    }
                }
            }
        }

        val metadataRows = pageHeader?.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")
        if (metadataRows != null) {
            for (r in 0 until metadataRows.length()) {
                val parts = metadataRows.optJSONObject(r)?.optJSONArray("metadataParts") ?: continue
                for (p in 0 until parts.length()) {
                    val content = parts.optJSONObject(p)?.optJSONObject("text")?.optString("content").orEmpty()
                    if (content.isNotBlank()) inspectMetaPart(content)
                }
            }
        }
        if (handle == null) handle = text(c4Header?.optJSONObject("channelHandleText")).ifBlank { null }
        if (subscriberCountText == null) subscriberCountText = text(c4Header?.optJSONObject("subscriberCountText")).ifBlank { null }
        if (videoCountText == null) videoCountText = text(c4Header?.optJSONObject("videosCountText")).ifBlank { null }

        val description = pageHeader?.optJSONObject("description")
            ?.optJSONObject("descriptionPreviewViewModel")
            ?.optJSONObject("description")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: text(c4Header?.optJSONObject("tagline")?.optJSONObject("channelTaglineRenderer")).ifBlank { null }

        val attributionText = pageHeader?.optJSONObject("attribution")?.optJSONObject("attributionViewModel")?.optJSONObject("text")
        val rawSummary = attributionText?.optString("content")?.trim()?.takeIf { it.isNotBlank() }
        val links = mutableListOf<ChannelLink>()
        extractLinksFromCommandRuns(attributionText?.optJSONArray("commandRuns"), rawSummary, links)
        collectChannelLinks(header, links)
        collectChannelLinks(channelJson.optJSONArray("engagementPanels"), links)
        val distinctLinks = links.distinctBy { it.url }

        val linksSummary = rawSummary ?: when (distinctLinks.size) {
            0 -> null
            1 -> distinctLinks[0].title.ifBlank { distinctLinks[0].url.removePrefix("https://").removePrefix("http://") }
            else -> "${distinctLinks[0].title} and ${distinctLinks.size - 1} more ${if (distinctLinks.size - 1 == 1) "link" else "links"}"
        }

        var isSubscribed: Boolean? = null
        c4Header?.optJSONObject("subscribeButton")?.optJSONObject("subscribeButtonRenderer")?.let { sub ->
            if (sub.has("subscribed")) isSubscribed = sub.optBoolean("subscribed")
        }
        if (isSubscribed == null) {
            fun findSubscribedFlag(node: Any?): Boolean? {
                when (node) {
                    is JSONArray -> for (i in 0 until node.length()) findSubscribedFlag(node.opt(i))?.let { return it }
                    is JSONObject -> {
                        val content = node.optJSONObject("subscribeButtonViewModel")?.optJSONObject("subscribeButtonContent")
                        if (content != null && content.has("subscribed")) return content.optBoolean("subscribed")
                        for (k in node.keys()) findSubscribedFlag(node.opt(k))?.let { return it }
                    }
                }
                return null
            }
            isSubscribed = findSubscribedFlag(header)
        }

        var homeTabParams: String? = null
        var postsTabParams: String? = null
        var searchTabParams: String? = null
        var hasHomeTab = false
        var hasPostsTab = false
        var homeShelves: List<ChannelHomeShelf> = emptyList()

        val tabs = channelJson.optJSONObject("contents")
            ?.optJSONObject("twoColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")
        if (tabs != null) {
            for (i in 0 until tabs.length()) {
                val entry = tabs.optJSONObject(i) ?: continue
                entry.optJSONObject("expandableTabRenderer")
                    ?.optJSONObject("endpoint")
                    ?.optJSONObject("browseEndpoint")
                    ?.optString("params")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { searchTabParams = it }
                val tr = entry.optJSONObject("tabRenderer") ?: continue
                val title = tr.optString("title")
                val params = tr.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("params")
                    ?.takeIf { it.isNotBlank() }
                when {
                    title.equals("Home", ignoreCase = true) -> {
                        hasHomeTab = true
                        if (params != null) homeTabParams = params
                        if (tr.optBoolean("selected")) {
                            homeShelves = parseChannelHomeShelves(tr.optJSONObject("content"))
                        }
                    }
                    title.equals("Posts", ignoreCase = true) || title.equals("Community", ignoreCase = true) -> {
                        hasPostsTab = true
                        if (params != null) postsTabParams = params
                    }
                }
            }
        }

        return ChannelUiData(
            handle = handle,
            subscriberCountText = subscriberCountText,
            videoCountText = videoCountText,
            description = description,
            linksSummary = linksSummary,
            links = distinctLinks,
            homeTabParams = homeTabParams,
            postsTabParams = postsTabParams,
            searchTabParams = searchTabParams,
            hasHomeTab = hasHomeTab,
            hasPostsTab = hasPostsTab,
            isSubscribed = isSubscribed,
            homeShelves = homeShelves
        )
    }

    internal fun unwrapYoutubeRedirectUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""
        val normalized = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "$HOST$trimmed"
            !trimmed.contains("://") -> "https://$trimmed"
            else -> trimmed
        }
        val queryStart = normalized.indexOf('?')
        if (queryStart < 0 || !normalized.substring(0, queryStart).contains("youtube.com/redirect", ignoreCase = true)) {
            return normalized
        }
        val query = normalized.substring(queryStart + 1)
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            if (eq > 0 && pair.substring(0, eq) == "q") {
                val decoded = runCatching { URLDecoder.decode(pair.substring(eq + 1), "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank()) return decoded
            }
        }
        return normalized
    }

    private fun extractLinksFromCommandRuns(runs: JSONArray?, fallbackLabel: String?, out: MutableList<ChannelLink>) {
        if (runs == null) return
        for (i in 0 until runs.length()) {
            val url = runs.optJSONObject(i)
                ?.optJSONObject("onTap")
                ?.optJSONObject("innertubeCommand")
                ?.optJSONObject("urlEndpoint")
                ?.optString("url")
                ?.takeIf { it.isNotBlank() } ?: continue
            val cleanUrl = unwrapYoutubeRedirectUrl(url)
            val title = fallbackLabel?.substringBefore(" and ")?.trim()?.takeIf { it.isNotBlank() }
                ?: cleanUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
            out.add(ChannelLink(title = title, url = cleanUrl))
        }
    }

    private fun collectChannelLinks(node: Any?, out: MutableList<ChannelLink>) {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) collectChannelLinks(node.opt(i), out)
            is JSONObject -> {
                node.optJSONObject("channelExternalLinkViewModel")?.let { linkVm ->
                    val title = linkVm.optJSONObject("title")?.optString("content").orEmpty().trim()
                    val linkObj = linkVm.optJSONObject("link")
                    val cmdUrl = linkObj?.optJSONArray("commandRuns")?.optJSONObject(0)
                        ?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
                        ?.optJSONObject("urlEndpoint")?.optString("url")
                    val rawUrl = cmdUrl?.takeIf { it.isNotBlank() } ?: linkObj?.optString("content").orEmpty()
                    if (rawUrl.isNotBlank()) {
                        val cleanUrl = unwrapYoutubeRedirectUrl(rawUrl)
                        out.add(ChannelLink(title = title.ifBlank { cleanUrl }, url = cleanUrl))
                    }
                }
                node.optJSONObject("channelHeaderLinksRenderer")?.let { legacyLinks ->
                    listOf("primaryLinks", "secondaryLinks").forEach { key ->
                        val arr = legacyLinks.optJSONArray(key) ?: return@forEach
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val title = text(item.optJSONObject("title")).ifBlank { "Link" }
                            val url = item.optJSONObject("navigationEndpoint")?.optJSONObject("urlEndpoint")?.optString("url").orEmpty()
                            if (url.isNotBlank()) {
                                out.add(ChannelLink(title = title, url = unwrapYoutubeRedirectUrl(url)))
                            }
                        }
                    }
                }
                for (k in node.keys()) {
                    if (k != "channelExternalLinkViewModel" && k != "channelHeaderLinksRenderer") {
                        collectChannelLinks(node.opt(k), out)
                    }
                }
            }
        }
    }

    internal fun parseChannelHomeShelves(contentNode: JSONObject?): List<ChannelHomeShelf> {
        if (contentNode == null) return emptyList()
        val sections = contentNode.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            ?: contentNode.optJSONObject("richGridRenderer")?.optJSONArray("contents")
            ?: return emptyList()
        val shelves = mutableListOf<ChannelHomeShelf>()

        fun targetTabFor(title: String, videos: List<StreamInfoItem>, shorts: List<ShortsCard>, playlists: List<PlaylistInfoItem>): String? = when {
            shorts.isNotEmpty() && videos.isEmpty() && playlists.isEmpty() -> ChannelTabs.SHORTS
            playlists.isNotEmpty() && videos.isEmpty() && shorts.isEmpty() -> ChannelTabs.PLAYLISTS
            title.contains("live", ignoreCase = true) -> ChannelTabs.LIVESTREAMS
            title.contains("playlist", ignoreCase = true) || title.contains("release", ignoreCase = true) -> ChannelTabs.PLAYLISTS
            title.contains("video", ignoreCase = true) || title.equals("For You", ignoreCase = true) -> ChannelTabs.VIDEOS
            else -> null
        }

        fun extractShelfItems(list: JSONArray?, title: String) {
            if (list == null) return
            val videos = mutableListOf<StreamInfoItem>()
            val shorts = mutableListOf<ShortsCard>()
            val playlists = mutableListOf<PlaylistInfoItem>()
            for (i in 0 until list.length()) {
                val entry = list.optJSONObject(i) ?: continue
                val item = entry.optJSONObject("richItemRenderer")?.optJSONObject("content") ?: entry
                item.optJSONObject("shortsLockupViewModel")?.let { card -> parseShortsCard(card)?.let { shorts.add(it) } }
                item.optJSONObject("reelItemRenderer")?.let { reel -> parseReelItemRenderer(reel)?.let { shorts.add(it) } }
                val pl = item.optJSONObject("gridPlaylistRenderer") ?: item.optJSONObject("playlistRenderer")
                if (pl != null) {
                    parsePlaylistRenderer(pl)?.let { playlists.add(it) }
                } else {
                    item.optJSONObject("lockupViewModel")?.let { lockup ->
                        val ctype = lockup.optString("contentType")
                        if (ctype == "LOCKUP_CONTENT_TYPE_PLAYLIST" || ctype == "LOCKUP_CONTENT_TYPE_PODCAST") {
                            parsePlaylistLockup(lockup)?.let { playlists.add(it) }
                        } else {
                            parseLockupViewModel(lockup)?.let { videos.add(it) }
                        }
                    }
                }
                val vr = item.optJSONObject("videoRenderer") ?: item.optJSONObject("gridVideoRenderer")
                if (vr != null) parseVideoRenderer(vr)?.let { videos.add(it) }
            }
            if (videos.isNotEmpty() || shorts.isNotEmpty() || playlists.isNotEmpty()) {
                val resolvedTitle = title.ifBlank { if (shorts.isNotEmpty()) "Shorts" else if (playlists.isNotEmpty()) "Playlists" else "Videos" }
                shelves.add(
                    ChannelHomeShelf(
                        title = resolvedTitle,
                        videos = videos.distinctBy { it.url },
                        shorts = shorts.distinctBy { it.videoId },
                        playlists = playlists.distinctBy { it.url },
                        targetTab = targetTabFor(resolvedTitle, videos, shorts, playlists)
                    )
                )
            }
        }

        fun visitNode(node: JSONObject) {
            if (node.has("recognitionShelfRenderer")) return
            node.optJSONObject("channelVideoPlayerRenderer")?.let { player ->
                val vid = player.optString("videoId").takeIf { it.length == 11 } ?: return@let
                val title = text(player.optJSONObject("title")).ifBlank { "Featured video" }
                val viewsText = text(player.optJSONObject("viewCountText"))
                val publishedText = text(player.optJSONObject("publishedTimeText"))
                val canonicalUrl = "$HOST/watch?v=$vid"
                rememberMeta(canonicalUrl, VideoMeta(viewsText = viewsText, publishedText = publishedText))
                val stream = StreamInfoItem(ServiceList.YouTube.serviceId, canonicalUrl, title, StreamType.VIDEO_STREAM).apply {
                    viewCount = parseViews(viewsText)
                    textualUploadDate = publishedText
                    thumbnails = listOf(Image("https://i.ytimg.com/vi/$vid/hqdefault.jpg", 1280, 720, Image.ResolutionLevel.HIGH))
                }
                shelves.add(ChannelHomeShelf(title = "Featured", videos = listOf(stream), targetTab = ChannelTabs.VIDEOS))
                return
            }
            node.optJSONObject("channelFeaturedContentRenderer")?.let { featured ->
                extractShelfItems(featured.optJSONArray("items"), text(featured.optJSONObject("title")).ifBlank { "Featured" })
                return
            }
            node.optJSONObject("shelfRenderer")?.let { shelf ->
                val title = text(shelf.optJSONObject("title"))
                val content = shelf.optJSONObject("content")
                val list = content?.optJSONObject("horizontalListRenderer")?.optJSONArray("items")
                    ?: content?.optJSONObject("expandedShelfContentsRenderer")?.optJSONArray("items")
                extractShelfItems(list, title)
                return
            }
            val directShelf = node.optJSONObject("reelShelfRenderer")
                ?: node.optJSONObject("richShelfRenderer")
                ?: node.optJSONObject("gridShelfViewModel")
            if (directShelf != null) {
                val list = directShelf.optJSONArray("contents") ?: directShelf.optJSONArray("items")
                extractShelfItems(list, parseShelfTitle(directShelf))
                return
            }
            node.optJSONObject("richSectionRenderer")?.optJSONObject("content")?.let { visitNode(it); return }
            node.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { visitNode(it) }
            }
        }

        for (i in 0 until sections.length()) {
            sections.optJSONObject(i)?.let { visitNode(it) }
        }
        return shelves
    }

    suspend fun fetchChannelHomeShelves(channelId: String, homeTabParams: String): List<ChannelHomeShelf> = withContext(Dispatchers.IO) {
        val clean = channelId.trim()
        if (!clean.startsWith("UC")) return@withContext emptyList()
        val json = publicOrAccountRequest("browse", JSONObject().put("browseId", clean).put("params", homeTabParams))
        val tabs = json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")?.optJSONArray("tabs")
            ?: return@withContext emptyList()
        for (i in 0 until tabs.length()) {
            val tr = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
            if (tr.optBoolean("selected") || tr.optString("title").equals("Home", ignoreCase = true)) {
                val parsed = parseChannelHomeShelves(tr.optJSONObject("content"))
                if (parsed.isNotEmpty()) return@withContext parsed
            }
        }
        emptyList()
    }

    internal fun parseChannelPostsPage(
        json: JSONObject,
        fallbackAuthor: String = "",
        fallbackAvatar: String? = null
    ): ChannelPostsPage {
        val posts = mutableListOf<ChannelPost>()
        val seenIds = mutableSetOf<String>()
        var nextToken: String? = null

        fun addPost(pr: JSONObject) {
            val postId = pr.optString("postId").ifBlank { return }
            if (!seenIds.add(postId)) return
            val author = text(pr.optJSONObject("authorText")).ifBlank { fallbackAuthor }
            val avatarThumbs = pr.optJSONObject("authorThumbnail")?.optJSONArray("thumbnails")
            val authorAvatar = absoluteUrl(pickBestThumbnailUrl(avatarThumbs)) ?: fallbackAvatar
            val publishedText = text(pr.optJSONObject("publishedTimeText"))
            val postText = text(pr.optJSONObject("contentText"))
            val voteCount = text(pr.optJSONObject("voteCount")).ifBlank { null }
            val replyCount = text(
                pr.optJSONObject("actionButtons")
                    ?.optJSONObject("commentActionButtonsRenderer")
                    ?.optJSONObject("replyButton")
                    ?.optJSONObject("buttonRenderer")
                    ?.optJSONObject("text")
            ).ifBlank { null }

            val images = mutableListOf<String>()
            var attachedVideo: StreamInfoItem? = null
            val pollChoices = mutableListOf<String>()
            val att = pr.optJSONObject("backstageAttachment")
            att?.optJSONObject("backstageImageRenderer")?.let { bir ->
                absoluteUrl(pickBestThumbnailUrl(bir.optJSONObject("image")?.optJSONArray("thumbnails")))?.let { images.add(it) }
            }
            att?.optJSONObject("postMultiImageRenderer")?.optJSONArray("images")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val img = arr.optJSONObject(i)?.optJSONObject("backstageImageRenderer")
                        ?.optJSONObject("image")?.optJSONArray("thumbnails")
                    absoluteUrl(pickBestThumbnailUrl(img))?.let { images.add(it) }
                }
            }
            att?.optJSONObject("videoRenderer")?.let { vr ->
                attachedVideo = parseVideoRenderer(vr)
            }
            att?.optJSONObject("pollRenderer")?.optJSONArray("choices")?.let { choices ->
                for (i in 0 until choices.length()) {
                    val c = text(choices.optJSONObject(i)?.optJSONObject("text"))
                    if (c.isNotBlank()) pollChoices.add(c)
                }
            }

            posts.add(
                ChannelPost(
                    postId = postId,
                    author = author,
                    authorAvatarUrl = authorAvatar,
                    publishedText = publishedText,
                    text = postText,
                    images = images,
                    attachedVideo = attachedVideo,
                    pollChoices = pollChoices,
                    voteCountText = voteCount,
                    replyCountText = replyCount
                )
            )
        }

        fun visitPosts(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) visitPosts(node.opt(i))
                is JSONObject -> {
                    node.optJSONObject("continuationItemRenderer")
                        ?.optJSONObject("continuationEndpoint")
                        ?.optJSONObject("continuationCommand")
                        ?.optString("token")
                        ?.takeIf { it.isNotBlank() && nextToken == null }
                        ?.let { nextToken = it }
                    val threadPost = node.optJSONObject("backstagePostThreadRenderer")?.optJSONObject("post")
                    val directPost = threadPost?.optJSONObject("backstagePostRenderer")
                        ?: threadPost?.optJSONObject("sharedPostRenderer")?.optJSONObject("originalPost")?.optJSONObject("backstagePostRenderer")
                        ?: node.optJSONObject("backstagePostRenderer")
                    if (directPost != null) {
                        addPost(directPost)
                        return
                    }
                    for (k in node.keys()) {
                        if (k != "trackingParams" && k != "frameworkUpdates") visitPosts(node.opt(k))
                    }
                }
            }
        }

        val contRoots = continuationItems(json)
        if (contRoots != null) {
            visitPosts(contRoots)
        } else {
            val tabs = json.optJSONObject("contents")?.optJSONObject("twoColumnBrowseResultsRenderer")?.optJSONArray("tabs")
            var selectedContent: JSONObject? = null
            if (tabs != null) {
                for (i in 0 until tabs.length()) {
                    val tr = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
                    val title = tr.optString("title")
                    if (tr.optBoolean("selected") || title.equals("Posts", ignoreCase = true) || title.equals("Community", ignoreCase = true)) {
                        selectedContent = tr.optJSONObject("content")
                        if (tr.optBoolean("selected")) break
                    }
                }
            }
            visitPosts(selectedContent ?: json.optJSONObject("contents"))
        }
        return ChannelPostsPage(posts = posts, nextToken = nextToken ?: findContinuationToken(json))
    }

    suspend fun fetchChannelPosts(
        channelId: String,
        postsTabParams: String? = null,
        continuation: String? = null,
        fallbackAuthor: String = "",
        fallbackAvatar: String? = null
    ): ChannelPostsPage = withContext(Dispatchers.IO) {
        val clean = channelId.trim()
        val body = JSONObject()
        when {
            !continuation.isNullOrBlank() -> body.put("continuation", continuation)
            clean.startsWith("UC") && !postsTabParams.isNullOrBlank() -> body.put("browseId", clean).put("params", postsTabParams)
            clean.startsWith("UC") -> body.put("browseId", clean).put("params", "EgVwb3N0c_IGBAoCBgA%3D")
            else -> return@withContext ChannelPostsPage(emptyList(), null)
        }
        val response = publicOrAccountRequest("browse", body)
        parseChannelPostsPage(response, fallbackAuthor, fallbackAvatar)
    }

    suspend fun searchInChannel(channelId: String, query: String, searchParams: String? = null): List<InfoItem> = withContext(Dispatchers.IO) {
        val clean = channelId.trim()
        val q = query.trim()
        if (!clean.startsWith("UC") || q.isBlank()) return@withContext emptyList()
        val body = JSONObject()
            .put("browseId", clean)
            .put("params", searchParams ?: "EgZzZWFyY2jyBgQKAggB")
            .put("query", q)
        val json = publicOrAccountRequest("browse", body)
        val items = mutableListOf<InfoItem>()
        val seen = mutableSetOf<String>()
        fun visit(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
                is JSONObject -> {
                    val pl = node.optJSONObject("playlistRenderer") ?: node.optJSONObject("gridPlaylistRenderer")
                    if (pl != null) {
                        parsePlaylistRenderer(pl)?.let { if (seen.add(it.url)) items.add(it) }
                        return
                    }
                    node.optJSONObject("lockupViewModel")?.let { lockup ->
                        val ctype = lockup.optString("contentType")
                        val item: InfoItem? = if (ctype == "LOCKUP_CONTENT_TYPE_PLAYLIST" || ctype == "LOCKUP_CONTENT_TYPE_PODCAST") {
                            parsePlaylistLockup(lockup)
                        } else {
                            parseLockupViewModel(lockup)
                        }
                        if (item != null && seen.add(item.url)) items.add(item)
                        return
                    }
                    val vr = node.optJSONObject("videoRenderer") ?: node.optJSONObject("gridVideoRenderer")
                    if (vr != null) {
                        parseVideoRenderer(vr)?.let { if (seen.add(it.url)) items.add(it) }
                        return
                    }
                    for (k in node.keys()) {
                        if (k != "trackingParams" && k != "frameworkUpdates") visit(node.opt(k))
                    }
                }
            }
        }
        visit(json.optJSONObject("contents"))
        items
    }

    private fun parsePlaylistRenderer(obj: JSONObject): PlaylistInfoItem? {
        val pid = obj.optString("playlistId").takeIf { it.isNotBlank() } ?: return null
        val title = text(obj.optJSONObject("title")).ifBlank { return null }
        val url = "$HOST/playlist?list=$pid"
        val thumbs = obj.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?: obj.optJSONArray("thumbnails")?.optJSONObject(0)?.optJSONArray("thumbnails")
        val thumbUrl = absoluteUrl(pickBestThumbnailUrl(thumbs))
        val count = obj.optString("videoCount").toLongOrNull()
            ?: text(obj.optJSONObject("videoCountText")).filter { it.isDigit() }.toLongOrNull()
            ?: -1L
        val owner = text(obj.optJSONObject("shortBylineText")).ifBlank { text(obj.optJSONObject("longBylineText")) }
        return PlaylistInfoItem(ServiceList.YouTube.serviceId, url, title).apply {
            uploaderName = owner
            streamCount = count
            if (!thumbUrl.isNullOrBlank()) thumbnails = listOf(Image(thumbUrl, 1280, 720, Image.ResolutionLevel.HIGH))
        }
    }

    private fun parsePlaylistLockup(lockup: JSONObject): PlaylistInfoItem? {
        val pid = lockup.optString("contentId").removePrefix("VL").takeIf { it.isNotBlank() } ?: return null
        val parsed = parseLockup(lockup) ?: return null
        val count = parsed.meta.playlistVideoCountText.orEmpty().filter { it.isDigit() }.toLongOrNull() ?: -1L
        val url = "$HOST/playlist?list=$pid"
        return PlaylistInfoItem(ServiceList.YouTube.serviceId, url, parsed.meta.playlistTitle ?: parsed.item.name).apply {
            uploaderName = parsed.item.uploaderName
            streamCount = count
            thumbnails = parsed.item.thumbnails
        }
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

    internal fun parsePage(json: JSONObject, isContinuation: Boolean): HomeFeedPage {
        val entries = mutableListOf<HomeEntry>()
        val items = mutableListOf<StreamInfoItem>()
        val seenVideos = mutableSetOf<String>()
        val seenShorts = mutableSetOf<String>()
        val pendingShorts = mutableListOf<ShortsCard>()
        val roots = if (isContinuation) continuationItems(json) ?: initialContents(json) else initialContents(json)

        fun flushPendingShorts(title: String = "Shorts") {
            if (pendingShorts.isEmpty()) return
            val fresh = pendingShorts.filter { seenShorts.add(it.videoId) }
            pendingShorts.clear()
            if (fresh.isNotEmpty()) entries.add(HomeEntry.ShortsShelf(fresh, title))
        }

        fun addVideoItem(item: StreamInfoItem?) {
            if (item == null || !seenVideos.add(item.url)) return
            flushPendingShorts()
            items.add(item)
            entries.add(HomeEntry.Video(item))
        }

        fun handleContentNode(node: JSONObject) {
            node.optJSONObject("shortsLockupViewModel")?.let { card ->
                parseShortsCard(card)?.let { pendingShorts.add(it) }
                return
            }
            node.optJSONObject("reelItemRenderer")?.let { reel ->
                parseReelItemRenderer(reel)?.let { pendingShorts.add(it) }
                return
            }
            node.optJSONObject("lockupViewModel")?.let { lockup ->
                addVideoItem(parseLockupViewModel(lockup))
                return
            }
            node.optJSONObject("videoRenderer")?.let { video ->
                addVideoItem(parseVideoRenderer(video))
            }
        }

        fun visitShelf(shelf: JSONObject) {
            flushPendingShorts()
            val shelfTitle = parseShelfTitle(shelf)
            val shelfList = shelf.optJSONArray("contents")
                ?: shelf.optJSONArray("items")
                ?: shelf.optJSONObject("content")?.optJSONObject("horizontalListRenderer")?.optJSONArray("items")
                ?: shelf.optJSONObject("content")?.optJSONObject("gridRenderer")?.optJSONArray("items")
                ?: return
            for (i in 0 until shelfList.length()) {
                val child = shelfList.optJSONObject(i) ?: continue
                val inner = child.optJSONObject("richItemRenderer")?.optJSONObject("content") ?: child
                handleContentNode(inner)
            }
            flushPendingShorts(shelfTitle)
        }

        fun visitNode(node: JSONObject) {
            node.optJSONObject("richItemRenderer")?.optJSONObject("content")?.let { content ->
                handleContentNode(content)
                return
            }
            node.optJSONObject("richSectionRenderer")?.optJSONObject("content")?.let { content ->
                val shelf = content.optJSONObject("gridShelfViewModel")
                    ?: content.optJSONObject("richShelfRenderer")
                    ?: content.optJSONObject("shelfRenderer")
                    ?: content.optJSONObject("reelShelfRenderer")
                if (shelf != null) visitShelf(shelf)
                return
            }
            val directShelf = node.optJSONObject("gridShelfViewModel")
                ?: node.optJSONObject("richShelfRenderer")
                ?: node.optJSONObject("reelShelfRenderer")
            if (directShelf != null) {
                visitShelf(directShelf)
                return
            }
            node.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")?.let { sectionContents ->
                for (i in 0 until sectionContents.length()) {
                    sectionContents.optJSONObject(i)?.let { visitNode(it) }
                }
                return
            }
            handleContentNode(node)
        }

        if (roots != null) {
            for (i in 0 until roots.length()) {
                roots.optJSONObject(i)?.let { visitNode(it) }
            }
            flushPendingShorts()
        }

        if (entries.isEmpty()) {
            val holder = TokenHolder()
            collect(roots ?: json, items, seenVideos, holder)
            items.forEach { entries.add(HomeEntry.Video(it)) }
            val next = directContinuationToken(roots) ?: holder.value ?: findContinuationToken(json)
            return HomeFeedPage(items, next, collectChips(json), entries)
        }

        val next = directContinuationToken(roots) ?: findContinuationToken(json)
        return HomeFeedPage(items, next, collectChips(json), entries)
    }

    private fun parseShelfTitle(shelf: JSONObject): String {
        val modern = shelf.optJSONObject("header")?.optJSONObject("sectionHeaderViewModel")
            ?.optJSONObject("headline")?.optString("content").orEmpty()
        if (modern.isNotBlank()) return modern
        val legacy = text(shelf.optJSONObject("title"))
        return legacy.ifBlank { "Shorts" }
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
            val contents = shelf.optJSONArray("contents") ?: shelf.optJSONArray("items") ?: return
            val shorts = mutableListOf<ShortsCard>()
            for (i in 0 until contents.length()) {
                val child = contents.optJSONObject(i) ?: continue
                val content = child.optJSONObject("richItemRenderer")?.optJSONObject("content") ?: child
                val card = content.optJSONObject("shortsLockupViewModel")
                val reel = content.optJSONObject("reelItemRenderer")
                when {
                    card != null -> parseShortsCard(card)?.let { shorts.add(it) }
                    reel != null -> parseReelItemRenderer(reel)?.let { shorts.add(it) }
                    else -> {
                        if (shorts.isNotEmpty()) { addShorts(shorts.toList()); shorts.clear() }
                        addContent(content)
                    }
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
                node.optJSONObject("gridShelfViewModel")?.let { visitShelf(it) }
                node.optJSONObject("reelShelfRenderer")?.let { visitShelf(it) }
                node.optJSONObject("richSectionRenderer")?.optJSONObject("content")?.let { content ->
                    content.optJSONObject("gridShelfViewModel")?.let { visitShelf(it) }
                    content.optJSONObject("richShelfRenderer")?.let { visitShelf(it) }
                    content.optJSONObject("shelfRenderer")?.let { visitShelf(it) }
                    content.optJSONObject("reelShelfRenderer")?.let { visitShelf(it) }
                }
            }
        }
        return SubsPage(entries, next ?: findContinuationToken(json))
    }

    internal fun parseShortsCard(shorts: JSONObject): ShortsCard? {
        val cmd = shorts.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
        val reelEndpoint = cmd?.optJSONObject("reelWatchEndpoint")
        val videoId = reelEndpoint?.optString("videoId").orEmpty().takeIf { it.length == 11 }
            ?: cmd?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty().takeIf { it.length == 11 }
            ?: shorts.optString("entityId").removePrefix("shorts-shelf-item-").takeIf { it.length == 11 }
            ?: return null
        val overlay = shorts.optJSONObject("overlayMetadata")
        val title = overlay?.optJSONObject("primaryText")?.optString("content").orEmpty()
            .ifBlank { shorts.optString("accessibilityText").substringBefore(",").trim() }
        val views = overlay?.optJSONObject("secondaryText")?.optString("content").orEmpty()
        val thumbSources = shorts.optJSONObject("thumbnailViewModel")?.optJSONObject("thumbnailViewModel")
            ?.optJSONObject("image")?.optJSONArray("sources")
            ?: shorts.optJSONObject("thumbnail")?.optJSONArray("sources")
        val thumb = absoluteUrl(pickBestThumbnailUrl(thumbSources)).orEmpty()
        val badgeText = extractShortsBadgeText(shorts)
        val sequenceParams = reelEndpoint?.optString("sequenceParams").orEmpty()
        return ShortsCard(videoId, title, views, thumb, badgeText, sequenceParams)
    }

    private fun parseReelItemRenderer(reel: JSONObject): ShortsCard? {
        val videoId = reel.optString("videoId").takeIf { it.length == 11 } ?: return null
        val title = text(reel.optJSONObject("headline"))
        val views = text(reel.optJSONObject("viewCountText"))
        val thumb = absoluteUrl(pickBestThumbnailUrl(reel.optJSONObject("thumbnail")?.optJSONArray("thumbnails"))).orEmpty()
        return ShortsCard(videoId, title, views, thumb)
    }

    private fun extractShortsBadgeText(shorts: JSONObject): String {
        shorts.optJSONObject("badge")?.optJSONObject("badgeViewModel")?.optString("badgeText")
            ?.takeIf { it.isNotBlank() }?.let { return it }
        val badges = shorts.optJSONArray("badges") ?: return ""
        for (i in 0 until badges.length()) {
            val item = badges.optJSONObject(i) ?: continue
            val text = item.optJSONObject("badgeViewModel")?.optString("badgeText")
                ?: item.optJSONObject("metadataBadgeRenderer")?.optString("label")
            if (!text.isNullOrBlank()) return text
        }
        return ""
    }

    private fun pickBestThumbnailUrl(sources: JSONArray?): String? {
        if (sources == null || sources.length() == 0) return null
        var bestUrl: String? = null
        var bestArea = -1L
        for (i in 0 until sources.length()) {
            val obj = sources.optJSONObject(i) ?: continue
            val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
            val area = obj.optInt("width", 0).toLong() * obj.optInt("height", 0).toLong()
            if (area >= bestArea) {
                bestArea = area
                bestUrl = url
            }
        }
        return bestUrl ?: sources.optJSONObject(0)?.optString("url")
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
            channelIdByUrl.put(channelUrl, browseChannelId)
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
        var hasLiveOverlay = false
        val overlays = videoRenderer.optJSONArray("thumbnailOverlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val overlay = overlays.optJSONObject(i) ?: continue
                overlay.optJSONObject("thumbnailOverlayProgressBarRenderer")
                    ?.optInt("startPercent", -1)
                    ?.takeIf { it >= 0 }
                    ?.let { videoProgress = it }
                val timeStatus = overlay.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                if (timeStatus != null) {
                    val style = timeStatus.optString("style")
                    val label = text(timeStatus.optJSONObject("text"))
                    if (style.contains("LIVE", ignoreCase = true) || label.equals("LIVE", ignoreCase = true)) {
                        hasLiveOverlay = true
                    }
                }
            }
        }
        val badges = videoRenderer.optJSONArray("badges")
        var hasLiveBadge = false
        if (badges != null) {
            for (i in 0 until badges.length()) {
                val badge = badges.optJSONObject(i)?.optJSONObject("metadataBadgeRenderer") ?: continue
                if (badge.optString("style").contains("LIVE", ignoreCase = true) ||
                    badge.optString("label").equals("LIVE", ignoreCase = true)
                ) {
                    hasLiveBadge = true
                    break
                }
            }
        }
        val viewsLower = viewsStr.lowercase(Locale.ROOT)
        val isWatchingNow = (viewsLower.contains("watching") || viewsLower.contains("menonton")) &&
            uploadDate.isBlank() && durationSec == 0L
        val isLive = durationSec == 0L && (hasLiveOverlay || hasLiveBadge || isWatchingNow)
        val navEndpoint = videoRenderer.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
        val navStartSeconds = navEndpoint?.optLong("startTimeSeconds", 0L) ?: 0L
        val calculatedSeconds = if (navStartSeconds > 0L) navStartSeconds else if (videoProgress in 1..98 && durationSec > 0L) {
            (videoProgress / 100.0 * durationSec).toLong()
        } else 0L

        val canonicalUrl = "$HOST/watch?v=$videoId"
        val meta = VideoMeta(
            viewsText = if (isLive) "" else viewsStr,
            publishedText = uploadDate,
            watchingText = if (isLive) viewsStr else "",
            live = isLive,
            progressPercent = videoProgress,
            resumePositionMs = calculatedSeconds * 1000L
        )
        rememberMeta(canonicalUrl, meta)

        return StreamInfoItem(
            ServiceList.YouTube.serviceId,
            canonicalUrl,
            title,
            if (isLive) StreamType.LIVE_STREAM else StreamType.VIDEO_STREAM
        ).apply {
            uploaderName = uploader
            uploaderUrl = channelUrl
            duration = if (isLive) 0L else durationSec
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
        val isMixLockup = contentType == "LOCKUP_CONTENT_TYPE_MIX"
        val isPlaylistLockup = isMixLockup || contentType in setOf("LOCKUP_CONTENT_TYPE_PLAYLIST", "LOCKUP_CONTENT_TYPE_PODCAST")
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
            channelIdByUrl.put(channelUrl, channelBrowseId)
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
            val countLabel = badgeText?.takeIf { it.isNotBlank() } ?: if (isMixLockup) "Mix" else "Playlist"
            val effectiveIsMix = isMixLockup || countLabel.equals("Mix", ignoreCase = true) || pid.startsWith("RD")
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
                isPlaylist = true,
                isMix = effectiveIsMix
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
            metaByUrl.put(itemUrl, meta)
            return ParsedLockup(item, meta)
        }

        val videoId = lockup.optString("contentId").takeIf { it.length == 11 }
            ?: referencedVideoIds.firstOrNull()
            ?: return null

        val isLive = hasLiveIndicator(contentImage, badgeText, watchingText, publishedText)
        val durationSec = if (isLive) 0L else parseDuration(badgeText.orEmpty())
        val rememberedPlaylist = WatchHistory.playlistRefForVideoId(videoId)
        val resolvedPlaylistId = playlistId ?: rememberedPlaylist?.playlistId
        val resolvedPlaylistTitle = rememberedPlaylist?.playlistTitle
        val resolvedPlaylistCount = rememberedPlaylist?.playlistVideoCount

        val itemUrl = "$HOST/watch?v=$videoId"
        val countSource = viewsText.ifBlank { watchingText }
        val item = StreamInfoItem(
            ServiceList.YouTube.serviceId,
            itemUrl,
            title,
            if (isLive) StreamType.LIVE_STREAM else StreamType.VIDEO_STREAM
        ).apply {
            uploaderName = uploader
            uploaderUrl = channelUrl
            duration = durationSec
            viewCount = if (countSource.isBlank()) -1L else parseViews(countSource)
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

    private fun isLiveBadgeText(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        return text.equals("LIVE", ignoreCase = true) || text.equals("SEDANG LIVE", ignoreCase = true)
    }

    private fun hasLiveIndicator(
        contentImage: JSONObject?,
        badgeText: String?,
        watchingText: String,
        publishedText: String,
    ): Boolean {
        if (isLiveBadgeText(badgeText)) return true
        // Video with a parsed duration badge (e.g. "1:12:30") is a VOD/replay, not an ongoing live stream.
        if (parseDuration(badgeText.orEmpty()) > 0L) return false

        val thumbModel = contentImage?.optJSONObject("thumbnailViewModel")
            ?: contentImage?.optJSONObject("collectionThumbnailViewModel")
                ?.optJSONObject("primaryThumbnail")?.optJSONObject("thumbnailViewModel")
        val overlays = thumbModel?.optJSONArray("overlays")
        if (overlays != null) {
            for (i in 0 until overlays.length()) {
                val overlay = overlays.optJSONObject(i) ?: continue
                val badgeArrays = listOfNotNull(
                    overlay.optJSONObject("thumbnailBottomOverlayViewModel")?.optJSONArray("badges"),
                    overlay.optJSONObject("thumbnailOverlayBadgeViewModel")?.optJSONArray("thumbnailBadges")
                )
                for (arr in badgeArrays) {
                    for (j in 0 until arr.length()) {
                        val badge = arr.optJSONObject(j)?.optJSONObject("thumbnailBadgeViewModel") ?: continue
                        if (badge.optString("badgeStyle").contains("LIVE", ignoreCase = true) ||
                            isLiveBadgeText(badge.optString("text"))
                        ) {
                            return true
                        }
                    }
                }
                val timeStatus = overlay.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                if (timeStatus != null) {
                    if (timeStatus.optString("style").contains("LIVE", ignoreCase = true) ||
                        isLiveBadgeText(text(timeStatus.optJSONObject("text")))
                    ) {
                        return true
                    }
                }
            }
        }
        // Active livestreams show "N watching" without a duration badge or past publish date.
        return watchingText.isNotBlank() && publishedText.isBlank() && parseDuration(badgeText.orEmpty()) == 0L
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
