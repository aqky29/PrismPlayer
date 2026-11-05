package id.flufclip.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackRequestHeadersTest {
    @Test fun youtubeLoginCookiesNeverReachMediaCdn() {
        val headers = playbackRequestHeaders("https://rr1.googlevideo.com/videoplayback?c=ANDROID", "test=value", mapOf("cookie" to "old=value", "Range" to "bytes=100-"))
        assertFalse(headers.keys.any { it.equals("Cookie", true) })
        assertEquals("bytes=100-", headers["Range"])
        assertTrue(headers.getValue("User-Agent").contains("Android"))
    }

    @Test fun cookieHostMatchingRequiresAnActualYoutubeDomain() {
        for (host in listOf("youtube.com.evil.test", "notyoutube.com", "googlevideo.com")) {
            assertFalse(playbackRequestHeaders("https://$host/media", "test=value").containsKey("Cookie"))
        }
        assertEquals("test=value", playbackRequestHeaders("https://www.youtube.com/media", "test=value")["Cookie"])
    }

    @Test fun signedManifestPathAndSegmentQueryUseTheSameClient() {
        val manifest = playbackRequestHeaders("https://manifest.googlevideo.com/api/manifest/dash/c/ANDROID/id/test", "")
        val segment = playbackRequestHeaders("https://rr1.googlevideo.com/videoplayback?id=test&c=ANDROID", "")
        assertEquals(manifest["User-Agent"], segment["User-Agent"])
        assertNotEquals(manifest["User-Agent"], playbackRequestHeaders("https://rr1.googlevideo.com/videoplayback?c=IOS", "")["User-Agent"])
    }

    @Test fun insecureTargetsAndExistingCredentialsAreRejected() {
        for (url in listOf("http://youtube.com/media", "https://youtube.com:8443/media", "https://user@youtube.com/media", "https://other.test/path?next=youtube.com")) {
            val headers = playbackRequestHeaders(url, "test=value", mapOf("aUtHoRiZaTiOn" to "secret", "COOKIE" to "old", "X-Goog-AuthUser" to "0"))
            assertFalse(headers.keys.any { key -> sensitiveAccountHeaders.any { it.equals(key, true) } })
        }
    }
}
