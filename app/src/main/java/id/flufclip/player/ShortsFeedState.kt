package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * State untuk feed dan antrean Shorts di tab Shorts maupun pembuka Short dari layar lain.
 */
@Stable
internal class ShortsFeedState {
    var request by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var page by mutableStateOf<SearchPage?>(null)
    var moreLoading by mutableStateOf(false)
    var moreError by mutableStateOf<String?>(null)
    var moreRequest by mutableIntStateOf(0)
    var topicIndex by mutableIntStateOf(0)
    var index by mutableIntStateOf(0)
    var retry by mutableIntStateOf(0)
    var generation by mutableIntStateOf(0)
    var tabQueue by mutableStateOf(false)
    var seqNext by mutableStateOf<String?>(null)
    var fromFeed by mutableStateOf(false)
    var downloadInfo by mutableStateOf<StreamInfo?>(null)
}
