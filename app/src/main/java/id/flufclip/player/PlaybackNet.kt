package id.flufclip.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import okhttp3.ConnectionPool

private val clientParamRegex = Regex("(?:[?&]c=|/c/)([^/&?#]+)")

/** Match each signed media URL's client; YouTube login cookies belong only on YouTube hosts. */
internal fun playbackRequestHeaders(url: String, cookies: String, original: Map<String, String> = emptyMap()): Map<String, String> {
    val target = url.toHttpUrlOrNull()
    val client = clientParamRegex.find(url)?.groupValues?.get(1)
    val agent = when (client) {
        "ANDROID" -> YoutubeParsingHelper.getAndroidUserAgent(Localization.DEFAULT)
        "IOS" -> YoutubeParsingHelper.getIosUserAgent(Localization.DEFAULT)
        "VISIONOS" -> YoutubeParsingHelper.getVisionOsUserAgent(Localization.DEFAULT)
        else -> InnerTubePlayer.AGENT
    }
    return original.filterKeys { key -> sensitiveAccountHeaders.none { it.equals(key, true) } && !key.equals("User-Agent", true) }
        .toMutableMap().apply {
            put("User-Agent", agent)
            put("Referer", "https://www.youtube.com/")
            if (target != null && permitsAccountHeaders(target) && cookies.isNotBlank()) put("Cookie", cookies)
        }
}

internal fun youTubeHttpDataSourceFactory(context: Context): DataSource.Factory =
    ResolvingDataSource.Factory(OkHttpDataSource.Factory(playbackHttpClient)) { spec ->
        spec.withRequestHeaders(playbackRequestHeaders(spec.uri.toString(), YouTubeAuth.getCookies(), spec.httpRequestHeaders))
    }

private val playbackHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
    .addNetworkInterceptor(accountHeaderInterceptor)
    .build()

/** Probe only manifests, using the same per-hop header policy as playback. */
internal fun inspectLiveDvr(hlsUrl: String, dashUrl: String, cookies: String): Boolean {
    val client = playbackHttpClient.newBuilder().callTimeout(1_500, TimeUnit.MILLISECONDS).build()
    fun read(url: String): String? {
        val target = url.toHttpUrlOrNull() ?: return null
        if (target.scheme != "https") return null
        val builder = Request.Builder().url(target)
        playbackRequestHeaders(url, cookies).forEach { (key, value) -> builder.header(key, value) }
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) null else response.peekBody(256 * 1024L).string()
        }
    }
    return try {
        val master = read(hlsUrl) ?: return false
        val child = master.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith('#') }
        val playlist = if (master.contains("#EXT-X-STREAM-INF") && child != null) {
            val url = hlsUrl.toHttpUrlOrNull()?.resolve(child)?.toString() ?: return false
            read(url) ?: return false
        } else master
        val hls = hlsWindowSeconds(playlist) ?: return false
        if (hls > 15) return false
        val dash = dashWindowSeconds(read(dashUrl) ?: return false)
        preferDashForDvr(hls, dash)
    } catch (_: Exception) { false }
}

/** A forbidden signed URL must reach the error handler instead of cycling through tracks forever. */
private class YouTubeLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getFallbackSelectionFor(options: LoadErrorHandlingPolicy.FallbackOptions,
        info: LoadErrorHandlingPolicy.LoadErrorInfo): LoadErrorHandlingPolicy.FallbackSelection? =
        if ((info.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 403) null
        else super.getFallbackSelectionFor(options, info)

    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
        if ((info.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 403) {
            if (info.errorCount < 2) 500L else C.TIME_UNSET
        } else super.getRetryDelayMsFor(info)
}

/**
 * ExoPlayer harus memakai UA/header yang sama dengan request InnerTube pembuat URL HLS.
 * Tanpa ini, segmen googlevideo bisa mengembalikan 403 meskipun manifest HLS berhasil dibaca.
 */
internal fun youTubeMediaSourceFactory(context: Context): DefaultMediaSourceFactory =
    DefaultMediaSourceFactory(DefaultDataSource.Factory(context, youTubeHttpDataSourceFactory(context)))
        .setLoadErrorHandlingPolicy(YouTubeLoadErrorPolicy())

internal fun liveMediaConfiguration(): MediaItem.LiveConfiguration =
    MediaItem.LiveConfiguration.Builder()
        .setTargetOffsetMs(2_000L)
        .setMinOffsetMs(1_000L)
        .setMaxOffsetMs(3_500L)
        .setMinPlaybackSpeed(0.95f)
        .setMaxPlaybackSpeed(1.10f)
        .build()

internal fun playbackLoadControl(): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMsForStreaming(12_000, 30_000, 500, 1_000)
        .setPrioritizeTimeOverSizeThresholdsForStreaming(true)
        .build()

/** Smaller streaming buffer for Slot 2 in Dual Tab Mode to limit concurrent memory use. */
internal fun secondaryLoadControl(): DefaultLoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMsForStreaming(6_000, 15_000, 500, 1_000)
        .setPrioritizeTimeOverSizeThresholdsForStreaming(true)
        .build()

internal fun playbackLiveSpeedControl(): DefaultLivePlaybackSpeedControl =
    DefaultLivePlaybackSpeedControl.Builder()
        .setFallbackMinPlaybackSpeed(0.95f)
        .setFallbackMaxPlaybackSpeed(1.10f)
        .setMinUpdateIntervalMs(300L)
        .setProportionalControlFactor(0.35f)
        .setMaxLiveOffsetErrorMsForUnitSpeed(100L)
        .setTargetLiveOffsetIncrementOnRebufferMs(250L)
        .setMinPossibleLiveOffsetSmoothingFactor(0.90f)
        .build()

internal fun youTubeHlsSource(context: Context, url: String, mediaId: String? = null, isLive: Boolean = false): MediaSource {
    val item = MediaItem.Builder().setUri(url).apply {
        if (!mediaId.isNullOrBlank()) setMediaId(mediaId)
        if (isLive) setLiveConfiguration(liveMediaConfiguration())
    }.build()
    return HlsMediaSource.Factory(DefaultDataSource.Factory(context, youTubeHttpDataSourceFactory(context)))
        .setLoadErrorHandlingPolicy(YouTubeLoadErrorPolicy())
        .createMediaSource(item)
}

/** DASH remains available when live HLS is missing, and for ordinary videos/replays. */
internal fun youTubeDashSource(context: Context, url: String, mediaId: String? = null): MediaSource {
    val item = MediaItem.Builder().setUri(url).apply { if (!mediaId.isNullOrBlank()) setMediaId(mediaId) }.build()
    return DashMediaSource.Factory(DefaultDataSource.Factory(context, youTubeHttpDataSourceFactory(context)))
        .setLoadErrorHandlingPolicy(YouTubeLoadErrorPolicy())
        .createMediaSource(item)
}
