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

internal enum class ChannelSortOrder(val label: String) {
    LATEST("Latest"),
    POPULAR("Popular"),
    OLDEST("Oldest")
}

internal enum class ChannelAccessFilter(val label: String) {
    ALL("All"),
    MEMBERS_ONLY("Members only"),
    PUBLIC("Public")
}

internal enum class PlaylistSortOrder(val label: String) {
    RECENT("Recently added"),
    NAME("A-Z")
}

/**
 * State untuk halaman penjelajahan channel dan playlist.
 */
@Stable
internal class BrowseState {
    var channel by mutableStateOf<ChannelPage?>(null)
    var channelTab by mutableStateOf(ChannelTabs.VIDEOS)
    var channelSort by mutableStateOf(ChannelSortOrder.LATEST)
    var channelAccessFilter by mutableStateOf(ChannelAccessFilter.ALL)
    var playlistSort by mutableStateOf(PlaylistSortOrder.RECENT)
    var channelItems by mutableStateOf<List<InfoItem>>(emptyList())
    var channelHomeShelves by mutableStateOf<List<ChannelHomeShelf>>(emptyList())
    var channelPosts by mutableStateOf<List<ChannelPost>>(emptyList())
    var channelPostsNextToken by mutableStateOf<String?>(null)
    var channelSearchActive by mutableStateOf(false)
    var channelSearchQuery by mutableStateOf("")
    var channelSearchSubmitted by mutableStateOf("")
    var channelSearchResults by mutableStateOf<List<InfoItem>>(emptyList())
    var channelSearchLoading by mutableStateOf(false)
    var playlist by mutableStateOf<PlaylistInfo?>(null)
    var playlistItems by mutableStateOf<List<InfoItem>>(emptyList())
    var next by mutableStateOf<Page?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun openChannelPage(page: ChannelPage) {
        playlist = null
        playlistItems = emptyList()
        channel = page
        val shelves = page.uiData?.homeShelves.orEmpty()
        channelHomeShelves = shelves
        channelPosts = emptyList()
        channelPostsNextToken = null
        channelSearchActive = false
        channelSearchQuery = ""
        channelSearchSubmitted = ""
        channelSearchResults = emptyList()
        channelSearchLoading = false
        channelSort = ChannelSortOrder.LATEST
        channelAccessFilter = ChannelAccessFilter.ALL
        playlistSort = PlaylistSortOrder.RECENT
        channelTab = if (shelves.isNotEmpty()) VideoRepository.TAB_HOME else ChannelTabs.VIDEOS
        channelItems = page.videos
        next = null
        error = null
    }

    fun submitChannelSearch(query: String, scope: CoroutineScope) {
        val page = channel ?: return
        val clean = query.trim()
        channelSearchQuery = query
        if (clean.isBlank()) {
            channelSearchSubmitted = ""
            channelSearchResults = emptyList()
            channelSearchLoading = false
            return
        }
        channelSearchSubmitted = clean
        channelSearchLoading = true
        error = null
        scope.launch {
            try {
                val results = VideoRepository.searchChannel(page, clean)
                if (channel == page && channelSearchSubmitted == clean) {
                    channelSearchResults = results
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (channel == page) error = "Failed to search channel: ${e.message ?: "try again"}"
            } finally {
                if (channel == page) channelSearchLoading = false
            }
        }
    }

    fun closeChannelSearch() {
        channelSearchActive = false
        channelSearchQuery = ""
        channelSearchSubmitted = ""
        channelSearchResults = emptyList()
        channelSearchLoading = false
    }

    fun loadChannelTab(
        filter: String,
        scope: CoroutineScope,
        pageLoading: Boolean,
        onPageLoading: (Boolean) -> Unit,
        onPageError: (String?) -> Unit
    ) {
        val page = channel ?: return
        if (pageLoading) return
        channelTab = filter
        channelAccessFilter = ChannelAccessFilter.ALL
        onPageError(null)
        if (filter == VideoRepository.TAB_HOME) {
            next = null
            if (channelHomeShelves.isNotEmpty()) return
            val homeParams = page.uiData?.homeTabParams ?: return
            onPageLoading(true)
            scope.launch {
                try {
                    val shelves = HomeFeed.fetchChannelHomeShelves(page.info.id, homeParams)
                    if (channel == page && channelTab == filter) {
                        channelHomeShelves = shelves
                        error = null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onPageError("Failed to load Home: ${e.message ?: "try again"}")
                } finally {
                    onPageLoading(false)
                }
            }
            return
        }
        if (filter == VideoRepository.TAB_POSTS) {
            channelPosts = emptyList()
            channelPostsNextToken = null
            next = null
            onPageLoading(true)
            scope.launch {
                try {
                    val result = VideoRepository.channelPostsPage(page, null)
                    if (channel == page && channelTab == filter) {
                        channelPosts = result.posts
                        channelPostsNextToken = result.nextToken
                        error = null
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onPageError("Failed to load posts: ${e.message ?: "try again"}")
                } finally {
                    onPageLoading(false)
                }
            }
            return
        }
        channelItems = emptyList()
        next = null
        onPageLoading(true)
        scope.launch {
            try {
                val result = VideoRepository.channelTabPage(page, filter)
                if (channel == page && channelTab == filter) {
                    channelItems = result.items
                    next = result.next
                    error = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onPageError("Failed to load tab: ${e.message ?: "try again"}")
            } finally {
                onPageLoading(false)
            }
        }
    }

    fun loadMoreBrowse(scope: CoroutineScope) {
        val currentPlaylist = playlist
        val currentChannel = channel
        val currentTab = channelTab
        if (loading) return
        if (currentPlaylist == null && currentChannel != null && currentTab == VideoRepository.TAB_POSTS) {
            val postToken = channelPostsNextToken ?: return
            loading = true
            error = null
            scope.launch {
                try {
                    val result = VideoRepository.channelPostsPage(currentChannel, postToken)
                    if (channel != currentChannel || channelTab != currentTab) return@launch
                    val old = channelPosts
                    val merged = (old + result.posts).distinctBy { it.postId }
                    channelPosts = merged
                    channelPostsNextToken = if (merged.size == old.size) null else result.nextToken
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = "Failed to load more posts: ${e.message ?: "try again"}"
                } finally {
                    loading = false
                }
            }
            return
        }
        val next = next ?: return
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

