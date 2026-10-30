package id.flufclip.player

import kotlinx.coroutines.Dispatchers
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
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.util.concurrent.TimeUnit

object VideoRepository {
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
            val result = runCatching { ChannelInfo.getInfo(NewPipe.getService("YouTube"), url).avatars.lastOrNull()?.url }.getOrNull().orEmpty()
            avatarCache[url] = result
            result.ifBlank { null }
        }
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
        ChannelPage(info, videos)
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
        val tab = page.info.tabs.firstOrNull { filter in it.contentFilters } ?: return@withContext emptyList()
        val result = ChannelTabInfo.getInfo(NewPipe.getService("YouTube"), tab)
        if (result.relatedItems.isEmpty() && result.errors.isNotEmpty()) error("Gagal membaca tab: ${result.errors.first().message}")
        result.relatedItems
    }
    suspend fun playlist(url: String): PlaylistInfo = withContext(Dispatchers.IO) { PlaylistInfo.getInfo(url) }
    suspend fun resolve(url: String): StreamInfo = withContext(Dispatchers.IO) { StreamInfo.getInfo(url) }
    fun progressive(info: StreamInfo) = info.videoStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.sortedByDescending { it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0 }
    fun videos(info: StreamInfo) = info.videoOnlyStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.sortedByDescending { it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0 }
    fun audios(info: StreamInfo) = info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.sortedByDescending { it.averageBitrate }
}

 data class ChannelPage(val info: ChannelInfo, val videos: List<StreamInfoItem>)

data class SearchPage(val items: List<InfoItem>, val extractor: SearchExtractor, val next: Page?)

data class CommentsPage(val items: List<CommentsInfoItem>, val next: Page?)
