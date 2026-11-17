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

internal data class SubsChipOption(val key: String, val label: String)

internal val subsChipOptions = listOf(
    SubsChipOption("all", "All"),
    SubsChipOption("members", "Membership"),
    SubsChipOption("today", "Today"),
    SubsChipOption("videos", "Video"),
    SubsChipOption("shorts", "Shorts"),
    SubsChipOption("live", "Live"),
    SubsChipOption("continue", "Continue watching"),
    SubsChipOption("unwatched", "Unwatched")
)

internal val SubsChannel.key: String get() = id.ifBlank { url }.ifBlank { name }

internal fun matchesSubsChannel(item: StreamInfoItem, channel: SubsChannel): Boolean {
    val itemUrl = item.uploaderUrl.orEmpty()
    return (channel.url.isNotBlank() && itemUrl == channel.url) ||
        (channel.name.isNotBlank() && item.uploaderName.equals(channel.name, ignoreCase = true))
}

internal fun isTodayText(text: String): Boolean {
    val value = text.lowercase()
    return value.contains("hour") || value.contains("minute") || value.contains("today") || value.contains("just now") ||
        value.contains("jam") || value.contains("menit") || value.contains("hari ini") || value.contains("baru saja")
}

internal fun filterSubsEntries(entries: List<SubsEntry>, chip: String, channel: SubsChannel?, watchedUrls: Set<String>): List<SubsEntry> {
    fun matchesChannel(item: StreamInfoItem): Boolean = channel == null || matchesSubsChannel(item, channel)

    fun videoAllowed(entry: SubsEntry.Video): Boolean {
        if (!matchesChannel(entry.item)) return false
        val meta = entry.meta
        return when (chip) {
            "members" -> meta.membersOnly
            "today" -> isTodayText(meta.publishedText)
            "live" -> meta.live || entry.item.isOngoingLive()
            "continue" -> meta.progressPercent in 0..99
            "unwatched" -> entry.item.url !in watchedUrls
            else -> true
        }
    }

    return if (chip == "shorts") {
        if (channel != null) emptyList() else entries.filterIsInstance<SubsEntry.ShortsShelf>()
    } else {
        entries.mapNotNull { entry ->
            when (entry) {
                is SubsEntry.Video -> if (videoAllowed(entry)) entry else null
                is SubsEntry.ShortsShelf -> if (channel == null && chip == "all") entry else null
            }
        }
    }
}

internal fun mergeSubsEntries(old: List<SubsEntry>, new: List<SubsEntry>): List<SubsEntry> {
    val videoUrls = old.filterIsInstance<SubsEntry.Video>().map { it.item.url }.toMutableSet()
    val shortIds = old.filterIsInstance<SubsEntry.ShortsShelf>().flatMap { it.items }.map { it.videoId }.toMutableSet()
    val result = old.toMutableList()
    for (entry in new) {
        when (entry) {
            is SubsEntry.Video -> if (videoUrls.add(entry.item.url)) result.add(entry)
            is SubsEntry.ShortsShelf -> {
                val fresh = entry.items.filter { shortIds.add(it.videoId) }
                if (fresh.isNotEmpty()) result.add(SubsEntry.ShortsShelf(fresh))
            }
        }
    }
    return result
}

