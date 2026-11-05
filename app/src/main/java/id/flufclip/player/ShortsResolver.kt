package id.flufclip.player

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Cache hasil [VideoRepository.resolve] dan [ShortsFeed.fetchMeta] untuk Shorts.
 * Short berikutnya di-resolve lebih dulu selagi Short sekarang diputar.
 */
internal class ShortsResolver(parent: CoroutineScope, private val context: Context) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext.job))
    private val infos = mutableStateMapOf<String, StreamInfo>()
    private val metas = mutableStateMapOf<String, ShortsMeta>()
    private val stamps = HashMap<String, Long>()
    private val errors = mutableStateMapOf<String, String>()
    private val jobs = HashMap<String, Deferred<StreamInfo>>()
    private val metaJobs = HashMap<String, Deferred<ShortsMeta?>>()
    private val order = ArrayDeque<String>()
    private val gate = Semaphore(2)

    /** URL stream kedaluwarsa setelah beberapa jam; hasil lebih tua dari [TTL_MS] dianggap tidak ada. */
    fun info(url: String): StreamInfo? {
        val key = videoKey(url)
        val info = infos[key] ?: return null
        return if (System.currentTimeMillis() - (stamps[key] ?: 0L) > TTL_MS) null else info
    }

    fun meta(url: String): ShortsMeta? = metas[videoKey(url)]

    fun error(url: String): String? = errors[videoKey(url)]

    /** Mulai me-resolve di latar belakang bila belum ada di cache. */
    fun prefetch(url: String) {
        start(url)
        prefetchMeta(url)
    }

    /** Hasil dari cache bila ada, kalau tidak menunggu resolve yang sedang berjalan (atau memulainya). */
    suspend fun await(url: String): StreamInfo = info(url) ?: start(url).await()

    /** Buang hasil di cache agar resolve berikutnya mengambil yang baru. */
    fun evict(url: String) {
        val key = videoKey(url)
        infos.remove(key); metas.remove(key); stamps.remove(key); order.remove(key)
        VideoRepository.evict(url)
    }

    fun fail(url: String, message: String) { errors[videoKey(url)] = message }

    /** Buang hasil gagal lalu coba lagi. */
    fun retry(url: String) {
        errors.remove(videoKey(url))
        VideoRepository.evict(url)
        start(url)
        prefetchMeta(url)
    }

    private fun prefetchMeta(url: String) {
        val key = videoKey(url)
        if (metas.containsKey(key) || metaJobs.containsKey(key)) return
        val job = scope.async(Dispatchers.IO) {
            try {
                val meta = ShortsFeed.fetchMeta(key)
                if (meta != null) {
                    withContext(Dispatchers.Main) { metas[key] = meta }
                }
                meta
            } catch (_: Exception) {
                null
            } finally {
                metaJobs.remove(key)
            }
        }
        metaJobs[key] = job
    }

    private fun start(url: String): Deferred<StreamInfo> {
        val key = videoKey(url)
        prefetchMeta(url)
        info(url)?.let { return CompletableDeferred(it) }
        jobs[key]?.let { return it }
        errors.remove(key)
        val job = scope.async {
            try {
                val info = gate.withPermit { VideoRepository.resolve(url, context) }
                infos[key] = info
                stamps[key] = System.currentTimeMillis()
                order.remove(key); order.addLast(key)
                while (order.size > MAX_CACHED) {
                    order.removeFirst().let {
                        infos.remove(it)
                        metas.remove(it)
                        stamps.remove(it)
                    }
                }
                info
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { errors[key] = e.message ?: "failed to load"; throw e }
            finally { jobs.remove(key) }
        }
        jobs[key] = job
        return job
    }

    private companion object { const val MAX_CACHED = 16; const val TTL_MS = 30 * 60 * 1000L }
}
