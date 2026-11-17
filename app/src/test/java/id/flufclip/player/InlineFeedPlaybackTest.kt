package id.flufclip.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class InlineFeedPlaybackTest {
    private fun streamItem(
        url: String,
        type: StreamType,
        shortForm: Boolean = false,
    ): StreamInfoItem = StreamInfoItem(
        ServiceList.YouTube.serviceId,
        url,
        "Sample Title",
        type,
    ).apply {
        isShortFormContent = shortForm
    }

    @Test
    fun isInlineFeedCandidateAcceptsRegularVideosAndLivestreams() {
        val vod = streamItem("https://www.youtube.com/watch?v=abc12345678", StreamType.VIDEO_STREAM)
        val live = streamItem("https://www.youtube.com/watch?v=liv12345678", StreamType.LIVE_STREAM)
        val audioLive = streamItem("https://www.youtube.com/watch?v=aud12345678", StreamType.AUDIO_LIVE_STREAM)
        val shortItem = streamItem(
            "https://www.youtube.com/shorts/sht12345678",
            StreamType.VIDEO_STREAM,
            shortForm = true,
        )

        assertTrue(isInlineFeedCandidate(vod))
        assertTrue(isInlineFeedCandidate(live))
        assertTrue(isInlineFeedCandidate(audioLive))
        assertFalse(isInlineFeedCandidate(shortItem))
    }

    @Test
    fun computeInlineTimelineStateForVodAndLiveModes() {
        val vod = computeInlineTimelineState(
            isLive = false,
            positionMs = 45_000L,
            durationMs = 180_000L,
            bufferedMs = 90_000L,
            windowDurationMs = 0L,
            windowDefaultPositionMs = 0L,
            isSeekable = true,
            liveOffsetMs = -1L,
            targetOffsetMs = -1L,
        )
        assertFalse(vod.isLive)
        assertFalse(vod.isDvr)
        assertTrue(vod.canSeek)
        assertTrue(vod.showScrubber)
        assertEquals(45_000L, vod.barPositionMs)
        assertEquals(90_000L, vod.barBufferedMs)
        assertEquals(180_000L, vod.barDurationMs)

        val liveNoDvr = computeInlineTimelineState(
            isLive = true,
            positionMs = 1_500L,
            durationMs = 3_000L,
            bufferedMs = 3_000L,
            windowDurationMs = 3_000L,
            windowDefaultPositionMs = 2_500L,
            isSeekable = false,
            liveOffsetMs = 2_000L,
            targetOffsetMs = 2_000L,
        )
        assertTrue(liveNoDvr.isLive)
        assertFalse(liveNoDvr.isDvr)
        assertFalse(liveNoDvr.canSeek)
        assertFalse(liveNoDvr.showScrubber)

        val liveAtEdge = computeInlineTimelineState(
            isLive = true,
            positionMs = 7_190_000L,
            durationMs = 0L,
            bufferedMs = 7_195_000L,
            windowDurationMs = 7_200_000L,
            windowDefaultPositionMs = 7_198_000L,
            isSeekable = true,
            liveOffsetMs = 2_000L,
            targetOffsetMs = 2_000L,
        )
        assertTrue(liveAtEdge.isLive)
        assertTrue(liveAtEdge.isDvr)
        assertTrue(liveAtEdge.canSeek)
        assertTrue(liveAtEdge.showScrubber)
        assertFalse(liveAtEdge.behindLive)
        assertEquals(7_198_000L, liveAtEdge.barPositionMs)
        assertEquals(7_198_000L, liveAtEdge.barDurationMs)

        val liveBehind = computeInlineTimelineState(
            isLive = true,
            positionMs = 6_900_000L,
            durationMs = 0L,
            bufferedMs = 7_100_000L,
            windowDurationMs = 7_200_000L,
            windowDefaultPositionMs = 7_198_000L,
            isSeekable = true,
            liveOffsetMs = 298_000L,
            targetOffsetMs = 2_000L,
        )
        assertTrue(liveBehind.behindLive)
        assertEquals(6_900_000L, liveBehind.barPositionMs)
        assertEquals("LIVE", formatInlineScrubLabel(7_197_000L, liveBehind))
        assertEquals("-4:58", formatInlineScrubLabel(6_900_000L, liveBehind))
        assertEquals("0:45 / 3:00", formatInlineScrubLabel(45_000L, vod))
    }

    @Test
    fun isInlinePlaybackEligibleRespectsOverlaysOfflineMiniAndTabs() {
        fun eligible(
            hasSelectedVideo: Boolean = false,
            hasPendingVideo: Boolean = false,
            shortsMode: Boolean = false,
            inPip: Boolean = false,
            showHistory: Boolean = false,
            hasAccountPlaylist: Boolean = false,
            showLoginDialog: Boolean = false,
            showAccountDialog: Boolean = false,
            hasChannelDownloadTarget: Boolean = false,
            hasOfflineMiniSession: Boolean = false,
            mainTab: String = "home",
            searchActive: Boolean = false,
            searchMode: Boolean = false,
            searchFilter: String = "all",
            hasBrowseChannel: Boolean = false,
            hasBrowsePlaylist: Boolean = false,
        ) = isInlinePlaybackEligible(
            hasSelectedVideo = hasSelectedVideo,
            hasPendingVideo = hasPendingVideo,
            shortsMode = shortsMode,
            inPip = inPip,
            showHistory = showHistory,
            hasAccountPlaylist = hasAccountPlaylist,
            showLoginDialog = showLoginDialog,
            showAccountDialog = showAccountDialog,
            hasChannelDownloadTarget = hasChannelDownloadTarget,
            hasOfflineMiniSession = hasOfflineMiniSession,
            mainTab = mainTab,
            searchActive = searchActive,
            searchMode = searchMode,
            searchFilter = searchFilter,
            hasBrowseChannel = hasBrowseChannel,
            hasBrowsePlaylist = hasBrowsePlaylist,
        )

        assertTrue(eligible(mainTab = "home"))
        assertTrue(eligible(mainTab = "subscriptions"))
        assertTrue(eligible(searchMode = true, searchFilter = "videos"))

        assertFalse(eligible(mainTab = "you"))
        assertFalse(eligible(mainTab = "shorts"))
        assertFalse(eligible(hasOfflineMiniSession = true))
        assertFalse(eligible(hasAccountPlaylist = true))
        assertFalse(eligible(showLoginDialog = true))
        assertFalse(eligible(showAccountDialog = true))
        assertFalse(eligible(hasChannelDownloadTarget = true))
        assertFalse(eligible(hasBrowseChannel = true))
        assertFalse(eligible(hasBrowsePlaylist = true))
        assertFalse(eligible(searchActive = true))
        assertFalse(eligible(searchMode = true, searchFilter = "channels"))
        assertFalse(eligible(hasSelectedVideo = true))
        assertFalse(eligible(hasPendingVideo = true))
        assertFalse(eligible(shortsMode = true))
        assertFalse(eligible(inPip = true))
        assertFalse(eligible(showHistory = true))
    }
}
