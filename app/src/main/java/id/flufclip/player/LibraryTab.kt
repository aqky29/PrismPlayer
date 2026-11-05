package id.flufclip.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
internal fun LibraryTab(
    modifier: Modifier = Modifier,
    tab: String,
    videos: List<WatchedVideo>,
    historySource: String,
    historyLoading: Boolean,
    historyError: String?,
    onRetryHistory: () -> Unit,
    onHistory: () -> Unit,
    onOpenVideo: (WatchedVideo) -> Unit,
    onOpenPlaylist: (AccountPlaylist) -> Unit,
    historyActions: HistoryActions,
    onDownloads: () -> Unit,
    onAccount: () -> Unit,
    shortsLoading: Boolean,
    shortsError: String?,
    onSearchShorts: () -> Unit,
    subscriptionsContent: (@Composable () -> Unit)? = null
) {
    when (tab) {
        "you" -> {
            val context = LocalContext.current
            val compact = LocalConfiguration.current.screenWidthDp < 600
            val cardWidth = if (compact) 164.dp else 208.dp
            val downloadsRevision by SavedDownloads.revision.collectAsState()
            val savedDownloadsCount = remember(downloadsRevision) { SavedDownloads.list(context).size }

            LazyColumn(
                modifier = modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item {
                    ProfileHeaderSection(
                        compact = compact,
                        onAccount = onAccount,
                        onHistory = onHistory
                    )
                }

                item {
                    SectionHeaderRow(
                        title = "History",
                        subtitle = historySource,
                        actionText = "View all",
                        onAction = onHistory,
                        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
                    )
                }

                if (videos.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A1A1A))
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            when {
                                historyLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Text(
                                        "Loading YouTube history…",
                                        Modifier.padding(start = 12.dp),
                                        color = Color(0xFFAAAAAA),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                historyError != null -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        historyError,
                                        modifier = Modifier.weight(1f),
                                        color = Color(0xFFCCCCCC),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    YouTubePillButton(text = "Try again", onClick = onRetryHistory)
                                }
                                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.yt_w_history),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = Color(0xFF888888)
                                    )
                                    Text(
                                        if (YouTubeAuth.isLoggedIn) "No YouTube history is available to show yet."
                                        else "Videos watched on this device will appear here.",
                                        Modifier.padding(start = 12.dp),
                                        color = Color(0xFFAAAAAA),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (historyLoading) item {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .height(2.dp),
                            color = Color(0xFFFF0033),
                            trackColor = Color(0xFF272727)
                        )
                    }
                    if (historyError != null) item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1F1F1F))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                historyError,
                                Modifier.weight(1f),
                                color = Color(0xFFCCCCCC),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.width(8.dp))
                            YouTubePillButton(text = "Try again", onClick = onRetryHistory)
                        }
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(videos.take(10), key = { index, _ -> index }) { _, video ->
                                HistoryPreviewCard(
                                    video = video,
                                    width = cardWidth,
                                    actions = historyActions,
                                    onClick = { onOpenVideo(video) }
                                )
                            }
                        }
                    }
                }

                item {
                    AccountLibrarySection(
                        cardWidth = cardWidth,
                        onOpen = onOpenPlaylist,
                        onSignIn = onAccount
                    )
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                        color = Color(0xFF242424)
                    )
                    LibraryRow(
                        label = "Watch history",
                        subtitle = historySource,
                        onClick = onHistory,
                        icon = {
                            Icon(
                                painterResource(R.drawable.yt_w_history),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    )
                    LibraryRow(
                        label = "Downloads",
                        subtitle = if (savedDownloadsCount > 0) {
                            "$savedDownloadsCount ${if (savedDownloadsCount == 1) "video" else "videos"}"
                        } else {
                            "0 videos · Watch offline"
                        },
                        onClick = onDownloads,
                        trailing = {
                            if (savedDownloadsCount > 0) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded",
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF777777)
                                )
                            }
                        },
                        icon = {
                            Icon(
                                painterResource(R.drawable.yt_w_download),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    )
                }
            }
        }
        "subscriptions" -> {
            if (YouTubeAuth.isLoggedIn && subscriptionsContent != null) {
                Box(modifier.fillMaxWidth()) { subscriptionsContent() }
            } else {
                val configuration = LocalConfiguration.current
                val isTabletLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && configuration.screenWidthDp >= 600

                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isTabletLandscape) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 48.dp, vertical = 24.dp)
                                .widthIn(max = 760.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.yt_no_subscriptions),
                                contentDescription = null,
                                modifier = Modifier.size(200.dp)
                            )
                            Spacer(Modifier.width(36.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    "Don’t miss new videos",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "Sign in to see updates from your favorite YouTube channels",
                                    modifier = Modifier.padding(top = 8.dp),
                                    color = Color(0xFFAAAAAA),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    onClick = onAccount,
                                    modifier = Modifier
                                        .padding(top = 20.dp)
                                        .height(38.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color(0xFF0F0F0F)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
                                ) {
                                    Text("Sign in", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                                .widthIn(max = 380.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(R.drawable.yt_no_subscriptions),
                                contentDescription = null,
                                modifier = Modifier.size(200.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Don’t miss new videos",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "Sign in to see updates from your favorite YouTube channels",
                                modifier = Modifier.padding(top = 8.dp),
                                color = Color(0xFFAAAAAA),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onAccount,
                                modifier = Modifier
                                    .padding(top = 20.dp)
                                    .height(38.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF0F0F0F)
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
                            ) {
                                Text("Sign in", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
        else -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(56.dp), tint = Color.LightGray)
                Spacer(Modifier.height(20.dp))
                Text("Shorts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (shortsLoading) CircularProgressIndicator(Modifier.padding(top = 16.dp))
                else {
                    Text(shortsError ?: "Open a Short to start watching.", Modifier.padding(top = 8.dp), color = Color.LightGray)
                    Button(onClick = onSearchShorts, modifier = Modifier.padding(top = 16.dp)) { Text(if (shortsError == null) "Open Shorts" else "Try again") }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeaderSection(
    compact: Boolean,
    onAccount: () -> Unit,
    onHistory: () -> Unit
) {
    val profile = YouTubeAuth.profile
    val loggedIn = YouTubeAuth.isLoggedIn
    val avatarSize = if (compact) 68.dp else 76.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAccount)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (compact) 14.dp else 18.dp,
                    bottom = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(Color(0xFF242424))
                    .border(1.dp, Color(0xFF333333), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!profile.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(avatarSize * 0.78f),
                        tint = Color(0xFFAAAAAA)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = profile.name.ifBlank { if (loggedIn) "YouTube account" else "You" },
                    fontSize = if (compact) 22.sp else 24.sp,
                    lineHeight = if (compact) 26.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val secondary = when {
                        !loggedIn -> "Sign in to YouTube"
                        profile.emailOrHandle.isNotBlank() -> "${profile.emailOrHandle} · View account"
                        else -> "View account"
                    }
                    Text(
                        text = secondary,
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(16.dp),
                        tint = Color(0xFFAAAAAA)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            YouTubeActionChip(
                label = if (loggedIn) "Switch account" else "Sign in",
                onClick = onAccount,
                leadingIcon = {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            )
            if (loggedIn && YouTubeAuth.savedAccounts.size > 1) {
                YouTubeActionChip(
                    label = "${YouTubeAuth.savedAccounts.size} accounts",
                    onClick = onAccount,
                    leadingIcon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = Color(0xFF2BA640)
                        )
                    }
                )
            }
            YouTubeActionChip(
                label = "Watch history",
                onClick = onHistory,
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.yt_w_history),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            )
        }
    }
}

@Composable
internal fun SectionHeaderRow(
    title: String,
    subtitle: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = Color(0xFF9E9E9E),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!actionText.isNullOrBlank() && onAction != null) {
            YouTubePillButton(text = actionText, onClick = onAction)
        }
    }
}

@Composable
internal fun YouTubePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(32.dp),
        shape = CircleShape,
        color = Color(0xFF272727),
        contentColor = if (enabled) Color.White else Color(0xFF777777)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun YouTubeActionChip(
    label: String,
    onClick: () -> Unit,
    leadingIcon: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF272727),
        contentColor = Color.White,
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leadingIcon()
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LibraryRow(
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF1F1F1F)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp)
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color(0xFF777777)
            )
        }
    }
}

