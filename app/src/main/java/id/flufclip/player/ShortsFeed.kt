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
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()
    private val versionRegex = Regex("\"INNERTUBE_CONTEXT_CLIENT_VERSION\":\"([^\"]+)\"")
    private val tokenRegex = Regex("\"sequenceContinuation\":\"([^\"]+)\"")
    private val seedRegex = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")
    @Volatile private var clientVersion: String? = null

    private class Sequence(val ids: List<String>, val next: String?)

    private val lang: String get() = Locale.getDefault().toLanguageTag().substringBefore('-').ifBlank { "en" }
    private val country: String get() = Locale.getDefault().country.ifBlank { "US" }

    private class Warm(val page: ShortsSeqPage, val at: Long)
    @Volatile private var warm: Warm? = null
    private const val WARM_TTL_MS = 3 * 60 * 1000L

    /**
     * Siapkan halaman pertama di latar belakang supaya membuka tab Shorts tidak menunggu jaringan. Hasilnya dipakai
     * sekali oleh [start] dan dibuang setelah 3 menit (token urutan terikat sesi). Mengembalikan null bila gagal.
     */
    suspend fun warm(): ShortsSeqPage? {
        warm?.takeIf { System.currentTimeMillis() - it.at < WARM_TTL_MS }?.let { return it.page }
        return try { load {}.also { warm = Warm(it, System.currentTimeMillis()) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }

    /**
     * Halaman pertama: Short pembuka dari halaman /shorts, disusul entri pertama urutan. [onSeed] dipanggil begitu
     * Short pembuka diketahui, sebelum permintaan urutan selesai, agar pemuatannya bisa berjalan paralel.
     */
    suspend fun start(onSeed: suspend (StreamInfoItem) -> Unit = {}): ShortsSeqPage {
        val ready = warm?.takeIf { System.currentTimeMillis() - it.at < WARM_TTL_MS }
        warm = null
        return ready?.page ?: load(onSeed)
    }

    private suspend fun load(onSeed: suspend (StreamInfoItem) -> Unit): ShortsSeqPage = withContext(Dispatchers.IO) {
        val html = fetch("$HOST/shorts?hl=$lang&gl=$country")
        val version = versionRegex.find(html)?.groupValues?.get(1) ?: error("Versi klien YouTube tidak ditemukan")
        clientVersion = version
        val token = tokenRegex.find(html)?.groupValues?.get(1) ?: error("Token urutan Shorts tidak ditemukan")
        val seed = html.indexOf("\"replacementEndpoint\"").takeIf { it >= 0 }?.let { from ->
            seedRegex.find(html.substring(from, minOf(html.length, from + 4000)))?.groupValues?.get(1)
        }
        if (seed != null) onSeed(item(seed))
        val page = sequence(version, JSONObject().put("sequenceParams", token))
        ShortsSeqPage((listOfNotNull(seed) + page.ids).distinct().map(::item), page.next)
    }

    /** Halaman berikutnya memakai token continuation dari halaman sebelumnya. */
    suspend fun more(token: String): ShortsSeqPage = withContext(Dispatchers.IO) {
        val version = clientVersion
            ?: versionRegex.find(fetch("$HOST/shorts?hl=$lang&gl=$country"))?.groupValues?.get(1)?.also { clientVersion = it }
            ?: error("Versi klien YouTube tidak ditemukan")
        val page = sequence(version, JSONObject().put("continuation", token))
        ShortsSeqPage(page.ids.distinct().map(::item), page.next)
    }

    /**
     * Beri penilaian lewat InnerTube: [action] adalah `like`, `dislike`, atau `removelike`. Perlu login (cookie dan
     * SAPISIDHASH dari [YouTubeAuth]). Mengembalikan null bila berhasil, selain itu pesan kesalahan. Bentuk permintaan
     * mengikuti klien web; belum diuji dengan akun sungguhan.
     */
    suspend fun rate(videoId: String, action: String): String? = withContext(Dispatchers.IO) {
        if (!YouTubeAuth.isLoggedIn) return@withContext "Login dulu untuk memberi penilaian."
        try {
            val version = clientVersion
                ?: versionRegex.find(execute("$HOST/shorts?hl=$lang&gl=$country", null, null, auth = false))?.groupValues?.get(1)?.also { clientVersion = it }
                ?: error("Versi klien YouTube tidak ditemukan")
            val body = JSONObject().put("context", context(version)).put("target", JSONObject().put("videoId", videoId))
            execute("$HOST/youtubei/v1/like/$action?prettyPrint=false", body.toString(), version, auth = true)
            null
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { e.message ?: "Gagal mengirim penilaian." }
    }

    private fun context(version: String): JSONObject =
        JSONObject().put("client", JSONObject().put("clientName", "WEB").put("clientVersion", version).put("hl", lang).put("gl", country))

    private fun sequence(version: String, extra: JSONObject): Sequence {
        val body = JSONObject().put("context", context(version))
        extra.keys().forEach { body.put(it, extra.get(it)) }
        val response = JSONObject(fetch("$HOST/youtubei/v1/reel/reel_watch_sequence?prettyPrint=false", body.toString(), version))
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

    private fun fetch(url: String, body: String? = null, version: String? = null): String {
        if (YouTubeAuth.isLoggedIn) {
            try { return execute(url, body, version, auth = true) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* coba lagi sebagai tamu */ }
        }
        return execute(url, body, version, auth = false)
    }

    private fun execute(url: String, body: String?, version: String?, auth: Boolean): String {
        val builder = Request.Builder().url(url).header("User-Agent", AGENT).header("Accept-Language", "$lang-$country,$lang;q=0.9,en;q=0.8")
        if (body != null) {
            builder.post(body.toRequestBody(jsonType)).header("X-Youtube-Client-Name", "1")
            if (version != null) builder.header("X-Youtube-Client-Version", version)
        }
        if (auth) YouTubeAuth.buildAuthHeaders().forEach { (key, value) -> builder.header(key, value) }
        return client.newCall(builder.build()).execute().use {
            if (!it.isSuccessful) error("YouTube merespons ${it.code}")
            it.body?.string().orEmpty().ifBlank { error("Respons Shorts kosong") }
        }
    }

    private fun item(id: String) = StreamInfoItem(ServiceList.YouTube.serviceId, "$HOST/shorts/$id", "", StreamType.VIDEO_STREAM).apply {
        isShortFormContent = true
        thumbnails = listOf(Image("https://i.ytimg.com/vi/$id/frame0.jpg", 1920, 1080, Image.ResolutionLevel.HIGH))
    }
}
