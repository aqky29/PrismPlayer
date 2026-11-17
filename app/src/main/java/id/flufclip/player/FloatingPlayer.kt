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

@Composable internal fun FloatingPlayer(
    player: ExoPlayer, title: String, zoomKey: String, locked: Boolean, onUnlock: () -> Unit, isLive: Boolean, autoplay: Boolean, hasSubtitles: Boolean, subtitlesEnabled: Boolean, previewFrames: List<org.schabi.newpipe.extractor.stream.Frameset>, segments: List<org.schabi.newpipe.extractor.stream.StreamSegment>,
    fullscreen: Boolean, minimized: Boolean, progress: Float, travelDistance: androidx.compose.ui.unit.Dp, miniState: MiniPlayerState,
    modifier: Modifier, fullscreenTransitioning: Boolean = false, fullscreenChat: Boolean = true, onFullscreenChatChange: ((Boolean) -> Unit)? = null, recovering: Boolean = false, onRetryPlayback: (() -> Unit)? = null, onChapters: (() -> Unit)? = null, chatState: LiveChatState? = null, channelName: String? = null, moreVideos: List<StreamInfoItem> = emptyList(), onMoreVideo: ((StreamInfoItem) -> Unit)? = null, onCast: (() -> Unit)? = null, actions: FullscreenActions? = null, commentsPanel: (@Composable (Modifier) -> Unit)? = null, onNext: (() -> Unit)?, onPrevious: (() -> Unit)?, onAutoplay: () -> Unit, onSubtitles: () -> Unit, onBack: () -> Unit, onSettings: () -> Unit, onFullscreen: () -> Unit,
    onExpand: () -> Unit, onClose: () -> Unit, onDrag: (Float) -> Unit, onCancelDrag: () -> Unit, onRelease: (Float, Float) -> Unit
) {
    // Pinch-to-fill state is per player surface; the view is scaled and clipped by its host so the
    // picture is cropped rather than squashed (see VideoZoom.kt).
    val videoZoom = remember { VideoZoomState() }
    val zoomScope = rememberCoroutineScope()
    SideEffect { videoZoom.bindScope(zoomScope) }
    val zoomHud = remember { mutableStateOf<String?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val miniGestureModifier = rememberMiniPlayerGesture(minimized && !locked, miniState, onExpand)

    // A new video starts at "fit"; leaving fullscreen or minimising drops the crop zoom too.
    LaunchedEffect(zoomKey, fullscreen, minimized) {
        if (minimized || !fullscreen) videoZoom.reset()
    }

    // Full player: pinch/pan the crop zoom, and keep the existing swipe-down behaviour while at fit.
    // In fullscreen the same swipe leaves fullscreen (see App's onRelease).
    val watchGestureModifier = if (!minimized && !locked) Modifier.watchPlayerGestures(
        state = videoZoom,
        enabled = true,
        videoAspect = { videoAspectOf(player.videoFormat) },
        rootOffset = { origin },
        travelDistance = { with(density) { travelDistance.toPx() } },
        onSwipeDrag = onDrag,
        onSwipeRelease = onRelease,
        onSwipeCancel = onCancelDrag,
        onZoomChanged = { value -> zoomHud.value = zoomLabel(value, videoZoom.fillScale) },
    ) else Modifier
    var playing by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == androidx.media3.common.Player.STATE_BUFFERING) }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(playbackState: Int) { buffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING }
            override fun onVideoSizeChanged(videoSize: VideoSize) { videoZoom.onVideoSize(videoSize) }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    var internalFullscreenChat by remember(zoomKey) { mutableStateOf(true) }
    val chatOverlayVisible = if (onFullscreenChatChange != null) fullscreenChat else internalFullscreenChat
    val setChatOverlayVisible: (Boolean) -> Unit = { visible ->
        if (onFullscreenChatChange != null) onFullscreenChatChange(visible) else internalFullscreenChat = visible
    }
    val cornerOutlineProvider = remember {
        object : android.view.ViewOutlineProvider() {
            var radiusPx: Float = 0f
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
    }
    val corner = 12.dp * progress
    val hasChat = isLive || (chatState?.hasChat == true)
    BoxWithConstraints(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .shadow(6.dp * progress, RoundedCornerShape(corner))
            .clip(RoundedCornerShape(corner))
            .background(Color.Black)
    ) {
        val inFullscreenDock = fullscreen && !fullscreenTransitioning && progress < .1f && !locked
        val showDockedComments = commentsPanel != null && inFullscreenDock
        val showDockedChat = !showDockedComments && inFullscreenDock && hasChat && chatOverlayVisible && chatState != null
        val chatWidth = minOf(320.dp, (maxWidth - 440.dp).coerceAtLeast(280.dp))
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (locked) Modifier else watchGestureModifier)
            ) {
                AndroidView(
                    factory = { context ->
                        // The host clips the scaled player view, so pinch zoom crops the picture
                        // instead of letting it draw over the watch page below.
                        val host = android.widget.FrameLayout(context).apply {
                            clipChildren = true
                            clipToPadding = true
                            outlineProvider = cornerOutlineProvider
                        }
                        val view = android.view.LayoutInflater.from(context).inflate(R.layout.floating_player, host, false) as PlayerView
                        host.addView(
                            view,
                            android.widget.FrameLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        host
                    },
                    update = { host ->
                        val view = host.getChildAt(0) as? PlayerView
                        if (view != null) {
                            view.player = player
                            view.useController = false
                            videoZoom.setVideoAspect(videoAspectOf(player.videoFormat))
                            videoZoom.attach(view)
                        }
                        val targetRadius = with(density) { (12.dp * progress).toPx() }
                        if (cornerOutlineProvider.radiusPx != targetRadius) {
                            cornerOutlineProvider.radiusPx = targetRadius
                            host.invalidateOutline()
                        }
                        val rounded = targetRadius > 0.5f
                        if (host.clipToOutline != rounded) host.clipToOutline = rounded
                    },
                    onRelease = { host -> (host.getChildAt(0) as? PlayerView)?.player = null },
                    modifier = Modifier.fillMaxSize().onSizeChanged { videoZoom.setViewport(it) }
                )
                if (locked) {
                    var showUnlock by remember { mutableStateOf(true) }
                    LaunchedEffect(showUnlock) { if (showUnlock) { delay(3000); showUnlock = false } }
                    Box(Modifier.fillMaxSize().clickable { showUnlock = true }, contentAlignment = Alignment.Center) {
                        if (showUnlock) TextButton(
                            onClick = onUnlock,
                            modifier = Modifier.heightIn(min = 44.dp),
                            colors = ButtonDefaults.textButtonColors(containerColor = Color.Black.copy(alpha = .75f), contentColor = Color.White)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Screen locked · Tap to unlock")
                            }
                        }
                    }
                } else if (minimized && progress > .9f) {
                    MiniPlayerControls(player, playing, buffering = buffering, onClose = onClose, gestureModifier = miniGestureModifier, recovering = recovering, onRetryPlayback = onRetryPlayback)
                } else if (progress < .1f) {
                    PlayerControlsOverlay(
                        player, playing, buffering || recovering, fullscreen, title, isLive, autoplay, hasSubtitles, subtitlesEnabled, previewFrames, segments,
                        onBack, onSettings, onAutoplay, onSubtitles, onFullscreen, onPrevious, onNext,
                        hasChat = hasChat,
                        showChatOverlay = chatOverlayVisible,
                        onToggleChatOverlay = { setChatOverlayVisible(!chatOverlayVisible) },
                        channelName = channelName, moreVideos = moreVideos, onMoreVideo = onMoreVideo,
                        onCast = onCast, actions = actions, onChapters = onChapters, onRetryPlayback = onRetryPlayback
                    )
                }
                ZoomHud(zoomHud, Modifier.align(Alignment.Center))
            }
            if (showDockedComments && commentsPanel != null) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF272727)))
                commentsPanel(Modifier.width(chatWidth).fillMaxHeight())
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
                    onDismiss = { setChatOverlayVisible(false) },
                    modifier = Modifier
                        .width(chatWidth)
                        .fillMaxHeight()
                )
            }
        }
    }
}

