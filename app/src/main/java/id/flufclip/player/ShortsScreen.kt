@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package id.flufclip.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import kotlin.math.roundToInt

/** Keadaan gestur Shorts yang dibagi antara layar, halaman aktif, dan bar progress. */
@Stable
private class ShortsUiState {
    var holding by mutableStateOf(false)
    var pulled by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var scrubbing by mutableStateOf(false)
    var scrubFraction by mutableFloatStateOf(0f)
    /** Kecepatan pilihan pengguna dari menu; tahan/kunci menimpanya sementara dengan 2x. */
    var speed by mutableFloatStateOf(1f)
}

/** Warna bar progress Shorts: `reel_time_bar_played_color` di APK YouTube 21.43.523 bernilai merah (yt_medium_red). */
private val ShortsPlayedRed = Color(0xFFFF0000)

private class Heart(val id: Long, val pos: Offset, val tilt: Float)

/**
 * Layar Shorts: pager vertikal yang mengikuti jari. Setiap halaman langsung menampilkan poster dari ID video
 * (tanpa menunggu resolve), jadi geser terasa instan; video baru muncul di atas poster begitu pemutar siap.
 * Satu [ExoPlayer] dipakai bersama dan hanya dipasang ke halaman yang sedang aktif.
 *
 * Gestur, ukuran, dan panel mengikuti pengamatan statis pada YouTube 21.43.523 (lihat docs/ANALISIS_GESTUR_UX_SHORTS_APPBAR_YOUTUBE_REA.md
 * dan docs/ANALISIS_PANEL_SHORTS_YOUTUBE_REA.md): ketuk = putar/jeda, ketuk dua kali = suka, tahan = 2x (tarik bawah = kunci),
 * geser di bar progress = scrub, komentar dengan scrim, menu titik tiga berupa lembar bawah.
 */
