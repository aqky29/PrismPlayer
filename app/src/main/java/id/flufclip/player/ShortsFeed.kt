package id.flufclip.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Metadata tambahan Shorts dari InnerTube (audio pivot dan link ke video sumber/remix). */
data class ShortsMeta(
    val audioTitle: String? = null,
    val audioThumbnail: String? = null,
    val sourceVideoId: String? = null,
    val sourceVideoTitle: String? = null,
)

/** Satu halaman urutan Shorts. [next] adalah token continuation untuk halaman berikutnya, atau null bila sudah habis. */
data class ShortsSeqPage(val items: List<StreamInfoItem>, val next: String?)

/**
 * Urutan Shorts dari endpoint `reel/reel_watch_sequence`, endpoint yang sama dengan tab Shorts YouTube
 * (lihat docs/ANALISIS_SHORTS_YOUTUBE_FLUFPLAY.md). Item hanya berisi ID video; judul, channel, dan stream
 * diambil kemudian lewat [VideoRepository.resolve] saat Short dibuka. Saat login, cookie dan SAPISIDHASH
 * ditambahkan agar urutannya mengikuti akun; bila permintaan berauth gagal, dicoba ulang sebagai tamu.
 */
object ShortsFeed {
    private const val HOST = "https://www.youtube.com"
    private const val AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).addNetworkInterceptor(accountHeaderInterceptor).build()
    private val jsonType = "application/json".toMediaType()
    private val versionRegex = Regex("\"INNERTUBE_CONTEXT_CLIENT_VERSION\":\"([^\"]+)\"")
    private val tokenRegex = Regex("\"sequenceContinuation\":\"([^\"]+)\"")
    private val seedRegex = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
    @Volatile private var clientVersion: String? = null

    private class Sequence(val ids: List<String>, val next: String?)

    private val lang: String get() = Locale.getDefault().toLanguageTag().substringBefore('-').ifBlank { "en" }
    private val country: String get() = Locale.getDefault().country.ifBlank { "US" }

    private class Warm(val page: ShortsSeqPage, val at: Long, val revision: Int)
    @Volatile private var warm: Warm? = null
    private const val WARM_TTL_MS = 3 * 60 * 1000L

    /**
     * Siapkan halaman pertama di latar belakang supaya membuka tab Shorts tidak menunggu jaringan. Hasilnya dipakai
     * sekali oleh [start] dan dibuang setelah 3 menit (token urutan terikat sesi). Mengembalikan null bila gagal.
     */
    suspend fun warm(): ShortsSeqPage? {
        val revision = YouTubeAuth.sessionRevision
        warm?.takeIf { it.revision == revision && System.currentTimeMillis() - it.at < WARM_TTL_MS }?.let { return it.page }
        return try { load(revision) {}.also { if (revision == YouTubeAuth.sessionRevision) warm = Warm(it, System.currentTimeMillis(), revision) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    /**
     * Halaman pertama: Short pembuka dari halaman /shorts, disusul entri pertama urutan. [onSeed] dipanggil begitu
     * Short pembuka diketahui, sebelum permintaan urutan selesai, agar pemuatannya bisa berjalan paralel.
     */
    suspend fun start(onSeed: suspend (StreamInfoItem) -> Unit = {}): ShortsSeqPage {
        val revision = YouTubeAuth.sessionRevision
        val ready = warm?.takeIf { it.revision == revision && System.currentTimeMillis() - it.at < WARM_TTL_MS }
        warm = null
        return ready?.page ?: load(revision, onSeed)
    }

    private suspend fun load(revision: Int, onSeed: suspend (StreamInfoItem) -> Unit): ShortsSeqPage = withContext(Dispatchers.IO) {
        val html = fetch("$HOST/shorts?hl=$lang&gl=$country", revision = revision)
        val version = versionRegex.find(html)?.groupValues?.get(1) ?: error("YouTube client version not found")
        clientVersion = version
        val token = tokenRegex.find(html)?.groupValues?.get(1) ?: error("Shorts sequence token not found")
        val seed = html.indexOf("\"replacementEndpoint\"").takeIf { it >= 0 }?.let { from ->
            seedRegex.find(html.substring(from, minOf(html.length, from + 4000)))?.groupValues?.get(1)
        }
        check(revision == YouTubeAuth.sessionRevision) { "Account changed" }
        if (seed != null) onSeed(item(seed))
        val page = sequence(version, JSONObject().put("sequenceParams", token), revision)
        ShortsSeqPage((listOfNotNull(seed) + page.ids).distinct().map(::item), page.next)
    }

    /** Halaman berikutnya memakai token continuation dari halaman sebelumnya. */
    suspend fun more(token: String, revision: Int = YouTubeAuth.sessionRevision): ShortsSeqPage = withContext(Dispatchers.IO) {
        val version = clientVersion
            ?: versionRegex.find(fetch("$HOST/shorts?hl=$lang&gl=$country", revision = revision))?.groupValues?.get(1)?.also { clientVersion = it }
            ?: error("YouTube client version not found")
        val page = sequence(version, JSONObject().put("continuation", token), revision)
        ShortsSeqPage(page.ids.distinct().map(::item), page.next)
    }

    /**
     * Beri penilaian lewat InnerTube: [action] adalah `like`, `dislike`, atau `removelike`. Perlu login (cookie dan
     * SAPISIDHASH dari [YouTubeAuth]). Hasil diklasifikasikan sebagai terkonfirmasi, ditolak, atau belum pasti agar UI
     * tidak menampilkan sukses palsu. Bentuk permintaan mengikuti klien web; belum diuji dengan akun sungguhan.
     */
    internal suspend fun rate(videoId: String, action: String, expectedRevision: Int): MutationResult {
        try {
            require(action in setOf("like", "dislike", "removelike"))
            HomeFeed.accountRequest("like/$action", JSONObject().put("target", JSONObject().put("videoId", videoId)), expectedRevision)
            return MutationResult.Confirmed
        } catch (e: CancellationException) { throw e }
        catch (e: java.io.IOException) { return MutationResult.Uncertain(e.message ?: "The result could not be confirmed.") }
        catch (e: Exception) { return MutationResult.Rejected(e.message ?: "Failed to submit rating.") }
    }

    /**
     * Tambahkan video ke daftar "Watch later" (`browse/edit_playlist`, playlist `WL`). Perlu login. Mengembalikan null bila
     * berhasil, selain itu pesan kesalahan. Bentuk permintaan mengikuti klien web; belum diuji dengan akun sungguhan.
     */
    suspend fun saveToWatchLater(videoId: String, expectedRevision: Int? = null): String? = withContext(Dispatchers.IO) {
        if (!YouTubeAuth.isLoggedIn) return@withContext "Sign in to save videos."
        try {
            require(videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid video" }
            val action = JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
            val body = JSONObject().put("playlistId", "WL").put("actions", org.json.JSONArray().put(action))
            val json = HomeFeed.accountRequest("browse/edit_playlist", body, expectedRevision)
            val status = json.optString("status")
            if (status == "STATUS_SUCCEEDED" || (status.isBlank() && (json.optJSONArray("playlistEditResults")?.length() ?: 0) > 0)) null
            else "YouTube did not confirm the save${if (status.isNotBlank()) " ($status)" else ""}."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { e.message ?: "Failed to save video." }
    }

    /**
     * Berlangganan ([subscribe] true) atau berhenti berlangganan ke channel lewat `subscription/subscribe` dan
     * `subscription/unsubscribe`. Perlu login. Hasil diklasifikasikan seperti [rate]. Bentuk
     * permintaan mengikuti klien web (parameter `params` dari ingatan atas klien itu); belum diuji dengan akun sungguhan.
     */
    internal suspend fun setSubscribed(channelId: String, subscribe: Boolean, expectedRevision: Int): MutationResult {
        try {
            val body = JSONObject().put("channelIds", org.json.JSONArray().put(channelId))
                .put("params", if (subscribe) "EgIIAhgA" else "CgIIAhgA")
            HomeFeed.accountRequest("subscription/${if (subscribe) "subscribe" else "unsubscribe"}", body, expectedRevision)
            return MutationResult.Confirmed
        } catch (e: CancellationException) { throw e }
        catch (e: java.io.IOException) { return MutationResult.Uncertain(e.message ?: "The result could not be confirmed.") }
        catch (e: Exception) { return MutationResult.Rejected(e.message ?: "Failed to update subscription.") }
    }

    /**
     * Ubah preferensi notifikasi channel (Semua, Dipersonalisasi, Tidak Ada).
     * Endpoint: `notification/modify_channel_preference`.
     * Menggunakan token server [params] dari `ModifyChannelPreferenceEndpoint`.
     */
    internal suspend fun setNotificationPreference(
        channelId: String,
        preference: NotificationPreference,
        params: String?,
        expectedRevision: Int
    ): MutationResult {
        if (!YouTubeAuth.isLoggedIn) return MutationResult.Rejected("Sign in to change notifications")
        // Bila server belum menyediakan token khusus channel ini, simpan preferensi secara lokal tanpa error 400
        if (params.isNullOrBlank()) {
            return MutationResult.Confirmed
        }
        try {
            val body = JSONObject().put("params", params)
            HomeFeed.accountRequest("notification/modify_channel_preference", body, expectedRevision)
            return MutationResult.Confirmed
        } catch (e: CancellationException) { throw e }
        catch (e: java.io.IOException) { return MutationResult.Uncertain(e.message ?: "The result could not be confirmed.") }
        catch (e: Exception) { return MutationResult.Rejected(e.message ?: "Failed to update notification preference.") }
    }

    private fun context(version: String): JSONObject =
        JSONObject().put("client", JSONObject().put("clientName", "WEB").put("clientVersion", version).put("hl", lang).put("gl", country))

    private fun sequence(version: String, extra: JSONObject, revision: Int): Sequence {
        val body = JSONObject().put("context", context(version))
        extra.keys().forEach { body.put(it, extra.get(it)) }
        val response = JSONObject(fetch("$HOST/youtubei/v1/reel/reel_watch_sequence?prettyPrint=false", body.toString(), version, revision))
        val entries = response.optJSONArray("entries")
        val ids = ArrayList<String>()
        if (entries != null) for (i in 0 until entries.length()) {
            entries.optJSONObject(i)?.optJSONObject("command")?.optJSONObject("reelWatchEndpoint")?.optString("videoId")
                ?.takeIf { it.length == 11 }?.let(ids::add)
        }
        if (ids.isEmpty()) error("Urutan Shorts kosong")
        val next = response.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotBlank() }
        return Sequence(ids, next)
    }

    private fun fetch(url: String, body: String? = null, version: String? = null, revision: Int = YouTubeAuth.sessionRevision): String {
        check(revision == YouTubeAuth.sessionRevision) { "Account changed" }
        if (YouTubeAuth.isLoggedIn) {
            val headers = YouTubeAuth.buildAuthHeaders()
            check(revision == YouTubeAuth.sessionRevision) { "Account changed" }
            try {
                val response = execute(url, body, version, auth = true, headers = headers)
                check(revision == YouTubeAuth.sessionRevision) { "Account changed" }
                return response
            }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* coba lagi sebagai tamu */ }
        }
        check(revision == YouTubeAuth.sessionRevision) { "Account changed" }
        val response = execute(url, body, version, auth = false)
        check(revision == YouTubeAuth.sessionRevision) { "Account changed" }
        return response
    }

    private fun execute(url: String, body: String?, version: String?, auth: Boolean, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(url).header("User-Agent", AGENT).header("Accept-Language", "$lang-$country,$lang;q=0.9,en;q=0.8")
        if (body != null) {
            builder.post(body.toRequestBody(jsonType)).header("X-Youtube-Client-Name", "1")
            if (version != null) builder.header("X-Youtube-Client-Version", version)
        }
        if (auth) headers.forEach { (key, value) -> builder.header(key, value) }
        return client.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) error("YouTube merespons ${it.code}")
            it.body?.string().orEmpty().ifBlank { error("Respons Shorts kosong") }
        }
    }

    private fun item(id: String) = StreamInfoItem(ServiceList.YouTube.serviceId, "$HOST/shorts/$id", "", StreamType.VIDEO_STREAM).apply {
        isShortFormContent = true
        thumbnails = listOf(Image("https://i.ytimg.com/vi/$id/frame0.jpg", 1920, 1080, Image.ResolutionLevel.HIGH))
    }

    /**
     * Ambil metadata tambahan (audio/suara & video sumber asal) dari halaman web Shorts InnerTube.
     */
    suspend fun fetchMeta(videoId: String): ShortsMeta? = withContext(Dispatchers.IO) {
        try {
            val html = fetch("$HOST/shorts/$videoId?hl=$lang&gl=$country")
            val marker = "ytInitialData = "
            val startIndex = html.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return@withContext null
            // Cari kurung kurawal pembuka dan penutup
            val jsonStart = html.indexOf('{', startIndex).takeIf { it >= 0 } ?: return@withContext null
            // Ekstrak JSON menggunakan substring sederhana sampai penutup script
            val scriptEnd = html.indexOf(";</script>", jsonStart).takeIf { it >= 0 }
                ?: html.indexOf("</script>", jsonStart).takeIf { it >= 0 }
                ?: return@withContext null
            val jsonText = html.substring(jsonStart, scriptEnd).trim().removeSuffix(";")
            val root = JSONObject(jsonText)
            parseShortsMeta(root)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShortsMeta(root: JSONObject): ShortsMeta {
        val overlay = root.optJSONObject("overlay")?.optJSONObject("reelPlayerOverlayRenderer")
        val playerOverlay = overlay?.optJSONObject("playerOverlay")?.optJSONObject("reelPlayerOverlayViewModel")
        
        // 1. Pivot audio
        var audioTitle: String? = null
        var audioThumb: String? = null
        val buttons = playerOverlay?.optJSONObject("actionBar")
            ?.optJSONObject("reelActionBarViewModel")
            ?.optJSONArray("buttonViewModels")
        if (buttons != null) {
            for (i in 0 until buttons.length()) {
                val btn = buttons.optJSONObject(i)?.optJSONObject("pivotButtonViewModel") ?: continue
                audioTitle = btn.optJSONObject("soundAttributionTitle")?.optString("content")
                    ?.takeIf { it.isNotBlank() }
                audioThumb = btn.optJSONObject("thumbnail")?.optJSONArray("sources")
                    ?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
                if (audioTitle != null) break
            }
        }

        // 2. Carousel / remix source video & carousel audio title
        var sourceVideoId: String? = null
        var sourceVideoTitle: String? = null
        val metadataItems = playerOverlay?.optJSONObject("metapanel")
            ?.optJSONObject("reelMetapanelViewModel")
            ?.optJSONArray("metadataItems")
        if (metadataItems != null) {
            for (i in 0 until metadataItems.length()) {
                val itemObj = metadataItems.optJSONObject(i) ?: continue
                val carousel = itemObj.optJSONObject("reelCarouselViewModel") ?: continue
                val carouselButtons = carousel.optJSONArray("buttonViewModels") ?: continue
                for (j in 0 until carouselButtons.length()) {
                    val cBtn = carouselButtons.optJSONObject(j)
                        ?.optJSONObject("reelCarouselButtonViewModel")
                        ?.optJSONObject("buttonViewModel")
                        ?.optJSONObject("buttonViewModel") ?: continue
                    
                    val title = cBtn.optJSONObject("titleFormatted")?.optString("content")
                        ?.takeIf { it.isNotBlank() } ?: cBtn.optString("title").takeIf { it.isNotBlank() }
                    
                    val onTap = cBtn.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
                    val watchEp = onTap?.optJSONObject("watchEndpoint")
                    var vId = watchEp?.optString("videoId")?.takeIf { it.isNotBlank() }
                    if (vId == null) {
                        val webUrl = onTap?.optJSONObject("commandMetadata")
                            ?.optJSONObject("webCommandMetadata")?.optString("url").orEmpty()
                        val m = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(webUrl)
                        if (m != null) vId = m.groupValues[1]
                    }

                    if (vId != null && sourceVideoId == null) {
                        sourceVideoId = vId
                        sourceVideoTitle = title
                    } else if (title != null && (audioTitle == null || audioTitle.equals("Suara Asli", ignoreCase = true) || audioTitle.equals("Original audio", ignoreCase = true))) {
                        // Jika carousel memiliki judul musik yang lebih lengkap (misal "Song - Artist")
                        val iconName = cBtn.optJSONObject("iconImage")?.optJSONObject("clientResource")?.optString("imageName")
                        if (iconName == "MUSIC_NOTE_SYMBOL" || cBtn.optString("accessibilityId").contains("sound", ignoreCase = true)) {
                            audioTitle = title
                        }
                    }
                }
            }
        }

        return ShortsMeta(
            audioTitle = audioTitle,
            audioThumbnail = audioThumb,
            sourceVideoId = sourceVideoId,
            sourceVideoTitle = sourceVideoTitle,
        )
    }
}
