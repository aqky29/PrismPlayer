package id.flufclip.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page

/**
 * State untuk feed beranda, kategori/chips, paging feed, dan sesi muat beranda.
 */
@Stable
internal class HomeFeedState(
    initialLoggedIn: Boolean = YouTubeAuth.isLoggedIn,
    initialSessionRevision: Int = YouTubeAuth.sessionRevision
) {
    var homePage by mutableStateOf<SearchPage?>(null)
    var homeContinuationToken by mutableStateOf<String?>(null)
    var category by mutableStateOf("All")
    var categoryItems by mutableStateOf<List<InfoItem>>(emptyList())
    var categoryPage by mutableStateOf<SearchPage?>(null)
    var liveNext by mutableStateOf<Page?>(null)
    var homeChips by mutableStateOf<List<HomeChip>>(emptyList())
    var selectedChip by mutableStateOf<HomeChip?>(null)
    var categoryContinuation by mutableStateOf<String?>(null)
    var homeChipTitle by mutableStateOf("All")
    var feedLoading by mutableStateOf(false)
    var feedError by mutableStateOf<String?>(null)
    var feedEnd by mutableStateOf(false)
    var topicIndex by mutableIntStateOf(0)
    var homeLoadJob by mutableStateOf<Job?>(null)
    var lastAccountState by mutableStateOf(initialLoggedIn)
    var homeSessionRevision by mutableIntStateOf(initialSessionRevision)
}