@Composable
internal fun ShortsScreen(
    items: List<StreamInfoItem>,
    startIndex: Int,
    generation: Int,
    resolver: ShortsResolver,
    activeUrl: String?,
    player: ExoPlayer,
    emptyLoading: Boolean,
    emptyMessage: String?,
    feedMessage: String?,
    onSettled: (Int) -> Unit,
    onRetry: () -> Unit,
    onRetryMore: () -> Unit,
    onRetryItem: (String) -> Unit,
    onLink: (StreamInfo, String) -> Unit,
    onChannel: (String) -> Unit,
    onClose: (() -> Unit)?,
    onShare: (StreamInfo) -> Unit,
    onSave: (String) -> Unit,
    onRate: (StreamInfo, Int) -> Unit,
    onSubscribe: (StreamInfo, Boolean) -> Unit = { _, _ -> },
    onNeedLogin: () -> Unit,
    onNotInterested: (StreamInfoItem) -> Unit,
    onSearch: () -> Unit,
    onDownload: (StreamInfo) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var commentsFor by remember { mutableStateOf<StreamInfo?>(null) }
    // Panel deskripsi (`engagement-panel-structured-description` di YouTube): dibuka dengan menahan di zona tengah atau mengetuk judul.
    var descriptionFor by remember { mutableStateOf<StreamInfo?>(null) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    BackHandler(commentsFor != null || descriptionFor != null) { commentsFor = null; descriptionFor = null }
    val activeKey = activeUrl?.let(::videoKey)
    val scope = rememberCoroutineScope()
    val ui = remember { ShortsUiState() }
    var userPaused by remember { mutableStateOf(false) }
    LaunchedEffect(activeKey) { userPaused = false; ui.locked = false; ui.holding = false; ui.pulled = false; ui.scrubbing = false }
    // Kecepatan: pemutar bersama dikembalikan ke nilai awalnya saat layar ini hilang; 2x berlaku selama menahan atau terkunci.
    DisposableEffect(player) {
        val entrySpeed = player.playbackParameters.speed
        onDispose { runCatching { player.setPlaybackSpeed(entrySpeed) } }
    }
    LaunchedEffect(player, ui.holding, ui.locked, ui.speed) {
        player.setPlaybackSpeed(if (ui.holding || ui.locked) 2f else ui.speed)
    }
    // Volume: pastikan pemutar tidak mewarisi state mute dari inline feed; volume awal dikembalikan saat layar Shorts hilang.
    DisposableEffect(player) {
        val entryVolume = player.volume
        onDispose { runCatching { player.volume = entryVolume } }
    }
    LaunchedEffect(player) { player.volume = 1f }
    // Saat menahan atau men-scrub, kolom aksi, judul, dan bar atas memudar (mode bersih), 125 ms seperti YouTube.
    val chrome = animateFloatAsState(if (ui.holding || ui.scrubbing) 0f else 1f, tween(125), label = "shortsChrome")
    // Status suka dibaca dari Engagement: sumber yang sama dengan Watch, fullscreen, dan player offline.
    val rate: (StreamInfo, Int) -> Unit = { info, direction ->
        if (!YouTubeAuth.isLoggedIn) {
            Toast.makeText(context, "Sign in to rate videos", Toast.LENGTH_SHORT).show()
            onNeedLogin()
        } else onRate(info, direction)
    }
    // Ketuk dua kali menyukai (tidak membatalkan suka); hati hanya muncul bila memang bisa menilai.
    val doubleTapLike: (StreamInfo) -> Boolean = { info ->
        if (Engagement.rating(videoKey(info.url)).value != 1) rate(info, 1)
        true
    }
    val pagerState = key(generation) { rememberPagerState(initialPage = startIndex.coerceIn(0, maxOf(0, items.lastIndex))) { items.size } }
    var menuOpen by remember { mutableStateOf(false) }
    // Status komentar per Short (maks. 8): dimuat untuk halaman aktif supaya jumlah komentar tampil di bawah ikon,
    // dan dipakai ulang oleh panel komentar tanpa permintaan kedua.
    val commentStates = remember { mutableStateMapOf<String, CommentsState>() }
    val commentOrder = remember { ArrayDeque<String>() }
    val commentsOf: (StreamInfo) -> CommentsState = { info ->
        commentOrder.remove(info.url)
        commentOrder.addLast(info.url)
        commentStates[info.url] ?: CommentsState(info.url, scope).also { state ->
            commentStates[info.url] = state
            scope.launch { state.load() }
            while (commentStates.size > 8) {
                val oldest = commentOrder.firstOrNull { it != info.url && it != commentsFor?.url } ?: break
                commentOrder.remove(oldest)
                commentStates.remove(oldest)
            }
        }
    }
    val settledInfo = items.getOrNull(pagerState.settledPage)?.let { resolver.info(it.url) }
    LaunchedEffect(settledInfo?.url) { settledInfo?.let { commentsOf(it) } }
    // Status akun Short aktif dimuat sendiri; tanpa ini Short yang belum pernah dibuka di Watch selalu tampil "belum".
    LaunchedEffect(settledInfo?.url, YouTubeAuth.sessionRevision) {
        val info = settledInfo ?: return@LaunchedEffect
        if (!YouTubeAuth.isLoggedIn) return@LaunchedEffect
        delay(300)
        val revision = YouTubeAuth.sessionRevision
        val videoId = info.id.ifBlank { videoKey(info.url) }
        val known = Engagement.rating(videoId)
        // Hanya ambil status yang belum diketahui; jangan menambah satu request setiap kali Short ditonton ulang.
        if (known.confirmed != null && !known.uncertain) return@LaunchedEffect
        val channelId = Regex("/channel/(UC[\\w-]{22})").find(info.uploaderUrl.orEmpty())?.groupValues?.get(1)
        val generation = Engagement.generation(videoId, channelId)
        val snapshot = try { EngagementSource.fetch(videoId, revision) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
        if (snapshot != null) Engagement.refresh(videoId, snapshot, revision, generation, channelId)
    }

    FrostedGlassBackdropHost(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()) {
        val glassBackdrop = LocalFrostedGlassBackdrop.current
        BoxWithConstraints(Modifier.fillMaxSize().frostedGlassSource(glassBackdrop)) {
            // Layar lebar: komentar jadi panel samping (±31% lebar) dan video bergeser ke tengah area kiri, seperti YouTube di tablet.
            val sideComments = isWideWatchLayout(maxWidth, maxHeight)
            val panelWidth = (maxWidth * 0.31f).coerceIn(340.dp, 460.dp)
            val contentEnd by animateDpAsState(if (sideComments && (commentsFor != null || descriptionFor != null)) panelWidth else 0.dp, tween(250), label = "shortsPanelInset")
            Box(Modifier.fillMaxSize().padding(end = contentEnd)) {
                if (items.isEmpty()) {
                    Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (emptyLoading || emptyMessage == null) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 2.dp)
                        else {
                            Text(emptyMessage, color = Color.White, textAlign = TextAlign.Center)
                            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Try again") }
                        }
                    }
                } else key(generation) {
                    val settledCallback by rememberUpdatedState(onSettled)
                    LaunchedEffect(pagerState) { snapshotFlow { pagerState.settledPage }.collect { settledCallback(it) } }
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 1,
                        userScrollEnabled = commentsFor == null && descriptionFor == null && !ui.holding && !ui.scrubbing,
                        flingBehavior = PagerDefaults.flingBehavior(pagerState, pagerSnapDistance = PagerSnapDistance.atMost(1)),
                        key = { "${items.getOrNull(it)?.let { item -> videoKey(item.url) }}#$it" },
                    ) { page ->
                        val item = items.getOrNull(page) ?: return@VerticalPager
                        val settled = pagerState.settledPage == page
                        ShortPage(
                            item = item, settled = settled, info = resolver.info(item.url),
                            meta = resolver.meta(item.url),
                            error = resolver.error(item.url),
                            showsPlayer = settled && activeKey == videoKey(item.url),
                            player = player, playing = playing, buffering = buffering, userPaused = userPaused,
                            rating = Engagement.rating(videoKey(item.url)).value ?: 0, onRate = rate, onDoubleTapLike = doubleTapLike,
                            onSubscribe = onSubscribe,
                            ui = ui, chrome = chrome,
                            feedMessage = feedMessage.takeIf { page == items.lastIndex },
                            onTogglePlay = { if (player.isPlaying) { player.pause(); userPaused = true } else { player.play(); userPaused = false } },
                            commentCount = resolver.info(item.url)?.let { commentStates[it.url]?.info?.commentsCount }?.takeIf { it >= 0 },
                            onComments = { commentsOf(it); descriptionFor = null; commentsFor = it }, onDescription = { commentsFor = null; descriptionFor = it },
                            onChannel = onChannel, onShare = onShare, onSave = onSave, onDownload = onDownload,
                            onLink = { onLink(resolver.info(item.url) ?: StreamInfo(0, item.url, item.url, org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM, videoKey(item.url), item.name, 0), it) },
                            onRetryItem = { onRetryItem(item.url) }, onRetryMore = onRetryMore,
                        )
                    }
                }
                // Gradasi atas 96 dp dan bar atas; ikut memudar pada mode bersih.
                Box(Modifier.fillMaxWidth().height(96.dp).align(Alignment.TopCenter).graphicsLayer { alpha = chrome.value }.background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
                Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth().height(56.dp).graphicsLayer { alpha = chrome.value }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onClose != null) IconButton(onClick = onClose, modifier = Modifier.clip(CircleShape).background(Color(0x66000000))) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onSearch) { Icon(painterResource(R.drawable.yt_reel_search), "Search", tint = Color.White) }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreHoriz, "More options", tint = Color.White) }
                }
            }
            commentsFor?.let { info ->
                val comments = commentStates[info.url]
                val thread = remember(info.url) { mutableStateOf<CommentsInfoItem?>(null) }
                if (comments != null && sideComments) {
                    // Panel samping tanpa scrim; video tetap terlihat di area kiri (lihat contentEnd).
                    CommentsPanel(
                        comments, info, draggable = false, onDismiss = { commentsFor = null }, onLink = { onLink(info, it) }, onChannel = onChannel,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().statusBarsPadding().width(panelWidth).pointerInput(Unit) { detectTapGestures { } },
                        threadState = thread, showHandle = false,
                    )
                } else if (comments != null) {
                    // Scrim di luar panel: ketuk menutup panel; saat replies terbuka, ketuk kembali ke daftar komentar (seperti POP_STACK).
                    Box(Modifier.fillMaxSize().background(Color(0x66000000)).pointerInput(info.url) {
                        detectTapGestures { if (thread.value != null) thread.value = null else commentsFor = null }
                    })
                    CommentsPanel(
                        comments, info, draggable = true, onDismiss = { commentsFor = null }, onLink = { onLink(info, it) }, onChannel = onChannel,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.7f).pointerInput(Unit) { detectTapGestures { } },
                        threadState = thread,
                    )
                }
            }
            descriptionFor?.let { info ->
                val descriptionPanel: @Composable (Modifier, Boolean) -> Unit = { panelModifier, side ->
                    DescriptionPanel(
                        info, draggable = !side, onDismiss = { descriptionFor = null }, onLink = { onLink(info, it) },
                        onSeek = { seconds -> player.seekTo(seconds * 1000L); player.play() }, onChannel = { onChannel(info.uploaderUrl.orEmpty()) },
                        modifier = panelModifier.pointerInput(Unit) { detectTapGestures { } }, showHandle = !side,
                    )
                }
                if (sideComments) descriptionPanel(Modifier.align(Alignment.CenterEnd).fillMaxHeight().statusBarsPadding().width(panelWidth), true)
                else {
                    Box(Modifier.fillMaxSize().background(Color(0x66000000)).pointerInput(info.url) { detectTapGestures { descriptionFor = null } })
                    descriptionPanel(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.7f), false)
                }
            }
            if (menuOpen) {
                val menuItem = items.getOrNull(pagerState.settledPage)
                val menuLink = menuItem?.let { "https://www.youtube.com/shorts/${videoKey(it.url)}" }
                ShortsMenuSheet(
                    speed = if (ui.locked) 2f else ui.speed, canAct = menuLink != null, canHide = menuItem != null,
                    onSpeed = {
                        ui.locked = false
                        ui.speed = it
                    },
                    onSave = { menuItem?.let { onSave(videoKey(it.url)) } },
                    onCopy = {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Video link", menuLink))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                    },
                    onOpen = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(menuLink))) } },
                    onNotInterested = {
                        menuItem?.let {
                            onNotInterested(it)
                            if (pagerState.settledPage < items.lastIndex) scope.launch { pagerState.animateScrollToPage(pagerState.settledPage + 1) }
                        }
                    },
                    onDismiss = { menuOpen = false },
                )
            }
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            bottomBar()
        }
    }
}

