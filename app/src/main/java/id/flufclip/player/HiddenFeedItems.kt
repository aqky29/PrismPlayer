package id.flufclip.player

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Video dan channel yang disembunyikan pengguna dari rekomendasi ("Not interested" / "Don't recommend channel"). */
@Stable
internal class HiddenFeedItems(private val prefs: SharedPreferences) {
    var videos by mutableStateOf(HomeRecommendations.read(prefs, "videos")); private set
    var channels by mutableStateOf(HomeRecommendations.read(prefs, "channels")); private set

    fun hideVideo(url: String) { videos = HomeRecommendations.hide(prefs, "videos", url) }
    fun hideChannel(url: String) { channels = HomeRecommendations.hide(prefs, "channels", url) }

    fun reset() {
        prefs.edit().clear().apply()
        videos = emptySet()
        channels = emptySet()
    }
}
