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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
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

internal fun resolveChannelTabOrder(
    rawTabs: List<String>,
    hasMembership: Boolean
): List<String> {
    val priority = listOf(
        VideoRepository.TAB_HOME,
        ChannelTabs.VIDEOS,
        ChannelTabs.SHORTS,
        ChannelTabs.LIVESTREAMS,
        ChannelTabs.PLAYLISTS,
        VideoRepository.TAB_POSTS
    )
    val base = rawTabs.filter { it != VideoRepository.TAB_MEMBERSHIP }.distinct()
    val ordered = priority.filter { it in base } + base.filter { it !in priority }
    return if (hasMembership) ordered + VideoRepository.TAB_MEMBERSHIP else ordered
}

internal fun formatChannelMetaLines(
    handle: String,
    subscriberCountText: String?,
    videoCountText: String?
): Pair<String, String> {
    val line1 = handle.trim()
    val line2 = listOfNotNull(
        subscriberCountText?.trim()?.takeIf { it.isNotBlank() },
        videoCountText?.trim()?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    return line1 to line2
}

@Composable internal fun ChannelHeader(
    page: ChannelPage,
    selectedTab: String,
    isLive: Boolean = false,
    channelSort: ChannelSortOrder = ChannelSortOrder.LATEST,
    channelAccessFilter: ChannelAccessFilter = ChannelAccessFilter.ALL,
    playlistSort: PlaylistSortOrder = PlaylistSortOrder.RECENT,
    subscribedChannels: List<SubsChannel> = emptyList(),
    onSubscribe: (Boolean) -> Unit,
    onNotificationPreference: (NotificationPreference) -> Unit = {},
    onSelectSort: (ChannelSortOrder) -> Unit = {},
    onSelectAccessFilter: (ChannelAccessFilter) -> Unit = {},
    onSelectPlaylistSort: (PlaylistSortOrder) -> Unit = {},
    onTab: (String) -> Unit
) {
    val tabNames = mapOf(
        VideoRepository.TAB_HOME to "Home",
        ChannelTabs.VIDEOS to "Videos",
        ChannelTabs.SHORTS to "Shorts",
        ChannelTabs.LIVESTREAMS to "Live",
        ChannelTabs.PLAYLISTS to "Playlists",
        VideoRepository.TAB_POSTS to "Posts",
        ChannelTabs.CHANNELS to "Channels",
        ChannelTabs.TRACKS to "Tracks",
        ChannelTabs.ALBUMS to "Albums",
        ChannelTabs.LIKES to "Liked",
        VideoRepository.TAB_MEMBERSHIP to "Membership"
    )
    val uiData = page.uiData
    val baseTabs = remember(page.info.tabs, uiData, selectedTab) {
        buildList {
            if (uiData?.hasHomeTab == true || uiData?.homeShelves?.isNotEmpty() == true || selectedTab == VideoRepository.TAB_HOME) {
                add(VideoRepository.TAB_HOME)
            }
            page.info.tabs.mapNotNullTo(this) { tab ->
                tab.contentFilters.firstOrNull { filter -> filter in tabNames }
            }
            if (uiData?.hasPostsTab == true || selectedTab == VideoRepository.TAB_POSTS) {
                add(VideoRepository.TAB_POSTS)
            }
        }.distinct()
    }
    val tabs = resolveChannelTabOrder(baseTabs, page.hasMembership)
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }

    val description = remember(page.info, uiData) {
        uiData?.description?.takeIf { it.isNotBlank() } ?: page.info.description.orEmpty().trim()
    }
    val allLinks = remember(page.info, uiData) {
        val fromUi = uiData?.links.orEmpty()
        if (fromUi.isNotEmpty()) fromUi
        else page.info.donationLinks.orEmpty().map { link ->
            val host = runCatching { Uri.parse(link).host?.removePrefix("www.") }.getOrNull() ?: link
            ChannelLink(host, link)
        }.distinctBy { it.url }
    }
    val linksSummaryText = remember(uiData, allLinks) {
        uiData?.linksSummary?.takeIf { it.isNotBlank() } ?: when (allLinks.size) {
            0 -> null
            1 -> allLinks[0].title
            else -> "${allLinks[0].title} and ${allLinks.size - 1} more ${if (allLinks.size - 1 == 1) "link" else "links"}"
        }
    }
    val handle = remember(page.info, uiData) {
        uiData?.handle?.takeIf { it.isNotBlank() } ?: run {
            val urlMatch = Regex("/@([\\w\\.-]+)").find(page.info.url)?.groupValues?.get(1)
            if (!urlMatch.isNullOrBlank()) "@$urlMatch"
            else if (page.info.name.isNotBlank()) "@${page.info.name.replace("\\s+".toRegex(), "").lowercase()}"
            else ""
        }
    }
    val (metaLine1, metaLine2) = remember(handle, page.info.subscriberCount, uiData) {
        val subsText = uiData?.subscriberCountText?.takeIf { it.isNotBlank() }
            ?: if (page.info.subscriberCount >= 0) "${formatCount(page.info.subscriberCount)} subscribers" else null
        formatChannelMetaLines(handle, subsText, uiData?.videoCountText)
    }

    val subState = Engagement.subscription(page.info.id)
    val inSubscribedList = remember(subscribedChannels, page.info.id, page.info.url) {
        subscribedChannels.any {
            it.id == page.info.id || (!page.info.url.isNullOrBlank() && it.url == page.info.url)
        }
    }
    val isSubscribed = subState.subscribed ?: if (inSubscribedList) true else uiData?.isSubscribed ?: false
    val hasPostsTab = uiData?.hasPostsTab == true || selectedTab == VideoRepository.TAB_POSTS

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 600.dp
        val avatarSize = if (wide) 104.dp else 72.dp

        Column(Modifier.fillMaxWidth()) {
            val banner = page.info.banners.lastOrNull()?.url
            if (banner != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (wide) Modifier.aspectRatio(6f).heightIn(min = 128.dp, max = 184.dp)
                            else Modifier.aspectRatio(3.8f).heightIn(min = 88.dp, max = 124.dp)
                        )
                        .clip(RoundedCornerShape(if (wide) 14.dp else 12.dp))
                        .background(Surface)
                ) {
                    AsyncImage(
                        model = banner,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(if (wide) 16.dp else 12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .then(
                                if (isLive) Modifier
                                    .border(width = 2.5.dp, color = LiveBadgeRed, shape = CircleShape)
                                    .padding(3.dp)
                                else Modifier.border(width = 1.dp, color = HairlineBorder, shape = CircleShape)
                            )
                            .clip(CircleShape)
                            .background(Surface)
                    ) {
                        AsyncImage(
                            model = page.info.avatars.lastOrNull()?.url,
                            contentDescription = "${page.info.name} avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (isLive) {
                        Box(
                            modifier = Modifier
                                .offset(y = 6.dp)
                                .background(LiveBadgeRed, RoundedCornerShape(4.dp))
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
                Spacer(Modifier.width(if (wide) 18.dp else 14.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = page.info.name,
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (wide) 22.sp else 20.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (page.info.isVerified) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (metaLine1.isNotBlank()) {
                        Text(
                            text = metaLine1,
                            color = TextPrimary.copy(alpha = 0.88f),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    if (metaLine2.isNotBlank()) {
                        Text(
                            text = metaLine2,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }

                if (wide) {
                    Spacer(Modifier.width(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SubscribeButton(
                            subscribed = isSubscribed,
                            onSubscribe = { onSubscribe(true) },
                            onUnsubscribe = { onSubscribe(false) },
                            channelName = page.info.name,
                            notificationPreference = subState.notificationPreference,
                            onNotificationPreference = onNotificationPreference,
                            fullWidth = false,
                            modifier = Modifier.widthIn(min = 128.dp)
                        )
                        if (hasPostsTab) {
                            ChannelCommunityPill(
                                fullWidth = false,
                                onClick = { onTab(VideoRepository.TAB_POSTS) }
                            )
                        }
                        if (page.hasMembership) {
                            ChannelJoinPill(
                                label = page.membershipUi?.headerButtonLabel ?: "Join",
                                onClick = { onTab(VideoRepository.TAB_MEMBERSHIP) }
                            )
                        }
                    }
                }
            }

            if (description.isNotBlank()) {
                val cleanDesc = description.replace("\r", "").replace("\n", " ").trim()
                val snippet = if (cleanDesc.length > 82) cleanDesc.take(82).trimEnd() else cleanDesc
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAboutDialog = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append(snippet)
                            append(if (cleanDesc.length > 82) " ... " else " ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = TextPrimary)) {
                                append("...more")
                            }
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (linksSummaryText != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAboutDialog = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = linksSummaryText,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!wide) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SubscribeButton(
                        subscribed = isSubscribed,
                        onSubscribe = { onSubscribe(true) },
                        onUnsubscribe = { onSubscribe(false) },
                        channelName = page.info.name,
                        notificationPreference = subState.notificationPreference,
                        onNotificationPreference = onNotificationPreference,
                        fullWidth = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasPostsTab) {
                        ChannelCommunityPill(
                            fullWidth = !page.hasMembership,
                            onClick = { onTab(VideoRepository.TAB_POSTS) },
                            modifier = if (!page.hasMembership) Modifier.weight(1f) else Modifier
                        )
                    }
                    if (page.hasMembership) {
                        ChannelJoinPill(
                            label = if (hasPostsTab) {
                                val rawLabel = page.membershipUi?.headerButtonLabel ?: "Join"
                                if (rawLabel.equals("See perks", ignoreCase = true)) "Perks" else rawLabel
                            } else {
                                page.membershipUi?.headerButtonLabel ?: "Join"
                            },
                            fullWidth = !hasPostsTab,
                            onClick = { onTab(VideoRepository.TAB_MEMBERSHIP) },
                            modifier = if (!hasPostsTab) Modifier.weight(1f) else Modifier
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val tabColor = if (isSelected) TextPrimary else TextSecondary
                    Column(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { this.selected = isSelected }
                            .clickable { onTab(tab) }
                            .padding(horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabNames[tab] ?: tab,
                                color = tabColor,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(min = 36.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(if (isSelected) TextPrimary else Color.Transparent)
                        )
                    }
                }
            }
            HorizontalDivider(color = HairlineBorder, thickness = 1.dp)

            ChannelTabFilterBar(
                selectedTab = selectedTab,
                hasMembership = page.hasMembership,
                channelSort = channelSort,
                channelAccessFilter = channelAccessFilter,
                playlistSort = playlistSort,
                onSelectSort = onSelectSort,
                onSelectAccessFilter = onSelectAccessFilter,
                onSelectPlaylistSort = onSelectPlaylistSort
            )
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(page.info.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (description.isNotBlank()) {
                        Text(description, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Spacer(Modifier.height(16.dp))
                    }
                    if (metaLine1.isNotBlank()) {
                        Text(metaLine1, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    if (metaLine2.isNotBlank()) {
                        Text(metaLine2, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (allLinks.isNotEmpty()) {
                        Text(
                            "Links",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                        )
                        for (link in allLinks) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url))) }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(link.title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    Text(link.url, style = MaterialTheme.typography.bodySmall, color = CallToActionBlue, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    if (page.info.url.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.info.url))) }
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Text(
                                text = page.info.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = CallToActionBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = TextPrimary)
                }
            },
            containerColor = Surface,
            textContentColor = TextPrimary,
            titleContentColor = TextPrimary,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun ChannelTabFilterBar(
    selectedTab: String,
    hasMembership: Boolean,
    channelSort: ChannelSortOrder,
    channelAccessFilter: ChannelAccessFilter,
    playlistSort: PlaylistSortOrder,
    onSelectSort: (ChannelSortOrder) -> Unit,
    onSelectAccessFilter: (ChannelAccessFilter) -> Unit,
    onSelectPlaylistSort: (PlaylistSortOrder) -> Unit
) {
    when (selectedTab) {
        ChannelTabs.VIDEOS, ChannelTabs.LIVESTREAMS, ChannelTabs.SHORTS -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChannelSortOrder.entries.forEach { sortOrder ->
                    ChannelFilterChip(
                        label = sortOrder.label,
                        selected = channelSort == sortOrder && channelAccessFilter == ChannelAccessFilter.ALL,
                        onClick = {
                            onSelectAccessFilter(ChannelAccessFilter.ALL)
                            onSelectSort(sortOrder)
                        }
                    )
                }
                if (hasMembership) {
                    ChannelFilterChip(
                        label = "Members only",
                        selected = channelAccessFilter == ChannelAccessFilter.MEMBERS_ONLY,
                        onClick = {
                            onSelectAccessFilter(
                                if (channelAccessFilter == ChannelAccessFilter.MEMBERS_ONLY) ChannelAccessFilter.ALL
                                else ChannelAccessFilter.MEMBERS_ONLY
                            )
                        }
                    )
                    ChannelFilterChip(
                        label = "Public",
                        selected = channelAccessFilter == ChannelAccessFilter.PUBLIC,
                        onClick = {
                            onSelectAccessFilter(
                                if (channelAccessFilter == ChannelAccessFilter.PUBLIC) ChannelAccessFilter.ALL
                                else ChannelAccessFilter.PUBLIC
                            )
                        }
                    )
                }
            }
        }
        ChannelTabs.PLAYLISTS -> {
            var sortMenuOpen by remember { mutableStateOf(false) }
            Box(Modifier.padding(top = 6.dp)) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { sortMenuOpen = true }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Sort by",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false },
                    containerColor = SurfaceElevated
                ) {
                    PlaylistSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label, color = TextPrimary) },
                            leadingIcon = if (playlistSort == order) {
                                { Icon(Icons.Default.Check, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp)) }
                            } else null,
                            onClick = {
                                sortMenuOpen = false
                                onSelectPlaylistSort(order)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(shape)
                .background(if (selected) TextPrimary else Surface)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) Background else TextPrimary,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun ChannelCommunityPill(
    fullWidth: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = (if (fullWidth) Modifier.fillMaxWidth() else Modifier.widthIn(min = 112.dp))
                .height(36.dp)
                .clip(shape)
                .background(Surface)
                .border(1.dp, HairlineBorder, shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Outlined.People,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Community",
                color = TextPrimary,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
private fun ChannelJoinPill(
    label: String = "Join",
    fullWidth: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showPerksStar = label.equals("See perks", ignoreCase = true) || label.equals("Perks", ignoreCase = true)
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = (if (fullWidth) Modifier.fillMaxWidth() else Modifier.widthIn(min = 82.dp))
                .height(36.dp)
                .clip(shape)
                .background(Surface)
                .border(1.dp, HairlineBorder, shape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            if (showPerksStar) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(TextPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Background,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            Text(
                text = label,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