@Composable
private fun ShortPage(
    item: StreamInfoItem,
    settled: Boolean,
    info: StreamInfo?,
    meta: ShortsMeta?,
    error: String?,
    showsPlayer: Boolean,
    player: ExoPlayer,
    playing: Boolean,
    buffering: Boolean,
    userPaused: Boolean,
    rating: Int,
    onRate: (StreamInfo, Int) -> Unit,
    onDoubleTapLike: (StreamInfo) -> Boolean,
    onSubscribe: (StreamInfo, Boolean) -> Unit,
    ui: ShortsUiState,
    chrome: State<Float>,
    feedMessage: String?,
    onTogglePlay: () -> Unit,
    commentCount: Int?,
    onComments: (StreamInfo) -> Unit,
    onDescription: (StreamInfo) -> Unit,
    onChannel: (String) -> Unit,
    onShare: (StreamInfo) -> Unit,
    onSave: (String) -> Unit,
    onDownload: (StreamInfo) -> Unit,
    onLink: (String) -> Unit,
    onRetryItem: () -> Unit,
    onRetryMore: () -> Unit,
) {
    val id = videoKey(item.url)
    var posterFailed by remember(id) { mutableStateOf(false) }
    var firstFrameRendered by remember(id) { mutableStateOf(false) }
    DisposableEffect(player, showsPlayer, id) {
        if (!showsPlayer) {
            firstFrameRendered = false
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            firstFrameRendered = false
            ui.holding = false
            ui.pulled = false
        }
    }
    val hearts = remember { mutableStateListOf<Heart>() }
    val haptic = LocalHapticFeedback.current
    val showSpinner = settled && error == null && (info == null || (showsPlayer && buffering))
    val showPaused = showsPlayer && userPaused && !playing && error == null && !showSpinner
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (showsPlayer) AndroidView(
            factory = { LayoutInflater.from(it).inflate(R.layout.shorts_player_view, null, false) as PlayerView },
            update = { it.player = player }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize(),
        )
        // Poster veil di atas SurfaceView untuk mencegah lubang hitam sebelum frame pertama ter-render
        if (!showsPlayer || !firstFrameRendered) {
            AsyncImage(
                model = "https://i.ytimg.com/vi/$id/${if (posterFailed) "hqdefault" else "frame0"}.jpg", contentDescription = null,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), onError = { posterFailed = true },
            )
        }
        // Lapisan interaksi: ketuk, ketuk dua kali, dan tahan (di bawah semua tombol, jadi tombol tetap menang di area mereka).
        Box(Modifier.fillMaxSize().shortsGestures(
            enabled = showsPlayer,
            doubleTapEnabled = info != null,
            onTap = onTogglePlay,
            onDoubleTap = { pos ->
                if (info != null && onDoubleTapLike(info)) hearts.add(Heart(System.nanoTime(), pos, (-12..12).random().toFloat()))
            },
            onHoldStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); ui.holding = true; ui.pulled = false },
            onHoldPull = { ui.pulled = it },
            onHoldEnd = { pulled -> ui.holding = false; ui.pulled = false; if (pulled) ui.locked = !ui.locked },
            // Tahan di tengah = perintah tahan dari server di YouTube (membuka deskripsi), bukan 2x.
            onHoldCenter = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); info?.let(onDescription) },
        ))
        // Gradasi bawah 220 dp.
        Box(Modifier.fillMaxWidth().height(220.dp).align(Alignment.BottomCenter).graphicsLayer { alpha = chrome.value }.background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().graphicsLayer { alpha = chrome.value }.padding(start = 12.dp, end = 78.dp, bottom = 92.dp)) {
            // Chip Sumber Remix / Video Asal (bila Short ini adalah klip / remix dari video panjang)
            if (meta?.sourceVideoId != null) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x66000000))
                        .clickable { onLink("https://www.youtube.com/watch?v=${meta.sourceVideoId}") }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.yt_shorts_remix_play), contentDescription = null, Modifier.size(14.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = meta.sourceVideoTitle ?: "Source video",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                }
            }
            if (info != null) {
                val channelId = remember(info.uploaderUrl) {
                    Regex("/channel/(UC[\\w-]{22})").find(info.uploaderUrl.orEmpty())?.groupValues?.get(1)
                }
                val subState = channelId?.let { Engagement.subscription(it) }
                val isSubscribed = subState?.subscribed == true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val channelAvatar = rememberAvatarUrl(info.uploaderAvatars.lastOrNull()?.url, info.uploaderUrl)
                    val channelLabel = remember(info.uploaderUrl, info.uploaderName) {
                        val handle = Regex("/(@[\\w.-]+)").find(info.uploaderUrl.orEmpty())?.groupValues?.get(1)
                        handle ?: info.uploaderName.orEmpty().let { if (it.isNotBlank() && !it.startsWith("@")) "@$it" else it }
                    }
                    Avatar(channelAvatar, info.uploaderName, 36, Modifier.clickable(enabled = !info.uploaderUrl.isNullOrBlank()) { onChannel(info.uploaderUrl) })
                    Text(
                        channelLabel,
                        Modifier.weight(1f, fill = false).clickable(enabled = !info.uploaderUrl.isNullOrBlank()) { onChannel(info.uploaderUrl) }.padding(start = 8.dp, end = 8.dp),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (channelId != null) {
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSubscribed) Color(0x4DFFFFFF) else Color.White)
                                .clickable { onSubscribe(info, !isSubscribed) }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSubscribed) "Subscribed" else "Subscribe",
                                color = if (isSubscribed) Color.White else Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
                // Ketuk judul membuka deskripsi (`reelTitleOnClickCommand` = showEngagementPanelEndpoint, tag structured-description).
                Text(info.name, Modifier.padding(top = 10.dp).clickable { onDescription(info) }, color = Color.White, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0x33FFFFFF)))
                    Box(Modifier.padding(start = 8.dp).width(110.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)))
                }
                Box(Modifier.padding(top = 14.dp).fillMaxWidth(.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)))
            }
            // Baris Audio / Musik yang dipakai
            if (meta?.audioTitle != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = info != null) { info?.let(onDescription) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.yt_shorts_music), contentDescription = null, Modifier.size(13.dp), tint = Color.White)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = meta.audioTitle,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }
            if (feedMessage != null) Text(feedMessage, Modifier.padding(top = 8.dp).clickable(onClick = onRetryMore), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.align(Alignment.BottomEnd).graphicsLayer { alpha = chrome.value }.padding(end = 8.dp, bottom = 96.dp).width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Kolom aksi Shorts: Like, Comments, Save, Share, Audio track
            val likeState = info?.let { Engagement.rating(videoKey(it.url)) }
            ShortsAction(if (rating == 1) R.drawable.yt_shorts_modern_like_on else R.drawable.yt_shorts_modern_like, if (info != null && info.likeCount >= 0) formatCount((info.likeCount + (likeState?.likeDelta ?: 0)).coerceAtLeast(0)) + if (likeState?.uncertain == true) "?" else "" else "Like", "Like", tint = Color.White) { info?.let { onRate(it, 1) } }
            ShortsAction(R.drawable.yt_shorts_modern_comment, commentCount?.let { formatCount(it.toLong()) } ?: "Comments", "Comments", tint = Color.White) { info?.let(onComments) }
            ShortsAction(R.drawable.yt_w_save, "Save", "Save", tint = Color.White) { info?.let { onSave(videoKey(it.url)) } }
            ShortsAction(R.drawable.yt_shorts_modern_share, "Share", "Share", tint = Color.White) { info?.let(onShare) }
            // Pivot thumbnail audio di pojok kanan bawah aksi
            if (meta?.audioThumbnail != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x33FFFFFF))
                        .clickable { if (info != null) onDescription(info) },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = meta.audioThumbnail,
                        contentDescription = "Audio track",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (showsPlayer) ShortsProgress(player, playing, ui, showPaused, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 80.dp))
        // Keadaan jeda: scrim tipis di seluruh halaman dan ikon putar 96 dp yang muncul/menghilang dengan animasi.
        AnimatedVisibility(showPaused, enter = fadeIn(tween(120)), exit = fadeOut(tween(200)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x33000000)))
        }
        AnimatedVisibility(showPaused, enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = .8f), exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 1.15f), modifier = Modifier.align(Alignment.Center)) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Color(0x66000000)), contentAlignment = Alignment.Center) { PlayerGlyph("pause", Modifier.size(44.dp)) }
        }
        // Saat jeda YouTube menaruh tombol di bawah ikon putar (`reel_player_paused_state_buttons`, margin atas 80 dp); di sini tombol unduh.
        AnimatedVisibility(showPaused && info != null, enter = fadeIn(tween(160)) + scaleIn(tween(160), initialScale = .8f), exit = fadeOut(tween(160)), modifier = Modifier.align(Alignment.Center).offset(y = 96.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0x66000000)).clickable { info?.let(onDownload) }, contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.yt_download), "Download", Modifier.size(26.dp), tint = Color.White)
            }
        }
        if (showsPlayer && (ui.holding || ui.locked)) SpeedChip(
            onClick = { ui.locked = false },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
        )
        if (showsPlayer && ui.holding) SpeedHint(ui.locked, ui.pulled, Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp))
        if (showsPlayer && ui.scrubbing) {
            val duration = player.duration.coerceAtLeast(0L)
            Text(
                "${clock((ui.scrubFraction * duration).toLong())} / ${clock(duration)}", Modifier.align(Alignment.Center),
                style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium, shadow = Shadow(Color(0x99000000), blurRadius = 8f)),
            )
        }
        hearts.forEach { heart -> key(heart.id) { HeartBurst(heart) { hearts.remove(heart) } } }
        if (settled && error != null) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This Short cannot be played. Swipe to continue.", color = Color.White, textAlign = TextAlign.Center)
            TextButton(onClick = onRetryItem) { Text("Try again", color = Color.White) }
        }
        else if (showSpinner) CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = Color.White, strokeWidth = 2.dp)
    }
}

