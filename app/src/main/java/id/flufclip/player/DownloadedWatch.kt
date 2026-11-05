package id.flufclip.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

internal fun downloadNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadedWatch(
    saved: SavedDownload, downloads: List<SavedDownload>, player: ExoPlayer, playing: Boolean, buffering: Boolean,
    hasSubtitles: Boolean, fullscreen: Boolean, autoplay: Boolean, playbackError: String?,
    onBack: () -> Unit, onFullscreen: () -> Unit, onAutoplay: () -> Unit,
    onPick: (SavedDownload) -> Unit, onList: () -> Unit, onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var online by remember(saved.uri) { mutableStateOf<StreamInfo?>(null) }
    var loadAttempt by remember(saved.uri) { mutableIntStateOf(0) }
    // Optional enrichment only. The player is already reading the saved content URI independently.
    LaunchedEffect(saved.uri, loadAttempt) {
        online = null
        if (saved.video.sourceUrl.isNotBlank() && downloadNetworkAvailable(context)) {
            online = withTimeoutOrNull(12000) {
                try { VideoRepository.resolve(saved.video.sourceUrl, context) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            }
        }
    }
    val source = online?.url ?: saved.video.sourceUrl
    val comments = remember(source) { CommentsState(source, scope).apply { loading = false } }
    LaunchedEffect(online?.url) { if (online != null) comments.load() }
    val chat = remember(source) { LiveChatState(source, false, false, scope).apply { loading = false; error = "Chat is unavailable for this file" } }
    var panel by remember(saved.uri) { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    var downloadedMenu by remember { mutableStateOf(false) }
    var loginNeeded by remember { mutableStateOf(false) }
    var subtitlesEnabled by remember(saved.uri) { mutableStateOf(C.TRACK_TYPE_TEXT !in player.trackSelectionParameters.disabledTrackTypes) }
    val revision by SavedDownloads.revision.collectAsState()
    val title = online?.name ?: saved.title
    val uploader = online?.uploaderName ?: saved.video.uploader
    val channel = online?.uploaderUrl ?: saved.video.channelUrl
    val channelId = Regex("/channel/(UC[\\w-]{22})").find(channel)?.groupValues?.get(1)
    val ratingState = Engagement.rating(videoKey(source))
    val savedState = Engagement.saved(videoKey(source))
    val subscriptionState = channelId?.let { Engagement.subscription(it) }
    // Status akun untuk file offline tetap mengikuti sesi aktif; file lokal sendiri tidak bergantung pada login.
    LaunchedEffect(online?.url, YouTubeAuth.sessionRevision) {
        val session = YouTubeAuth.sessionRevision
        val info = online ?: return@LaunchedEffect
        if (!YouTubeAuth.isLoggedIn) return@LaunchedEffect
        val videoId = info.id.ifBlank { videoKey(source) }
        val generation = Engagement.generation(videoId, channelId)
        val snapshot = try { EngagementSource.fetch(videoId, session) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        if (snapshot != null) Engagement.refresh(videoId, snapshot, session, generation, channelId)
        val saveGeneration = Engagement.generation(videoId, channelId).save
        val choices = try { AccountLibrary.choices(videoId) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        if (choices != null && session == YouTubeAuth.sessionRevision) {
            Engagement.refresh(videoId, EngagementSnapshot(saved = choices.any { it.selected }), session,
                EngagementGeneration(save = saveGeneration), channelId)
        }
    }
    val index = downloads.indexOfFirst { it.uri == saved.uri }
    val previous = downloads.getOrNull(index - 1)
    val next = downloads.getOrNull(index + 1)
    val related = online?.relatedItems?.filterIsInstance<StreamInfoItem>()?.filter { it.url != source }?.distinctBy { it.url }?.take(16).orEmpty()
    fun message(text: String) { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    fun account(action: suspend (Int) -> String?, success: () -> Unit) {
        if (!YouTubeAuth.isLoggedIn) { loginNeeded = true; return }
        val session = YouTubeAuth.sessionRevision
        scope.launch {
            val failure = action(session)
            if (session != YouTubeAuth.sessionRevision) return@launch
            if (failure == null) success() else message(failure)
        }
    }
    fun rate(direction: Int) {
        if (!YouTubeAuth.isLoggedIn) { loginNeeded = true; return }
        val videoId = videoKey(source)
        val first = Engagement.toggleRating(videoId, direction) ?: return
        scope.launch {
            var request: RatingRequest? = first
            try {
                while (request != null) {
                    val current = request ?: break
                    val result = sendRating(current.videoId, ratingAction(current.target), current.revision)
                    Engagement.completeRating(current, result)
                    val failure = result.messageOrNull()
                    if (failure != null) {
                        request = null
                        message(failure)
                        if (result is MutationResult.Uncertain) scope.reconcileEngagement(videoId, channelId, current.revision)
                        break
                    }
                    request = Engagement.followUpRating(videoId)
                }
            } finally {
                // Minimalkan/menutup player membatalkan scope ini; jangan tinggalkan status pending terkunci.
                request?.let(Engagement::abandonRating)
            }
        }
    }
    fun subscribe(value: Boolean) {
        val id = channelId
        val videoId = videoKey(source)
        if (id == null) { message("Channel information is unavailable"); return }
        if (!YouTubeAuth.isLoggedIn) { loginNeeded = true; return }
        val first = Engagement.setSubscription(id, value) ?: return
        scope.launch {
            var request: SubscriptionRequest? = first
            try {
                while (request != null) {
                    val current = request ?: break
                    val result = sendSubscription(current.channelId, current.target, current.revision)
                    Engagement.completeSubscription(current, result)
                    val failure = result.messageOrNull()
                    if (failure != null) {
                        request = null
                        message(failure)
                        if (result is MutationResult.Uncertain) scope.reconcileEngagement(videoId, channelId, current.revision)
                        break
                    }
                    request = Engagement.followUpSubscription(id)
                }
            } finally {
                request?.let(Engagement::abandonSubscription)
            }
        }
    }
    fun copyLink(timed: Boolean = false) {
        if (source.isBlank()) { message("The original link is unavailable for this older file"); return }
        val link = if (timed) "$source${if (source.contains('?')) "&" else "?"}t=${player.currentPosition.coerceAtLeast(0) / 1000}" else source
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(title, link))
        message("Link copied")
    }
    fun share() {
        if (source.isBlank()) { message("The original link is unavailable for this older file"); return }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, source), "Share"))
    }
    val engagement = WatchEngagement(ratingState.value ?: 0, savedState.saved == true, subscriptionState?.subscribed == true,
        onLike = { rate(1) }, onDislike = { rate(-1) },
        onSave = { account({ session -> ShortsFeed.saveToWatchLater(videoKey(source), session) }) { Engagement.setWatchLater(videoKey(source), true); message("Saved to Watch later") } },
        onSubscribe = { subscribe(true) }, onUnsubscribe = { subscribe(false) },
        likeDelta = ratingState.likeDelta, ratingUncertain = ratingState.uncertain, subscriptionUncertain = subscriptionState?.uncertain == true)
    BackHandler(panel != null) { panel = null }

    @Composable fun PlayerSurface(modifier: Modifier) {
        Box(modifier.background(Color.Black).offlineMinimizeGesture(onBack)) {
            AndroidView(factory = { PlayerView(it).apply { useController = false; this.player = player } },
                update = { it.player = player; it.useController = false }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())
            PlayerControlsOverlay(
                player, playing, buffering, fullscreen, title, false, autoplay, hasSubtitles, subtitlesEnabled,
                previewFrames = emptyList(), segments = emptyList(), onBack = onBack, onSettings = { settings = true },
                onAutoplay = onAutoplay, onSubtitles = {
                    subtitlesEnabled = !subtitlesEnabled
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled).build()
                }, onFullscreen = onFullscreen, onPrevious = previous?.let { { onPick(it) } }, onNext = next?.let { { onPick(it) } },
                channelName = uploader, onRetryPlayback = onRetry)
            if (playbackError != null) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .8f)).padding(12.dp)) {
                Text(playbackError, fontSize = 13.sp)
                TextButton(onClick = onRetry) { Text("Try again") }
            }
        }
    }
    @Composable fun Metadata() {
        val info = online
        if (info != null) VideoDetails(info, comments, chat, null,
            onDownload = { downloadedMenu = true }, onChannel = { if (channel.isNotBlank()) openMainFromDownloads(context, channel) },
            onComments = { panel = "comments" }, onDescription = { panel = "description" }, onShare = { share() },
            onRemix = { openMainFromDownloads(context, source) }, onClip = { copyLink(true) }, onCopy = { copyLink() },
            onChat = { message("Chat is unavailable for this file") }, isLive = false, engagement = engagement, downloaded = true)
        else OfflineVideoDetails(saved, ratingState.value ?: 0, engagement, onDescription = { panel = "description" },
            onDownloaded = { downloadedMenu = true }, onShare = { share() }, onChannel = { if (channel.isNotBlank()) openMainFromDownloads(context, channel) },
            onComments = { panel = "comments" }, onRefresh = { loadAttempt++ })
    }
    @Composable fun QueueAndRelated() {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (related.isNotEmpty()) {
                items(related, key = { it.url }) { item -> VideoCard(item, Modifier.padding(horizontal = 12.dp), resolveAvatar = false) {
                    val local = downloads.firstOrNull { it.video.sourceUrl.isNotBlank() && videoKey(it.video.sourceUrl) == videoKey(item.url) }
                    if (local != null) onPick(local) else openMainFromDownloads(context, item.url)
                } }
            } else {
                items(downloads.filter { it.uri != saved.uri }, key = { it.uri }) { item -> DownloadedRow(item, revision, onClick = { onPick(item) }) }
            }
        }
    }
    @Composable fun QueueFooter() {
        Row(Modifier.fillMaxWidth().background(Color(0xFF211B18), RoundedCornerShape(12.dp)).clickable(onClick = onList).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.yt_download_queue), null, Modifier.size(26.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(if (next == null) "Akhir antrean" else "Download queue", fontSize = 14.sp)
                Text("Downloads · ${index + 1}/${downloads.size}", fontSize = 12.sp, color = Muted)
            }
            Icon(Icons.Default.KeyboardArrowUp, "View downloads")
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = isWideWatchLayout(maxWidth, maxHeight)
        val rawVideoAspect = videoAspectOf(player.videoSize)
        val currentAspect = if (rawVideoAspect > 0f) rawVideoAspect else (16f / 9f)
        val watchAspect = if (wide) (16f / 9f) else currentAspect.coerceIn(4f / 3f, 21f / 9f)
        if (fullscreen) PlayerSurface(Modifier.fillMaxSize())
        else if (wide) {
            val available = maxWidth - 24.dp
            val playerWidth = available - minOf(available / 3f, 400.dp)
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(playerWidth).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    PlayerSurface(Modifier.fillMaxWidth().aspectRatio(watchAspect))
                    Metadata()
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(end = 12.dp)) {
                    Box(Modifier.weight(1f)) { QueueAndRelated() }
                    QueueFooter()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                PlayerSurface(Modifier.fillMaxWidth().aspectRatio(watchAspect))
                LazyColumn(Modifier.weight(1f)) {
                    item { Metadata() }
                    item { QueueFooter() }
                    if (related.isNotEmpty()) items(related, key = { it.url }) { item -> VideoCard(item, Modifier.padding(12.dp), resolveAvatar = false) {
                        val local = downloads.firstOrNull { it.video.sourceUrl.isNotBlank() && videoKey(it.video.sourceUrl) == videoKey(item.url) }
                        if (local != null) onPick(local) else openMainFromDownloads(context, item.url)
                    } }
                    else items(downloads.filter { it.uri != saved.uri }, key = { it.uri }) { item -> DownloadedRow(item, revision, onClick = { onPick(item) }) }
                }
            }
        }
        if (panel != null && !fullscreen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)).clickable { panel = null })
            val panelModifier = Modifier.align(Alignment.BottomEnd).then(if (wide) Modifier.width((maxWidth / 3f).coerceAtLeast(320.dp)).fillMaxHeight() else Modifier.fillMaxWidth().fillMaxHeight(.7f))
            val info = online
            if (info != null && panel == "comments") CommentsPanel(comments, info, true, { panel = null }, { link -> openMainFromDownloads(context, link) }, { url -> openMainFromDownloads(context, url) }, panelModifier)
            else if (info != null) DescriptionPanel(info, true, { panel = null }, { link -> openMainFromDownloads(context, link) }, { seconds -> player.seekTo(seconds * 1000L) }, { if (channel.isNotBlank()) openMainFromDownloads(context, channel) }, panelModifier)
            else androidx.compose.material3.Surface(panelModifier, color = Background, shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (panel == "comments") "Comments" else "Description", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { panel = null }) { Icon(Icons.Default.Close, "Close") }
                    }
                    Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState())) {
                        Text(if (panel == "comments") "Comments require an internet connection. Saved videos can still be played." else saved.video.description.ifBlank { "No description was saved for this video." }, fontSize = 14.sp)
                        if (panel == "comments") TextButton(onClick = { panel = null; loadAttempt++ }) { Text("Load online information") }
                    }
                }
            }
        }
    }
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }, containerColor = Surface, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text("Video settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            val height = player.videoSize.height
            Text(if (height > 0) "Saved file quality · ${height}p" else if (saved.mime.startsWith("audio/")) "Saved audio" else "Saved file quality", Modifier.padding(vertical = 14.dp), color = Muted)
            Text("Playback speed", fontSize = 14.sp)
            listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                Row(Modifier.fillMaxWidth().clickable { player.setPlaybackSpeed(speed); settings = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (speed == 1f) "Normal" else "${speed}x", Modifier.weight(1f))
                    if (player.playbackParameters.speed == speed) Icon(Icons.Default.Check, null)
                }
            }
        }
    }
    if (downloadedMenu) AlertDialog(onDismissRequest = { downloadedMenu = false }, title = { Text("Downloaded") },
        text = { Text("${saved.title}\n${Formatter.formatFileSize(context, saved.bytes)} · Saved on device\nThis video is playing from a saved file.") },
        confirmButton = { TextButton(onClick = { downloadedMenu = false; onList() }) { Text("View downloads") } },
        dismissButton = { TextButton(onClick = { downloadedMenu = false }) { Text("Close") } })
    if (loginNeeded) AlertDialog(onDismissRequest = { loginNeeded = false }, title = { Text("Login ke YouTube") }, text = { Text("Sign in from the You tab to rate, save, or subscribe.") },
        confirmButton = { TextButton(onClick = { loginNeeded = false; openMainFromDownloads(context, "flufplay-tab:you") }) { Text("Open account") } }, dismissButton = { TextButton(onClick = { loginNeeded = false }) { Text("Cancel") } })
}

