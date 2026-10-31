package id.flufclip.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
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
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.MediaFormat
import java.io.File
import java.util.concurrent.TimeUnit

object VideoRepository {
    const val TAB_MEMBERSHIP = "membership"
    private val avatarCache = ConcurrentHashMap<String, String>()
    private val avatarRequests = Semaphore(3)
    private val uploadsCache = ConcurrentHashMap<String, List<StreamInfoItem>>()
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    init {
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val builder = okhttp3.Request.Builder().url(request.url()).method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                request.headers().forEach { (key, values) -> values.forEach { builder.addHeader(key, it) } }
                if (request.headers().keys.none { it.equals("User-Agent", true) }) builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                val reqUrl = request.url()
                if (YouTubeAuth.isLoggedIn && (reqUrl.contains("youtube.com") || reqUrl.contains("googlevideo.com"))) {
                    YouTubeAuth.buildAuthHeaders().forEach { (key, value) ->
                        if (request.headers().keys.none { it.equals(key, true) }) {
                            builder.header(key, value)
                        }
                    }
                }
                client.newCall(builder.build()).execute().use { response ->
                    if (response.code == 429) throw ReCaptchaException("Terlalu banyak permintaan", request.url())
                    return Response(response.code, response.message, response.headers.toMultimap(), response.body?.string(), response.request.url.toString())
                }
            }
        })
    }
    suspend fun search(query: String): List<InfoItem> = withContext(Dispatchers.IO) {
        val extractor = NewPipe.getService("YouTube").getSearchExtractor(query)
        extractor.fetchPage()
        val info = SearchInfo.getInfo(extractor)
        val items = info.relatedItems
        if (items.isEmpty() && info.errors.isNotEmpty()) {
            throw IllegalStateException("Gagal membaca hasil YouTube: ${info.errors.first().message ?: info.errors.first().javaClass.simpleName}")
        }
        items
    }
    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        NewPipe.getService("YouTube").suggestionExtractor.suggestionList(query)
    }
    suspend fun comments(url: String): CommentsInfo = withContext(Dispatchers.IO) {
        val info = CommentsInfo.getInfo(NewPipe.getService("YouTube"), url)
        if (!info.isCommentsDisabled && info.relatedItems.isEmpty() && info.errors.isNotEmpty()) error(info.errors.first().message ?: "Komentar gagal dimuat")
        info
    }
    suspend fun moreComments(info: CommentsInfo, page: Page): CommentsPage = withContext(Dispatchers.IO) {
        val result = CommentsInfo.getMoreItems(info, page)
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error(result.errors.first().message ?: "Komentar gagal dimuat")
        CommentsPage(result.items, result.nextPage)
    }
    suspend fun replies(info: CommentsInfo, page: Page): CommentsPage = withContext(Dispatchers.IO) {
        val result = CommentsInfo.getMoreItems(info, page)
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error(result.errors.first().message ?: "Balasan gagal dimuat")
        CommentsPage(result.items, result.nextPage)
    }
    suspend fun searchPage(query: String, filter: String = "all"): SearchPage = withContext(Dispatchers.IO) {
        val service = NewPipe.getService("YouTube")
        val extractor = if (filter == "all") service.getSearchExtractor(query) else service.getSearchExtractor(query, listOf(filter), "")
        extractor.fetchPage()
        val info = SearchInfo.getInfo(extractor)
        if (info.relatedItems.isEmpty() && info.errors.isNotEmpty()) error("Gagal membaca hasil: ${info.errors.first().message}")
        SearchPage(info.relatedItems, extractor, info.nextPage)
    }
    suspend fun moreSearch(page: SearchPage): SearchPage = withContext(Dispatchers.IO) {
        val next = page.next ?: return@withContext page.copy(items = emptyList())
        val result = page.extractor.getPage(next)
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error("Gagal membaca halaman: ${result.errors.first().message}")
        SearchPage(result.items, page.extractor, result.nextPage)
    }
    suspend fun home(): List<StreamInfoItem> = withContext(Dispatchers.IO) {
        val extractor = NewPipe.getService("YouTube").kioskList.defaultKioskExtractor
        extractor.fetchPage()
        val info = KioskInfo.getInfo(extractor)
        if (info.relatedItems.isEmpty() && info.errors.isNotEmpty()) error("Gagal membaca feed: ${info.errors.first().message}")
        info.relatedItems
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
    fun membershipPlaylistUrl(channelId: String): String? {
        val clean = channelId.trim()
        return if (clean.startsWith("UC") && clean.length > 2) {
            "https://www.youtube.com/playlist?list=UUMO" + clean.removePrefix("UC")
        } else null
    }

    suspend fun channel(url: String): ChannelPage = withContext(Dispatchers.IO) {
        val service = NewPipe.getService("YouTube")
        val info = ChannelInfo.getInfo(service, url)
        val tab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
        val videos = if (tab == null) emptyList() else {
            val result = ChannelTabInfo.getInfo(service, tab)
            if (result.relatedItems.isEmpty() && result.errors.isNotEmpty()) error("Gagal membaca video channel: ${result.errors.first().message}")
            result.relatedItems.filterIsInstance<StreamInfoItem>()
        }
        val uumo = membershipPlaylistUrl(info.id)
        var hasMembership = false
        if (uumo != null) {
            try {
                val pl = PlaylistInfo.getInfo(uumo)
                if (pl.relatedItems.isNotEmpty()) {
                    hasMembership = true
                }
            } catch (_: Exception) {
                // Not all channels have active membership playlists
            }
        }
        ChannelPage(info, videos, hasMembership = hasMembership, membershipUrl = if (hasMembership) uumo else null)
    }
    /** Video dari tab Video channel untuk bagian "Dari channel ini"; hasil yang berhasil disimpan di memori selama aplikasi berjalan. */
    suspend fun channelUploads(url: String): List<StreamInfoItem> = withContext(Dispatchers.IO) {
        uploadsCache[url]?.let { return@withContext it }
        val service = NewPipe.getService("YouTube")
        val tab = ChannelInfo.getInfo(service, url).tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
        val items = if (tab == null) emptyList() else {
            val result = ChannelTabInfo.getInfo(service, tab)
            if (result.relatedItems.isEmpty() && result.errors.isNotEmpty()) error("Gagal membaca video channel: ${result.errors.first().message}")
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
        if (result.relatedItems.isEmpty() && result.errors.isNotEmpty()) error("Gagal membaca tab: ${result.errors.first().message}")
        result.relatedItems
    }
    suspend fun channelTabPage(page: ChannelPage, filter: String): BrowsePage = withContext(Dispatchers.IO) {
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
        if (info.relatedItems.isEmpty() && info.errors.isNotEmpty()) error(info.errors.first().message ?: "Tab gagal dimuat")
        BrowsePage(info.relatedItems, info.nextPage)
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
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error(result.errors.first().message ?: "Halaman gagal dimuat")
        BrowsePage(result.items, result.nextPage)
    }
    suspend fun morePlaylist(url: String, next: Page): BrowsePage = withContext(Dispatchers.IO) {
        val result = PlaylistInfo.getMoreItems(NewPipe.getService("YouTube"), url, next)
        if (result.items.isEmpty() && result.errors.isNotEmpty()) error(result.errors.first().message ?: "Playlist gagal dimuat")
        BrowsePage(result.items, result.nextPage)
    }
    suspend fun playlist(url: String): PlaylistInfo = withContext(Dispatchers.IO) { PlaylistInfo.getInfo(url) }
    suspend fun resolve(url: String, context: android.content.Context? = null): StreamInfo = withContext(Dispatchers.IO) {
        try {
            val info = StreamInfo.getInfo(url)
            val hasStreams = info.videoStreams.isNotEmpty() || info.videoOnlyStreams.isNotEmpty() || !info.hlsUrl.isNullOrBlank()
            if (!hasStreams && YouTubeAuth.isLoggedIn && context != null) {
                resolveWithYtDlp(url, context) ?: info
            } else {
                info
            }
        } catch (e: Exception) {
            if (YouTubeAuth.isLoggedIn && context != null) {
                resolveWithYtDlp(url, context) ?: throw e
            } else {
                throw e
            }
        }
    }

    private fun resolveWithYtDlp(url: String, context: android.content.Context): StreamInfo? {
        var cookieFile: File? = null
        return try {
            com.yausername.youtubedl_android.YoutubeDL.init(context)
            val request = com.yausername.youtubedl_android.YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                if (YouTubeAuth.isLoggedIn) {
                    cookieFile = YouTubeAuth.writeNetscapeCookiesFile(context.cacheDir)
                    if (cookieFile != null && cookieFile!!.exists()) {
                        addOption("--cookies", cookieFile!!.absolutePath)
                    }
                }
            }
            val videoInfo = com.yausername.youtubedl_android.YoutubeDL.getInstance().getInfo(request)
            val title = videoInfo.title ?: videoInfo.fulltitle ?: "Video"
            val uploader = videoInfo.uploader ?: ""
            val id = videoInfo.id ?: ""
            val duration = videoInfo.duration.toLong()

            val streamInfo = StreamInfo(0, url, url, StreamType.VIDEO_STREAM, id, title, 0).apply {
                uploaderName = uploader
                this.duration = duration
                val desc = videoInfo.description
                if (!desc.isNullOrBlank()) {
                    description = org.schabi.newpipe.extractor.stream.Description(desc, org.schabi.newpipe.extractor.stream.Description.PLAIN_TEXT)
                }
                val thumb = videoInfo.thumbnail
                if (!thumb.isNullOrBlank()) {
                    thumbnails = listOf(org.schabi.newpipe.extractor.Image(thumb, org.schabi.newpipe.extractor.Image.HEIGHT_UNKNOWN, org.schabi.newpipe.extractor.Image.WIDTH_UNKNOWN, org.schabi.newpipe.extractor.Image.ResolutionLevel.HIGH))
                }
                val manifest = videoInfo.manifestUrl
                val directUrl = videoInfo.url
                if (!manifest.isNullOrBlank()) {
                    hlsUrl = manifest
                } else if (!directUrl.isNullOrBlank() && directUrl.contains(".m3u8")) {
                    hlsUrl = directUrl
                }

                val progressiveList = mutableListOf<VideoStream>()
                val videoOnlyList = mutableListOf<VideoStream>()
                val audioList = mutableListOf<AudioStream>()

                videoInfo.formats?.forEach { fmt ->
                    val fUrl = fmt.url ?: return@forEach
                    val isVideo = !fmt.vcodec.isNullOrBlank() && fmt.vcodec != "none"
                    val isAudio = !fmt.acodec.isNullOrBlank() && fmt.acodec != "none"
                    val height = fmt.height
                    val res = if (height > 0) "${height}p" else VideoStream.RESOLUTION_UNKNOWN

                    if (isVideo && isAudio) {
                        progressiveList.add(
                            VideoStream.Builder()
                                .setContent(fUrl, true)
                                .setIsVideoOnly(false)
                                .setResolution(res)
                                .setMediaFormat(MediaFormat.MPEG_4)
                                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                                .build()
                        )
                    } else if (isVideo) {
                        videoOnlyList.add(
                            VideoStream.Builder()
                                .setContent(fUrl, true)
                                .setIsVideoOnly(true)
                                .setResolution(res)
                                .setMediaFormat(MediaFormat.MPEG_4)
                                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                                .build()
                        )
                    } else if (isAudio) {
                        audioList.add(
                            AudioStream.Builder()
                                .setContent(fUrl, true)
                                .setAverageBitrate(fmt.abr.takeIf { it > 0 } ?: AudioStream.UNKNOWN_BITRATE)
                                .setMediaFormat(MediaFormat.M4A)
                                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                                .setAudioTrackType(org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL)
                                .build()
                        )
                    }
                }

                if (progressiveList.isNotEmpty()) {
                    videoStreams = progressiveList
                }
                if (videoOnlyList.isNotEmpty()) {
                    videoOnlyStreams = videoOnlyList
                }
                if (audioList.isNotEmpty()) {
                    audioStreams = audioList
                }
                if (hlsUrl.isNullOrBlank() && !directUrl.isNullOrBlank() && videoStreams.isEmpty()) {
                    hlsUrl = directUrl
                }
            }
            streamInfo
        } catch (_: Exception) {
            null
        } finally {
            cookieFile?.delete()
        }
    }

    fun progressive(info: StreamInfo) = info.videoStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.sortedByDescending { it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0 }
    fun videos(info: StreamInfo) = info.videoOnlyStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.sortedByDescending { it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0 }
    fun audios(info: StreamInfo) = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.sortedByDescending { it.averageBitrate }
}

data class ChannelPage(
    val info: ChannelInfo,
    val videos: List<StreamInfoItem>,
    val hasMembership: Boolean = false,
    val membershipUrl: String? = null
)

data class SearchPage(val items: List<InfoItem>, val extractor: SearchExtractor, val next: Page?)

data class CommentsPage(val items: List<CommentsInfoItem>, val next: Page?)

data class BrowsePage(val items: List<InfoItem>, val next: Page?)
