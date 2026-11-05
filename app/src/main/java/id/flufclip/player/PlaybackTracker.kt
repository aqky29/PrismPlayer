package id.flufclip.player

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Best-effort account playback tracking; a successful ping does not confirm history insertion. */
object PlaybackTracker {
    private const val TAG = "PlaybackTracker"
    private const val PREFS_NAME = "playback_tracker"
    private const val KEY_SYNC_ENABLED = "sync_history_to_google"
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
    // Serialize pings, including a final ping queued while an earlier request is in flight.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(Dispatchers.IO.limitedParallelism(1) + SupervisorJob())
    private var job: Job? = null
    private class Session(val context: Context, val revision: Int, val cpn: String, val durationSec: Long, val playlistId: String?) {
        @Volatile var tracking: TrackingTarget? = null
        @Volatile var lastPositionMs: Long = 0L
        var lastFinalPositionMs: Long? = null // Only accessed on the serialized tracker dispatcher.
    }
    private data class TrackingTarget(val watchtime: String, val clientVersion: String, val startedAtMs: Long)
    @Volatile private var session: Session? = null
    var settingsRevision by mutableIntStateOf(0)
        private set

    fun isSyncEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SYNC_ENABLED, true)

    fun setSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
        if (!enabled) stop(sendFinal = false)
        settingsRevision++
    }

    internal fun trackingUrl(tracking: JSONObject, field: String): String? {
        val url = tracking.optJSONObject(field)?.optString("baseUrl")?.toHttpUrlOrNull() ?: return null
        val host = url.host
        if (url.scheme != "https" || !(host == "youtube.com" || host.endsWith(".youtube.com"))) return null
        if (url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        val expectedPath = if (field == "videostatsPlaybackUrl") "/api/stats/playback" else "/api/stats/watchtime"
        if (url.encodedPath != expectedPath) return null
        return url.toString()
    }

    fun start(videoId: String, durationSec: Long, context: Context, playlistId: String? = null, isPlaying: () -> Boolean, positionProvider: () -> Long) {
        stop()
        if (!YouTubeAuth.isLoggedIn || !isSyncEnabled(context) || !videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) return
        val revision = YouTubeAuth.sessionRevision
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        val rng = SecureRandom()
        val cpn = (1..16).map { chars[rng.nextInt(chars.length)] }.joinToString("")
        val cleanPlaylistId = playlistId?.removePrefix("VL")?.takeIf { it.isNotBlank() }
        val current = Session(context.applicationContext, revision, cpn, durationSec, cleanPlaylistId)
        session = current
        job = scope.launch {
            try {
                while (isActive && currentSession(current) && !withContext(Dispatchers.Main.immediate) { isPlaying() }) delay(500)
                if (!isActive || !currentSession(current)) return@launch
                var tracking: InnerTubePlayer.TrackingResponse? = null
                repeat(3) { attempt ->
                    if (tracking == null && currentSession(current)) {
                        try { tracking = InnerTubePlayer.playbackTracking(videoId, cleanPlaylistId) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { Log.w(TAG, "Player tracking request failed") }
                        if (tracking == null && attempt < 2) delay(10_000)
                    }
                }
                if (!isActive || !currentSession(current)) return@launch
                val trackingResponse = tracking ?: return@launch
                val playback = trackingUrl(trackingResponse.tracking, "videostatsPlaybackUrl")
                val watchtime = trackingUrl(trackingResponse.tracking, "videostatsWatchtimeUrl")
                if (playback == null || watchtime == null) {
                    Log.w(TAG, "Player response has no usable tracking URLs")
                    return@launch
                }
                val startMs = android.os.SystemClock.elapsedRealtime()
                var previous = withContext(Dispatchers.Main.immediate) { positionProvider().coerceAtLeast(0L) }
                current.lastPositionMs = previous
                var segmentStart = previous
                var segmentEnd = previous
                var wasPlaying = false
                fun report(state: String) {
                    if (segmentEnd <= segmentStart || !currentSession(current)) return
                    val rt = (android.os.SystemClock.elapsedRealtime() - startMs) / 1000.0
                    val params = mutableMapOf("cpn" to cpn, "cmt" to (segmentEnd / 1000).toString(),
                        "st" to String.format(Locale.US, "%.3f", segmentStart / 1000.0),
                        "et" to String.format(Locale.US, "%.3f", segmentEnd / 1000.0),
                        "rt" to String.format(Locale.US, "%.1f", rt), "state" to state)
                    if (cleanPlaylistId != null) params["list"] = cleanPlaylistId
                    ping(watchtime, params, trackingResponse.clientVersion, revision, "watchtime")
                    segmentStart = segmentEnd
                }
                val playbackParams = mutableMapOf("cpn" to cpn, "cmt" to (previous / 1000).toString(),
                    "len" to durationSec.coerceAtLeast(0).toString())
                if (cleanPlaylistId != null) playbackParams["list"] = cleanPlaylistId
                ping(playback, playbackParams, trackingResponse.clientVersion, revision, "playback")
                if (!isActive || !currentSession(current)) return@launch
                current.tracking = TrackingTarget(watchtime, trackingResponse.clientVersion, startMs)
                while (isActive && currentSession(current)) {
                    delay(1000)
                    val (playing, position) = withContext(Dispatchers.Main.immediate) { isPlaying() to positionProvider().coerceAtLeast(0L) }
                    current.lastPositionMs = position
                    val delta = position - previous
                    // A seek or a pause starts a new contiguous watched segment.
                    if (!playing || !wasPlaying || delta < 0 || delta > 4000) {
                        if (!playing && wasPlaying && delta in 0..4000) segmentEnd = position
                        report(if (playing) "playing" else "paused")
                        // A final position update is distinct from the watched segment, even if
                        // the previous periodic report happened immediately before the pause.
                        if (!playing && (wasPlaying || kotlin.math.abs(delta) > 1000)) reportFinal(current, position)
                        segmentStart = position
                        segmentEnd = position
                    } else {
                        segmentEnd = position
                        if (segmentEnd - segmentStart >= 10_000) report("playing")
                    }
                    previous = position
                    wasPlaying = playing
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Log.w(TAG, "Tracking stopped due to an error") }
        }
    }

    fun stop(finalPositionSec: Long? = null, sendFinal: Boolean = true) {
        val previous = session
        session = null
        job?.cancel()
        job = null
        if (sendFinal && previous != null && previous.tracking != null) {
            val positionMs = finalPositionSec?.coerceAtLeast(0L)?.times(1000L) ?: previous.lastPositionMs
            scope.launch { reportFinal(previous, positionMs) }
        }
    }

    private fun currentSession(session: Session): Boolean = YouTubeAuth.isLoggedIn &&
        YouTubeAuth.sessionRevision == session.revision && isSyncEnabled(session.context)

    /** A paused/final ping may have no new watched segment but still has an exact resume position. */
    internal fun finalWatchTimeParams(cpn: String, positionMs: Long, durationSec: Long, elapsedMs: Long): Map<String, String> {
        val position = String.format(Locale.US, "%.3f", positionMs.coerceAtLeast(0L) / 1000.0)
        return mapOf("cpn" to cpn, "cmt" to position, "st" to position, "et" to position,
            "len" to durationSec.coerceAtLeast(0L).toString(), "rt" to String.format(Locale.US, "%.1f", elapsedMs.coerceAtLeast(0L) / 1000.0),
            "state" to "paused", "final" to "1")
    }

    private fun reportFinal(session: Session, positionMs: Long) {
        val target = session.tracking ?: return
        if (!currentSession(session)) return
        if (session.lastFinalPositionMs?.let { kotlin.math.abs(it - positionMs) < 1000L } == true) return
        val elapsed = android.os.SystemClock.elapsedRealtime() - target.startedAtMs
        val params = finalWatchTimeParams(session.cpn, positionMs, session.durationSec, elapsed).toMutableMap()
        if (session.playlistId != null) params["list"] = session.playlistId
        if (ping(target.watchtime, params,
                target.clientVersion, session.revision, "final watchtime")) session.lastFinalPositionMs = positionMs
    }

    private fun ping(base: String, params: Map<String, String>, clientVersion: String, revision: Int, kind: String): Boolean {
        if (!YouTubeAuth.isLoggedIn || YouTubeAuth.sessionRevision != revision) return false
        return try {
            val builder = base.toHttpUrlOrNull()!!.newBuilder()
            builder.setQueryParameter("ver", "2").setQueryParameter("c", "web")
                .setQueryParameter("cver", clientVersion).setQueryParameter("cbrver", clientVersion)
            params.forEach { (key, value) -> builder.setQueryParameter(key, value) }
            val request = Request.Builder().url(builder.build())
                .header("User-Agent", InnerTubePlayer.AGENT).header("Referer", "https://www.youtube.com/")
            YouTubeAuth.buildAuthHeaders().forEach { (key, value) -> request.header(key, value) }
            client.newCall(request.build()).execute().use { Log.i(TAG, "$kind HTTP ${it.code}"); it.isSuccessful }
        } catch (_: Exception) { Log.w(TAG, "$kind request failed"); false }
    }
}
