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

/** Load the watch feed independently of whichever extractor supplied playback. */
@Composable internal fun rememberWatchRecommendations(info: StreamInfo, revision: Int): List<StreamInfoItem> {
    val id = InnerTubePlayer.videoIdOf(info.url) ?: videoKey(info.url)
    fun itemKey(item: StreamInfoItem) = InnerTubePlayer.videoIdOf(item.url) ?: videoKey(item.url)
    val initial = remember(info) { info.relatedItems.filterIsInstance<StreamInfoItem>().filter { itemKey(it) != id }.distinctBy { itemKey(it) } }
    var recommendations by remember(info, revision) { mutableStateOf(initial) }
    LaunchedEffect(info, revision) {
        try {
            val items = InnerTubePlayer.related(id, 30).filter { itemKey(it) != id }.distinctBy { itemKey(it) }
            if (revision == YouTubeAuth.sessionRevision && items.isNotEmpty()) recommendations = items
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Keep the extractor recommendations if the watch feed is unavailable. */ }
    }
    return recommendations
}

@Composable internal fun WatchScreen(
    showDescription: Boolean, showComments: Boolean, showChat: Boolean, showAsk: Boolean, ask: AskSession?, onAsk: () -> Unit,
    showChapters: Boolean, onChapters: () -> Unit, onClosePanel: () -> Unit, watchAlpha: Float, watchOffset: Float,
    info: StreamInfo, isLive: Boolean, comments: CommentsState, chatState: LiveChatState,
    onComments: () -> Unit, onDescription: () -> Unit, onChat: () -> Unit, onLink: (String) -> Unit, onSeek: (Int) -> Unit,
    onShare: () -> Unit, onRemix: () -> Unit, onClip: () -> Unit, onCopy: () -> Unit, player: ExoPlayer, hidden: Boolean,
    onFullscreen: () -> Unit, recommendations: List<StreamInfoItem>, error: String?, onBack: () -> Unit,
    onSettings: () -> Unit, onDownload: () -> Unit, onChannel: () -> Unit, onOpenChannel: (String) -> Unit,
    onVideo: (StreamInfoItem) -> Unit, engagement: WatchEngagement,
    queue: List<StreamInfoItem> = emptyList(),
    playlistTitle: String? = null,
    playlistId: String? = null,
    onSelectQueueItem: (StreamInfoItem) -> Unit = {},
    onOpenFullPlaylist: (() -> Unit)? = null,
    watchAspect: Float = 16f / 9f,
) {
    if (hidden) {
        return
    }
    val hasPlaylist = queue.isNotEmpty() || !playlistId.isNullOrBlank()
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = watchAlpha; translationY = size.height * watchOffset }.background(Background)) {
        val wide = isWideWatchLayout(maxWidth, maxHeight)
        val effectiveAspect = if (wide) (16f / 9f) else watchAspect
        var showPlaylist by remember(playlistId, queue.isNotEmpty(), wide) {
            mutableStateOf(wide && hasPlaylist && queue.isNotEmpty())
        }
        val otherPanel = when {
            showChapters && info.streamSegments.isNotEmpty() -> "chapters"
            showDescription -> "description"
            showComments -> "comments"
            showChat -> "chat"
            showAsk && ask != null -> "ask"
            else -> null
        }
        val panel = otherPanel ?: if (showPlaylist && hasPlaylist) "playlist" else null
        val dismissActivePanel: () -> Unit = {
            if (otherPanel != null) onClosePanel() else showPlaylist = false
        }
        val togglePlaylistPanel: () -> Unit = {
            if (otherPanel != null) onClosePanel()
            showPlaylist = !showPlaylist
        }
        BackHandler(panel != null) { dismissActivePanel() }
        @Composable fun Panel(kind: String, draggable: Boolean, rawModifier: Modifier, showCount: Boolean = true, showHandle: Boolean = true) {
            // Panel digambar di atas daftar video/rincian. Latar saja tidak menghentikan sentuhan, jadi klik pada area kosong panel
            // diteruskan ke kartu video di belakangnya; pointerInput kosong membuat panel menjadi sasaran sentuhan dan menahan peneruskan itu.
            val modifier = rawModifier.pointerInput(Unit) { }
            if (kind == "chapters") ChaptersPanel(info, { player.currentPosition }, draggable, dismissActivePanel, onSeek, modifier, showHandle = showHandle)
            else if (kind == "description") DescriptionPanel(info, draggable, dismissActivePanel, onLink, onSeek, onChannel, modifier, showHandle = showHandle, onChapters = onChapters)
            else if (kind == "ask" && ask != null) AskPanel(ask, draggable, dismissActivePanel, onSeek, modifier)
            else if (kind == "chat") LiveChatPanel(chatState, draggable, dismissActivePanel, modifier, watchingCount = info.viewCount)
            else if (kind == "playlist") PlaylistQueuePanel(
                info = info,
                queue = queue,
                playlistTitle = playlistTitle,
                draggable = draggable,
                showHandle = showHandle,
                onDismiss = dismissActivePanel,
                onSelectQueueItem = onSelectQueueItem,
                onOpenFullPlaylist = onOpenFullPlaylist,
                modifier = modifier
            )
            else CommentsPanel(comments, info, draggable, dismissActivePanel, onLink, onOpenChannel, modifier, showCount, showHandle = showHandle)
        }
        val related = recommendations.distinctBy { it.url }
        if (wide) {
            val playerWidth = wideWatchPlayerWidth(maxWidth)
            Row(Modifier.fillMaxSize().padding(end = if (panel == null) 12.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(if (panel == null) 12.dp else 0.dp)) {
                Column(Modifier.width(playerWidth).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.fillMaxWidth().aspectRatio(effectiveAspect))
                    if (hasPlaylist) {
                        WatchPlaylistQueueBar(
                            info = info,
                            queue = queue,
                            playlistTitle = playlistTitle,
                            expanded = panel == "playlist",
                            onToggle = togglePlaylistPanel,
                            onOpenFullPlaylist = onOpenFullPlaylist,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    VideoDetails(
                        info, comments, chatState, error, onDownload, onChannel, onComments, onDescription, onShare, onRemix, onClip, onCopy, onChat, isLive, engagement,
                        askAvailable = ask != null, onAsk = onAsk, onChapters = onChapters,
                        wide = true, chatOpen = panel == "chat"
                    )
                }
                Box(Modifier.weight(1f).fillMaxHeight().clip(androidx.compose.ui.graphics.RectangleShape).background(Color(0xFF0F0F0F))) {
                    if (panel == null) {
                        Column(Modifier.fillMaxSize()) {
                            if (hasPlaylist) {
                                WatchPlaylistQueueBar(
                                    info = info,
                                    queue = queue,
                                    playlistTitle = playlistTitle,
                                    expanded = false,
                                    onToggle = { onClosePanel(); showPlaylist = true },
                                    onOpenFullPlaylist = onOpenFullPlaylist,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                                items(related, key = { it.url }) { item -> VideoCard(item, resolveAvatar = false, onChannel = onOpenChannel) { onVideo(item) } }
                            }
                        }
                    } else {
                        Panel(panel, draggable = false, rawModifier = Modifier.fillMaxSize(), showCount = false, showHandle = false)
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item { Spacer(Modifier.fillMaxWidth().aspectRatio(effectiveAspect)) }
                if (hasPlaylist) {
                    item {
                        WatchPlaylistQueueBar(
                            info = info,
                            queue = queue,
                            playlistTitle = playlistTitle,
                            expanded = panel == "playlist",
                            onToggle = togglePlaylistPanel,
                            onOpenFullPlaylist = onOpenFullPlaylist,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                item {
                    VideoDetails(
                        info, comments, chatState, error, onDownload, onChannel, onComments, onDescription, onShare, onRemix, onClip, onCopy, onChat, isLive, engagement,
                        askAvailable = ask != null, onAsk = onAsk, onChapters = onChapters,
                        wide = false, chatOpen = panel == "chat"
                    )
                }
                if (recommendations.isEmpty()) item { Text("Related videos are unavailable.", color = Color.LightGray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                items(related) { item -> VideoCard(item, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onChannel = onOpenChannel) { onVideo(item) } }
            }
            // Like YouTube, the panel slides up from the bottom and covers everything below the player, which keeps playing.
            androidx.compose.animation.AnimatedContent(targetState = panel, modifier = Modifier.fillMaxSize(), label = "watch panel", transitionSpec = {
                val enter = if (targetState != null) androidx.compose.animation.slideInVertically(tween(250, easing = FastOutSlowInEasing)) { it } else androidx.compose.animation.EnterTransition.None
                val exit = if (targetState == null) androidx.compose.animation.slideOutVertically(tween(200)) { it } else androidx.compose.animation.fadeOut(tween(120))
                enter togetherWith exit
            }) { kind ->
                if (kind != null) Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.fillMaxWidth().aspectRatio(effectiveAspect))
                    Panel(kind, draggable = true, rawModifier = Modifier.fillMaxWidth().weight(1f), showHandle = true)
                }
            }
        }
    }
}

@Composable internal fun WatchPlaylistQueueBar(
    info: StreamInfo,
    queue: List<StreamInfoItem>,
    playlistTitle: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenFullPlaylist: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val currentKey = videoKey(info.url)
    val currentIndex = queue.indexOfFirst { videoKey(it.url) == currentKey }
    val nextItem = if (currentIndex >= 0) queue.getOrNull(currentIndex + 1) else queue.firstOrNull { videoKey(it.url) != currentKey }
    val titleText = playlistTitle?.takeIf { it.isNotBlank() } ?: "Playlist"
    val positionText = when {
        currentIndex >= 0 && queue.isNotEmpty() -> "${currentIndex + 1}/${queue.size}"
        queue.isNotEmpty() -> "${queue.size} videos"
        else -> "Loading playlist…"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .clickable(enabled = queue.isNotEmpty(), onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.yt_w_save),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = if (nextItem != null && !expanded) "Next: ${nextItem.name}" else titleText,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$titleText · $positionText",
                color = Color(0xFFAAAAAA),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (onOpenFullPlaylist != null) {
            Text(
                text = "Playlist",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onOpenFullPlaylist)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        if (queue.isNotEmpty()) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse playlist" else "Expand playlist",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable internal fun PlaylistQueuePanel(
    info: StreamInfo,
    queue: List<StreamInfoItem>,
    playlistTitle: String?,
    draggable: Boolean,
    showHandle: Boolean,
    onDismiss: () -> Unit,
    onSelectQueueItem: (StreamInfoItem) -> Unit,
    onOpenFullPlaylist: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val currentKey = videoKey(info.url)
    val currentIndex = queue.indexOfFirst { videoKey(it.url) == currentKey }
    val titleText = playlistTitle?.takeIf { it.isNotBlank() } ?: "Playlist"
    val positionText = when {
        currentIndex >= 0 && queue.isNotEmpty() -> "${currentIndex + 1}/${queue.size}"
        queue.isNotEmpty() -> "${queue.size} videos"
        else -> null
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0))
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            runCatching { listState.animateScrollToItem((currentIndex - 1).coerceAtLeast(0)) }
        }
    }
    EngagementPanel(
        title = titleText,
        subtitle = positionText,
        draggable = draggable,
        onDismiss = onDismiss,
        modifier = modifier,
        showHandle = showHandle
    ) {
        if (onOpenFullPlaylist != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = info.uploaderName?.takeIf { it.isNotBlank() } ?: "Playlist queue",
                    color = Color(0xFFAAAAAA),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "View full playlist",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Surface)
                        .clickable(onClick = onOpenFullPlaylist)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = listState,
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            itemsIndexed(queue, key = { idx, item -> "${idx}:${item.url}" }) { idx, item ->
                val isCurrent = videoKey(item.url) == currentKey
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isCurrent) Surface else Color.Transparent)
                        .clickable { onSelectQueueItem(item) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isCurrent) "▶" else "${idx + 1}",
                        color = if (isCurrent) Color.White else Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.width(24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Box(
                        modifier = Modifier
                            .width(112.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface)
                    ) {
                        AsyncImage(
                            model = item.thumbnails.lastOrNull()?.url,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (item.duration > 0) {
                            DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        item.uploaderName?.takeIf { it.isNotBlank() }?.let { uploader ->
                            Text(
                                text = uploader,
                                color = Color(0xFFAAAAAA),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