@Composable
private fun HistoryPreviewCard(
    video: WatchedVideo,
    width: Dp,
    actions: HistoryActions,
    onClick: () -> Unit
) {
    val playlistId = video.playlistId?.takeIf { it.isNotBlank() }
    val primaryTitle = video.playlistTitle?.takeIf { it.isNotBlank() } ?: video.title
    val progress = video.progressFraction
        ?: if (video.position > 0 && video.duration > 0) video.position.toFloat() / video.duration else null

    Column(Modifier.width(width)) {
        if (playlistId != null) {
            PlaylistStackedThumbnail(
                playlist = AccountPlaylist(playlistId, primaryTitle, video.thumbnail),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                badgeText = video.playlistVideoCount?.takeIf { it.isNotBlank() } ?: "Playlist",
                progressFraction = progress
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF212121))
                    .clickable(onClick = onClick)
            ) {
                if (video.isShort) {
                    AsyncImage(
                        video.thumbnail,
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = .22f
                    )
                    AsyncImage(
                        video.thumbnail,
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Icon(
                        painterResource(R.drawable.yt_nav_shorts_active),
                        contentDescription = "Shorts",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = .78f), CircleShape)
                            .padding(4.dp),
                        tint = Color.White
                    )
                } else {
                    AsyncImage(
                        video.thumbnail,
                        null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                if (video.duration > 0) {
                    Text(
                        formatHistoryDuration(video.duration),
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color(0xD9000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (progress != null && progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color(0x55FFFFFF))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(Color(0xFFFF0033))
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Text(
                    text = primaryTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                val byline = if (playlistId != null) {
                    listOf(video.uploader, "Playlist").filter { it.isNotBlank() }.joinToString(" · ")
                } else video.uploader
                if (byline.isNotBlank()) {
                    Text(
                        text = byline,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF9E9E9E),
                        fontSize = 11.sp
                    )
                }
            }
            HistoryVideoMenu(video, actions, compact = true)
        }
    }
}

private fun formatHistoryDuration(durationMs: Long): String {
    val seconds = durationMs / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