@Composable
private fun OfflineVideoDetails(saved: SavedDownload, rating: Int, engagement: WatchEngagement, onDescription: () -> Unit, onDownloaded: () -> Unit, onShare: () -> Unit, onChannel: () -> Unit, onComments: () -> Unit, onRefresh: () -> Unit) {
    val video = saved.video
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text(saved.title, Modifier.clickable(onClick = onDescription), fontSize = 18.sp, maxLines = 3)
        Row(Modifier.padding(top = 8.dp).clickable(onClick = onDescription), verticalAlignment = Alignment.CenterVertically) {
            Text(if (video.views >= 0) "${formatCount(video.views)} penayangan" else "Saved video", fontSize = 13.sp, color = Muted)
            Text("  …more", fontSize = 13.sp)
        }
        if (video.uploader.isNotBlank()) Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(video.avatar.takeIf { isLocalDownloadUri(it) }, video.uploader, 32, Modifier.clickable(onClick = onChannel))
            Text(video.uploader, Modifier.weight(1f).padding(start = 12.dp), fontSize = 15.sp)
            if (video.subscribers >= 0) Text(formatCount(video.subscribers), Modifier.padding(end = 8.dp), fontSize = 13.sp, color = Muted)
            if (video.channelUrl.isNotBlank()) SubscribeButton(engagement.subscribed, engagement.onSubscribe, engagement.onUnsubscribe, uncertain = engagement.subscriptionUncertain, channelName = video.uploader)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (video.sourceUrl.isNotBlank()) {
                LikeDislikePill((if (video.likes >= 0) formatCount((video.likes + engagement.likeDelta).coerceAtLeast(0)) else "Like") + if (engagement.ratingUncertain) "?" else "", rating, engagement.onLike, engagement.onDislike)
                WatchChip(R.drawable.yt_w_share, "Share", onShare)
                WatchChip(if (engagement.saved) R.drawable.yt_w_save_on else R.drawable.yt_w_save, if (engagement.saved) "Saved" else "Save", engagement.onSave)
            }
            WatchChip(R.drawable.yt_watch_downloaded_arrow, "Downloaded", onDownloaded)
        }
        Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).clickable(onClick = onComments).padding(12.dp)) {
            Text("Comments", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("Available when connected to the internet", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = Muted)
        }
        if (video.sourceUrl.isNotBlank()) TextButton(onClick = onRefresh) { Text("Load online information", fontSize = 12.sp) }
    }
}
