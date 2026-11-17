package id.flufclip.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackManifestTest {
    @Test fun shortLiveHlsWithLargeDashWindowUsesDash() {
        val hls = hlsWindowSeconds("#EXTM3U\n#EXTINF:1.0,\na\n#EXTINF:1.0,\nb\n#EXTINF:1.0,\nc\n")
        val dash = dashWindowSeconds("<MPD type=\"dynamic\" timeShiftBufferDepth=\"PT7200.000S\">")
        assertEquals(true, preferDashForDvr(hls, dash))
        assertEquals(PlaybackManifest.DASH, playbackManifest(true, "hls", "dash", preferDashForDvr(hls, dash)))
    }

    @Test fun unknownOrLongHlsPreservesExistingSelection() {
        assertEquals(false, preferDashForDvr(null, 7200.0))
        assertEquals(false, preferDashForDvr(300.0, 7200.0))
        assertEquals(false, preferDashForDvr(3.0, null))
        assertEquals(PlaybackManifest.HLS, playbackManifest(true, "hls", "dash", false))
    }
    @Test fun liveWithBothManifestsUsesHlsWallClockInsteadOfDashTimeline() {
        assertEquals(PlaybackManifest.HLS, playbackManifest(true, "https://media/live.m3u8", "https://media/live.mpd"))
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

    @Test fun liveConfigurationTargetsLowLatencyWithFasterCatchUp() {
        val config = liveMediaConfiguration()
        assertEquals(2_000L, config.targetOffsetMs)
        assertEquals(1_000L, config.minOffsetMs)
        assertEquals(3_500L, config.maxOffsetMs)
        assertEquals(0.95f, config.minPlaybackSpeed)
        assertEquals(1.10f, config.maxPlaybackSpeed)
    }
}