/** Hati putih yang muncul di titik ketuk, membesar sedikit, lalu memudar (YouTube memakai animasi Lottie 240 dp di titik ketuk). */
@Composable
private fun HeartBurst(heart: Heart, onDone: () -> Unit) {
    val scale = remember { Animatable(.3f) }
    val alpha = remember { Animatable(1f) }
    val size = 120.dp
    val sizePx = with(LocalDensity.current) { size.toPx() }
    LaunchedEffect(heart.id) {
        launch { scale.animateTo(1.15f, spring(dampingRatio = .5f, stiffness = 400f)) }
        delay(450)
        alpha.animateTo(0f, tween(260))
        onDone()
    }
    Icon(
        Icons.Default.Favorite, null, tint = Color.White,
        modifier = Modifier
            .offset { IntOffset((heart.pos.x - sizePx / 2f).roundToInt(), (heart.pos.y - sizePx / 2f).roundToInt()) }
            .size(size)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value; rotationZ = heart.tilt },
    )
}

/** Lencana kecepatan di tengah-atas: tinggi 32 dp, padding 12 dp, teks 14 sp, ikon maju-cepat 18 dp. */
@Composable
private fun SpeedChip(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x99000000))
            .clickable(onClickLabel = "Reset playback speed", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("2x", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(4.dp))
        PlayerGlyph("forward", Modifier.size(18.dp))
    }
}