@Composable
internal fun SubsChannelAvatarRow(
    channels: List<SubsChannel>,
    selectedKey: String?,
    liveKeys: Set<String>,
    freshKeys: Set<String>,
    onAll: () -> Unit,
    onSelect: (SubsChannel) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "subs_avatar_all") {
            val isAll = selectedKey == null
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAll() }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isAll) Color.White else Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Semua",
                        color = if (isAll) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Semua",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = if (isAll) Color.White else TextSecondary,
                    maxLines = 1
                )
            }
        }
        items(channels.size, key = { index -> "subs_channel_${channels[index].key}" }) { index ->
            val channel = channels[index]
            val isSelected = selectedKey == channel.key
            val isLive = channel.key in liveKeys || channel.name in liveKeys
            val hasFresh = channel.key in freshKeys || channel.name in freshKeys
            Column(
                modifier = Modifier
                    .width(62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(channel) }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                else if (isLive) Modifier.border(2.dp, Accent, CircleShape)
                                else if (hasFresh) Modifier.border(2.dp, CallToActionBlue, CircleShape)
                                else Modifier
                            )
                            .padding(if (isSelected || isLive || hasFresh) 3.dp else 0.dp)
                            .clip(CircleShape)
                            .background(Surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            channel.name.firstOrNull()?.uppercase() ?: "•",
                            style = MaterialTheme.typography.titleMedium
                        )
                        channel.avatarUrl?.let { avatarUrl ->
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = "Channel ${channel.name}",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .background(Accent, RoundedCornerShape(2.dp))
                                .padding(horizontal = 3.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                "LIVE",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (hasFresh && !isSelected) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .align(Alignment.BottomEnd)
                                .background(CallToActionBlue, CircleShape)
                                .border(1.5.dp, Background, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    channel.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = if (isSelected) Color.White else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun SubsChipBar(active: String, onSelect: (String) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(subsChipOptions, key = { it.key }) { chip ->
            val selected = chip.key == active
            Box(
                Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(if (selected) Color.White else Surface)
                    .clickable { onSelect(chip.key) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    chip.label,
                    color = if (selected) Color.Black else Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun SubsChannelRail(
    channels: List<SubsChannel>,
    selectedKey: String?,
    liveKeys: Set<String>,
    freshKeys: Set<String>,
    onAll: () -> Unit,
    onSelect: (SubsChannel) -> Unit
) {
    var sortOpen by remember { mutableStateOf(false) }
    LazyColumn(Modifier.width(224.dp).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clickable { sortOpen = true }
                        .padding(start = 16.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Paling relevan", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    DropdownMenuItem(text = { Text("Paling relevan") }, onClick = { sortOpen = false })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
                    .clip(CircleShape)
                    .background(if (selectedKey == null) Surface else Color.Transparent)
                    .clickable(onClick = onAll)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(painterResource(R.drawable.yt_all_channels), contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                Text("All channels", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
        items(channels, key = { it.key }) { channel ->
            val selected = selectedKey == channel.key
            val live = channel.url in liveKeys || channel.name in liveKeys
            val fresh = channel.url in freshKeys || channel.name in freshKeys
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
                    .clip(CircleShape)
                    .background(if (selected) Surface else Color.Transparent)
                    .clickable { onSelect(channel) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(36.dp)) {
                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Surface), contentAlignment = Alignment.Center) {
                        Text(channel.name.firstOrNull()?.uppercase() ?: "•", style = MaterialTheme.typography.labelLarge)
                        channel.avatarUrl?.let { avatarUrl ->
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (live || fresh) {
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(10.dp).clip(CircleShape)
                                .background(if (live) Color(0xFFFF0033) else Color(0xFF3EA6FF))
                                .border(1.5.dp, Background, CircleShape)
                        )
                    }
                }
                Text(
                    channel.name,
                    Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ShortsShelfRow(items: List<ShortsCard>, onOpen: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            Icon(painterResource(R.drawable.yt_nav_shorts_active), contentDescription = null, tint = Accent, modifier = Modifier.size(22.dp))
            Text("Shorts", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.padding(start = 2.dp).size(20.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(items, key = { _, card -> card.videoId }) { index, card ->
                Box(
                    Modifier.width(160.dp).height(284.dp).clip(RoundedCornerShape(12.dp)).background(Surface)
                        .clickable { onOpen(index) }
                ) {
                    AsyncImage(
                        model = card.thumbnailUrl.ifBlank { "https://i.ytimg.com/vi/${card.videoId}/frame0.jpg" },
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(.45f).align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .86f))))
                    )
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        if (card.title.isNotBlank()) {
                            Text(
                                card.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (card.viewsText.isNotBlank()) {
                            Text(card.viewsText, color = Color(0xFFDDDDDD), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionsScreen(
    entries: List<SubsEntry>,
    channels: List<SubsChannel>,
    membershipEntries: List<SubsEntry.Video>,
    membershipLoading: Boolean,
    watchedUrls: Set<String>,
    loading: Boolean,
    error: String?,
    canLoadMore: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onLoadMembership: () -> Unit,
    onVideo: (StreamInfoItem) -> Unit,
    onChannel: (String) -> Unit,
    onShorts: (List<ShortsCard>, Int) -> Unit,
    onNotInterested: (StreamInfoItem) -> Unit,
    onInlineCandidateChanged: ((StreamInfoItem?) -> Unit)? = null,
    inlineOverlayFor: ((StreamInfoItem) -> (@Composable BoxScope.() -> Unit)?)? = null,
) {
    val gridState = rememberLazyGridState()
    val refreshState = rememberPullToRefreshState()
    val refreshing = loading && entries.isNotEmpty()
    var activeChip by remember { mutableStateOf("all") }
    var selectedChannelKey by remember { mutableStateOf<String?>(null) }

    val railChannels = remember(channels, entries) {
        if (channels.isNotEmpty()) channels
        else entries.asSequence().filterIsInstance<SubsEntry.Video>()
            .mapNotNull { entry ->
                val name = entry.item.uploaderName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SubsChannel(entry.item.uploaderUrl.orEmpty(), name, entry.item.uploaderAvatars.lastOrNull()?.url, entry.item.uploaderUrl.orEmpty())
            }
            .distinctBy { it.name }
            .toList()
    }
    val selectedChannel = railChannels.firstOrNull { it.key == selectedChannelKey }
    val liveKeys = remember(entries) {
        entries.asSequence().filterIsInstance<SubsEntry.Video>()
            .filter { it.meta.live || it.item.isOngoingLive() }
            .flatMap { sequenceOf(it.item.uploaderUrl.orEmpty(), it.item.uploaderName.orEmpty()) }
            .filter { it.isNotBlank() }
            .toSet()
    }
    val freshKeys = remember(entries) {
        entries.asSequence().filterIsInstance<SubsEntry.Video>()
            .flatMap { sequenceOf(it.item.uploaderUrl.orEmpty(), it.item.uploaderName.orEmpty()) }
            .filter { it.isNotBlank() }
            .toSet()
    }
    val visibleEntries = remember(entries, membershipEntries, activeChip, selectedChannel, watchedUrls) {
        val source: List<SubsEntry> = if (activeChip == "members" && membershipEntries.isNotEmpty()) membershipEntries else entries
        filterSubsEntries(source, activeChip, selectedChannel, watchedUrls)
    }
    val keyToStream = remember(visibleEntries) {
        buildMap<Any, StreamInfoItem> {
            visibleEntries.forEach { entry ->
                if (entry is SubsEntry.Video) put("video:${entry.item.url}", entry.item)
            }
        }
    }
    ObserveInlineFeedCandidate(
        gridState = gridState,
        enabled = visibleEntries.isNotEmpty() && !loading,
        keyToStream = keyToStream,
        onCandidateChanged = onInlineCandidateChanged,
    )

    LaunchedEffect(activeChip, membershipEntries.size, channels.size) {
        if (activeChip == "members" && membershipEntries.isEmpty()) onLoadMembership()
    }

    LaunchedEffect(gridState, visibleEntries.size, canLoadMore, loading, activeChip, selectedChannelKey) {
        // Hanya feed penuh yang memuat halaman berikutnya; hasil filter tidak memicu pagination berulang.
        if (!loading && canLoadMore && visibleEntries.isNotEmpty() && activeChip == "all" && selectedChannelKey == null) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .distinctUntilChanged().collect { last ->
                    if (last >= visibleEntries.size - 3) onLoadMore()
                }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = refreshing,
                state = refreshState,
                onRefresh = onRefresh
            )
    ) {
        if (loading && entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (entries.isEmpty() && error != null) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(error, color = Color.LightGray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRefresh) { Text("Try again") }
            }
        } else if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No videos from your subscribed channels yet.", color = Color.LightGray)
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide) {
                        SubsChannelRail(
                            channels = railChannels,
                            selectedKey = selectedChannelKey,
                            liveKeys = liveKeys,
                            freshKeys = freshKeys,
                            onAll = { selectedChannelKey = null },
                            onSelect = { selectedChannelKey = it.key }
                        )
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (!wide && railChannels.isNotEmpty()) {
                            SubsChannelAvatarRow(
                                channels = railChannels,
                                selectedKey = selectedChannelKey,
                                liveKeys = liveKeys,
                                freshKeys = freshKeys,
                                onAll = { selectedChannelKey = null },
                                onSelect = { selectedChannelKey = if (selectedChannelKey == it.key) null else it.key }
                            )
                        }
                        SubsChipBar(active = activeChip, onSelect = { activeChip = it })
                        val subsContentPadding = if (!wide) PaddingValues(start = 0.dp, end = 0.dp, top = 4.dp, bottom = 92.dp) else PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp)
                        val subsSpacing = if (!wide) 0.dp else 12.dp
                        val subsVerticalSpacing = if (!wide) 0.dp else 20.dp
                        LazyVerticalGrid(
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            columns = if (wide) GridCells.Fixed(2) else GridCells.Adaptive(minSize = 300.dp),
                            contentPadding = subsContentPadding,
                            horizontalArrangement = Arrangement.spacedBy(subsSpacing),
                            verticalArrangement = Arrangement.spacedBy(subsVerticalSpacing)
                        ) {
                            visibleEntries.forEach { entry ->
                                when (entry) {
                                    is SubsEntry.ShortsShelf -> item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "shorts:${entry.items.first().videoId}"
                                    ) {
                                        ShortsShelfRow(entry.items) { index -> onShorts(entry.items, index) }
                                    }
                                    is SubsEntry.Video -> item(key = "video:${entry.item.url}") {
                                        VideoCard(
                                            item = entry.item,
                                            meta = entry.meta,
                                            inlineOverlay = inlineOverlayFor?.invoke(entry.item),
                                            onClick = { onVideo(entry.item) },
                                            onChannel = { entry.item.uploaderUrl?.let(onChannel) },
                                            onNotInterested = { onNotInterested(entry.item) },
                                            resolveAvatar = true
                                        )
                                    }
                                }
                            }
                            if (activeChip == "members" && membershipLoading) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                                    }
                                }
                            }
                            if (visibleEntries.isEmpty() && !membershipLoading) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text("No videos match this filter.", color = Color.LightGray, modifier = Modifier.padding(vertical = 24.dp))
                                }
                            }
                            if (loading && entries.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.dp)
                                    }
                                }
                            }
                            if (error != null && entries.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(error, color = Color.LightGray, modifier = Modifier.weight(1f))
                                        TextButton(onClick = onLoadMore) { Text("Try again") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        PullToRefreshDefaults.Indicator(state = refreshState, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter))
    }
}

