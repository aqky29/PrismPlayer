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

@Composable internal fun ChannelHeader(
    page: ChannelPage,
    selectedTab: String,
    isLive: Boolean = false,
    onSubscribe: (Boolean) -> Unit,
    onNotificationPreference: (NotificationPreference) -> Unit = {},
    onTab: (String) -> Unit
) {
    val tabNames = mapOf(
        ChannelTabs.VIDEOS to "Video",
        ChannelTabs.SHORTS to "Shorts",
        ChannelTabs.LIVESTREAMS to "Live",
        ChannelTabs.PLAYLISTS to "Playlist",
        ChannelTabs.CHANNELS to "Channel",
        ChannelTabs.TRACKS to "Lagu",
        ChannelTabs.ALBUMS to "Album",
        ChannelTabs.LIKES to "Liked",
        VideoRepository.TAB_MEMBERSHIP to "Membership"
    )
    val baseTabs = page.info.tabs.mapNotNull { it.contentFilters.firstOrNull { filter -> filter in tabNames } }.distinct()
    val tabs = if (page.hasMembership && VideoRepository.TAB_MEMBERSHIP !in baseTabs) {
        val list = baseTabs.toMutableList()
        val insertPos = if (list.contains(ChannelTabs.VIDEOS)) list.indexOf(ChannelTabs.VIDEOS) + 1 else 0
        list.add(insertPos.coerceAtMost(list.size), VideoRepository.TAB_MEMBERSHIP)
        list
    } else baseTabs
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }
    val description = remember(page.info) { page.info.description.orEmpty().trim() }
    val donationLinks = remember(page.info) { page.info.donationLinks }

    Column(Modifier.fillMaxWidth()) {
        val banner = page.info.banners.lastOrNull()?.url
        if (banner != null) {
            AsyncImage(
                model = banner,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 150.dp)
                    .aspectRatio(16f / 5.2f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface)
            )
            Spacer(Modifier.height(14.dp))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomCenter) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .then(
                            if (isLive) Modifier
                                .border(width = 2.5.dp, color = Color(0xFFCC0000), shape = CircleShape)
                                .padding(3.dp)
                            else Modifier
                        )
                        .clip(CircleShape)
                        .background(Surface)
                ) {
                    AsyncImage(
                        model = page.info.avatars.lastOrNull()?.url,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .offset(y = 6.dp)
                            .background(Color(0xFFCC0000), RoundedCornerShape(3.dp))
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
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = page.info.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (page.info.isVerified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Terverifikasi",
                            tint = Color(0xFFAAAAAA),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                val handle = remember(page.info) {
                    val urlMatch = Regex("/@([\\w\\.-]+)").find(page.info.url)?.groupValues?.get(1)
                    if (!urlMatch.isNullOrBlank()) "@$urlMatch"
                    else if (page.info.name.isNotBlank()) "@${page.info.name.replace("\\s+".toRegex(), "").lowercase()}"
                    else ""
                }
                if (handle.isNotBlank()) {
                    Text(
                        text = handle,
                        color = Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val subCountText = if (page.info.subscriberCount >= 0) "${formatCount(page.info.subscriberCount)} subscriber" else null
                val videoCountText = if (page.videos.isNotEmpty()) "${page.videos.size} video" else null
                val statsLine = listOfNotNull(subCountText, videoCountText).joinToString(" • ")
                if (statsLine.isNotBlank()) {
                    Text(
                        text = statsLine,
                        color = Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        val description = remember(page.info) { page.info.description.orEmpty().trim() }
        if (description.isNotBlank()) {
            val cleanDesc = description.replace("\r", "").replace("\n", " ")
            val snippet = if (cleanDesc.length > 90) cleanDesc.take(90) else cleanDesc
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true }
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(snippet)
                        if (cleanDesc.length > 90) {
                            append("...")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) {
                                append("selengkapnya")
                            }
                        }
                    },
                    color = Color(0xFFAAAAAA),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val hasLinks = !donationLinks.isNullOrEmpty() || page.info.url.isNotBlank()
        if (hasLinks) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true }
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "🔗",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 2.dp)
                )
                val mainLinkTitle = donationLinks?.firstOrNull()?.let { link ->
                    runCatching { Uri.parse(link).host?.removePrefix("www.") }.getOrNull() ?: link
                } ?: "Tautan channel"
                Text(
                    text = mainLinkTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                )
                val extraCount = (donationLinks?.size ?: 1) - 1
                if (extraCount > 0) {
                    Text(
                        text = "dan $extraCount link lainnya",
                        color = Color(0xFFAAAAAA),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val subState = Engagement.subscription(page.info.id)
            val isSubscribed = subState.subscribed == true
            if (page.hasMembership) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface)
                        .clickable { onTab(VideoRepository.TAB_MEMBERSHIP) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("⭐", fontSize = 13.sp)
                        Text(
                            text = "Gabung",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
            var showChannelUnsubDialog by remember { mutableStateOf(false) }
            var showChannelNotificationSheet by remember { mutableStateOf(false) }

            if (showChannelNotificationSheet) {
                NotificationOptionsSheet(
                    channelName = page.info.name,
                    currentPreference = subState.notificationPreference,
                    onSelectPreference = { pref -> onNotificationPreference(pref) },
                    onUnsubscribe = { showChannelUnsubDialog = true },
                    onDismiss = { showChannelNotificationSheet = false }
                )
            }

            if (showChannelUnsubDialog) {
                AlertDialog(
                    onDismissRequest = { showChannelUnsubDialog = false },
                    title = {
                        Text(
                            text = "Unsubscribe dari ${page.info.name}?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showChannelUnsubDialog = false
                                onSubscribe(false)
                            }
                        ) {
                            Text(
                                "Unsubscribe",
                                color = Color(0xFFFF4E4E),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showChannelUnsubDialog = false }) {
                            Text("Batal", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF282828),
                    textContentColor = Color.White,
                    titleContentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            Box(
                modifier = Modifier
                    .then(if (page.hasMembership) Modifier.weight(1f) else Modifier.fillMaxWidth())
                    .height(38.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSubscribed) Surface else Color.White)
                    .clickable {
                        if (isSubscribed) {
                            showChannelNotificationSheet = true
                        } else {
                            onSubscribe(true)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isSubscribed) {
                        val bellIcon = when (subState.notificationPreference) {
                            NotificationPreference.ALL -> Icons.Filled.NotificationsActive
                            NotificationPreference.PERSONALIZED -> Icons.Outlined.Notifications
                            NotificationPreference.NONE -> Icons.Outlined.NotificationsOff
                        }
                        Icon(
                            imageVector = bellIcon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Disubscribe",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = "Subscribe",
                            color = Color.Black,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { tab ->
                val isMembershipTab = tab == VideoRepository.TAB_MEMBERSHIP
                val isSelected = tab == selectedTab
                val tabColor = when {
                    isSelected && isMembershipTab -> Color(0xFF0F9D58)
                    isSelected -> Color.White
                    isMembershipTab -> Color(0xFF81C784)
                    else -> Color(0xFFAAAAAA)
                }
                Column(
                    modifier = Modifier
                        .clickable { onTab(tab) }
                        .padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = tabNames[tab] ?: tab,
                            color = tabColor,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        )
                        if (tab == ChannelTabs.LIVESTREAMS && isLive) {
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFCC0000)))
                        } else if (isMembershipTab) {
                            Text("⭐", fontSize = 11.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (isSelected) (if (isMembershipTab) Color(0xFF0F9D58) else Color.White) else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text("Tentang channel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (description.isNotBlank()) {
                        Text(description, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Spacer(Modifier.height(16.dp))
                    }
                    if (page.info.subscriberCount >= 0) {
                        Text("Subscriber: ${formatCount(page.info.subscriberCount)}", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    }
                    if (page.info.url.isNotBlank()) {
                        Text("Tautan: ${page.info.url}", style = MaterialTheme.typography.bodySmall, color = Color.LightGray, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (!donationLinks.isNullOrEmpty()) {
                        Text("Link lainnya:", style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(top = 10.dp))
                        for (link in donationLinks) {
                            Text(link, style = MaterialTheme.typography.bodySmall, color = Accent, modifier = Modifier.clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                            }.padding(vertical = 2.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Tutup", color = Color.White)
                }
            },
            containerColor = Surface,
            textContentColor = Color.White,
            titleContentColor = Color.White
        )
    }
}

