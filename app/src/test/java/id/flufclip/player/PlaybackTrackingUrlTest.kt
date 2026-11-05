package id.flufclip.player

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaybackTrackingUrlTest {
    private fun tracking(url: String) = JSONObject().put("videostatsPlaybackUrl", JSONObject().put("baseUrl", url))

    @Test fun acceptsYoutubeTrackingUrlAndPreservesServerParameters() {
        val url = "https://s.youtube.com/api/stats/playback?ns=yt&token=abc"
        assertEquals(url, PlaybackTracker.trackingUrl(tracking(url), "videostatsPlaybackUrl"))
    }

    @Test fun rejectsNonYoutubeHostsAndUnexpectedPaths() {
        for (url in listOf("https://youtube.com.evil.test/api/stats/playback", "http://s.youtube.com/api/stats/playback", "https://s.youtube.com/other", "https://s.youtube.com:8443/api/stats/playback")) {
            assertNull(PlaybackTracker.trackingUrl(tracking(url), "videostatsPlaybackUrl"))
        }
        assertNull(PlaybackTracker.trackingUrl(JSONObject(), "videostatsPlaybackUrl"))
    }
}
