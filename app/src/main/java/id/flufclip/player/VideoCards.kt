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

@Composable internal fun SearchWideVideoCard(
    item: StreamInfoItem,
    resolveAvatar: Boolean = true,
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
    val isLive = effectiveMeta?.live == true || item.isOngoingLive()
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
                if (isLive) {
                    LiveThumbnailBadge(Modifier.align(Alignment.BottomEnd))
                } else if (item.duration > 0) {
                    DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
                }
            }
        }
        Column(Modifier.weight(.57f).padding(top = 2.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (isPlaylist) {
                val metaText = listOfNotNull("Playlist", effectiveMeta?.viewsText?.takeIf { it.isNotBlank() }, effectiveMeta?.publishedText?.takeIf { it.isNotBlank() }).joinToString(" · ")
                Text(metaText, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            } else if (isLive) {
                // Server (lockup live): lencana LIVE hanya di thumbnail; di bawah judul cukup "N watching" abu-abu, tanpa chip merah.
                val viewers = if (item.viewCount > 0) "${formatCount(item.viewCount)} watching" else "Live"
                Text(viewers, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            } else {
                val metadata = listOfNotNull(item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} views" }, item.textualUploadDate?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (metadata.isNotBlank()) Text(metadata, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
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
                Text(item.uploaderName.orEmpty(), color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
            item.shortDescription?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp)) }
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
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(item.uploaderName?.takeIf { it.isNotBlank() }, "Playlist").joinToString(" · ")
            Text(sub, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            Text("View full playlist", color = Color.LightGray, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
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
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                item.uploaderName ?: "",
                color = Color.LightGray,
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
                Text(viewers, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable internal fun MembersOnlyBadge(modifier: Modifier = Modifier) {
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

@Composable internal fun VideoCard(
    item: StreamInfoItem,
    modifier: Modifier = Modifier,
    resolveAvatar: Boolean = true,
    isMembership: Boolean = false,
    meta: VideoMeta? = null,
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
    val isLive = effectiveMeta?.live == true || item.isOngoingLive()
    val channelUrl = item.uploaderUrl
    val channelClickable = onChannel != null && !channelUrl.isNullOrBlank()
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600
    val cornerRadius = if (isWideScreen) 12.dp else 0.dp
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        if (isPlaylist) {
            PlaylistStackedThumbnail(
                playlist = AccountPlaylist(
                    id = effectiveMeta?.playlistId ?: "PL",
                    title = item.name,
                    thumbnail = item.thumbnails.lastOrNull()?.url.orEmpty()
                ),
                modifier = Modifier.fillMaxWidth(),
                badgeText = effectiveMeta?.playlistVideoCountText?.takeIf { it.isNotBlank() } ?: "Playlist"
            )
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(if (cornerRadius > 0.dp) Modifier.clip(RoundedCornerShape(cornerRadius)) else Modifier).background(Surface)) {
                AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (isMembership || effectiveMeta?.membersOnly == true) {
                    MembersOnlyBadge(Modifier.align(Alignment.TopStart))
                }
                if (isLive) {
                    LiveThumbnailBadge(Modifier.align(if (effectiveMeta != null) Alignment.BottomStart else Alignment.BottomEnd))
                } else if (item.duration > 0) {
                    DurationBadge(item.duration, Modifier.align(Alignment.BottomEnd))
                }
                if (effectiveMeta != null && effectiveMeta.progressPercent in 1..99) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .35f))) {
                        Box(Modifier.fillMaxWidth(effectiveMeta.progressPercent / 100f).fillMaxHeight().background(Color(0xFFFF0000)))
                    }
                }
            }
        }
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = if (cornerRadius > 0.dp) 0.dp else 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .clickable(enabled = channelClickable) {
                        channelUrl?.let { onChannel?.invoke(it) }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(item.uploaderName?.firstOrNull()?.uppercase() ?: "•", style = MaterialTheme.typography.titleSmall)
                avatar?.let { imageUrl ->
                    AsyncImage(model = imageUrl, contentDescription = "Channel photo of ${item.uploaderName ?: ""}", contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (effectiveMeta != null) {
                    // Baris metadata memakai teks server apa adanya ("40K views", "Streamed 2 days ago", "1.4K watching"),
                    // jadi tidak bergantung pada bahasa akun. Centang verified dikirim sebagai attachmentRuns.
                    val uploader = item.uploaderName.orEmpty()
                    val detail = when {
                        effectiveMeta.isPlaylist -> listOfNotNull(
                            "Playlist",
                            effectiveMeta.viewsText.takeIf { it.isNotBlank() && !it.equals("Playlist", ignoreCase = true) },
                            effectiveMeta.publishedText.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                        effectiveMeta.live -> effectiveMeta.watchingText.ifBlank { if (item.viewCount > 0) "${formatCount(item.viewCount)} watching" else "Live" }
                        else -> listOfNotNull(
                            effectiveMeta.viewsText.takeIf { it.isNotBlank() } ?: item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
                            effectiveMeta.publishedText.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                    }
                    val line = buildAnnotatedString {
                        if (uploader.isNotBlank()) {
                            append(uploader)
                            if (effectiveMeta.verified) {
                                append(" ")
                                appendInlineContent("verified", "✓")
                            }
                        }
                        if (detail.isNotBlank()) {
                            if (uploader.isNotBlank()) append(" · ")
                            append(detail)
                        }
                    }
                    Text(
                        line,
                        inlineContent = mapOf(
                            "verified" to InlineTextContent(Placeholder(12.sp, 12.sp, PlaceholderVerticalAlign.TextCenter)) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Terverifikasi",
                                    tint = Color(0xFFAAAAAA),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        ),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    // Live memakai baris metadata yang sama dengan video biasa: "Channel · N watching" abu-abu. Lencana LIVE hanya di thumbnail,
                    // dan avatar kartu tidak diberi cincin merah (server hanya menandai live di avatar header halaman channel).
                    val metadata = if (isLive) {
                        listOfNotNull(item.uploaderName?.takeIf { it.isNotBlank() }, if (item.viewCount > 0) "${formatCount(item.viewCount)} watching" else "Live")
                    } else {
                        listOfNotNull(item.uploaderName?.takeIf { it.isNotBlank() }, item.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} views" }, item.textualUploadDate?.takeIf { it.isNotBlank() })
                    }.joinToString(" · ")
                    Text(metadata, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (onNotInterested != null) {
                var menuOpen by remember(item.url) { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.MoreVert, "Opsi rekomendasi") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Not interested") }, onClick = { menuOpen = false; onNotInterested() })
                        if (onHideChannel != null) DropdownMenuItem(text = { Text("Don't recommend channel") }, onClick = { menuOpen = false; onHideChannel() })
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

@Composable internal fun PlaylistCard(item: PlaylistInfoItem, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        PlaylistStackedThumbnail(
            playlist = AccountPlaylist(
                id = runCatching { Uri.parse(item.url).getQueryParameter("list") }.getOrNull() ?: "PL",
                title = item.name,
                thumbnail = item.thumbnails.lastOrNull()?.url.orEmpty()
            ),
            modifier = Modifier.fillMaxWidth(),
            badgeText = if (item.streamCount >= 0) "${item.streamCount} videos" else "Playlist"
        )
        Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        val sub = listOfNotNull(item.uploaderName?.takeIf { it.isNotBlank() }, "Playlist").joinToString(" · ")
        Text(sub, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Text("View full playlist", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable internal fun SearchChannelCard(item: ChannelInfoItem, onClick: () -> Unit) {
    val subState = Engagement.subscription(item.url)
    val isSubscribed = subState.subscribed == true
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnails.lastOrNull()?.url,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(76.dp).clip(CircleShape).background(Surface)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.isVerified) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Terverifikasi", tint = Color(0xFFAAAAAA), modifier = Modifier.size(14.dp))
                }
            }
            val handle = remember(item) {
                val match = Regex("/@([\\w\\.-]+)").find(item.url)?.groupValues?.get(1)
                if (!match.isNullOrBlank()) "@$match" else ""
            }
            val subCount = if (item.subscriberCount >= 0) "${formatCount(item.subscriberCount)} subscribers" else "Channel"
            val metaLine = if (handle.isNotBlank()) "$handle • $subCount" else subCount
            Text(metaLine, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isSubscribed) Surface else Color.White)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isSubscribed) "Disubscribe" else "Subscribe",
                color = if (isSubscribed) Color.White else Color.Black,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable internal fun ChannelCard(item: ChannelInfoItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clipCard().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = item.thumbnails.lastOrNull()?.url, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(50)).background(Color.DarkGray))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (item.subscriberCount >= 0) "Channel · ${item.subscriberCount} subscriber" else "Channel", color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
        Text("›", style = MaterialTheme.typography.headlineMedium, color = Accent)
    }
}

