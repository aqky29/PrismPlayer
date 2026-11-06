package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfo

/**
 * State untuk halaman penjelajahan channel dan playlist.
 */
@Stable
internal class BrowseState {
    var channel by mutableStateOf<ChannelPage?>(null)
    var channelTab by mutableStateOf(ChannelTabs.VIDEOS)
    var channelItems by mutableStateOf<List<InfoItem>>(emptyList())
    var playlist by mutableStateOf<PlaylistInfo?>(null)
    var playlistItems by mutableStateOf<List<InfoItem>>(emptyList())
    var next by mutableStateOf<Page?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun loadChannelTab(
        filter: String,
        scope: CoroutineScope,
        pageLoading: Boolean,
        onPageLoading: (Boolean) -> Unit,
        onPageError: (String?) -> Unit
    ) {
        val page = channel ?: return
        if (pageLoading) return
        channelTab = filter; channelItems = emptyList(); onPageError(null); onPageLoading(true)
        scope.launch {
            try { val result = VideoRepository.channelTabPage(page, filter); channelItems = result.items; next = result.next; error = null }
            catch (e: Exception) { onPageError("Failed to load tab: ${e.message ?: "try again"}") }
            finally { onPageLoading(false) }
        }
    }

    fun loadMoreBrowse(scope: CoroutineScope) {
        val next = next ?: return
        val currentPlaylist = playlist
        val currentChannel = channel
        val currentTab = channelTab
        if (loading) return
        loading = true; error = null
        scope.launch {
            try {
                val result = if (currentPlaylist != null) VideoRepository.morePlaylist(currentPlaylist.url, next) else if (currentChannel != null) VideoRepository.moreChannelTab(currentChannel, currentTab, next) else return@launch
                if (playlist != currentPlaylist || channel != currentChannel || channelTab != currentTab) return@launch
                val old = if (currentPlaylist != null) playlistItems else channelItems
                val merged = (old + result.items).distinctBy { it.url }
                if (currentPlaylist != null) playlistItems = merged else channelItems = merged
                this@BrowseState.next = if (merged.size == old.size) null else result.next
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "Failed to load next page: ${e.message ?: "try again"}" }
            finally { loading = false }
        }
    }
}

