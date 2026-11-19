package id.flufclip.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.dash.manifest.AdaptationSet
import androidx.media3.exoplayer.dash.manifest.BaseUrl
import androidx.media3.exoplayer.dash.manifest.DashManifest
import androidx.media3.exoplayer.dash.manifest.Period
import androidx.media3.exoplayer.dash.manifest.RangedUri
import androidx.media3.exoplayer.dash.manifest.Representation
import androidx.media3.exoplayer.dash.manifest.SegmentBase
import androidx.media3.exoplayer.dash.manifest.ServiceDescriptionElement
import androidx.media3.exoplayer.dash.manifest.UrlTemplate
import androidx.media3.exoplayer.dash.manifest.UtcTimingElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackManifestTest {
    private fun explicitRep(
        id: String,
        mime: String,
        startSec: Long,
        segmentCount: Int,
        segmentDurSec: Long,
    ): Representation {
        val timeline = List(segmentCount) { index ->
            SegmentBase.SegmentTimelineElement((startSec + index * segmentDurSec) * 1_000L, segmentDurSec * 1_000L)
        }
        val mediaSegments = List(segmentCount) { index ->
            RangedUri("sq/${100 + index}", 0L, C.LENGTH_UNSET.toLong())
        }
        val segmentList = SegmentBase.SegmentList(
            RangedUri("init", 0L, C.LENGTH_UNSET.toLong()),
            1_000L,
            0L,
            100L,
            segmentDurSec * 1_000L,
            timeline,
            C.TIME_UNSET,
            mediaSegments,
            0L,
            0L,
        )
        return Representation.newInstance(
            Representation.REVISION_ID_DEFAULT,
            Format.Builder().setId(id).setSampleMimeType(mime).build(),
            listOf(BaseUrl("https://manifest.googlevideo.com/")),
            segmentList,
            null,
            emptyList(),
            emptyList(),
            null,
        )
    }

    private fun unboundedRep(id: String, mime: String): Representation {
        val template = SegmentBase.SegmentTemplate(
            RangedUri("init", 0L, C.LENGTH_UNSET.toLong()),
            1_000L,
            0L,
            1L,
            C.INDEX_UNSET.toLong(),
            2_000L,
            null,
            C.TIME_UNSET,
            UrlTemplate.compile("init.mp4"),
            UrlTemplate.compile("seg-\$Number\$.m4s"),
            0L,
            0L,
        )
        return Representation.newInstance(
            Representation.REVISION_ID_DEFAULT,
            Format.Builder().setId(id).setSampleMimeType(mime).build(),
            listOf(BaseUrl("https://manifest.googlevideo.com/")),
            template,
            null,
            emptyList(),
            emptyList(),
            null,
        )
    }

    private fun buildManifest(
        dynamic: Boolean,
        availabilityStartTimeMs: Long,
        minUpdatePeriodMs: Long = 10_000L,
        suggestedPresentationDelayMs: Long = 30_000L,
        publishTimeMs: Long = 1_700_000_000_000L,
        videoReps: List<Representation>,
        audioReps: List<Representation> = emptyList(),
    ): DashManifest {
        val sets = buildList {
            if (videoReps.isNotEmpty()) {
                add(AdaptationSet(1L, C.TRACK_TYPE_VIDEO, videoReps, emptyList(), emptyList(), emptyList()))
            }
            if (audioReps.isNotEmpty()) {
                add(AdaptationSet(2L, C.TRACK_TYPE_AUDIO, audioReps, emptyList(), emptyList(), emptyList()))
            }
        }
        val period = Period("0", 0L, sets)
        return DashManifest(
            availabilityStartTimeMs,
            if (dynamic) C.TIME_UNSET else 300_000L,
            1_500L,
            dynamic,
            minUpdatePeriodMs,
            7_200_000L,
            suggestedPresentationDelayMs,
            publishTimeMs,
            null,
            UtcTimingElement("urn:mpeg:dash:utc:direct:2014", "2026-11-18T00:00:00Z"),
            ServiceDescriptionElement(15_000L, 5_000L, 30_000L, 0.95f, 1.05f),
            listOf(period),
            emptyList(),
        )
    }

    @Test fun livePrefersDashWhenAvailableAndFallsBackToHls() {
        assertEquals(PlaybackManifest.DASH, playbackManifest(true, "https://media/live.m3u8", "https://media/live.mpd"))
        assertEquals(PlaybackManifest.HLS, playbackManifest(true, "https://media/live.m3u8", "https://media/live.mpd", allowLiveDash = false))
        assertEquals(PlaybackManifest.HLS, playbackManifest(true, "https://media/live.m3u8", null))
        assertEquals(PlaybackManifest.HLS, playbackManifest(true, "https://media/live.m3u8", ""))
    }

    @Test fun liveWithoutHlsRetainsDashFallback() {
        for (hls in listOf(null, "", " ")) {
            assertEquals(PlaybackManifest.DASH, playbackManifest(true, hls, "https://media/live.mpd"))
        }
    }

    @Test fun recordingsKeepExistingDashAndExplicitStreamSelection() {
        assertEquals(PlaybackManifest.DASH, playbackManifest(false, "https://media/video.m3u8", "https://media/video.mpd"))
        assertEquals(PlaybackManifest.NONE, playbackManifest(false, "https://media/video.m3u8", null))
        assertEquals(PlaybackManifest.NONE, playbackManifest(true, null, null))
    }

    @Test fun staticDashManifestPassesThroughUntouched() {
        val vod = buildManifest(
            dynamic = false,
            availabilityStartTimeMs = C.TIME_UNSET,
            videoReps = listOf(explicitRep("308", "video/webm", 0L, 10, 5L)),
        )
        val (normalized, anchor) = normalizeLiveDashManifest(vod, nowMs = 1_700_000_500_000L, anchoredStartMs = null)
        assertSame(vod, normalized)
        assertNull(anchor)
    }

    @Test fun dynamicLiveDashReanchorsDriftedClockAndCapsUpdatePeriod() {
        val nowMs = 1_700_000_600_000L
        // Stream segments end at 3600s, while MPD availabilityStartTime drifted 4 minutes earlier.
        val driftedStartMs = nowMs - 3_840_000L
        val live = buildManifest(
            dynamic = true,
            availabilityStartTimeMs = driftedStartMs,
            minUpdatePeriodMs = 15_000L,
            suggestedPresentationDelayMs = 25_000L,
            publishTimeMs = nowMs - 1_000L,
            videoReps = listOf(explicitRep("308", "video/webm", 3_580L, 10, 2L)), // ends at 3600s
            audioReps = listOf(explicitRep("251", "audio/webm", 3_580L, 10, 2L)), // ends at 3600s
        )
        val (normalized, anchor) = normalizeLiveDashManifest(live, nowMs = nowMs, anchoredStartMs = null)
        assertEquals(nowMs - 3_600_000L, normalized.availabilityStartTimeMs)
        assertEquals(nowMs - 3_600_000L, anchor)
        assertEquals(C.TIME_UNSET, normalized.suggestedPresentationDelayMs)
        assertNull(normalized.utcTiming)
        assertNull(normalized.serviceDescription)
        assertEquals(2_000L, normalized.minUpdatePeriodMs)
        assertEquals(live.publishTimeMs, normalized.publishTimeMs)
        assertEquals(live.timeShiftBufferDepthMs, normalized.timeShiftBufferDepthMs)
    }

    @Test fun dynamicLiveDashAnchorsToMinOfVideoAndAudioTail() {
        val nowMs = 1_700_000_600_000L
        val live = buildManifest(
            dynamic = true,
            availabilityStartTimeMs = nowMs - 4_000_000L,
            videoReps = listOf(explicitRep("308", "video/webm", 3_580L, 9, 2L)),  // ends at 3598s
            audioReps = listOf(explicitRep("251", "audio/webm", 3_580L, 10, 2L)), // ends at 3600s
        )
        val (normalized, _) = normalizeLiveDashManifest(live, nowMs = nowMs, anchoredStartMs = null)
        assertEquals(nowMs - 3_598_000L, normalized.availabilityStartTimeMs)
    }

    @Test fun dynamicLiveDashLocksAnchorAcrossNormalRefreshAndReanchorsOnLargeDrift() {
        val t0 = 1_700_000_600_000L
        val first = buildManifest(
            dynamic = true,
            availabilityStartTimeMs = t0 - 3_900_000L,
            videoReps = listOf(explicitRep("308", "video/webm", 3_580L, 10, 2L)), // ends at 3600s
        )
        val (_, lockedAnchor) = normalizeLiveDashManifest(first, nowMs = t0, anchoredStartMs = null)

        // 3s later, a new 2s segment arrives (drift = +1s, within hysteresis) -> anchor stays locked.
        val refreshNormal = buildManifest(
            dynamic = true,
            availabilityStartTimeMs = t0 - 3_900_000L,
            videoReps = listOf(explicitRep("308", "video/webm", 3_580L, 11, 2L)), // ends at 3602s
        )
        val (normNormal, anchorNormal) = normalizeLiveDashManifest(refreshNormal, nowMs = t0 + 3_000L, anchoredStartMs = lockedAnchor)
        assertEquals(lockedAnchor, anchorNormal)
        assertEquals(lockedAnchor, normNormal.availabilityStartTimeMs)

        // Stream stalls for 25s (drift = +25s > 15s threshold) -> re-anchors cleanly.
        val (normDrifted, anchorDrifted) = normalizeLiveDashManifest(refreshNormal, nowMs = t0 + 27_000L, anchoredStartMs = lockedAnchor)
        val expectedReanchor = (t0 + 27_000L) - 3_602_000L
        assertEquals(expectedReanchor, anchorDrifted)
        assertEquals(expectedReanchor, normDrifted.availabilityStartTimeMs)
    }

    @Test fun unboundedSegmentTemplateLeavesManifestUntouched() {
        val nowMs = 1_700_000_600_000L
        val originalStart = nowMs - 120_000L
        val unbounded = buildManifest(
            dynamic = true,
            availabilityStartTimeMs = originalStart,
            videoReps = listOf(unboundedRep("308", "video/webm")),
        )
        val (normalized, anchor) = normalizeLiveDashManifest(unbounded, nowMs = nowMs, anchoredStartMs = null)
        assertSame(unbounded, normalized)
        assertNull(anchor)
    }

    @Test fun liveConfigurationTargetsLowLatencyWithFasterCatchUp() {
        val config = liveMediaConfiguration()
        assertEquals(2_000L, config.targetOffsetMs)
        assertEquals(1_000L, config.minOffsetMs)
        assertEquals(3_500L, config.maxOffsetMs)
        assertEquals(0.95f, config.minPlaybackSpeed)
        assertEquals(1.10f, config.maxPlaybackSpeed)
    }
}