/** Petunjuk saat menahan: kunci atau keluar dari 2x dengan menarik jari ke bawah lalu melepas. */
@Composable
private fun SpeedHint(locked: Boolean, pulled: Boolean, modifier: Modifier = Modifier) {
    val text = when {
        locked && pulled -> "Release to exit 2x"
        locked -> "Pull down to exit 2x"
        pulled -> "Release to lock 2x"
        else -> "Pull down to lock 2x"
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (pulled && !locked) Icon(painterResource(R.drawable.yt_menu_lock), null, Modifier.size(18.dp), tint = Color.White)
        else if (!pulled) Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp), tint = Color.White)
        Text(text, Modifier.padding(start = 4.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Bar progress 2 dp dengan margin horizontal 8 dp. Area sentuh 32 dp di tepi bawah: geser horizontal untuk scrub
 * (pemutar dijeda selama menggeser, seek saat dilepas); bar menebal dan cap waktu tampil di tengah halaman.
 */
@Composable
private fun ShortsProgress(player: ExoPlayer, playing: Boolean, ui: ShortsUiState, paused: Boolean, modifier: Modifier = Modifier) {
    var fraction by remember { mutableFloatStateOf(0f) }
    var seekDuration by remember { mutableLongStateOf(0L) }
    var seekable by remember { mutableStateOf(false) }
    var resume by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        while (true) {
            if (!ui.scrubbing) {
                val duration = player.duration
                seekDuration = duration.coerceAtLeast(0L)
                seekable = player.isCurrentMediaItemSeekable
                fraction = if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            }
            if (playing) withFrameNanos { } else delay(250)
        }
    }
    val finish: (Boolean) -> Unit = { commit ->
        val duration = player.duration
        ui.scrubbing = false
        if (commit && duration > 0) { player.seekTo((ui.scrubFraction * duration).toLong()); fraction = ui.scrubFraction }
        if (resume) player.play()
        resume = false
    }
    val barHeight by animateDpAsState(if (ui.scrubbing) 6.dp else 2.dp, tween(100), label = "shortsBar")
    val seekLabel = stringResource(R.string.accessibility_seek)
    val currentSeconds = ((if (ui.scrubbing) ui.scrubFraction else fraction) * seekDuration / 1000).toLong()
    val seekState = stringResource(R.string.accessibility_seek_position, playerClock(currentSeconds * 1000), playerClock(seekDuration))
    Box(
        modifier.fillMaxWidth().height(32.dp).then(if (seekable && seekDuration > 0L) Modifier.semantics {
            contentDescription = seekLabel
            stateDescription = seekState
            progressBarRangeInfo = ProgressBarRangeInfo(currentSeconds.toFloat().coerceIn(0f, seekDuration / 1000f), 0f..(seekDuration / 1000f))
            setProgress { seconds ->
                val duration = player.duration
                if (!seconds.isFinite() || duration <= 0L || !player.isCurrentMediaItemSeekable) false else {
                    val target = (seconds.coerceIn(0f, duration / 1000f) * 1000).toLong()
                    player.seekTo(target)
                    fraction = target.toFloat() / duration
                    true
                }
            }
        } else Modifier).pointerInput(player) {
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    if (player.duration > 0) {
                        ui.scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        ui.scrubbing = true
                        resume = player.isPlaying
                        player.pause()
                    }
                },
                onHorizontalDrag = { change, _ ->
                    if (ui.scrubbing) { change.consume(); ui.scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
                onDragEnd = { if (ui.scrubbing) finish(true) },
                onDragCancel = { if (ui.scrubbing) finish(false) },
            )
        },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Saat jeda atau scrub bar naik sedikit supaya titik penggeser tidak tertutup bilah navigasi.
        val lift by animateDpAsState(if (paused || ui.scrubbing) 6.dp else 0.dp, tween(120), label = "shortsBarLift")
        // YouTube menyembunyikan bar merah saat video sedang diputar tanpa disentuh; bar (dengan titik) baru tampil saat jeda atau scrub.
        // Area sentuh 32 dp di bawah tetap aktif, jadi menggeser dari tepi bawah tetap bisa men-scrub.
        if (paused || ui.scrubbing) Box(
            Modifier.fillMaxWidth().padding(bottom = lift).padding(horizontal = 8.dp).height(barHeight).clip(RoundedCornerShape(50)).background(Color(0x4DFFFFFF))
                .drawBehind { drawRect(ShortsPlayedRed, size = Size(size.width * (if (ui.scrubbing) ui.scrubFraction else fraction), size.height)) },
        )
        if (paused || ui.scrubbing) Canvas(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 8.dp)) {
            val x = size.width * (if (ui.scrubbing) ui.scrubFraction else fraction)
            drawCircle(ShortsPlayedRed, radius = (if (ui.scrubbing) 7.dp else 5.dp).toPx(), center = Offset(x, size.height - lift.toPx() - barHeight.toPx() / 2f))
        }
    }
}

