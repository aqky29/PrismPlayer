package id.flufclip.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.io.File
import java.util.concurrent.TimeUnit

object VideoRepository {
    const val TAB_HOME = "home"
    const val TAB_POSTS = "posts"
    const val TAB_MEMBERSHIP = "membership"
    private val avatarCache = ConcurrentHashMap<String, String>()
    private val avatarRequests = Semaphore(3)
    private val uploadsCache = ConcurrentHashMap<String, List<StreamInfoItem>>()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val extractorClient = client.newBuilder().addNetworkInterceptor { chain ->
        val request = chain.request()
        val safeRequest = if (permitsAccountHeaders(request.url)) request else request.newBuilder().apply {
            sensitiveAccountHeaders.forEach { removeHeader(it) }
        }.build()
        chain.proceed(safeRequest)
    }.build()
    private data class CachedInfo(val info: StreamInfo, val at: Long)
    private val resolveCache = LinkedHashMap<String, CachedInfo>(16, .75f, true)
    private val resolveJobs = ConcurrentHashMap<String, CompletableDeferred<StreamInfo>>()
    private val embeddedAudio = java.util.Collections.synchronizedMap(java.util.WeakHashMap<StreamInfo, List<EmbeddedAudioTrack>>())
    private val liveDashDvr = java.util.Collections.synchronizedMap(java.util.WeakHashMap<StreamInfo, Boolean>())
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private data class LiveInfo(val liveNow: Boolean, val wasLive: Boolean)
    private val liveInfoCache = object : LinkedHashMap<String, LiveInfo>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LiveInfo>): Boolean = size > 32
    }
    private const val COMMENT_EMOJI_MAX = 2000
    private val commentEmojiCache = object : LinkedHashMap<String, Map<String, String>>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Map<String, String>>): Boolean = size > COMMENT_EMOJI_MAX
    }
    private val commentAvatarCache = object : LinkedHashMap<String, String>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > COMMENT_EMOJI_MAX
    }
    /** Retry publik tanpa cookie saat NewPipe gagal mendapatkan visitorData pada sesi login. */
    private val authDisabled = ThreadLocal.withInitial { false }
    private const val RESOLVE_TTL_MS = 10 * 60 * 1000L
    private const val RESOLVE_MAX = 10
    init {
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val builder = okhttp3.Request.Builder().url(request.url()).method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                request.headers().forEach { (key, values) -> values.forEach { builder.addHeader(key, it) } }
                if (request.headers().keys.none { it.equals("User-Agent", true) }) builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                val reqUrl = request.url()
                val isYouTubeHost = permitsAccountHeaders(builder.build().url)
                if (YouTubeAuth.isLoggedIn && authDisabled.get() != true && isYouTubeHost) {
                    val authHeaders = YouTubeAuth.buildAuthHeaders()
                    val existingCookie = request.headers().entries.firstOrNull { it.key.equals("Cookie", true) }?.value?.joinToString("; ")
                    val authCookie = authHeaders["Cookie"]
                    if (!authCookie.isNullOrBlank()) {
                        val merged = if (!existingCookie.isNullOrBlank()) "$existingCookie; $authCookie" else authCookie
                        builder.header("Cookie", merged)
                    }
                    authHeaders.forEach { (key, value) ->
                        if (!key.equals("Cookie", true)) {
                            builder.header(key, value)
                        }
                    }
                }
                extractorClient.newCall(builder.build()).execute().use { response ->
                    if (response.code == 429) throw ReCaptchaException("Terlalu banyak permintaan", request.url())
                    val body = response.body?.string()
                    if (body != null && reqUrl.contains("/youtubei/v1/player") &&
                        (body.contains("\"isLiveContent\"") || body.contains("\"liveBroadcastDetails\""))
                    ) rememberLiveInfo(body)
                    if (body != null && reqUrl.contains("/youtubei/v1/next") &&
                        (body.contains("\"commentEntityPayload\"") || body.contains("\"commentRenderer\""))
                    ) rememberCommentEmojis(body)
                    return Response(response.code, response.message, response.headers.toMultimap(), body, response.request.url.toString())
                }
            }
        })
    }

    /**
     * NewPipe hanya memberi POST_LIVE_STREAM selama `isPostLiveDvr` masih ada; setelah replay diproses,
     * `isLiveContent` tetap satu-satunya penanda bahwa video pernah live. Respons player sudah diambil
     * NewPipe, jadi simpan penandanya di sini tanpa permintaan tambahan.
     */
    private fun rememberLiveInfo(body: String) {
        val json = try { org.json.JSONObject(body) } catch (_: Exception) { return }
        val details = json.optJSONObject("videoDetails") ?: return
        val id = details.optString("videoId")
        if (id.isBlank()) return
        val broadcast = json.optJSONObject("microformat")?.optJSONObject("playerMicroformatRenderer")
            ?.optJSONObject("liveBroadcastDetails")
        // Request metadata NewPipe hanya meminta sebagian field, jadi jangan menimpa sinyal yang sudah lengkap.
        val liveNow = when {
            broadcast != null -> broadcast.optBoolean("isLiveNow")
            details.has("isLive") -> details.optBoolean("isLive")
            else -> null
        }
        val wasLive = when {
            broadcast != null -> broadcast.optBoolean("isLiveNow") || broadcast.has("endTimestamp")
            details.has("isLiveContent") -> details.optBoolean("isLiveContent")
            else -> null
        }
        if (liveNow == null && wasLive == null) return
        synchronized(liveInfoCache) {
            val old = liveInfoCache[id]
            liveInfoCache[id] = LiveInfo(liveNow ?: old?.liveNow ?: false, wasLive ?: old?.wasLive ?: false)
        }
    }

    private fun liveInfoOf(url: String): LiveInfo? {
        val id = InnerTubePlayer.videoIdOf(url) ?: return null
        return synchronized(liveInfoCache) { liveInfoCache[id] }
    }

    private fun rememberCommentEmojis(body: String) {
        val parsed = CommentEmojiParser.parseMetadataFromNext(body)
        if (parsed.emojis.isNotEmpty()) {
            synchronized(commentEmojiCache) {
                parsed.emojis.forEach { (commentId, emojis) ->
                    if (emojis.isNotEmpty()) commentEmojiCache[commentId] = emojis
                }
            }
        }
        if (parsed.avatars.isNotEmpty()) {
            synchronized(commentAvatarCache) {
                parsed.avatars.forEach { (commentId, avatarUrl) ->
                    if (avatarUrl.isNotBlank()) commentAvatarCache[commentId] = avatarUrl
                }
            }
        }
    }

    internal fun commentEmojis(commentId: String?): Map<String, String> {
        val id = commentId?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return synchronized(commentEmojiCache) { commentEmojiCache[id] }.orEmpty()
    }

    internal fun commentAvatar(commentId: String?): String? {
        val id = commentId?.takeIf { it.isNotBlank() } ?: return null
        return synchronized(commentAvatarCache) { commentAvatarCache[id] }
    }

    suspend fun search(query: String): List<InfoItem> = withContext(Dispatchers.IO) {
        val extractor = NewPipe.getService("YouTube").getSearchExtractor(query)
        extractor.fetchPage()
        val info = SearchInfo.getInfo(extractor)
        val items = info.relatedItems
        if (items.isEmpty() && info.errors.isNotEmpty()) {
            throw IllegalStateException("Failed to read YouTube results: ${info.errors.first().message ?: info.errors.first().javaClass.simpleName}")
        }
        items
    }
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        NewPipe.getService("YouTube").suggestionExtractor.suggestionList(query)
    }
    suspend fun comments(url: String): CommentsInfo = withContext(Dispatchers.IO) {
        val info = CommentsInfo.getInfo(NewPipe.getService("YouTube"), url)
        if (!info.isCommentsDisabled && info.relatedItems.isEmpty() && info.errors.isNotEmpty()) error(info.errors.first().message ?: "Failed to load comments")
        info
    }
    suspend fun moreComments(info: CommentsInfo, page: Page): CommentsPage = withContext(Dispatchers.IO) {
        val result = CommentsInfo.getMoreItems(info, page)
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error(result.errors.first().message ?: "Failed to load comments")
        CommentsPage(result.items, result.nextPage)
    }
    suspend fun replies(info: CommentsInfo, page: Page): CommentsPage = withContext(Dispatchers.IO) {
        val result = CommentsInfo.getMoreItems(info, page)
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error(result.errors.first().message ?: "Failed to load replies")
        CommentsPage(result.items, result.nextPage)
    }
    suspend fun searchPage(query: String, filter: String = "all"): SearchPage = withContext(Dispatchers.IO) {
        val service = NewPipe.getService("YouTube")
        val extractor = if (filter == "all") service.getSearchExtractor(query) else service.getSearchExtractor(query, listOf(filter), "")
        extractor.fetchPage()
        val info = SearchInfo.getInfo(extractor)
        if (info.relatedItems.isEmpty() && info.errors.isNotEmpty()) error("Failed to read results: ${info.errors.first().message}")
        val correctedSuggestion = info.searchSuggestion?.takeIf {
            it.isNotBlank() && !it.equals(query.trim(), ignoreCase = true)
        }
        SearchPage(
            items = info.relatedItems,
            extractor = extractor,
            next = info.nextPage,
            searchSuggestion = correctedSuggestion,
            isCorrectedSearch = info.isCorrectedSearch && correctedSuggestion != null,
        )
    }
    suspend fun moreSearch(page: SearchPage): SearchPage = withContext(Dispatchers.IO) {
        val next = page.next ?: return@withContext page.copy(items = emptyList())
        val result = page.extractor.getPage(next)
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error("Failed to read page: ${result.errors.first().message}")
        SearchPage(result.items, page.extractor, result.nextPage)
    }
    @Volatile private var liveKioskExtractor: KioskExtractor<*>? = null
    /**
     * Daftar siaran live umum dari kiosk "live" NewPipe (channel topik Live milik YouTube). Pemanggilan ini mengganti
     * ekstraktor yang dipakai [moreLiveKiosk]. Beranda asli YouTube tidak bisa dibaca tanpa login (untuk tamu isinya hanya
     * ajakan menelusuri), sehingga kiosk inilah satu-satunya sumber live yang tidak butuh akun.
     */
    suspend fun liveKiosk(): BrowsePage = withContext(Dispatchers.IO) {
        val extractor = NewPipe.getService("YouTube").kioskList.getExtractorById("live", null)
        extractor.fetchPage()
        val info = KioskInfo.getInfo(extractor)
        if (info.relatedItems.isEmpty() && info.errors.isNotEmpty()) error("Failed to read feed: ${info.errors.first().message}")
        liveKioskExtractor = extractor
        BrowsePage(info.relatedItems, info.nextPage)
    }
    suspend fun moreLiveKiosk(next: Page): BrowsePage = withContext(Dispatchers.IO) {
        val extractor = liveKioskExtractor ?: return@withContext BrowsePage(emptyList(), null)
        val result = extractor.getPage(next)
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error("Failed to read page: ${result.errors.first().message}")
        BrowsePage(result.items, result.nextPage)
    }
    suspend fun home(): List<StreamInfoItem> = liveKiosk().items.filterIsInstance<StreamInfoItem>()

    private class LiveCheck(val at: Long, val items: List<StreamInfoItem>)
    private val liveCache = ConcurrentHashMap<String, LiveCheck>()
    private val liveRequests = Semaphore(3)
    private const val LIVE_TTL_MS = 5 * 60 * 1000L
    private const val LIVE_TIMEOUT_MS = 12_000L
    /**
     * Siaran yang sedang berlangsung milik channel-channel ini, berurutan menurut urutan [channelUrls]. Tiap channel dicek
     * lewat tab Live-nya (dua permintaan, paling banyak tiga channel sekaligus); channel yang gagal atau lambat dilewati
     * diam-diam. Hasil disimpan lima menit supaya penyegaran beranda berulang tidak mengulang permintaan.
     */
    suspend fun liveFromChannels(channelUrls: List<String>): List<StreamInfoItem> = coroutineScope {
        channelUrls.filter { it.isNotBlank() }.distinct().map { url -> async { liveOf(url) } }.awaitAll().flatten()
    }
    private suspend fun liveOf(url: String): List<StreamInfoItem> {
        liveCache[url]?.takeIf { System.currentTimeMillis() - it.at < LIVE_TTL_MS }?.let { return it.items }
        val items = liveRequests.withPermit {
            try {
                withTimeoutOrNull(LIVE_TIMEOUT_MS) {
                    runInterruptible(Dispatchers.IO) {
                        val service = NewPipe.getService("YouTube")
                        val tab = ChannelInfo.getInfo(service, url).tabs.firstOrNull { ChannelTabs.LIVESTREAMS in it.contentFilters }
                        if (tab == null) emptyList() else ChannelTabInfo.getInfo(service, tab).relatedItems
                            .filterIsInstance<StreamInfoItem>().filter { it.isOngoingLive() }.take(2)
                    }
                } ?: return emptyList()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { return emptyList() }
        }
        liveCache[url] = LiveCheck(System.currentTimeMillis(), items)
        return items
    }
    suspend fun channelAvatar(url: String): String? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        avatarCache[url]?.let { return@withContext it.ifBlank { null } }
        avatarRequests.withPermit {
            avatarCache[url]?.let { return@withPermit it.ifBlank { null } }
            val result = try { ChannelInfo.getInfo(NewPipe.getService("YouTube"), url).avatars.lastOrNull()?.url.orEmpty() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { "" }
            avatarCache[url] = result
            result.ifBlank { null }
        }
    }
    fun cachedAvatar(url: String): String? = avatarCache[url]?.ifBlank { null }
    fun cacheAvatar(url: String, avatarUrl: String) {
        if (url.isNotBlank() && avatarUrl.isNotBlank()) {
            avatarCache[url] = avatarUrl
        }
    }
    fun membershipPlaylistUrl(channelId: String): String? {
        val clean = channelId.trim()
        return if (clean.startsWith("UC") && clean.length > 2) {
            "https://www.youtube.com/playlist?list=UUMO" + clean.removePrefix("UC")
        } else null
    }

    suspend fun channel(url: String): ChannelPage = withContext(Dispatchers.IO) {
        val service = NewPipe.getService("YouTube")
        val info = ChannelInfo.getInfo(service, url)
        val uumo = membershipPlaylistUrl(info.id)
        coroutineScope {
            val videosDeferred = async {
                val tab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
                if (tab == null) emptyList() else {
                    val result = ChannelTabInfo.getInfo(service, tab)
                    if (result.relatedItems.isEmpty() && result.errors.isNotEmpty()) error("Failed to read channel videos: ${result.errors.first().message}")
                    result.relatedItems.filterIsInstance<StreamInfoItem>()
                }
            }
            val uumoDeferred = async {
                if (uumo == null) null
                else runCatching { PlaylistInfo.getInfo(uumo) }.getOrNull()
            }
            val channelBundleDeferred = async {
                runCatching { HomeFeed.fetchChannelBundle(info.id) }.getOrNull()
            }

            val videos = videosDeferred.await()
            val pl = uumoDeferred.await()
            val channelBundle = channelBundleDeferred.await()
            val membershipUi = channelBundle?.membershipUi
            val uiData = channelBundle?.uiData

            var hasMembership = (pl?.relatedItems?.isNotEmpty() == true) || (membershipUi?.hasMembershipSurface == true)
            val memberIds = LinkedHashSet<String>()
            pl?.relatedItems?.forEach { item ->
                item.url.takeIf { it.isNotBlank() }?.let { memberIds.add(InnerTubePlayer.videoIdOf(it) ?: it) }
            }
            membershipUi?.highlighted?.forEach { item ->
                memberIds.add(InnerTubePlayer.videoIdOf(item.url) ?: item.url)
            }
            membershipUi?.recentVideos?.forEach { item ->
                memberIds.add(InnerTubePlayer.videoIdOf(item.url) ?: item.url)
            }

            ChannelPage(
                info = info,
                videos = videos,
                hasMembership = hasMembership,
                membershipUrl = if (hasMembership) uumo else null,
                membershipVideoIds = memberIds,
                membershipUi = membershipUi,
                uiData = uiData
            )
        }
    }
    /** Video dari tab Video channel untuk bagian "From this channel"; hasil yang berhasil disimpan di memori selama aplikasi berjalan. */
    suspend fun channelUploads(url: String): List<StreamInfoItem> = withContext(Dispatchers.IO) {
        uploadsCache[url]?.let { return@withContext it }
        val service = NewPipe.getService("YouTube")
        val tab = ChannelInfo.getInfo(service, url).tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
        val items = if (tab == null) emptyList() else {
            val result = ChannelTabInfo.getInfo(service, tab)
            if (result.relatedItems.isEmpty() && result.errors.isNotEmpty()) error("Failed to read channel videos: ${result.errors.first().message}")
            result.relatedItems.filterIsInstance<StreamInfoItem>()
        }
        items.also { uploadsCache[url] = it }
    }
    suspend fun channelTab(page: ChannelPage, filter: String): List<InfoItem> = withContext(Dispatchers.IO) {
        if (filter == TAB_MEMBERSHIP) {
            val uumo = page.membershipUrl ?: membershipPlaylistUrl(page.info.id)
            if (uumo != null) {
                val pl = PlaylistInfo.getInfo(uumo)
                return@withContext pl.relatedItems
            }
            return@withContext emptyList()
        }
        val tab = page.info.tabs.firstOrNull { filter in it.contentFilters } ?: return@withContext emptyList()
        val result = ChannelTabInfo.getInfo(NewPipe.getService("YouTube"), tab)
        if (result.relatedItems.isEmpty() && result.errors.isNotEmpty()) error("Failed to read tab: ${result.errors.first().message}")
        result.relatedItems
    }
    suspend fun channelTabPage(page: ChannelPage, filter: String): BrowsePage = withContext(Dispatchers.IO) {
        if (filter == TAB_HOME || filter == TAB_POSTS) {
            return@withContext BrowsePage(emptyList(), null)
        }
        if (filter == TAB_MEMBERSHIP) {
            val uumo = page.membershipUrl ?: membershipPlaylistUrl(page.info.id)
            if (uumo != null) {
                val pl = PlaylistInfo.getInfo(uumo)
                return@withContext BrowsePage(pl.relatedItems, pl.nextPage)
            }
            return@withContext BrowsePage(emptyList(), null)
        }
        val tab = page.info.tabs.firstOrNull { filter in it.contentFilters } ?: return@withContext BrowsePage(emptyList(), null)
        val info = ChannelTabInfo.getInfo(NewPipe.getService("YouTube"), tab)
        if (info.relatedItems.isEmpty() && info.errors.isNotEmpty()) error(info.errors.first().message ?: "Failed to load tab")
        BrowsePage(info.relatedItems, info.nextPage)
    }
    suspend fun channelPostsPage(page: ChannelPage, continuation: String? = null): ChannelPostsPage = withContext(Dispatchers.IO) {
        HomeFeed.fetchChannelPosts(
            channelId = page.info.id,
            postsTabParams = page.uiData?.postsTabParams,
            continuation = continuation,
            fallbackAuthor = page.info.name,
            fallbackAvatar = page.info.avatars.lastOrNull()?.url
        )
    }

    suspend fun searchChannel(page: ChannelPage, query: String): List<InfoItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val remote = runCatching {
            HomeFeed.searchInChannel(page.info.id, q, page.uiData?.searchTabParams)
        }.getOrDefault(emptyList())
        if (remote.isNotEmpty()) return@withContext remote
        page.videos.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.uploaderName.orEmpty().contains(q, ignoreCase = true)
        }
    }

    suspend fun moreChannelTab(page: ChannelPage, filter: String, next: Page): BrowsePage = withContext(Dispatchers.IO) {
        if (filter == TAB_MEMBERSHIP) {
            val uumo = page.membershipUrl ?: membershipPlaylistUrl(page.info.id)
            if (uumo != null) {
                return@withContext morePlaylist(uumo, next)
            }
            return@withContext BrowsePage(emptyList(), null)
        }
        val tab = page.info.tabs.firstOrNull { filter in it.contentFilters } ?: return@withContext BrowsePage(emptyList(), null)
        val result = ChannelTabInfo.getMoreItems(NewPipe.getService("YouTube"), tab, next)
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error(result.errors.first().message ?: "Failed to load page")
        BrowsePage(result.items, result.nextPage)
    }
    suspend fun morePlaylist(url: String, next: Page): BrowsePage = withContext(Dispatchers.IO) {
        val result = PlaylistInfo.getMoreItems(NewPipe.getService("YouTube"), url, next)
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error(result.errors.first().message ?: "Failed to load playlist")
        BrowsePage(result.items, result.nextPage)
    }
    suspend fun playlist(url: String): PlaylistInfo = withContext(Dispatchers.IO) { PlaylistInfo.getInfo(url) }

    private fun cachedResolve(key: String): StreamInfo? = synchronized(resolveCache) {
        val entry = resolveCache[key]
        if (entry != null && System.currentTimeMillis() - entry.at > RESOLVE_TTL_MS) {
            resolveCache.remove(key)
            null
        } else entry?.info
    }

    private fun storeResolve(key: String, info: StreamInfo) {
        synchronized(resolveCache) {
            resolveCache[key] = CachedInfo(info, System.currentTimeMillis())
            while (resolveCache.size > RESOLVE_MAX) {
                val eldest = resolveCache.keys.firstOrNull() ?: break
                resolveCache.remove(eldest)
            }
        }
    }

    /** Buang hasil resolve (mis. URL stream ditolak server) supaya permintaan berikutnya mengambil URL baru. */
    fun evict(url: String) {
        val key = "${YouTubeAuth.sessionRevision}|${videoKey(url)}"
        synchronized(resolveCache) { resolveCache.remove(key) }
        resolveJobs.remove(key)?.cancel()
    }

    /** Resolve di latar belakang untuk item berikutnya; error diabaikan karena ini hanya pemanasan cache. */
    fun prefetch(url: String, context: android.content.Context? = null) {
        val revision = YouTubeAuth.sessionRevision
        val key = "$revision|${videoKey(url)}"
        if (cachedResolve(key) != null || resolveJobs.containsKey(key)) return
        resolveScope.launch {
            try {
                // Prefetch tidak masuk resolveJobs: klik user tidak boleh menunggu job latar belakang yang macet.
                val info = withTimeoutOrNull(45_000) { resolveUncached(url, context) }
                if (info != null && revision == YouTubeAuth.sessionRevision) {
                    inspectLiveManifests(info, revision)
                    if (revision == YouTubeAuth.sessionRevision) storeResolve(key, info)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { }
        }
    }

    suspend fun resolve(url: String, context: android.content.Context? = null): StreamInfo {
        val revision = YouTubeAuth.sessionRevision
        val key = "$revision|${videoKey(url)}"
        cachedResolve(key)?.let { return it }
        val pending = CompletableDeferred<StreamInfo>()
        val existing = resolveJobs.putIfAbsent(key, pending)
        if (existing != null) return existing.await()
        resolveScope.launch {
            try {
                check(revision == YouTubeAuth.sessionRevision) { "Account changed" }
                val info = resolveUncached(url, context)
                check(revision == YouTubeAuth.sessionRevision) { "Account changed" }
                if (!pending.isCancelled) {
                    storeResolve(key, info)
                    pending.complete(info)
                    // Probe live DVR asynchronously so initial playback does not wait on extra manifest roundtrips.
                    if (info.isOngoingLive()) {
                        resolveScope.launch {
                            runCatching { inspectLiveManifests(info, revision) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                pending.cancel(e)
                throw e
            } catch (e: Exception) {
                pending.completeExceptionally(e)
            } finally {
                resolveJobs.remove(key, pending)
            }
        }
        return pending.await()
    }

    internal fun preferLiveDash(info: StreamInfo): Boolean = liveDashDvr[info] == true

    private fun inspectLiveManifests(info: StreamInfo, revision: Int) {
        if (!info.isOngoingLive() || revision != YouTubeAuth.sessionRevision) return
        val hls = info.hlsUrl?.takeIf { it.isNotBlank() } ?: return
        val dash = info.dashMpdUrl?.takeIf { it.isNotBlank() } ?: return
        val cookies = YouTubeAuth.getCookies()
        val prefer = inspectLiveDvr(hls, dash, cookies)
        if (revision == YouTubeAuth.sessionRevision) liveDashDvr[info] = prefer
    }

    private suspend fun resolveUncached(url: String, context: android.content.Context?): StreamInfo {
        var info: StreamInfo? = null
        var primaryError: Exception? = null
        try {
            info = StreamInfo.getInfo(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            primaryError = e
            if (e.message?.contains("visitorData", ignoreCase = true) == true) {
                authDisabled.set(true)
                try {
                    info = StreamInfo.getInfo(url)
                    android.util.Log.d("FlufResolve", "primary no-auth success type=${info.streamType} hls=${info.hlsUrl?.length ?: -1} dash=${info.dashMpdUrl?.length ?: -1}")
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    primaryError = e2
                } finally {
                    authDisabled.remove()
                }
            }
        }
        val resolved = info ?: run {
            android.util.Log.d("FlufResolve", "primary failed: ${primaryError?.message}")
            return resolveFallback(url, context) ?: throw (primaryError ?: IllegalStateException("Failed to load video"))
        }
        if (resolved.streamType == StreamType.VIDEO_STREAM) {
            liveInfoOf(resolved.url)?.let { live ->
                if (live.liveNow) resolved.streamType = StreamType.LIVE_STREAM
                else if (live.wasLive) resolved.streamType = StreamType.POST_LIVE_STREAM
            }
        }
        val hasStreams = resolved.videoStreams.isNotEmpty() || resolved.videoOnlyStreams.isNotEmpty() || !resolved.hlsUrl.isNullOrBlank() || !resolved.dashMpdUrl.isNullOrBlank()
        android.util.Log.d("FlufResolve", "primary type=${resolved.streamType} hls=${resolved.hlsUrl?.length ?: -1} dash=${resolved.dashMpdUrl?.length ?: -1}")
        return if (hasStreams) resolved else resolveFallback(url, context) ?: resolved
    }

    /**
     * Jalur cadangan untuk video yang ditolak client NewPipe (terutama konten membership).
     * yt-dlp dengan client web_safari + EJS/QuickJS mencoba menyelesaikan `n` dan memberi URL
     * direct; InnerTube dipakai bila yt-dlp tidak mendapat format.
     */
    private suspend fun resolveFallback(url: String, context: android.content.Context?): StreamInfo? {
        if (!YouTubeAuth.isLoggedIn) return null
        if (context != null) {
            resolveWithYtDlp(url, context)?.let { return it }
        }
        var innerError: Exception? = null
        try {
            return InnerTubePlayer.resolve(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            innerError = e
        }
        val error = innerError
        if (error is PlaybackUnavailableException) throw error
        return null
    }

    @Volatile private var ytDlpUpdateChecked = false

    private fun ensureYtDlpUpdated(context: android.content.Context) {
        if (ytDlpUpdateChecked) return
        ytDlpUpdateChecked = true
        val prefs = context.getSharedPreferences("youtube_auth", android.content.Context.MODE_PRIVATE)
        val last = prefs.getLong("ytdlp_updated_at", 0L)
        if (System.currentTimeMillis() - last < 7L * 24 * 60 * 60 * 1000) return
        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance()
                .updateYoutubeDL(context, com.yausername.youtubedl_android.YoutubeDL.UpdateChannel.STABLE)
            prefs.edit().putLong("ytdlp_updated_at", System.currentTimeMillis()).apply()
        } catch (_: Exception) {
            // Tetap pakai binary bawaan bila update gagal.
        }
    }

    private suspend fun resolveWithYtDlp(url: String, context: android.content.Context): StreamInfo? {
        var cookieFile: File? = null
        return try {
            com.yausername.youtubedl_android.YoutubeDL.init(context)
            YtDlpBinary.ensure(context)
            runCatching { com.yausername.ffmpeg.FFmpeg.init(context) }
            ensureYtDlpUpdated(context)
            val request = com.yausername.youtubedl_android.YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("--extractor-args", "youtube:player_client=web_safari")
                if (YouTubeAuth.isLoggedIn) {
                    cookieFile = YouTubeAuth.writeNetscapeCookiesFile(context.cacheDir)
                    if (cookieFile != null && cookieFile!!.exists()) {
                        addOption("--cookies", cookieFile!!.absolutePath)
                    }
                }
            }
            // getInfo() maps through VideoFormat, which drops yt-dlp's language and language_preference fields.
            // Read the same --dump-json response once so that the format metadata remains available.
            request.addOption("--dump-json")
            val youtubeDl = com.yausername.youtubedl_android.YoutubeDL.getInstance()
            val rawInfo = youtubeDl.execute(request).out
            val videoInfo = org.json.JSONObject(rawInfo)
            fun field(obj: org.json.JSONObject, key: String): String? =
                if (obj.isNull(key)) null else obj.optString(key).takeIf { it.isNotBlank() }
            val rawFormats = videoInfo.optJSONArray("formats")
            val title = field(videoInfo, "title") ?: field(videoInfo, "fulltitle") ?: "Video"
            val uploader = field(videoInfo, "uploader").orEmpty()
            val id = field(videoInfo, "id").orEmpty()
            val duration = videoInfo.optDouble("duration", 0.0).toLong()
            val manifest = field(videoInfo, "manifest_url")
            val directUrl = field(videoInfo, "url")
            val formats = parseYtDlpFormats(rawFormats)
            val liveStatus = field(videoInfo, "live_status").orEmpty()
            val streamType = when {
                liveStatus == "is_live" || liveStatus == "is_upcoming" -> StreamType.LIVE_STREAM
                liveStatus == "was_live" || liveStatus == "post_live" || videoInfo.optBoolean("was_live") -> StreamType.POST_LIVE_STREAM
                else -> StreamType.VIDEO_STREAM
            }

            val streamInfo = StreamInfo(0, url, url, streamType, id, title, 0).apply {
                uploaderName = uploader
                this.duration = duration
                val desc = field(videoInfo, "description")
                if (!desc.isNullOrBlank()) {
                    description = org.schabi.newpipe.extractor.stream.Description(desc, org.schabi.newpipe.extractor.stream.Description.PLAIN_TEXT)
                }
                val thumb = field(videoInfo, "thumbnail")
                if (!thumb.isNullOrBlank()) {
                    thumbnails = listOf(org.schabi.newpipe.extractor.Image(thumb, org.schabi.newpipe.extractor.Image.HEIGHT_UNKNOWN, org.schabi.newpipe.extractor.Image.WIDTH_UNKNOWN, org.schabi.newpipe.extractor.Image.ResolutionLevel.HIGH))
                }

                if (formats.progressive.isNotEmpty()) {
                    videoStreams = formats.progressive
                }
                if (formats.videoOnly.isNotEmpty()) {
                    videoOnlyStreams = formats.videoOnly
                }
                if (formats.audio.isNotEmpty()) {
                    audioStreams = formats.audio
                }
                if (videoStreams.isEmpty() && videoOnlyStreams.isEmpty()) {
                    if (!manifest.isNullOrBlank()) {
                        hlsUrl = manifest
                    } else if (!directUrl.isNullOrBlank() && directUrl.contains(".m3u8")) {
                        hlsUrl = directUrl
                    }
                }
            }
            if (formats.embeddedAudio.isNotEmpty()) embeddedAudio[streamInfo] = formats.embeddedAudio
            val uploaderId = field(videoInfo, "uploader_id").orEmpty()
            if (uploaderId.startsWith("UC")) streamInfo.uploaderUrl = "https://www.youtube.com/channel/$uploaderId"
            else if (uploaderId.startsWith("@")) streamInfo.uploaderUrl = "https://www.youtube.com/$uploaderId"
            val hasVideo = streamInfo.videoStreams.isNotEmpty() || streamInfo.videoOnlyStreams.isNotEmpty() || !streamInfo.hlsUrl.isNullOrBlank() || !streamInfo.dashMpdUrl.isNullOrBlank()
            android.util.Log.d("FlufResolve", "ytdlp type=${streamInfo.streamType} hls=${streamInfo.hlsUrl?.length ?: -1} dash=${streamInfo.dashMpdUrl?.length ?: -1}")
            if (hasVideo) streamInfo else null
        } catch (_: Exception) {
            null
        } finally {
            cookieFile?.delete()
        }
    }

    fun progressive(info: StreamInfo) = info.videoStreams.filter {
        it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.HLS)
    }.sortedByDescending { it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0 }
    fun videos(info: StreamInfo) = info.videoOnlyStreams.filter {
        it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.HLS)
    }.sortedByDescending { it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0 }
    fun audios(info: StreamInfo) = info.audioStreams.filter {
        it.isUrl && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.deliveryMethod == DeliveryMethod.HLS)
    }.sortedByDescending { it.averageBitrate }
    internal fun embeddedAudio(info: StreamInfo): List<EmbeddedAudioTrack> = embeddedAudio[info].orEmpty()
    internal fun embeddedAudio(info: StreamInfo, video: VideoStream?): AudioTrackMetadata? =
        if (video == null) null else embeddedAudio(info).firstOrNull { it.video.content == video.content }?.metadata
}

data class ChannelPerkItem(
    val title: String,
    val kind: String = "videos"
)

data class ChannelMembershipUiData(
    val isJoinedMember: Boolean = false,
    val headerButtonLabel: String = "Join",
    val memberTierTitle: String? = null,
    val memberStatusLabel: String? = null,
    val memberTenureLabel: String? = null,
    val memberAvatarUrl: String? = null,
    val badgeUrl: String? = null,
    val ourMembersTitle: String? = null,
    val ourMembersSubtitle: String? = null,
    val ourMembersAvatars: List<String> = emptyList(),
    val perks: List<ChannelPerkItem> = emptyList(),
    val highlighted: List<StreamInfoItem> = emptyList(),
    val recentVideos: List<StreamInfoItem> = emptyList()
) {
    val hasMembershipSurface: Boolean
        get() = isJoinedMember || headerButtonLabel.equals("See perks", ignoreCase = true) ||
            highlighted.isNotEmpty() || recentVideos.isNotEmpty() || ourMembersAvatars.isNotEmpty()
}

data class ChannelLink(
    val title: String,
    val url: String
)

data class ChannelHomeShelf(
    val title: String,
    val videos: List<StreamInfoItem> = emptyList(),
    val shorts: List<ShortsCard> = emptyList(),
    val playlists: List<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem> = emptyList(),
    val targetTab: String? = null
)

data class ChannelPost(
    val postId: String,
    val author: String,
    val authorAvatarUrl: String?,
    val publishedText: String,
    val text: String,
    val images: List<String> = emptyList(),
    val attachedVideo: StreamInfoItem? = null,
    val pollChoices: List<String> = emptyList(),
    val voteCountText: String? = null,
    val replyCountText: String? = null
)

data class ChannelPostsPage(
    val posts: List<ChannelPost>,
    val nextToken: String? = null
)

data class ChannelUiData(
    val handle: String? = null,
    val subscriberCountText: String? = null,
    val videoCountText: String? = null,
    val description: String? = null,
    val linksSummary: String? = null,
    val links: List<ChannelLink> = emptyList(),
    val homeTabParams: String? = null,
    val postsTabParams: String? = null,
    val searchTabParams: String? = null,
    val hasHomeTab: Boolean = false,
    val hasPostsTab: Boolean = false,
    val isSubscribed: Boolean? = null,
    val homeShelves: List<ChannelHomeShelf> = emptyList()
)

data class ChannelFetchBundle(
    val uiData: ChannelUiData? = null,
    val membershipUi: ChannelMembershipUiData? = null
)

data class ChannelPage(
    val info: ChannelInfo,
    val videos: List<StreamInfoItem>,
    val hasMembership: Boolean = false,
    val membershipUrl: String? = null,
    val membershipVideoIds: Set<String> = emptySet(),
    val membershipUi: ChannelMembershipUiData? = null,
    val uiData: ChannelUiData? = null
) {
    fun isMemberVideo(item: StreamInfoItem): Boolean {
        if (HomeFeed.metaOf(item.url)?.membersOnly == true) return true
        val key = InnerTubePlayer.videoIdOf(item.url) ?: item.url
        return key in membershipVideoIds
    }
}

data class SearchPage(
    val items: List<InfoItem>,
    val extractor: SearchExtractor,
    val next: Page?,
    val searchSuggestion: String? = null,
    val isCorrectedSearch: Boolean = false,
)

data class CommentsPage(val items: List<CommentsInfoItem>, val next: Page?)

data class BrowsePage(val items: List<InfoItem>, val next: Page?)
