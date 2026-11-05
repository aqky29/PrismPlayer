package id.flufclip.player

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountHistoryPreviewTest {
    private fun short(id: String) = JSONObject().put("shortsLockupViewModel", JSONObject()
        .put("onTap", JSONObject().put("innertubeCommand", JSONObject().put("reelWatchEndpoint", JSONObject().put("videoId", id))))
        .put("overlayMetadata", JSONObject().put("primaryText", JSONObject().put("content", "Short"))))

    @Test fun shelfShowsTwoShortsBeforeVideoAndKeepsAllHistoryEntries() {
        val shorts = JSONArray()
        repeat(26) { shorts.put(short("short%06d".format(it))) }
        val otherShelf = JSONArray().put(short("other000001"))
        val contents = JSONArray().put(JSONObject().put("reelShelfRenderer", JSONObject().put("items", shorts)))
            .put(JSONObject().put("videoRenderer", JSONObject().put("videoId", "video000001")
                .put("title", JSONObject().put("simpleText", "Video"))))
            .put(JSONObject().put("reelShelfRenderer", JSONObject().put("items", otherShelf)))
        val response = JSONObject().put("contents", JSONObject().put("sectionListRenderer", JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("itemSectionRenderer", JSONObject().put("contents", contents))))))
        val page = AccountHistory.parse(response)
        assertEquals(28, page.entries.size)
        val preview = accountHistoryPreview(page.entries)
        assertEquals(4, preview.size)
        assertEquals(listOf(true, true, false, true), preview.map { it.isShort })
        assertEquals("Video", preview[2].title)
    }

    @Test fun historyProgressUsesOnlyServerProvidedValue() {
        val progress = JSONObject().put("startPercent", 37)
        val overlay = JSONObject().put("thumbnailBottomOverlayViewModel", JSONObject()
            .put("progressBar", JSONObject().put("thumbnailOverlayProgressBarViewModel", progress)))
        val lockup = JSONObject().put("contentImage", JSONObject().put("thumbnailViewModel", JSONObject()
            .put("overlays", JSONArray().put(overlay))))
        assertEquals(.37f, HomeFeed.historyProgress(lockup)!!, .001f)
        assertNull(HomeFeed.historyProgress(JSONObject()))
    }
}
