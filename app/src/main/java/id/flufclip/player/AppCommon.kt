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

internal val Background = Color(0xFF0F0F0F)
internal val Surface = Color(0xFF272727)
internal val WatchActionSurface = Color(0xFF343434)
internal val Accent = Color(0xFFFF0000)

/**
 * Menentukan apakah layout layar tonton menggunakan 2 kolom lebar (seperti YouTube `qye.java`),
 * mencakup layar lebar ekspansif (>= 840dp) atau tablet/foldable dalam posisi landscape (>= 600dp dan lebar > tinggi).
 */
internal fun isWideWatchLayout(width: Dp, height: Dp): Boolean {
    return width >= 840.dp || (width >= 600.dp && width > height)
}

internal fun wideWatchPlayerWidth(width: Dp): Dp {
    val available = (width - 24.dp).coerceAtLeast(0.dp)
    return available - minOf(available / 3f, 400.dp)
}

internal fun videoKey(url: String): String {
    if (url.length == 11 && url.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return url
    Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)?.let { return it }
    Regex("(?:/shorts/|youtu\\.be/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.get(1)?.let { return it }
    return runCatching {
        val uri = Uri.parse(url)
        uri.getQueryParameter("v")
            ?: if (uri.host == "youtu.be" || uri.pathSegments.firstOrNull() == "shorts") uri.pathSegments.lastOrNull().orEmpty() else url
    }.getOrDefault(url)
}

/**
 * Status live item yang sedang dimuat, diikat ke mediaId supaya status video sebelumnya tidak terbawa.
 * Dipakai untuk video yang tipenya tidak membawa LIVE_STREAM (hasil fallback yt-dlp).
 */
@Composable
internal fun rememberPlayerLive(player: ExoPlayer, mediaKey: String?): Boolean {
    var live by remember(player, mediaKey) { mutableStateOf(false) }
    DisposableEffect(player, mediaKey) {
        fun sync() {
            live = mediaKey != null && player.currentMediaItem?.mediaId == mediaKey && player.isCurrentMediaItemLive
        }
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) = sync()
        }
        player.addListener(listener)
        sync()
        onDispose { player.removeListener(listener) }
    }
    return live
}

/** ExoPlayer membungkus error HLS di PlaybackException, jadi periksa rantai cause-nya. */
internal fun Throwable.causedByBehindLiveWindow(): Boolean =
    generateSequence(this) { it.cause }.take(8).any { it is BehindLiveWindowException }

/** Status bar height from framework resources, so it stays the same while the bar is hidden in fullscreen. */
internal fun statusBarPixels(context: Context): Int {
    val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (id > 0) context.resources.getDimensionPixelSize(id) else 0
}

internal fun formatCount(count: Long): String = when {
    count >= 1_000_000_000 -> String.format(java.util.Locale.US, "%.1fB", count / 1_000_000_000.0).replace(".0B", "B")
    count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
    count >= 10_000 -> "${count / 1_000}K"
    count >= 1_000 -> String.format(java.util.Locale.US, "%.1fK", count / 1_000.0).replace(".0K", "K")
    else -> count.toString()
}

internal fun StreamInfoItem.isOngoingLive(): Boolean =
    streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
        streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM

internal fun StreamInfo.isOngoingLive(): Boolean =
    streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
    streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM

