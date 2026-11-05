package id.flufclip.player

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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.media3.common.VideoSize
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
import org.schabi.newpipe.extractor.stream.DeliveryMethod

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val searchHistory = remember { SearchHistoryStore(context.getSharedPreferences("search_history", Context.MODE_PRIVATE)) }
    val searchState = remember { SearchState() }
    val homeCachePrefs = remember(YouTubeAuth.sessionScopeId) { context.getSharedPreferences("home_feed_cache_v2_${YouTubeAuth.sessionScopeId}", Context.MODE_PRIVATE) }
    val relatedCachePrefs = remember(YouTubeAuth.sessionScopeId) { context.getSharedPreferences("home_related_cache_v2_${YouTubeAuth.sessionScopeId}", Context.MODE_PRIVATE) }
    var home by remember(YouTubeAuth.sessionScopeId) { mutableStateOf(HomeFeedCache.read(homeCachePrefs)) }
    var lastAccountState by remember { mutableStateOf(YouTubeAuth.isLoggedIn) }
    var homeSessionRevision by remember { mutableIntStateOf(YouTubeAuth.sessionRevision) }
    var homeLoadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var addLoginAccount by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        YouTubeLoginDialog(onDismiss = { showLoginDialog = false; addLoginAccount = false }, addAccount = addLoginAccount)
    }
    if (showAccountDialog) {
        YouTubeAccountDialog(
            onDismiss = { showAccountDialog = false },
            onAddAccount = { showAccountDialog = false; addLoginAccount = true; showLoginDialog = true },
            onLogout = {
                YouTubeAuth.logout(context)
                showAccountDialog = false
            }
        )
    }
    LaunchedEffect(searchState.active, searchState.query) {
        searchState.suggestions = emptyList()
        val term = searchState.query.trim()
        if (searchState.active && term.isNotBlank()) {
            delay(350)
            try { searchState.suggestions = VideoRepository.suggestions(term).distinct().take(10) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { searchState.suggestions = emptyList() }
        }
    }
    val browse = remember { BrowseState() }
    var accountPlaylist by remember(YouTubeAuth.sessionRevision) { mutableStateOf<AccountPlaylist?>(null) }
    var savePlaylistVideoId by remember(YouTubeAuth.sessionRevision) { mutableStateOf<String?>(null) }
    savePlaylistVideoId?.let { id -> SavePlaylistDialog(id, onDismiss = { savePlaylistVideoId = null }, onSelectedPlaylists = { Engagement.setPlaylists(id, it) }) }
    var selected by remember { mutableStateOf<StreamInfo?>(null) }
    var pendingVideo by remember { mutableStateOf<PendingVideo?>(null) }
    var openJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var selectedSubtitle by remember { mutableStateOf<SubtitlesStream?>(null) }
    var lastSubtitle by remember { mutableStateOf<SubtitlesStream?>(null) }
    var attachedSubtitle by remember { mutableStateOf<SubtitlesStream?>(null) }
    var attachedSubtitleVideo by remember { mutableStateOf<String?>(null) }
    val watchPrefs = remember { context.getSharedPreferences("watch_history", Context.MODE_PRIVATE) }
    var watchHistory by remember { mutableStateOf(WatchHistory.read(watchPrefs)) }
    val accountHistorySession = YouTubeAuth.sessionRevision
    val accountHistoryLoggedIn = YouTubeAuth.isLoggedIn
    val accountHistory = remember(accountHistorySession, accountHistoryLoggedIn) { AccountHistoryState(accountHistoryLoggedIn) }
    val hidden = remember { HiddenFeedItems(context.getSharedPreferences("home_recommendations", Context.MODE_PRIVATE)) }
    var showHistory by remember { mutableStateOf(false) }
    var mainTab by remember { mutableStateOf("home") }
    LaunchedEffect(accountHistorySession, accountHistoryLoggedIn, mainTab, showHistory, accountHistory.request) {
        if (!accountHistoryLoggedIn || (mainTab != "you" && !showHistory)) return@LaunchedEffect
        accountHistory.loading = true
        accountHistory.error = null
        try {
            val page = AccountHistory.load()
            if (YouTubeAuth.sessionRevision == accountHistorySession && YouTubeAuth.isLoggedIn) {
                accountHistory.entries = WatchHistory.enrichAccountEntries(page.entries, watchPrefs)
                accountHistory.continuation = page.continuation
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            if (YouTubeAuth.sessionRevision == accountHistorySession) accountHistory.error = "Couldn't load account history. Check your connection or sign in again."
        } finally {
            if (YouTubeAuth.sessionRevision == accountHistorySession) accountHistory.loading = false
        }
    }
    LaunchedEffect(accountHistorySession, accountHistory.request, accountHistory.nextRequest) {
        val token = accountHistory.continuation
        if (accountHistory.nextRequest == 0 || token == null || !accountHistoryLoggedIn || accountHistory.loading) return@LaunchedEffect
        accountHistory.loading = true
        try {
            val page = AccountHistory.load(token)
            if (YouTubeAuth.sessionRevision == accountHistorySession && accountHistory.continuation == token) {
                accountHistory.entries = WatchHistory.enrichAccountEntries(accountHistory.entries + page.entries, watchPrefs)
                accountHistory.continuation = page.continuation?.takeUnless { it == token }
                accountHistory.error = null
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { if (YouTubeAuth.sessionRevision == accountHistorySession) accountHistory.error = "Couldn't load the next page. Try again." }
        finally { if (YouTubeAuth.sessionRevision == accountHistorySession) accountHistory.loading = false }
    }
    // Enrich recent history items with playlist associations from their channels' playlist shelves.
    LaunchedEffect(accountHistory.entries.size, watchHistory.size, home.size) {
        if (accountHistory.entries.isNotEmpty()) {
            val reEnriched = WatchHistory.enrichAccountEntries(accountHistory.entries, watchPrefs)
            if (reEnriched != accountHistory.entries) accountHistory.entries = reEnriched
        }
        val refreshedLocal = WatchHistory.read(watchPrefs)
        if (refreshedLocal != watchHistory) watchHistory = refreshedLocal

        val candidateChannels = (accountHistory.entries.take(12).map { it.video } + watchHistory.take(12))
            .filter { !it.isShort && it.playlistId.isNullOrBlank() && !it.channelUrl.isNullOrBlank() }
            .mapNotNull { HomeFeed.channelIdOf(it.channelUrl) }
            .distinct()
            .take(4)
        if (candidateChannels.isEmpty()) return@LaunchedEffect
        var discoveredAny = false
        for (cid in candidateChannels) {
            if (HomeFeed.discoverChannelPlaylists(cid, watchPrefs)) {
                discoveredAny = true
            }
        }
        if (discoveredAny) {
            watchHistory = WatchHistory.read(watchPrefs)
            if (accountHistory.entries.isNotEmpty()) {
                accountHistory.entries = WatchHistory.enrichAccountEntries(accountHistory.entries, watchPrefs)
            }
        }
    }
    val subs = remember { SubscriptionsState(scope) }
    LaunchedEffect(mainTab, YouTubeAuth.sessionRevision, YouTubeAuth.isLoggedIn) {
        if (mainTab == "subscriptions" && YouTubeAuth.isLoggedIn) subs.onTabOpened()
    }
    val shorts = remember { ShortsFeedState() }
    val shortsResolver = remember(YouTubeAuth.sessionRevision) { ShortsResolver(scope, context) }
    var autoplay by remember { mutableStateOf(watchPrefs.getBoolean("autoplay", true)) }
    var maxVideoHeight by remember { mutableIntStateOf(watchPrefs.getInt("max_video_height", 720)) }
    var queue by remember { mutableStateOf<List<StreamInfoItem>>(emptyList()) }
    var activePlaylistId by remember { mutableStateOf<String?>(null) }
    var activePlaylistTitle by remember { mutableStateOf<String?>(null) }
    var activePlaylistCount by remember { mutableStateOf<String?>(null) }
    var playlistQueueJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var shortsMode by remember { mutableStateOf(false) }
    var minimized by remember { mutableStateOf(false) }
    val miniState = remember { MiniPlayerState(scope) }
    // Keep the same mini-player position when a pending thumbnail becomes the actual video.
    LaunchedEffect(minimized) { if (minimized) miniState.show() }
    var fullscreen by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf<Float?>(null) }
    var category by remember { mutableStateOf("All") }
    var categoryItems by remember { mutableStateOf<List<InfoItem>>(emptyList()) }
    var homePage by remember { mutableStateOf<SearchPage?>(null) }
    var homeContinuationToken by remember { mutableStateOf<String?>(null) }
    var categoryPage by remember { mutableStateOf<SearchPage?>(null) }
    /** Halaman berikutnya feed live untuk chip "Live"; chip lain memakai [categoryPage]. */
    var liveNext by remember { mutableStateOf<Page?>(null) }
    var homeChips by remember { mutableStateOf<List<HomeChip>>(emptyList()) }
    var selectedChip by remember { mutableStateOf<HomeChip?>(null) }
    var categoryContinuation by remember { mutableStateOf<String?>(null) }
    var homeChipTitle by remember { mutableStateOf("All") }
    val fallbackChips = remember { listOf("All", "Music", "Gaming", "News", "Movies", "Sports", "Live").map { HomeChip(it) } }
    val visibleChips = if (homeChips.isNotEmpty()) homeChips else fallbackChips
    fun mergeHomeChips(updated: List<HomeChip>) {
        if (updated.isEmpty()) return
        val merged = LinkedHashMap<String, HomeChip>()
        homeChips.forEach { merged[it.title] = it }
        updated.forEach { merged[it.title] = it }
        homeChips = merged.values.toList()
    }
    var feedLoading by remember { mutableStateOf(false) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var feedEnd by remember { mutableStateOf(false) }
    var topicIndex by remember { mutableIntStateOf(0) }
    val topics = remember { listOf("video terbaru", "musik terbaru", "game terbaru", "sains teknologi terbaru", "film terbaru", "olahraga terbaru") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDownloads by remember { mutableStateOf(false) }
    var requestedDownload by remember { mutableStateOf<String?>(null) }
    var pendingDownload by remember { mutableStateOf<DownloadChoice?>(null) }
    val downloadState by DownloadStatus.state.collectAsState()
    val launchDownload: (DownloadChoice) -> Unit = { choice ->
        try {
            val intent = Intent(context, DownloadService::class.java)
                .putExtra("url", choice.url).putExtra("title", choice.video.title).putExtra("video", choice.video.json().toString())
                .putExtra("height", choice.height).putExtra("audio", choice.audio)
            ContextCompat.startForegroundService(context, intent)
            (context as? MainActivity)?.pipPlayer?.pause()
            SavedDownloads.open(context)
        } catch (e: Exception) {
            DownloadStatus.state.value = DownloadState(message = "Cannot start download: ${e.message}")
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val choice = pendingDownload
        pendingDownload = null
        if (granted && choice != null) launchDownload(choice)
        else if (choice != null) android.widget.Toast.makeText(context, "Storage permission is required to save to the Download folder", android.widget.Toast.LENGTH_LONG).show()
    }
    // Status suka/simpan/subscribe dimiliki Engagement dan dipisahkan menurut sesi akun, bukan map lokal per layar.
    val rateWatch: (StreamInfo, Int) -> Unit = { target, direction ->
        if (!YouTubeAuth.isLoggedIn) { android.widget.Toast.makeText(context, "Sign in to rate videos", android.widget.Toast.LENGTH_SHORT).show(); showLoginDialog = true }
        else {
            val videoId = target.id.ifBlank { videoKey(target.url) }
            val channelId = Regex("/channel/(UC[\\w-]{22})").find(target.uploaderUrl.orEmpty())?.groupValues?.get(1)
            val first = Engagement.toggleRating(videoId, direction)
            if (first != null) scope.launch {
                var request: RatingRequest? = first
                try {
                    while (request != null) {
                        val current = request ?: break
                        val result = sendRating(current.videoId, ratingAction(current.target), current.revision)
                        Engagement.completeRating(current, result)
                        val message = result.messageOrNull()
                        if (message != null) {
                            request = null
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            if (result is MutationResult.Uncertain) scope.reconcileEngagement(videoId, channelId, current.revision)
                            break
                        }
                        request = Engagement.followUpRating(videoId)
                    }
                } finally {
                    // Coroutine dapat dibatalkan saat layar ditutup; jangan biarkan status pending selamanya.
                    request?.let(Engagement::abandonRating)
                }
            }
        }
    }
    val saveWatch: (StreamInfo) -> Unit = { target ->
        val key = videoKey(target.url)
        if (!YouTubeAuth.isLoggedIn) { android.widget.Toast.makeText(context, "Sign in to save videos", android.widget.Toast.LENGTH_SHORT).show(); showLoginDialog = true }
        else savePlaylistVideoId = target.id.ifBlank { key }
    }
    val subscribeChannelId: (String, Boolean, String?) -> Unit = { channelId, subscribe, videoIdHint ->
        if (!YouTubeAuth.isLoggedIn) { android.widget.Toast.makeText(context, "Sign in to subscribe", android.widget.Toast.LENGTH_SHORT).show(); showLoginDialog = true }
        else if (channelId.isBlank()) android.widget.Toast.makeText(context, "Unrecognized channel", android.widget.Toast.LENGTH_SHORT).show()
        else {
            val first = Engagement.setSubscription(channelId, subscribe)
            if (first != null) scope.launch {
                var request: SubscriptionRequest? = first
                try {
                    while (request != null) {
                        val current = request ?: break
                        val result = sendSubscription(current.channelId, current.target, current.revision)
                        Engagement.completeSubscription(current, result)
                        val message = result.messageOrNull()
                        if (message != null) {
                            request = null
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            if (result is MutationResult.Uncertain && videoIdHint != null) scope.reconcileEngagement(videoIdHint, channelId, current.revision)
                            break
                        }
                        request = Engagement.followUpSubscription(channelId)
                    }
                } finally {
                    request?.let(Engagement::abandonSubscription)
                }
            }
        }
    }
    val subscribeWatch: (StreamInfo, Boolean) -> Unit = { target, subscribe ->
        val channelId = Regex("/channel/(UC[\\w-]{22})").find(target.uploaderUrl.orEmpty())?.groupValues?.get(1)
        val videoId = target.id.ifBlank { videoKey(target.url) }
        if (channelId == null) android.widget.Toast.makeText(context, "Unrecognized channel", android.widget.Toast.LENGTH_SHORT).show()
        else subscribeChannelId(channelId, subscribe, videoId)
    }
    val setChannelNotificationPref: (String, NotificationPreference) -> Unit = { channelId, pref ->
        if (!YouTubeAuth.isLoggedIn) {
            android.widget.Toast.makeText(context, "Sign in to change notifications", android.widget.Toast.LENGTH_SHORT).show()
            showLoginDialog = true
        } else if (channelId.isBlank()) {
            android.widget.Toast.makeText(context, "Unrecognized channel", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val request = Engagement.setNotificationPreference(channelId, pref)
            if (request != null) scope.launch {
                val result = sendNotificationPreference(request.channelId, request.target, request.params, request.revision)
                Engagement.completeNotificationPreference(request, result)
                val message = result.messageOrNull()
                if (message != null) {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val startDownload: (StreamInfo, Int, Boolean) -> Unit = { target, height, audio ->
        if (downloadState.running) {
            android.widget.Toast.makeText(context, "Wait for the current download to finish", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            val choice = DownloadChoice(target.url, height, audio, DownloadVideo(target.url, target.name, target.uploaderName.orEmpty(), target.uploaderUrl.orEmpty(), target.thumbnails.lastOrNull()?.url.orEmpty(), target.uploaderAvatars.lastOrNull()?.url.orEmpty(), target.duration.coerceAtLeast(0) * 1000, target.viewCount, target.likeCount, target.uploaderSubscriberCount, target.description?.content.orEmpty()))
            if (android.os.Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingDownload = choice
                storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else launchDownload(choice)
        }
    }
    var showQuality by remember { mutableStateOf(false) }
    var qualityInitialPage by remember { mutableStateOf("menu") }
    var playerLocked by remember { mutableStateOf(false) }
    var qualityMode by remember { mutableStateOf("auto") }
    var quality by remember { mutableStateOf<VideoStream?>(null) }
    var selectedAudio by remember { mutableStateOf<AudioStream?>(null) }
    var forceDirectPlayback by remember { mutableStateOf(false) }
    var manualAudioOverride by remember { mutableStateOf(false) }
    var attemptedAudioFallback by remember { mutableStateOf<String?>(null) }
    /** Panel "Bab" terbuka: di halaman tonton sebagai panel bawah, di layar penuh menempel di samping seperti komentar. */
    var chaptersOpen by remember { mutableStateOf(false) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addAnalyticsListener(object : AnalyticsListener {
                override fun onPlaybackStateChanged(eventTime: AnalyticsListener.EventTime, state: Int) {
                    android.util.Log.d("FlufPlayback", "state=$state pos=$currentPosition buffer=$bufferedPosition total=$totalBufferedDuration loading=$isLoading live=$isCurrentMediaItemLive")
                }
                override fun onLoadError(eventTime: AnalyticsListener.EventTime,
                    loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                    mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData,
                    error: java.io.IOException, wasCanceled: Boolean) {
                    val status = (error as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                    android.util.Log.w("FlufPlayback", "loadError id=${loadEventInfo.loadTaskId} type=${mediaLoadData.dataType} track=${mediaLoadData.trackType} cause=${error.javaClass.simpleName} http=$status canceled=$wasCanceled")
                }
                override fun onDownstreamFormatChanged(eventTime: AnalyticsListener.EventTime,
                    mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData) {
                    val format = mediaLoadData.trackFormat ?: return
                    if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO || mediaLoadData.trackType == C.TRACK_TYPE_AUDIO) {
                        android.util.Log.d("FlufPlayback", "track type=${mediaLoadData.trackType} mime=${format.sampleMimeType} height=${format.height} bitrate=${format.bitrate}")
                    }
                }
                override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime,
                    loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                    mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData) {
                    android.util.Log.d("FlufPlayback", "loadDone id=${loadEventInfo.loadTaskId} type=${mediaLoadData.dataType} track=${mediaLoadData.trackType} bytes=${loadEventInfo.bytesLoaded} elapsedMs=${loadEventInfo.loadDurationMs}")
                }
                override fun onLoadStarted(eventTime: AnalyticsListener.EventTime,
                    loadEventInfo: androidx.media3.exoplayer.source.LoadEventInfo,
                    mediaLoadData: androidx.media3.exoplayer.source.MediaLoadData) {
                    val host = loadEventInfo.uri.host.orEmpty()
                    val server = when {
                        host == "googlevideo.com" || host.endsWith(".googlevideo.com") -> "googlevideo"
                        host == "youtube.com" || host.endsWith(".youtube.com") -> "youtube"
                        else -> "other"
                    }
                    android.util.Log.d("FlufPlayback", "loadStart id=${loadEventInfo.loadTaskId} type=${mediaLoadData.dataType} track=${mediaLoadData.trackType} server=$server")
                }
            })
        }
    }
    var activePlaybackTracks by remember(player) { mutableStateOf(player.currentTracks) }
    var activePlaybackState by remember(player) { mutableIntStateOf(player.playbackState) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { activePlaybackTracks = tracks }
            override fun onPlaybackStateChanged(state: Int) { activePlaybackState = state }
        }
        player.addListener(listener)
        activePlaybackTracks = player.currentTracks
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    // PiP: eligibilitas dinilai saat pengguna meninggalkan app (lambda membaca state terbaru), rasio mengikuti ukuran video.
    DisposableEffect(player) {
        val activity = context as? MainActivity
        activity?.pipPlayer = player
        onlinePlayerForDownloads = java.lang.ref.WeakReference(player)
        activity?.pipEligible = {
            val state = player.playbackState
            selected != null && player.playWhenReady && (state == Player.STATE_BUFFERING || state == Player.STATE_READY)
        }
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) { activity?.refreshPipParams() }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            activity?.pipEligible = { false }
            activity?.pipPlayer = null
            if (onlinePlayerForDownloads.get() === player) onlinePlayerForDownloads.clear()
        }
    }
    // Seperti YouTube 21.43.523 (apby.aA -> apcs.o -> View.setKeepScreenOn): layar ditahan selama niat main aktif
    // (playWhenReady) dan player sedang buffering/siap; dilepas saat pause, selesai, error, idle, atau player dibuang.
    DisposableEffect(player) {
        val activity = context as? MainActivity
        fun sync() {
            val state = player.playbackState
            activity?.keepScreenOn(player.playWhenReady && (state == Player.STATE_BUFFERING || state == Player.STATE_READY))
        }
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = sync()
            override fun onPlaybackStateChanged(playbackState: Int) = sync()
            override fun onPlayerError(error: PlaybackException) = sync()
        }
        player.addListener(listener)
        sync()
        onDispose { player.removeListener(listener); activity?.keepScreenOn(false) }
    }
    // Jangan `return` dini saat PiP: itu membuang semua state dan efek di bawahnya (muat ulang beranda, daftar scroll,
    // pendengar autoplay/Shorts, simpan posisi) dan menyetelnya dari nol setelah kembali. Cabang PiP ada di tingkat UI, di akhir App().
    val inPip = (context as? MainActivity)?.inPip == true
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
        context.startActivity(Intent.createChooser(intent, "Share video"))
    }
    fun copyVideoLink(info: StreamInfo) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Video link", info.url))
        android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
    }
    fun clipVideoLink(info: StreamInfo) {
        val sec = (player.currentPosition / 1000L).coerceAtLeast(0L)
        val m = sec / 60
        val s = sec % 60
        val timeLabel = "%02d:%02d".format(m, s)
        val clipUrl = if (info.url.contains("?")) "${info.url}&t=${sec}s" else "${info.url}?t=${sec}s"
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Video link at this time", clipUrl))
        android.widget.Toast.makeText(context, "Link copied at $timeLabel", android.widget.Toast.LENGTH_SHORT).show()
    }
    fun savePosition() {
        selected?.let { info ->
            val pid = if (!shortsMode) activePlaylistId?.takeIf { it.isNotBlank() } else null
            val ptitle = if (pid != null) activePlaylistTitle?.takeIf { it.isNotBlank() } else null
            val pcount = if (pid != null) {
                activePlaylistCount?.takeIf { it.isNotBlank() }
                    ?: queue.size.takeIf { it > 0 }?.let { "$it videos" }
            } else null
            WatchHistory.save(
                watchPrefs,
                info.url,
                info.name,
                info.uploaderName.orEmpty(),
                info.thumbnails.lastOrNull()?.url.orEmpty(),
                player.currentPosition.coerceAtLeast(0),
                player.duration,
                short = shortsMode,
                channelUrl = info.uploaderUrl,
                playlistId = pid,
                playlistTitle = ptitle,
                playlistVideoCount = pcount
            )
            watchHistory = WatchHistory.read(watchPrefs)
            if (pid != null && accountHistory.entries.isNotEmpty()) {
                accountHistory.entries = WatchHistory.enrichAccountEntries(accountHistory.entries, watchPrefs)
            }
        }
    }
    fun closePlayer() {
        openJob?.cancel(); openJob = null; playlistQueueJob?.cancel(); playlistQueueJob = null; pendingVideo = null; dragProgress = null
        PlaybackTracker.stop(player.currentPosition / 1000L)
        savePosition()
        shortsMode = false
        activePlaylistId = null
        activePlaylistTitle = null
        activePlaylistCount = null
        player.stop()
        player.clearMediaItems()
        selected = null
        minimized = false
        quality = null
        selectedAudio = null
        forceDirectPlayback = false
        manualAudioOverride = false
        attemptedAudioFallback = null
        chaptersOpen = false
    }
    fun back() {
        when {
            fullscreen -> setFullscreen(false)
            shortsMode && !minimized -> { closePlayer(); if (shorts.tabQueue && mainTab == "shorts") mainTab = "home" }
            searchState.active -> { searchState.active = false; keyboard?.hide() }
            showHistory -> showHistory = false
            selected != null && !minimized -> minimized = true
            accountPlaylist != null -> accountPlaylist = null
            browse.playlist != null -> browse.playlist = null
            browse.channel != null -> { browse.channel = null; if (mainTab == "shorts" && shorts.tabQueue && queue.isNotEmpty()) shortsMode = true }
            searchState.mode -> { searchState.mode = false; searchState.query = "" }
            mainTab != "home" -> mainTab = "home"
        }
        error = null
    }
    fun playQuality(info: StreamInfo, stream: VideoStream?, audio: AudioStream?, position: Long, play: Boolean, forceDirect: Boolean = false) {
        OfflinePlaybackBridge.close()
        val isLive = info.isOngoingLive()
        val dashUrl = info.dashMpdUrl?.takeIf { it.isNotBlank() }
        val hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() }
            ?: stream?.takeIf { it.deliveryMethod == DeliveryMethod.HLS }?.content
        val manifest = if (forceDirect) PlaybackManifest.NONE else playbackManifest(isLive, hlsUrl, dashUrl, VideoRepository.preferLiveDash(info))
        val adaptive = manifest != PlaybackManifest.NONE
        android.util.Log.d("FlufMedia", "type=${info.streamType} dur=${info.duration} hls=${hlsUrl?.length ?: -1} dash=${dashUrl?.length ?: -1} manifest=$manifest live=$isLive")
        val wantedHeight = when {
            adaptive -> stream?.height?.takeIf { it > 0 } ?: maxVideoHeight
            stream == null -> maxVideoHeight
            else -> Int.MAX_VALUE
        }
        val parameters = player.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, wantedHeight)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, selectedSubtitle == null)
        if (stream != null) parameters.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        player.trackSelectionParameters = parameters.build()
        val factory = youTubeMediaSourceFactory(context)
        val mediaKey = videoKey(info.url)
        fun media(url: String, manifestMime: String? = null): MediaItem {
            val builder = MediaItem.Builder().setUri(url).setMediaId(mediaKey)
            if (manifestMime != null) builder.setMimeType(manifestMime)
            if (isLive) builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(5_000L).setMinPlaybackSpeed(.97f).setMaxPlaybackSpeed(1.03f).build())
            selectedSubtitle?.takeIf { it.isUrl && it.content.isNotBlank() }?.let { sub ->
                val mime = when (sub.format?.suffix?.lowercase()) { "vtt" -> MimeTypes.TEXT_VTT; "ttml" -> MimeTypes.APPLICATION_TTML; "srt" -> MimeTypes.APPLICATION_SUBRIP; else -> null }
                if (mime != null) builder.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.content)).setMimeType(mime).setLanguage(sub.languageTag).setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT).build()))
            }
            return builder.build()
        }
        fun plain(url: String): MediaItem = MediaItem.Builder().setUri(url).setMediaId(mediaKey).build()
        val source = when {
            manifest == PlaybackManifest.HLS -> factory.createMediaSource(media(hlsUrl!!, MimeTypes.APPLICATION_M3U8))
            manifest == PlaybackManifest.DASH -> factory.createMediaSource(media(dashUrl!!, MimeTypes.APPLICATION_MPD))
            stream != null && stream.deliveryMethod == DeliveryMethod.HLS -> factory.createMediaSource(media(stream.content, MimeTypes.APPLICATION_M3U8))
            stream != null && stream.isVideoOnly && audio != null -> MergingMediaSource(factory.createMediaSource(media(stream.content)), factory.createMediaSource(plain(audio.content)))
            stream != null && !stream.isVideoOnly -> factory.createMediaSource(media(stream.content))
            stream == null && hlsUrl != null -> factory.createMediaSource(media(hlsUrl, MimeTypes.APPLICATION_M3U8))
            else -> error("No playable video format is available.")
        }
        if (position > 0) player.setMediaSource(source, position) else player.setMediaSource(source)
        attachedSubtitle = selectedSubtitle
        attachedSubtitleVideo = mediaKey
        quality = stream
        forceDirectPlayback = forceDirect
        player.prepare()
        player.playWhenReady = play
    }
    fun changeSubtitle(info: StreamInfo, subtitle: SubtitlesStream?) {
        val previous = selectedSubtitle
        try {
            if (subtitle == null || (attachedSubtitleVideo == videoKey(info.url) && attachedSubtitle == subtitle)) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle == null).build()
                selectedSubtitle = subtitle
            } else {
                val position = player.currentPosition.coerceAtLeast(0L)
                val playing = player.playWhenReady
                selectedSubtitle = subtitle
                playQuality(info, quality, selectedAudio, position, playing, forceDirectPlayback)
            }
            if (subtitle != null) lastSubtitle = subtitle
        } catch (e: Exception) {
            selectedSubtitle = previous
            android.widget.Toast.makeText(context, "Captions failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    BackHandler(shortsMode || showHistory || fullscreen || searchState.active || (selected != null && !minimized) || accountPlaylist != null || browse.playlist != null || browse.channel != null || searchState.mode || mainTab != "home") { back() }
    fun loadHome(refresh: Boolean = false) {
        if (loading || feedLoading) return
        val revision = YouTubeAuth.sessionRevision
        loading = true; error = null; feedError = null; feedEnd = false
        homeLoadJob = scope.launch {
            // Cek siaran live channel yang sering views berjalan paralel; hasilnya disisipkan setelah feed tampil.
            val liveJob = async {
                runCatching {
                    VideoRepository.liveFromChannels(HomeRecommendations.watchedChannels(watchHistory, home + HomeFeedCache.read(relatedCachePrefs)))
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
                    error = if (home.isEmpty()) "Feed is unavailable." else "Cannot refresh feed. Showing the latest videos."
                } else {
                    home = if (nextContinuation != null || (YouTubeAuth.isLoggedIn && nextHomePage == null)) {
                        items.filter { it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels }
                    } else {
                        HomeRecommendations.rank(items, watchHistory, emptySet(), emptySet())
                    }
                    homePage = nextHomePage
                    homeContinuationToken = nextContinuation
                    HomeFeedCache.save(homeCachePrefs, home)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == YouTubeAuth.sessionRevision) error = if (home.isEmpty()) "Failed to load feed: ${e.message ?: "try again"}" else "Cannot refresh feed. Showing the latest videos." }
            finally { if (revision == YouTubeAuth.sessionRevision) loading = false }
            val live = liveJob.await()
            if (revision == YouTubeAuth.sessionRevision && live.isNotEmpty()) home = HomeRecommendations.withLive(home, live, hidden.videos, hidden.channels)
        }
    }
    LaunchedEffect(YouTubeAuth.sessionRevision, YouTubeAuth.isLoggedIn) {
        val loggedIn = YouTubeAuth.isLoggedIn
        if (homeSessionRevision != YouTubeAuth.sessionRevision || (lastAccountState && !loggedIn)) {
            homeLoadJob?.cancel()
            loading = false
            home = emptyList()
            subs.reset()
            homeContinuationToken = null
            feedLoading = false
        }
        homeSessionRevision = YouTubeAuth.sessionRevision
        homeChips = emptyList()
        selectedChip = null
        homeChipTitle = "All"
        category = "All"
        categoryItems = emptyList()
        categoryContinuation = null
        lastAccountState = loggedIn
        loadHome()
        if (loggedIn && mainTab == "subscriptions") subs.load()
    }
    fun startFallbackCategory(value: String, force: Boolean) {
        loading = true
        if (value == "Live") {
            // Chip "Live" menampilkan siaran yang benar-benar sedang berlangsung, bukan hasil pencarian kata "Live":
            // lebih dulu dari channel yang pernah views, lalu feed live umum.
            scope.launch {
                try {
                    val mine = async {
                        runCatching {
                            VideoRepository.liveFromChannels(HomeRecommendations.watchedChannels(watchHistory, home + HomeFeedCache.read(relatedCachePrefs)))
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
                    } else error = "Cannot refresh category. Showing the latest videos."
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = "Failed to load category: ${e.message ?: "try again"}" }
                finally { loading = false }
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
                } else error = "Cannot refresh category. Showing the latest videos."
            } catch (e: Exception) { error = "Failed to load category: ${e.message ?: "try again"}" }
            finally { loading = false }
        }
    }

    fun selectChip(chip: HomeChip, force: Boolean = false) {
        if (loading || feedLoading || (chip.title == category && !force)) return
        category = chip.title; selectedChip = chip; error = null; feedError = null; feedEnd = false
        if (chip.title == homeChipTitle) {
            categoryItems = emptyList(); categoryContinuation = null; categoryPage = null; liveNext = null
            return
        }
        if (!force) { categoryItems = emptyList(); categoryPage = null; categoryContinuation = null; liveNext = null }
        if (!chip.hasEndpoint) {
            startFallbackCategory(chip.title, force)
            return
        }
        val revision = YouTubeAuth.sessionRevision
        loading = true
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
                } else error = "Cannot refresh category. Showing the latest videos."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == YouTubeAuth.sessionRevision && selectedChip == chip) error = "Failed to load category: ${e.message ?: "try again"}" }
            finally { if (revision == YouTubeAuth.sessionRevision) loading = false }
        }
    }

    fun selectFallbackCategory(value: String, force: Boolean = false) {
        if (loading || feedLoading || (value == category && !force)) return
        category = value; selectedChip = null; error = null; feedError = null; feedEnd = false
        if (value == homeChipTitle) {
            categoryItems = emptyList(); categoryContinuation = null; categoryPage = null; liveNext = null
            return
        }
        if (!force) { categoryItems = emptyList(); categoryPage = null; categoryContinuation = null; liveNext = null }
        startFallbackCategory(value, force)
    }

    fun selectCategory(value: String, force: Boolean = false) {
        val chip = homeChips.firstOrNull { it.title == value }
        if (chip != null) selectChip(chip, force) else selectFallbackCategory(value, force)
    }
    fun loadMoreFeed() {
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
                            val existing = home.mapTo(HashSet()) { it.url }
                            val newItems = nextPage.items.filter { it.url !in existing && it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels }
                            feedEnd = newItems.isEmpty() && nextPage.continuationToken == null
                            home = home + newItems
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
                        val existing = home.mapTo(HashSet()) { it.url }
                        var newItems = page.items.filterIsInstance<StreamInfoItem>().filter { it.url !in existing }
                        if (newItems.isEmpty()) {
                            val fallback = VideoRepository.searchPage(HomeRecommendations.query(watchHistory, topics, topicIndex++, searchHistory.terms))
                            if (revision != YouTubeAuth.sessionRevision || currentCategory != category) return@launch
                            homePage = fallback
                            newItems = fallback.items.filterIsInstance<StreamInfoItem>().filter { it.url !in existing }
                        }
                        val updated = home + HomeRecommendations.rank(newItems, watchHistory, emptySet(), emptySet())
                        feedEnd = newItems.isEmpty()
                        home = updated
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
                browse.next = first.next; browse.error = null; showHistory = false
                // Seperti YouTube: membuka channel tidak mematikan pemutaran. Dari layar tonton videonya
                // berubah menjadi mini player, dan mini player yang sudah melayang tetap berjalan di atas
                // halaman channel.
                if (selected != null && !shortsMode) {
                    savePosition()
                    if (fullscreen) setFullscreen(false)
                    minimized = true
                } else {
                    savePosition(); player.stop(); shortsMode = false; selected = null; quality = null; selectedAudio = null
                }
                browse.channel = page; browse.channelTab = ChannelTabs.VIDEOS; browse.channelItems = items; browse.playlist = null
            } catch (e: Exception) { error = "Cannot open channel: ${e.message ?: "try again"}" }
            finally { loading = false }
        }
    }
    fun search(text: String = searchState.query, filter: String = "all") {
        val term = text.trim()
        if (term.isBlank() || loading) return
        val looksLikeUrl = term.startsWith("http://", ignoreCase = true) ||
            term.startsWith("https://", ignoreCase = true) ||
            term.startsWith("youtube.com/", ignoreCase = true) ||
            term.startsWith("www.youtube.com/", ignoreCase = true) ||
            term.startsWith("m.youtube.com/", ignoreCase = true) ||
            term.startsWith("youtu.be/", ignoreCase = true)
        if (looksLikeUrl) {
            val uri = runCatching { Uri.parse(if (term.startsWith("http", ignoreCase = true)) term else "https://$term") }.getOrNull()
            if (uri != null) {
                val listId = uri.getQueryParameter("list")?.takeIf { it.isNotBlank() }
                val vid = InnerTubePlayer.videoIdOf(uri.toString())
                if (listId != null && vid == null && !listId.startsWith("RD")) {
                    searchState.active = false; keyboard?.hide()
                    showHistory = false
                    accountPlaylist = AccountPlaylist(listId.removePrefix("VL"), "Playlist")
                    return
                } else if (listId != null) {
                    searchState.active = false; keyboard?.hide()
                    (context as? MainActivity)?.incomingVideo = uri.toString()
                    return
                } else if (vid != null) {
                    searchState.active = false; keyboard?.hide()
                    (context as? MainActivity)?.incomingVideo = "https://www.youtube.com/watch?v=$vid"
                    return
                }
            }
        }
        mainTab = "home"; searchState.query = term; searchState.lastSearch = term; searchState.filter = filter; searchState.active = false; keyboard?.hide()
        searchHistory.add(term)
        searchState.generation++; searchState.moreLoading = false
        loading = true; error = null; searchState.mode = true; browse.channel = null; browse.playlist = null; searchState.results = emptyList(); searchState.page = null; searchState.moreError = null
        scope.launch {
            try { val page = VideoRepository.searchPage(term, filter); searchState.page = page; searchState.results = page.items; if (searchState.results.isEmpty()) error = "No results found." }
            catch (e: Exception) { error = "Search failed: ${e.message ?: "try again"}" }
            finally { loading = false }
        }
    }
    fun moreSearchResults() {
        val page = searchState.page ?: return
        if (page.next == null || searchState.moreLoading || loading) return
        searchState.moreLoading = true; searchState.moreError = null
        val generation = searchState.generation
        scope.launch {
            try {
                val next = VideoRepository.moreSearch(page)
                if (generation != searchState.generation) return@launch
                val updated = (searchState.results + next.items).distinctBy { it.url }
                searchState.page = if (updated.size == searchState.results.size) next.copy(next = null) else next
                searchState.results = updated
            } catch (e: Exception) { if (generation == searchState.generation) searchState.moreError = "Failed to load more results: ${e.message ?: "try again"}" }
            finally { if (generation == searchState.generation) searchState.moreLoading = false }
        }
    }
    fun loadChannelTab(filter: String) {
        val page = browse.channel ?: return
        if (loading) return
        browse.channelTab = filter; browse.channelItems = emptyList(); error = null; loading = true
        scope.launch {
            try { val result = VideoRepository.channelTabPage(page, filter); browse.channelItems = result.items; browse.next = result.next; browse.error = null }
            catch (e: Exception) { error = "Failed to load tab: ${e.message ?: "try again"}" }
            finally { loading = false }
        }
    }
    fun openPlaylist(url: String) {
        if (loading) return
        val listId = runCatching { Uri.parse(url).getQueryParameter("list") }.getOrNull()?.takeIf { it.isNotBlank() }
        if (listId != null && !listId.startsWith("RD")) {
            showHistory = false
            accountPlaylist = AccountPlaylist(listId.removePrefix("VL"), "Playlist")
            return
        }
        loading = true; error = null
        scope.launch {
            try { val result = VideoRepository.playlist(url); browse.playlist = result; browse.playlistItems = result.relatedItems; browse.next = result.nextPage; browse.error = null; showHistory = false }
            catch (e: Exception) { error = "Cannot open playlist: ${e.message ?: "try again"}" }
            finally { loading = false }
        }
    }
    fun loadMoreBrowse() {
        val next = browse.next ?: return
        val currentPlaylist = browse.playlist
        val currentChannel = browse.channel
        val currentTab = browse.channelTab
        if (browse.loading) return
        browse.loading = true; browse.error = null
        scope.launch {
            try {
                val result = if (currentPlaylist != null) VideoRepository.morePlaylist(currentPlaylist.url, next) else if (currentChannel != null) VideoRepository.moreChannelTab(currentChannel, currentTab, next) else return@launch
                if (browse.playlist != currentPlaylist || browse.channel != currentChannel || browse.channelTab != currentTab) return@launch
                val old = if (currentPlaylist != null) browse.playlistItems else browse.channelItems
                val merged = (old + result.items).distinctBy { it.url }
                if (currentPlaylist != null) browse.playlistItems = merged else browse.channelItems = merged
                browse.next = if (merged.size == old.size) null else result.next
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { browse.error = "Failed to load next page: ${e.message ?: "try again"}" }
            finally { browse.loading = false }
        }
    }
    /** Kualitas video dan jalur audio awal untuk [info]. */
    fun pickStreams(info: StreamInfo): Pair<VideoStream?, AudioStream?> {
        val options = qualityOptions(info)
        val audio = selectAudioTrack(audioTracks(info), PlayerSettings.audioPreference, java.util.Locale.getDefault())
        fun heightOf(stream: VideoStream): Int =
            stream.height.takeIf { it > 0 } ?: (stream.resolution.filter(Char::isDigit).toIntOrNull() ?: 0)
        val adaptive = !info.hlsUrl.isNullOrBlank() || !info.dashMpdUrl.isNullOrBlank()
        // A combined/progressive video has fixed audio; use a separate video stream when available.
        val separate = if (audio != null) options.filter { it.isVideoOnly } else emptyList()
        val candidates = separate.ifEmpty { options }
        val initial = if (adaptive) null else candidates.firstOrNull { heightOf(it) in 1..maxVideoHeight } ?: candidates.lastOrNull()
        if (initial == null && !adaptive) error("No playable video format is available.")
        return initial to audio
    }
    fun configurePreferredAudio(info: StreamInfo, preference: AudioPreference) {
        val languages = preferredAudioLanguages(preference, java.util.Locale.getDefault(), audioTracks(info)).toMutableList()
        val embeddedOriginal = VideoRepository.embeddedAudio(info).firstOrNull { it.metadata.type == AudioTrackType.ORIGINAL }
            ?.metadata?.locale?.toLanguageTag()
        if (!embeddedOriginal.isNullOrBlank() && languages.none { it.equals(embeddedOriginal, ignoreCase = true) }) languages += embeddedOriginal
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setPreferredAudioLanguages(*languages.toTypedArray()).build()
    }
    fun usesAdaptiveAudio(info: StreamInfo): Boolean = !forceDirectPlayback && (
        playbackManifest(info.isOngoingLive(), info.hlsUrl, info.dashMpdUrl) != PlaybackManifest.NONE ||
            quality?.deliveryMethod == DeliveryMethod.HLS || (quality == null && !info.hlsUrl.isNullOrBlank())
    )
    fun changePreferredAudio(info: StreamInfo, preference: AudioPreference) {
        PlayerSettings.updateAudioPreference(preference)
        manualAudioOverride = false
        attemptedAudioFallback = null
        configurePreferredAudio(info, preference)
        if (VideoRepository.embeddedAudio(info, quality) != null) {
            val tracks = VideoRepository.embeddedAudio(info)
            val preferred = selectEmbeddedAudio(tracks, preference, java.util.Locale.getDefault())
            val height = player.videoFormat?.height?.takeIf { it > 0 } ?: maxVideoHeight
            val video = tracks.filter { it.metadata.id == preferred?.metadata?.id }.minByOrNull {
                kotlin.math.abs((it.video.height.takeIf { value -> value > 0 } ?: it.video.resolution.filter(Char::isDigit).toIntOrNull() ?: 0) - height)
            }?.video
            if (video != null && video.content != quality?.content) {
                try {
                    playQuality(info, video, null, player.currentPosition.coerceAtLeast(0L), player.playWhenReady, forceDirect = true)
                    selectedAudio = null
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Failed to change audio: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        val chosen = selectAudioTrack(audioTracks(info), preference, java.util.Locale.getDefault())
        if (usesAdaptiveAudio(info)) {
            selectedAudio = chosen
            return
        }
        if (chosen == null || chosen == selectedAudio && quality?.isVideoOnly == true) return
        val candidates = VideoRepository.videos(info).filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val height = quality?.height ?: player.videoFormat?.height?.takeIf { it > 0 } ?: maxVideoHeight
        val video = quality?.takeIf { it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            ?: candidates.minByOrNull { kotlin.math.abs(it.height - height) }
        if (video != null) {
            try {
                playQuality(info, video, chosen, player.currentPosition.coerceAtLeast(0L), player.playWhenReady, forceDirect = true)
                selectedAudio = chosen
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Failed to change audio: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    /** Pasang [info] ke pemutar: pilih kualitas dan audio awal, lalu mulai memutar. Dipakai video biasa. */
    fun startInfo(info: StreamInfo, resume: Long, startMinimized: Boolean = false) {
        chaptersOpen = false
        selectedSubtitle = null
        lastSubtitle = null
        playerLocked = false
        qualityMode = "auto"
        player.pauseAtEndOfMediaItems = false
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
        val (initial, audio) = pickStreams(info)
        manualAudioOverride = false
        attemptedAudioFallback = null
        configurePreferredAudio(info, PlayerSettings.audioPreference)
        // Saved positions are relative to an older moving DVR window, not the current live edge.
        if (info.isOngoingLive()) player.setPlaybackSpeed(1f)
        playQuality(info, initial, audio, if (info.isOngoingLive()) 0L else resume, true)
        selectedAudio = audio
        selected = info
        minimized = startMinimized
    }
    /** Sumber media satu Short. mediaId = videoKey, supaya item di daftar putar pemutar bisa dikenali. */
    fun shortSource(info: StreamInfo, stream: VideoStream?, audio: AudioStream?): MediaSource {
        val key = videoKey(info.url)
        val factory = youTubeMediaSourceFactory(context)
        fun item(url: String) = MediaItem.Builder().setUri(url).setMediaId(key).build()
        return when {
            stream != null && stream.deliveryMethod == DeliveryMethod.HLS -> youTubeHlsSource(context, stream.content, key)
            stream != null && stream.isVideoOnly && audio != null -> MergingMediaSource(factory.createMediaSource(item(stream.content)), factory.createMediaSource(MediaItem.fromUri(audio.content)))
            stream != null && !stream.isVideoOnly -> factory.createMediaSource(item(stream.content))
            stream == null && !info.hlsUrl.isNullOrBlank() -> youTubeHlsSource(context, info.hlsUrl, key)
            stream == null && !info.dashMpdUrl.isNullOrBlank() -> youTubeDashSource(context, info.dashMpdUrl, key)
            else -> error("No playable video format is available.")
        }
    }
    /**
     * Putar sebuah Short dari awal. Bila Short ini sudah masuk daftar putar pemutar sebagai item berikutnya
     * ([preloadNext]), cukup pindah ke item itu: datanya sudah di-buffer sehingga frame pertama muncul seketika.
     */
    fun startShort(info: StreamInfo) {
        val key = videoKey(info.url)
        val (stream, audio) = pickStreams(info)
        forceDirectPlayback = false
        manualAudioOverride = false
        attemptedAudioFallback = null
        configurePreferredAudio(info, PlayerSettings.audioPreference)
        val loaded = if (player.playbackState == Player.STATE_IDLE) null else (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == key }
        selectedSubtitle = null; lastSubtitle = null; playerLocked = false; qualityMode = "auto"
        attachedSubtitle = null; attachedSubtitleVideo = null
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
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
    // Some adaptive manifests omit a dub that the extractor returned as a direct stream.
    // Switch once to that separate audio when it is available, without overriding a manual choice.
    LaunchedEffect(selected, pendingVideo, shortsMode, forceDirectPlayback, manualAudioOverride, qualityMode, activePlaybackState, activePlaybackTracks, PlayerSettings.audioPreference) {
        val info = selected ?: return@LaunchedEffect
        if (pendingVideo != null || shortsMode || manualAudioOverride || qualityMode != "auto" || activePlaybackState != Player.STATE_READY || !usesAdaptiveAudio(info) ||
            info.isOngoingLive() || player.isCurrentMediaItemLive || player.currentMediaItem?.mediaId != videoKey(info.url)) return@LaunchedEffect
        if (!allowAutomaticDirectAudioFallback(!info.hlsUrl.isNullOrBlank(), !info.dashMpdUrl.isNullOrBlank())) return@LaunchedEffect
        delay(1_500L) // Let the manifest track list settle before deciding an audio language is absent.
        if (player.playbackState != Player.STATE_READY || !player.playWhenReady || player.currentMediaItem?.mediaId != videoKey(info.url)) return@LaunchedEffect
        val fallbackKey = "${videoKey(info.url)}|${PlayerSettings.audioPreference.key}|${java.util.Locale.getDefault().toLanguageTag()}"
        if (attemptedAudioFallback == fallbackKey) return@LaunchedEffect
        val formats = manifestAudioFormats(activePlaybackTracks)
        val audio = selectAudioTrack(audioTracks(info), PlayerSettings.audioPreference, java.util.Locale.getDefault())
        val height = player.videoFormat?.height?.takeIf { it > 0 } ?: maxVideoHeight
        val useSeparate = audio?.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
            shouldUseSeparateAudio(formats, audio, PlayerSettings.audioPreference, java.util.Locale.getDefault())
        val embedded = selectEmbeddedAudio(VideoRepository.embeddedAudio(info), PlayerSettings.audioPreference, java.util.Locale.getDefault())
        val useEmbedded = shouldUseEmbeddedAudio(formats, embedded, PlayerSettings.audioPreference, java.util.Locale.getDefault())
        val separateVideo = if (useSeparate) VideoRepository.videos(info).filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .minByOrNull { kotlin.math.abs(it.height - height) }
        else null
        val embeddedVideo = if (useEmbedded) VideoRepository.embeddedAudio(info).filter { it.metadata.id == embedded?.metadata?.id }
            .minByOrNull { kotlin.math.abs((it.video.height.takeIf { value -> value > 0 } ?: it.video.resolution.filter(Char::isDigit).toIntOrNull() ?: 0) - height) }?.video
        else null
        val video = separateVideo ?: embeddedVideo ?: return@LaunchedEffect
        attemptedAudioFallback = fallbackKey
        try {
            playQuality(info, video, if (separateVideo != null) audio else null, player.currentPosition.coerceAtLeast(0L), player.playWhenReady, forceDirect = true)
            selectedAudio = if (separateVideo != null) audio else null
        } catch (e: Exception) {
            android.util.Log.w("FlufPlayback", "preferred audio fallback failed: ${e.javaClass.simpleName}")
        }
    }
    // A direct stream selected automatically for a preferred audio track can stall even when the
    // original HLS stream is available. Try HLS once without changing saved quality preferences.
    LaunchedEffect(selected, pendingVideo, shortsMode, forceDirectPlayback, attemptedAudioFallback, manualAudioOverride, qualityMode) {
        val info = selected ?: return@LaunchedEffect
        if (pendingVideo != null || !canRecoverAutomaticAudioFallback(forceDirectPlayback,
                attemptedAudioFallback != null, manualAudioOverride, qualityMode,
                !info.hlsUrl.isNullOrBlank() && info.dashMpdUrl.isNullOrBlank(), info.isOngoingLive(), shortsMode)) return@LaunchedEffect
        val key = videoKey(info.url)
        var bufferingSince: Long? = null
        while (true) {
            delay(500)
            if (player.currentMediaItem?.mediaId != key || player.isCurrentMediaItemLive) return@LaunchedEffect
            if (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING) {
                val now = android.os.SystemClock.elapsedRealtime()
                val since = bufferingSince ?: now.also { bufferingSince = it }
                if (now - since >= 8_000L) {
                    try {
                        val position = player.currentPosition.coerceAtLeast(0L)
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                        playQuality(info, null, null, position, true, forceDirect = false)
                        android.util.Log.i("FlufPlayback", "automatic audio fallback stalled; retrying HLS")
                        android.widget.Toast.makeText(context, "Switching stream to reduce buffering", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.w("FlufPlayback", "HLS recovery failed: ${e.javaClass.simpleName}")
                    }
                    return@LaunchedEffect
                }
            } else bufferingSince = null
        }
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
        if (lastRecovered == key) { shortsResolver.fail(info.url, "Playback failed"); return }
        lastRecovered = key
        shortsResolver.evict(info.url)
        player.stop(); player.clearMediaItems()
        scope.launch {
            try { startShort(shortsResolver.await(info.url)) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { shortsResolver.fail(info.url, e.message ?: "failed to load") }
        }
    }
    // The watch page opens on the tap with the card's thumbnail and title (YouTube does the same); the stream loads behind it.
    fun openUrl(url: String, preview: PendingVideo = PendingVideo(url), resumeLocal: Boolean = true, keepMinimized: Boolean = false) {
        OfflinePlaybackBridge.close()
        if (pendingVideo?.url == url && pendingVideo?.error == null) {
            if (!keepMinimized) minimized = false
            return
        }
        openJob?.cancel()
        error = null
        if (!fullscreen) {
            pendingVideo = preview.copy(resumeLocal = resumeLocal, error = null)
            if (!keepMinimized) minimized = false
            dragProgress = null
            if (selected != null) player.pause()
        }
        openJob = scope.launch {
            try {
                // Jangan biarkan halaman sementara menunggu resolusi tanpa batas saat jaringan/fallback macet.
                val info = withTimeoutOrNull(45_000) { VideoRepository.resolve(url, context) }
                if (info == null) {
                    VideoRepository.evict(url)
                    throw IllegalStateException("Timed out loading video")
                }
                // Do not replace the surface in the middle of a swipe: let the user finish the gesture first.
                if (dragProgress != null) snapshotFlow { dragProgress }.first { it == null }
                savePosition()
                val startMinimized = pendingVideo?.url == url && minimized
                val key = videoKey(info.url).ifBlank { videoKey(url) }
                val localResume = if (resumeLocal) {
                    watchHistory.firstOrNull { videoKey(it.url) == key }?.position ?: 0L
                } else 0L
                val previewResume = pendingVideo?.resumePositionMs ?: preview.resumePositionMs ?: 0L
                val meta = HomeFeed.metaOf(info.url) ?: HomeFeed.metaOf(url)
                val serverResume = if (resumeLocal) {
                    when {
                        meta != null && meta.resumePositionMs > 0L -> meta.resumePositionMs
                        meta != null && meta.progressPercent in 1..98 && info.duration > 0L -> (meta.progressPercent / 100.0 * info.duration * 1000L).toLong()
                        else -> 0L
                    }
                } else 0L
                val resumePosition = when {
                    localResume >= 15_000L && localResume >= serverResume -> localResume
                    serverResume > 0L -> serverResume
                    previewResume > 0L -> previewResume
                    else -> localResume
                }
                startInfo(info, resumePosition, startMinimized)
                pendingVideo = null
                val index = queue.indexOfFirst { videoKey(it.url) == videoKey(info.url) }
                val nextUrl = (if (index >= 0) queue.getOrNull(index + 1) else null)?.url
                    ?: info.relatedItems.filterIsInstance<StreamInfoItem>().firstOrNull { it.url != info.url }?.url
                nextUrl?.let { VideoRepository.prefetch(it, context) }
                scope.launch {
                    withContext(Dispatchers.Default) {
                        runCatching {
                            val related = info.relatedItems.filterIsInstance<StreamInfoItem>().filter { it.url != info.url }
                            if (related.isNotEmpty()) {
                                val candidates = (related + HomeFeedCache.read(relatedCachePrefs)).distinctBy { it.url }
                                HomeFeedCache.save(relatedCachePrefs, candidates)
                                home = HomeRecommendations.rank((related + home).distinctBy { it.url }, watchHistory, emptySet(), emptySet())
                            }
                        }
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = "Cannot open video: ${e.message ?: "try again"}"
                if (pendingVideo != null) pendingVideo = pendingVideo?.copy(error = message) else error = message
            }
        }
    }
    fun queuedItem(video: WatchedVideo): StreamInfoItem = StreamInfoItem(
        org.schabi.newpipe.extractor.ServiceList.YouTube.serviceId, video.url, video.title,
        org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM
    ).apply {
        uploaderName = video.uploader
        duration = video.duration / 1000
        isShortFormContent = video.isShort
        if (video.thumbnail.isNotBlank()) thumbnails = listOf(org.schabi.newpipe.extractor.Image(video.thumbnail, 720, 405, org.schabi.newpipe.extractor.Image.ResolutionLevel.HIGH))
    }
    fun playPlaylistContext(
        playlistId: String,
        initialVideoUrl: String? = null,
        fallbackTitle: String? = null,
        fallbackUploader: String? = null,
        fallbackThumb: String? = null,
        resumeLocal: Boolean = true
    ) {
        val pid = playlistId.removePrefix("VL").trim()
        if (pid.isBlank()) return
        if (initialVideoUrl == null && !pid.startsWith("RD")) {
            showHistory = false
            accountPlaylist = AccountPlaylist(pid, fallbackTitle?.takeIf { it.isNotBlank() } ?: "Playlist", fallbackThumb.orEmpty())
            return
        }
        showHistory = false
        shortsMode = false
        activePlaylistId = pid
        activePlaylistTitle = fallbackTitle?.takeIf { it.isNotBlank() } ?: "Playlist"
        activePlaylistCount = null
        if (initialVideoUrl != null) {
            openUrl(initialVideoUrl, PendingVideo(initialVideoUrl, fallbackTitle.orEmpty(), fallbackUploader.orEmpty(), fallbackThumb), resumeLocal = resumeLocal)
        }
        playlistQueueJob?.cancel()
        playlistQueueJob = scope.launch {
            val page = try {
                if (!pid.startsWith("RD")) {
                    AccountLibrary.videos(AccountPlaylist(pid, fallbackTitle?.takeIf { it.isNotBlank() } ?: "Playlist", fallbackThumb.orEmpty()))
                } else null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
                ?.takeIf { it.videos.isNotEmpty() }
                ?: try { InnerTubePlayer.playlistPanel(initialVideoUrl?.let(::videoKey), pid) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            if (page != null && page.videos.isNotEmpty()) {
                val resolvedTitle = page.header?.title?.takeIf { it.isNotBlank() }
                    ?: page.videos.firstOrNull()?.playlistTitle?.takeIf { it.isNotBlank() }
                    ?: activePlaylistTitle
                    ?: "Playlist"
                val resolvedCount = page.header?.count?.takeIf { it.isNotBlank() } ?: "${page.videos.size} videos"
                activePlaylistTitle = resolvedTitle
                activePlaylistCount = resolvedCount
                queue = page.videos.map(::queuedItem)
                WatchHistory.rememberPlaylistVideos(
                    watchPrefs,
                    PlaylistRef(pid, resolvedTitle, resolvedCount, page.header?.owner.orEmpty()),
                    page.videos.map { it.url }
                )
                watchHistory = WatchHistory.read(watchPrefs)
                if (accountHistory.entries.isNotEmpty()) accountHistory.entries = WatchHistory.enrichAccountEntries(accountHistory.entries, watchPrefs)
                if (initialVideoUrl == null) {
                    val first = page.videos.first()
                    openUrl(first.url, PendingVideo(first.url, first.title, first.uploader, first.thumbnail.ifBlank { null }), resumeLocal = resumeLocal)
                }
            }
        }
    }
    // Riwayat: entri yang views lewat layar Shorts dibuka lagi di layar Shorts (bukan layar tonton biasa).
    fun openHistoryEntry(entry: WatchedVideo) {
        showHistory = false
        if (entry.isShort) {
            if (pendingVideo != null) closePlayer()
            val id = videoKey(entry.url)
            val item = StreamInfoItem(org.schabi.newpipe.extractor.ServiceList.YouTube.serviceId, "https://www.youtube.com/shorts/$id", entry.title, org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM).apply {
                isShortFormContent = true
                uploaderName = entry.uploader
                if (entry.thumbnail.isNotBlank()) thumbnails = listOf(org.schabi.newpipe.extractor.Image(entry.thumbnail, 720, 1280, org.schabi.newpipe.extractor.Image.ResolutionLevel.HIGH))
            }
            queue = listOf(item); shorts.index = 0; shorts.generation++; shorts.tabQueue = false; shorts.moreError = null; minimized = false; shortsMode = true
        } else if (!entry.playlistId.isNullOrBlank()) {
            playPlaylistContext(
                playlistId = entry.playlistId,
                initialVideoUrl = entry.url,
                fallbackTitle = entry.playlistTitle ?: entry.title,
                fallbackUploader = entry.uploader,
                fallbackThumb = entry.thumbnail.ifBlank { null },
                resumeLocal = true
            )
        } else {
            playlistQueueJob?.cancel()
            activePlaylistId = null; activePlaylistTitle = null; activePlaylistCount = null
            shortsMode = false; queue = emptyList(); openUrl(entry.url, PendingVideo(entry.url, entry.title, entry.uploader, entry.thumbnail.ifBlank { null }))
        }
    }
    fun openAccountHistoryEntry(entry: WatchedVideo) {
        if (entry.isShort) openHistoryEntry(entry)
        else if (!entry.playlistId.isNullOrBlank()) {
            playPlaylistContext(
                playlistId = entry.playlistId,
                initialVideoUrl = entry.url,
                fallbackTitle = entry.playlistTitle ?: entry.title,
                fallbackUploader = entry.uploader,
                fallbackThumb = entry.thumbnail.ifBlank { null },
                resumeLocal = false
            )
        } else {
            playlistQueueJob?.cancel()
            activePlaylistId = null; activePlaylistTitle = null; activePlaylistCount = null
            showHistory = false; shortsMode = false; queue = emptyList()
            openUrl(entry.url, PendingVideo(entry.url, entry.title, entry.uploader, entry.thumbnail.ifBlank { null }, resumePositionMs = entry.position.takeIf { it > 0L }), resumeLocal = true)
        }
    }
    /** Putar [video] dari playlist akun [collection] dengan [entries] sebagai antrean. Bila [shortsAsHistory], Short dibuka di layar Shorts. */
    fun playAccountPlaylist(collection: AccountPlaylist, entries: List<WatchedVideo>, video: WatchedVideo, shortsAsHistory: Boolean) {
        val pid = collection.id.removePrefix("VL")
        val resolvedTitle = entries.firstOrNull()?.playlistTitle?.takeIf { it.isNotBlank() } ?: collection.title
        val resolvedCount = "${entries.size} videos"
        WatchHistory.rememberPlaylistVideos(
            watchPrefs,
            PlaylistRef(pid, resolvedTitle, resolvedCount, entries.firstOrNull()?.uploader.orEmpty()),
            entries.map { it.url }
        )
        watchHistory = WatchHistory.read(watchPrefs)
        if (accountHistory.entries.isNotEmpty()) accountHistory.entries = WatchHistory.enrichAccountEntries(accountHistory.entries, watchPrefs)
        if (shortsAsHistory && video.isShort) {
            openHistoryEntry(video)
            return
        }
        playlistQueueJob?.cancel()
        activePlaylistId = pid
        activePlaylistTitle = resolvedTitle
        activePlaylistCount = resolvedCount
        showHistory = false; shortsMode = false; queue = entries.map(::queuedItem)
        openUrl(video.url, PendingVideo(video.url, video.title, video.uploader, video.thumbnail.ifBlank { null }), resumeLocal = false)
    }
    val historyActions = HistoryActions(
        queue = { video, playNext ->
            val current = selected
            if (current == null && !shortsMode) {
                showHistory = false
                queue = listOf(queuedItem(video))
                openUrl(video.url, PendingVideo(video.url, video.title, video.uploader, video.thumbnail.ifBlank { null }))
            } else if (current == null || shortsMode) {
                android.widget.Toast.makeText(context, "Queue is available while watching a video", android.widget.Toast.LENGTH_SHORT).show()
            } else if (videoKey(video.url) == videoKey(current.url)) {
                android.widget.Toast.makeText(context, "This video is already playing", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val currentItem = queuedItem(WatchedVideo(current.url, current.name.orEmpty(), current.uploaderName.orEmpty(), current.thumbnails.lastOrNull()?.url.orEmpty(), 0L))
                queue = enqueueHistoryVideo(videoKey(current.url), queue, currentItem, queuedItem(video), playNext) { videoKey(it.url) }
                android.widget.Toast.makeText(context, if (playNext) "Added to play next" else "Added to queue", android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        watchLater = { video ->
            if (!YouTubeAuth.isLoggedIn) showLoginDialog = true
            else {
                val revision = YouTubeAuth.sessionRevision
                scope.launch {
                    val result = ShortsFeed.saveToWatchLater(videoKey(video.url), revision)
                    if (result == null && revision == YouTubeAuth.sessionRevision) Engagement.setWatchLater(videoKey(video.url), true)
                    android.widget.Toast.makeText(context, result ?: "Saved to Watch later", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        },
        save = { video -> if (!YouTubeAuth.isLoggedIn) showLoginDialog = true else savePlaylistVideoId = videoKey(video.url) },
        share = { video ->
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, video.url)
            runCatching { context.startActivity(Intent.createChooser(intent, "Share video")) }
                .onFailure { android.widget.Toast.makeText(context, "No app available to share", android.widget.Toast.LENGTH_SHORT).show() }
        },
        remove = { video ->
            if (!accountHistoryLoggedIn) {
                WatchHistory.remove(watchPrefs, video.url); watchHistory = WatchHistory.read(watchPrefs)
            } else {
                val matching = accountHistory.entries.filter { it.video == video }
                val entry = matching.singleOrNull()
                val token = entry?.removeToken
                if (token == null) android.widget.Toast.makeText(context, "YouTube did not provide a history removal action", android.widget.Toast.LENGTH_LONG).show()
                else scope.launch {
                    try {
                        AccountHistory.remove(token, accountHistorySession)
                        if (YouTubeAuth.sessionRevision == accountHistorySession) accountHistory.entries = accountHistory.entries.filterNot { it === entry }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { android.widget.Toast.makeText(context, "Couldn't remove from YouTube history", android.widget.Toast.LENGTH_LONG).show() }
                }
            }
        },
        castScreen = {
            try { context.startActivity(Intent(android.provider.Settings.ACTION_CAST_SETTINGS)) }
            catch (_: Exception) { android.widget.Toast.makeText(context, "Cast is unavailable on this device", android.widget.Toast.LENGTH_SHORT).show() }
        },
    )
    fun open(item: StreamInfoItem) {
        val meta = HomeFeed.metaOf(item.url)
        val listParam = runCatching { Uri.parse(item.url).getQueryParameter("list") }.getOrNull()?.takeIf { it.isNotBlank() }
        if (meta?.isPlaylist == true || listParam != null) {
            val pid = (meta?.playlistId ?: listParam.orEmpty()).removePrefix("VL")
            val vid = InnerTubePlayer.videoIdOf(item.url)
            playPlaylistContext(
                playlistId = pid,
                initialVideoUrl = vid?.let { "https://www.youtube.com/watch?v=$it" },
                fallbackTitle = meta?.playlistTitle?.takeIf { it.isNotBlank() } ?: item.name,
                fallbackUploader = item.uploaderName,
                fallbackThumb = item.thumbnails.maxByOrNull { it.width }?.url
            )
            return
        }
        val short = item.isShortFormContent || (browse.channel != null && browse.channelTab == ChannelTabs.SHORTS)
        val knownRef = if (!short && browse.playlist == null) {
            InnerTubePlayer.videoIdOf(item.url)?.let { WatchHistory.playlistRefForVideoId(it, watchPrefs) }
        } else null
        if (knownRef != null) {
            playPlaylistContext(
                playlistId = knownRef.playlistId,
                initialVideoUrl = item.url,
                fallbackTitle = knownRef.playlistTitle.ifBlank { item.name },
                fallbackUploader = item.uploaderName,
                fallbackThumb = item.thumbnails.maxByOrNull { it.width }?.url
            )
            return
        }
        val list = if (browse.playlist != null) browse.playlistItems.filterIsInstance<StreamInfoItem>()
            else if (short) (if (browse.channel != null) browse.channelItems else searchState.results).filterIsInstance<StreamInfoItem>().filter { it.isShortFormContent || (browse.channel != null && browse.channelTab == ChannelTabs.SHORTS) }
            else emptyList()
        if (short) {
            if (pendingVideo != null) closePlayer()
            playlistQueueJob?.cancel()
            activePlaylistId = null; activePlaylistTitle = null; activePlaylistCount = null
            // Shorts tidak lewat openUrl: pager menampilkan poster seketika, lalu ShortsResolver memuat dan memutar halaman yang aktif.
            val index = list.indexOfFirst { videoKey(it.url) == videoKey(item.url) }
            queue = if (index >= 0) list else listOf(item)
            shorts.index = index.coerceAtLeast(0)
            shorts.generation++
            shorts.tabQueue = false
            shorts.moreError = null
            minimized = false
            shortsMode = true
        } else {
            playlistQueueJob?.cancel()
            val currentPlaylist = browse.playlist
            if (currentPlaylist != null) {
                activePlaylistId = currentPlaylist.id?.removePrefix("VL")
                activePlaylistTitle = currentPlaylist.name
                activePlaylistCount = "${list.size} videos"
            } else {
                activePlaylistId = null; activePlaylistTitle = null; activePlaylistCount = null
            }
            queue = list
            shortsMode = false
            val meta = HomeFeed.metaOf(item.url)
            val initialResume = meta?.resumePositionMs?.takeIf { it > 0L }
                ?: if (meta != null && meta.progressPercent in 1..98 && item.duration > 0) {
                    (meta.progressPercent / 100.0 * item.duration * 1000L).toLong()
                } else null
            openUrl(item.url, PendingVideo(item.url, item.name.orEmpty(), item.uploaderName.orEmpty(), item.thumbnails.maxByOrNull { it.width }?.url, item.uploaderAvatars.lastOrNull()?.url, resumePositionMs = initialResume))
        }
    }
    fun openShortsShelf(cards: List<ShortsCard>, index: Int) {
        val items = cards.mapNotNull { card ->
            if (card.videoId.length != 11) null
            else StreamInfoItem(
                org.schabi.newpipe.extractor.ServiceList.YouTube.serviceId,
                "https://www.youtube.com/shorts/${card.videoId}",
                card.title,
                org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM
            ).apply {
                isShortFormContent = true
                if (card.thumbnailUrl.isNotBlank()) {
                    thumbnails = listOf(org.schabi.newpipe.extractor.Image(card.thumbnailUrl, 720, 1280, org.schabi.newpipe.extractor.Image.ResolutionLevel.HIGH))
                }
            }
        }
        if (items.isEmpty()) return
        if (pendingVideo != null) closePlayer()
        queue = items
        shorts.index = index.coerceIn(0, items.size - 1)
        shorts.generation++
        shorts.tabQueue = false
        shorts.moreError = null
        minimized = false
        shortsMode = true
    }
    // Menekan tab Shorts langsung membuka layar Shorts (kosong + spinner); antrean diisi dari feed begitu siap.
    LaunchedEffect(mainTab, shorts.request, YouTubeAuth.sessionRevision) {
        if (mainTab != "shorts") return@LaunchedEffect
        minimized = false
        shortsMode = true; shorts.tabQueue = true
        queue = emptyList(); shorts.index = 0; shorts.generation++
        shorts.loading = true
        shorts.error = null
        shorts.page = null; shorts.moreError = null; shorts.topicIndex = 0; shorts.seqNext = null; shorts.fromFeed = false
        try {
            // Sumber utama: urutan Shorts YouTube (reel_watch_sequence). Urutan dari server dipertahankan; pencarian "shorts" hanya cadangan.
            // Short pembuka ditampilkan dan mulai di-resolve begitu diketahui, paralel dengan permintaan urutan.
            var seedShown = false
            val feed = try {
                ShortsFeed.start { seed -> withContext(Dispatchers.Main) { if (queue.isEmpty()) { queue = listOf(seed); seedShown = true } } }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val feedItems = feed?.items.orEmpty().filter { it.url !in hidden.videos }.distinctBy { videoKey(it.url) }
            if (feedItems.isNotEmpty()) {
                shorts.fromFeed = true
                shorts.seqNext = feed?.next
                queue = feedItems
            } else if (seedShown) {
                shorts.fromFeed = false // pertahankan Short pembuka; antrean diisi dari pencarian saat tinggal sedikit
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
                shorts.page = page
                queue = HomeRecommendations.rank(found.distinctBy { videoKey(it.url) }, watchHistory, hidden.videos, hidden.channels)
            }
            if (queue.isEmpty()) shorts.error = "Shorts are unavailable. Try again."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { shorts.error = "Failed to load Shorts: ${e.message ?: "try again"}" }
        finally { shorts.loading = false }
    }
    // Siapkan halaman pertama Shorts selagi pengguna di beranda, supaya menekan tab Shorts tidak menunggu jaringan.
    LaunchedEffect(Unit) {
        delay(4000)
        if (mainTab == "shorts") return@LaunchedEffect
        ShortsFeed.warm()?.items?.firstOrNull()?.let { shortsResolver.prefetch(it.url) }
    }
    // Memutar halaman yang sedang aktif di pager. Resolve diambil dari cache bila sudah di-prefetch, jadi geser tidak menunggu jaringan.
    LaunchedEffect(shortsMode, shorts.index, shorts.generation, shorts.retry, queue.firstOrNull()?.url) {
        if (!shortsMode) return@LaunchedEffect
        val item = queue.getOrNull(shorts.index) ?: return@LaunchedEffect
        if (selected?.let { videoKey(it.url) } == videoKey(item.url)) { player.playWhenReady = true; return@LaunchedEffect }
        if (shortsResolver.info(item.url) == null) player.pause()
        try {
            val info = shortsResolver.await(item.url)
            if (shortsMode && queue.getOrNull(shorts.index)?.let { videoKey(it.url) } == videoKey(item.url)) {
                savePosition()
                startShort(info)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { shortsResolver.fail(item.url, e.message ?: "failed to load") }
    }
    // Setelah Short berikutnya selesai di-resolve, masukkan ke daftar putar supaya ikut di-buffer.
    val nextShortInfo = if (shortsMode) queue.getOrNull(shorts.index + 1)?.let { shortsResolver.info(it.url) } else null
    LaunchedEffect(shortsMode, selected?.url, nextShortInfo) {
        val next = nextShortInfo ?: return@LaunchedEffect
        val current = selected ?: return@LaunchedEffect
        if (shortsMode && queue.getOrNull(shorts.index)?.let { videoKey(it.url) } == videoKey(current.url)) preloadNext(current, next)
    }
    // Siapkan Short di sekitar halaman aktif selagi yang sekarang diputar.
    LaunchedEffect(shortsMode, shorts.index, queue.size) {
        if (!shortsMode) return@LaunchedEffect
        for (offset in intArrayOf(0, 1, 2, -1)) queue.getOrNull(shorts.index + offset)?.let { shortsResolver.prefetch(it.url) }
    }
    // Isi ulang antrean tab Shorts saat sisa halaman tinggal sedikit.
    val shortsNeedMore = shortsMode && shorts.tabQueue && queue.isNotEmpty() && queue.size - shorts.index <= 4
    LaunchedEffect(shortsNeedMore, queue.size, shorts.moreRequest, YouTubeAuth.sessionRevision) {
        if (!shortsNeedMore) return@LaunchedEffect
        shorts.moreLoading = true
        shorts.moreError = null
        try {
            val existing = queue.mapTo(mutableSetOf()) { videoKey(it.url) }
            val found = mutableListOf<StreamInfoItem>()
            var fromFeed = false
            if (shorts.fromFeed) {
                var feedAttempts = 0
                try {
                    while (found.isEmpty() && feedAttempts < 3) {
                        val token = shorts.seqNext ?: break
                        val feed = ShortsFeed.more(token)
                        shorts.seqNext = feed.next
                        found += feed.items.filter { it.url !in hidden.videos && existing.add(videoKey(it.url)) }
                        feedAttempts++
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* jatuh ke pencarian untuk putaran ini; token feed tetap disimpan untuk dicoba lagi */ }
                if (found.isNotEmpty()) fromFeed = true else if (shorts.seqNext == null) shorts.fromFeed = false
            }
            var page = shorts.page
            var attempts = 0
            while (found.isEmpty() && attempts < 4) {
                page = if (page?.next != null) VideoRepository.moreSearch(page) else {
                    val topic = HomeRecommendations.query(watchHistory, topics, shorts.topicIndex++, searchHistory.terms)
                    VideoRepository.searchPage("$topic shorts")
                }
                found += page.items.filterIsInstance<StreamInfoItem>().filter {
                    (it.isShortFormContent || it.url.contains("/shorts/")) && existing.add(videoKey(it.url))
                }
                attempts++
            }
            shorts.page = page
            val additions = if (fromFeed) found.toList() else HomeRecommendations.rank(found, watchHistory, hidden.videos, hidden.channels)
            queue = queue + additions
            if (additions.isEmpty()) shorts.moreError = "No new Shorts. Tap to try again."
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { shorts.moreError = "Failed to load Shorts. Tap to try again." }
        finally { shorts.moreLoading = false }
    }
    fun nextItem(info: StreamInfo): StreamInfoItem? {
        val index = queue.indexOfFirst { videoKey(it.url) == videoKey(info.url) }
        return if (index >= 0) queue.getOrNull(index + 1) else info.relatedItems.filterIsInstance<StreamInfoItem>().firstOrNull { it.url != info.url }
    }
    val currentOpen = rememberUpdatedState<(String) -> Unit> { openUrl(it) }
    val currentSelected = rememberUpdatedState(selected)
    val currentAutoplay = rememberUpdatedState(autoplay)
    val currentShortsMode = rememberUpdatedState(shortsMode)
    val currentPending = rememberUpdatedState(pendingVideo)
    val currentQueue = rememberUpdatedState(queue)
    val currentRecover = rememberUpdatedState<(StreamInfo) -> Unit> { recoverShort(it) }
    val currentRefreshForbidden = rememberUpdatedState<suspend (StreamInfo, Long, Boolean) -> Unit> { old, position, play ->
        VideoRepository.evict(old.url)
        val fresh = kotlinx.coroutines.withTimeout(30_000L) { VideoRepository.resolve(old.url, context) }
        if (currentSelected.value?.let { videoKey(it.url) } == videoKey(old.url) && !currentShortsMode.value && currentPending.value == null) {
            playQuality(fresh, quality, selectedAudio, position, player.playWhenReady, forceDirectPlayback)
            selected = fresh
        }
    }
    var refreshingPlaybackKey by remember { mutableStateOf<String?>(null) }
    var playbackRefreshJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun refreshPlayback(info: StreamInfo, position: Long, play: Boolean) {
        val key = videoKey(info.url)
        if (playbackRefreshJob?.isActive == true && refreshingPlaybackKey == key) return
        playbackRefreshJob?.cancel()
        player.playWhenReady = play
        refreshingPlaybackKey = key
        playbackRefreshJob = scope.launch {
            try {
                retryPlaybackRecovery(stillCurrent = {
                    currentSelected.value?.let { videoKey(it.url) } == key && !currentShortsMode.value && currentPending.value == null
                }) {
                    try {
                        currentRefreshForbidden.value(info, position, player.playWhenReady)
                        kotlinx.coroutines.withTimeoutOrNull(20_000L) { awaitPlaybackReady(player, key) } == true
                    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                        false
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("FlufPlayback", "refresh retry cause=${e.javaClass.simpleName}")
                        false
                    }
                }
            } finally {
                if (refreshingPlaybackKey == key) refreshingPlaybackKey = null
            }
        }
    }
    LaunchedEffect(selected?.let { videoKey(it.url) }, shortsMode, pendingVideo != null) {
        if (refreshingPlaybackKey != null && (refreshingPlaybackKey != selected?.let { videoKey(it.url) } || shortsMode || pendingVideo != null)) {
            playbackRefreshJob?.cancel()
            refreshingPlaybackKey = null
        }
    }
    DisposableEffect(player) {
        var lastLiveRecoveryKey: String? = null
        var lastLiveRecoveryAt = 0L
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // Shorts berhenti di akhir item (pauseAtEndOfMediaItems) agar tidak lompat ke item yang sudah di-buffer; ulang dari awal.
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && currentShortsMode.value) {
                    player.seekTo(player.currentMediaItemIndex, 0L); player.playWhenReady = true
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                if (currentPending.value != null) return
                val info = currentSelected.value
                val forbidden = generateSequence<Throwable>(error) { it.cause }.take(8).any {
                    (it as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode == 403
                }
                if (info != null && info.isOngoingLive() && !currentShortsMode.value && forbidden) {
                    refreshPlayback(info, player.currentPosition.coerceAtLeast(0L), player.playWhenReady)
                    return
                }
                // Window DVR HLS bergeser. Jika posisi jatuh di belakang window, ExoPlayer melempar
                // BehindLiveWindowException; pulihkan ke live edge, jangan biarkan pemutar mati.
                if (info != null && (player.isCurrentMediaItemLive || info.isOngoingLive()) && error.causedByBehindLiveWindow()) {
                    val key = videoKey(info.url)
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (lastLiveRecoveryKey != key || now - lastLiveRecoveryAt > 30_000L) {
                        lastLiveRecoveryKey = key
                        lastLiveRecoveryAt = now
                        val wasPlaying = player.playWhenReady
                        player.seekToDefaultPosition()
                        player.prepare()
                        if (wasPlaying) player.play()
                        return
                    }
                }
                // URL stream di cache bisa ditolak server; buang agar percobaan berikutnya resolve ulang.
                info?.let { VideoRepository.evict(it.url) }
                if (currentShortsMode.value) info?.let { currentRecover.value(it) }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (currentPending.value != null) return
                val info = currentSelected.value ?: return
                if (state == Player.STATE_ENDED && currentShortsMode.value) {
                    player.seekTo(0); player.play()
                } else if (state == Player.STATE_ENDED) {
                    val list = currentQueue.value
                    val index = list.indexOfFirst { videoKey(it.url) == videoKey(info.url) }
                    val next = if (index >= 0) list.getOrNull(index + 1) else if (currentAutoplay.value) info.relatedItems.filterIsInstance<StreamInfoItem>().firstOrNull { it.url != info.url } else null
                    if (next != null) currentOpen.value(next.url)
                }
            }
        }
        player.addListener(listener)
        onDispose { playbackRefreshJob?.cancel(); player.removeListener(listener) }
    }
    LaunchedEffect(selected, activePlaylistId, YouTubeAuth.sessionRevision, YouTubeAuth.isLoggedIn, PlaybackTracker.settingsRevision) {
        val info = selected
        if (info != null) {
            PlaybackTracker.start(videoKey(info.url), info.duration, context, activePlaylistId, { player.isPlaying }) { player.currentPosition }
        } else {
            PlaybackTracker.stop()
        }
    }
    DisposableEffect(player) { onDispose { PlaybackTracker.stop(sendFinal = false) } }
    LaunchedEffect(selected) {
        while (selected != null) { delay(5000); savePosition() }
    }
    fun openLink(info: StreamInfo, url: String) {
        timestampSeconds(url, info.id)?.let { seconds -> player.seekTo(seconds * 1000L); player.play(); return }
        val uri = runCatching { Uri.parse(if (url.startsWith("/")) "https://www.youtube.com$url" else url) }.getOrNull() ?: return
        val host = uri.host.orEmpty()
        val path = uri.path.orEmpty()
        val listId = uri.getQueryParameter("list")?.takeIf { it.isNotBlank() }
        val vid = InnerTubePlayer.videoIdOf(uri.toString())
        when {
            host.contains("youtube.com") && path == "/playlist" && listId != null ->
                playPlaylistContext(listId, initialVideoUrl = null)
            (host.endsWith("youtu.be") || (host.contains("youtube.com") && path == "/watch")) && listId != null ->
                playPlaylistContext(listId, initialVideoUrl = vid?.let { "https://www.youtube.com/watch?v=$it" })
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
    // A pending video replaces the old selection visually, including while its mini-player is visible.
    val info = selected?.takeIf { pendingVideo == null }?.takeUnless { minimized && progress > .999f }
    val showFeed = selected == null || minimized || progress > 0f
    val selectTab: (String) -> Unit = { tab ->
        if (tab == "search") {
            if (shortsMode && !minimized) closePlayer()
            searchState.active = true
        } else {
            if (tab != "shorts" && shortsMode && !minimized) closePlayer()
            if (tab == "shorts" && mainTab != "shorts") {
                if (pendingVideo != null) closePlayer()
                // Masuk ke layar Shorts seketika (hitam + spinner); antrean diisi oleh efek pemuatan feed.
                if (selected != null && !shortsMode) { savePosition(); player.stop(); player.clearMediaItems(); selected = null; quality = null; selectedAudio = null }
                minimized = false; shortsMode = true; shorts.tabQueue = true
                queue = emptyList(); shorts.index = 0; shorts.error = null; shorts.loading = true
            }
            mainTab = tab; showHistory = false; accountPlaylist = null; searchState.mode = false; searchState.active = false; browse.channel = null; browse.playlist = null; category = homeChipTitle; selectedChip = null; categoryItems = emptyList(); categoryContinuation = null; error = null
        }
    }
    val offlineMini = OfflinePlaybackBridge.session
    LaunchedEffect(offlineMini?.player) { if (offlineMini != null) closePlayer() }
    val incoming = (context as? MainActivity)?.incomingVideo
    LaunchedEffect(incoming) {
        if (!incoming.isNullOrBlank()) {
            (context as? MainActivity)?.incomingVideo = null
            when {
                incoming.startsWith("flufplay-tab:") -> selectTab(incoming.substringAfter(':'))
                incoming.startsWith("flufplay-download:") -> {
                    requestedDownload = incoming.substringAfter(':')
                    openUrl(incoming.substringAfter(':'))
                }
                else -> {
                    val uri = runCatching { Uri.parse(incoming) }.getOrNull()
                    val path = uri?.path.orEmpty()
                    val listId = uri?.getQueryParameter("list")?.takeIf { it.isNotBlank() }
                    val vid = InnerTubePlayer.videoIdOf(incoming)
                    when {
                        path.startsWith("/channel/") || path.startsWith("/@") -> openChannel(incoming)
                        listId != null -> playPlaylistContext(listId, initialVideoUrl = vid?.let { "https://www.youtube.com/watch?v=$it" })
                        else -> openUrl(incoming)
                    }
                }
            }
        }
    }
    LaunchedEffect(requestedDownload, selected?.url) {
        val requested = requestedDownload
        val opened = selected
        if (requested != null && opened != null && videoKey(requested) == videoKey(opened.url)) {
            requestedDownload = null
            showDownloads = true
        }
    }
    if (inPip) AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { useController = false } },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) else Surface(modifier = Modifier.fillMaxSize(), color = Background) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val wideWatch = isWideWatchLayout(maxWidth, maxHeight)
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        FrostedGlassBackdropHost(Modifier.fillMaxSize()) {
          val glassBackdrop = LocalFrostedGlassBackdrop.current
          Box(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().frostedGlassSource(glassBackdrop)) {
            if (showFeed) FeedTopBar(
                searchActive = searchState.active, query = searchState.query, onQueryChange = { searchState.query = it }, onSearch = { search() },
                onCloseSearch = { searchState.active = false; keyboard?.hide() },
                showBack = browse.playlist != null || browse.channel != null || searchState.mode, onBack = { back() },
                searchTitle = searchState.lastSearch.takeIf { searchState.mode && browse.playlist == null && browse.channel == null },
                onEditSearch = { searchState.query = searchState.lastSearch; searchState.active = true },
                channelShareUrl = browse.channel?.info?.url,
            )
            val showTopTabs = showFeed && !searchState.active && browse.playlist == null && browse.channel == null && !searchState.mode && (mainTab == "home" || mainTab == "subscriptions")
            if (showTopTabs) HomeTopTabs(mainTab, category, homeChipTitle, visibleChips, selectTab, { selectCategory(it) }, { selectChip(it) })
            if (downloadState.message.isNotBlank() && showFeed) DownloadStatusBanner(downloadState, onOpen = { saved -> player.pause(); SavedDownloads.open(context, saved) })
            if (loading && showFeed && (searchState.mode || browse.channel != null || browse.playlist != null || searchState.active)) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (showFeed && searchState.active) {
                SearchSuggestionList(
                    query = searchState.query, history = searchHistory.terms, suggestions = searchState.suggestions,
                    onSearch = { search(it) },
                    onRemoveHistory = { searchHistory.remove(it) },
                    onPickSuggestion = { searchState.query = it },
                    modifier = Modifier.weight(1f),
                )
            } else if (showFeed && mainTab != "home" && browse.channel == null && browse.playlist == null && !searchState.mode) {
                LibraryTab(
                    modifier = Modifier.weight(1f), tab = mainTab, videos = if (accountHistoryLoggedIn) accountHistoryPreview(accountHistory.entries) else watchHistory,
                    historySource = if (accountHistoryLoggedIn) "From your YouTube account" else "Watched on this device",
                    historyLoading = accountHistoryLoggedIn && accountHistory.loading,
                    historyError = if (accountHistoryLoggedIn) accountHistory.error else null,
                    onRetryHistory = { accountHistory.request++ },
                    onHistory = { accountHistory.deviceHistory = false; showHistory = true },
                    onOpenVideo = { entry -> if (accountHistoryLoggedIn) openAccountHistoryEntry(entry) else openHistoryEntry(entry) },
                    onOpenPlaylist = { accountPlaylist = it }, historyActions = historyActions,
                    onDownloads = { player.pause(); SavedDownloads.open(context) },
                    onAccount = { if (YouTubeAuth.isLoggedIn || YouTubeAuth.savedAccounts.isNotEmpty()) showAccountDialog = true else showLoginDialog = true },
                    shortsLoading = shorts.loading || (mainTab == "shorts" && loading),
                    shortsError = shorts.error ?: if (mainTab == "shorts") error else null,
                    onSearchShorts = { error = null; shorts.request++ },
                    subscriptionsContent = {
                        SubscriptionsScreen(
                            entries = subs.entries,
                            channels = subs.channels,
                            membershipEntries = subs.membership,
                            membershipLoading = subs.membershipLoading,
                            watchedUrls = watchHistory.map { it.url }.toSet(),
                            loading = subs.loading,
                            error = subs.error,
                            canLoadMore = !subs.end,
                            onRefresh = { subs.load(true) },
                            onLoadMore = { subs.loadMore() },
                            onLoadMembership = { subs.loadMembership() },
                            onVideo = { open(it) },
                            onChannel = { openChannel(it) },
                            onShorts = { cards, index -> openShortsShelf(cards, index) },
                            onNotInterested = { item -> hidden.hideVideo(item.url) }
                        )
                    }
                )
            } else if (showFeed) {
                error?.let { message ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        if (!searchState.mode && browse.channel == null) TextButton(onClick = { if (category == homeChipTitle) loadHome() else selectCategory(category, true) }) { Text("Try again") }
                    }
                }
                val page = browse.channel
                val currentPlaylist = browse.playlist
                val filteredHome = remember(home, hidden.videos, hidden.channels) {
                    home.filter { it.url !in hidden.videos && it.uploaderUrl.orEmpty() !in hidden.channels }
                }
                val visibleItems: List<InfoItem> = if (currentPlaylist != null) browse.playlistItems else if (page != null) browse.channelItems else if (searchState.mode) searchState.results else if (category == homeChipTitle) filteredHome else categoryItems
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
                LaunchedEffect(gridState, isFeed, category, visibleItems.size, feedLoading, feedError, feedEnd, loading) {
                    if (isFeed && !feedLoading && feedError == null && !feedEnd && !loading && visibleItems.isNotEmpty()) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                            .distinctUntilChanged().collect { last ->
                                if (last >= visibleItems.size - 3) loadMoreFeed()
                            }
                    }
                }
                val isSearchResults = searchState.mode && page == null && currentPlaylist == null
                LaunchedEffect(searchState.lastSearch, searchState.filter, isSearchResults) { if (isSearchResults) gridState.scrollToItem(0) }
                LaunchedEffect(gridState, isSearchResults, searchState.results.size, searchState.moreLoading, searchState.moreError, searchState.page, loading) {
                    if (isSearchResults && !loading && !searchState.moreLoading && searchState.moreError == null && searchState.page?.next != null && searchState.results.isNotEmpty()) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { last ->
                            if (last >= searchState.results.size - 3) moreSearchResults()
                        }
                    }
                }
                LaunchedEffect(gridState, page, currentPlaylist, visibleItems.size, browse.next, browse.loading, browse.error) {
                    if ((page != null || currentPlaylist != null) && browse.next != null && !browse.loading && browse.error == null && visibleItems.isNotEmpty()) {
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
                    onRefresh = { if (category == homeChipTitle) loadHome(refresh = true) else selectCategory(category, true) }
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

                Box(Modifier.weight(1f).fillMaxWidth().then(refreshModifier)) {
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
                                    onSubscribe = { subscribeChannelId(page.info.id, it, null) },
                                    onNotificationPreference = { pref -> setChannelNotificationPref(page.info.id, pref) }
                                ) { loadChannelTab(it) }
                            } else if (searchState.mode) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("all" to "All", "videos" to "Video", "channels" to "Channel", "playlists" to "Playlist").forEach { (value, label) ->
                                    val active = searchState.filter == value
                                    Text(label, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (active) Color.White else Surface).clickable(enabled = !loading && !searchState.moreLoading) { if (value != searchState.filter) search(searchState.lastSearch, value) }.padding(horizontal = 13.dp, vertical = 8.dp), color = if (active) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
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
                                        is StreamInfoItem -> if (wideResult) SearchWideVideoCard(item, onChannel = { openChannel(it) }) { open(item) } else VideoCard(item, onChannel = { openChannel(it) }) { open(item) }
                                        is ChannelInfoItem -> SearchChannelCard(item) { openChannel(item.url) }
                                        is PlaylistInfoItem -> if (wideResult) SearchWidePlaylistCard(item) { openPlaylist(item.url) } else PlaylistCard(item) { openPlaylist(item.url) }
                                    }
                                }
                            }
                        } else when (item) {
                            is StreamInfoItem -> {
                                val isMembership = browse.channel != null && browse.channelTab == VideoRepository.TAB_MEMBERSHIP
                                VideoCard(
                                    item,
                                    isMembership = isMembership,
                                    onNotInterested = if (isFeed && category == homeChipTitle) {
                                        { hidden.hideVideo(item.url) }
                                    } else null,
                                    onHideChannel = if (isFeed && category == homeChipTitle && !item.uploaderUrl.isNullOrBlank()) {
                                        { hidden.hideChannel(item.uploaderUrl) }
                                    } else null,
                                    onChannel = { openChannel(it) }
                                ) { open(item) }
                            }
                            is ChannelInfoItem -> ChannelCard(item) { openChannel(item.url) }
                            is PlaylistInfoItem -> PlaylistCard(item) { openPlaylist(item.url) }
                        }
                    }
                    if (browse.loading && (page != null || currentPlaylist != null)) item(span = { GridItemSpan(maxLineSpan) }) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    if (browse.error != null && (page != null || currentPlaylist != null)) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(browse.error ?: "", Modifier.weight(1f)); TextButton(onClick = { loadMoreBrowse() }) { Text("Try again") } } }
                    if (isSearchResults && searchState.moreLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    if (isSearchResults && searchState.moreError != null) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(searchState.moreError ?: "", Modifier.weight(1f)); TextButton(onClick = { moreSearchResults() }) { Text("Try again") } } }
                    if (isFeed && feedLoading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
                    if (isFeed && feedError != null) item(span = { GridItemSpan(maxLineSpan) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(feedError ?: "", Modifier.weight(1f), color = Color.LightGray); TextButton(onClick = { loadMoreFeed() }) { Text("Try again") } } }
                    if (!loading && error == null && visibleItems.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text(if (page != null) "No videos available yet." else "No results.", color = Color.LightGray) }
                }
                if (isFeed) PullToRefreshDefaults.Indicator(state = refreshState, isRefreshing = refreshingFeed, modifier = Modifier.align(Alignment.TopCenter))
                }
            }
            }
            val currentNavTab = if (searchState.active || (searchState.mode && !shortsMode)) "search" else if (mainTab == "subscriptions") "home" else mainTab
            if (showFeed) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    HomeNavigation(selected = currentNavTab, onSelect = selectTab)
                }
            }
          }
        }
        if (showHistory && (selected == null || minimized)) {
            if (accountHistoryLoggedIn && !accountHistory.deviceHistory) AccountHistoryScreen(
                entries = accountHistory.entries, loading = accountHistory.loading, error = accountHistory.error, hasMore = accountHistory.continuation != null,
                onBack = { showHistory = false }, onOpen = { entry -> openAccountHistoryEntry(entry) },
                onRefresh = { accountHistory.request++ }, onMore = { accountHistory.nextRequest++ }, onDeviceHistory = { accountHistory.deviceHistory = true },
                onManage = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/feed/history"))) } },
                onNavigate = { selectTab(it) }, historyActions = historyActions,
            ) else WatchHistoryScreen(watchHistory, onBack = { if (accountHistoryLoggedIn && accountHistory.deviceHistory) accountHistory.deviceHistory = false else showHistory = false }, onOpen = { entry -> openHistoryEntry(entry) }, onClear = { WatchHistory.clear(watchPrefs); watchHistory = emptyList() }, onRemove = { entry -> WatchHistory.remove(watchPrefs, entry.url); watchHistory = WatchHistory.read(watchPrefs) }, historyActions = historyActions, onResetRecommendations = if (hidden.videos.isNotEmpty() || hidden.channels.isNotEmpty()) {{ hidden.reset() }} else null)
        }
        accountPlaylist?.takeIf { !showHistory && !shortsMode && (selected == null || minimized) }?.let { collection ->
            AccountPlaylistScreen(collection, onBack = { accountPlaylist = null }, onNavigate = selectTab,
                onOpen = { video, entries -> playAccountPlaylist(collection, entries, video, shortsAsHistory = true) },
                onPlay = { entries -> entries.firstOrNull()?.let { playAccountPlaylist(collection, entries, it, shortsAsHistory = false) } },
                onSave = historyActions.save, onShare = historyActions.share)
        }
        val playerLiveKey = selected?.takeIf { !shortsMode }?.let { videoKey(it.url) }
        val playerLive = rememberPlayerLive(player, playerLiveKey)
        val activeChatState = selected?.takeIf { !shortsMode }?.let { current ->
            val streamType = current.streamType
            val liveStream = streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
            val postLive = streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_STREAM ||
                streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_AUDIO_STREAM
            rememberLiveChat(current.url, liveStream || playerLive, postLive, player)
        }
        // Move the opaque watch page down with the player before fading it out.
        if (info != null && !shortsMode) Box(if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val isLive = info.isOngoingLive() || playerLive
            val isPostLive = info.streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_STREAM ||
                info.streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_AUDIO_STREAM
            val chatState = activeChatState ?: rememberLiveChat(info.url, isLive, isPostLive, player)
            var showComments by remember(info.url) { mutableStateOf(false) }
            var showChat by remember(info.url, isLive) { mutableStateOf(isLive) }
            var showDescription by remember(info.url) { mutableStateOf(false) }
            var showAsk by remember(info.url) { mutableStateOf(false) }
            // Ask hanya ada bila server mengirim panelnya untuk akun dan video ini (lihat AskYouTube.panel); tanpa itu tombolnya tidak muncul.
            val askPanel by produceState<AskPanel?>(null, info.url, YouTubeAuth.isLoggedIn) { value = null; value = AskYouTube.panel(info.url) }
            // Status akun dibaca ulang saat video dibuka. Generasi menolak respons basi yang dimulai sebelum
            // tindakan pengguna, tetapi tetap mengizinkan pembacaan baru memperbaiki nilai uncertain/known.
            LaunchedEffect(info.url, YouTubeAuth.sessionRevision) {
                val revision = YouTubeAuth.sessionRevision
                if (!YouTubeAuth.isLoggedIn) return@LaunchedEffect
                val videoId = info.id.ifBlank { videoKey(info.url) }
                val channelId = Regex("/channel/(UC[\\w-]{22})").find(info.uploaderUrl.orEmpty())?.groupValues?.get(1)
                val generation = Engagement.generation(videoId, channelId)
                val snapshot = try { EngagementSource.fetch(videoId, revision) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                if (snapshot != null) Engagement.refresh(videoId, snapshot, revision, generation, channelId)
                // Status "tersimpan" juga berasal dari server; nilai baru menggantikan status lama bila belum ada klik.
                val saveGeneration = Engagement.generation(videoId, channelId).save
                val choices = try { AccountLibrary.choices(videoId) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                if (choices != null && revision == YouTubeAuth.sessionRevision) {
                    Engagement.refresh(videoId, EngagementSnapshot(saved = choices.any { it.selected }), revision,
                        EngagementGeneration(save = saveGeneration), channelId)
                }
            }
            val askScope = rememberCoroutineScope()
            val askSession = remember(info.url, askPanel != null) { askPanel?.let { AskSession(info.url, it, askScope) { player.currentPosition } } }
            val comments = rememberComments(info.url)
            val watchRecommendations = rememberWatchRecommendations(info, YouTubeAuth.sessionRevision)
            WatchScreen(showDescription = showDescription, showComments = showComments, showChat = showChat, showAsk = showAsk && askSession != null, ask = askSession, onAsk = { chaptersOpen = false; showComments = false; showDescription = false; showChat = false; showAsk = true }, showChapters = chaptersOpen, onClosePanel = { showDescription = false; showComments = false; showChat = false; showAsk = false; chaptersOpen = false }, watchAlpha = 1f - watchFade, watchOffset = if (fullscreen) 0f else verticalProgress, info = info, isLive = isLive, comments = comments, chatState = chatState, onComments = { chaptersOpen = false; showDescription = false; showChat = false; showAsk = false; showComments = true }, onDescription = { chaptersOpen = false; showComments = false; showChat = false; showAsk = false; showDescription = true }, onChat = { chaptersOpen = false; showComments = false; showDescription = false; showAsk = false; showChat = true },
                onChapters = { showComments = false; showDescription = false; showChat = false; showAsk = false; chaptersOpen = true },
                onLink = { openLink(info, it) }, onSeek = { player.seekTo(it * 1000L); player.play() }, onShare = { shareVideo(info) }, onRemix = { closePlayer(); selectTab("shorts") }, onClip = { clipVideoLink(info) }, onCopy = { copyVideoLink(info) }, player = player, hidden = fullscreen && !exitDragging, onFullscreen = { setFullscreen(!fullscreen) }, recommendations = watchRecommendations, error = error,
                onBack = { back() }, onSettings = { qualityInitialPage = "menu"; showQuality = true }, onDownload = { showDownloads = true },
                onChannel = { openChannel(info.uploaderUrl ?: "") }, onOpenChannel = { openChannel(it) }, onVideo = { open(it) },
                engagement = run {
                    val key = videoKey(info.url)
                    val channelId = Regex("/channel/(UC[\\w-]{22})").find(info.uploaderUrl.orEmpty())?.groupValues?.get(1)
                    val ratingState = Engagement.rating(key)
                    val subscriptionState = channelId?.let { Engagement.subscription(it) }
                    WatchEngagement(
                        rating = ratingState.value ?: 0,
                        saved = Engagement.saved(key).saved == true,
                        subscribed = subscriptionState?.subscribed == true,
                        onLike = { rateWatch(info, 1) },
                        onDislike = { rateWatch(info, -1) },
                        onSave = { saveWatch(info) },
                        onSubscribe = { subscribeWatch(info, true) },
                        onUnsubscribe = { subscribeWatch(info, false) },
                        likeDelta = ratingState.likeDelta,
                        ratingUncertain = ratingState.uncertain,
                        subscriptionUncertain = subscriptionState?.uncertain == true,
                        notificationPreference = subscriptionState?.notificationPreference ?: NotificationPreference.PERSONALIZED,
                        onNotificationPreference = { pref -> channelId?.let { setChannelNotificationPref(it, pref) } }
                    )
                },
                queue = queue,
                playlistTitle = activePlaylistTitle,
                playlistId = activePlaylistId,
                watchAspect = if (wideWatch) (16f / 9f) else run { val a = videoAspectOf(player.videoSize); if (a > 0f) a.coerceIn(4f / 3f, 21f / 9f) else 16f / 9f },
                onSelectQueueItem = { item ->
                    openUrl(item.url, PendingVideo(item.url, item.name.orEmpty(), item.uploaderName.orEmpty(), item.thumbnails.lastOrNull()?.url), resumeLocal = false)
                },
                onOpenFullPlaylist = activePlaylistId?.takeUnless { it.startsWith("RD") }?.let { pid ->
                    {
                        minimized = true
                        showHistory = false
                        accountPlaylist = AccountPlaylist(pid.removePrefix("VL"), activePlaylistTitle ?: "Playlist", queue.firstOrNull()?.thumbnails?.lastOrNull()?.url.orEmpty())
                    }
                })
            if (showQuality) QualityDialog(info, quality, selectedAudio, selectedSubtitle, autoplay, maxVideoHeight, player, qualityInitialPage, fullscreen, qualityMode,
                useManifestAudio = usesAdaptiveAudio(info), onAudioPreference = { changePreferredAudio(info, it) },
                onSelectManifestAudio = { group, indices ->
                    manualAudioOverride = true
                    val format = group.getTrackFormat(indices.first())
                    val lang = audioLocale(format.language)?.toLanguageTag()
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setPreferredAudioLanguage(lang)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, indices)).build()
                    val matching = audioTracks(info).filter { audioLocale(audioLanguage(it))?.language == audioLocale(lang)?.language && lang != null }
                    selectedAudio = matching.singleOrNull() ?: matching.firstOrNull { !format.label.isNullOrBlank() && it.audioTrackName.equals(format.label, ignoreCase = true) }
                    showQuality = false
                }, onSelectEmbedded = { metadata ->
                    manualAudioOverride = true
                    val height = quality?.height ?: player.videoFormat?.height?.takeIf { it > 0 } ?: maxVideoHeight
                    fun heightOf(video: VideoStream) = video.height.takeIf { it > 0 } ?: (video.resolution.filter(Char::isDigit).toIntOrNull() ?: 0)
                    val video = VideoRepository.embeddedAudio(info).filter { it.metadata.id == metadata.id }
                        .minByOrNull { kotlin.math.abs(heightOf(it.video) - height) }?.video
                    if (video == null) {
                        android.widget.Toast.makeText(context, "Audio selection is unavailable for this video format", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        try {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
                            playQuality(info, video, null, player.currentPosition.coerceAtLeast(0L), player.playWhenReady, forceDirect = true)
                            selectedAudio = null
                        } catch (e: Exception) { android.widget.Toast.makeText(context, "Failed to change audio: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
                    }
                    showQuality = false
                }, onQualityMode = { qualityMode = it }, onLock = { playerLocked = true; showQuality = false }, onMaxHeight = { height ->
                maxVideoHeight = height; watchPrefs.edit().putInt("max_video_height", height).apply()
                if (quality == null && qualityMode == "auto") player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE, height).build()
            }, onAutoplay = { autoplay = it; watchPrefs.edit().putBoolean("autoplay", it).apply() }, onSubtitle = { subtitle -> changeSubtitle(info, subtitle) }, onDismiss = { showQuality = false }, onSelect = { stream ->
                if (stream != quality) {
                    val position = player.currentPosition.coerceAtLeast(0L)
                    val playing = player.playWhenReady
                    val audio = selectedAudio ?: selectAudioTrack(audioTracks(info), PlayerSettings.audioPreference, java.util.Locale.getDefault())
                    try { playQuality(info, stream, audio, position, playing, forceDirectPlayback && stream?.isVideoOnly == true) }
                    catch (e: Exception) { android.widget.Toast.makeText(context, "Failed to change quality: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
                }
                showQuality = false
            }, onSelectAudio = { audio ->
                manualAudioOverride = true
                val language = audioLanguage(audio)
                val manifestHasLanguage = language != null && player.currentTracks.groups.any { group ->
                    group.type == C.TRACK_TYPE_AUDIO && (0 until group.length).any { index ->
                        group.isTrackSupported(index) && audioLocale(group.getTrackFormat(index).language)?.language == audioLocale(language)?.language
                    }
                }
                if (usesAdaptiveAudio(info) && manifestHasLanguage) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setPreferredAudioLanguage(language).build()
                    selectedAudio = audio
                } else if (audio != selectedAudio || quality?.isVideoOnly != true) {
                    val separate = VideoRepository.videos(info).filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                    // In auto/HLS mode `quality` is null; keep the height that is playing instead of falling to the lowest stream.
                    val currentHeight = quality?.height ?: player.videoFormat?.height?.takeIf { it > 0 } ?: maxVideoHeight
                    val video = quality?.takeIf { it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                        ?: separate.minByOrNull { kotlin.math.abs(it.height - currentHeight) }
                    if (video == null) {
                        android.widget.Toast.makeText(context, "Audio selection is unavailable for this video format", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        try {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO).setPreferredAudioLanguage(language).build()
                            playQuality(info, video, audio, player.currentPosition.coerceAtLeast(0L), player.playWhenReady, forceDirect = true)
                            selectedAudio = audio
                        } catch (e: Exception) { android.widget.Toast.makeText(context, "Failed to change audio: ${e.message}", android.widget.Toast.LENGTH_LONG).show() }
                    }
                }
                showQuality = false
            })
            if (showDownloads) DownloadDialog(info, onDismiss = { showDownloads = false }) { height, audio ->
                showDownloads = false
                startDownload(info, height, audio)
            }
        }
        if (shortsMode && !minimized) {
            val inTab = shorts.tabQueue && mainTab == "shorts"
            ShortsScreen(
                items = queue, startIndex = shorts.index, generation = shorts.generation, resolver = shortsResolver,
                activeUrl = selected?.url, player = player,
                emptyLoading = shorts.loading, emptyMessage = shorts.error, feedMessage = shorts.moreError,
                onSettled = { shorts.index = it }, onRetry = { shorts.request++ }, onRetryMore = { shorts.moreRequest++ },
                onRetryItem = { url ->
                    // Buang pemutaran yang gagal supaya efek pemutaran memuat ulang Short ini dari awal.
                    lastRecovered = null; player.stop(); player.clearMediaItems(); selected = null
                    shortsResolver.evict(url); shortsResolver.retry(url); shorts.retry++
                },
                onRate = { info, direction -> rateWatch(info, direction) },
                onNeedLogin = { showLoginDialog = true },
                onNotInterested = { item -> hidden.hideVideo(item.url) },
                onLink = { info, link -> openLink(info, link) }, onChannel = { openChannel(it) },
                onClose = if (inTab) null else {{ closePlayer() }},
                onShare = { shareVideo(it) },
                onSave = { id -> if (YouTubeAuth.isLoggedIn) savePlaylistVideoId = id else showLoginDialog = true },
                onSearch = { selectTab("home"); searchState.active = true },
                onDownload = { shorts.downloadInfo = it },
                bottomBar = { if (inTab) HomeNavigation(selected = "shorts", onSelect = selectTab) },
            )
            shorts.downloadInfo?.let { target ->
                DownloadDialog(target, onDismiss = { shorts.downloadInfo = null }) { height, audio ->
                    shorts.downloadInfo = null
                    startDownload(target, height, audio)
                }
            }
        }
        selected?.takeIf { pendingVideo == null && (!shortsMode || minimized) }?.let { playing ->
            val isPlayingLive = playing.isOngoingLive() || playerLive
            val playingPostLive = playing.streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_STREAM ||
                playing.streamType == org.schabi.newpipe.extractor.stream.StreamType.POST_LIVE_AUDIO_STREAM
            val playingChatState = activeChatState ?: rememberLiveChat(playing.url, isPlayingLive, playingPostLive, player)
            val insets = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            BoxWithConstraints(insets) {
                val wide = isWideWatchLayout(maxWidth, maxHeight)
                val rawVideoAspect = videoAspectOf(player.videoSize)
                val currentAspect = if (rawVideoAspect > 0f) rawVideoAspect else (16f / 9f)
                val watchAspect = if (wide) (16f / 9f) else currentAspect.coerceIn(4f / 3f, 21f / 9f)
                // Size and position of the watch layout (exitProgress 1). Fullscreen is the whole screen (exitProgress 0).
                val inlineWidth = if (wide) wideWatchPlayerWidth(maxWidth) else maxWidth
                val inlineHeight = inlineWidth / watchAspect
                val startX = 0.dp
                val limits = MiniLimits.of(maxWidth, maxHeight, if (searchState.active || fullscreen) 0.dp else 78.dp)
                miniState.limits = limits
                LaunchedEffect(limits) { miniState.settle(animate = minimized) }
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
                var fsComments by remember(playing.url) { mutableStateOf(false) }
                LaunchedEffect(fullscreen) { if (!fullscreen) fsComments = false }
                BackHandler(fullscreen && (fsComments || (chaptersOpen && playing.streamSegments.isNotEmpty()))) {
                    fsComments = false
                    chaptersOpen = false
                }
                FloatingPlayer(player = player, title = playing.name, zoomKey = playing.url, locked = playerLocked, onUnlock = { playerLocked = false }, isLive = isPlayingLive, autoplay = autoplay, hasSubtitles = playing.subtitles.any { it.isUrl && it.format?.suffix?.lowercase() in listOf("vtt", "ttml", "srt") }, subtitlesEnabled = selectedSubtitle != null, previewFrames = playing.previewFrames, segments = playing.streamSegments.orEmpty(), fullscreen = fullscreen, minimized = minimized, progress = collapse, travelDistance = dragTravel.coerceAtLeast(180.dp),
                    miniState = miniState, modifier = Modifier.offset { IntOffset(x.roundToPx(), y.roundToPx()) }.size(width, height), chatState = playingChatState,
                    recovering = refreshingPlaybackKey == videoKey(playing.url),
                    onRetryPlayback = { refreshPlayback(playing, player.currentPosition.coerceAtLeast(0L), true) },
                    channelName = playing.uploaderName, moreVideos = playing.relatedItems.filterIsInstance<StreamInfoItem>().filter { it.url != playing.url }.distinctBy { it.url }.take(12), onMoreVideo = { open(it) },
                    onCast = {
                        // Tanpa Google Cast SDK: buka pengaturan Cast/Smart View sistem untuk mencerminkan layar.
                        try { context.startActivity(Intent(android.provider.Settings.ACTION_CAST_SETTINGS)) }
                        catch (e: Exception) { android.widget.Toast.makeText(context, "Cast is unavailable on this device", android.widget.Toast.LENGTH_SHORT).show() }
                    },
                    actions = if (!fullscreen || shortsMode) null else {
                        val key = videoKey(playing.url)
                        FullscreenActions(
                            rating = Engagement.rating(key).value ?: 0, saved = Engagement.saved(key).saved == true,
                            onLike = { rateWatch(playing, 1) }, onDislike = { rateWatch(playing, -1) },
                            onComments = { chaptersOpen = false; fsComments = true },
                            onSave = { saveWatch(playing) },
                            onShare = { shareVideo(playing) }, onDownload = { showDownloads = true }, onCopyLink = { copyVideoLink(playing) },
                            onOpenInYouTube = { try { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=${playing.id.ifBlank { key }}"))) } catch (_: Exception) {} },
                        )
                    },
                    onChapters = if (playing.streamSegments.isNotEmpty()) { { fsComments = false; chaptersOpen = true } } else null,
                    commentsPanel = if (fullscreen && chaptersOpen && playing.streamSegments.isNotEmpty()) {
                        { panelModifier -> ChaptersPanel(playing, { player.currentPosition }, draggable = false, onDismiss = { chaptersOpen = false }, onSeek = { player.seekTo(it * 1000L); player.play() }, modifier = panelModifier, showHandle = false) }
                    } else if (fullscreen && fsComments) {
                        { panelModifier ->
                            val fsState = rememberComments(playing.url)
                            CommentsPanel(fsState, playing, draggable = false, onDismiss = { fsComments = false }, onLink = { openLink(playing, it) }, onChannel = { openChannel(it) }, modifier = panelModifier, showHandle = false)
                        }
                    } else null,
                    onNext = nextItem(playing)?.let { next -> { openUrl(next.url) } },
                    onPrevious = queue.indexOfFirst { videoKey(it.url) == videoKey(playing.url) }.let { index -> queue.getOrNull(index - 1) }?.let { previous -> { openUrl(previous.url) } },
                    onAutoplay = { autoplay = !autoplay; watchPrefs.edit().putBoolean("autoplay", autoplay).apply() },
                    onSubtitles = {
                        val subtitle = if (selectedSubtitle != null) null else lastSubtitle ?: playing.subtitles.firstOrNull { it.isUrl && it.format?.suffix?.lowercase() in listOf("vtt", "ttml", "srt") }
                        changeSubtitle(playing, subtitle)
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
        pendingVideo?.takeIf { !fullscreen }?.let { pending ->
            BackHandler(enabled = !minimized) { dragProgress = null; minimized = true }
            if (watchFade < 1f) PendingWatchScreen(pending, watchAlpha = 1f - watchFade, watchOffset = verticalProgress)
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                val wide = isWideWatchLayout(maxWidth, maxHeight)
                val inlineWidth = if (wide) wideWatchPlayerWidth(maxWidth) else maxWidth
                val inlineHeight = inlineWidth * 9f / 16f
                val limits = MiniLimits.of(maxWidth, maxHeight, if (searchState.active) 0.dp else 78.dp)
                miniState.limits = limits
                LaunchedEffect(limits) { miniState.settle(animate = minimized) }
                val miniWidth = (miniState.width.value.takeIf { it > 0f } ?: limits.default).dp
                val miniHeight = miniWidth * 9f / 16f
                val miniPosition = miniState.position.value
                val width = inlineWidth + (miniWidth - inlineWidth) * collapse
                val height = inlineHeight + (miniHeight - inlineHeight) * collapse
                val x = miniPosition.x.dp * collapse
                val y = miniPosition.y.dp * verticalProgress
                val travel = ((limits.areaHeight - limits.bottomBar - limits.inset).dp - miniHeight).coerceAtLeast(180.dp)
                PendingFloatingPlayer(
                    pending = pending, minimized = minimized, progress = collapse, miniState = miniState,
                    travelDistance = travel,
                    modifier = Modifier.offset { IntOffset(x.roundToPx(), y.roundToPx()) }.size(width, height),
                    onMinimize = { dragProgress = null; minimized = true },
                    onExpand = { minimized = false }, onClose = { closePlayer() },
                    onRetry = { openUrl(pending.url, pending, resumeLocal = pending.resumeLocal, keepMinimized = true) },
                    onDrag = { dragProgress = it }, onCancelDrag = { dragProgress = null },
                    onRelease = { value, velocity -> minimized = swipeCommits(value, velocity); dragProgress = null },
                )
                if (minimized && miniState.stashed != 0 && progress > .9f) MiniPlayerHandle(miniState.stashed, wide,
                    Modifier.align(if (miniState.stashed < 0) Alignment.TopStart else Alignment.TopEnd)
                        .offset(y = (miniPosition.y.dp + miniHeight / 2 - 54.dp).coerceAtLeast(0.dp))) { miniState.unstash() }
            }
        }
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) { OfflineMiniOverlay() }
      }
    }
}

