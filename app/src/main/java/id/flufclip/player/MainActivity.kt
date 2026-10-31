package id.flufclip.player

import android.app.DownloadManager
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.distinctUntilChanged
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType

internal val Background = Color(0xFF0F0F0F)
internal val Surface = Color(0xFF272727)
private val Accent = Color(0xFFFF0000)

private fun wideWatchPlayerWidth(width: Dp): Dp {
    val available = (width - 24.dp).coerceAtLeast(0.dp)
    return available - minOf(available / 3f, 400.dp)
}

internal fun videoKey(url: String): String {
    val uri = Uri.parse(url)
    return uri.getQueryParameter("v") ?: if (uri.host == "youtu.be" || uri.pathSegments.firstOrNull() == "shorts") uri.pathSegments.lastOrNull().orEmpty() else url
}

class MainActivity : ComponentActivity() {
    var inPip by mutableStateOf(false)
    var pipEligible = false
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipEligible && android.os.Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            runCatching { enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(16, 9)).build()) }
        }
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        YouTubeAuth.init(applicationContext)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Background, surface = Surface)) { App() } }
    }
}

/** Status bar height from framework resources, so it stays the same while the bar is hidden in fullscreen. */
private fun statusBarPixels(context: Context): Int {
    val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val preferences = remember { context.getSharedPreferences("search_history", Context.MODE_PRIVATE) }
    remember { PlayerSettings.load(context) }
    var history by remember { mutableStateOf(runCatching {
        val json = org.json.JSONArray(preferences.getString("recent", "[]"))
        (0 until json.length()).map { json.getString(it) }
    }.getOrDefault(emptyList())) }
    var searchActive by remember { mutableStateOf(false) }
    var lastSearch by remember { mutableStateOf("") }
    var searchFilter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<InfoItem>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searchPage by remember { mutableStateOf<SearchPage?>(null) }
    var searchMoreLoading by remember { mutableStateOf(false) }
    var searchMoreError by remember { mutableStateOf<String?>(null) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    val homeCachePrefs = remember { context.getSharedPreferences("home_feed_cache", Context.MODE_PRIVATE) }
    val relatedCachePrefs = remember { context.getSharedPreferences("home_related_cache", Context.MODE_PRIVATE) }
    var home by remember { mutableStateOf(HomeFeedCache.read(homeCachePrefs)) }
    var searchMode by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        YouTubeLoginDialog(onDismiss = { showLoginDialog = false })
    }
    if (showAccountDialog) {
        YouTubeAccountDialog(
            onDismiss = { showAccountDialog = false },
            onLogout = {
                YouTubeAuth.logout(context)
                showAccountDialog = false
            }
        )
    }
    LaunchedEffect(searchActive, query) {
        suggestions = emptyList()
        val term = query.trim()
        if (searchActive && term.isNotBlank()) {
            delay(350)
            try { suggestions = VideoRepository.suggestions(term).distinct().take(10) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { suggestions = emptyList() }
        }
    }
    var channel by remember { mutableStateOf<ChannelPage?>(null) }
    var channelTab by remember { mutableStateOf(ChannelTabs.VIDEOS) }
    var channelItems by remember { mutableStateOf<List<InfoItem>>(emptyList()) }
    var playlist by remember { mutableStateOf<PlaylistInfo?>(null) }
    var selected by remember { mutableStateOf<StreamInfo?>(null) }
    var browseNext by remember { mutableStateOf<Page?>(null) }
    var playlistItems by remember { mutableStateOf<List<InfoItem>>(emptyList()) }
    var browseLoading by remember { mutableStateOf(false) }
    var browseError by remember { mutableStateOf<String?>(null) }
    var selectedSubtitle by remember { mutableStateOf<SubtitlesStream?>(null) }
    var lastSubtitle by remember { mutableStateOf<SubtitlesStream?>(null) }
    val watchPrefs = remember { context.getSharedPreferences("watch_history", Context.MODE_PRIVATE) }
    var watchHistory by remember { mutableStateOf(WatchHistory.read(watchPrefs)) }
    val recommendationPrefs = remember { context.getSharedPreferences("home_recommendations", Context.MODE_PRIVATE) }
    var hiddenVideos by remember { mutableStateOf(HomeRecommendations.read(recommendationPrefs, "videos")) }
    var hiddenChannels by remember { mutableStateOf(HomeRecommendations.read(recommendationPrefs, "channels")) }
    var showHistory by remember { mutableStateOf(false) }
    var mainTab by remember { mutableStateOf("home") }
    var shortsRequest by remember { mutableIntStateOf(0) }
    var shortsLoading by remember { mutableStateOf(false) }
    var shortsError by remember { mutableStateOf<String?>(null) }
    var shortsPage by remember { mutableStateOf<SearchPage?>(null) }
    var shortsMoreLoading by remember { mutableStateOf(false) }
    var shortsMoreError by remember { mutableStateOf<String?>(null) }
    var shortsMoreRequest by remember { mutableIntStateOf(0) }
    var shortsTopicIndex by remember { mutableIntStateOf(0) }
    var shortsIndex by remember { mutableIntStateOf(0) }
    var shortsRetry by remember { mutableIntStateOf(0) }
    var shortsGeneration by remember { mutableIntStateOf(0) }
    var shortsTabQueue by remember { mutableStateOf(false) }
    val shortsResolver = remember { ShortsResolver(scope, context) }
    var shortsSeqNext by remember { mutableStateOf<String?>(null) }
    var shortsFromFeed by remember { mutableStateOf(false) }
    var autoplay by remember { mutableStateOf(watchPrefs.getBoolean("autoplay", true)) }
    var maxVideoHeight by remember { mutableIntStateOf(watchPrefs.getInt("max_video_height", 720)) }
    var queue by remember { mutableStateOf<List<StreamInfoItem>>(emptyList()) }
    var shortsMode by remember { mutableStateOf(false) }
    var minimized by remember { mutableStateOf(false) }
    val miniState = remember { MiniPlayerState(scope) }
    var fullscreen by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var category by remember { mutableStateOf("Semua") }
    var categoryItems by remember { mutableStateOf<List<InfoItem>>(emptyList()) }
    var homePage by remember { mutableStateOf<SearchPage?>(null) }
    var categoryPage by remember { mutableStateOf<SearchPage?>(null) }
    var feedLoading by remember { mutableStateOf(false) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var feedEnd by remember { mutableStateOf(false) }
    var topicIndex by remember { mutableIntStateOf(0) }
    val topics = remember { listOf("video terbaru", "musik terbaru", "game terbaru", "sains teknologi terbaru", "film terbaru", "olahraga terbaru") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDownloads by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<DownloadChoice?>(null) }
    val downloadState by DownloadStatus.state.collectAsState()
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val choice = pendingDownload
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && choice != null) {
            try {
                val flags = result.data?.flags ?: 0
                if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val intent = Intent(context, DownloadService::class.java).putExtra("url", choice.url).putExtra("destination", uri.toString()).putExtra("height", choice.height).putExtra("audio", choice.audio)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) { DownloadStatus.state.value = DownloadState(message = "Tidak bisa memulai unduhan: ${e.message}") }
        }
        pendingDownload = null
    }
    var showQuality by remember { mutableStateOf(false) }
    var qualityInitialPage by remember { mutableStateOf("menu") }
    var playerLocked by remember { mutableStateOf(false) }
    var qualityMode by remember { mutableStateOf("auto") }
    var quality by remember { mutableStateOf<VideoStream?>(null) }
    var selectedAudio by remember { mutableStateOf<AudioStream?>(null) }
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { (context as? MainActivity)?.pipEligible = false; player.release() } }
    SideEffect { (context as? MainActivity)?.pipEligible = selected != null && player.playWhenReady }
    if ((context as? MainActivity)?.inPip == true) {
        AndroidView(factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply { useController = false } }, update = { it.player = player }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
        return
    }
    val density = LocalDensity.current
    val statusBarHeight = remember(density) { with(density) { statusBarPixels(context).toDp() } }
    /** 0 = fullscreen, 1 = watch layout. A swipe down in fullscreen drives it, and leaving fullscreen animates it to 1. */
    val exitProgress = remember { Animatable(1f) }
    var exitDragging by remember { mutableStateOf(false) }
    fun setFullscreen(value: Boolean) {
        fullscreen = value
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (value) exitProgress.snapTo(0f) else exitProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
        (context as? Activity)?.let { activity ->
            val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            if (value) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    /** Release decision for a swipe down: a fling decides, otherwise the finger must travel 35% of the way. */
    fun swipeCommits(value: Float, velocity: Float) = when { velocity > 800f -> true; velocity < -800f -> false; else -> value >= .35f }
    fun dragFullscreenExit(fraction: Float) {
        exitDragging = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) { exitProgress.snapTo(fraction) }
    }
    fun settleFullscreenExit(leave: Boolean) {
        exitDragging = false
        if (leave) setFullscreen(false)
        else scope.launch(start = CoroutineStart.UNDISPATCHED) { exitProgress.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
    }
    fun shareVideo(info: StreamInfo) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, info.url)
        context.startActivity(Intent.createChooser(intent, "Bagikan video"))
    }
    fun copyVideoLink(info: StreamInfo) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Link video", info.url))
        android.widget.Toast.makeText(context, "Link disalin", android.widget.Toast.LENGTH_SHORT).show()
    }
    fun savePosition() {
        selected?.let { info ->
            WatchHistory.save(watchPrefs, info.url, info.name, info.uploaderName.orEmpty(), info.thumbnails.lastOrNull()?.url.orEmpty(), player.currentPosition.coerceAtLeast(0), player.duration)
            watchHistory = WatchHistory.read(watchPrefs)
        }
    }
    fun closePlayer() { savePosition(); shortsMode = false; player.stop(); player.clearMediaItems(); selected = null; minimized = false; quality = null; selectedAudio = null }
    fun back() {
        when {
            fullscreen -> setFullscreen(false)
            shortsMode && !minimized -> { closePlayer(); if (shortsTabQueue && mainTab == "shorts") mainTab = "home" }
            searchActive -> { searchActive = false; keyboard?.hide() }
            showHistory -> showHistory = false
            selected != null && !minimized -> minimized = true
            playlist != null -> playlist = null
            channel != null -> { channel = null; if (mainTab == "shorts" && shortsTabQueue && queue.isNotEmpty()) shortsMode = true }
            searchMode -> { searchMode = false; query = "" }
            mainTab != "home" -> mainTab = "home"
        }
        error = null
    }
    fun playQuality(info: StreamInfo, stream: VideoStream?, audio: AudioStream?, position: Long, play: Boolean) {
        val parameters = player.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, if (stream == null) maxVideoHeight else Int.MAX_VALUE)
        if (stream != null) parameters.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        player.trackSelectionParameters = parameters.build()
        val factory = DefaultMediaSourceFactory(context)
        fun media(url: String): MediaItem {
            val builder = MediaItem.Builder().setUri(url)
            selectedSubtitle?.takeIf { it.isUrl && it.content.isNotBlank() }?.let { sub ->
                val mime = when (sub.format?.suffix?.lowercase()) { "vtt" -> MimeTypes.TEXT_VTT; "ttml" -> MimeTypes.APPLICATION_TTML; "srt" -> MimeTypes.APPLICATION_SUBRIP; else -> null }
                if (mime != null) builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.content)).setMimeType(mime).setLanguage(sub.languageTag).setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT).build()))
            }
            return builder.build()
        }
        when {
            stream != null && stream.isVideoOnly && audio != null -> player.setMediaSource(MergingMediaSource(factory.createMediaSource(media(stream.content)), factory.createMediaSource(MediaItem.fromUri(audio.content))))
            stream != null && !stream.isVideoOnly -> player.setMediaItem(media(stream.content))
            stream == null && !info.hlsUrl.isNullOrBlank() -> player.setMediaItem(media(info.hlsUrl))
            else -> error("Format video yang bisa diputar tidak tersedia.")
        }
        quality = stream
        player.prepare()
        if (position > 0) player.seekTo(position)
        player.playWhenReady = play
    }
    BackHandler(shortsMode || showHistory || fullscreen || searchActive || (selected != null && !minimized) || playlist != null || channel != null || searchMode || mainTab != "home") { back() }
    fun loadHome(refresh: Boolean = false) {
        if (loading || feedLoading) return
        loading = true; error = null; feedError = null; feedEnd = false
        scope.launch {
            try {
                var items = HomeFeedCache.read(relatedCachePrefs)
                var nextHomePage: SearchPage? = null
                if (!refresh) {
                    try {
                        items = (items + VideoRepository.home()).distinctBy { it.url }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Try the search-based feed below. */ }
                }
                if (items.isEmpty() || refresh) {
                    val page = VideoRepository.searchPage(HomeRecommendations.query(watchHistory, topics, topicIndex, history))
                    topicIndex++
                    items = (items + page.items.filterIsInstance<StreamInfoItem>()).distinctBy { it.url }
                    nextHomePage = page
                } else if (watchHistory.any { it.uploader.isNotBlank() }) {
                    try {
                        val page = VideoRepository.searchPage(HomeRecommendations.query(watchHistory, topics, topicIndex++, history))
                        items = (page.items.filterIsInstance<StreamInfoItem>() + items).distinctBy { it.url }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Keep the kiosk feed if personalization is unavailable. */ }
                }
                if (items.isEmpty()) {
                    error = if (home.isEmpty()) "Feed belum tersedia." else "Tidak dapat memperbarui feed. Menampilkan video terakhir."
                } else {
                    home = HomeRecommendations.rank(items, watchHistory, emptySet(), emptySet())
                    homePage = nextHomePage
                    HomeFeedCache.save(homeCachePrefs, home)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (home.isEmpty()) "Feed gagal dimuat: ${e.message ?: "coba lagi"}" else "Tidak dapat memperbarui feed. Menampilkan video terakhir." }
            finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { loadHome() }
    fun selectCategory(value: String, force: Boolean = false) {
        if (loading || feedLoading || (value == category && !force)) return
        category = value; error = null; feedError = null; feedEnd = false
        if (value == "Semua") return
        if (!force) { categoryItems = emptyList(); categoryPage = null }
        loading = true
        scope.launch {
            try {
                val page = VideoRepository.searchPage(value)
                val items = page.items.filterIsInstance<StreamInfoItem>().distinctBy { it.url }
                if (items.isNotEmpty() || !force) {
                    categoryPage = page
                    categoryItems = items
                    feedEnd = page.next == null
                } else error = "Tidak dapat memperbarui kategori. Menampilkan video terakhir."
            } catch (e: Exception) { error = "Kategori gagal dimuat: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun loadMoreFeed() {
        if (loading || feedLoading || feedEnd || searchMode || channel != null || playlist != null || searchActive || (selected != null && !minimized)) return
        feedLoading = true; feedError = null
        val currentCategory = category
        scope.launch {
            try {
                if (currentCategory == "Semua") {
                    val current = homePage
                    val page = if (current?.next != null) VideoRepository.moreSearch(current) else {
                        val next = VideoRepository.searchPage(HomeRecommendations.query(watchHistory, topics, topicIndex, history)); topicIndex++; next
                    }
                    homePage = page
                    val existing = home.mapTo(HashSet()) { it.url }
                    var newItems = page.items.filterIsInstance<StreamInfoItem>().filter { it.url !in existing }
                    if (newItems.isEmpty()) {
                        val fallback = VideoRepository.searchPage(HomeRecommendations.query(watchHistory, topics, topicIndex++, history))
                        homePage = fallback
                        newItems = fallback.items.filterIsInstance<StreamInfoItem>().filter { it.url !in existing }
                    }
                    val updated = home + HomeRecommendations.rank(newItems, watchHistory, emptySet(), emptySet())
                    feedEnd = newItems.isEmpty()
                    home = updated
                } else {
                    val current = categoryPage
                    if (current?.next == null) { feedEnd = true } else {
                        val page = VideoRepository.moreSearch(current)
                        categoryPage = page
                        val updated = (categoryItems + page.items.filterIsInstance<StreamInfoItem>()).distinctBy { it.url }
                        feedEnd = page.next == null || updated.size == categoryItems.size
                        categoryItems = updated
                    }
                }
            } catch (e: Exception) { feedError = "Tidak bisa memuat video berikutnya: ${e.message ?: "coba lagi"}" }
            finally { feedLoading = false }
        }
    }
    fun openChannel(url: String) {
        if (loading || url.isBlank()) return
        loading = true; error = null
        scope.launch {
            try {
                val page = VideoRepository.channel(url)
                val liveTab = page.info.tabs.firstOrNull { ChannelTabs.LIVESTREAMS in it.contentFilters }
                val liveDeferred = if (liveTab != null) async {
                    runCatching {
                        VideoRepository.channelTabPage(page, ChannelTabs.LIVESTREAMS).items.filterIsInstance<StreamInfoItem>().firstOrNull { it.isOngoingLive() }
                    }.getOrNull()
                } else null
                val firstDeferred = async { VideoRepository.channelTabPage(page, ChannelTabs.VIDEOS) }

                val ongoingLive = liveDeferred?.await()
                val first = firstDeferred.await()
                val items = if (ongoingLive != null && first.items.none { (it as? StreamInfoItem)?.url == ongoingLive.url }) {
                    listOf(ongoingLive) + first.items
                } else {
                    first.items
                }
                browseNext = first.next; browseError = null; showHistory = false
                savePosition(); player.stop(); shortsMode = false; selected = null; quality = null; selectedAudio = null; channel = page; channelTab = ChannelTabs.VIDEOS; channelItems = items; playlist = null
            } catch (e: Exception) { error = "Channel gagal dibuka: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun search(text: String = query, filter: String = "all") {
        val term = text.trim()
        if (term.isBlank() || loading) return
        mainTab = "home"; query = term; lastSearch = term; searchFilter = filter; searchActive = false; keyboard?.hide()
        history = (listOf(term) + history.filterNot { it.equals(term, ignoreCase = true) }).take(20)
        preferences.edit().putString("recent", org.json.JSONArray(history).toString()).apply()
        searchGeneration++; searchMoreLoading = false
        loading = true; error = null; searchMode = true; channel = null; playlist = null; results = emptyList(); searchPage = null; searchMoreError = null
        scope.launch {
            try { val page = VideoRepository.searchPage(term, filter); searchPage = page; results = page.items; if (results.isEmpty()) error = "Tidak ada hasil ditemukan." }
            catch (e: Exception) { error = "Pencarian gagal: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun moreSearchResults() {
        val page = searchPage ?: return
        if (page.next == null || searchMoreLoading || loading) return
        searchMoreLoading = true; searchMoreError = null
        val generation = searchGeneration
        scope.launch {
            try {
                val next = VideoRepository.moreSearch(page)
                if (generation != searchGeneration) return@launch
                val updated = (results + next.items).distinctBy { it.url }
                searchPage = if (updated.size == results.size) next.copy(next = null) else next
                results = updated
            } catch (e: Exception) { if (generation == searchGeneration) searchMoreError = "Hasil berikutnya gagal dimuat: ${e.message ?: "coba lagi"}" }
            finally { if (generation == searchGeneration) searchMoreLoading = false }
        }
    }
    fun loadChannelTab(filter: String) {
        val page = channel ?: return
        if (loading) return
        channelTab = filter; channelItems = emptyList(); error = null; loading = true
        scope.launch {
            try { val result = VideoRepository.channelTabPage(page, filter); channelItems = result.items; browseNext = result.next; browseError = null }
            catch (e: Exception) { error = "Tab gagal dimuat: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun openPlaylist(url: String) {
        if (loading) return
        loading = true; error = null
        scope.launch {
            try { val result = VideoRepository.playlist(url); playlist = result; playlistItems = result.relatedItems; browseNext = result.nextPage; browseError = null; showHistory = false }
            catch (e: Exception) { error = "Playlist gagal dibuka: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun loadMoreBrowse() {
        val next = browseNext ?: return
        val currentPlaylist = playlist
        val currentChannel = channel
        val currentTab = channelTab
        if (browseLoading) return
        browseLoading = true; browseError = null
        scope.launch {
            try {
                val result = if (currentPlaylist != null) VideoRepository.morePlaylist(currentPlaylist.url, next) else if (currentChannel != null) VideoRepository.moreChannelTab(currentChannel, currentTab, next) else return@launch
                if (playlist != currentPlaylist || channel != currentChannel || channelTab != currentTab) return@launch
                val old = if (currentPlaylist != null) playlistItems else channelItems
                val merged = (old + result.items).distinctBy { it.url }
                if (currentPlaylist != null) playlistItems = merged else channelItems = merged
                browseNext = if (merged.size == old.size) null else result.next
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { browseError = "Halaman berikutnya gagal dimuat: ${e.message ?: "coba lagi"}" }
            finally { browseLoading = false }
        }
    }
    /** Kualitas video dan jalur audio awal untuk [info]. */
    fun pickStreams(info: StreamInfo): Pair<VideoStream?, AudioStream?> {
        val options = qualityOptions(info)
        val initial = if (!info.hlsUrl.isNullOrBlank()) null else options.firstOrNull { it.height in 1..maxVideoHeight } ?: options.lastOrNull()
        if (initial == null && info.hlsUrl.isNullOrBlank()) error("Format video yang bisa diputar tidak tersedia.")
        val audio = audioTracks(info).firstOrNull { it.audioTrackType == AudioTrackType.ORIGINAL } ?: audioTracks(info).firstOrNull()
        return initial to audio
    }
    /** Pasang [info] ke pemutar: pilih kualitas dan audio awal, lalu mulai memutar. Dipakai video biasa. */
    fun startInfo(info: StreamInfo, resume: Long) {
        selectedSubtitle = null
        lastSubtitle = null
        playerLocked = false
        qualityMode = "auto"
        player.pauseAtEndOfMediaItems = false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        val (initial, audio) = pickStreams(info)
        playQuality(info, initial, audio, resume, true)
        selectedAudio = audio
        selected = info
        minimized = false
    }
    /** Sumber media satu Short. mediaId = videoKey, supaya item di daftar putar pemutar bisa dikenali. */
    fun shortSource(info: StreamInfo, stream: VideoStream?, audio: AudioStream?): MediaSource {
        val key = videoKey(info.url)
        val factory = DefaultMediaSourceFactory(context)
        fun item(url: String) = MediaItem.Builder().setUri(url).setMediaId(key).build()
        return when {
            stream != null && stream.isVideoOnly && audio != null -> MergingMediaSource(factory.createMediaSource(item(stream.content)), factory.createMediaSource(MediaItem.fromUri(audio.content)))
            stream != null && !stream.isVideoOnly -> factory.createMediaSource(item(stream.content))
            stream == null && !info.hlsUrl.isNullOrBlank() -> factory.createMediaSource(item(info.hlsUrl))
            else -> error("Format video yang bisa diputar tidak tersedia.")
        }
    }
    /**
     * Putar sebuah Short dari awal. Bila Short ini sudah masuk daftar putar pemutar sebagai item berikutnya
     * ([preloadNext]), cukup pindah ke item itu: datanya sudah di-buffer sehingga frame pertama muncul seketika.
     */
    fun startShort(info: StreamInfo) {
        val key = videoKey(info.url)
        val (stream, audio) = pickStreams(info)
        val loaded = if (player.playbackState == Player.STATE_IDLE) null else (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == key }
        selectedSubtitle = null; lastSubtitle = null; playerLocked = false; qualityMode = "auto"
        player.pauseAtEndOfMediaItems = true
        if (loaded != null) {
            player.seekTo(loaded, 0L)
            if (loaded > 0) player.removeMediaItems(0, loaded)
            player.playWhenReady = true
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setMaxVideoSize(Int.MAX_VALUE, if (stream == null) maxVideoHeight else Int.MAX_VALUE).build()
            player.setMediaSource(shortSource(info, stream, audio))
            player.prepare()
            player.playWhenReady = true
        }
        quality = stream; selectedAudio = audio; selected = info; minimized = false
    }
    /** Tambahkan Short berikutnya ke daftar putar agar ExoPlayer mem-buffernya selagi Short sekarang diputar. */
    fun preloadNext(current: StreamInfo, next: StreamInfo) {
        if (player.playbackState == Player.STATE_IDLE || player.currentMediaItem?.mediaId != videoKey(current.url)) return
        val nextKey = videoKey(next.url)
        val at = player.currentMediaItemIndex + 1
        if (player.mediaItemCount > at) {
            if (player.getMediaItemAt(at).mediaId == nextKey) return
            player.removeMediaItems(at, player.mediaItemCount)
        }
        runCatching {
            val (stream, audio) = pickStreams(next)
            player.addMediaSource(shortSource(next, stream, audio))
        }
    }
    var lastRecovered by remember { mutableStateOf<String?>(null) }
    /** Pemutar error saat Shorts (misalnya item yang di-buffer lebih dulu ditolak server): ulangi sekali dengan resolve baru. */
    fun recoverShort(info: StreamInfo) {
        val key = videoKey(info.url)
        if (lastRecovered == key) { shortsResolver.fail(info.url, "Pemutaran gagal"); return }
        lastRecovered = key
        shortsResolver.evict(info.url)
        player.stop(); player.clearMediaItems()
        scope.launch {
            try { startShort(shortsResolver.await(info.url)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { shortsResolver.fail(info.url, e.message ?: "gagal dimuat") }
        }
    }
    fun openUrl(url: String) {
        if (loading) return
        loading = true; error = null
        scope.launch {
            try {
                val info = VideoRepository.resolve(url, context)
                savePosition()
                val related = info.relatedItems.filterIsInstance<StreamInfoItem>().filter { it.url != info.url }
                if (related.isNotEmpty()) {
                    val candidates = (related + HomeFeedCache.read(relatedCachePrefs)).distinctBy { it.url }
                    HomeFeedCache.save(relatedCachePrefs, candidates)
                    home = HomeRecommendations.rank((related + home).distinctBy { it.url }, watchHistory, emptySet(), emptySet())
                }
                startInfo(info, watchHistory.firstOrNull { it.url == info.url }?.position ?: 0L)
            } catch (e: Exception) { error = "Video gagal dibuka: ${e.message ?: "coba lagi"}" }
            finally { loading = false }
        }
    }
    fun open(item: StreamInfoItem) {
        val short = item.isShortFormContent || (channel != null && channelTab == ChannelTabs.SHORTS)
        val list = if (playlist != null) playlistItems.filterIsInstance<StreamInfoItem>()
            else if (short) (if (channel != null) channelItems else results).filterIsInstance<StreamInfoItem>().filter { it.isShortFormContent || (channel != null && channelTab == ChannelTabs.SHORTS) }
            else emptyList()
        if (short) {
            // Shorts tidak lewat openUrl: pager menampilkan poster seketika, lalu ShortsResolver memuat dan memutar halaman yang aktif.
            val index = list.indexOfFirst { videoKey(it.url) == videoKey(item.url) }
            queue = if (index >= 0) list else listOf(item)
            shortsIndex = index.coerceAtLeast(0)
            shortsGeneration++
            shortsTabQueue = false
            shortsMoreError = null
            minimized = false
            shortsMode = true
        } else {
            queue = list
            shortsMode = false
            openUrl(item.url)
        }
    }
    // Menekan tab Shorts langsung membuka layar Shorts (kosong + spinner); antrean diisi dari feed begitu siap.
    LaunchedEffect(mainTab, shortsRequest) {
        if (mainTab != "shorts") return@LaunchedEffect
        minimized = false
        shortsMode = true; shortsTabQueue = true
        queue = emptyList(); shortsIndex = 0; shortsGeneration++
        shortsLoading = true
        shortsError = null
        shortsPage = null; shortsMoreError = null; shortsTopicIndex = 0; shortsSeqNext = null; shortsFromFeed = false
        try {
            // Sumber utama: urutan Shorts YouTube (reel_watch_sequence). Urutan dari server dipertahankan; pencarian "shorts" hanya cadangan.
            // Short pembuka ditampilkan dan mulai di-resolve begitu diketahui, paralel dengan permintaan urutan.
            var seedShown = false
            val feed = try {
                ShortsFeed.start { seed -> withContext(Dispatchers.Main) { if (queue.isEmpty()) { queue = listOf(seed); seedShown = true } } }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val feedItems = feed?.items.orEmpty().filter { it.url !in hiddenVideos }.distinctBy { videoKey(it.url) }
            if (feedItems.isNotEmpty()) {
                shortsFromFeed = true
                shortsSeqNext = feed?.next
                queue = feedItems
            } else if (seedShown) {
                shortsFromFeed = false // pertahankan Short pembuka; antrean diisi dari pencarian saat tinggal sedikit
            } else {
                var page = VideoRepository.searchPage("shorts")
                val found = mutableListOf<StreamInfoItem>()
                var attempts = 0
                while (true) {
                    found += page.items.filterIsInstance<StreamInfoItem>().filter { it.isShortFormContent || it.url.contains("/shorts/") }
                    attempts++
                    if (found.size >= 10 || page.next == null || attempts >= 3) break
                    page = VideoRepository.moreSearch(page)
                }
                shortsPage = page
                queue = HomeRecommendations.rank(found.distinctBy { videoKey(it.url) }, watchHistory, hiddenVideos, hiddenChannels)
            }
            if (queue.isEmpty()) shortsError = "Shorts belum tersedia. Coba lagi."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { shortsError = "Shorts gagal dimuat: ${e.message ?: "coba lagi"}" }
        finally { shortsLoading = false }
    }
    // Siapkan halaman pertama Shorts selagi pengguna di beranda, supaya menekan tab Shorts tidak menunggu jaringan.
    LaunchedEffect(Unit) {
        delay(4000)
        if (mainTab == "shorts") return@LaunchedEffect
        ShortsFeed.warm()?.items?.firstOrNull()?.let { shortsResolver.prefetch(it.url) }
    }
    // Memutar halaman yang sedang aktif di pager. Resolve diambil dari cache bila sudah di-prefetch, jadi geser tidak menunggu jaringan.
    LaunchedEffect(shortsMode, shortsIndex, shortsGeneration, shortsRetry, queue.firstOrNull()?.url) {
        if (!shortsMode) return@LaunchedEffect
        val item = queue.getOrNull(shortsIndex) ?: return@LaunchedEffect
        if (selected?.let { videoKey(it.url) } == videoKey(item.url)) { player.playWhenReady = true; return@LaunchedEffect }
        if (shortsResolver.info(item.url) == null) player.pause()
        try {
            val info = shortsResolver.await(item.url)
            if (shortsMode && queue.getOrNull(shortsIndex)?.let { videoKey(it.url) } == videoKey(item.url)) {
                savePosition()
                startShort(info)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { shortsResolver.fail(item.url, e.message ?: "gagal dimuat") }
    }
    // Setelah Short berikutnya selesai di-resolve, masukkan ke daftar putar supaya ikut di-buffer.
    val nextShortInfo = if (shortsMode) queue.getOrNull(shortsIndex + 1)?.let { shortsResolver.info(it.url) } else null
    LaunchedEffect(shortsMode, selected?.url, nextShortInfo) {
        val next = nextShortInfo ?: return@LaunchedEffect
        val current = selected ?: return@LaunchedEffect
        if (shortsMode && queue.getOrNull(shortsIndex)?.let { videoKey(it.url) } == videoKey(current.url)) preloadNext(current, next)
    }
    // Siapkan Short di sekitar halaman aktif selagi yang sekarang diputar.
    LaunchedEffect(shortsMode, shortsIndex, queue.size) {
        if (!shortsMode) return@LaunchedEffect
        for (offset in intArrayOf(0, 1, 2, -1)) queue.getOrNull(shortsIndex + offset)?.let { shortsResolver.prefetch(it.url) }
    }
    // Isi ulang antrean tab Shorts saat sisa halaman tinggal sedikit.
    val shortsNeedMore = shortsMode && shortsTabQueue && queue.isNotEmpty() && queue.size - shortsIndex <= 4
    LaunchedEffect(shortsNeedMore, queue.size, shortsMoreRequest) {
        if (!shortsNeedMore) return@LaunchedEffect
        shortsMoreLoading = true
        shortsMoreError = null
        try {
            val existing = queue.mapTo(mutableSetOf()) { videoKey(it.url) }
            val found = mutableListOf<StreamInfoItem>()
            var fromFeed = false
            if (shortsFromFeed) {
                var feedAttempts = 0
                try {
                    while (found.isEmpty() && feedAttempts < 3) {
                        val token = shortsSeqNext ?: break
                        val feed = ShortsFeed.more(token)
                        shortsSeqNext = feed.next
                        found += feed.items.filter { it.url !in hiddenVideos && existing.add(videoKey(it.url)) }
                        feedAttempts++
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* jatuh ke pencarian untuk putaran ini; token feed tetap disimpan untuk dicoba lagi */ }
                if (found.isNotEmpty()) fromFeed = true else if (shortsSeqNext == null) shortsFromFeed = false
            }
            var page = shortsPage
            var attempts = 0
            while (found.isEmpty() && attempts < 4) {
                page = if (page?.next != null) VideoRepository.moreSearch(page) else {
                    val topic = HomeRecommendations.query(watchHistory, topics, shortsTopicIndex++, history)
                    VideoRepository.searchPage("$topic shorts")
                }
                found += page.items.filterIsInstance<StreamInfoItem>().filter {
                    (it.isShortFormContent || it.url.contains("/shorts/")) && existing.add(videoKey(it.url))
                }
                attempts++
            }
            shortsPage = page
            val additions = if (fromFeed) found.toList() else HomeRecommendations.rank(found, watchHistory, hiddenVideos, hiddenChannels)
            queue = queue + additions
            if (additions.isEmpty()) shortsMoreError = "Tidak ada Shorts baru. Ketuk untuk mencoba lagi."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { shortsMoreError = "Gagal memuat Shorts. Ketuk untuk mencoba lagi." }
        finally { shortsMoreLoading = false }
    }
    fun nextItem(info: StreamInfo): StreamInfoItem? {
        val index = queue.indexOfFirst { it.url == info.url }
        return if (index >= 0) queue.getOrNull(index + 1) else info.relatedItems.filterIsInstance<StreamInfoItem>().firstOrNull { it.url != info.url }
    }
    val currentOpen = rememberUpdatedState<(String) -> Unit> { openUrl(it) }
    val currentSelected = rememberUpdatedState(selected)
    val currentAutoplay = rememberUpdatedState(autoplay)
    val currentShortsMode = rememberUpdatedState(shortsMode)
    val currentQueue = rememberUpdatedState(queue)
    val currentRecover = rememberUpdatedState<(StreamInfo) -> Unit> { recoverShort(it) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // Shorts berhenti di akhir item (pauseAtEndOfMediaItems) agar tidak lompat ke item yang sudah di-buffer; ulang dari awal.
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && currentShortsMode.value) {
                    player.seekTo(player.currentMediaItemIndex, 0L); player.playWhenReady = true
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (currentShortsMode.value) currentSelected.value?.let { currentRecover.value(it) }
            }
            override fun onPlaybackStateChanged(state: Int) {
                val info = currentSelected.value ?: return
                if (state == Player.STATE_ENDED && currentShortsMode.value) {
                    player.seekTo(0); player.play()
                } else if (state == Player.STATE_ENDED && currentAutoplay.value) {
                    val list = currentQueue.value
                    val index = list.indexOfFirst { it.url == info.url }
                    val next = if (index >= 0) list.getOrNull(index + 1) else info.relatedItems.filterIsInstance<StreamInfoItem>().firstOrNull { it.url != info.url }
                    if (next != null) currentOpen.value(next.url)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(selected) {
        while (selected != null) { delay(5000); savePosition() }
    }
    fun openLink(info: StreamInfo, url: String) {
        timestampSeconds(url, info.id)?.let { seconds -> player.seekTo(seconds * 1000L); player.play(); return }
        val uri = runCatching { Uri.parse(if (url.startsWith("/")) "https://www.youtube.com$url" else url) }.getOrNull() ?: return
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        when {
            host.endsWith("youtu.be") || (host.contains("youtube.com") && (path == "/watch" || path.startsWith("/shorts/"))) -> openUrl(uri.toString())
            host.contains("youtube.com") && (path.startsWith("/@") || path.startsWith("/channel/") || path.startsWith("/c/") || path.startsWith("/user/")) -> openChannel(uri.toString())
            else -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        }
    }
    // The feed stays under the watch page while the player moves, so the mini player lands on the feed it returns to.
    val gridState = rememberLazyGridState()
    val target = if (fullscreen) 0f else dragProgress ?: if (minimized) 1f else 0f
    val progress by animateFloatAsState(targetValue = target, animationSpec = tween(if (dragProgress == null) 300 else 0, easing = FastOutSlowInEasing), label = "player transition")
    // Keep the watch screen intact at the start of a short drag. After that,
    // quickly form the mini player while its vertical position keeps following the gesture.
    val collapse = FastOutSlowInEasing.transform(((progress - .12f) / .22f).coerceIn(0f, 1f))
    val watchFade = ((progress - .22f) / .12f).coerceIn(0f, 1f)
    val verticalProgress = if (progress <= .12f) progress * .25f else (.03f + (progress - .12f) * (0.97f / .88f)).coerceIn(0f, 1f)
    val info = selected?.takeUnless { minimized && progress > .999f }
    val showFeed = selected == null || minimized || progress > 0f
    val selectTab: (String) -> Unit = { tab ->
        if (tab != "shorts" && shortsMode && !minimized) closePlayer()
        if (tab == "shorts" && mainTab != "shorts") {
            // Masuk ke layar Shorts seketika (hitam + spinner); antrean diisi oleh efek pemuatan feed.
            if (selected != null && !shortsMode) { savePosition(); player.stop(); player.clearMediaItems(); selected = null; quality = null; selectedAudio = null }
            minimized = false; shortsMode = true; shortsTabQueue = true
            queue = emptyList(); shortsIndex = 0; shortsError = null; shortsLoading = true
        }
        mainTab = tab; showHistory = false; searchMode = false; searchActive = false; channel = null; playlist = null; category = "Semua"; error = null
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val wideWatch = maxWidth >= 840.dp
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        Column(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            if (showFeed) {
                if (searchActive) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus(); keyboard?.show() }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { searchActive = false; keyboard?.hide() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                        TextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f).focusRequester(focusRequester), placeholder = { Text("Telusuri YouTube") }, singleLine = true,
                            colors = TextFieldDefaults.colors(focusedContainerColor = Surface, unfocusedContainerColor = Surface, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Hapus") } },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }), shape = RoundedCornerShape(50))
                        IconButton(onClick = { search() }) { Icon(Icons.Default.Search, "Cari") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (playlist != null || channel != null || searchMode) IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                        if (searchMode && playlist == null && channel == null) {
                            Text(lastSearch, modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Surface).clickable { query = lastSearch; searchActive = true }.padding(horizontal = 16.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                        Box(Modifier.size(width = 34.dp, height = 24.dp).background(Accent, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Text("FlufPlay", modifier = Modifier.padding(start = 7.dp).weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { searchActive = true }) { Icon(Icons.Default.Search, "Telusuri") }
                        IconButton(onClick = {
                            val currentPlaylist = playlist
                            val currentChannel = channel
                            when {
                                currentPlaylist != null -> openPlaylist(currentPlaylist.url)
                                currentChannel != null -> loadChannelTab(channelTab)
                                searchMode -> search(lastSearch, searchFilter)
                                else -> if (category == "Semua") loadHome(refresh = true) else selectCategory(category, true)
                            }
                        }, enabled = !loading) { Icon(Icons.Default.Refresh, "Muat ulang halaman") }
                        IconButton(onClick = { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) }) { Text("↓", style = MaterialTheme.typography.headlineSmall) }
                        IconButton(onClick = {
                            if (YouTubeAuth.isLoggedIn) showAccountDialog = true else showLoginDialog = true
                        }) {
                            val profile = YouTubeAuth.profile
                            if (YouTubeAuth.isLoggedIn && !profile.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = profile.avatarUrl,
                                    contentDescription = "Akun YouTube",
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, Color(0xFF0F9D58), CircleShape)
                                )
                            } else {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = "Akun YouTube",
                                    tint = if (YouTubeAuth.isLoggedIn) Color(0xFF0F9D58) else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (downloadState.message.isNotBlank() && showFeed) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(downloadState.message, style = MaterialTheme.typography.bodySmall)
                        if (downloadState.running) {
                            val value = downloadState.progress
                            if (value == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            else LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                    if (downloadState.running) TextButton(onClick = { context.startService(Intent(context, DownloadService::class.java).setAction("cancel")) }) { Text("Batal") }
                    else TextButton(onClick = { DownloadStatus.state.value = DownloadState() }) { Text("Tutup") }
                }
            }
            if (loading && showFeed && (searchMode || channel != null || playlist != null || searchActive)) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (showFeed && searchActive) {
                val recent = history.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
                val online = suggestions.filterNot { suggestion -> recent.any { it.equals(suggestion, ignoreCase = true) } }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                    items(recent) { term ->
                        Row(Modifier.fillMaxWidth().clickable { search(term) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("◷", style = MaterialTheme.typography.titleLarge, color = Color.LightGray)
                            Text(term, Modifier.weight(1f).padding(start = 20.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { history = history.filterNot { it == term }; preferences.edit().putString("recent", org.json.JSONArray(history).toString()).apply() }) { Icon(Icons.Default.Close, "Hapus dari riwayat", tint = Color.LightGray) }
                        }
                    }
                    items(online) { term ->
                        Row(Modifier.fillMaxWidth().clickable { search(term) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = Color.LightGray)
                            Text(term, Modifier.weight(1f).padding(start = 20.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { query = term }) { Text("↖", color = Color.LightGray) }
                        }
                    }
                }
            } else if (showFeed && mainTab != "home" && channel == null && playlist == null && !searchMode) {
                LibraryTab(
                    modifier = Modifier.weight(1f), tab = mainTab, videos = watchHistory,
                    onHistory = { showHistory = true },
                    onOpenVideo = { entry -> shortsMode = false; queue = emptyList(); openUrl(entry.url) },
                    onDownloads = { context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)) },
                    onAccount = { if (YouTubeAuth.isLoggedIn) showAccountDialog = true else showLoginDialog = true },
                    shortsLoading = shortsLoading || (mainTab == "shorts" && loading),
                    shortsError = shortsError ?: if (mainTab == "shorts") error else null,
                    onSearchShorts = { error = null; shortsRequest++ }
                )
            } else if (showFeed) {
                error?.let { message ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        if (!searchMode && channel == null) TextButton(onClick = { if (category == "Semua") loadHome() else selectCategory(category, true) }) { Text("Coba lagi") }
                    }
                }
                val page = channel
                val currentPlaylist = playlist
                val filteredHome = remember(home, hiddenVideos, hiddenChannels) {
                    home.filter { it.url !in hiddenVideos && it.uploaderUrl.orEmpty() !in hiddenChannels }
                }
                val visibleItems: List<InfoItem> = if (currentPlaylist != null) playlistItems else if (page != null) channelItems else if (searchMode) results else if (category == "Semua") filteredHome else categoryItems
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
                val resolveFeedAvatars = !gridState.isScrollInProgress
                val isFeed = currentPlaylist == null && page == null && !searchMode
                LaunchedEffect(gridState, isFeed, category, visibleItems.size, feedLoading, feedError, feedEnd, loading) {
                    if (isFeed && !feedLoading && feedError == null && !feedEnd && !loading && visibleItems.isNotEmpty()) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .distinctUntilChanged().collect { last ->
                                if (last >= visibleItems.size - 3) loadMoreFeed()
                            }
                    }
                }
                val isSearchResults = searchMode && page == null && currentPlaylist == null
                LaunchedEffect(lastSearch, searchFilter, isSearchResults) { if (isSearchResults) gridState.scrollToItem(0) }
                LaunchedEffect(gridState, isSearchResults, results.size, searchMoreLoading, searchMoreError, searchPage, loading) {
                    if (isSearchResults && !loading && !searchMoreLoading && searchMoreError == null && searchPage?.next != null && results.isNotEmpty()) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
                            if (last >= results.size - 3) moreSearchResults()
                        }
                    }
                }
                LaunchedEffect(gridState, page, currentPlaylist, visibleItems.size, browseNext, browseLoading, browseError) {
                    if ((page != null || currentPlaylist != null) && browseNext != null && !browseLoading && browseError == null && visibleItems.isNotEmpty()) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
                            if (last >= visibleItems.size - 3) loadMoreBrowse()
                        }
                    }
                }
                val refreshState = rememberPullToRefreshState()
                val refreshingFeed = isFeed && loading
                val refreshModifier = if (isFeed) Modifier.pullToRefresh(
                    isRefreshing = refreshingFeed,
                    state = refreshState,
                    enabled = !showHistory && (selected == null || minimized) && !feedLoading,
                    onRefresh = { if (category == "Semua") loadHome(refresh = true) else selectCategory(category, true) }
                ) else Modifier
                Box(Modifier.weight(1f).fillMaxWidth().then(refreshModifier)) {
                LazyVerticalGrid(state = gridState, modifier = Modifier.fillMaxSize(), columns = if (isSearchResults) GridCells.Fixed(1) else GridCells.Adaptive(minSize = 300.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (currentPlaylist != null) {
                            Column { Text(currentPlaylist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Playlist · ${playlistItems.size} video dimuat", color = Color.LightGray, modifier = Modifier.padding(top = 8.dp)) }
                        } else if (page != null) {
                            val isChannelLive = remember(page, channelItems) {
                                channelItems.filterIsInstance<StreamInfoItem>().any { it.isOngoingLive() } || page.videos.any { it.isOngoingLive() }
                            }
                            ChannelHeader(page, channelTab, isChannelLive) { loadChannelTab(it) }
                        }
                        else if (searchMode) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("all" to "Semua", "videos" to "Video", "channels" to "Channel", "playlists" to "Playlist").forEach { (value, label) ->
                                val active = searchFilter == value
                                Text(label, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (active) Color.White else Surface).clickable(enabled = !loading && !searchMoreLoading) { if (value != searchFilter) search(lastSearch, value) }.padding(horizontal = 13.dp, vertical = 8.dp), color = if (active) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Semua", "Musik", "Game", "Berita", "Film", "Olahraga", "Live").forEach { label ->
                                val active = category == label
                                Text(label, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (active) Color.White else Surface).clickable { selectCategory(label) }.padding(horizontal = 13.dp, vertical = 8.dp), color = if (active) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
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
                                        is StreamInfoItem -> if (wideResult) SearchWideVideoCard(item, resolveAvatar = resolveFeedAvatars) { open(item) } else VideoCard(item, resolveAvatar = resolveFeedAvatars) { open(item) }
                                        is ChannelInfoItem -> SearchChannelCard(item) { openChannel(item.url) }
                                        is PlaylistInfoItem -> if (wideResult) SearchWidePlaylistCard(item) { openPlaylist(item.url) } else PlaylistCard(item) { openPlaylist(item.url) }
                                    }
                                }
                            }
                        } else when (item) {
                            is StreamInfoItem -> {
                                val isMembership = channel != null && channelTab == VideoRepository.TAB_MEMBERSHIP
                                VideoCard(
                                    item,
                                    resolveAvatar = resolveFeedAvatars,
                                    isMembership = isMembership,
                                    onNotInterested = if (isFeed && category == "Semua") {
                                        { hiddenVideos = HomeRecommendations.hide(recommendationPrefs, "videos", item.url) }
                                    } else null,
                                    onHideChannel = if (isFeed && category == "Semua" && !item.uploaderUrl.isNullOrBlank()) {
                                        { hiddenChannels = HomeRecommendations.hide(recommendationPrefs, "channels", item.uploaderUrl) }
                                    } else null
                                ) { open(item) }
                            }
                            is ChannelInfoItem -> ChannelCard(item) { openChannel(item.url) }
                            is PlaylistInfoItem -> PlaylistCard(item) { openPlaylist(item.url) }
                        }
                    }
                    if (browseLoading && (page != null || currentPlaylist != null)) item(span = { GridItemSpan(maxLineSpan) }) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    if (browseError != null && (page != null || currentPlaylist != null)) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(browseError ?: "", Modifier.weight(1f)); TextButton(onClick = { loadMoreBrowse() }) { Text("Coba lagi") } } }
                    if (isSearchResults && searchMoreLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    if (isSearchResults && searchMoreError != null) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(searchMoreError ?: "", Modifier.weight(1f)); TextButton(onClick = { moreSearchResults() }) { Text("Coba lagi") } } }
                    if (isFeed && feedLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    if (isFeed && feedError != null) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(feedError ?: "", Modifier.weight(1f), color = Color.LightGray); TextButton(onClick = { loadMoreFeed() }) { Text("Coba lagi") } } }
                    if (!loading && error == null && visibleItems.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(if (page != null) "Belum ada video yang tersedia." else "Tidak ada hasil.", color = Color.LightGray) }
                }
                if (isFeed) PullToRefreshDefaults.Indicator(state = refreshState, isRefreshing = refreshingFeed, modifier = Modifier.align(Alignment.TopCenter))
                }
            }
            if (showFeed && !searchActive) HomeNavigation(selected = mainTab, onSelect = selectTab)
        }
        if (showHistory && (selected == null || minimized)) {
            WatchHistoryScreen(watchHistory, onBack = { showHistory = false }, onOpen = { entry -> showHistory = false; shortsMode = false; queue = emptyList(); openUrl(entry.url) }, onClear = { WatchHistory.clear(watchPrefs); watchHistory = emptyList() }, onRemove = { entry -> WatchHistory.remove(watchPrefs, entry.url); watchHistory = WatchHistory.read(watchPrefs) }, onResetRecommendations = if (hiddenVideos.isNotEmpty() || hiddenChannels.isNotEmpty()) {{ recommendationPrefs.edit().clear().apply(); hiddenVideos = emptySet(); hiddenChannels = emptySet() }} else null)
        }
        val activeChatState = selected?.takeIf { !shortsMode }?.let { current ->
            val liveStream = current.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                current.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
            rememberLiveChat(current.url, liveStream, player)
        }
        // Move the opaque watch page down with the player before fading it out.
        if (info != null && !shortsMode) Box(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val isLive = info.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM || info.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
            val chatState = activeChatState ?: rememberLiveChat(info.url, isLive, player)
            var showComments by remember(info.url) { mutableStateOf(false) }
            var showChat by remember(info.url) { mutableStateOf(false) }
            var showDescription by remember(info.url) { mutableStateOf(false) }
            val comments = rememberComments(info.url)
            val channelUploads = rememberChannelUploads(info.uploaderUrl)
            WatchScreen(showDescription = showDescription, showComments = showComments, showChat = showChat, onClosePanel = { showDescription = false; showComments = false; showChat = false }, watchAlpha = 1f - watchFade, watchOffset = if (fullscreen) 0f else verticalProgress, info = info, comments = comments, chatState = chatState, onComments = { showDescription = false; showChat = false; showComments = true }, onDescription = { showComments = false; showChat = false; showDescription = true }, onChat = { showComments = false; showDescription = false; showChat = true },
                onLink = { openLink(info, it) }, onSeek = { player.seekTo(it * 1000L); player.play() }, onShare = { shareVideo(info) }, onCopy = { copyVideoLink(info) }, player = player, hidden = fullscreen && !exitDragging, onFullscreen = { setFullscreen(!fullscreen) }, recommendations = info.relatedItems.filterIsInstance<StreamInfoItem>().filter { it.url != info.url }.distinctBy { it.url }, channelVideos = channelUploads.filter { it.url != info.url }.take(6), error = error,
                onBack = { back() }, onSettings = { qualityInitialPage = "menu"; showQuality = true }, onDownload = { showDownloads = true },
                onChannel = { openChannel(info.uploaderUrl ?: "") }, onOpenChannel = { openChannel(it) }, onVideo = { open(it) })
            if (showQuality) QualityDialog(info, quality, selectedAudio, selectedSubtitle, autoplay, maxVideoHeight, player, qualityInitialPage, fullscreen, qualityMode, onQualityMode = { qualityMode = it }, onLock = { playerLocked = true; showQuality = false }, onMaxHeight = { height ->
                maxVideoHeight = height; watchPrefs.edit().putInt("max_video_height", height).apply()
                if (quality == null && qualityMode == "auto") player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, height).build()
            }, onAutoplay = { autoplay = it; watchPrefs.edit().putBoolean("autoplay", it).apply() }, onSubtitle = { subtitle -> selectedSubtitle = subtitle; if (subtitle != null) lastSubtitle = subtitle; try { playQuality(info, quality, selectedAudio, player.currentPosition.coerceAtLeast(0L), player.playWhenReady) } catch (e: Exception) { android.widget.Toast.makeText(context, "Subtitle gagal: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() } }, onDismiss = { showQuality = false }, onSelect = { stream ->
                if (stream != quality) {
                    val position = player.currentPosition.coerceAtLeast(0L)
                    val playing = player.playWhenReady
                    try { playQuality(info, stream, selectedAudio, position, playing) }
                    catch (e: Exception) { android.widget.Toast.makeText(context, "Gagal mengganti kualitas: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
                }
                showQuality = false
            }, onSelectAudio = { audio ->
                if (audio != selectedAudio || quality?.isVideoOnly != true) {
                    val separate = VideoRepository.videos(info)
                    val currentHeight = quality?.height ?: 0
                    val video = if (quality?.isVideoOnly == true) quality else separate.minByOrNull { kotlin.math.abs(it.height - currentHeight) }
                    if (video == null) {
                        android.widget.Toast.makeText(context, "Pilihan audio tidak tersedia untuk format video ini", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        try {
                            playQuality(info, video, audio, player.currentPosition.coerceAtLeast(0L), player.playWhenReady)
                            selectedAudio = audio
                        } catch (e: Exception) { android.widget.Toast.makeText(context, "Gagal mengganti audio: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
                    }
                }
                showQuality = false
            })
            if (showDownloads) DownloadDialog(info, onDismiss = { showDownloads = false }) { height, audio ->
                showDownloads = false
                if (downloadState.running) {
                    android.widget.Toast.makeText(context, "Tunggu unduhan yang sedang berjalan", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val safeTitle = info.name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(90).ifBlank { "video" }
                    pendingDownload = DownloadChoice(info.url, height, audio)
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(if (audio) "audio/mpeg" else "video/mp4")
                        .putExtra(Intent.EXTRA_TITLE, "$safeTitle.${if (audio) "mp3" else "mp4"}")
                    saveFile.launch(intent)
                }
            }
        }
        if (shortsMode && !minimized) {
            val inTab = shortsTabQueue && mainTab == "shorts"
            ShortsScreen(
                items = queue, startIndex = shortsIndex, generation = shortsGeneration, resolver = shortsResolver,
                activeUrl = selected?.url, player = player,
                emptyLoading = shortsLoading, emptyMessage = shortsError, feedMessage = shortsMoreError,
                onSettled = { shortsIndex = it }, onRetry = { shortsRequest++ }, onRetryMore = { shortsMoreRequest++ },
                onRetryItem = { url ->
                    // Buang pemutaran yang gagal supaya efek pemutaran memuat ulang Short ini dari awal.
                    lastRecovered = null; player.stop(); player.clearMediaItems(); selected = null
                    shortsResolver.evict(url); shortsResolver.retry(url); shortsRetry++
                },
                onRate = { videoId, action -> ShortsFeed.rate(videoId, action) },
                onNeedLogin = { showLoginDialog = true },
                onNotInterested = { item -> hiddenVideos = HomeRecommendations.hide(recommendationPrefs, "videos", item.url) },
                onLink = { info, link -> openLink(info, link) }, onChannel = { openChannel(it) },
                onClose = if (inTab) null else {{ closePlayer() }},
                onShare = { shareVideo(it) },
                bottomBar = { if (inTab) HomeNavigation(selected = "shorts", onSelect = selectTab) },
            )
        }
        selected?.takeIf { !shortsMode || minimized }?.let { playing ->
            val isPlayingLive = playing.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM || playing.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
            val playingChatState = activeChatState ?: rememberLiveChat(playing.url, isPlayingLive, player)
            val insets = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            BoxWithConstraints(insets) {
                val wide = maxWidth >= 840.dp
                // Size and position of the watch layout (exitProgress 1). Fullscreen is the whole screen (exitProgress 0).
                val inlineWidth = if (wide) wideWatchPlayerWidth(maxWidth) else maxWidth
                val inlineHeight = inlineWidth * 9f / 16f
                val startX = 0.dp
                val limits = MiniLimits.of(maxWidth, maxHeight, if (searchActive || fullscreen) 0.dp else 56.dp)
                miniState.limits = limits
                LaunchedEffect(limits) { miniState.settle(animate = minimized) }
                LaunchedEffect(minimized) { if (minimized) miniState.show() }
                val miniWidth = miniState.width.value.dp
                val miniHeight = miniWidth * 9f / 16f
                // Each edge lerps linearly from the screen to the watch layout. The box is inset by the status bar unless fullscreen.
                val exit = exitProgress.value
                val boxTop = if (fullscreen) 0.dp else statusBarHeight
                val baseX = startX * exit
                val baseY = statusBarHeight * exit - boxTop
                val baseWidth = screenWidth + (inlineWidth - screenWidth) * exit
                val baseHeight = screenHeight + (inlineHeight - screenHeight) * exit
                val width = baseWidth + (miniWidth - inlineWidth) * collapse
                val height = baseHeight + (miniHeight - inlineHeight) * collapse
                val miniPosition = miniState.position.value
                val x = baseX + (miniPosition.x.dp - startX) * collapse
                val y = baseY + miniPosition.y.dp * verticalProgress
                val travel = (limits.areaHeight - limits.bottomBar - limits.inset).dp - miniHeight
                val dragTravel = if (fullscreen) inlineHeight else travel
                FloatingPlayer(player = player, title = playing.name, locked = playerLocked, onUnlock = { playerLocked = false }, isLive = isPlayingLive, autoplay = autoplay, hasSubtitles = playing.subtitles.any { it.isUrl && it.format?.suffix?.lowercase() in listOf("vtt", "ttml", "srt") }, subtitlesEnabled = selectedSubtitle != null, previewFrames = playing.previewFrames, segments = playing.streamSegments.orEmpty(), fullscreen = fullscreen, minimized = minimized, progress = collapse, travelDistance = dragTravel.coerceAtLeast(180.dp),
                    miniState = miniState, modifier = Modifier.offset(x = x, y = y).size(width, height), chatState = playingChatState,
                    onNext = nextItem(playing)?.let { next -> { openUrl(next.url) } },
                    onPrevious = queue.indexOfFirst { it.url == playing.url }.let { index -> queue.getOrNull(index - 1) }?.let { previous -> { openUrl(previous.url) } },
                    onAutoplay = { autoplay = !autoplay; watchPrefs.edit().putBoolean("autoplay", autoplay).apply() },
                    onSubtitles = {
                        val subtitle = if (selectedSubtitle != null) null else lastSubtitle ?: playing.subtitles.firstOrNull { it.isUrl && it.format?.suffix?.lowercase() in listOf("vtt", "ttml", "srt") }
                        val oldSubtitle = selectedSubtitle
                        selectedSubtitle = subtitle
                        try {
                            playQuality(playing, quality, selectedAudio, player.currentPosition.coerceAtLeast(0L), player.playWhenReady)
                            if (subtitle != null) lastSubtitle = subtitle
                        } catch (e: Exception) {
                            selectedSubtitle = oldSubtitle
                            android.widget.Toast.makeText(context, "Subtitle gagal: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBack = { back() }, onSettings = { qualityInitialPage = "menu"; showQuality = true }, onFullscreen = { setFullscreen(!fullscreen) },
                    onExpand = { minimized = false }, onClose = { closePlayer() },
                    onDrag = { if (fullscreen) dragFullscreenExit(it) else { dragProgress = it } },
                    onCancelDrag = { if (fullscreen) settleFullscreenExit(leave = false) else { dragProgress = null } },
                    onRelease = { value, velocity ->
                        if (fullscreen) settleFullscreenExit(leave = swipeCommits(value, velocity))
                        else {
                            minimized = swipeCommits(value, velocity)
                            dragProgress = null
                        }
                    })
                if (minimized && miniState.stashed != 0 && progress > .9f) MiniPlayerHandle(miniState.stashed, wide,
                    Modifier.align(if (miniState.stashed < 0) Alignment.TopStart else Alignment.TopEnd).offset(y = (miniPosition.y.dp + miniHeight / 2 - 54.dp).coerceAtLeast(0.dp))) { miniState.unstash() }
            }
        }
      }
    }
}

/** Video terbaru dari channel yang sedang ditonton; kosong sampai dimuat atau kalau channel gagal dibaca. */
@Composable private fun rememberChannelUploads(channelUrl: String?): List<StreamInfoItem> {
    var uploads by remember(channelUrl) { mutableStateOf<List<StreamInfoItem>>(emptyList()) }
    LaunchedEffect(channelUrl) {
        uploads = if (channelUrl.isNullOrBlank()) emptyList() else runCatching { VideoRepository.channelUploads(channelUrl) }.getOrDefault(emptyList())
    }
    return uploads
}

@Composable private fun WatchScreen(showDescription: Boolean, showComments: Boolean, showChat: Boolean, onClosePanel: () -> Unit, watchAlpha: Float, watchOffset: Float, info: StreamInfo, comments: CommentsState, chatState: LiveChatState, onComments: () -> Unit, onDescription: () -> Unit, onChat: () -> Unit, onLink: (String) -> Unit, onSeek: (Int) -> Unit, onShare: () -> Unit, onCopy: () -> Unit, player: ExoPlayer, hidden: Boolean, onFullscreen: () -> Unit, recommendations: List<StreamInfoItem>, channelVideos: List<StreamInfoItem>, error: String?, onBack: () -> Unit, onSettings: () -> Unit, onDownload: () -> Unit, onChannel: () -> Unit, onOpenChannel: (String) -> Unit, onVideo: (StreamInfoItem) -> Unit) {
    if (hidden) {
        return
    }
    val panel = when { showDescription -> "description"; showComments -> "comments"; showChat -> "chat"; else -> null }
    val isLive = info.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM || info.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
    BackHandler(panel != null) { onClosePanel() }
    @Composable fun Panel(kind: String, draggable: Boolean, modifier: Modifier, showCount: Boolean = true) {
        if (kind == "description") DescriptionPanel(info, draggable, onClosePanel, onLink, onSeek, onChannel, modifier)
        else if (kind == "chat") LiveChatPanel(chatState, draggable, onClosePanel, modifier)
        else CommentsPanel(comments, info, draggable, onClosePanel, onLink, onOpenChannel, modifier, showCount)
    }
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = watchAlpha; translationY = size.height * watchOffset }.background(Background)) {
        val wide = maxWidth >= 840.dp
        val related = recommendations.distinctBy { it.url }
        val extraChannelVideos = channelVideos.filter { item -> related.none { it.url == item.url } }
        if (wide) {
            val playerWidth = wideWatchPlayerWidth(maxWidth)
            Row(Modifier.fillMaxSize().padding(end = if (panel == null) 12.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(if (panel == null) 12.dp else 0.dp)) {
                Column(Modifier.width(playerWidth).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    VideoDetails(info, comments, chatState, error, onDownload, onChannel, onComments, onDescription, onShare, onCopy, onChat, isLive)
                }
                Box(Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.ui.graphics.RectangleShape)) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                        items(related, key = { it.url }) { item -> VideoCard(item, resolveAvatar = false) { onVideo(item) } }
                        if (extraChannelVideos.isNotEmpty()) {
                            item { Text("Dari ${info.uploaderName ?: "channel ini"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                            items(extraChannelVideos, key = { it.url }) { item -> VideoCard(item, resolveAvatar = false) { onVideo(item) } }
                        }
                    }
                    if (panel != null) Panel(panel, true, Modifier.fillMaxSize(), showCount = false)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item { Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
                item { VideoDetails(info, comments, chatState, error, onDownload, onChannel, onComments, onDescription, onShare, onCopy, onChat, isLive) }
                if (extraChannelVideos.isNotEmpty()) item {
                    Column {
                        Text("Dari ${info.uploaderName ?: "channel ini"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(extraChannelVideos) { item -> VideoCard(item, Modifier.width(240.dp)) { onVideo(item) } }
                        }
                    }
                }
                if (recommendations.isEmpty()) item { Text("Video terkait tidak tersedia.", color = Color.LightGray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                items(related) { item -> VideoCard(item, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { onVideo(item) } }
            }
            // Like YouTube, the panel slides up from the bottom and covers everything below the player, which keeps playing.
            androidx.compose.animation.AnimatedContent(targetState = panel, modifier = Modifier.fillMaxSize(), label = "watch panel", transitionSpec = {
                val enter = if (targetState != null) androidx.compose.animation.slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it } else androidx.compose.animation.EnterTransition.None
                val exit = if (targetState == null) androidx.compose.animation.slideOutVertically(tween(200)) { it } else androidx.compose.animation.fadeOut(tween(120))
                enter togetherWith exit
            }) { kind ->
                if (kind != null) Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    Panel(kind, true, Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}

@Composable private fun FloatingPlayer(
    player: ExoPlayer, title: String, locked: Boolean, onUnlock: () -> Unit, isLive: Boolean, autoplay: Boolean, hasSubtitles: Boolean, subtitlesEnabled: Boolean, previewFrames: List<org.schabi.newpipe.extractor.stream.Frameset>, segments: List<org.schabi.newpipe.extractor.stream.StreamSegment>,
    fullscreen: Boolean, minimized: Boolean, progress: Float, travelDistance: androidx.compose.ui.unit.Dp, miniState: MiniPlayerState,
    modifier: Modifier, chatState: LiveChatState? = null, onNext: (() -> Unit)?, onPrevious: (() -> Unit)?, onAutoplay: () -> Unit, onSubtitles: () -> Unit, onBack: () -> Unit, onSettings: () -> Unit, onFullscreen: () -> Unit,
    onExpand: () -> Unit, onClose: () -> Unit, onDrag: (Float) -> Unit, onCancelDrag: () -> Unit, onRelease: (Float, Float) -> Unit
) {
    val currentDrag by rememberUpdatedState(onDrag)
    val currentRelease by rememberUpdatedState(onRelease)
    val currentCancel by rememberUpdatedState(onCancelDrag)
    val currentTravelDistance by rememberUpdatedState(travelDistance)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val swipeModifier = when {
        // Mini player: drag anywhere (snaps to the nearest corner or stashes at a side edge), pinch to resize around the fingers.
        // Keyed on minimized: a pointerInput keeps its first lambda until its key changes, so without this the full-player swipe would keep running here.
        minimized -> Modifier.pointerInput(minimized) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, Offset.Zero) }
                var total = Offset.Zero
                var dragging = false
                var pinching = false
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        if (!pinching) { pinching = true; miniState.pinchStart() }
                        val zoom = event.calculateZoom()
                        val focus = event.calculateCentroid(useCurrent = true)
                        if (zoom.isFinite() && zoom > 0f && focus != Offset.Unspecified) miniState.pinch(zoom, focus / density)
                        event.changes.forEach { it.consume() }
                    } else if (!pinching) {
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.positionChange()
                        total += delta
                        tracker.addPosition(change.uptimeMillis, total)
                        if (!dragging && total.getDistance() > viewConfiguration.touchSlop) dragging = true
                        if (dragging) { change.consume(); miniState.drag(delta / density) }
                    }
                } while (event.changes.any { it.pressed })
                if (pinching) miniState.pinchEnd()
                else if (dragging) tracker.calculateVelocity().let { miniState.release(Offset(it.x, it.y) / density) }
            }
        }
        // Full player: swipe down to minimize, following YouTube's WatchTransitionBehavior. Only vertical movement counts and sideways
        // movement is ignored. A downward move past the slop starts the drag, measured from that point. Upward moves never start it.
        // In fullscreen the same swipe leaves fullscreen (see App's onRelease).
        else -> Modifier.pointerInput(minimized) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var anchor = down.position + origin
                val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, anchor) }
                var dragging = false
                var completed = false
                try {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val currentPosition = change.position + origin
                        tracker.addPosition(change.uptimeMillis, currentPosition)
                        // Checked on every move until the drag starts, so an earlier sideways or upward move does not block a later downward one.
                        if (!dragging && currentPosition.y - anchor.y > viewConfiguration.touchSlop) {
                            dragging = true
                            anchor = currentPosition
                        }
                        if (dragging) {
                            change.consume()
                            val fraction = ((currentPosition.y - anchor.y) / currentTravelDistance.toPx()).coerceIn(0f, 1f)
                            if (change.pressed) currentDrag(fraction) else {
                                completed = true
                                currentRelease(fraction, tracker.calculateVelocity().y / density)
                            }
                        }
                    } while (change.pressed)
                } finally {
                    if (dragging && !completed) currentCancel()
                }
            }
        }
    }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == androidx.media3.common.Player.STATE_BUFFERING) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) { buffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    var fullscreenChat by remember { mutableStateOf(true) }
    val corner = 12.dp * progress
    val hasChat = isLive || (chatState?.hasChat == true)
    BoxWithConstraints(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .shadow(6.dp * progress, RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner))
            .background(Color.Black)
    ) {
        val showDockedChat = fullscreen && hasChat && fullscreenChat && chatState != null && progress < .1f && !locked
        val chatWidth = (maxWidth * 0.28f).coerceIn(280.dp, 360.dp)
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (locked) Modifier else swipeModifier)
            ) {
                AndroidView(
                    factory = { context ->
                        android.view.LayoutInflater.from(context).inflate(R.layout.floating_player, null, false) as PlayerView
                    },
                    update = { view ->
                        view.player = player
                        view.useController = false
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize()
                )
                if (locked) {
                    var showUnlock by remember { mutableStateOf(true) }
                    LaunchedEffect(showUnlock) { if (showUnlock) { delay(3000); showUnlock = false } }
                    Box(Modifier.fillMaxSize().clickable { showUnlock = true }, contentAlignment = Alignment.Center) {
                        if (showUnlock) TextButton(onClick = onUnlock, colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = .75f), contentColor = Color.White)) { Text("🔒  Layar terkunci • Ketuk untuk membuka") }
                    }
                } else if (minimized && progress > .9f) {
                    MiniPlayerControls(player, playing, buffering, miniState.width.value.dp, onExpand = onExpand, onClose = onClose)
                } else if (progress < .1f) {
                    PlayerControlsOverlay(
                        player, playing, buffering, fullscreen, title, isLive, autoplay, hasSubtitles, subtitlesEnabled, previewFrames, segments,
                        onBack, onSettings, onAutoplay, onSubtitles, onFullscreen, onPrevious, onNext,
                        hasChat = hasChat,
                        showChatOverlay = fullscreenChat,
                        onToggleChatOverlay = { fullscreenChat = !fullscreenChat }
                    )
                }
            }
            if (showDockedChat && chatState != null) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF272727))
                )
                LiveChatPanel(
                    state = chatState,
                    draggable = false,
                    onDismiss = { fullscreenChat = false },
                    modifier = Modifier
                        .width(chatWidth)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable private fun HomeNavigation(selected: String, onSelect: (String) -> Unit) {
    HorizontalDivider(color = Color(0xFF303030))
    Row(Modifier.fillMaxWidth().height(56.dp).background(Background), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf("home" to "Beranda", "shorts" to "Shorts", "subscriptions" to "Subscription", "you" to "Anda").forEach { (key, label) ->
            Column(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(key) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val resource = when (key) {
                    "home" -> if (selected == key) R.drawable.yt_nav_home_active else R.drawable.yt_nav_home
                    "shorts" -> if (selected == key) R.drawable.yt_nav_shorts_active else R.drawable.yt_nav_shorts
                    "subscriptions" -> if (selected == key) R.drawable.yt_nav_subscriptions_active else R.drawable.yt_nav_subscriptions
                    else -> null
                }
                if (resource != null) Icon(painterResource(resource), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                else Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text(label, color = if (selected == key) Color.White else Color.LightGray, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable private fun VideoDetails(info: StreamInfo, comments: CommentsState, chatState: LiveChatState, error: String?, onDownload: () -> Unit, onChannel: () -> Unit, onComments: () -> Unit, onDescription: () -> Unit, onShare: () -> Unit, onCopy: () -> Unit, onChat: () -> Unit, isLive: Boolean) {
    val isPostLive = info.streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_STREAM ||
        info.streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_AUDIO_STREAM
    val showReplayChat = !isLive && (chatState.hasChat || (isPostLive && chatState.error == null))
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        DescriptionTeaser(info, onDescription)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val channelAvailable = !info.uploaderUrl.isNullOrBlank()
            Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(enabled = channelAvailable, onClick = onChannel).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(info.uploaderAvatars.lastOrNull()?.url, info.uploaderName, 36, modifier = if (isLive) Modifier.border(1.5.dp, Color(0xFFCC0000), CircleShape).padding(2.dp) else Modifier)
                Text(info.uploaderName ?: "Channel", modifier = Modifier.padding(start = 12.dp).weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isLive) LiveInlineBadge(Modifier.padding(start = 6.dp))
                info.uploaderSubscriberCount.takeIf { it >= 0 }?.let { Text(formatCount(it), color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.padding(start = 8.dp)) }
            }
            Text("Lihat channel", color = Color.Black, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable(enabled = channelAvailable, onClick = onChannel).padding(horizontal = 14.dp, vertical = 8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (info.likeCount >= 0) Row(Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(Surface).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ThumbUp, contentDescription = "Jumlah suka", modifier = Modifier.size(20.dp), tint = Color.White)
                Spacer(Modifier.width(7.dp)); Text(formatCount(info.likeCount), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            if (isLive) {
                WatchAction("chat", "Live chat", onChat)
            } else if (showReplayChat) {
                WatchAction("chat", "Replay chat", onChat)
            }
            WatchAction("share", "Bagikan", onShare)
            WatchAction("download", "Unduh", onDownload)
            WatchAction("copy", "Salin link", onCopy)
        }
        if (isLive) {
            Box(Modifier.padding(top = 16.dp)) {
                LiveChatTeaser(chatState, isLive = true, onClick = onChat)
            }
        } else {
            val openComments = { if (comments.error != null && comments.info == null) comments.retry(); onComments() }
            if (showReplayChat) {
                // Seperti aplikasi YouTube: satu kartu yang bisa digeser horizontal antara Komentar dan Rekaman live chat.
                val pagerState = androidx.compose.foundation.pager.rememberPagerState { 2 }
                Column(Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.pager.HorizontalPager(pagerState, Modifier.fillMaxWidth(), pageSpacing = 8.dp, verticalAlignment = Alignment.Top) { page ->
                        if (page == 0) CommentsTeaser(comments, onClick = openComments)
                        else LiveChatTeaser(chatState, isLive = false, onClick = onChat)
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(2) { i ->
                            Box(Modifier.size(6.dp).clip(CircleShape).background(if (pagerState.currentPage == i) Color.White else Color(0xFF606060)))
                        }
                    }
                }
            } else {
                Box(Modifier.padding(top = 16.dp)) { CommentsTeaser(comments, onClick = openComments) }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable private fun WatchAction(icon: String, label: String, onClick: () -> Unit) {
    Row(Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(Surface).clickable(onClick = onClick).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionGlyph(icon, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable private fun ActionGlyph(icon: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = 1.8.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(Color.White, Offset(w*x1,h*y1), Offset(w*x2,h*y2), strokeWidth = stroke, cap = StrokeCap.Round)
        when (icon) {
            "chat" -> {
                drawRoundRect(Color.White, topLeft = Offset(w*.12f, h*.16f), size = androidx.compose.ui.geometry.Size(w*.76f, h*.54f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w*.14f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                line(.28f, .70f, .20f, .88f); line(.20f, .88f, .44f, .70f)
                line(.28f, .36f, .72f, .36f); line(.28f, .50f, .58f, .50f)
            }
            "share" -> { line(.27f,.50f,.72f,.25f); line(.27f,.50f,.72f,.75f); listOf(Offset(.23f,.50f), Offset(.76f,.23f), Offset(.76f,.77f)).forEach { drawCircle(Color.White, radius = w*.10f, center = Offset(it.x*w,it.y*h)) } }
            "download" -> { line(.5f,.12f,.5f,.68f); line(.27f,.47f,.5f,.70f); line(.73f,.47f,.5f,.70f); line(.18f,.84f,.82f,.84f) }
            else -> { drawRoundRect(Color.White, topLeft = Offset(w*.31f,h*.28f), size = androidx.compose.ui.geometry.Size(w*.52f,h*.58f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w*.06f), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)); line(.19f,.66f,.19f,.14f); line(.19f,.14f,.69f,.14f) }
        }
    }
}

internal fun formatCount(count: Long): String = when {
    count >= 1_000_000_000 -> String.format(java.util.Locale("id", "ID"), "%.1f M", count / 1_000_000_000.0).replace(",0 ", " ")
    count >= 1_000_000 -> String.format(java.util.Locale("id", "ID"), "%.1f jt", count / 1_000_000.0).replace(",0 ", " ")
    count >= 10_000 -> "${count / 1_000} rb"
    count >= 1_000 -> String.format(java.util.Locale("id", "ID"), "%.1f rb", count / 1_000.0).replace(",0 ", " ")
    else -> count.toString()
}

internal fun StreamInfoItem.isOngoingLive(): Boolean {
    if (streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_STREAM ||
        streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_AUDIO_STREAM) {
        return false
    }
    if (streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
        streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM) {
        return true
    }
    if (duration <= 0) {
        val dateText = textualUploadDate?.lowercase().orEmpty()
        if (dateText.contains("live") || dateText.contains("watching") || dateText.contains("menonton") || dateText.contains("siaran langsung")) {
            return true
        }
    }
    return false
}

internal fun StreamInfo.isOngoingLive(): Boolean =
    streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
    streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM

@Composable
internal fun LiveThumbnailBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(6.dp)
            .background(Color(0xFFCC0000), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.yt_live_badge),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(11.dp)
        )
        Text(
            "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = Color.White
        )
    }
}

@Composable
internal fun LiveInlineBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFFCC0000), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White
        )
    }
}

@Composable private fun SearchWideVideoCard(item: StreamInfoItem, resolveAvatar: Boolean = true, onClick: () -> Unit) {
    val suppliedAvatar = item.uploaderAvatars.lastOrNull()?.url
    var avatar by remember(item.url, suppliedAvatar) { mutableStateOf(suppliedAvatar) }
    LaunchedEffect(item.uploaderUrl, suppliedAvatar, resolveAvatar) {
        if (resolveAvatar && suppliedAvatar.isNullOrBlank() && !item.uploaderUrl.isNullOrBlank()) {
            delay(350)
            avatar = VideoRepository.channelAvatar(item.uploaderUrl)
        }
    }
    val isLive = item.isOngoingLive()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(.43f).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
            AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (isLive) {
                LiveThumbnailBadge(Modifier.align(Alignment.BottomEnd))
            } else if (item.duration > 0) {
                DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
            }
        }
        Column(Modifier.weight(.57f).padding(top = 2.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (isLive) {
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LiveInlineBadge()
                    val viewers = if (item.viewCount > 0) "${formatCount(item.viewCount)} sedang menonton" else "Siaran langsung"
                    Text(viewers, color = Color(0xFFFF5252), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            } else {
                val metadata = listOfNotNull(item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} ditonton" }, item.textualUploadDate?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (metadata.isNotBlank()) Text(metadata, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(24.dp)
                        .then(if (isLive) Modifier.border(1.5.dp, Color(0xFFCC0000), CircleShape).padding(1.5.dp) else Modifier)
                        .clip(CircleShape)
                        .background(Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.uploaderName?.firstOrNull()?.uppercase() ?: "•", style = MaterialTheme.typography.labelSmall)
                    avatar?.let { AsyncImage(model = it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Text(item.uploaderName.orEmpty(), color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
            item.shortDescription?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

@Composable private fun SearchWidePlaylistCard(item: PlaylistInfoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.weight(.43f).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
            AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text(if (item.streamCount >= 0) "${item.streamCount} video · Playlist" else "Playlist", modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = .85f), RoundedCornerShape(4.dp)).padding(5.dp), style = MaterialTheme.typography.labelSmall)
        }
        Column(Modifier.weight(.57f).padding(top = 2.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Playlist", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Text("Lihat playlist", color = Color.LightGray, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable private fun CompactVideoCard(item: StreamInfoItem, onClick: () -> Unit) {
    val isLive = item.isOngoingLive()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Surface)) {
            AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (isLive) {
                LiveThumbnailBadge(Modifier.align(Alignment.BottomEnd))
            } else if (item.duration > 0) {
                DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (isLive) {
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LiveInlineBadge()
                    val viewers = if (item.viewCount > 0) "${formatCount(item.viewCount)} menonton" else "Live"
                    Text(viewers, color = Color(0xFFFF5252), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text(item.uploaderName ?: "", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable private fun MembersOnlyBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(6.dp)
            .background(Color(0xE6003314), RoundedCornerShape(4.dp))
            .border(0.5.dp, Color(0xFF0F9D58), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⭐ Khusus Member", color = Color(0xFFB9F6CA), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable private fun VideoCard(
    item: StreamInfoItem,
    modifier: Modifier = Modifier,
    resolveAvatar: Boolean = true,
    isMembership: Boolean = false,
    onNotInterested: (() -> Unit)? = null,
    onHideChannel: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val suppliedAvatar = item.uploaderAvatars.lastOrNull()?.url
    var avatar by remember(item.url, suppliedAvatar) { mutableStateOf(suppliedAvatar) }
    LaunchedEffect(item.uploaderUrl, suppliedAvatar, resolveAvatar) {
        if (resolveAvatar && suppliedAvatar.isNullOrBlank() && !item.uploaderUrl.isNullOrBlank()) {
            delay(350)
            avatar = VideoRepository.channelAvatar(item.uploaderUrl)
        }
    }
    val isLive = item.isOngoingLive()
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
            AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (isMembership) {
                MembersOnlyBadge(Modifier.align(Alignment.TopStart))
            }
            if (isLive) {
                LiveThumbnailBadge(Modifier.align(Alignment.BottomEnd))
            } else if (item.duration > 0) {
                DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .then(
                        if (isLive) Modifier
                            .border(1.5.dp, Color(0xFFCC0000), CircleShape)
                            .padding(2.dp)
                        else Modifier
                    )
                    .clip(CircleShape)
                    .background(Surface),
                contentAlignment = Alignment.Center
            ) {
                Text(item.uploaderName?.firstOrNull()?.uppercase() ?: "•", style = MaterialTheme.typography.titleSmall)
                avatar?.let { imageUrl ->
                    AsyncImage(model = imageUrl, contentDescription = "Foto channel ${item.uploaderName ?: ""}", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (isLive) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!item.uploaderName.isNullOrBlank()) {
                            Text(item.uploaderName.orEmpty(), color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("·", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                        LiveInlineBadge()
                        val viewers = if (item.viewCount > 0) "${formatCount(item.viewCount)} sedang menonton" else "Siaran langsung"
                        Text(viewers, color = Color(0xFFFF5252), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    val metadata = listOfNotNull(item.uploaderName?.takeIf { it.isNotBlank() }, item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} ditonton" }, item.textualUploadDate?.takeIf { it.isNotBlank() }).joinToString(" · ")
                    Text(metadata, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (onNotInterested != null) {
                var menuOpen by remember(item.url) { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "Opsi rekomendasi") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Tidak tertarik") }, onClick = { menuOpen = false; onNotInterested() })
                        if (onHideChannel != null) DropdownMenuItem(text = { Text("Jangan rekomendasikan channel") }, onClick = { menuOpen = false; onHideChannel() })
                    }
                }
            }
        }
    }
}

@Composable private fun DurationBadge(duration: Long, modifier: Modifier = Modifier) {
    val label = if (duration >= 3600) "%d:%02d:%02d".format(duration / 3600, duration / 60 % 60, duration % 60) else "%d:%02d".format(duration / 60, duration % 60)
    Text(label, modifier = modifier.padding(6.dp).background(Color.Black.copy(alpha = .85f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
}
private fun Modifier.clipCard(): Modifier = this.clip(RoundedCornerShape(16.dp)).background(Surface)

private data class DownloadChoice(val url: String, val height: Int, val audio: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DownloadDialog(info: StreamInfo, onDismiss: () -> Unit, download: (Int, Boolean) -> Unit) {
    var choice by remember(info.url) { mutableIntStateOf(0) }
    val choices = listOf(0 to "Kualitas terbaik", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p", -1 to "Audio saja")
    val listHeight = (LocalConfiguration.current.screenHeightDp - 310).coerceIn(72, 300).dp
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF272727), contentColor = Color.White, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Unduh video", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Tutup") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = info.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Background))
                Text(info.name, Modifier.weight(1f).padding(start = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text("Kualitas unduhan", Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = Color.LightGray)
            Column(Modifier.heightIn(max = listHeight).verticalScroll(rememberScrollState())) {
                choices.forEach { (height, label) ->
                    val description = if (height == -1) "MP3" else if (height == 0) "MP4 · terbaik yang tersedia" else "MP4 · maksimum $label"
                    PlayerChoiceRow(label, description, selected = choice == height, onClick = { choice = height })
                }
            }
            Text("Kualitas akhir mengikuti format yang tersedia.", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            Button(onClick = { download(choice.coerceAtLeast(0), choice == -1) }, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 20.dp).height(48.dp), shape = RoundedCornerShape(50), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                Text("Pilih lokasi dan unduh", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun PlayerChoiceRow(label: String, detail: String? = null, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(start = 20.dp, end = 20.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        }
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.LightGray), modifier = Modifier.size(40.dp))
    }
}

private fun audioTracks(info: StreamInfo): List<AudioStream> = VideoRepository.audios(info)
    .distinctBy { audio -> audio.audioTrackId?.takeIf { it.isNotBlank() }
        ?: listOf(audio.audioLocale?.toLanguageTag(), audio.audioTrackType?.name, audio.audioTrackName).joinToString("|") }

private fun audioLabel(audio: AudioStream): String {
    val name = audio.audioTrackName?.takeIf { it.isNotBlank() }
        ?: audio.audioLocale?.getDisplayName(java.util.Locale.forLanguageTag("id"))?.takeIf { it.isNotBlank() }
        ?: "Audio default"
    val type = when (audio.audioTrackType) {
        AudioTrackType.ORIGINAL -> "Asli"
        AudioTrackType.DUBBED -> "Dub"
        AudioTrackType.DESCRIPTIVE -> "Deskripsi audio"
        AudioTrackType.SECONDARY -> "Audio tambahan"
        else -> null
    }
    return if (type != null && !name.contains(type, ignoreCase = true)) "$name · $type" else name
}

private fun qualityOptions(info: StreamInfo): List<VideoStream> {
    val separate = if (VideoRepository.audios(info).isNotEmpty()) VideoRepository.videos(info) else emptyList()
    return (separate + VideoRepository.progressive(info))
        .sortedWith(compareByDescending<VideoStream> { it.height.takeIf { height -> height > 0 } ?: (it.resolution.filter(Char::isDigit).toIntOrNull() ?: 0) }.thenByDescending { it.fps })
        .distinctBy { "${it.resolution}-${it.fps}-${it.format?.name}" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun QualityDialog(info: StreamInfo, selected: VideoStream?, selectedAudio: AudioStream?, selectedSubtitle: SubtitlesStream?, autoplay: Boolean, maxHeight: Int, player: ExoPlayer, initialPage: String, fullscreen: Boolean, qualityMode: String, onQualityMode: (String) -> Unit, onLock: () -> Unit, onMaxHeight: (Int) -> Unit, onAutoplay: (Boolean) -> Unit, onSubtitle: (SubtitlesStream?) -> Unit, onDismiss: () -> Unit, onSelect: (VideoStream?) -> Unit, onSelectAudio: (AudioStream) -> Unit) {
    val options = remember(info) { qualityOptions(info) }
    var playerTracks by remember(player) { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { playerTracks = tracks }
        }
        player.addListener(listener)
        playerTracks = player.currentTracks
        onDispose { player.removeListener(listener) }
    }
    val hlsTracks = if (selected == null && !info.hlsUrl.isNullOrBlank()) {
        playerTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
            (0 until group.length).filter { group.isTrackSupported(it) && group.getTrackFormat(it).height > 0 }
                .map { index -> group to index }
        }.sortedByDescending { (group, index) -> group.getTrackFormat(index).height }
    } else emptyList()
    val qualityLabel = selected?.resolution ?: if (qualityMode == "advanced") {
        hlsTracks.firstOrNull { (group, index) -> group.isTrackSelected(index) }
            ?.let { (group, index) -> "${group.getTrackFormat(index).height}p" } ?: "Dipilih"
    } else "Otomatis"
    val tracks = remember(info) { audioTracks(info) }
    val canSelectAudio = remember(info) { VideoRepository.videos(info).isNotEmpty() }
    var page by remember(info.url, initialPage) { mutableStateOf(initialPage) }
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    val configuration = LocalConfiguration.current
    val listHeight = (configuration.screenHeightDp - 140).coerceIn(120, 380).dp
    val wide = configuration.screenWidthDp >= 840
    if (wide && page == "menu") {
        val density = LocalDensity.current
        val screenWidth = configuration.screenWidthDp.dp
        val playerWidth = if (fullscreen) screenWidth else wideWatchPlayerWidth(screenWidth)
        val menuWidth = minOf(380.dp, playerWidth - 24.dp)
        val offset = with(density) { IntOffset((playerWidth - menuWidth).roundToPx(), 64.dp.roundToPx()) }
        Popup(alignment = Alignment.TopStart, offset = offset, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
            Column(Modifier.width(menuWidth).clip(RoundedCornerShape(14.dp)).background(Color(0xFF252525)).padding(vertical = 8.dp)) {
                SettingsRow(R.drawable.yt_menu_quality, "Kualitas", qualityLabel) { page = "quality" }
                SettingsRow(R.drawable.yt_menu_speed, "Kecepatan pemutaran", if (speed == 1f) "1x" else "${speed}x") { page = "speed" }
                if (tracks.size > 1 && canSelectAudio) SettingsRow(R.drawable.yt_menu_audio, "Trek audio", selectedAudio?.let { audioLabel(it) } ?: "Default") { page = "audio" }
                if (info.subtitles.any { it.isUrl && it.format?.suffix?.lowercase() in listOf("vtt", "ttml", "srt") }) SettingsRow(R.drawable.yt_cc_active, "Subtitle", selectedSubtitle?.displayLanguageName ?: "Nonaktif") { page = "subtitle" }
                SettingsRow(R.drawable.yt_menu_lock, "Kunci layar", "", onClick = onLock)
                SettingsRow(R.drawable.yt_settings, "Lainnya", "") { page = "more" }
            }
        }
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF272727), contentColor = Color.White, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = if (page == "quality" || page == "advanced" || page == "menu") 20.dp else 4.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page != "menu" && page != "quality" && page != "advanced") IconButton(onClick = { page = "menu" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
                Text(when (page) { "quality", "advanced" -> "Kualitas untuk video ini  •  ${selected?.resolution ?: player.videoFormat?.height?.takeIf { it > 0 }?.let { "${it}p" } ?: "Otomatis"}"; "speed" -> "Kecepatan pemutaran"; "audio" -> "Trek audio"; "doubletap" -> "Ketuk dua kali untuk mencari"; "subtitle" -> "Subtitle"; "data" -> "Batas kualitas otomatis"; "more" -> "Lainnya"; else -> "Pengaturan" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (page != "quality" && page != "advanced") IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Tutup") }
            }
            if (page != "quality" && page != "advanced") HorizontalDivider(color = Color.White.copy(alpha = .08f))
            Column(Modifier.heightIn(max = listHeight).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                when (page) {
                    "menu" -> {
                        SettingsRow(R.drawable.yt_menu_quality, "Kualitas", qualityLabel) { page = "quality" }
                        SettingsRow(R.drawable.yt_menu_speed, "Kecepatan pemutaran", if (speed == 1f) "Normal" else "${speed}x") { page = "speed" }
                        if (tracks.size > 1 && canSelectAudio) SettingsRow(R.drawable.yt_menu_audio, "Trek audio", if (selected?.isVideoOnly == true) selectedAudio?.let { audioLabel(it) } ?: "Default" else "Audio bawaan") { page = "audio" }
                        if (info.subtitles.any { it.isUrl && it.format?.suffix?.lowercase() in listOf("vtt", "ttml", "srt") }) SettingsRow(R.drawable.yt_cc_active, "Subtitle", selectedSubtitle?.displayLanguageName ?: "Nonaktif") { page = "subtitle" }
                        SettingsRow(R.drawable.yt_menu_lock, "Kunci layar", "", onClick = onLock)
                        SettingsRow(R.drawable.yt_menu_quality, "Batas kualitas otomatis", "${maxHeight}p") { page = "data" }
                        SettingsRow(R.drawable.yt_menu_autoplay, "Putar otomatis", if (autoplay) "Aktif" else "Nonaktif") { onAutoplay(!autoplay) }
                        SettingsRow(R.drawable.yt_menu_speed, "Ketuk dua kali untuk mencari", "${PlayerSettings.doubleTapSeconds} detik") { page = "doubletap" }
                    }
                    "more" -> {
                        SettingsRow(R.drawable.yt_menu_quality, "Batas kualitas otomatis", "${maxHeight}p") { page = "data" }
                        SettingsRow(R.drawable.yt_menu_autoplay, "Putar otomatis", if (autoplay) "Aktif" else "Nonaktif") { onAutoplay(!autoplay) }
                        SettingsRow(R.drawable.yt_menu_speed, "Ketuk dua kali untuk mencari", "${PlayerSettings.doubleTapSeconds} detik") { page = "doubletap" }
                    }
                    "data" -> listOf(144, 240, 360, 480, 720, 1080, 2160).forEach { height ->
                        PlayerChoiceRow("${height}p", selected = height == maxHeight) { onMaxHeight(height); page = "menu" }
                    }
                    "subtitle" -> {
                        PlayerChoiceRow("Nonaktif", selected = selectedSubtitle == null) { onSubtitle(null); page = "menu" }
                        info.subtitles.filter { it.isUrl && it.format?.suffix?.lowercase() in listOf("vtt", "ttml", "srt") }.forEach { subtitle ->
                            PlayerChoiceRow(subtitle.displayLanguageName, if (subtitle.isAutoGenerated) "Otomatis" else null, selectedSubtitle == subtitle) { onSubtitle(subtitle); page = "menu" }
                        }
                    }
                    "doubletap" -> PlayerSettings.doubleTapChoices.forEach { seconds ->
                        PlayerChoiceRow("$seconds detik", selected = seconds == PlayerSettings.doubleTapSeconds) { PlayerSettings.updateDoubleTap(seconds); page = "menu" }
                    }
                    "quality" -> {
                        fun choosePreset(mode: String, height: Int) {
                            onQualityMode(mode)
                            if (!info.hlsUrl.isNullOrBlank()) {
                                onSelect(null)
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).setMaxVideoSize(Int.MAX_VALUE, height).build()
                            } else {
                                val target = options.firstOrNull { it.height in 1..height } ?: options.lastOrNull()
                                if (target != null) onSelect(target) else onDismiss()
                            }
                        }
                        QualityOption("Otomatis (direkomendasikan)", if (!info.hlsUrl.isNullOrBlank()) "Menyesuaikan dengan kondisi Anda" else "Memilih kualitas yang tersedia", qualityMode == "auto") { choosePreset("auto", maxHeight) }
                        QualityOption("Kualitas gambar lebih tinggi", "Menggunakan lebih banyak data", qualityMode == "high") { choosePreset("high", Int.MAX_VALUE) }
                        QualityOption("Penghemat data", "Kualitas gambar lebih rendah", qualityMode == "data") { choosePreset("data", 480) }
                        QualityOption("Lanjutan", "Pilih resolusi tertentu", false) { page = "advanced" }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Color.White.copy(alpha = .15f))
                        Text("Pilihan ini hanya berlaku untuk video saat ini.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                    }
                    "advanced" -> {
                        if (options.isEmpty() && hlsTracks.isEmpty()) Text("Resolusi khusus belum tersedia untuk stream ini.", Modifier.padding(20.dp), color = Color.LightGray)
                        hlsTracks.forEach { (group, index) ->
                            val format = group.getTrackFormat(index)
                            val detail = listOfNotNull(format.frameRate.takeIf { it > 30f }?.let { "${Math.round(it)} fps" }, format.sampleMimeType?.substringAfter('/')).joinToString(" · ")
                            QualityOption("${format.height}p", detail, qualityMode == "advanced" && group.isTrackSelected(index)) {
                                onQualityMode("advanced")
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(index))).build()
                                onDismiss()
                            }
                        }
                        options.forEach { stream ->
                            val detail = listOfNotNull(if (stream.fps > 30) "${stream.fps} fps" else null, stream.format?.name).joinToString(" · ")
                            QualityOption(stream.resolution, detail, qualityMode == "advanced" && stream == selected) { onQualityMode("advanced"); onSelect(stream) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = Color.White.copy(alpha = .15f))
                        Text("Pilihan ini hanya berlaku untuk video saat ini.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                    }
                    "audio" -> {
                        if (selected?.isVideoOnly != true) Text("Memilih trek akan memakai format video dengan audio terpisah.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        tracks.forEach { audio -> PlayerChoiceRow(audioLabel(audio), selected = selected?.isVideoOnly == true && audio == selectedAudio) { onSelectAudio(audio) } }
                    }
                    "speed" -> listOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { value ->
                        PlayerChoiceRow(if (value == 1f) "Normal" else "${value}x", selected = value == speed) { player.setPlaybackSpeed(value); speed = value; onDismiss() }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable private fun QualityOption(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 62.dp).padding(start = 20.dp, end = 20.dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.CenterStart) {
            if (selected) Icon(Icons.Default.Check, contentDescription = "Dipilih", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            if (!detail.isNullOrBlank()) Text(detail, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun SettingsRow(icon: Int, label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) { Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White)) }
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
        Image(painterResource(R.drawable.yt_chevron_right), contentDescription = null, modifier = Modifier.padding(start = 4.dp).size(24.dp), colorFilter = ColorFilter.tint(Color.LightGray))
    }
}

@Composable private fun ChannelHeader(page: ChannelPage, selectedTab: String, isLive: Boolean = false, onTab: (String) -> Unit) {
    val tabNames = mapOf(
        ChannelTabs.VIDEOS to "Video",
        ChannelTabs.SHORTS to "Shorts",
        ChannelTabs.LIVESTREAMS to "Live",
        ChannelTabs.PLAYLISTS to "Playlist",
        ChannelTabs.CHANNELS to "Channel",
        ChannelTabs.TRACKS to "Lagu",
        ChannelTabs.ALBUMS to "Album",
        ChannelTabs.LIKES to "Disukai",
        VideoRepository.TAB_MEMBERSHIP to "Membership"
    )
    val baseTabs = page.info.tabs.mapNotNull { it.contentFilters.firstOrNull { filter -> filter in tabNames } }.distinct()
    val tabs = if (page.hasMembership && VideoRepository.TAB_MEMBERSHIP !in baseTabs) {
        val list = baseTabs.toMutableList()
        val insertPos = if (list.contains(ChannelTabs.VIDEOS)) list.indexOf(ChannelTabs.VIDEOS) + 1 else 0
        list.add(insertPos.coerceAtMost(list.size), VideoRepository.TAB_MEMBERSHIP)
        list
    } else baseTabs
    Column(Modifier.fillMaxWidth()) {
        val banner = page.info.banners.lastOrNull()?.url
        if (banner != null) AsyncImage(model = banner, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 180.dp).aspectRatio(5f).clip(RoundedCornerShape(12.dp)).background(Surface))
        Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomCenter) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .then(
                            if (isLive) Modifier
                                .border(width = 2.5.dp, color = Color(0xFFCC0000), shape = CircleShape)
                                .padding(3.dp)
                            else Modifier
                        )
                        .clip(CircleShape)
                        .background(Surface)
                ) {
                    AsyncImage(
                        model = page.info.avatars.lastOrNull()?.url,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .offset(y = 6.dp)
                            .background(Color(0xFFCC0000), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "LIVE",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(page.info.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (isLive) {
                        LiveInlineBadge()
                    }
                }
                if (page.info.subscriberCount >= 0) {
                    Text("${formatCount(page.info.subscriberCount)} subscriber", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { tab ->
                val isMembershipTab = tab == VideoRepository.TAB_MEMBERSHIP
                val isSelected = tab == selectedTab
                val tabColor = when {
                    isSelected && isMembershipTab -> Color(0xFF0F9D58)
                    isSelected -> Color.White
                    isMembershipTab -> Color(0xFF81C784)
                    else -> Color.LightGray
                }
                Column(Modifier.clickable { onTab(tab) }.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            tabNames[tab] ?: tab,
                            color = tabColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (tab == ChannelTabs.LIVESTREAMS && isLive) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFCC0000)))
                        } else if (isMembershipTab) {
                            Text("⭐", fontSize = 11.sp)
                        }
                    }
                    Box(Modifier.width(32.dp).height(2.dp).background(if (isSelected) (if (isMembershipTab) Color(0xFF0F9D58) else Color.White) else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = Surface)
    }
}

@Composable private fun PlaylistCard(item: PlaylistInfoItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
            AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text("${if (item.streamCount >= 0) item.streamCount else ""} video · Playlist", modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = .85f), RoundedCornerShape(4.dp)).padding(5.dp), style = MaterialTheme.typography.labelSmall)
        }
        Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Text("Lihat playlist", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun SearchChannelCard(item: ChannelInfoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(50)).background(Surface))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (item.subscriberCount >= 0) "${formatCount(item.subscriberCount)} subscriber" else "Channel", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        Text("Lihat", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundedCornerShape(50)).background(Surface).padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable private fun ChannelCard(item: ChannelInfoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clipCard().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(Color.DarkGray))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (item.subscriberCount >= 0) "Channel · ${item.subscriberCount} subscriber" else "Channel", color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = Accent)
    }
}
