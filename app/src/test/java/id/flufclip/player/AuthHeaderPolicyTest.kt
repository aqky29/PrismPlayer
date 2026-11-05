package id.flufclip.player

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthHeaderPolicyTest {
    @Test fun restrictsAccountHeadersToHttpsYouTubeHosts() {
        listOf("https://youtube.com/", "https://www.youtube.com/watch").forEach {
            assertTrue(it, permitsAccountHeaders(it.toHttpUrl()))
        }
        listOf(
            "https://notyoutube.com/", "https://youtube.com.example.invalid/",
            "https://example.invalid/youtube.com", "https://example.invalid/?q=youtube.com",
            "http://youtube.com/", "https://youtube.com:8443/", "https://user@youtube.com/"
        ).forEach { assertFalse(it, permitsAccountHeaders(it.toHttpUrl())) }
    }
}
