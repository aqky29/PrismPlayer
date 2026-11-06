package id.flufclip.player

import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

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

    fun mergeHomeChips(updated: List<HomeChip>) {
        if (updated.isEmpty()) return
        val merged = LinkedHashMap<String, HomeChip>()
        homeChips.forEach { merged[it.title] = it }
        updated.forEach { merged[it.title] = it }
        homeChips = merged.values.toList()
    }

    fun loadHome(
        refresh: Boolean = false,
        scope: CoroutineScope,
        topics: List<String>,
        homeCachePrefs: SharedPreferences,
        relatedCachePrefs: SharedPreferences,
        watchHistory: List<WatchedVideo>,
        searchHistory: SearchHistoryStore,
        hidden: HiddenFeedItems,
        loading: Boolean,
        getHome: () -> List<StreamInfoItem>,
        onHomeChange: (List<StreamInfoItem>) -> Unit,
        onLoadingChange: (Boolean) -> Unit,
        onErrorChange: (String?) -> Unit
    ) {
        if (loading || feedLoading) return
        val revision = YouTubeAuth.sessionRevision
        onLoadingChange(true); onErrorChange(null); feedError = null; feedEnd = false
        homeLoadJob = scope.launch {
            // Cek siaran live channel yang sering views berjalan paralel; hasilnya disisipkan setelah feed tampil.
            val liveJob = async {
                runCatching {
                    VideoRepository.liveFromChannels(HomeRecommendations.watchedChannels(watchHistory, getHome() + HomeFeedCache.read(relatedCachePrefs)))
                }.getOrDefault(emptyList())
            }
            try {
                var items = HomeFeedCache.read(relatedCachePrefs)
                var nextHomePage: SearchPage? = null
                var nextContinuation: String? = null

                // JALUR 1: Jika login, coba ambil feed Beranda personal resmi dari YouTube (FEwhat_to_watch)
                if (YouTubeAuth.isLoggedIn) {
                    try {
                        val accountHome = HomeFeed.start(revision)
                        if (revision != YouTubeAuth.sessionRevision) return@launch
                        if (accountHome.items.isNotEmpty()) {
                            items = accountHome.items
                            nextContinuation = accountHome.continuationToken
                        }
                        if (accountHome.chips.isNotEmpty()) {
                            homeChips = accountHome.chips
                            val homeChip = accountHome.chips.firstOrNull { it.title.equals("All", true) || it.title.equals("Semua", true) }
                                ?: accountHome.chips.firstOrNull { !it.hasEndpoint }
                                ?: accountHome.chips.firstOrNull()
                            selectedChip = homeChip
                            if (homeChip != null) { homeChipTitle = homeChip.title; category = homeChip.title }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Fallback ke Kiosk / search jika request akun gagal */ }
                } else {
                    homeChips = emptyList()
                    selectedChip = null
                    homeChipTitle = "All"
                    category = "All"
                }

                // JALUR 2: Fallback ke Kiosk NewPipe atau Search jika akun belum login atau feed akun kosong (nudge)
                if (revision != YouTubeAuth.sessionRevision) return@launch
                if (items.isEmpty()) {
                    if (!refresh) {
                        try {
                            items = (items + VideoRepository.home()).distinctBy { it.url }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { /* Try the search-based feed below. */ }
                    }
                    if (items.isEmpty() || refresh) {
                        val page = VideoRepository.searchPage(HomeRecommendations.query(watchHistory, topics, topicIndex, searchHistory.terms))
                        topicIndex++
                        items = (items + page.items.filterIsInstance<StreamInfoItem>()).distinctBy { it.url }
                        nextHomePage = page
                    } else if (watchHistory.any { it.uploader.isNotBlank() }) {
                        try {
                            val page = VideoRepository.searchPage(HomeRecommendations.query(watchHistory, topics, topicIndex++, searchHistory.terms))
                            items = (page.items.filterIsInstance<StreamInfoItem>() + items).distinctBy { it.url }
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { /* Keep the kiosk feed if personalization is unavailable. */ }
                    }
                }

                if (revision != YouTubeAuth.sessionRevision) return@launch
                if (items.isEmpty()) {
                    onErrorChange(if (getHome().isEmpty()) "Feed is unavailable." else "Cannot refresh feed. Showing the latest videos.")
                } else {
                    val updatedHome = if (nextContinuation != null || (YouTubeAuth.isLoggedIn && nextHomePage == null)) {
                        items.filter { it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels }
                    } else {
                        HomeRecommendations.rank(items, watchHistory, emptySet(), emptySet())
                    }
                    onHomeChange(updatedHome)
                    homePage = nextHomePage
                    homeContinuationToken = nextContinuation
                    HomeFeedCache.save(homeCachePrefs, updatedHome)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == YouTubeAuth.sessionRevision) onErrorChange(if (getHome().isEmpty()) "Failed to load feed: ${e.message ?: "try again"}" else "Cannot refresh feed. Showing the latest videos.") }
            finally { if (revision == YouTubeAuth.sessionRevision) onLoadingChange(false) }
            val live = liveJob.await()
            if (revision == YouTubeAuth.sessionRevision && live.isNotEmpty()) onHomeChange(HomeRecommendations.withLive(getHome(), live, hidden.videos, hidden.channels))
        }
    }

    fun startFallbackCategory(
        value: String,
        force: Boolean,
        scope: CoroutineScope,
        relatedCachePrefs: SharedPreferences,
        watchHistory: List<WatchedVideo>,
        hidden: HiddenFeedItems,
        getHome: () -> List<StreamInfoItem>,
        onLoadingChange: (Boolean) -> Unit,
        onErrorChange: (String?) -> Unit
    ) {
        onLoadingChange(true)
        if (value == "Live") {
            // Chip "Live" menampilkan siaran yang benar-benar sedang berlangsung, bukan hasil pencarian kata "Live":
            // lebih dulu dari channel yang pernah views, lalu feed live umum.
            scope.launch {
                try {
                    val mine = async {
                        runCatching {
                            VideoRepository.liveFromChannels(HomeRecommendations.watchedChannels(watchHistory, getHome() + HomeFeedCache.read(relatedCachePrefs)))
                        }.getOrDefault(emptyList())
                    }
                    val kiosk = VideoRepository.liveKiosk()
                    val items = (mine.await() + kiosk.items.filterIsInstance<StreamInfoItem>())
                        .filter { it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels }.distinctBy { it.url }
                    if (items.isNotEmpty() || !force) {
                        categoryPage = null
                        liveNext = kiosk.next
                        categoryItems = items
                        feedEnd = kiosk.next == null
                    } else onErrorChange("Cannot refresh category. Showing the latest videos.")
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { onErrorChange("Failed to load category: ${e.message ?: "try again"}") }
                finally { onLoadingChange(false) }
            }
            return
        }
        scope.launch {
            try {
                val page = VideoRepository.searchPage(value)
                val items = page.items.filterIsInstance<StreamInfoItem>().distinctBy { it.url }
                if (items.isNotEmpty() || !force) {
                    categoryPage = page
                    categoryItems = items
                    feedEnd = page.next == null
                } else onErrorChange("Cannot refresh category. Showing the latest videos.")
            } catch (e: Exception) { onErrorChange("Failed to load category: ${e.message ?: "try again"}") }
            finally { onLoadingChange(false) }
        }
    }

    fun selectChip(
        chip: HomeChip,
        force: Boolean = false,
        scope: CoroutineScope,
        relatedCachePrefs: SharedPreferences,
        watchHistory: List<WatchedVideo>,
        hidden: HiddenFeedItems,
        loading: Boolean,
        getHome: () -> List<StreamInfoItem>,
        onLoadingChange: (Boolean) -> Unit,
        onErrorChange: (String?) -> Unit
    ) {
        if (loading || feedLoading || (chip.title == category && !force)) return
        category = chip.title; selectedChip = chip; onErrorChange(null); feedError = null; feedEnd = false
        if (chip.title == homeChipTitle) {
            categoryItems = emptyList(); categoryContinuation = null; categoryPage = null; liveNext = null
            return
        }
        if (!force) { categoryItems = emptyList(); categoryPage = null; categoryContinuation = null; liveNext = null }
        if (!chip.hasEndpoint) {
            startFallbackCategory(chip.title, force, scope, relatedCachePrefs, watchHistory, hidden, getHome, onLoadingChange, onErrorChange)
            return
        }
        val revision = YouTubeAuth.sessionRevision
        onLoadingChange(true)
        scope.launch {
            try {
                val page = HomeFeed.chip(chip, revision)
                if (revision != YouTubeAuth.sessionRevision || selectedChip != chip || category != chip.title) return@launch
                val items = page.items.filter { it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels }.distinctBy { it.url }
                if (items.isNotEmpty() || !force) {
                    categoryItems = items
                    categoryContinuation = page.continuationToken
                    feedEnd = page.continuationToken == null
                    mergeHomeChips(page.chips)
                } else onErrorChange("Cannot refresh category. Showing the latest videos.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == YouTubeAuth.sessionRevision && selectedChip == chip) onErrorChange("Failed to load category: ${e.message ?: "try again"}") }
            finally { if (revision == YouTubeAuth.sessionRevision) onLoadingChange(false) }
        }
    }

    fun selectFallbackCategory(
        value: String,
        force: Boolean = false,
        scope: CoroutineScope,
        relatedCachePrefs: SharedPreferences,
        watchHistory: List<WatchedVideo>,
        hidden: HiddenFeedItems,
        loading: Boolean,
        getHome: () -> List<StreamInfoItem>,
        onLoadingChange: (Boolean) -> Unit,
        onErrorChange: (String?) -> Unit
    ) {
        if (loading || feedLoading || (value == category && !force)) return
        category = value; selectedChip = null; onErrorChange(null); feedError = null; feedEnd = false
        if (value == homeChipTitle) {
            categoryItems = emptyList(); categoryContinuation = null; categoryPage = null; liveNext = null
            return
        }
        if (!force) { categoryItems = emptyList(); categoryPage = null; categoryContinuation = null; liveNext = null }
        startFallbackCategory(value, force, scope, relatedCachePrefs, watchHistory, hidden, getHome, onLoadingChange, onErrorChange)
    }

    fun selectCategory(
        value: String,
        force: Boolean = false,
        scope: CoroutineScope,
        relatedCachePrefs: SharedPreferences,
        watchHistory: List<WatchedVideo>,
        hidden: HiddenFeedItems,
        loading: Boolean,
        getHome: () -> List<StreamInfoItem>,
        onLoadingChange: (Boolean) -> Unit,
        onErrorChange: (String?) -> Unit
    ) {
        val chip = homeChips.firstOrNull { it.title == value }
        if (chip != null) selectChip(chip, force, scope, relatedCachePrefs, watchHistory, hidden, loading, getHome, onLoadingChange, onErrorChange)
        else selectFallbackCategory(value, force, scope, relatedCachePrefs, watchHistory, hidden, loading, getHome, onLoadingChange, onErrorChange)
    }

    fun loadMoreFeed(
        scope: CoroutineScope,
        topics: List<String>,
        watchHistory: List<WatchedVideo>,
        searchHistory: SearchHistoryStore,
        hidden: HiddenFeedItems,
        loading: Boolean,
        searchState: SearchState,
        browse: BrowseState,
        selected: StreamInfo?,
        minimized: Boolean,
        getHome: () -> List<StreamInfoItem>,
        onHomeChange: (List<StreamInfoItem>) -> Unit
    ) {
        if (loading || feedLoading || feedEnd || searchState.mode || browse.channel != null || browse.playlist != null || searchState.active || (selected != null && !minimized)) return
        val revision = YouTubeAuth.sessionRevision
        feedLoading = true; feedError = null
        val currentCategory = category
        val currentChip = selectedChip
        val homeTitle = homeChipTitle
        scope.launch {
            try {
                if (currentCategory == homeTitle) {
                    val token = homeContinuationToken
                    if (token != null) {
                        try {
                            val nextPage = HomeFeed.more(token, revision)
                            if (revision != YouTubeAuth.sessionRevision || token != homeContinuationToken || currentCategory != category) return@launch
                            homeContinuationToken = nextPage.continuationToken
                            val currentHome = getHome()
                            val existing = currentHome.mapTo(HashSet()) { it.url }
                            val newItems = nextPage.items.filter { it.url !in existing && it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels }
                            feedEnd = newItems.isEmpty() && nextPage.continuationToken == null
                            onHomeChange(currentHome + newItems)
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            if (revision == YouTubeAuth.sessionRevision && token == homeContinuationToken) homeContinuationToken = null
                        }
                    } else {
                        val current = homePage
                        val page = if (current?.next != null) VideoRepository.moreSearch(current) else {
                            val next = VideoRepository.searchPage(HomeRecommendations.query(watchHistory, topics, topicIndex, searchHistory.terms)); topicIndex++; next
                        }
                        if (revision != YouTubeAuth.sessionRevision || currentCategory != category) return@launch
                        homePage = page
                        val existing = getHome().mapTo(HashSet()) { it.url }
                        var newItems = page.items.filterIsInstance<StreamInfoItem>().filter { it.url !in existing }
                        if (newItems.isEmpty()) {
                            val fallback = VideoRepository.searchPage(HomeRecommendations.query(watchHistory, topics, topicIndex++, searchHistory.terms))
                            if (revision != YouTubeAuth.sessionRevision || currentCategory != category) return@launch
                            homePage = fallback
                            newItems = fallback.items.filterIsInstance<StreamInfoItem>().filter { it.url !in existing }
                        }
                        val updated = getHome() + HomeRecommendations.rank(newItems, watchHistory, emptySet(), emptySet())
                        feedEnd = newItems.isEmpty()
                        onHomeChange(updated)
                    }
                } else if (currentChip?.hasEndpoint == true) {
                    val token = categoryContinuation
                    if (token == null) { feedEnd = true } else {
                        val page = HomeFeed.more(token, revision)
                        if (revision != YouTubeAuth.sessionRevision || currentChip != selectedChip || currentCategory != category || token != categoryContinuation) return@launch
                        categoryContinuation = page.continuationToken
                        val updated = (categoryItems + page.items.filter {
                            it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels
                        }).distinctBy { it.url }
                        feedEnd = page.continuationToken == null || updated.size == categoryItems.size
                        categoryItems = updated
                        mergeHomeChips(page.chips)
                    }
                } else if (currentCategory == "Live") {
                    val next = liveNext
                    if (next == null) { feedEnd = true } else {
                        val page = VideoRepository.moreLiveKiosk(next)
                        if (revision != YouTubeAuth.sessionRevision || currentCategory != category) return@launch
                        liveNext = page.next
                        val updated = (categoryItems + page.items.filterIsInstance<StreamInfoItem>()
                            .filter { it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels }).distinctBy { it.url }
                        feedEnd = page.next == null || updated.size == categoryItems.size
                        categoryItems = updated
                    }
                } else {
                    val current = categoryPage
                    if (current?.next == null) { feedEnd = true } else {
                        val page = VideoRepository.moreSearch(current)
                        if (revision != YouTubeAuth.sessionRevision || currentCategory != category) return@launch
                        categoryPage = page
                        val updated = (categoryItems + page.items.filterIsInstance<StreamInfoItem>()).distinctBy { it.url }
                        feedEnd = page.next == null || updated.size == categoryItems.size
                        categoryItems = updated
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == YouTubeAuth.sessionRevision && currentCategory == category) feedError = "Cannot load next video: ${e.message ?: "try again"}" }
            finally { if (revision == YouTubeAuth.sessionRevision) feedLoading = false }
        }
    }
}

