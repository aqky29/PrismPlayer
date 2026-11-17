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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
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
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod

@Composable
internal fun LiveThumbnailBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Live" }
            .background(LiveBadgeRed, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.yt_live_badge),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp)
        )
        Text(
            "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            ),
            color = Color.White
        )
    }
}

@Composable internal fun SearchWideVideoCard(
    item: StreamInfoItem,
    resolveAvatar: Boolean = true,
    inlineOverlay: (@Composable BoxScope.() -> Unit)? = null,
    onChannel: ((String) -> Unit)? = null,
    onClick: () -> Unit
) {
    val suppliedAvatar = item.uploaderAvatars.lastOrNull()?.url
        ?: item.uploaderUrl?.let { VideoRepository.cachedAvatar(it) }
    var avatar by remember(item.url, suppliedAvatar) { mutableStateOf(suppliedAvatar) }
    LaunchedEffect(item.uploaderUrl, suppliedAvatar) {
        if (resolveAvatar && avatar.isNullOrBlank() && !item.uploaderUrl.isNullOrBlank()) {
            val resolved = VideoRepository.channelAvatar(item.uploaderUrl)
            if (!resolved.isNullOrBlank()) {
                avatar = resolved
                if (item.uploaderAvatars.isEmpty()) {
                    item.uploaderAvatars = listOf(org.schabi.newpipe.extractor.Image(resolved, 88, 88, org.schabi.newpipe.extractor.Image.ResolutionLevel.LOW))
                }
            }
        }
    }
    val effectiveMeta = HomeFeed.metaOf(item.url)
    val isPlaylist = effectiveMeta?.isPlaylist == true
    val isLive = isCardLiveStream(item, effectiveMeta)
    val channelUrl = item.uploaderUrl
    val channelClickable = onChannel != null && !channelUrl.isNullOrBlank()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (isPlaylist) {
            PlaylistStackedThumbnail(
                playlist = AccountPlaylist(
                    id = effectiveMeta?.playlistId ?: "PL",
                    title = item.name,
                    thumbnail = item.thumbnails.lastOrNull()?.url.orEmpty()
                ),
                modifier = Modifier.weight(.43f),
                badgeText = effectiveMeta?.playlistVideoCountText?.takeIf { it.isNotBlank() } ?: "Playlist"
            )
        } else {
            Box(Modifier.weight(.43f).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(Surface)) {
                AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (inlineOverlay != null) {
                    inlineOverlay()
                } else {
                    if (isLive) {
                        LiveThumbnailBadge(Modifier.align(Alignment.BottomEnd))
                    } else if (item.duration > 0) {
                        DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        }
        Column(Modifier.weight(.57f).padding(top = 2.dp)) {
            Text(item.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (isPlaylist) {
                val metaText = listOfNotNull("Playlist", effectiveMeta?.viewsText?.takeIf { it.isNotBlank() }, effectiveMeta?.publishedText?.takeIf { it.isNotBlank() }).joinToString(" · ")
                Text(metaText, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            } else if (isLive) {
                // Server (lockup live): lencana LIVE hanya di thumbnail; di bawah judul cukup "N watching" abu-abu, tanpa chip merah.
                val viewers = if (item.viewCount > 0) "${formatCount(item.viewCount)} watching" else "Live"
                Text(viewers, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            } else {
                val metadata = listOfNotNull(item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} views" }, item.textualUploadDate?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (metadata.isNotBlank()) Text(metadata, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            }
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = channelClickable) {
                        channelUrl?.let { onChannel?.invoke(it) }
                    }
                    .padding(vertical = 2.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(24.dp)
                        .clip(CircleShape)
                        .background(Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.uploaderName?.firstOrNull()?.uppercase() ?: "•", style = MaterialTheme.typography.labelSmall)
                    avatar?.let { AsyncImage(model = it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
                Text(item.uploaderName.orEmpty(), color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
            item.shortDescription?.takeIf { it.isNotBlank() }?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

@Composable internal fun SearchWidePlaylistCard(item: PlaylistInfoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PlaylistStackedThumbnail(
            playlist = AccountPlaylist(
                id = runCatching { Uri.parse(item.url).getQueryParameter("list") }.getOrNull() ?: "PL",
                title = item.name,
                thumbnail = item.thumbnails.lastOrNull()?.url.orEmpty()
            ),
            modifier = Modifier.weight(.43f),
            badgeText = if (item.streamCount >= 0) "${item.streamCount} videos" else "Playlist"
        )
        Column(Modifier.weight(.57f).padding(top = 2.dp)) {
            Text(item.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(item.uploaderName?.takeIf { it.isNotBlank() }, "Playlist").joinToString(" · ")
            Text(sub, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Text("View full playlist", color = TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable internal fun CompactVideoCard(
    item: StreamInfoItem,
    onChannel: ((String) -> Unit)? = null,
    onClick: () -> Unit
) {
    val isLive = item.isOngoingLive()
    val channelUrl = item.uploaderUrl
    val channelClickable = onChannel != null && !channelUrl.isNullOrBlank()
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
            Text(item.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                item.uploaderName ?: "",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(enabled = channelClickable) { channelUrl?.let { onChannel?.invoke(it) } }
            )
            // Kartu terkait live: baris channel, lalu baris "N watching" abu-abu (dua baris metadata terpisah di lockup server).
            if (isLive) {
                val viewers = if (item.viewCount > 0) "${formatCount(item.viewCount)} watching" else "Live"
                Text(viewers, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Compact inline tag rendered below video metadata instead of overlaying the thumbnail. */
@Composable internal fun MembersOnlyMetaTag(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E8E3E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
        }
        Text(
            text = "Members only",
            color = MembershipGreen,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

internal data class ChannelVideoActions(
    val onCast: (() -> Unit)? = null,
    val onPlayNextInQueue: (StreamInfoItem) -> Unit = {},
    val onPlayLastInQueue: (StreamInfoItem) -> Unit = {},
    val onSaveToWatchLater: (StreamInfoItem) -> Unit = {},
    val onSaveToPlaylist: (StreamInfoItem) -> Unit = {},
    val onDownload: ((StreamInfoItem) -> Unit)? = null,
    val onShare: (StreamInfoItem) -> Unit = {}
)

internal data class ChannelPlaylistActions(
    val onPlayNextInQueue: (PlaylistInfoItem) -> Unit = {},
    val onSaveToLibrary: ((PlaylistInfoItem) -> Unit)? = null,
    val onShare: (PlaylistInfoItem) -> Unit = {}
)

internal fun isCardLiveStream(item: StreamInfoItem, meta: VideoMeta?): Boolean {
    if (isUpcomingLiveStream(item, meta) || item.duration > 0L) return false
    return meta?.live == true || item.isOngoingLive() ||
        (meta?.watchingText?.isNotBlank() == true && meta.publishedText.isBlank())
}

internal fun isUpcomingLiveStream(item: StreamInfoItem, meta: VideoMeta?): Boolean {
    val date = item.textualUploadDate.orEmpty()
    val watching = meta?.watchingText.orEmpty()
    return item.streamType == StreamType.LIVE_STREAM &&
        (date.contains("Scheduled", ignoreCase = true) ||
            date.contains("Premier", ignoreCase = true) ||
            watching.contains("waiting", ignoreCase = true))
}

@Composable
private fun UpcomingThumbnailBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "Upcoming",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun ChannelStreamMenuAnchor(
    item: StreamInfoItem,
    isLiveOrUpcoming: Boolean,
    actions: ChannelVideoActions,
    iconTint: Color = TextPrimary,
    scrimBehindIcon: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { expanded = true },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (scrimBehindIcon) Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options for ${item.name}",
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SurfaceElevated
        ) {
            actions.onCast?.let { onCast ->
                DropdownMenuItem(
                    text = { Text("Play on different device", color = TextPrimary) },
                    leadingIcon = { Icon(painterResource(R.drawable.yt_cast), null, tint = TextPrimary, modifier = Modifier.size(20.dp)) },
                    onClick = { expanded = false; onCast() }
                )
            }
            DropdownMenuItem(
                text = { Text("Play next in queue", color = TextPrimary) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = TextPrimary) },
                onClick = { expanded = false; actions.onPlayNextInQueue(item) }
            )
            DropdownMenuItem(
                text = { Text("Play last in queue", color = TextPrimary) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = TextPrimary) },
                onClick = { expanded = false; actions.onPlayLastInQueue(item) }
            )
            DropdownMenuItem(
                text = { Text("Save to Watch later", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Default.WatchLater, null, tint = TextPrimary) },
                onClick = { expanded = false; actions.onSaveToWatchLater(item) }
            )
            DropdownMenuItem(
                text = { Text("Save to playlist", color = TextPrimary) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = TextPrimary) },
                onClick = { expanded = false; actions.onSaveToPlaylist(item) }
            )
            if (!isLiveOrUpcoming && actions.onDownload != null) {
                DropdownMenuItem(
                    text = { Text("Download video", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Download, null, tint = TextPrimary) },
                    onClick = { expanded = false; actions.onDownload.invoke(item) }
                )
            }
            DropdownMenuItem(
                text = { Text("Share", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Default.Share, null, tint = TextPrimary) },
                onClick = { expanded = false; actions.onShare(item) }
            )
        }
    }
}

@Composable
private fun ChannelPlaylistMenuAnchor(
    item: PlaylistInfoItem,
    actions: ChannelPlaylistActions,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { expanded = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "Playlist options for ${item.name}",
            tint = TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SurfaceElevated
        ) {
            DropdownMenuItem(
                text = { Text("Play next in queue", color = TextPrimary) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = TextPrimary) },
                onClick = { expanded = false; actions.onPlayNextInQueue(item) }
            )
            actions.onSaveToLibrary?.let { onSave ->
                DropdownMenuItem(
                    text = { Text("Save to Library", color = TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.BookmarkBorder, null, tint = TextPrimary) },
                    onClick = { expanded = false; onSave(item) }
                )
            }
            DropdownMenuItem(
                text = { Text("Share", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Default.Share, null, tint = TextPrimary) },
                onClick = { expanded = false; actions.onShare(item) }
            )
        }
    }
}

private fun formatChannelVideoMeta(
    item: StreamInfoItem,
    isLive: Boolean,
    isUpcoming: Boolean,
    showChannel: Boolean,
    effectiveMeta: VideoMeta?
): String {
    if (effectiveMeta != null) {
        return when {
            isUpcoming -> listOfNotNull(
                effectiveMeta.watchingText.takeIf { it.isNotBlank() }
                    ?: item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} waiting" },
                effectiveMeta.publishedText.takeIf { it.isNotBlank() } ?: item.textualUploadDate?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            isLive -> effectiveMeta.watchingText.ifBlank {
                if (item.viewCount > 0) "${formatCount(item.viewCount)} watching" else "Live"
            }
            else -> listOfNotNull(
                effectiveMeta.viewsText.takeIf { it.isNotBlank() } ?: item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
                effectiveMeta.publishedText.takeIf { it.isNotBlank() } ?: item.textualUploadDate?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
        }
    }
    val channelPart = if (showChannel) item.uploaderName?.takeIf { it.isNotBlank() } else null
    return when {
        isUpcoming -> listOfNotNull(
            channelPart,
            item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} waiting" },
            item.textualUploadDate?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        isLive -> listOfNotNull(channelPart, if (item.viewCount > 0) "${formatCount(item.viewCount)} watching" else "Live").joinToString(" · ")
        else -> listOfNotNull(
            channelPart,
            item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
            item.textualUploadDate?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
    }
}

@Composable internal fun ChannelCompactVideoRow(
    item: StreamInfoItem,
    isMembership: Boolean = false,
    meta: VideoMeta? = null,
    horizontalPadding: Dp = 16.dp,
    videoActions: ChannelVideoActions? = null,
    onClick: () -> Unit
) {
    val effectiveMeta = meta ?: HomeFeed.metaOf(item.url)
    val isUpcoming = isUpcomingLiveStream(item, effectiveMeta)
    val isLive = isCardLiveStream(item, effectiveMeta)
    val showMembersTag = isMembership || effectiveMeta?.membersOnly == true
    val metadata = formatChannelVideoMeta(item, isLive = isLive, isUpcoming = isUpcoming, showChannel = false, effectiveMeta = effectiveMeta)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = horizontalPadding, end = if (videoActions != null) (horizontalPadding - 10.dp).coerceAtLeast(4.dp) else horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface)
        ) {
            AsyncImage(
                model = item.thumbnails.lastOrNull()?.url,
                contentDescription = item.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            when {
                isUpcoming -> UpcomingThumbnailBadge(Modifier.align(Alignment.BottomEnd))
                isLive -> LiveThumbnailBadge(Modifier.align(Alignment.BottomEnd))
                item.duration > 0 -> DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
            }
            if (effectiveMeta != null && effectiveMeta.progressPercent in 1..99) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .35f))) {
                    Box(Modifier.fillMaxWidth(effectiveMeta.progressPercent / 100f).fillMaxHeight().background(Accent))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.name,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showMembersTag) {
                MembersOnlyMetaTag(Modifier.padding(top = 1.dp))
            }
        }
        if (videoActions != null) {
            ChannelStreamMenuAnchor(
                item = item,
                isLiveOrUpcoming = isLive || isUpcoming,
                actions = videoActions,
                modifier = Modifier.offset(y = (-8).dp)
            )
        }
    }
}

@Composable internal fun MembershipShelfVideoCard(
    item: StreamInfoItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val effectiveMeta = HomeFeed.metaOf(item.url)
    val isUpcoming = isUpcomingLiveStream(item, effectiveMeta)
    val isLive = isCardLiveStream(item, effectiveMeta)
    val metadata = formatChannelVideoMeta(item, isLive = isLive, isUpcoming = isUpcoming, showChannel = false, effectiveMeta = effectiveMeta)
    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
        ) {
            AsyncImage(
                model = item.thumbnails.lastOrNull()?.url,
                contentDescription = item.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            when {
                isUpcoming -> UpcomingThumbnailBadge(Modifier.align(Alignment.BottomEnd))
                isLive -> LiveThumbnailBadge(Modifier.align(Alignment.BottomEnd))
                item.duration > 0 -> DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
            }
            if (effectiveMeta != null && effectiveMeta.progressPercent in 1..99) {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .35f))) {
                    Box(Modifier.fillMaxWidth(effectiveMeta.progressPercent / 100f).fillMaxHeight().background(Accent))
                }
            }
        }
        Text(
            text = item.name,
            color = TextPrimary,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        MembersOnlyMetaTag()
    }
}

@Composable internal fun ChannelCompactPlaylistRow(
    item: PlaylistInfoItem,
    horizontalPadding: Dp = 16.dp,
    playlistActions: ChannelPlaylistActions? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp)
            .clickable(onClick = onClick)
            .padding(start = horizontalPadding, end = if (playlistActions != null) (horizontalPadding - 10.dp).coerceAtLeast(4.dp) else horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        PlaylistStackedThumbnail(
            playlist = AccountPlaylist(
                id = runCatching { Uri.parse(item.url).getQueryParameter("list") }.getOrNull() ?: "PL",
                title = item.name,
                thumbnail = item.thumbnails.lastOrNull()?.url.orEmpty()
            ),
            modifier = Modifier.width(160.dp),
            badgeText = if (item.streamCount >= 0) "${item.streamCount}" else "Playlist"
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.name,
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val sub = listOfNotNull(item.uploaderName?.takeIf { it.isNotBlank() }, "Playlist").joinToString(" · ")
            Text(
                text = sub,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "View full playlist",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp)
            )
        }
        if (playlistActions != null) {
            ChannelPlaylistMenuAnchor(
                item = item,
                actions = playlistActions,
                modifier = Modifier.offset(y = (-8).dp)
            )
        }
    }
}

@Composable internal fun ChannelShortCard(
    item: StreamInfoItem,
    sharpEdges: Boolean = true,
    videoActions: ChannelVideoActions? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "shortCardPressScale"
    )
    val cardShape = if (sharpEdges) androidx.compose.ui.graphics.RectangleShape else RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(cardShape)
            .background(Surface)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
    ) {
        AsyncImage(
            model = item.thumbnails.lastOrNull()?.url,
            contentDescription = item.name,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                    )
                )
        )
        if (videoActions != null) {
            ChannelStreamMenuAnchor(
                item = item,
                isLiveOrUpcoming = false,
                actions = videoActions,
                iconTint = Color.White,
                scrimBehindIcon = true,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        if (item.viewCount >= 0) {
            Text(
                text = "${formatCount(item.viewCount)} views",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

internal fun homeShortsLayoutSpec(screenWidth: Dp): Pair<Int, Int> = when {
    screenWidth >= 1200.dp -> 5 to 5
    screenWidth >= 900.dp -> 4 to 4
    screenWidth >= 600.dp -> 3 to 6
    else -> 2 to 4
}

@Composable internal fun HomeShortsShelfSection(
    title: String,
    items: List<ShortsCard>,
    screenWidth: Dp,
    onOpen: (Int) -> Unit,
    onHideCard: (ShortsCard) -> Unit,
    onHideShelf: () -> Unit,
    onSaveCard: ((ShortsCard) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isWideScreen = screenWidth >= 600.dp
    val columns = homeShortsLayoutSpec(screenWidth).first
    val horizontalPadding = if (isWideScreen) 0.dp else 12.dp
    var shelfMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = 6.dp,
                bottom = if (isWideScreen) 6.dp else 18.dp
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(9.dp).background(Color.White))
                Icon(
                    painter = painterResource(R.drawable.yt_nav_shorts_active),
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title.ifBlank { "Shorts" },
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(
                    onClick = { shelfMenuOpen = true },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Shorts shelf options",
                        tint = TextPrimary
                    )
                }
                DropdownMenu(
                    expanded = shelfMenuOpen,
                    onDismissRequest = { shelfMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Watch in Shorts") },
                        onClick = {
                            shelfMenuOpen = false
                            if (items.isNotEmpty()) onOpen(0)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hide Shorts shelf") },
                        onClick = {
                            shelfMenuOpen = false
                            onHideShelf()
                        }
                    )
                }
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val gap = 8.dp
            val cardWidth = ((maxWidth - horizontalPadding * 2 - gap * (columns - 1)) / columns)
                .coerceAtLeast(132.dp)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, card -> card.videoId }
                ) { index, card ->
                    HomeShortsCard(
                        card = card,
                        modifier = Modifier.width(cardWidth),
                        onClick = { onOpen(index) },
                        onNotInterested = { onHideCard(card) },
                        onSave = onSaveCard?.let { cb -> { cb(card) } }
                    )
                }
            }
        }
    }
}

@Composable internal fun HomeShortsCard(
    card: ShortsCard,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onNotInterested: () -> Unit,
    onSave: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "homeShortCardPressScale"
    )
    var menuOpen by remember(card.videoId) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
    ) {
        AsyncImage(
            model = card.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${card.videoId}/frame0.jpg" },
            contentDescription = card.title.ifBlank { "Short" },
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
        )
        if (card.badgeText.isNotBlank()) {
            Text(
                text = card.badgeText,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .semantics { contentDescription = "Short options" }
                    .clickable(role = Role.Button, onClick = { menuOpen = true }),
                contentAlignment = Alignment.TopEnd
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(top = 8.dp, end = 8.dp)
                        .size(20.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                if (onSave != null) {
                    DropdownMenuItem(
                        text = { Text("Save to Watch later") },
                        onClick = {
                            menuOpen = false
                            onSave()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        menuOpen = false
                        val shareIntent = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/shorts/${card.videoId}")
                        runCatching { context.startActivity(Intent.createChooser(shareIntent, "Share Short")) }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Not interested") },
                    onClick = {
                        menuOpen = false
                        onNotInterested()
                    }
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.50f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.48f), Color.Black.copy(alpha = 0.88f))
                    )
                )
        )
        val label = card.title.ifBlank { card.viewsText }
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            )
        }
    }
}

private val TRAILING_VIEW_LABEL_REGEX = Regex("\\s+(?:views?|x\\s+ditonton|ditonton|tayangan)\\s*$", RegexOption.IGNORE_CASE)

private fun compactViewCountText(raw: String): String {
    val trimmed = raw.trim()
    val stripped = trimmed.replace(TRAILING_VIEW_LABEL_REGEX, "").trim()
    return stripped.ifBlank { trimmed }
}

@Composable
private fun HollowPlayViewsIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val left = size.width * 0.14f
        val right = size.width * 0.78f
        val top = size.height * 0.14f
        val bottom = size.height * 0.86f
        val path = Path().apply {
            moveTo(left, top)
            lineTo(right, size.height * 0.5f)
            lineTo(left, bottom)
            close()
        }
        drawPath(
            path = path,
            color = TextSecondary,
            style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable internal fun VideoCard(
    item: StreamInfoItem,
    modifier: Modifier = Modifier,
    resolveAvatar: Boolean = true,
    isMembership: Boolean = false,
    meta: VideoMeta? = null,
    showChannel: Boolean = true,
    roundedThumbnail: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
    bottomPadding: Dp = if (roundedThumbnail) 0.dp else 18.dp,
    inlineOverlay: (@Composable BoxScope.() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    onHideChannel: (() -> Unit)? = null,
    onChannel: ((String) -> Unit)? = null,
    onClick: () -> Unit
) {
    val effectiveMeta = meta ?: HomeFeed.metaOf(item.url)
    val isPlaylist = effectiveMeta?.isPlaylist == true
    val suppliedAvatar = item.uploaderAvatars.lastOrNull()?.url
        ?: item.uploaderUrl?.let { VideoRepository.cachedAvatar(it) }
    var avatar by remember(item.url, suppliedAvatar) { mutableStateOf(suppliedAvatar) }
    LaunchedEffect(item.uploaderUrl, suppliedAvatar, showChannel) {
        if (showChannel && resolveAvatar && avatar.isNullOrBlank() && !item.uploaderUrl.isNullOrBlank()) {
            val resolved = VideoRepository.channelAvatar(item.uploaderUrl)
            if (!resolved.isNullOrBlank()) {
                avatar = resolved
                if (item.uploaderAvatars.isEmpty()) {
                    item.uploaderAvatars = listOf(org.schabi.newpipe.extractor.Image(resolved, 88, 88, org.schabi.newpipe.extractor.Image.ResolutionLevel.LOW))
                }
            }
        }
    }
    val isMix = effectiveMeta?.isMix == true
    val isLive = isCardLiveStream(item, effectiveMeta)
    val channelUrl = item.uploaderUrl
    val channelClickable = showChannel && onChannel != null && !channelUrl.isNullOrBlank()
    val cornerRadius = if (roundedThumbnail) 12.dp else 0.dp
    val sideInset = if (roundedThumbnail) 2.dp else 12.dp
    val showAvatarSlot = showChannel && !(isMix && avatar.isNullOrBlank() && channelUrl.isNullOrBlank())
    Column(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        if (isPlaylist) {
            PlaylistStackedThumbnail(
                playlist = AccountPlaylist(
                    id = effectiveMeta?.playlistId ?: "PL",
                    title = item.name,
                    thumbnail = item.thumbnails.lastOrNull()?.url.orEmpty()
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (roundedThumbnail) 0.dp else 12.dp),
                badgeText = effectiveMeta?.playlistVideoCountText?.takeIf { it.isNotBlank() }
                    ?: if (isMix) "Mix" else "Playlist",
                isMix = isMix,
                cornerRadius = 12.dp
            )
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(if (cornerRadius > 0.dp) Modifier.clip(RoundedCornerShape(cornerRadius)) else Modifier).background(Surface)) {
                AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (inlineOverlay != null) {
                    inlineOverlay()
                } else {
                    if (isLive) {
                        LiveThumbnailBadge(Modifier.align(Alignment.BottomEnd))
                    } else if (item.duration > 0) {
                        DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
                    }
                    if (effectiveMeta != null && effectiveMeta.progressPercent in 1..99) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .35f))) {
                            Box(Modifier.fillMaxWidth(effectiveMeta.progressPercent / 100f).fillMaxHeight().background(Accent))
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = sideInset, end = sideInset, top = 10.dp, bottom = bottomPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showAvatarSlot) {
                Box(
                    Modifier
                        .size(if (isLive) 40.dp else 36.dp)
                        .then(
                            if (channelClickable) {
                                Modifier
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "Open channel ${item.uploaderName ?: ""}"
                                    }
                                    .clickable(role = Role.Button) {
                                        channelUrl?.let { onChannel?.invoke(it) }
                                    }
                            } else Modifier
                        ),
                    contentAlignment = if (isLive) Alignment.TopCenter else Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(if (isLive) 38.dp else 36.dp)
                            .then(
                                if (isLive) {
                                    Modifier
                                        .border(1.5.dp, Accent, CircleShape)
                                        .padding(2.dp)
                                } else Modifier
                            )
                            .clip(CircleShape)
                            .background(Surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.uploaderName?.firstOrNull()?.uppercase() ?: "•", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        avatar?.let { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = if (channelClickable) null else "Channel photo of ${item.uploaderName ?: ""}",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (isLive) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 8.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.3.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(LiveBadgeRed, RoundedCornerShape(3.dp))
                                .border(1.dp, Background, RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val uploader = if (showChannel) item.uploaderName.orEmpty().trim() else ""
                val inlineMetaIcons = remember {
                    mapOf(
                        "verified" to InlineTextContent(Placeholder(12.sp, 12.sp, PlaceholderVerticalAlign.TextCenter)) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = TextSecondary,
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        "play_views" to InlineTextContent(Placeholder(13.sp, 11.sp, PlaceholderVerticalAlign.TextCenter)) {
                            HollowPlayViewsIcon(Modifier.fillMaxSize())
                        }
                    )
                }
                if (effectiveMeta != null) {
                    val line = buildAnnotatedString {
                        if (uploader.isNotBlank()) {
                            append(uploader)
                            if (effectiveMeta.verified) {
                                append(" ")
                                appendInlineContent("verified", "Verified")
                            }
                        }
                        when {
                            effectiveMeta.isPlaylist -> {
                                val detail = if (isMix) {
                                    listOfNotNull(
                                        effectiveMeta.viewsText.takeIf { it.isNotBlank() && !it.equals("Mix", ignoreCase = true) && !it.equals("Playlist", ignoreCase = true) },
                                        effectiveMeta.publishedText.takeIf { it.isNotBlank() }
                                    ).joinToString(" · ")
                                } else {
                                    listOfNotNull(
                                        "Playlist",
                                        effectiveMeta.viewsText.takeIf { it.isNotBlank() && !it.equals("Playlist", ignoreCase = true) },
                                        effectiveMeta.publishedText.takeIf { it.isNotBlank() }
                                    ).joinToString(" · ")
                                }
                                if (detail.isNotBlank()) {
                                    if (uploader.isNotBlank()) append(" · ")
                                    append(detail)
                                }
                            }
                            isLive -> {
                                val watching = effectiveMeta.watchingText.ifBlank {
                                    if (item.viewCount > 0) "${formatCount(item.viewCount)} watching" else "Live"
                                }
                                if (watching.isNotBlank()) {
                                    if (uploader.isNotBlank()) append("  ")
                                    if (!watching.equals("Live", ignoreCase = true)) {
                                        appendInlineContent("play_views", "Viewers")
                                    }
                                    append(watching)
                                }
                            }
                            else -> {
                                val rawViews = effectiveMeta.viewsText.takeIf { it.isNotBlank() }
                                    ?: item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} views" }
                                val compactViews = rawViews?.let(::compactViewCountText).orEmpty()
                                val published = effectiveMeta.publishedText.trim()
                                if (compactViews.isNotBlank()) {
                                    if (uploader.isNotBlank()) append("  ")
                                    appendInlineContent("play_views", "Views")
                                    append(compactViews)
                                }
                                if (published.isNotBlank()) {
                                    if (uploader.isNotBlank() || compactViews.isNotBlank()) append("  ")
                                    append(published)
                                }
                            }
                        }
                    }
                    Text(
                        line,
                        inlineContent = inlineMetaIcons,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    val line = buildAnnotatedString {
                        if (uploader.isNotBlank()) {
                            append(uploader)
                        }
                        if (isLive) {
                            val watching = if (item.viewCount > 0) "${formatCount(item.viewCount)} watching" else "Live"
                            if (uploader.isNotBlank()) append("  ")
                            if (item.viewCount > 0) appendInlineContent("play_views", "Viewers")
                            append(watching)
                        } else {
                            val views = item.viewCount.takeIf { it >= 0 }?.let { formatCount(it) }.orEmpty()
                            val published = item.textualUploadDate?.trim().orEmpty()
                            if (views.isNotBlank()) {
                                if (uploader.isNotBlank()) append("  ")
                                appendInlineContent("play_views", "Views")
                                append(views)
                            }
                            if (published.isNotBlank()) {
                                if (uploader.isNotBlank() || views.isNotBlank()) append("  ")
                                append(published)
                            }
                        }
                    }
                    Text(
                        line,
                        inlineContent = inlineMetaIcons,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (isMembership || effectiveMeta?.membersOnly == true) {
                    MembersOnlyMetaTag(Modifier.padding(top = 4.dp))
                }
            }
            val context = LocalContext.current
            var menuOpen by remember(item.url) { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.MoreHoriz, "Video options", tint = TextPrimary.copy(alpha = 0.85f))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.background(SurfaceElevated),
                ) {
                    DropdownMenuItem(
                        text = { Text("Share", color = TextPrimary) },
                        leadingIcon = { Icon(painterResource(R.drawable.yt_w_share), null, tint = TextPrimary, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            menuOpen = false
                            runCatching {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, item.url)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share video"))
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy link", color = TextPrimary) },
                        leadingIcon = { Icon(painterResource(R.drawable.yt_w_copy), null, tint = TextPrimary, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            menuOpen = false
                            runCatching {
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(ClipData.newPlainText("Video link", item.url))
                                android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                    if (channelClickable) {
                        DropdownMenuItem(
                            text = { Text("Open channel", color = TextPrimary) },
                            onClick = {
                                menuOpen = false
                                channelUrl?.let { onChannel?.invoke(it) }
                            },
                        )
                    }
                    if (onNotInterested != null) {
                        DropdownMenuItem(
                            text = { Text("Not interested", color = TextPrimary) },
                            onClick = { menuOpen = false; onNotInterested() },
                        )
                    }
                    if (onHideChannel != null) {
                        DropdownMenuItem(
                            text = { Text("Don't recommend channel", color = TextPrimary) },
                            onClick = { menuOpen = false; onHideChannel() },
                        )
                    }
                }
            }
        }
    }
}

@Composable internal fun DurationBadge(duration: Long, modifier: Modifier = Modifier) {
    val label = if (duration >= 3600) "%d:%02d:%02d".format(duration / 3600, duration / 60 % 60, duration % 60) else "%d:%02d".format(duration / 60, duration % 60)
    Text(label, modifier = modifier.padding(6.dp).background(Color.Black.copy(alpha = .85f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
}
internal fun Modifier.clipCard(): Modifier = this.clip(RoundedCornerShape(16.dp)).background(Surface)

@Composable internal fun PlaylistCard(
    item: PlaylistInfoItem,
    horizontalPadding: Dp = 0.dp,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        PlaylistStackedThumbnail(
            playlist = AccountPlaylist(
                id = runCatching { Uri.parse(item.url).getQueryParameter("list") }.getOrNull() ?: "PL",
                title = item.name,
                thumbnail = item.thumbnails.lastOrNull()?.url.orEmpty()
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
            badgeText = if (item.streamCount >= 0) "${item.streamCount} videos" else "Playlist"
        )
        Column(Modifier.padding(horizontal = horizontalPadding)) {
            Text(item.name, color = TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            val sub = listOfNotNull(item.uploaderName?.takeIf { it.isNotBlank() }, "Playlist").joinToString(" · ")
            Text(sub, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text("View full playlist", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable internal fun SearchChannelCard(
    item: ChannelInfoItem,
    subscribedChannels: List<SubsChannel> = emptyList(),
    onSubscribe: ((channelId: String, subscribe: Boolean) -> Unit)? = null,
    onNotificationPreference: ((channelId: String, pref: NotificationPreference) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val resolvedChannelId = remember(item.url, item.name, subscribedChannels) {
        HomeFeed.channelIdOf(item.url)
            ?: subscribedChannels.firstOrNull { ch ->
                (ch.url.isNotBlank() && ch.url.equals(item.url, ignoreCase = true)) ||
                    (ch.name.isNotBlank() && ch.name.equals(item.name, ignoreCase = true))
            }?.id
            ?: item.url
    }
    val subState = Engagement.subscription(resolvedChannelId)
    val inSubscribedList = remember(item.url, item.name, subscribedChannels) {
        subscribedChannels.any { ch ->
            (ch.id.isNotBlank() && ch.id == resolvedChannelId) ||
                (ch.url.isNotBlank() && ch.url.equals(item.url, ignoreCase = true)) ||
                (ch.name.isNotBlank() && ch.name.equals(item.name, ignoreCase = true))
        }
    }
    val isSubscribed = subState.subscribed ?: inSubscribedList
    val handle = remember(item.url, item.name) {
        val match = Regex("/@([\\w\\.-]+)").find(item.url)?.groupValues?.get(1)
        if (!match.isNullOrBlank()) "@$match"
        else {
            val clean = item.name.replace(Regex("[^A-Za-z0-9_.-]"), "")
            if (clean.isNotBlank()) "@$clean" else ""
        }
    }
    val subCountText = if (item.subscriberCount >= 0) "${formatCount(item.subscriberCount)} subscribers" else "Channel"
    val streamCountText = if (item.streamCount > 0) "${formatCount(item.streamCount)} videos" else null
    val statsLine = listOfNotNull(subCountText, streamCountText).joinToString(" • ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.thumbnails.lastOrNull()?.url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Surface),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = item.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.isVerified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                if (handle.isNotBlank()) {
                    Text(
                        text = handle,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = statsLine,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubscribeButton(
                subscribed = isSubscribed,
                onSubscribe = {
                    if (resolvedChannelId.startsWith("UC") && onSubscribe != null) {
                        onSubscribe(resolvedChannelId, true)
                    } else {
                        onClick()
                    }
                },
                onUnsubscribe = {
                    if (resolvedChannelId.startsWith("UC") && onSubscribe != null) {
                        onSubscribe(resolvedChannelId, false)
                    } else {
                        onClick()
                    }
                },
                enabled = !subState.pending,
                uncertain = subState.uncertain,
                channelName = item.name,
                notificationPreference = subState.notificationPreference,
                onNotificationPreference = { pref ->
                    if (resolvedChannelId.startsWith("UC")) {
                        onNotificationPreference?.invoke(resolvedChannelId, pref)
                    }
                },
                fullWidth = true,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "View Channel",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable internal fun ChannelCard(item: ChannelInfoItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clipCard()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.lastOrNull()?.url,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(CircleShape).background(SurfaceElevated)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isVerified) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
            val subText = if (item.subscriberCount >= 0) {
                "Channel · ${formatCount(item.subscriberCount)} subscribers"
            } else "Channel"
            Text(subText, color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open channel",
            tint = TextSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun ChannelHomeShelfSection(
    shelf: ChannelHomeShelf,
    isWideScreen: Boolean,
    videoActions: ChannelVideoActions,
    onOpenVideo: (StreamInfoItem) -> Unit,
    onOpenShorts: (List<ShortsCard>, Int) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenTab: (String) -> Unit
) {
    val outerPad = if (isWideScreen) 0.dp else 16.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = outerPad)
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = shelf.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val targetTab = shelf.targetTab
            if (targetTab != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onOpenTab(targetTab) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View all ${shelf.title}",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (shelf.videos.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = outerPad),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shelf.videos, key = { it.url }) { item ->
                    val effectiveMeta = HomeFeed.metaOf(item.url)
                    val isUpcoming = isUpcomingLiveStream(item, effectiveMeta)
                    val isLive = isCardLiveStream(item, effectiveMeta)
                    val metadata = formatChannelVideoMeta(item, isLive = isLive, isUpcoming = isUpcoming, showChannel = false, effectiveMeta = effectiveMeta)
                    Column(
                        modifier = Modifier
                            .width(if (isWideScreen) 256.dp else 232.dp)
                            .clickable { onOpenVideo(item) },
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                        ) {
                            AsyncImage(
                                model = item.thumbnails.lastOrNull()?.url,
                                contentDescription = item.name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            when {
                                isUpcoming -> UpcomingThumbnailBadge(Modifier.align(Alignment.BottomEnd))
                                isLive -> LiveThumbnailBadge(Modifier.align(Alignment.BottomEnd))
                                item.duration > 0 -> DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = item.name,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (metadata.isNotBlank()) {
                                    Text(
                                        text = metadata,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            ChannelStreamMenuAnchor(
                                item = item,
                                isLiveOrUpcoming = isLive || isUpcoming,
                                actions = videoActions,
                                modifier = Modifier.offset(x = 8.dp, y = (-6).dp)
                            )
                        }
                    }
                }
            }
        }

        if (shelf.shorts.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = outerPad),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(shelf.shorts, key = { _, s -> s.videoId }) { index, card ->
                    Box(
                        modifier = Modifier
                            .width(if (isWideScreen) 164.dp else 140.dp)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface)
                            .clickable { onOpenShorts(shelf.shorts, index) }
                    ) {
                        AsyncImage(
                            model = card.thumbnailUrl,
                            contentDescription = card.title,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(88.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (card.title.isNotBlank()) {
                                Text(
                                    text = card.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (card.viewsText.isNotBlank()) {
                                Text(
                                    text = card.viewsText,
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        if (shelf.playlists.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = outerPad),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shelf.playlists, key = { it.url }) { pl ->
                    Column(
                        modifier = Modifier
                            .width(204.dp)
                            .clickable { onOpenPlaylist(pl.url) },
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlaylistStackedThumbnail(
                            playlist = AccountPlaylist(
                                id = runCatching { Uri.parse(pl.url).getQueryParameter("list") }.getOrNull() ?: "PL",
                                title = pl.name,
                                thumbnail = pl.thumbnails.lastOrNull()?.url.orEmpty()
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            badgeText = if (pl.streamCount >= 0) "${pl.streamCount}" else "Playlist"
                        )
                        Text(
                            text = pl.name,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChannelPostCard(
    post: ChannelPost,
    isWideScreen: Boolean,
    videoActions: ChannelVideoActions,
    onOpenVideo: (StreamInfoItem) -> Unit
) {
    val context = LocalContext.current
    var textExpanded by remember(post.postId) { mutableStateOf(false) }
    var textOverflows by remember(post.postId) { mutableStateOf(false) }
    var menuOpen by remember(post.postId) { mutableStateOf(false) }
    val outerPad = if (isWideScreen) 0.dp else 16.dp
    val postShareUrl = "https://www.youtube.com/post/${post.postId}"

    fun sharePost() {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, postShareUrl)
        }
        runCatching { context.startActivity(Intent.createChooser(share, "Share post")) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = outerPad, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Surface)
                ) {
                    if (!post.authorAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = post.authorAvatarUrl,
                            contentDescription = "${post.author} avatar",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = post.author,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (post.publishedText.isNotBlank()) {
                        Text(
                            text = post.publishedText,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            maxLines = 1
                        )
                    }
                }
                Box {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Post options", tint = TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = SurfaceElevated
                    ) {
                        DropdownMenuItem(
                            text = { Text("Share post", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Share, null, tint = TextPrimary) },
                            onClick = { menuOpen = false; sharePost() }
                        )
                        if (post.text.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Copy text", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = TextPrimary) },
                                onClick = {
                                    menuOpen = false
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Post text", post.text))
                                    android.widget.Toast.makeText(context, "Post copied", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            if (post.text.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = textOverflows || textExpanded) { textExpanded = !textExpanded }
                ) {
                    Text(
                        text = post.text,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        maxLines = if (textExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            if (!textExpanded) textOverflows = result.hasVisualOverflow
                        }
                    )
                    if (textOverflows || textExpanded) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .padding(top = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = if (textExpanded) "Show less" else "Read more",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            if (post.images.size == 1) {
                AsyncImage(
                    model = post.images.first(),
                    contentDescription = "Post attachment",
                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface)
                )
            } else if (post.images.size > 1) {
                val pagerState = rememberPagerState(pageCount = { post.images.size })
                Box(modifier = Modifier.fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        pageSpacing = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) { pageIndex ->
                        AsyncImage(
                            model = post.images[pageIndex],
                            contentDescription = "Post image ${pageIndex + 1} of ${post.images.size}",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Surface)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/${post.images.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }

            post.attachedVideo?.let { video ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(vertical = 8.dp)
                ) {
                    ChannelCompactVideoRow(
                        item = video,
                        horizontalPadding = 8.dp,
                        videoActions = videoActions,
                        onClick = { onOpenVideo(video) }
                    )
                }
            }

            if (post.pollChoices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.pollChoices.forEach { choice ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface)
                                .border(1.dp, HairlineBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(choice, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.ThumbUp, contentDescription = "Likes", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Text(post.voteCountText ?: "0", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Replies", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Text(post.replyCountText ?: "0", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { sharePost() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share post", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        HorizontalDivider(color = Surface, thickness = 6.dp)
    }
}

