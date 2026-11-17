package id.flufclip.player

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamType
import androidx.compose.ui.unit.dp

class HomeFeedShortsParsingTest {
    private fun lockupVideo(id: String, title: String): JSONObject = JSONObject().put(
        "richItemRenderer",
        JSONObject().put(
            "content",
            JSONObject().put(
                "lockupViewModel",
                JSONObject()
                    .put("contentId", id)
                    .put("contentType", "LOCKUP_CONTENT_TYPE_VIDEO")
                    .put(
                        "metadata",
                        JSONObject().put(
                            "lockupMetadataViewModel",
                            JSONObject().put("title", JSONObject().put("content", title))
                        )
                    )
            )
        )
    )

    private fun shortsLockup(id: String, title: String, badge: String? = null): JSONObject {
        val sources = JSONArray()
            .put(JSONObject().put("url", "https://i.ytimg.com/vi/$id/hq.jpg").put("width", 405).put("height", 720))
            .put(JSONObject().put("url", "https://i.ytimg.com/vi/$id/low.jpg").put("width", 405).put("height", 608))
        return JSONObject().put(
            "shortsLockupViewModel",
            JSONObject()
                .put("entityId", "shorts-shelf-item-$id")
                .put(
                    "onTap",
                    JSONObject().put(
                        "innertubeCommand",
                        JSONObject().put(
                            "reelWatchEndpoint",
                            JSONObject()
                                .put("videoId", id)
                                .put("sequenceParams", "seq-$id")
                        )
                    )
                )
                .put("overlayMetadata", JSONObject()
                    .put("primaryText", JSONObject().put("content", title))
                    .put("secondaryText", JSONObject().put("content", "1.2K views")))
                .put("thumbnailViewModel", JSONObject().put("thumbnailViewModel", JSONObject()
                    .put("image", JSONObject().put("sources", sources))))
                .apply {
                    if (badge != null) {
                        put("badge", JSONObject().put("badgeViewModel", JSONObject().put("badgeText", badge)))
                    }
                }
        )
    }

    @Test
    fun parsePagePreservesShortsShelvesAndNewBadgesInOrder() {
        val gridShelf = JSONObject().put(
            "richSectionRenderer",
            JSONObject().put(
                "content",
                JSONObject().put(
                    "gridShelfViewModel",
                    JSONObject()
                        .put("header", JSONObject().put("sectionHeaderViewModel", JSONObject().put("headline", JSONObject().put("content", "Shorts"))))
                        .put("contents", JSONArray()
                            .put(shortsLockup("short000001", "Hacks Activated", badge = "New"))
                            .put(shortsLockup("short000002", "Wajar Lah Udah Lama", badge = "New")))
                )
            )
        )
        val rootContents = JSONArray()
            .put(gridShelf)
            .put(lockupVideo("video000001", "Bikin App Pake Claude"))

        val response = JSONObject().put(
            "contents",
            JSONObject().put(
                "twoColumnBrowseResultsRenderer",
                JSONObject().put(
                    "tabs",
                    JSONArray().put(
                        JSONObject().put(
                            "tabRenderer",
                            JSONObject().put(
                                "content",
                                JSONObject().put("richGridRenderer", JSONObject().put("contents", rootContents))
                            )
                        )
                    )
                )
            )
        )

        val page = HomeFeed.parsePage(response, isContinuation = false)
        assertEquals(2, page.entries.size)
        val shelf = page.entries[0]
        assertTrue(shelf is HomeEntry.ShortsShelf)
        shelf as HomeEntry.ShortsShelf
        assertEquals("Shorts", shelf.title)
        assertEquals(2, shelf.items.size)
        assertEquals("short000001", shelf.items[0].videoId)
        assertEquals("New", shelf.items[0].badgeText)
        assertEquals("seq-short000001", shelf.items[0].sequenceParams)
        assertEquals("https://i.ytimg.com/vi/short000001/hq.jpg", shelf.items[0].thumbnailUrl)
        assertEquals("short000002", shelf.items[1].videoId)

        val video = page.entries[1]
        assertTrue(video is HomeEntry.Video)
        assertEquals("Bikin App Pake Claude", (video as HomeEntry.Video).item.name)
    }

    @Test
    fun parseShortsCardSupportsRichShelfAndEntityIdFallback() {
        val richShelf = JSONObject().put(
            "richSectionRenderer",
            JSONObject().put(
                "content",
                JSONObject().put(
                    "richShelfRenderer",
                    JSONObject()
                        .put("title", JSONObject().put("runs", JSONArray().put(JSONObject().put("text", "Shorts"))))
                        .put(
                            "contents",
                            JSONArray().put(
                                JSONObject().put(
                                    "richItemRenderer",
                                    JSONObject().put(
                                        "content",
                                        JSONObject().put(
                                            "shortsLockupViewModel",
                                            JSONObject()
                                                .put("entityId", "shorts-shelf-item-short000003")
                                                .put("accessibilityText", "Fallback Title, 500 views - play Short")
                                        )
                                    )
                                )
                            )
                        )
                )
            )
        )
        val response = JSONObject().put(
            "onResponseReceivedActions",
            JSONArray().put(
                JSONObject().put(
                    "appendContinuationItemsAction",
                    JSONObject().put("continuationItems", JSONArray().put(richShelf))
                )
            )
        )

        val page = HomeFeed.parsePage(response, isContinuation = true)
        assertEquals(1, page.entries.size)
        val shelf = page.entries[0] as HomeEntry.ShortsShelf
        assertEquals("short000003", shelf.items[0].videoId)
        assertEquals("Fallback Title", shelf.items[0].title)
    }

    @Test
    fun homeShortsLayoutSpecMatchesMobileAndTabletBreakpoints() {
        assertEquals(2 to 4, homeShortsLayoutSpec(390.dp))
        assertEquals(3 to 6, homeShortsLayoutSpec(700.dp))
        assertEquals(4 to 4, homeShortsLayoutSpec(960.dp))
        assertEquals(5 to 5, homeShortsLayoutSpec(1280.dp))
    }

