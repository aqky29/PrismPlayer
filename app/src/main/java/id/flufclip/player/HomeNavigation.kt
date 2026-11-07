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
    // Google / iOS Liquid Glass Floating Navigation Bar:
    // - Latar luar sepenuhnya transparan tanpa blok penghalang.
    // - Kapsul utama memakai Liquid Glass jernih berwarna (AGSL meniscus refraction di sekeliling bibir kapsul + vibrancy tinggi).
    // - Pill indikator tab aktif memakai bantalan kaca smoked gelap tanpa garis putih (borderless) yang kontras & bersih,
    //   lengkap dengan fisika cair (stretch, squish, swell) saat berpindah atau digeser jari + ikon aktif biru muda (#C2E7FF).
    // - Di mobile (screenWidthDp < 600), auto-hide saat scroll ke bawah dan muncul kembali saat scroll ke atas.
    val isMobile = LocalConfiguration.current.screenWidthDp < 600
    val effectiveVisible = if (isMobile) visible else true
    val hideProgressState = animateFloatAsState(
        targetValue = if (effectiveVisible) 0f else 1f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "bottomNavAutoHide"
    )
    val hideProgress = hideProgressState.value
    if (hideProgress >= 0.999f) return

    val isDark = isSystemInDarkTheme() || true

    val tabs = remember { listOf("home", "shorts", "search", "you") }
    val activeIndex = remember(selected) {
        val idx = tabs.indexOf(selected)
        if (idx >= 0) idx else 0
    }

    val scope = rememberCoroutineScope()
    val pillProgress = remember { Animatable(activeIndex.toFloat()) }
    var isDraggingPill by remember { mutableStateOf(false) }
    var isTouchingActivePill by remember { mutableStateOf(false) }

    LaunchedEffect(activeIndex) {
        if (!isDraggingPill) {
            pillProgress.animateTo(
                targetValue = activeIndex.toFloat(),
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.76f,
                    stiffness = 360f
                )
            )
        }
    }

    // State pembengkakan fisik kapsul (swell) saat disentuh / di-drag
    val swellTarget = when {
        isDraggingPill -> 1f
        isTouchingActivePill -> 0.38f
        pillProgress.isRunning -> 0.14f
        else -> 0f
    }
    val swellState = animateFloatAsState(
        targetValue = swellTarget,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.66f,
            stiffness = 400f
        ),
        label = "liquidPillSwell"
    )

    // Kilau taktil tipis yang hanya muncul saat pill aktif di-drag jari (0f / tanpa border saat diam)
    val dragGlowTarget = if (isDraggingPill) 1f else 0f
    val dragGlowState = animateFloatAsState(
        targetValue = dragGlowTarget,
        animationSpec = tween(durationMillis = if (isDraggingPill) 120 else 220, easing = FastOutSlowInEasing),
        label = "liquidPillDragGlow"
    )

    // Pelacak kecepatan cairan (aktif hanya saat pill bergerak; saat diam = 0 callback / 0 fps)
    var fluidVelocity by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pillProgress.isRunning, isDraggingPill) {
        if (!pillProgress.isRunning && !isDraggingPill && kotlin.math.abs(fluidVelocity) < 0.01f) {
            fluidVelocity = 0f
            return@LaunchedEffect
        }
        var lastNanos = 0L
        var lastPos = pillProgress.value
        while (pillProgress.isRunning || isDraggingPill || kotlin.math.abs(fluidVelocity) >= 0.01f) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) * 1e-9f).coerceIn(0.001f, 0.05f)
                    val rawVel = if (pillProgress.isRunning && !isDraggingPill) {
                        pillProgress.velocity
                    } else {
                        (pillProgress.value - lastPos) / dt
                    }
                    val smoothing = (dt * 18f).coerceIn(0.12f, 0.65f)
                    fluidVelocity += (rawVel - fluidVelocity) * smoothing
                }
                lastNanos = now
                lastPos = pillProgress.value
            }
        }
        fluidVelocity = 0f
    }

    val visualIndex by remember {
        derivedStateOf { pillProgress.value.let { kotlin.math.round(it).toInt().coerceIn(0, 3) } }
    }

    val density = LocalDensity.current
    val slideDistancePx = with(density) { 96.dp.toPx() }
    var restingDockOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 10.dp)
            .offset { IntOffset(0, (hideProgressState.value * slideDistancePx).roundToInt()) }
            .graphicsLayer { alpha = (1f - hideProgressState.value).coerceIn(0f, 1f) },
        contentAlignment = Alignment.Center
    ) {
        // KAPSUL UTAMA GOOGLE LIQUID GLASS (Jernih membiaskan latar di tepi, tinggi 60.dp / R = 30.dp)
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 344.dp)
                .fillMaxWidth()
                .height(60.dp)
                .onGloballyPositioned { coords ->
                    val currentHideY = (hideProgressState.value * slideDistancePx).roundToInt().toFloat()
                    restingDockOriginInRoot = coords.positionInRoot() - Offset(0f, currentHideY)
                }
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(50),
                    ambientColor = Color.Black.copy(alpha = 0.36f),
                    spotColor = Color.Black.copy(alpha = 0.46f)
                )
                .clip(RoundedCornerShape(50))
                .liquidGlassBar(
                    isDark = isDark,
                    borderStrokeWidth = 1.dp,
                    rootPositionProvider = {
                        val currentHideY = (hideProgressState.value * slideDistancePx).roundToInt().toFloat()
                        Offset(
                            x = restingDockOriginInRoot.x,
                            y = restingDockOriginInRoot.y + currentHideY
                        )
                    }
                )
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val slotWidthPx = totalWidthPx / 4f
            // Geometri konsentris presisi (R_dock = 30.dp, inset = 5.dp -> R_pill = 25.dp / tinggi 50.dp)
            val pillWidth = (maxWidth / 4f).coerceIn(56.dp, 88.dp)
            val pillHeight = 50.dp
            // Kanvas sedikit lebih lebar agar regangan cair (stretch) & swell tidak memicu relayout
            val canvasWidth = pillWidth + 44.dp
            val canvasHeight = 60.dp
            val canvasWidthPx = with(density) { canvasWidth.toPx() }

            // Layer 1: Bantalan Pill Aktif Smoked Fluid (tanpa border putih saat diam, kontras bersih seperti Google App)
            Box(
                Modifier
                    .offset {
                        val exactCenterX = (pillProgress.value + 0.5f) * slotWidthPx
                        val exactLeftX = exactCenterX - canvasWidthPx * 0.5f
                        IntOffset(exactLeftX.roundToInt(), 0)
                    }
                    .size(width = canvasWidth, height = canvasHeight)
                    .smokedFluidPill(
                        baseWidth = pillWidth,
                        baseHeight = pillHeight,
                        isDark = isDark,
                        subPixelOffsetX = {
                            val exactCenterX = (pillProgress.value + 0.5f) * slotWidthPx
                            val exactLeftX = exactCenterX - canvasWidthPx * 0.5f
                            exactLeftX - exactLeftX.roundToInt().toFloat()
                        },
                        swellProvider = { swellState.value },
                        dragGlowProvider = { dragGlowState.value },
                        stretchProvider = {
                            (kotlin.math.abs(fluidVelocity) / 8.5f).coerceIn(0f, 1f) * 0.20f
                        }
                    )
            )

            // Layer 2: Baris 4 Tab Ikon (Home, Shorts, Search, You) + gestur tap & fluid slide
            val currentOnSelect by rememberUpdatedState(onSelect)
            val activeIconColor = Color(0xFFC2E7FF)
            val inactiveIconColor = Color.White.copy(alpha = 0.92f)

            Row(
                Modifier
                    .fillMaxSize()
                    .pointerInput(totalWidthPx, effectiveVisible) {
                        if (totalWidthPx <= 0f || !effectiveVisible) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var dragged = false
                            val startX = down.position.x
                            val downSlot = (startX / slotWidthPx).toInt().coerceIn(0, 3)
                            if (downSlot == kotlin.math.round(pillProgress.value).toInt().coerceIn(0, 3)) {
                                isTouchingActivePill = true
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        if (dragged) {
                                            val targetIdx = kotlin.math.round(pillProgress.value).toInt().coerceIn(0, 3)
                                            isDraggingPill = false
                                            isTouchingActivePill = false
                                            scope.launch {
                                                pillProgress.animateTo(
                                                    targetValue = targetIdx.toFloat(),
                                                    animationSpec = androidx.compose.animation.core.spring(
                                                        dampingRatio = 0.76f,
                                                        stiffness = 360f
                                                    )
                                                )
                                            }
                                            currentOnSelect(tabs[targetIdx])
                                        } else {
                                            isTouchingActivePill = false
                                            val tappedIdx = (change.position.x / slotWidthPx).toInt().coerceIn(0, 3)
                                            currentOnSelect(tabs[tappedIdx])
                                        }
                                        break
                                    }
                                    val dx = kotlin.math.abs(change.position.x - startX)
                                    if (!dragged && dx > viewConfiguration.touchSlop * 0.55f) {
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
                            } finally {
                                isDraggingPill = false
                                isTouchingActivePill = false
                            }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tabKey ->
                    val isSelected = (index == visualIndex)
                    val iconTint = if (isSelected) activeIconColor else inactiveIconColor

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                val proximity = (1f - kotlin.math.abs(pillProgress.value - index.toFloat())).coerceIn(0f, 1f)
                                val buoyScale = 1f + (0.06f * proximity * (0.30f + 0.70f * swellState.value))
                                scaleX = buoyScale
                                scaleY = buoyScale
                            },
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
                                val avatarRingColor = if (isSelected) activeIconColor else Color(0xFF4C8DF6)
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
                                            contentDescription = "You",
                                            tint = iconTint,
                                            modifier = Modifier.size(26.dp)
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

