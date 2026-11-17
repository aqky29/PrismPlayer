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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
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

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import kotlin.math.roundToInt

/**
 * State auto-hide untuk Floating Bottom Navigation di layar mobile.
 * Menyembunyikan bilah navigasi saat pengguna menggulir ke bawah dan memunculkannya kembali saat menggulir ke atas.
 */
@Stable
internal class BottomNavAutoHideState(
    private val hideThresholdPx: Float,
    private val showThresholdPx: Float,
) {
    var visible by mutableStateOf(true)
        private set

    private var accumulatedDy = 0f

    fun show() {
        visible = true
        accumulatedDy = 0f
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val dy = when {
                consumed.y != 0f -> consumed.y
                !visible && available.y > 0f && source == NestedScrollSource.UserInput -> available.y
                else -> 0f
            }
            if (dy < 0f) {
                if (accumulatedDy > 0f) accumulatedDy = 0f
                accumulatedDy += dy
                if (accumulatedDy <= -hideThresholdPx) {
                    visible = false
                    accumulatedDy = 0f
                }
            } else if (dy > 0f) {
                if (accumulatedDy < 0f) accumulatedDy = 0f
                accumulatedDy += dy
                if (accumulatedDy >= showThresholdPx || (consumed.y > 0f && available.y > 0f)) {
                    visible = true
                    accumulatedDy = 0f
                }
            }
            return Offset.Zero
        }
    }
}

@Composable
internal fun rememberBottomNavAutoHideState(vararg resetKeys: Any?): BottomNavAutoHideState {
    val density = LocalDensity.current
    val state = remember(density) {
        BottomNavAutoHideState(
            hideThresholdPx = with(density) { 18.dp.toPx() },
            showThresholdPx = with(density) { 10.dp.toPx() },
        )
    }
    LaunchedEffect(*resetKeys) {
        state.show()
    }
    return state
}

internal fun Modifier.bottomNavAutoHide(state: BottomNavAutoHideState): Modifier =
    nestedScroll(state.nestedScrollConnection)

@Composable internal fun HomeNavigation(
    selected: String,
    onSelect: (String) -> Unit,
    visible: Boolean = true,
    docked: Boolean = false,
) {
    val isMobile = LocalConfiguration.current.screenWidthDp < 600
    val effectiveVisible = if (docked || !isMobile) true else visible
    val hideProgressState = animateFloatAsState(
        targetValue = if (effectiveVisible) 0f else 1f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "bottomNavAutoHide"
    )

    val tabs = remember { listOf("home", "shorts", "search", "you") }
    val activeIndex = remember(selected) {
        val idx = tabs.indexOf(selected)
        if (idx >= 0) idx else 0
    }
    val density = LocalDensity.current
    val slideDistancePx = with(density) { 72.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!docked) Modifier.navigationBarsPadding() else Modifier)
            .offset { IntOffset(0, (hideProgressState.value * slideDistancePx).roundToInt()) }
            .graphicsLayer { alpha = (1f - hideProgressState.value).coerceIn(0f, 1f) }
            .background(Background)
    ) {
        HorizontalDivider(thickness = 1.dp, color = Color(0xFF272727))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tabKey ->
                val isSelected = (index == activeIndex)
                val tabLabel = when (tabKey) {
                    "home" -> "Home"
                    "shorts" -> "Shorts"
                    "search" -> "Search"
                    else -> "You"
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelect(tabKey) }
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = tabLabel
                        },
                    contentAlignment = Alignment.Center
                ) {
                    HomeNavTabIcon(
                        tabKey = tabKey,
                        isSelected = isSelected,
                        iconTint = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                        avatarRingColor = if (isSelected) Color.White else Color(0xFF4C8DF6)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeNavTabIcon(
    tabKey: String,
    isSelected: Boolean,
    iconTint: Color,
    avatarRingColor: Color,
) {
    when (tabKey) {
        "home" -> {
            Icon(
                painter = painterResource(if (isSelected) R.drawable.yt_nav_home_active else R.drawable.yt_nav_home),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        "shorts" -> {
            Icon(
                painter = painterResource(if (isSelected) R.drawable.yt_nav_shorts_active else R.drawable.yt_nav_shorts),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        "search" -> {
            Icon(
                painter = painterResource(R.drawable.yt_reel_search),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        "you" -> {
            val loggedIn = YouTubeAuth.isLoggedIn
            val avatarUrl = if (loggedIn) YouTubeAuth.profile.avatarUrl?.takeIf { it.isNotBlank() } else null
            val profileName = if (loggedIn) YouTubeAuth.profile.name.trim() else ""
            when {
                avatarUrl != null -> {
                    Avatar(
                        avatarUrl,
                        profileName,
                        26,
                        Modifier
                            .border(1.75.dp, avatarRingColor, CircleShape)
                            .padding(1.75.dp)
                    )
                }
                profileName.isNotEmpty() -> {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF283142))
                            .border(1.5.dp, avatarRingColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profileName.first().uppercaseChar().toString(),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TopCategoryTabs(
    activeTab: String,
    moreCategories: List<HomeChip>,
    onSelectTab: (String) -> Unit,
    onSelectMoreChip: (HomeChip) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreSheet by remember { mutableStateOf(false) }
    val tabs = remember { listOf("Home", "Subs", "Music", "Live", "More") }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tabName ->
                val isActive = (tabName == activeTab)
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .selectable(
                            selected = isActive,
                            role = Role.Tab,
                            onClick = {
                                if (tabName == "More") {
                                    showMoreSheet = true
                                } else {
                                    onSelectTab(tabName)
                                }
                            }
                        )
                        .padding(horizontal = 4.dp)
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tabName,
                        color = if (isActive) Color.White else Color(0xFFAAAAAA),
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (isActive) Color(0xFFFF0000) else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = Color(0xFF272727))
    }

    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            containerColor = Color(0xFF272727),
            contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF717171))
                )
            }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    "Explore Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                val displayChips = if (moreCategories.isNotEmpty()) moreCategories else listOf("Gaming", "News", "Movies", "Sports", "Trending").map { HomeChip(it) }
                displayChips.forEach { chip ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) {
                                showMoreSheet = false
                                onSelectMoreChip(chip)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(chip.title, color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