    @Test
    fun parseLockupDetectsActiveLivestreamFromStyleOrWatchingMetadata() {
        val watchingOnlyLockup = JSONObject()
            .put("contentId", "live0000001")
            .put("contentType", "LOCKUP_CONTENT_TYPE_VIDEO")
            .put(
                "metadata",
                JSONObject().put(
                    "lockupMetadataViewModel",
                    JSONObject()
                        .put("title", JSONObject().put("content", "Active Stream"))
                        .put(
                            "metadata",
                            JSONObject().put(
                                "contentMetadataViewModel",
                                JSONObject().put(
                                    "metadataRows",
                                    JSONArray()
                                        .put(JSONObject().put("metadataParts", JSONArray().put(JSONObject().put("text", JSONObject().put("content", "Nafari")))))
                                        .put(JSONObject().put("metadataParts", JSONArray().put(JSONObject().put("text", JSONObject().put("content", "225 watching")))))
                                )
                            )
                        )
                )
            )
        val styleBadgeLockup = JSONObject()
            .put("contentId", "live0000002")
            .put("contentType", "LOCKUP_CONTENT_TYPE_VIDEO")
            .put(
                "contentImage",
                JSONObject().put(
                    "thumbnailViewModel",
                    JSONObject().put(
                        "overlays",
                        JSONArray().put(
                            JSONObject().put(
                                "thumbnailBottomOverlayViewModel",
                                JSONObject().put(
                                    "badges",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "thumbnailBadgeViewModel",
                                            JSONObject()
                                                .put("text", "Sedang live")
                                                .put("badgeStyle", "THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE")
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            .put(
                "metadata",
                JSONObject().put(
                    "lockupMetadataViewModel",
                    JSONObject().put("title", JSONObject().put("content", "Style Live Stream"))
                )
            )
        val replayLockup = JSONObject()
            .put("contentId", "replay00001")
            .put("contentType", "LOCKUP_CONTENT_TYPE_VIDEO")
            .put(
                "contentImage",
                JSONObject().put(
                    "thumbnailViewModel",
                    JSONObject().put(
                        "overlays",
                        JSONArray().put(
                            JSONObject().put(
                                "thumbnailBottomOverlayViewModel",
                                JSONObject().put(
                                    "badges",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "thumbnailBadgeViewModel",
                                            JSONObject().put("text", "1:12:30")
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            .put(
                "metadata",
                JSONObject().put(
                    "lockupMetadataViewModel",
                    JSONObject()
                        .put("title", JSONObject().put("content", "Stream Replay"))
                        .put(
                            "metadata",
                            JSONObject().put(
                                "contentMetadataViewModel",
                                JSONObject().put(
                                    "metadataRows",
                                    JSONArray()
                                        .put(JSONObject().put("metadataParts", JSONArray().put(JSONObject().put("text", JSONObject().put("content", "Channel")))))
                                        .put(
                                            JSONObject().put(
                                                "metadataParts",
                                                JSONArray()
                                                    .put(JSONObject().put("text", JSONObject().put("content", "12K views")))
                                                    .put(JSONObject().put("text", JSONObject().put("content", "Streamed 2 days ago")))
                                            )
                                        )
                                )
                            )
                        )
                )
            )

        val liveItem1 = HomeFeed.parseLockupViewModel(watchingOnlyLockup)!!
        assertEquals(StreamType.LIVE_STREAM, liveItem1.streamType)
        assertEquals(225L, liveItem1.viewCount)
        assertTrue(HomeFeed.metaOf(liveItem1.url)?.live == true)

        val liveItem2 = HomeFeed.parseLockupViewModel(styleBadgeLockup)!!
        assertEquals(StreamType.LIVE_STREAM, liveItem2.streamType)
        assertTrue(HomeFeed.metaOf(liveItem2.url)?.live == true)

        val replayItem = HomeFeed.parseLockupViewModel(replayLockup)!!
        assertEquals(StreamType.VIDEO_STREAM, replayItem.streamType)
        assertFalse(HomeFeed.metaOf(replayItem.url)?.live == true)
    }

    @Test
    fun parseLockupIgnoresChannelAvatarLiveRingOnReplayVideos() {
        val replayWithLiveChannelAvatar = JSONObject()
            .put("contentId", "replay00002")
            .put("contentType", "LOCKUP_CONTENT_TYPE_VIDEO")
            .put(
                "contentImage",
                JSONObject().put(
                    "thumbnailViewModel",
                    JSONObject().put(
                        "overlays",
                        JSONArray().put(
                            JSONObject().put(
                                "thumbnailBottomOverlayViewModel",
                                JSONObject().put(
                                    "badges",
                                    JSONArray().put(
                                        JSONObject().put(
                                            "thumbnailBadgeViewModel",
                                            JSONObject().put("text", "1:12:30")
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
            .put(
                "metadata",
                JSONObject().put(
                    "lockupMetadataViewModel",
                    JSONObject()
                        .put("title", JSONObject().put("content", "Old Stream Replay"))
                        .put(
                            "image",
                            JSONObject().put(
                                "decoratedAvatarViewModel",
                                JSONObject().put(
                                    "liveData",
                                    JSONObject().put("liveBadgeText", "LIVE")
                                )
                            )
                        )
                        .put(
                            "metadata",
                            JSONObject().put(
                                "contentMetadataViewModel",
                                JSONObject().put(
                                    "metadataRows",
                                    JSONArray()
                                        .put(JSONObject().put("metadataParts", JSONArray().put(JSONObject().put("text", JSONObject().put("content", "Channel")))))
                                        .put(
                                            JSONObject().put(
                                                "metadataParts",
                                                JSONArray()
                                                    .put(JSONObject().put("text", JSONObject().put("content", "12K views")))
                                                    .put(JSONObject().put("text", JSONObject().put("content", "Streamed 2 days ago")))
                                            )
                                        )
                                )
                            )
                        )
                )
            )

        val item = HomeFeed.parseLockupViewModel(replayWithLiveChannelAvatar)!!
        assertEquals(StreamType.VIDEO_STREAM, item.streamType)
        assertEquals(4350L, item.duration)
        assertFalse(HomeFeed.metaOf(item.url)?.live == true)
        assertFalse(isCardLiveStream(item, VideoMeta(viewsText = "12K views", publishedText = "Streamed 2 days ago", live = true)))
    }
}
