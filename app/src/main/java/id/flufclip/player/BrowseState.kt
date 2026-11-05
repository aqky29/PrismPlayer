package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
}