/** Lembar bawah menu titik tiga (di YouTube: ReelsBottomSheetDialog): sudut atas 16 dp, latar diredupkan, bisa digeser. */
@Composable
private fun ShortsMenuSheet(
    speed: Float,
    canAct: Boolean,
    canHide: Boolean,
    onSpeed: (Float) -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onNotInterested: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by remember { mutableStateOf("menu") }
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color(0xFF272727), contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        if (page == "speed") {
            Row(Modifier.fillMaxWidth().clickable { page = "menu" }.heightIn(min = 52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                Text("Playback speed", Modifier.padding(start = 12.dp), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            listOf(.25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { value ->
                Row(Modifier.fillMaxWidth().clickable { onSpeed(value); onDismiss() }.heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (value == 1f) "Normal" else "${value}x", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    RadioButton(selected = value == speed, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.LightGray), modifier = Modifier.size(40.dp))
                }
            }
        } else {
            ShortsSheetRow("Playback speed", if (speed == 1f) "Normal" else "${speed}x", R.drawable.yt_menu_speed, true) { page = "speed" }
            ShortsSheetRow("Save to playlist", null, R.drawable.yt_w_save, canAct) { onDismiss(); onSave() }
            ShortsSheetRow("Copy link", null, null, canAct) { onDismiss(); onCopy() }
            ShortsSheetRow("Open in YouTube", null, null, canAct) { onDismiss(); onOpen() }
            ShortsSheetRow("Not interested", null, null, canHide) { onDismiss(); onNotInterested() }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ShortsSheetRow(label: String, value: String?, icon: Int?, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 10.dp)
            .graphicsLayer { alpha = if (enabled) 1f else .4f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
            if (icon != null) Icon(painterResource(icon), null, Modifier.size(24.dp), tint = Color.White)
        }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Color.White)
        if (value != null) Text(value, color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
        if (icon != null) Icon(painterResource(R.drawable.yt_chevron_right), null, Modifier.padding(start = 4.dp).size(24.dp), tint = Color.LightGray)
    }
}

@Composable
private fun ShortsAction(icon: Int, label: String, description: String, tint: Color? = Color.White, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), description, Modifier.size(32.dp), tint = tint ?: Color.Unspecified)
        Text(label, Modifier.padding(top = 3.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return if (total >= 3600) String.format(Locale.US, "%d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60) else String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
