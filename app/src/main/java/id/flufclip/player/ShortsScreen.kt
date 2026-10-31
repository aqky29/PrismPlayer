@file:OptIn(ExperimentalFoundationApi::class)

package id.flufclip.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Layar Shorts: pager vertikal yang mengikuti jari. Setiap halaman langsung menampilkan poster dari ID video
 * (tanpa menunggu resolve), jadi geser terasa instan; video baru muncul di atas poster begitu pemutar siap.
 * Satu [ExoPlayer] dipakai bersama dan hanya dipasang ke halaman yang sedang aktif.
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
    onRate: suspend (videoId: String, action: String) -> String?,
    onNeedLogin: () -> Unit,
    onNotInterested: (StreamInfoItem) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var commentsFor by remember { mutableStateOf<StreamInfo?>(null) }
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
    BackHandler(commentsFor != null) { commentsFor = null }
    val activeKey = activeUrl?.let(::videoKey)
    val scope = rememberCoroutineScope()
    var userPaused by remember { mutableStateOf(false) }
    LaunchedEffect(activeKey) { userPaused = false }
    // Penilaian lokal per video: 1 = suka, -1 = tidak suka. Status awal dari akun tidak diketahui.
    val ratings = remember { mutableStateMapOf<String, Int>() }
    val rate: (StreamInfo, Int) -> Unit = { info, target ->
        if (!YouTubeAuth.isLoggedIn) {
            Toast.makeText(context, "Login dulu untuk menilai video", Toast.LENGTH_SHORT).show()
            onNeedLogin()
        } else {
            val key = videoKey(info.url)
            val before = ratings[key] ?: 0
            val after = if (before == target) 0 else target
            ratings[key] = after
            scope.launch {
                val error = onRate(info.id.ifBlank { key }, when (after) { 1 -> "like"; -1 -> "dislike"; else -> "removelike" })
                if (error != null) { ratings[key] = before; Toast.makeText(context, error, Toast.LENGTH_SHORT).show() }
            }
        }
    }
    val pagerState = key(generation) { rememberPagerState(initialPage = startIndex.coerceIn(0, maxOf(0, items.lastIndex))) { items.size } }
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (emptyLoading || emptyMessage == null) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White, strokeWidth = 2.dp)
                    else {
                        Text(emptyMessage, color = Color.White, textAlign = TextAlign.Center)
                        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Coba lagi") }
                    }
                }
            } else key(generation) {
                val settledCallback by rememberUpdatedState(onSettled)
                LaunchedEffect(pagerState) { snapshotFlow { pagerState.settledPage }.collect { settledCallback(it) } }
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = commentsFor == null,
                    flingBehavior = PagerDefaults.flingBehavior(pagerState, pagerSnapDistance = PagerSnapDistance.atMost(1)),
                    key = { "${items.getOrNull(it)?.let { item -> videoKey(item.url) }}#$it" },
                ) { page ->
                    val item = items.getOrNull(page) ?: return@VerticalPager
                    val settled = pagerState.settledPage == page
                    ShortPage(
                        item = item, settled = settled, info = resolver.info(item.url), error = resolver.error(item.url),
                        showsPlayer = settled && activeKey == videoKey(item.url),
                        player = player, playing = playing, buffering = buffering, userPaused = userPaused,
                        rating = ratings[videoKey(item.url)] ?: 0, onRate = rate,
                        feedMessage = feedMessage.takeIf { page == items.lastIndex },
                        onTogglePlay = { if (player.isPlaying) { player.pause(); userPaused = true } else { player.play(); userPaused = false } },
                        onComments = { commentsFor = it }, onChannel = onChannel, onShare = onShare,
                        onRetryItem = { onRetryItem(item.url) }, onRetryMore = onRetryMore,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(130.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))))
            Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Tutup Shorts", tint = Color.White) }
                else Spacer(Modifier.width(8.dp))
                Text("Shorts", Modifier.weight(1f), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Opsi", tint = Color.White) }
                    val menuItem = items.getOrNull(pagerState.settledPage)
                    val menuLink = menuItem?.let { "https://www.youtube.com/shorts/${videoKey(it.url)}" }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Salin tautan") }, enabled = menuLink != null, onClick = {
                            menuOpen = false
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Link video", menuLink))
                            Toast.makeText(context, "Link disalin", Toast.LENGTH_SHORT).show()
                        })
                        DropdownMenuItem(text = { Text("Buka di YouTube") }, enabled = menuLink != null, onClick = {
                            menuOpen = false
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(menuLink))) }
                        })
                        DropdownMenuItem(text = { Text("Tidak tertarik") }, enabled = menuItem != null, onClick = {
                            menuOpen = false
                            menuItem?.let {
                                onNotInterested(it)
                                if (pagerState.settledPage < items.lastIndex) scope.launch { pagerState.animateScrollToPage(pagerState.settledPage + 1) }
                            }
                        })
                    }
                }
            }
            commentsFor?.let { info ->
                val comments = rememberComments(info.url)
                CommentsPanel(comments, info, draggable = true, onDismiss = { commentsFor = null }, onLink = { onLink(info, it) }, onChannel = onChannel, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(.7f))
            }
        }
        bottomBar()
    }
}

