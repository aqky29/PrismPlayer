package id.flufclip.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.FeedGrid(
    browse: BrowseState,
    searchState: SearchState,
    feedState: HomeFeedState,
    home: List<StreamInfoItem>,
    hidden: HiddenFeedItems,
    gridState: LazyGridState,
    loading: Boolean,
    error: String?,
    pullToRefreshEnabled: Boolean,
    screenWidth: Dp,
    onLoadHome: (refresh: Boolean) -> Unit,
    onSelectCategory: (category: String, retry: Boolean) -> Unit,
    onLoadMoreFeed: () -> Unit,
    onMoreSearchResults: () -> Unit,
    onLoadMoreBrowse: () -> Unit,
    onSubscribeChannel: (channelId: String, subscribe: Boolean, videoIdHint: String?) -> Unit,
    onNotificationPreference: (channelId: String, pref: NotificationPreference) -> Unit,
    onLoadChannelTab: (tab: String) -> Unit,
    onSearch: (query: String, filter: String) -> Unit,
    onOpenChannel: (url: String) -> Unit,
    onOpenPlaylist: (url: String) -> Unit,
    onOpenVideo: (item: StreamInfoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    error?.let { message ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            if (!searchState.mode && browse.channel == null) TextButton(onClick = { if (feedState.category == feedState.homeChipTitle) onLoadHome(false) else onSelectCategory(feedState.category, true) }) { Text("Try again") }
        }
    }
    val page = browse.channel
    val currentPlaylist = browse.playlist
    val filteredHome = remember(home, hidden.videos, hidden.channels) {
        home.filter { it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels }
    }
    val visibleItems: List<InfoItem> = if (currentPlaylist != null) browse.playlistItems else if (page != null) browse.channelItems else if (searchState.mode) searchState.results else if (feedState.category == feedState.homeChipTitle) filteredHome else feedState.categoryItems
    // A playlist can contain the same video more than once, so include its occurrence.
    val itemKeys = remember(visibleItems) {
        val counts = mutableMapOf<String, Int>()
        visibleItems.map { item ->
            val identity = "${item.infoType}:${item.url}"
            val occurrence = counts.getOrDefault(identity, 0)
            counts[identity] = occurrence + 1
            "feed:$identity:$occurrence"
        }
    }
    val isFeed = currentPlaylist == null && page == null && !searchState.mode
    LaunchedEffect(gridState, isFeed, feedState.category, visibleItems.size, feedState.feedLoading, feedState.feedError, feedState.feedEnd, loading) {
        if (isFeed && !feedState.feedLoading && feedState.feedError == null && !feedState.feedEnd && !loading && visibleItems.isNotEmpty()) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .distinctUntilChanged().collect { last ->
                    if (last >= visibleItems.size - 3) onLoadMoreFeed()
                }
        }
    }
    val isSearchResults = searchState.mode && page == null && currentPlaylist == null
    LaunchedEffect(searchState.lastSearch, searchState.filter, isSearchResults) { if (isSearchResults) gridState.scrollToItem(0) }
    LaunchedEffect(gridState, isSearchResults, searchState.results.size, searchState.moreLoading, searchState.moreError, searchState.page, loading) {
        if (isSearchResults && !loading && !searchState.moreLoading && searchState.moreError == null && searchState.page?.next != null && searchState.results.isNotEmpty()) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
                if (last >= searchState.results.size - 3) onMoreSearchResults()
            }
        }
    }
    LaunchedEffect(gridState, page, currentPlaylist, visibleItems.size, browse.next, browse.loading, browse.error) {
        if ((page != null || currentPlaylist != null) && browse.next != null && !browse.loading && browse.error == null && visibleItems.isNotEmpty()) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
                if (last >= visibleItems.size - 3) onLoadMoreBrowse()
            }
        }
    }
    val refreshState = rememberPullToRefreshState()
    val refreshingFeed = isFeed && loading
    val refreshModifier = if (isFeed) Modifier.pullToRefresh(
        isRefreshing = refreshingFeed,
        state = refreshState,
        enabled = pullToRefreshEnabled,
        onRefresh = { if (feedState.category == feedState.homeChipTitle) onLoadHome(true) else onSelectCategory(feedState.category, true) }
    ) else Modifier
    val isHomeFeed = isFeed && !searchState.mode && currentPlaylist == null && page == null
    val isWideScreen = screenWidth >= 600.dp
    val homeColumns = when {
        screenWidth >= 1600.dp -> 4
        screenWidth >= 900.dp -> 3
        screenWidth >= 600.dp -> 2
        else -> 1
    }

    val feedContentPadding = if (isHomeFeed && !isWideScreen) {
        PaddingValues(start = 0.dp, end = 0.dp, top = 2.dp, bottom = 92.dp)
    } else if (isSearchResults) {
        PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp)
    } else {
        PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp)
    }
    val feedHorizontalSpacing = if (isHomeFeed && !isWideScreen) 0.dp else 16.dp
    val feedVerticalSpacing = if (isHomeFeed && !isWideScreen) 0.dp else 20.dp

    Box(Modifier.weight(1f).fillMaxWidth().then(refreshModifier).then(modifier)) {
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            columns = if (isSearchResults) {
                GridCells.Fixed(1)
            } else if (isWideScreen) {
                GridCells.Fixed(homeColumns)
            } else {
                GridCells.Fixed(1)
            },
            contentPadding = feedContentPadding,
            horizontalArrangement = Arrangement.spacedBy(feedHorizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(feedVerticalSpacing)
        ) {
            if (currentPlaylist != null || page != null || searchState.mode) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (currentPlaylist != null) {
                        Column { Text(currentPlaylist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Playlist · ${browse.playlistItems.size} videos loaded", color = Color.LightGray, modifier = Modifier.padding(top = 8.dp)) }
                    } else if (page != null) {
                        val isChannelLive = remember(page, browse.channelItems) {
                            browse.channelItems.filterIsInstance<StreamInfoItem>().any { it.isOngoingLive() } || page.videos.any { it.isOngoingLive() }
                        }
                        ChannelHeader(
                            page = page,
                            selectedTab = browse.channelTab,
                            isLive = isChannelLive,
                            onSubscribe = { onSubscribeChannel(page.info.id, it, null) },
                            onNotificationPreference = { pref -> onNotificationPreference(page.info.id, pref) }
                        ) { onLoadChannelTab(it) }
                    } else if (searchState.mode) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("all" to "All", "videos" to "Video", "channels" to "Channel", "playlists" to "Playlist").forEach { (value, label) ->
                            val active = searchState.filter == value
                            Text(label, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (active) Color.White else Surface).clickable(enabled = !loading && !searchState.moreLoading) { if (value != searchState.filter) onSearch(searchState.lastSearch, value) }.padding(horizontal = 13.dp, vertical = 8.dp), color = if (active) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            items(visibleItems.size, key = { index -> itemKeys[index] }, contentType = { index -> visibleItems[index].infoType }) { index ->
                val item = visibleItems[index]
                if (isSearchResults) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BoxWithConstraints(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                            val wideResult = maxWidth >= 600.dp
                            when (item) {
                                is StreamInfoItem -> if (wideResult) SearchWideVideoCard(item, onChannel = { onOpenChannel(it) }) { onOpenVideo(item) } else VideoCard(item, onChannel = { onOpenChannel(it) }) { onOpenVideo(item) }
                                is ChannelInfoItem -> SearchChannelCard(item) { onOpenChannel(item.url) }
                                is PlaylistInfoItem -> if (wideResult) SearchWidePlaylistCard(item) { onOpenPlaylist(item.url) } else PlaylistCard(item) { onOpenPlaylist(item.url) }
                            }
                        }
                    }
                } else when (item) {
                    is StreamInfoItem -> {
                        val isMembership = browse.channel != null && browse.channelTab == VideoRepository.TAB_MEMBERSHIP
                        VideoCard(
                            item,
                            isMembership = isMembership,
                            onNotInterested = if (isFeed && feedState.category == feedState.homeChipTitle) {
                                { hidden.hideVideo(item.url) }
                            } else null,
                            onHideChannel = if (isFeed && feedState.category == feedState.homeChipTitle && !item.uploaderUrl.isNullOrBlank()) {
                                { hidden.hideChannel(item.uploaderUrl) }
                            } else null,
                            onChannel = { onOpenChannel(it) }
                        ) { onOpenVideo(item) }
                    }
                    is ChannelInfoItem -> ChannelCard(item) { onOpenChannel(item.url) }
                    is PlaylistInfoItem -> PlaylistCard(item) { onOpenPlaylist(item.url) }
                }
            }
            if (browse.loading && (page != null || currentPlaylist != null)) item(span = { GridItemSpan(maxLineSpan) }) { CircularProgressIndicator(Modifier.size(24.dp)) }
            if (browse.error != null && (page != null || currentPlaylist != null)) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(browse.error ?: "", Modifier.weight(1f)); TextButton(onClick = { onLoadMoreBrowse() }) { Text("Try again") } } }
            if (isSearchResults && searchState.moreLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
            if (isSearchResults && searchState.moreError != null) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(searchState.moreError ?: "", Modifier.weight(1f)); TextButton(onClick = { onMoreSearchResults() }) { Text("Try again") } } }
            if (isFeed && feedState.feedLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
            if (isFeed && feedState.feedError != null) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(feedState.feedError ?: "", Modifier.weight(1f), color = Color.LightGray); TextButton(onClick = { onLoadMoreFeed() }) { Text("Try again") } } }
            if (!loading && error == null && visibleItems.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(if (page != null) "No videos available yet." else "No results.", color = Color.LightGray) }
        }
        if (isFeed) PullToRefreshDefaults.Indicator(state = refreshState, isRefreshing = refreshingFeed, modifier = Modifier.align(Alignment.TopCenter))
    }
}
