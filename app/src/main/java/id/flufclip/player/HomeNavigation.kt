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
) {
    // Floating Frosted Pill Navigation Bar:
    // - Latar luar sepenuhnya transparan (tidak ada blok hitam penghalang di belakangnya).
    // - Kapsul mengambang menggunakan Frosted Glass (blur backdrop resolusi rendah + vibrancy + specular border).
    // - Pill indikator abu-abu dapat berpindah dengan animasi spring saat diketuk maupun digeser (interactive slide).
    // - Di mobile (screenWidthDp < 600), dapat auto-hide saat scroll ke bawah dan muncul kembali saat scroll ke atas.
    val isMobile = LocalConfiguration.current.screenWidthDp < 600
    val effectiveVisible = if (isMobile) visible else true
    val hideProgress by animateFloatAsState(
        targetValue = if (effectiveVisible) 0f else 1f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "bottomNavAutoHide"
    )
    if (hideProgress >= 0.999f) return

    val isDark = isSystemInDarkTheme() || true
    val pillColor = if (isDark) Color(0xFF3E3E3E).copy(alpha = 0.85f) else Color(0xFFE5E5E5).copy(alpha = 0.90f)
    val pillBorderColor = if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)

    val tabs = remember { listOf("home", "shorts", "search", "you") }
    val activeIndex = remember(selected) {
        val idx = tabs.indexOf(selected)
        if (idx >= 0) idx else 0
    }

    val scope = rememberCoroutineScope()
    val pillProgress = remember { Animatable(activeIndex.toFloat()) }
    var isDraggingPill by remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex) {
        if (!isDraggingPill) {
            pillProgress.animateTo(
                targetValue = activeIndex.toFloat(),
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.80f,
                    stiffness = 380f
                )
            )
        }
    }

    val visualIndex by remember {
        derivedStateOf { pillProgress.value.let { kotlin.math.round(it).toInt().coerceIn(0, 3) } }
    }

    val density = LocalDensity.current
    val slideDistancePx = with(density) { 96.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .offset { IntOffset(0, (hideProgress * slideDistancePx).roundToInt()) }
            .graphicsLayer { alpha = (1f - hideProgress).coerceIn(0f, 1f) },
        contentAlignment = Alignment.Center
    ) {
        // FLOATING FROSTED PILL CONTAINER (Compact Centered Dock pada Tablet/Layar Besar maks 380.dp)
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .height(58.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(50),
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.55f)
                )
                .clip(RoundedCornerShape(50))
                .frostedGlassCapsule(isDark = isDark, borderStrokeWidth = 1.dp)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val slotWidthPx = totalWidthPx / 4f
            val pillWidth = ((maxWidth / 4f) - 12.dp).coerceIn(56.dp, 76.dp)
            val pillHeight = 40.dp
            val pillWidthPx = with(density) { pillWidth.toPx() }

            // Layer 1: Pill Indikator Abu-Abu yang meluncur halus (offset dibaca di fase layout agar 0 recompose tiap frame)
            Box(
                Modifier
                    .offset {
                        val centerOffsetPx = (slotWidthPx - pillWidthPx) / 2f
                        val xPx = (pillProgress.value * slotWidthPx) + centerOffsetPx
                        IntOffset(xPx.let { kotlin.math.round(it).toInt() }, 0)
                    }
                    .size(width = pillWidth, height = pillHeight)
                    .clip(RoundedCornerShape(50))
                    .background(pillColor)
                    .border(0.8.dp, pillBorderColor, RoundedCornerShape(50))
            )

            // Layer 2: Baris 4 Tab Ikon Resmi YouTube (Home, Shorts, Search, You) + gestur tap & slide
            val currentOnSelect by rememberUpdatedState(onSelect)
            Row(
                Modifier
                    .fillMaxSize()
                    .pointerInput(totalWidthPx, effectiveVisible) {
                        if (totalWidthPx <= 0f || !effectiveVisible) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var dragged = false
                            val startX = down.position.x

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    if (dragged) {
                                        val targetIdx = kotlin.math.round(pillProgress.value).toInt().coerceIn(0, 3)
                                        isDraggingPill = false
                                        scope.launch {
                                            pillProgress.animateTo(
                                                targetValue = targetIdx.toFloat(),
                                                animationSpec = androidx.compose.animation.core.spring(
                                                    dampingRatio = 0.80f,
                                                    stiffness = 380f
                                                )
                                            )
                                        }
                                        currentOnSelect(tabs[targetIdx])
                                    } else {
                                        val tappedIdx = (change.position.x / slotWidthPx).toInt().coerceIn(0, 3)
                                        currentOnSelect(tabs[tappedIdx])
                                    }
                                    break
                                }
                                val dx = kotlin.math.abs(change.position.x - startX)
                                if (!dragged && dx > viewConfiguration.touchSlop * 0.6f) {
                                    dragged = true
                                    isDraggingPill = true
                                }
                                if (dragged) {
                                    change.consume()
                                    val frac = ((change.position.x - slotWidthPx / 2f) / slotWidthPx).coerceIn(0f, 3f)
                                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                        pillProgress.snapTo(frac)
                                    }
                                }
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tabKey ->
                    val isSelected = (index == visualIndex)
                    val iconTint = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f)

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (tabKey) {
                            "home" -> {
                                Icon(
                                    painter = painterResource(if (isSelected) R.drawable.yt_nav_home_active else R.drawable.yt_nav_home),
                                    contentDescription = "Home",
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            "shorts" -> {
                                Icon(
                                    painter = painterResource(if (isSelected) R.drawable.yt_nav_shorts_active else R.drawable.yt_nav_shorts),
                                    contentDescription = "Shorts",
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            "search" -> {
                                Icon(
                                    painter = painterResource(R.drawable.yt_reel_search),
                                    contentDescription = "Search",
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
                                            Modifier.then(
                                                if (isSelected) Modifier.border(1.5.dp, Color.White, CircleShape).padding(1.5.dp) else Modifier
                                            )
                                        )
                                    }
                                    profileName.isNotEmpty() -> {
                                        Box(
                                            Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF383838))
                                                .then(
                                                    if (isSelected) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier
                                                ),
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
                                            contentDescription = "You",
                                            tint = iconTint,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .then(
                                                    if (isSelected) Modifier.border(1.5.dp, Color.White, CircleShape).clip(CircleShape) else Modifier
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tabName ->
            val isActive = (tabName == activeTab)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        if (tabName == "More") {
                            showMoreSheet = true
                        } else {
                            onSelectTab(tabName)
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tabName,
                    color = if (isActive) Color.White else Color(0xFFAAAAAA),
                    fontSize = 15.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .height(2.5.dp)
                        .width(if (isActive) (tabName.length * 9).dp else 0.dp)
                        .background(if (isActive) Color(0xFFFF0000) else Color.Transparent, RoundedCornerShape(1.dp))
                )
            }
        }
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
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
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

