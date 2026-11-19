package id.flufclip.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.exoplayer.dash.DashSegmentIndex
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import java.io.InputStream

internal enum class PlaybackManifest { HLS, DASH, NONE }

private const val LIVE_DASH_REANCHOR_AHEAD_MS = 5_000L
private const val LIVE_DASH_REANCHOR_BEHIND_MS = 15_000L
private const val LIVE_DASH_MIN_UPDATE_FLOOR_MS = 1_000L
private const val LIVE_DASH_TARGET_UPDATE_MIN_MS = 2_000L
private const val LIVE_DASH_TARGET_UPDATE_MAX_MS = 4_000L
private const val LIVE_DASH_DEFAULT_UPDATE_MS = 3_000L

/** Prefer DASH for live streams (unlocks 1440p/2160p and full DVR) with instant HLS fallback when disabled or unavailable. */
internal fun playbackManifest(
    isLive: Boolean,
    hlsUrl: String?,
    dashUrl: String?,
    allowLiveDash: Boolean = true,
): PlaybackManifest = when {
    isLive && allowLiveDash && !dashUrl.isNullOrBlank() -> PlaybackManifest.DASH
    isLive && !hlsUrl.isNullOrBlank() -> PlaybackManifest.HLS
    !dashUrl.isNullOrBlank() -> PlaybackManifest.DASH
    else -> PlaybackManifest.NONE
}

/**
 * Re-anchors YouTube's dynamic live DASH manifest against the local wall clock using the last
 * explicit segment in the MPD so Media3 does not lag minutes behind live when `availabilityStartTime`
 * drifts from actual segment ingest timestamps. Static (VOD) and unbounded manifests pass through untouched.
 */
internal fun normalizeLiveDashManifest(
    manifest: DashManifest,
    nowMs: Long,
    anchoredStartMs: Long?,
): Pair<DashManifest, Long?> {
    if (!manifest.dynamic || manifest.periodCount <= 0) return manifest to anchoredStartMs

    val periodIndex = manifest.periodCount - 1
    val period = manifest.getPeriod(periodIndex)
    val periodDurationUs = manifest.getPeriodDurationUs(periodIndex)

    var videoEndUs: Long? = null
    var audioEndUs: Long? = null
    var sampleSegmentDurationMs: Long? = null

    for (adaptationSet in period.adaptationSets) {
        if (adaptationSet.type != C.TRACK_TYPE_VIDEO && adaptationSet.type != C.TRACK_TYPE_AUDIO) continue
        var setMaxEndUs: Long? = null
        for (representation in adaptationSet.representations) {
            val index = representation.index ?: continue
            val segmentCount = index.getSegmentCount(periodDurationUs)
            if (segmentCount <= 0L || segmentCount == DashSegmentIndex.INDEX_UNBOUNDED.toLong()) continue
            val lastSegNum = index.firstSegmentNum + segmentCount - 1L
            val lastStartUs = index.getTimeUs(lastSegNum)
            val lastDurationUs = index.getDurationUs(lastSegNum, periodDurationUs)
            if (lastStartUs < 0L || lastDurationUs <= 0L) continue
            val repEndUs = lastStartUs + lastDurationUs
            if (setMaxEndUs == null || repEndUs > setMaxEndUs) setMaxEndUs = repEndUs
            val durMs = lastDurationUs / 1_000L
            if (durMs in 500L..15_000L && (sampleSegmentDurationMs == null || durMs < sampleSegmentDurationMs)) {
                sampleSegmentDurationMs = durMs
            }
        }
        if (setMaxEndUs != null) {
            if (adaptationSet.type == C.TRACK_TYPE_VIDEO) {
                videoEndUs = if (videoEndUs == null) setMaxEndUs else maxOf(videoEndUs, setMaxEndUs)
            } else {
                audioEndUs = if (audioEndUs == null) setMaxEndUs else maxOf(audioEndUs, setMaxEndUs)
            }
        }
    }

    val commonEndUs = when {
        videoEndUs != null && audioEndUs != null -> minOf(videoEndUs, audioEndUs)
        videoEndUs != null -> videoEndUs
        audioEndUs != null -> audioEndUs
        else -> null
    } ?: return manifest to anchoredStartMs

    val periodStartMs = if (period.startMs == C.TIME_UNSET) 0L else period.startMs
    val explicitEndMs = periodStartMs + (commonEndUs / 1_000L)
    if (explicitEndMs <= 0L) return manifest to anchoredStartMs

    val candidateStartMs = nowMs - explicitEndMs
    val nextAnchor = if (anchoredStartMs == null) {
        candidateStartMs
    } else {
        val driftMs = (nowMs - anchoredStartMs) - explicitEndMs
        if (driftMs < -LIVE_DASH_REANCHOR_AHEAD_MS || driftMs > LIVE_DASH_REANCHOR_BEHIND_MS) {
            candidateStartMs
        } else {
            anchoredStartMs
        }
    }

    val targetUpdateCapMs = sampleSegmentDurationMs
        ?.coerceIn(LIVE_DASH_TARGET_UPDATE_MIN_MS, LIVE_DASH_TARGET_UPDATE_MAX_MS)
        ?: LIVE_DASH_DEFAULT_UPDATE_MS
    val effectiveMinUpdateMs = when {
        manifest.minUpdatePeriodMs == C.TIME_UNSET -> targetUpdateCapMs
        manifest.minUpdatePeriodMs > targetUpdateCapMs -> targetUpdateCapMs
        else -> manifest.minUpdatePeriodMs.coerceAtLeast(LIVE_DASH_MIN_UPDATE_FLOOR_MS)
    }

    val periods = List(manifest.periodCount) { manifest.getPeriod(it) }
    val normalized = DashManifest(
        nextAnchor,
        manifest.durationMs,
        manifest.minBufferTimeMs,
        manifest.dynamic,
        effectiveMinUpdateMs,
        manifest.timeShiftBufferDepthMs,
        C.TIME_UNSET,
        manifest.publishTimeMs,
        manifest.programInformation,
        null,
        null,
        periods,
        manifest.locations,
    )
    return normalized to nextAnchor
}

internal class YouTubeLiveDashManifestParser(
    private val clockMs: () -> Long = System::currentTimeMillis,
) : DashManifestParser() {
    @Volatile private var anchoredStartMs: Long? = null

    override fun parse(uri: Uri, inputStream: InputStream): DashManifest {
        val parsed = super.parse(uri, inputStream)
        val (normalized, nextAnchor) = normalizeLiveDashManifest(parsed, clockMs(), anchoredStartMs)
        anchoredStartMs = nextAnchor
        return normalized
    }
}