@Composable
private fun ShortPage(
    item: StreamInfoItem,
    settled: Boolean,
    info: StreamInfo?,
    error: String?,
    showsPlayer: Boolean,
    player: ExoPlayer,
    playing: Boolean,
    buffering: Boolean,
    userPaused: Boolean,
    rating: Int,
    onRate: (StreamInfo, Int) -> Unit,
    feedMessage: String?,
    onTogglePlay: () -> Unit,
    onComments: (StreamInfo) -> Unit,
    onChannel: (String) -> Unit,
    onShare: (StreamInfo) -> Unit,
    onRetryItem: () -> Unit,
    onRetryMore: () -> Unit,
) {
    val id = videoKey(item.url)
    var posterFailed by remember(id) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = "https://i.ytimg.com/vi/$id/${if (posterFailed) "hqdefault" else "frame0"}.jpg", contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(), onError = { posterFailed = true },
        )
        if (showsPlayer) AndroidView(
            factory = { LayoutInflater.from(it).inflate(R.layout.shorts_player_view, null, false) as PlayerView },
            update = { it.player = player }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().pointerInput(id, showsPlayer) { if (showsPlayer) detectTapGestures { onTogglePlay() } })
        Box(Modifier.fillMaxWidth().height(260.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 78.dp, bottom = 24.dp)) {
            if (info != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(info.uploaderAvatars.lastOrNull()?.url, info.uploaderName, 36, Modifier.clickable(enabled = !info.uploaderUrl.isNullOrBlank()) { onChannel(info.uploaderUrl) })
                    Text(info.uploaderName.orEmpty(), Modifier.weight(1f, fill = false).clickable(enabled = !info.uploaderUrl.isNullOrBlank()) { onChannel(info.uploaderUrl) }.padding(start = 8.dp, end = 8.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(info.name, Modifier.padding(top = 12.dp), color = Color.White, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0x33FFFFFF)))
                    Box(Modifier.padding(start = 8.dp).width(110.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)))
                }
                Box(Modifier.padding(top = 14.dp).fillMaxWidth(.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)))
            }
            if (feedMessage != null) Text(feedMessage, Modifier.padding(top = 8.dp).clickable(onClick = onRetryMore), color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = 26.dp).width(64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortsAction(R.drawable.yt_short_like, if (info != null && info.likeCount >= 0) formatCount(info.likeCount + if (rating == 1) 1 else 0) else "Suka", "Suka", tint = if (rating == 1) LinkBlue else Color.White) { info?.let { onRate(it, 1) } }
            ShortsAction(R.drawable.yt_short_dislike, "Tidak suka", "Tidak suka", tint = if (rating == -1) LinkBlue else Color.White) { info?.let { onRate(it, -1) } }
            ShortsAction(R.drawable.yt_short_comment, "Komentar", "Komentar") { info?.let(onComments) }
            ShortsAction(R.drawable.yt_short_share, "Bagikan", "Bagikan") { info?.let(onShare) }
        }
        if (showsPlayer) {
            var fraction by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(playing) {
                while (true) {
                    val duration = player.duration
                    fraction = if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    if (playing) withFrameNanos { } else delay(250)
                }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(Color(0x4DFFFFFF))
                .drawBehind { drawRect(Color.White, size = Size(size.width * fraction, size.height)) })
        }
        if (settled && error != null) Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Short ini tidak bisa diputar. Geser untuk lanjut.", color = Color.White, textAlign = TextAlign.Center)
            TextButton(onClick = onRetryItem) { Text("Coba lagi", color = Color.White) }
        }
        else if (settled && (info == null || (showsPlayer && buffering))) CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = Color.White, strokeWidth = 2.dp)
        else if (showsPlayer && userPaused && !playing) Icon(painterResource(R.drawable.yt_play), "Putar", Modifier.align(Alignment.Center).size(54.dp).clip(CircleShape).background(Color(0x66000000)).padding(12.dp), tint = Color.White)
    }
}

@Composable
private fun ShortsAction(icon: Int, label: String, description: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(icon), description, Modifier.size(32.dp), tint = tint)
        Text(label, Modifier.padding(top = 3.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
