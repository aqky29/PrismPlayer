package id.flufclip.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

internal fun isActiveChannelBrowse(channel: ChannelPage?, playlist: PlaylistInfo?): Boolean =
    channel != null && playlist == null

internal fun shouldShowCenteredSearchLoader(isSearchResults: Boolean, loading: Boolean, itemCount: Int): Boolean =
    isSearchResults && loading && itemCount == 0

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
    onOpenShorts: ((cards: List<ShortsCard>, index: Int) -> Unit)? = null,
    onSaveShort: ((card: ShortsCard) -> Unit)? = null,
    subscribedChannels: List<SubsChannel> = emptyList(),
    channelVideoActions: ChannelVideoActions? = null,
    channelPlaylistActions: ChannelPlaylistActions? = null,
    onClearChannelSearch: (() -> Unit)? = null,
    onInlineCandidateChanged: ((StreamInfoItem?) -> Unit)? = null,
    inlineOverlayFor: ((StreamInfoItem) -> (@Composable BoxScope.() -> Unit)?)? = null,
    modifier: Modifier = Modifier,
) {
    val page = browse.channel
    val currentPlaylist = browse.playlist
    val filteredHome = remember(home, hidden.videos, hidden.channels) {
        home.filter { it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels }
    }
    val visibleItems: List<InfoItem> = remember(
        currentPlaylist,
        browse.playlistItems,
        page,
        browse.channelItems,
        browse.channelTab,
        browse.channelSort,
        browse.channelAccessFilter,
        browse.playlistSort,
        browse.channelSearchSubmitted,
        browse.channelSearchResults,
        searchState.mode,
        searchState.results,
        feedState.category,
        filteredHome,
        feedState.categoryItems
    ) {
        when {
            currentPlaylist != null -> browse.playlistItems
            page != null && browse.channelSearchSubmitted.isNotBlank() -> browse.channelSearchResults
            page != null -> {
                val raw = browse.channelItems
                when (browse.channelTab) {
                    ChannelTabs.VIDEOS, ChannelTabs.LIVESTREAMS, ChannelTabs.SHORTS -> {
                        val filtered = when (browse.channelAccessFilter) {
                            ChannelAccessFilter.ALL -> raw
                            ChannelAccessFilter.MEMBERS_ONLY -> raw.filter { item ->
                                item is StreamInfoItem && page.isMemberVideo(item)
                            }
                            ChannelAccessFilter.PUBLIC -> raw.filter { item ->
                                item !is StreamInfoItem || !page.isMemberVideo(item)
                            }
                        }
                        when (browse.channelSort) {
                            ChannelSortOrder.LATEST -> filtered
                            ChannelSortOrder.POPULAR -> filtered.sortedByDescending { (it as? StreamInfoItem)?.viewCount ?: -1L }
                            ChannelSortOrder.OLDEST -> filtered.asReversed()
                        }
                    }
                    ChannelTabs.PLAYLISTS -> when (browse.playlistSort) {
                        PlaylistSortOrder.RECENT -> raw
                        PlaylistSortOrder.NAME -> raw.sortedBy { it.name.lowercase() }
                    }
                    else -> raw
                }
            }
            searchState.mode -> searchState.results
            feedState.category == feedState.homeChipTitle -> filteredHome
            else -> feedState.categoryItems
        }
    }
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
    val isSearchResults = searchState.mode && page == null && currentPlaylist == null
    val inlineSearchEligible = isSearchResults && isInlineSearchFilter(searchState.filter)
    val visibleFeedEntries: List<HomeEntry> = remember(
        isFeed,
        feedState.category,
        feedState.homeChipTitle,
        feedState.homeEntries,
        feedState.categoryEntries,
        filteredHome,
        feedState.categoryItems,
        hidden.videos,
        hidden.channels
    ) {
        if (!isFeed) {
            emptyList()
        } else {
            val isMainHome = feedState.category == feedState.homeChipTitle
            val base = if (isMainHome) feedState.homeEntries else feedState.categoryEntries
            val resolved = if (base.isNotEmpty()) {
                base
            } else {
                val fallbackVideos = if (isMainHome) filteredHome else feedState.categoryItems.filterIsInstance<StreamInfoItem>()
                fallbackVideos.map { HomeEntry.Video(it) }
            }
            resolved.mapNotNull { entry ->
                when (entry) {
                    is HomeEntry.Video -> {
                        val v = entry.item
                        if (v.url !in hidden.videos && v.uploaderUrl.orEmpty() !in hidden.channels) entry else null
                    }
                    is HomeEntry.ShortsShelf -> {
                        val visibleCards = entry.items.filter { card ->
                            "https://www.youtube.com/shorts/${card.videoId}" !in hidden.videos &&
                                "https://www.youtube.com/watch?v=${card.videoId}" !in hidden.videos
                        }
                        if (visibleCards.isEmpty()) null else entry.copy(items = visibleCards)
                    }
                }
            }
        }
    }
    if (page == null && (!isFeed || visibleFeedEntries.isNotEmpty())) {
        error?.let { message ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, color = ErrorText, modifier = Modifier.weight(1f))
                if (!searchState.mode) {
                    TextButton(
                        onClick = { if (feedState.category == feedState.homeChipTitle) onLoadHome(false) else onSelectCategory(feedState.category, true) },
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Try again")
                    }
                }
            }
        }
    }
    val feedEntryKeys = remember(visibleFeedEntries) {
        val counts = mutableMapOf<String, Int>()
        visibleFeedEntries.map { entry ->
            val identity = when (entry) {
                is HomeEntry.Video -> "video:${entry.item.url}"
                is HomeEntry.ShortsShelf -> "shorts:${entry.items.first().videoId}"
            }
            val occurrence = counts.getOrDefault(identity, 0)
            counts[identity] = occurrence + 1
            "home:$identity:$occurrence"
        }
    }
    val keyToIndex = remember(itemKeys, feedEntryKeys, isFeed) {
        (if (isFeed) feedEntryKeys else itemKeys).withIndex().associate { (i, k) -> k to i }
    }
    LaunchedEffect(gridState, isFeed, inlineSearchEligible, visibleFeedEntries, visibleItems, keyToIndex, onInlineCandidateChanged) {
        val callback = onInlineCandidateChanged ?: return@LaunchedEffect
        val activeFeedEmpty = if (isFeed) visibleFeedEntries.isEmpty() else visibleItems.isEmpty()
        if ((!isFeed && !inlineSearchEligible) || activeFeedEmpty) {
            callback(null)
            return@LaunchedEffect
        }
        snapshotFlow {
            val info = gridState.layoutInfo
            val scrolling = gridState.isScrollInProgress
            if (scrolling) {
                null to true
            } else {
                val vpStart = info.viewportStartOffset
                val vpEnd = info.viewportEndOffset
                val best = info.visibleItemsInfo.mapNotNull { entry ->
                    val idx = keyToIndex[entry.key] ?: return@mapNotNull null
                    val stream = when {
                        isFeed -> (visibleFeedEntries.getOrNull(idx) as? HomeEntry.Video)?.item
                        inlineSearchEligible -> visibleItems.getOrNull(idx) as? StreamInfoItem
                        else -> null
                    } ?: return@mapNotNull null
                    if (!isInlineFeedCandidate(stream)) return@mapNotNull null
                    val top = entry.offset.y
                    val height = entry.size.height.coerceAtLeast(1)
                    val bottom = top + height
                    val visibleHeight = (minOf(bottom, vpEnd) - maxOf(top, vpStart)).coerceAtLeast(0)
                    val ratio = visibleHeight.toFloat() / height.toFloat()
                    if (ratio < 0.5f) return@mapNotNull null
                    Triple(stream, ratio, top)
                }.maxWithOrNull(compareBy<Triple<StreamInfoItem, Float, Int>> { it.second }.thenByDescending { it.third })?.first
                best to false
            }
        }
            .distinctUntilChanged { old, new -> old.first?.url == new.first?.url && old.second == new.second }
            .collectLatest { (candidate, scrolling) ->
                if (!scrolling) {
                    delay(380L)
                    callback(candidate)
                }
            }
    }
    LaunchedEffect(gridState, isFeed, feedState.category, visibleFeedEntries.size, feedState.feedLoading, feedState.feedError, feedState.feedEnd, loading) {
        if (isFeed && !feedState.feedLoading && feedState.feedError == null && !feedState.feedEnd && !loading && visibleFeedEntries.isNotEmpty()) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .distinctUntilChanged().collect { last ->
                    if (last >= visibleFeedEntries.size - 3) onLoadMoreFeed()
                }
        }
    }
    LaunchedEffect(searchState.lastSearch, searchState.filter, isSearchResults) { if (isSearchResults) gridState.scrollToItem(0) }
    LaunchedEffect(gridState, isSearchResults, searchState.results.size, searchState.moreLoading, searchState.moreError, searchState.page, loading) {
        if (isSearchResults && !loading && !searchState.moreLoading && searchState.moreError == null && searchState.page?.next != null && searchState.results.isNotEmpty()) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
                if (last >= searchState.results.size - 3) onMoreSearchResults()
            }
        }
    }
    LaunchedEffect(
        gridState,
        page,
        currentPlaylist,
        browse.channelTab,
        browse.channelSearchSubmitted,
        browse.channelPosts.size,
        browse.channelPostsNextToken,
        visibleItems.size,
        browse.next,
        browse.loading,
        browse.error
    ) {
        if (browse.loading || browse.error != null || browse.channelSearchSubmitted.isNotBlank()) return@LaunchedEffect
        val isPostsPaging = page != null && currentPlaylist == null &&
            browse.channelTab == VideoRepository.TAB_POSTS &&
            browse.channelPostsNextToken != null &&
            browse.channelPosts.isNotEmpty()
        val isListPaging = (page != null || currentPlaylist != null) &&
            browse.channelTab != VideoRepository.TAB_HOME &&
            browse.channelTab != VideoRepository.TAB_POSTS &&
            browse.next != null &&
            visibleItems.isNotEmpty()
        if (isPostsPaging || isListPaging) {
            val total = if (isPostsPaging) browse.channelPosts.size else visibleItems.size
            val threshold = if (isPostsPaging) 2 else 3
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .distinctUntilChanged().collect { last ->
                    if (last >= total - threshold) onLoadMoreBrowse()
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
    val isHomeFeed = isFeed
    val isChannelView = isActiveChannelBrowse(page, currentPlaylist)
    val isChannelSearchView = isChannelView && browse.channelSearchSubmitted.isNotBlank()
    val isChannelHome = isChannelView && !isChannelSearchView && browse.channelTab == VideoRepository.TAB_HOME
    val isChannelPosts = isChannelView && !isChannelSearchView && browse.channelTab == VideoRepository.TAB_POSTS
    val isChannelShorts = isChannelView && !isChannelSearchView && browse.channelTab == ChannelTabs.SHORTS
    val isWideScreen = screenWidth >= 600.dp
    val homeColumns = when {
        screenWidth >= 1600.dp -> 4
        screenWidth >= 900.dp -> 3
        screenWidth >= 600.dp -> 2
        else -> 1
    }
    val shortColumns = when {
        screenWidth >= 1200.dp -> 5
        screenWidth >= 600.dp -> 4
        else -> 3
    }

    val feedContentPadding = when {
        (isHomeFeed || isSearchResults) && !isWideScreen -> PaddingValues(start = 0.dp, end = 0.dp, top = 2.dp, bottom = 16.dp)
        isChannelView && !isWideScreen -> PaddingValues(start = 0.dp, end = 0.dp, top = 8.dp, bottom = 20.dp)
        else -> PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 20.dp)
    }
    val feedHorizontalSpacing = when {
        (isHomeFeed || isSearchResults) && !isWideScreen -> 0.dp
        isChannelShorts && !isWideScreen -> 2.dp
        isChannelShorts -> 8.dp
        isChannelView && !isWideScreen -> 0.dp
        else -> 16.dp
    }
    val feedVerticalSpacing = when {
        (isHomeFeed || isSearchResults) && !isWideScreen -> 0.dp
        isChannelShorts && !isWideScreen -> 2.dp
        isChannelShorts -> 10.dp
        isChannelView && !isWideScreen -> 14.dp
        else -> 18.dp
    }

    val showCenteredSearchLoader = shouldShowCenteredSearchLoader(isSearchResults, loading, visibleItems.size)
    val showSearchHeader = isSearchResults && !showCenteredSearchLoader && (
        searchState.filter != org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.ALL ||
            !searchState.searchSuggestion.isNullOrBlank()
    )

    Box(Modifier.weight(1f).fillMaxWidth().then(refreshModifier).then(modifier)) {
        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            columns = when {
                isChannelShorts -> GridCells.Fixed(shortColumns)
                isSearchResults -> GridCells.Fixed(1)
                isWideScreen -> GridCells.Fixed(homeColumns)
                else -> GridCells.Fixed(1)
            },
            contentPadding = feedContentPadding,
            horizontalArrangement = Arrangement.spacedBy(feedHorizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(feedVerticalSpacing)
        ) {
            if (currentPlaylist != null || page != null || showSearchHeader) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (currentPlaylist != null) {
                        Column {
                            Text(currentPlaylist.name, color = TextPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Playlist · ${browse.playlistItems.size} videos loaded", color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
                        }
                    } else if (page != null && isChannelSearchView) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isWideScreen) 0.dp else 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Results for \"${browse.channelSearchSubmitted}\"",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            onClearChannelSearch?.let { clear ->
                                TextButton(onClick = clear, modifier = Modifier.heightIn(min = 44.dp)) {
                                    Text("Clear", color = CallToActionBlue)
                                }
                            }
                        }
                    } else if (page != null) {
                        val isChannelLive = remember(page, browse.channelItems) {
                            browse.channelItems.filterIsInstance<StreamInfoItem>().any { it.isOngoingLive() } || page.videos.any { it.isOngoingLive() }
                        }
                        Box(Modifier.fillMaxWidth().padding(horizontal = if (isWideScreen) 0.dp else 16.dp)) {
                            ChannelHeader(
                                page = page,
                                selectedTab = browse.channelTab,
                                isLive = isChannelLive,
                                channelSort = browse.channelSort,
                                channelAccessFilter = browse.channelAccessFilter,
                                playlistSort = browse.playlistSort,
                                subscribedChannels = subscribedChannels,
                                onSubscribe = { onSubscribeChannel(page.info.id, it, null) },
                                onNotificationPreference = { pref -> onNotificationPreference(page.info.id, pref) },
                                onSelectSort = { browse.channelSort = it },
                                onSelectAccessFilter = { browse.channelAccessFilter = it },
                                onSelectPlaylistSort = { browse.playlistSort = it },
                                onTab = { onLoadChannelTab(it) }
                            )
                        }
                    } else if (showSearchHeader) Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isWideScreen) 0.dp else 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (searchState.filter != org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory.ALL) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SEARCH_FILTER_CHIPS.forEach { (value, label) ->
                                    val active = searchState.filter == value
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = 44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) TextPrimary else Surface)
                                            .clickable(enabled = !loading && !searchState.moreLoading) { if (value != searchState.filter) onSearch(searchState.lastSearch, value) }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(label, color = if (active) Background else TextPrimary, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                        val suggestion = searchState.searchSuggestion
                        if (!loading && !suggestion.isNullOrBlank()) {
                            val prefix = if (searchState.isCorrectedSearch) "Showing results for " else "Did you mean "
                            val bannerText = remember(prefix, suggestion) {
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = TextSecondary)) { append(prefix) }
                                    withStyle(SpanStyle(color = CallToActionBlue, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic)) {
                                        append(suggestion)
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(role = Role.Button) { onSearch(suggestion, searchState.filter) }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = bannerText,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            if (isChannelView && !isChannelSearchView && browse.channelTab == VideoRepository.TAB_MEMBERSHIP && page != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val memberStreamItems = remember(visibleItems) {
                        visibleItems.filterIsInstance<StreamInfoItem>()
                    }
                    Box(Modifier.fillMaxWidth().padding(horizontal = if (isWideScreen) 0.dp else 16.dp)) {
                        ChannelMembershipOverviewSection(
                            page = page,
                            loadedItems = memberStreamItems,
                            isWideScreen = isWideScreen,
                            onOpenVideo = onOpenVideo
                        )
                    }
                }
            }
            val showChannelLoading = isChannelView && (
                (isChannelSearchView && browse.channelSearchLoading && visibleItems.isEmpty()) ||
                    (!isChannelSearchView && loading && when (browse.channelTab) {
                        VideoRepository.TAB_HOME -> browse.channelHomeShelves.isEmpty()
                        VideoRepository.TAB_POSTS -> browse.channelPosts.isEmpty()
                        else -> visibleItems.isEmpty()
                    })
                )
            if (showChannelLoading) {
                items(if (isChannelShorts) 6 else 4) {
                    if (isChannelShorts) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f)
                                .clip(if (isWideScreen) RoundedCornerShape(12.dp) else androidx.compose.ui.graphics.RectangleShape)
                                .background(Surface)
                        )
                    } else if (!isWideScreen) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                Modifier
                                    .width(160.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Surface)
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Surface)
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth(0.55f)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SurfaceElevated)
                                )
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Surface)
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(0.82f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Surface)
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth(0.48f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceElevated)
                            )
                        }
                    }
                }
            }
            if (isFeed && loading && visibleFeedEntries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = TextSecondary, strokeWidth = 2.dp)
                        Text(
                            text = if (feedState.category == feedState.homeChipTitle) "Loading home feed..." else "Loading ${feedState.category}...",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                items(if (isWideScreen) 6 else 3) {
                    val thumbCorner = if (isWideScreen) 12.dp else 0.dp
                    val metaInset = if (isWideScreen) 2.dp else 12.dp
                    Column(Modifier.fillMaxWidth()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .then(if (thumbCorner > 0.dp) Modifier.clip(RoundedCornerShape(thumbCorner)) else Modifier)
                                .background(Surface)
                        )
                        Row(
                            modifier = Modifier.padding(start = metaInset, end = metaInset, top = 10.dp, bottom = if (!isWideScreen) 18.dp else 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Surface))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.fillMaxWidth(0.86f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
                                Box(Modifier.fillMaxWidth(0.54f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceElevated))
                            }
                        }
                    }
                }
            }
            if (isFeed) {
                items(
                    count = visibleFeedEntries.size,
                    key = { index -> feedEntryKeys[index] },
                    contentType = { index ->
                        when (visibleFeedEntries[index]) {
                            is HomeEntry.Video -> "video"
                            is HomeEntry.ShortsShelf -> "shorts_shelf"
                        }
                    },
                    span = { index ->
                        when (visibleFeedEntries[index]) {
                            is HomeEntry.ShortsShelf -> GridItemSpan(maxLineSpan)
                            is HomeEntry.Video -> GridItemSpan(1)
                        }
                    }
                ) { index ->
                    when (val entry = visibleFeedEntries[index]) {
                        is HomeEntry.ShortsShelf -> {
                            HomeShortsShelfSection(
                                title = entry.title,
                                items = entry.items,
                                screenWidth = screenWidth,
                                onOpen = { cardIdx -> onOpenShorts?.invoke(entry.items, cardIdx) },
                                onHideCard = { card -> hidden.hideVideo("https://www.youtube.com/shorts/${card.videoId}") },
                                onHideShelf = {
                                    entry.items.forEach { card ->
                                        hidden.hideVideo("https://www.youtube.com/shorts/${card.videoId}")
                                    }
                                },
                                onSaveCard = onSaveShort
                            )
                        }
                        is HomeEntry.Video -> {
                            val item = entry.item
                            VideoCard(
                                item = item,
                                isMembership = false,
                                showChannel = true,
                                roundedThumbnail = isWideScreen,
                                bottomPadding = if (!isWideScreen) 18.dp else 0.dp,
                                inlineOverlay = inlineOverlayFor?.invoke(item),
                                onNotInterested = if (feedState.category == feedState.homeChipTitle) {
                                    { hidden.hideVideo(item.url) }
                                } else null,
                                onHideChannel = if (feedState.category == feedState.homeChipTitle && !item.uploaderUrl.isNullOrBlank()) {
                                    { hidden.hideChannel(item.uploaderUrl) }
                                } else null,
                                onChannel = { onOpenChannel(it) }
                            ) { onOpenVideo(item) }
                        }
                    }
                }
            } else if (isChannelHome) {
                items(
                    count = browse.channelHomeShelves.size,
                    key = { index -> "channel_home_shelf_${index}_${browse.channelHomeShelves[index].title}" },
                    span = { GridItemSpan(maxLineSpan) }
                ) { index ->
                    ChannelHomeShelfSection(
                        shelf = browse.channelHomeShelves[index],
                        isWideScreen = isWideScreen,
                        videoActions = channelVideoActions ?: ChannelVideoActions(),
                        onOpenVideo = onOpenVideo,
                        onOpenShorts = { cards, cardIdx -> onOpenShorts?.invoke(cards, cardIdx) },
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenTab = onLoadChannelTab
                    )
                }
            } else if (isChannelPosts) {
                items(
                    count = browse.channelPosts.size,
                    key = { index -> "channel_post_${browse.channelPosts[index].postId}" },
                    span = { GridItemSpan(maxLineSpan) }
                ) { index ->
                    ChannelPostCard(
                        post = browse.channelPosts[index],
                        isWideScreen = isWideScreen,
                        videoActions = channelVideoActions ?: ChannelVideoActions(),
                        onOpenVideo = onOpenVideo
                    )
                }
            } else items(visibleItems.size, key = { index -> itemKeys[index] }, contentType = { index -> visibleItems[index].infoType }) { index ->
                val item = visibleItems[index]
                if (isSearchResults) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BoxWithConstraints(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
                            val wideResult = maxWidth >= 600.dp
                            when (item) {
                                is StreamInfoItem -> if (wideResult) {
                                    SearchWideVideoCard(
                                        item = item,
                                        inlineOverlay = inlineOverlayFor?.invoke(item),
                                        onChannel = { onOpenChannel(it) },
                                    ) { onOpenVideo(item) }
                                } else {
                                    VideoCard(
                                        item = item,
                                        roundedThumbnail = false,
                                        bottomPadding = 16.dp,
                                        inlineOverlay = inlineOverlayFor?.invoke(item),
                                        onChannel = { onOpenChannel(it) },
                                    ) { onOpenVideo(item) }
                                }
                                is ChannelInfoItem -> SearchChannelCard(
                                    item = item,
                                    subscribedChannels = subscribedChannels,
                                    onSubscribe = { channelId, sub -> onSubscribeChannel(channelId, sub, null) },
                                    onNotificationPreference = { channelId, pref -> onNotificationPreference(channelId, pref) },
                                ) { onOpenChannel(item.url) }
                                is PlaylistInfoItem -> if (wideResult) {
                                    SearchWidePlaylistCard(item) { onOpenPlaylist(item.url) }
                                } else {
                                    PlaylistCard(item, horizontalPadding = 12.dp) { onOpenPlaylist(item.url) }
                                }
                            }
                        }
                    }
                } else when (item) {
                    is StreamInfoItem -> {
                        if (isChannelShorts) {
                            ChannelShortCard(
                                item = item,
                                sharpEdges = !isWideScreen,
                                videoActions = channelVideoActions,
                                onClick = { onOpenVideo(item) }
                            )
                        } else {
                            val isMembershipItem = isChannelView && (
                                browse.channelTab == VideoRepository.TAB_MEMBERSHIP ||
                                    page?.isMemberVideo(item) == true
                            )
                            if (isChannelView && !isWideScreen) {
                                ChannelCompactVideoRow(
                                    item = item,
                                    isMembership = isMembershipItem,
                                    horizontalPadding = 16.dp,
                                    videoActions = channelVideoActions,
                                    onClick = { onOpenVideo(item) }
                                )
                            } else {
                                VideoCard(
                                    item = item,
                                    isMembership = isMembershipItem,
                                    showChannel = !isChannelView || browse.channelTab == ChannelTabs.CHANNELS,
                                    roundedThumbnail = isWideScreen || isChannelView || currentPlaylist != null,
                                    bottomPadding = if (isHomeFeed && !isWideScreen) 18.dp else 0.dp,
                                    inlineOverlay = inlineOverlayFor?.invoke(item),
                                    onNotInterested = if (isFeed && feedState.category == feedState.homeChipTitle) {
                                        { hidden.hideVideo(item.url) }
                                    } else null,
                                    onHideChannel = if (isFeed && feedState.category == feedState.homeChipTitle && !item.uploaderUrl.isNullOrBlank()) {
                                        { hidden.hideChannel(item.uploaderUrl) }
                                    } else null,
                                    onChannel = { onOpenChannel(it) }
                                ) { onOpenVideo(item) }
                            }
                        }
                    }
                    is ChannelInfoItem -> ChannelCard(item) { onOpenChannel(item.url) }
                    is PlaylistInfoItem -> if (isChannelView && !isWideScreen) {
                        ChannelCompactPlaylistRow(
                            item = item,
                            horizontalPadding = 16.dp,
                            playlistActions = channelPlaylistActions,
                            onClick = { onOpenPlaylist(item.url) }
                        )
                    } else {
                        PlaylistCard(item) { onOpenPlaylist(item.url) }
                    }
                }
            }
            val channelErrorEmpty = when {
                isChannelHome -> browse.channelHomeShelves.isEmpty()
                isChannelPosts -> browse.channelPosts.isEmpty()
                else -> visibleItems.isEmpty()
            }
            if (isChannelView && error != null && channelErrorEmpty) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isWideScreen) 0.dp else 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Surface)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(error, color = ErrorText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        OutlinedButton(
                            onClick = { onLoadChannelTab(browse.channelTab) },
                            modifier = Modifier.heightIn(min = 44.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Text("Try again")
                        }
                    }
                }
            }
            if (browse.loading && (page != null || currentPlaylist != null)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = TextPrimary)
                    }
                }
            }
            if (browse.error != null && (page != null || currentPlaylist != null)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isChannelView && !isWideScreen) 16.dp else 0.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(browse.error ?: "", color = ErrorText, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onLoadMoreBrowse() }, modifier = Modifier.heightIn(min = 44.dp)) {
                            Text("Try again", color = CallToActionBlue)
                        }
                    }
                }
            }
            if (isSearchResults && searchState.moreLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp), color = CallToActionBlue, strokeWidth = 3.dp) } }
            if (isSearchResults && searchState.moreError != null) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(searchState.moreError ?: "", color = ErrorText, modifier = Modifier.weight(1f)); TextButton(onClick = { onMoreSearchResults() }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Try again", color = CallToActionBlue) } } }
            if (isFeed && feedState.feedLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = TextPrimary) } }
            if (isFeed && feedState.feedError != null) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(feedState.feedError ?: "", Modifier.weight(1f), color = ErrorText); TextButton(onClick = { onLoadMoreFeed() }, modifier = Modifier.heightIn(min = 44.dp)) { Text("Try again", color = CallToActionBlue) } } }
            if (isFeed && error != null && visibleFeedEntries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isWideScreen) 0.dp else 16.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceElevated)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(error, color = ErrorText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        OutlinedButton(
                            onClick = { if (feedState.category == feedState.homeChipTitle) onLoadHome(true) else onSelectCategory(feedState.category, true) },
                            modifier = Modifier.heightIn(min = 44.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Text("Try again")
                        }
                    }
                }
            }
            val hasEmptyResult = when {
                isFeed -> visibleFeedEntries.isEmpty()
                isChannelSearchView -> !browse.channelSearchLoading && visibleItems.isEmpty()
                isChannelHome -> browse.channelHomeShelves.isEmpty()
                isChannelPosts -> browse.channelPosts.isEmpty()
                else -> visibleItems.isEmpty()
            }
            if (!loading && error == null && !browse.loading && hasEmptyResult) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val (emptyIcon, emptyTitle, emptySubtitle) = when {
                        isChannelSearchView -> Triple(Icons.Outlined.SearchOff, "No channel results", "No videos or playlists on this channel matched \"${browse.channelSearchSubmitted}\".")
                        isChannelHome -> Triple(Icons.Outlined.VideoLibrary, "No featured sections", "Switch to Videos to browse all uploads.")
                        isChannelPosts -> Triple(Icons.Outlined.Forum, "No community posts", "This channel hasn't shared community posts yet.")
                        isChannelView && browse.channelAccessFilter != ChannelAccessFilter.ALL -> Triple(Icons.Outlined.VideoLibrary, "No ${browse.channelAccessFilter.label.lowercase()} items", "No loaded items on this tab match the active filter.")
                        isChannelView && browse.channelTab == ChannelTabs.SHORTS -> Triple(Icons.Outlined.VideoLibrary, "No Shorts yet", "This channel hasn't uploaded any Shorts.")
                        isChannelView && browse.channelTab == ChannelTabs.LIVESTREAMS -> Triple(Icons.Default.LiveTv, "No livestreams", "Past and active livestreams will appear here.")
                        isChannelView && browse.channelTab == ChannelTabs.PLAYLISTS -> Triple(Icons.AutoMirrored.Outlined.PlaylistPlay, "No playlists", "This channel hasn't created any public playlists.")
                        isChannelView && browse.channelTab == ChannelTabs.CHANNELS -> Triple(Icons.Outlined.Groups, "No featured channels", "This channel hasn't featured other channels.")
                        isChannelView && browse.channelTab == VideoRepository.TAB_MEMBERSHIP -> Triple(Icons.Default.Star, "No members-only videos", "Member perks and exclusives will appear here.")
                        isChannelView -> Triple(Icons.Outlined.VideoLibrary, "No videos available yet", "Uploads for this tab will show up once published.")
                        isFeed -> Triple(
                            Icons.Outlined.VideoLibrary,
                            if (feedState.category == feedState.homeChipTitle) "No videos in your feed" else "No videos in ${feedState.category}",
                            "Pull down to refresh or load recommendations again."
                        )
                        else -> Triple(Icons.Outlined.SearchOff, "No results", "Try adjusting your search or filter.")
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if ((isFeed || isChannelView) && !isWideScreen) 16.dp else 0.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceElevated)
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(emptyIcon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(32.dp))
                        Text(emptyTitle, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(emptySubtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        if (isFeed) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { if (feedState.category == feedState.homeChipTitle) onLoadHome(true) else onSelectCategory(feedState.category, true) },
                                modifier = Modifier.heightIn(min = 44.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Text("Refresh feed")
                            }
                        } else if (isChannelHome) {
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { onLoadChannelTab(ChannelTabs.VIDEOS) },
                                modifier = Modifier.heightIn(min = 44.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Text("Browse Videos")
                            }
                        }
                    }
                }
            }
        }
        if (showCenteredSearchLoader) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = CallToActionBlue,
                    strokeWidth = 3.5.dp
                )
            }
        }
        if (isFeed) PullToRefreshDefaults.Indicator(state = refreshState, isRefreshing = refreshingFeed, modifier = Modifier.align(Alignment.TopCenter))
    }
}
