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

/** What the tapped card already knows about a video, shown at once while its StreamInfo loads (like YouTube's watch page). */
internal data class PendingVideo(val url: String, val title: String = "", val uploader: String = "", val thumbnail: String? = null, val avatar: String? = null, val resumeLocal: Boolean = true, val resumePositionMs: Long? = null, val error: String? = null)

/** Watch details shown while the stream information is loading. The player is drawn separately so it can move. */
@Composable internal fun PendingWatchScreen(pending: PendingVideo, watchAlpha: Float, watchOffset: Float) {
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .graphicsLayer { alpha = watchAlpha; translationY = size.height * watchOffset }
        .background(Background)
        .semantics { if (pending.error == null) contentDescription = "Loading video" }
        .pointerInput(Unit) { detectTapGestures { } }) {
        val wide = isWideWatchLayout(maxWidth, maxHeight)
        val playerWidth = wideWatchPlayerWidth(maxWidth)
        if (wide) {
            Row(Modifier.fillMaxSize().padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PendingWatchMainColumn(pending = pending, wide = true, modifier = Modifier.width(playerWidth))
                Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(3) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface))
                            Box(Modifier.fillMaxWidth(0.78f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
                            Box(Modifier.fillMaxWidth(0.45f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceElevated))
                        }
                    }
                }
            }
        } else {
            PendingWatchMainColumn(pending = pending, wide = false, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable private fun PendingWatchMainColumn(pending: PendingVideo, wide: Boolean, modifier: Modifier) {
    Column(modifier) {
        Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (pending.title.isNotBlank()) Text(pending.title, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            else {
                Box(Modifier.fillMaxWidth(.85f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
                Box(Modifier.fillMaxWidth(.55f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
            }
            Box(Modifier.fillMaxWidth(.52f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (pending.uploader.isNotBlank()) {
                        Avatar(pending.avatar, pending.uploader, 40)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(pending.uploader, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.width(84.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
                        }
                    } else {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Surface))
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.width(116.dp).height(13.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
                            Box(Modifier.width(76.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
                        }
                    }
                }
                Box(Modifier.width(88.dp).height(34.dp).clip(RoundedCornerShape(50)).background(Surface))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(104, 78, 96, 86).forEach { Box(Modifier.width(it.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(Surface)) }
            }
            if (!wide) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.width(96.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceElevated))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(SurfaceElevated))
                        Box(Modifier.fillMaxWidth(.72f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceElevated))
                    }
                }
            }
        }
        if (!wide) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Surface))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Surface))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(.88f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(Surface))
                    Box(Modifier.fillMaxWidth(.52f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(SurfaceElevated))
                }
            }
        }
    }
}

/** Thumbnail player stays interactive while resolution continues, then hands its position to FloatingPlayer. */
@Composable internal fun PendingFloatingPlayer(
    pending: PendingVideo, minimized: Boolean, progress: Float, miniState: MiniPlayerState,
    travelDistance: Dp, modifier: Modifier, onMinimize: () -> Unit, onExpand: () -> Unit,
    onClose: () -> Unit, onRetry: () -> Unit, onDrag: (Float) -> Unit,
    onCancelDrag: () -> Unit, onRelease: (Float, Float) -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val miniGesture = rememberMiniPlayerGesture(minimized, miniState, onExpand)
    val swipe = if (minimized) Modifier else Modifier.pendingWatchSwipe(
        origin = { origin }, travelDistance = { with(density) { travelDistance.toPx() } },
        onDrag = onDrag, onCancel = onCancelDrag, onRelease = onRelease,
    )
    val corner = 12.dp * progress
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }
        .shadow(6.dp * progress, RoundedCornerShape(corner)).clip(RoundedCornerShape(corner))
        .background(Color.Black).then(swipe), contentAlignment = Alignment.Center) {
        if (pending.thumbnail != null) AsyncImage(pending.thumbnail, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        if (pending.error == null) CircularProgressIndicator(Modifier.size(if (minimized) 28.dp else 44.dp), color = Color.White, strokeWidth = 3.dp)
        else if (!minimized) Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .75f)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(pending.error, color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onRetry),
                contentAlignment = Alignment.Center
            ) {
                Text("Try again", color = Color.Black, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        if (minimized && progress > .9f) {
            Box(Modifier.fillMaxSize().then(miniGesture)) {
                Box(Modifier.align(Alignment.TopEnd).size(48.dp).clip(CircleShape).clickable(onClick = onClose)
                    .semantics { contentDescription = "Close miniplayer" }, contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.yt_mini_close), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White))
                }
                if (pending.error != null) Box(Modifier.align(Alignment.TopStart).size(48.dp).clip(CircleShape).clickable(onClick = onRetry)
                    .semantics { contentDescription = "Try again" }, contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.yt_replay), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White))
                }
            }
        } else if (progress < .1f) {
            Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(48.dp).clip(CircleShape).clickable(onClick = onMinimize), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.yt_chevron_down), "Minimize video", Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White))
            }
        }
    }
}

/** The pending player follows the same downward swipe and release thresholds as the loaded player. */
@Composable internal fun Modifier.pendingWatchSwipe(
    origin: () -> Offset, travelDistance: () -> Float, onDrag: (Float) -> Unit,
    onCancel: () -> Unit, onRelease: (Float, Float) -> Unit,
): Modifier {
    val currentOrigin by rememberUpdatedState(origin)
    val currentTravel by rememberUpdatedState(travelDistance)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentCancel by rememberUpdatedState(onCancel)
    val currentRelease by rememberUpdatedState(onRelease)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var anchor = down.position + currentOrigin()
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, anchor) }
            var dragging = false
            var completed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) break
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val current = change.position + currentOrigin()
                    if (!dragging && current.y - anchor.y > viewConfiguration.touchSlop) { dragging = true; anchor = current }
                    if (dragging) {
                        tracker.addPosition(change.uptimeMillis, current)
                        change.consume()
                        val fraction = ((current.y - anchor.y) / currentTravel().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        if (change.pressed) currentDrag(fraction) else {
                            completed = true
                            currentRelease(fraction, tracker.calculateVelocity().y / density)
                        }
                    }
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                if (dragging && !completed) currentCancel()
            }
        }
    }
}

/** Shared mini-player gestures for both the loading thumbnail and the resolved video. */
@Composable internal fun rememberMiniPlayerGesture(enabled: Boolean, miniState: MiniPlayerState, onExpand: () -> Unit): Modifier {
    val currentExpand by rememberUpdatedState(onExpand)
    val miniTapScope = rememberCoroutineScope()
    var pendingMiniTap by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    return if (!enabled) Modifier else Modifier.pointerInput(miniState) {
        val gestureDensity = this.density
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.isConsumed) {
                var released = false
                while (!released) released = awaitPointerEvent().changes.none { it.pressed }
            } else {
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
                        if (zoom.isFinite() && zoom > 0f && focus != Offset.Unspecified) miniState.pinch(zoom, focus / gestureDensity)
                        event.changes.forEach { it.consume() }
                    } else if (!pinching) {
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.positionChange()
                        total += delta
                        tracker.addPosition(change.uptimeMillis, total)
                        if (!dragging && total.getDistance() > viewConfiguration.touchSlop) { dragging = true; pendingMiniTap?.cancel() }
                        if (dragging) { change.consume(); miniState.drag(delta / gestureDensity) }
                    }
                } while (event.changes.any { it.pressed })
                if (pinching) miniState.pinchEnd()
                else if (dragging) tracker.calculateVelocity().let { miniState.release(Offset(it.x, it.y) / gestureDensity) }
                else {
                    val pending = pendingMiniTap
                    if (pending?.isActive == true) {
                        pending.cancel()
                        pendingMiniTap = null
                        miniState.toggleSize()
                    } else pendingMiniTap = miniTapScope.launch {
                        delay(viewConfiguration.doubleTapTimeoutMillis)
                        pendingMiniTap = null
                        currentExpand()
                    }
                }
            }
        }
    }
}

