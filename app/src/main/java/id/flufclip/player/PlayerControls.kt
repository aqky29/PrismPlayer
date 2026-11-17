package id.flufclip.player

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import org.schabi.newpipe.extractor.stream.StreamSegment
import kotlinx.coroutines.Job
import kotlin.math.abs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.schabi.newpipe.extractor.stream.Frameset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PlayedRed = Color(0xFFFF0000) // YouTube Red (#FF0000)

/** Player preferences shared between videos and retained across app restarts. */
internal object PlayerSettings {
    val doubleTapChoices = listOf(5, 10, 15, 20, 30, 60)
    var doubleTapSeconds by mutableIntStateOf(10)
        private set
    var audioPreference by mutableStateOf(AudioPreference.INDONESIAN)
        private set
    var backgroundPlayback by mutableStateOf(true)
        private set
    private var preferences: SharedPreferences? = null
    fun load(context: Context) {
        if (preferences != null) return
        preferences = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE).also {
            doubleTapSeconds = it.getInt("double_tap_seconds", 10)
            audioPreference = AudioPreference.fromKey(it.getString("audio_preference", null))
            backgroundPlayback = it.getBoolean("background_playback", true)
        }
    }
    fun updateDoubleTap(seconds: Int) { doubleTapSeconds = seconds; preferences?.edit()?.putInt("double_tap_seconds", seconds)?.apply() }
    fun updateAudioPreference(preference: AudioPreference) {
        audioPreference = preference
        preferences?.edit()?.putString("audio_preference", preference.key)?.apply()
    }
    fun updateBackgroundPlayback(enabled: Boolean) {
        backgroundPlayback = enabled
        preferences?.edit()?.putBoolean("background_playback", enabled)?.apply()
    }
}

/**
 * Aksi di baris bawah layar penuh (like, dislike, komentar, simpan, bagikan, lainnya), seperti `fullscreen_engagement_*` di YouTube.
 * [rating]: 1 = disukai, -1 = tidak disukai, 0 = belum dinilai.
 */
internal class FullscreenActions(
    val rating: Int, val saved: Boolean,
    val onLike: () -> Unit, val onDislike: () -> Unit, val onComments: () -> Unit, val onSave: () -> Unit, val onShare: () -> Unit,
    val onDownload: () -> Unit, val onCopyLink: () -> Unit, val onOpenInYouTube: () -> Unit,
)

/**
 * YouTube-like controls on top of the inline/fullscreen video: tap toggles, double tap seeks (further taps on the same
 * side keep adding), holding plays at 2x (or rewinds at 2x on the left third), centre previous/play/next, chaptered
 * red time bar whose scrubber can be pulled up for precise seeking.
 */
@Composable internal fun PlayerControlsOverlay(player: ExoPlayer, playing: Boolean, buffering: Boolean, fullscreen: Boolean, title: String, isLive: Boolean, autoplay: Boolean, hasSubtitles: Boolean, subtitlesEnabled: Boolean, previewFrames: List<Frameset>, segments: List<StreamSegment>,
    onBack: () -> Unit, onSettings: () -> Unit, onAutoplay: () -> Unit, onSubtitles: () -> Unit, onFullscreen: () -> Unit, onPrevious: (() -> Unit)?, onNext: (() -> Unit)?,
    hasChat: Boolean = isLive, showChatOverlay: Boolean = false, onToggleChatOverlay: (() -> Unit)? = null,
    /** Nama channel di bawah judul (layar penuh) dan daftar "More videos" di kanan bawah, seperti player layar penuh YouTube. */
    channelName: String? = null, moreVideos: List<StreamInfoItem> = emptyList(), onMoreVideo: ((StreamInfoItem) -> Unit)? = null,
    onCast: (() -> Unit)? = null, actions: FullscreenActions? = null,
    /** Opens the chapters list, like tapping the chapter title in YouTube's player. */
    onChapters: (() -> Unit)? = null, onRetryPlayback: (() -> Unit)? = null,
    modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showMore by remember { mutableStateOf(false) }
    val hasMore = fullscreen && moreVideos.isNotEmpty() && onMoreVideo != null
    // Layar penuh YouTube menaruh satu baris di bawah time bar (aksi di kiri, "More videos" di kanan, tinggi ±40 dp).
    // Time bar dan waktu naik setinggi [dock] supaya tidak bertumpuk dengan baris itu.
    val hasActions = fullscreen && actions != null
    val dock = if (hasActions || hasMore) 31.dp else 0.dp
    var showOverflow by remember { mutableStateOf(false) }
    BackHandler(showMore) { showMore = false }
    LaunchedEffect(fullscreen) { if (!fullscreen) showMore = false }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var windowDuration by remember { mutableLongStateOf(0L) }
    var liveOffset by remember { mutableLongStateOf(C.TIME_UNSET) }
    // "Tertinggal dari live": selisih ke ujung live (player.currentLiveOffset) lebih dari offset target pemutar + 5 seconds (ambang pilihan sendiri; YouTube tidak menyebutnya).
    var behindLive by remember { mutableStateOf(false) }
    val liveWindow = remember { Timeline.Window() }
    var liveSeekStart by remember { mutableLongStateOf(0L) }
    var liveSeekEnd by remember { mutableLongStateOf(0L) }
    var ended by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var fineScrub by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var scrubStart by remember { mutableLongStateOf(0L) }
    var scrubEnd by remember { mutableLongStateOf(0L) }
    var seekSide by remember { mutableIntStateOf(0) }
    var seekSeconds by remember { mutableIntStateOf(0) }
    var seekSequence by remember { mutableIntStateOf(0) }
    var seekTap by remember { mutableStateOf(Offset.Zero) }
    // 1 = holding for 2x forward, -1 = holding for 2x rewind.
    var speedHold by remember { mutableIntStateOf(0) }
    var normalSpeed by remember { mutableFloatStateOf(1f) }
    var resumeAfterRewind by remember { mutableStateOf(false) }
    var rewindJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(player) {
        onDispose {
            rewindJob?.cancel()
            rewindJob = null
            if (speedHold == 1) {
                player.setPlaybackSpeed(normalSpeed)
            } else if (speedHold == -1 && resumeAfterRewind) {
                player.play()
            }
            speedHold = 0
        }
    }
    // Live hanya boleh di-seek bila window DVR benar-benar tersedia. player.duration pada HLS live
    // kadang hanya durasi segmen (mis. 3000 ms) sementara currentLiveOffset lebih besar; timeline window dipakai lebih dulu.
    val liveBase = if (isLive) windowDuration.takeIf { it > 0L } ?: duration else duration
    // Live offset is latency, not DVR length; it may be unset or exceed a short window.
    val liveDvr = isLive && player.isCurrentMediaItemSeekable && liveSeekEnd - liveSeekStart >= 1_000L
    val canSeek = if (isLive) liveDvr else player.isCurrentMediaItemSeekable
    LaunchedEffect(player, isLive) {
        var ticks = 0
        var durationMediaId: String? = null
        var previousDuration = 0L
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            buffered = player.bufferedPosition.coerceAtLeast(0L)
            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId != durationMediaId) { durationMediaId = mediaId; previousDuration = 0L }
            val measuredDuration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            if (measuredDuration != null) previousDuration = measuredDuration
            duration = measuredDuration ?: if (!isLive && mediaId != null && player.playbackState == Player.STATE_BUFFERING) previousDuration else 0L
            liveOffset = player.currentLiveOffset
            ended = player.playbackState == Player.STATE_ENDED
            val timeline = player.currentTimeline
            val index = player.currentMediaItemIndex
            val hasWindow = !timeline.isEmpty && index in 0 until timeline.windowCount
            val liveConfig = if (hasWindow) timeline.getWindow(index, liveWindow).liveConfiguration else null
            val winMs = if (hasWindow) liveWindow.durationMs.takeIf { it > 0L && it != C.TIME_UNSET } ?: 0L else 0L
            windowDuration = winMs
            val base = if (isLive) winMs.takeIf { it > 0L } ?: duration else duration
            val target = liveConfig?.targetOffsetMs ?: C.TIME_UNSET
            val minOffset = liveConfig?.minOffsetMs ?: C.TIME_UNSET
            val maxOffset = liveConfig?.maxOffsetMs ?: C.TIME_UNSET
            behindLive = isLive && player.currentLiveOffset.let { offset ->
                offset != C.TIME_UNSET && offset > (target.takeIf { it != C.TIME_UNSET && it > 0 } ?: 10_000L) + 5_000L
            }
            if (isLive && base > 0L) {
                // Live speed-control offsets are not DVR bounds. Use the timeline's safe LIVE position.
                liveSeekStart = 0L
                liveSeekEnd = if (hasWindow) liveWindow.defaultPositionMs
                    .takeIf { it != C.TIME_UNSET && it > 0L }?.coerceAtMost(base) ?: base else base
            } else {
                liveSeekStart = 0L
                liveSeekEnd = base
            }
            if (isLive && ticks++ % 5 == 0) {
                android.util.Log.d("FlufLive", "pos=$position dur=$duration win=$windowDuration off=$liveOffset seekable=${player.isCurrentMediaItemSeekable} start=$liveSeekStart end=$liveSeekEnd target=$target min=$minOffset max=$maxOffset state=${player.playbackState}")
            }
            delay(200)
        }
    }
    LaunchedEffect(controlsVisible, playing, interaction, scrubbing) {
        if (controlsVisible && playing && !scrubbing) { delay(3000); controlsVisible = false }
    }
    LaunchedEffect(seekSequence) { if (seekSide != 0) { delay(700); seekSide = 0; seekSeconds = 0 } }
    LaunchedEffect(ended) { if (ended) controlsVisible = true }
    fun seek(side: Int, tap: Offset) {
        if (!canSeek) return
        val step = PlayerSettings.doubleTapSeconds
        val lower = if (isLive) liveSeekStart else 0L
        val upper = if (isLive) liveSeekEnd.takeIf { it > 0L } else player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
        val destination = (player.currentPosition + side * step * 1000L).coerceAtLeast(lower).let { if (upper != null && upper > 0) it.coerceAtMost(upper) else it }
        // Di live, maju sampai batas DVR berarti kembali ke posisi live default, bukan menempel di ujung window.
        if (isLive && upper != null && destination >= upper) player.seekToDefaultPosition() else player.seekTo(destination)
        position = destination
        seekSeconds = if (seekSide == side) seekSeconds + step else step
        seekSide = side
        seekTap = tap
        seekSequence++
    }
    fun togglePlay() {
        interaction++
        when {
            ended -> { player.seekTo(0); player.play() }
            playing -> player.pause()
            else -> resumeOrRetryPlayback(player, onRetryPlayback)
        }
    }
    val chapter = segments.lastOrNull { it.startTimeSeconds * 1000L <= (if (scrubbing) scrubPosition else position) }?.title?.takeIf { it.isNotBlank() }
    val scrim by animateFloatAsState(if (controlsVisible && speedHold == 0) 1f else 0f, tween(200), label = "scrim")
    BoxWithConstraints(modifier.fillMaxSize()) {
        val topGradient = minOf(180.dp, maxHeight * .6f)
        val wideScreen = maxWidth >= 599.dp // large_screen_width_for_chapters
        // Time and chapter text: extra_small_font_size (12 sp, 14 sp from sw600dp), system font at normal weight, like the APK.
        val barText = if (androidx.compose.ui.platform.LocalConfiguration.current.smallestScreenWidthDp >= 600) 14.sp else 12.sp
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim }.background(Color.Black.copy(alpha = .3f))) {
            Box(Modifier.fillMaxWidth().height(topGradient).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .55f), Color.Transparent))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(topGradient * .6f).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .45f)))))
        }
        Box(Modifier.fillMaxSize().pointerInput(player) {
            detectTapGestures(
                onTap = { tap ->
                    val side = if (tap.x < size.width / 2f) -1 else 1
                    // Right after a double tap YouTube treats further single taps on the same side as more seeking.
                    if (seekSide == side) seek(side, tap) else { controlsVisible = !controlsVisible; interaction++ }
                },
                onDoubleTap = { tap -> controlsVisible = false; seek(if (tap.x < size.width / 2f) -1 else 1, tap) },
                onLongPress = { press ->
                    if (press.x < size.width / 3f && canSeek && !ended) {
                        resumeAfterRewind = player.playWhenReady; player.pause(); speedHold = -1; controlsVisible = false
                        rewindJob = scope.launch {
                            val lower = if (isLive) liveSeekStart else 0L
                            while (true) {
                                val target = (player.currentPosition - 200L).coerceAtLeast(lower)
                                player.seekTo(target); position = target
                                if (target <= lower) break
                                delay(100)
                            }
                        }
                    } else if (playing && !isLive && !ended) {
                        normalSpeed = player.playbackParameters.speed
                        player.setPlaybackSpeed(2f)
                        speedHold = 1
                        controlsVisible = false
                    }
                },
                onPress = {
                    try {
                        tryAwaitRelease()
                    } finally {
                        when (speedHold) {
                            1 -> player.setPlaybackSpeed(normalSpeed)
                            -1 -> {
                                rewindJob?.cancel()
                                rewindJob = null
                                if (resumeAfterRewind) player.play()
                            }
                        }
                        speedHold = 0
                    }
                }
            )
        })
        if (seekSide != 0) SeekRipple(seekSide, seekSeconds, seekTap, seekSequence)
        if (speedHold != 0) Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (speedHold < 0) { PlayerGlyph("forward", Modifier.size(14.dp).graphicsLayer { scaleX = -1f }); Spacer(Modifier.width(4.dp)) }
            Text("2x", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (speedHold > 0) { Spacer(Modifier.width(4.dp)); PlayerGlyph("forward", Modifier.size(14.dp)) }
        }
        // TimeBar ada di bawah lapisan kontrol: area sentuhnya menumpuk dengan tombol (mis. layar penuh), jadi tombol harus menang.
        // Di layar penuh YouTube bar progress ikut hilang saat kontrol disembunyikan (video "fokus"); di pemutar inline garis tipisnya tetap.
        // Use window-relative positions for both drawing and seeking; the right edge is the safe LIVE position.
        val barStart = if (scrubbing) scrubStart else if (isLive) liveSeekStart else 0L
        val barEnd = if (scrubbing) scrubEnd else if (isLive) liveSeekEnd.takeIf { it > 0L } ?: liveBase else duration
        val barDuration = (barEnd - barStart).coerceAtLeast(if (isLive) 1L else 0L)
        val staticLiveBar = isLive && (!canSeek || liveBase <= 0L)
        val absolutePosition = when {
            !isLive -> if (scrubbing) scrubPosition else position
            scrubbing -> scrubPosition
            !behindLive && liveOffset != C.TIME_UNSET -> barEnd
            liveBase > 0L -> position.coerceIn(barStart, barEnd)
            else -> barEnd
        }
        val barPosition = if (staticLiveBar && !scrubbing) barDuration else (absolutePosition - barStart).coerceIn(0L, barDuration)
        val barBuffered = if (staticLiveBar) barDuration else (buffered - barStart).coerceIn(0L, barDuration)
        AnimatedVisibility(speedHold == 0 && (!fullscreen || controlsVisible || scrubbing), enter = fadeIn(tween(150)), exit = fadeOut(tween(200)), modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            TimeBar(position = barPosition, buffered = barBuffered, duration = barDuration, segments = segments, expanded = controlsVisible, fullscreen = fullscreen,
                enabled = if (isLive) liveBase > 0L && canSeek else duration > 0 && canSeek,
                onScrub = { value, fine ->
                    if (!scrubbing) { scrubStart = barStart; scrubEnd = barEnd }
                    scrubbing = true; fineScrub = fine
                    scrubPosition = (scrubStart + value).coerceIn(scrubStart, scrubEnd)
                    controlsVisible = true; interaction++
                },
                onScrubEnd = {
                    val target = if (isLive) scrubPosition.coerceIn(liveSeekStart, liveSeekEnd.coerceAtLeast(liveSeekStart)) else scrubPosition
                    if (isLive && scrubPosition >= scrubEnd) player.seekToDefaultPosition() else player.seekTo(target)
                    position = target; scrubbing = false; fineScrub = false; interaction++
                },
                onScrubCancel = { scrubbing = false; fineScrub = false; interaction++ },
                onAccessibleSeek = { value ->
                    val start = if (isLive) liveSeekStart else 0L
                    val end = if (isLive) liveSeekEnd.coerceAtLeast(start) else duration
                    val target = (start + value).coerceIn(start, end)
                    if (isLive && target >= end) player.seekToDefaultPosition() else player.seekTo(target)
                    position = target; controlsVisible = true; interaction++
                },
                onTap = { if (!controlsVisible) { controlsVisible = true; interaction++ } else scope.launch { val target = (barStart + it).coerceIn(barStart, barEnd); player.seekTo(target); position = target; interaction++ } },
                modifier = Modifier.padding(bottom = dock))
        }
        AnimatedVisibility(controlsVisible && speedHold == 0, enter = fadeIn(tween(150)), exit = fadeOut(tween(200)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                if (!scrubbing) Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 2.dp, vertical = if (fullscreen) 12.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    ControlButton("Minimalkan video", { interaction++; onBack() }) { Image(painterResource(R.drawable.yt_chevron_down), null, modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White)) }
                    if (fullscreen) Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!channelName.isNullOrBlank()) Text(channelName, color = Color.White.copy(alpha = .7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    else Spacer(Modifier.weight(1f))
                    if (fullscreen && hasChat && onToggleChatOverlay != null) {
                        ControlButton(if (showChatOverlay) "Hide chat" else "Show chat", { interaction++; onToggleChatOverlay() }) {
                            Image(painterResource(if (showChatOverlay) R.drawable.yt_chat_on else R.drawable.yt_chat_off), contentDescription = "Chat", modifier = Modifier.size(24.dp))
                        }
                    }
                    ControlButton(if (autoplay) "Turn off autoplay" else "Turn on autoplay", { interaction++; onAutoplay() }, width = 56.dp) {
                        Box(Modifier.size(width = 42.dp, height = 24.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .28f)), contentAlignment = if (autoplay) Alignment.CenterEnd else Alignment.CenterStart) {
                            Box(Modifier.padding(2.dp).size(20.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                Image(painterResource(if (autoplay) R.drawable.yt_autoplay_play else R.drawable.yt_autoplay_pause), null, modifier = Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color.Black))
                            }
                        }
                    }
                    if (onCast != null) ControlButton("Cast", { interaction++; onCast() }, width = 48.dp) { Image(painterResource(R.drawable.yt_cast), null, modifier = Modifier.size(27.dp)) }
                    ControlButton(if (subtitlesEnabled) "Captions on" else "Subtitle", { interaction++; if (hasSubtitles) onSubtitles() }, width = 48.dp) {
                        Image(painterResource(if (subtitlesEnabled) R.drawable.yt_cc_active else R.drawable.yt_cc), contentDescription = null, modifier = Modifier.size(27.dp).graphicsLayer { alpha = if (hasSubtitles) 1f else .4f })
                    }
                    ControlButton("Video settings", { interaction++; onSettings() }, width = 48.dp) { Image(painterResource(R.drawable.yt_settings), null, modifier = Modifier.size(27.dp)) }
                }
                // YouTube: 56 dp play/pause in an 80 dp touch area, 40 dp previous/next in 48 dp, 24 dp apart (40 dp fullscreen).
                if (!scrubbing) Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 40.dp else 24.dp)) {
                    val canRestart = position > 3_000L && !isLive
                    CircleButton("Previous video", 40, 48, enabled = canRestart || onPrevious != null, onClick = { interaction++; if (canRestart) player.seekTo(0) else onPrevious?.invoke() }) { Image(painterResource(R.drawable.yt_previous), null, Modifier.size(24.dp)) }
                    if (buffering && !ended) Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(44.dp), color = Color.White, strokeWidth = 3.dp) }
                    else CircleButton(if (player.playerError != null) "Try again" else if (ended) "Replay" else if (playing) "Pause" else "Play", 56, 80, onClick = { togglePlay() }) {
                        Image(painterResource(if (ended || player.playerError != null) R.drawable.yt_replay else if (playing) R.drawable.yt_pause else R.drawable.yt_play), null, Modifier.size(36.dp))
                    }
                    CircleButton("Next video", 40, 48, enabled = onNext != null, onClick = { interaction++; onNext?.invoke() }) { Image(painterResource(R.drawable.yt_next), null, Modifier.size(24.dp)) }
                }
                if (hasMore && !scrubbing && onMoreVideo != null) MoreVideosChip(moreVideos.first(), Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp)) { interaction++; showMore = true }
                if (hasActions && !scrubbing && actions != null) FullscreenActionRow(actions, showOverflow, { showOverflow = it }, { interaction++ }, Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp))
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = if (fullscreen) 16.dp else 10.dp, end = if (fullscreen) 4.dp else 6.dp, bottom = (if (fullscreen) 40.dp else 7.dp) + dock), verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        if (!scrubbing) {
                            // Label live dari APK (time_bar_live_label): teks "LIVE" berat normal, titik 6 dp di kiri dengan jarak 8 dp, latar pil yang sama dengan waktu.
                            // Titik merah #FF0033 (player_live_dot) saat di ujung live, putih 70% (player_notlive_dot) saat tertinggal; hanya saat tertinggal label bisa diketuk
                            // untuk lompat ke live. Saat di ujung live teks posisi/durasi disembunyikan, sama seperti kelas pengatur label di APK (off.h/off.G).
                            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .30f)).clickable(enabled = behindLive && canSeek) { player.seekToDefaultPosition(); interaction++ }
                                .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(if (behindLive) Color.White.copy(alpha = .7f) else Color(0xFFFF0033)))
                                Spacer(Modifier.width(8.dp))
                                Text("LIVE", color = Color.White, fontSize = barText)
                            }
                        }
                    } else {
                        // Time pill from the APK (time_bar_current_time / separator / total in one YouTubeModernPlayerButton):
                        // black 30%, padding 8 dp x 4 dp; position and duration white, the " / " separator white 70%.
                        Row(Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .30f)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(playerClock(if (scrubbing) scrubPosition else position), color = Color.White, fontSize = barText, maxLines = 1)
                            Text(" / ", color = Color.White.copy(alpha = .70f), fontSize = barText, maxLines = 1)
                            Text(if (duration > 0) playerClock(duration) else "--:--", color = Color.White, fontSize = barText, maxLines = 1)
                        }
                    }
                    // One weighted slot only: two weights (chapter with fill = false + spacer) left unused space after
                    // the fullscreen button, so it drifted away from the right corner whenever a chapter title showed.
                    // Chapter pill from the APK's time_bar_chapter_title (YouTubeModernPlayerButton): black 30% fill, fully
                    // rounded, min height 24 dp, padding 8 dp start / 2 dp end / 4 dp vertical, 12 sp text, 8 dp before the chevron.
                    Box(Modifier.weight(1f).padding(start = 8.dp)) {
                        if (chapter != null && !scrubbing) Row(
                            Modifier.widthIn(max = if (wideScreen) 240.dp else Dp.Unspecified).heightIn(min = 24.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .30f))
                                .clickable(enabled = onChapters != null) { interaction++; onChapters?.invoke() }.padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
                                .semantics { contentDescription = "Chapter: $chapter. Tap to see chapters" },
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(chapter, color = Color.White, fontSize = barText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Text("›", color = Color.White, fontSize = barText * 1.2f, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
                        }
                    }
                    // Panah diagonal ↖↘ (masuk) dan ↘↖ (keluar) dari APK (player_full_enter_alt / exit_alt), di lingkaran gelap tembus pandang 36 dp.
                    ControlButton(if (fullscreen) "Exit full screen" else "Full screen", { interaction++; onFullscreen() }, dark = true, circle = if (fullscreen) 40.dp else 36.dp) { Image(painterResource(if (fullscreen) R.drawable.yt_fullscreen_exit else R.drawable.yt_fullscreen), null, Modifier.size(24.dp)) }
                }
            }
        }
        if (scrubbing) ScrubPreview(previewFrames, scrubPosition, duration, chapter, fineScrub, maxWidth, maxHeight, fullscreen, dock,
            rangeStart = scrubStart, rangeEnd = scrubEnd, liveEdge = if (isLive) liveBase else null)
        if (showMore && onMoreVideo != null) {
            // Scrim: ketuk di luar daftar menutupnya. Daftar kartu video terkait menempel di bawah.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .5f)).pointerInput(Unit) { detectTapGestures { showMore = false } })
            MoreVideosPanel(moreVideos, maxHeight, Modifier.align(Alignment.BottomCenter), onClose = { showMore = false }) { item -> showMore = false; onMoreVideo(item) }
        }
    }
}

/** Kartu di kanan bawah layar penuh: pil gelap berisi teks "More videos" dan thumbnail berbingkai putih. */
@Composable private fun MoreVideosChip(first: StreamInfoItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .4f)).clickable(onClick = onClick).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("More videos", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        AsyncImage(first.thumbnails.lastOrNull()?.url, null, contentScale = ContentScale.Crop, modifier = Modifier.padding(start = 10.dp).size(width = 58.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)).border(1.5.dp, Color.White, RoundedCornerShape(6.dp)).background(Color(0xFF202020)))
    }
}

@Composable private fun MoreVideosPanel(items: List<StreamInfoItem>, maxHeight: Dp, modifier: Modifier = Modifier, onClose: () -> Unit, onPick: (StreamInfoItem) -> Unit) {
    val cardWidth = minOf(220.dp, maxHeight * .42f * 16f / 9f)
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f)))).padding(top = 24.dp, bottom = 20.dp)) {
        Text("More videos", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.url }) { item ->
                Column(Modifier.width(cardWidth).clip(RoundedCornerShape(8.dp)).clickable { onPick(item) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF202020))) {
                        AsyncImage(item.thumbnails.lastOrNull()?.url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (item.duration > 0) Text(playerClock(item.duration * 1000L), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = .7f)).padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                    Text(item.name, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(item.uploaderName.orEmpty(), color = Color.White.copy(alpha = .7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Selects a compact storyboard frameset suitable for scrub previews. */
internal fun selectScrubFrameset(framesets: List<Frameset>): Frameset? =
    framesets.filter { it.frameWidth <= 320 }.maxByOrNull { it.frameWidth } ?: framesets.minByOrNull { it.frameWidth }

/** Loads storyboard sprite sheets once and keeps them for the frames drawn while scrubbing. */
@Composable internal fun rememberStoryboardSheets(urls: List<String>): Map<String, ImageBitmap> {
    val context = LocalContext.current
    val sheets = remember { mutableStateMapOf<String, ImageBitmap>() }
    LaunchedEffect(urls) {
        urls.filter { it !in sheets }.forEach { url ->
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
            val bitmap = (result as? SuccessResult)?.drawable?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
            if (bitmap != null) sheets[url] = bitmap.asImageBitmap()
        }
    }
    return sheets
}

@Composable internal fun StoryboardFrame(frameset: Frameset, position: Long, sheets: Map<String, ImageBitmap>, modifier: Modifier) {
    val bounds = remember(frameset, position) { runCatching { frameset.getFrameBoundsAt(position) }.getOrNull() }
    val image = bounds?.let { frameset.urls.getOrNull(it[0]) }?.let { sheets[it] }
    Box(modifier.background(Color(0xFF202020))) {
        if (image != null && bounds != null) Canvas(Modifier.fillMaxSize()) {
            drawImage(image, srcOffset = IntOffset(bounds[1], bounds[2]), srcSize = IntSize(bounds[3] - bounds[1], bounds[4] - bounds[2]),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()))
        }
    }
}

/**
 * Storyboard frame (when YouTube provides one), chapter and time above the scrubber, following the finger like YouTube.
 * While the finger is pulled up (precise seeking) a film strip of neighbouring frames replaces the single preview.
 */
@Composable private fun BoxScope.ScrubPreview(framesets: List<Frameset>, position: Long, duration: Long, chapter: String?, fine: Boolean, maxWidth: Dp, maxHeight: Dp, fullscreen: Boolean, lift: Dp = 0.dp,
    rangeStart: Long = 0L, rangeEnd: Long = duration, liveEdge: Long? = null) {
    val frameset = remember(framesets) { selectScrubFrameset(framesets) }
    val step = frameset?.durationPerFrame?.toLong()?.takeIf { it > 0 } ?: 2_000L
    val stripCount = if (maxWidth >= 600.dp) 7 else 5
    val stripTimes = if (fine) (-(stripCount / 2)..(stripCount / 2)).map { (position + it * step).coerceIn(0L, duration.coerceAtLeast(0L)) } else listOf(position)
    val urls = remember(frameset, stripTimes) {
        frameset?.let { set -> stripTimes.mapNotNull { t -> runCatching { set.getFrameBoundsAt(t) }.getOrNull()?.let { set.urls.getOrNull(it[0]) } }.distinct() }.orEmpty()
    }
    val sheets = rememberStoryboardSheets(urls)
    val aspect = if (frameset != null && frameset.frameWidth > 0) frameset.frameHeight.toFloat() / frameset.frameWidth else 9f / 16f
    val bottom = (if (fullscreen) 64.dp else 34.dp) + lift
    val label: @Composable (Boolean) -> Unit = { big ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            if (chapter != null) Text(chapter, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (liveEdge != null) "-${playerClock((liveEdge - position).coerceAtLeast(0L))}" else playerClock(position), color = Color.White, fontSize = if (big && liveEdge == null) 22.sp else 14.sp, fontWeight = FontWeight.Medium)
        }
    }
    if (!fine && maxHeight >= 150.dp) Text("Pull up for precise seeking", color = Color.White, fontSize = 12.sp,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 12.dp, vertical = 4.dp))
    if (fine && frameset != null) {
        val itemHeight = minOf(if (fullscreen) 72.dp else 56.dp, maxHeight * .35f)
        val itemWidth = itemHeight / aspect
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = bottom), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                stripTimes.forEachIndexed { index, time ->
                    val center = index == stripTimes.size / 2
                    StoryboardFrame(frameset, time, sheets, Modifier.size(itemWidth, itemHeight).clip(RoundedCornerShape(4.dp))
                        .then(if (center) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier.graphicsLayer { alpha = .7f }))
                }
            }
            label(false)
        }
        return
    }
    val previewWidth = if (fullscreen) 200.dp else minOf(150.dp, maxWidth * .4f)
    val previewHeight = previewWidth * aspect
    val fraction = if (rangeEnd > rangeStart) ((position - rangeStart).toFloat() / (rangeEnd - rangeStart)).coerceIn(0f, 1f) else 0f
    val x = (maxWidth * fraction - previewWidth / 2).coerceIn(8.dp, (maxWidth - previewWidth - 8.dp).coerceAtLeast(8.dp))
    val showImage = frameset != null && (previewHeight + 28.dp + bottom) < maxHeight
    Column(Modifier.align(Alignment.BottomStart).offset(x = x).padding(bottom = bottom).width(previewWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        if (showImage && frameset != null) StoryboardFrame(frameset, position, sheets, Modifier.size(previewWidth, previewHeight).clip(RoundedCornerShape(8.dp)).border(1.5.dp, Color.White, RoundedCornerShape(8.dp)))
        label(!showImage)
    }
}

/**
 * Red time bar. Scrubbing starts after a horizontal slop; pulling the finger 40 dp above the bar switches to precise
 * seeking (one fifth of the movement). Chapters are separated by small gaps like YouTube.
 */
@Composable private fun TimeBar(position: Long, buffered: Long, duration: Long, segments: List<StreamSegment>, expanded: Boolean, fullscreen: Boolean, enabled: Boolean,
    onScrub: (Long, Boolean) -> Unit, onScrubEnd: () -> Unit, onScrubCancel: () -> Unit, onTap: (Long) -> Unit, onAccessibleSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val seekLabel = stringResource(R.string.accessibility_seek)
    val seekState = stringResource(R.string.accessibility_seek_position, playerClock(position), playerClock(duration))
    val rewindLabel = stringResource(R.string.accessibility_rewind, PlayerSettings.doubleTapSeconds)
    val forwardLabel = stringResource(R.string.accessibility_forward, PlayerSettings.doubleTapSeconds)
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    val dot by animateDpAsState(if (dragging) 20.dp else if (expanded) 12.dp else 0.dp, tween(150), label = "scrubber")
    val thickness = if (dragging) 4.dp else if (expanded) 3.dp else 2.dp
    val sideDp by animateDpAsState(if (fullscreen) 16.dp else if (expanded || dragging) 12.dp else 0.dp, tween(150), label = "timeBarSide")
    val side = with(LocalDensity.current) { sideDp.toPx() }
    val currentScrub by rememberUpdatedState(onScrub)
    val currentEnd by rememberUpdatedState(onScrubEnd)
    val currentCancel by rememberUpdatedState(onScrubCancel)
    val currentDuration by rememberUpdatedState(duration)
    val currentSide by rememberUpdatedState(side)
    val currentWidth by rememberUpdatedState(width)
    val currentTap by rememberUpdatedState(onTap)
    val chapterStarts = remember(segments, duration) { if (duration <= 0) emptyList() else segments.map { it.startTimeSeconds * 1000f / duration }.filter { it > 0f && it < 1f } }
    Box(modifier.fillMaxWidth().height(if (fullscreen) 56.dp else if (expanded || dragging) 28.dp else 16.dp).padding(bottom = if (fullscreen) 24.dp else 0.dp).onSizeChanged { width = it.width }
        .then(if (enabled && duration > 0L) Modifier.semantics {
            contentDescription = seekLabel
            stateDescription = seekState
            progressBarRangeInfo = ProgressBarRangeInfo((position / 1000L).toFloat().coerceIn(0f, duration / 1000f), 0f..(duration / 1000f))
            customActions = listOf(
                CustomAccessibilityAction(rewindLabel) { onAccessibleSeek((position - PlayerSettings.doubleTapSeconds * 1000L).coerceAtLeast(0L)); true },
                CustomAccessibilityAction(forwardLabel) { onAccessibleSeek((position + PlayerSettings.doubleTapSeconds * 1000L).coerceAtMost(duration)); true },
            )
            setProgress { seconds ->
                if (!seconds.isFinite()) false else {
                    onAccessibleSeek((seconds.coerceIn(0f, duration / 1000f) * 1000).toLong())
                    true
                }
            }
        } else Modifier)
        // A live manifest refresh must not cancel the finger gesture.
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                val gestureDuration = currentDuration
                val gestureSide = currentSide
                val gestureSpan = (currentWidth - 2 * gestureSide).coerceAtLeast(1f)
                fun at(x: Float): Long = if (gestureDuration <= 0L) 0L else
                    (((x - gestureSide) / gestureSpan).coerceIn(0f, 1f) * gestureDuration).toLong()
                val slop = viewConfiguration.touchSlop
                val pull = 40.dp.toPx()
                var scrubbing = false
                var moved = false
                var released = false
                var value = at(down.position.x)
                var lastX = down.position.x
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { released = true; if (scrubbing) change.consume(); break }
                        if (change.isConsumed) break
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!scrubbing) {
                            if (abs(dx) > slop || abs(dy) > slop) moved = true
                            if (abs(dx) > slop && abs(dx) >= abs(dy)) { scrubbing = true; dragging = true; value = at(change.position.x); lastX = change.position.x; change.consume(); currentScrub(value, false) }
                            else if (moved) break
                        } else {
                            change.consume()
                            val fine = down.position.y - change.position.y > pull
                            value = if (fine) (value + ((change.position.x - lastX) / gestureSpan * gestureDuration * .2f).toLong()).coerceIn(0L, gestureDuration) else at(change.position.x)
                            lastX = change.position.x
                            currentScrub(value, fine)
                        }
                    }
                } finally {
                    dragging = false
                    if (scrubbing && !released) currentCancel()
                }
                if (scrubbing && released) currentEnd() else if (released && !moved) currentTap(at(down.position.x))
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            // Inline: bar selebar video di tepi bawah (titik merah menumpang di atasnya); layar penuh: bar bersisi 16 dp di tengah area sentuh.
            val y = if (fullscreen) size.height / 2f else if (expanded || dragging) size.height - maxOf(4.5.dp.toPx(), dot.toPx() / 2 + 1.dp.toPx()) else size.height - thickness.toPx() / 2f
            val left = side; val right = size.width - side; val span = right - left
            fun x(value: Long) = left + if (duration > 0) span * (value.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val h = thickness.toPx()
            val gap = (if (dragging) 3.dp else 2.dp).toPx()
            val corner = CornerRadius(h / 2f, h / 2f)
            val edges = listOf(left) + chapterStarts.map { left + span * it } + right
            fun piece(color: Color, until: Float) {
                for (i in 0 until edges.size - 1) {
                    val a = edges[i] + if (i > 0) gap / 2 else 0f
                    val b = minOf(edges[i + 1] - if (i < edges.size - 2) gap / 2 else 0f, until)
                    if (b > a) drawRoundRect(color, Offset(a, y - h / 2), Size(b - a, h), corner)
                }
            }
            piece(Color.White.copy(alpha = .20f), right)
            piece(Color.White.copy(alpha = .70f), x(buffered))
            piece(PlayedRed, x(position))
            if (dot > 0.dp) {
                val dotX = x(position).coerceIn(dot.toPx() / 2, (size.width - dot.toPx() / 2).coerceAtLeast(dot.toPx() / 2))
                drawCircle(Color.Black.copy(alpha = .35f), radius = dot.toPx() / 2 + 1.dp.toPx(), center = Offset(dotX, y))
                drawCircle(PlayedRed, radius = dot.toPx() / 2, center = Offset(dotX, y))
            }
        }
    }
}

/** Side area bulging toward the centre, the shape YouTube clips its double-tap feedback to. */
private fun seekArcShape(side: Int) = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    if (side < 0) { moveTo(0f, 0f); lineTo(w * .7f, 0f); quadraticTo(w * 1.3f, h / 2f, w * .7f, h); lineTo(0f, h); close() }
    else { moveTo(w, 0f); lineTo(w * .3f, 0f); quadraticTo(-w * .3f, h / 2f, w * .3f, h); lineTo(w, h); close() }
}

/** Double-tap feedback: tinted arc on that side with a circle growing from the tap point (`CircularClipTapBloomView`). */
@Composable private fun BoxScope.SeekRipple(side: Int, seconds: Int, tap: Offset, sequence: Int) {
    val bloom = remember { Animatable(0f) }
    LaunchedEffect(sequence) { bloom.snapTo(0f); bloom.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullWidth = constraints.maxWidth.toFloat()
        Box(Modifier.align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.4f).clip(seekArcShape(side)).background(Color.White.copy(alpha = .1f))
            .drawBehind {
                val local = Offset(if (side < 0) tap.x else tap.x - (fullWidth - size.width), tap.y)
                drawCircle(Color.White.copy(alpha = .18f * (1f - bloom.value * .5f)), radius = bloom.value * maxOf(size.width, size.height), center = local)
            }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.graphicsLayer { scaleX = if (side < 0) -1f else 1f }) { repeat(3) { PlayerGlyph("play", Modifier.size(12.dp)) } }
                Text("$seconds seconds", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable private fun ControlButton(label: String, onClick: () -> Unit, dark: Boolean = false, width: Dp = 44.dp, circle: Dp = 40.dp, content: @Composable () -> Unit) {
    Box(Modifier.size(width = width, height = 44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (dark) Box(Modifier.size(circle).clip(CircleShape).background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) { content() } else content()
    }
}

/** Baris aksi di bawah layar penuh: pil gelap berisi suka, tidak suka, komentar, simpan, bagikan, dan menu lainnya. */
@Composable private fun FullscreenActionRow(actions: FullscreenActions, overflowOpen: Boolean, onOverflow: (Boolean) -> Unit, onInteract: () -> Unit, modifier: Modifier = Modifier) {
    @Composable fun Slot(label: String, icon: Int, onClick: () -> Unit) {
        Box(Modifier.size(width = 46.dp, height = 40.dp).clip(CircleShape).clickable { onInteract(); onClick() }.semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
            Image(painterResource(icon), null, Modifier.size(24.dp))
        }
    }
    Row(modifier.height(40.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .4f)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Slot(if (actions.rating == 1) "Remove like" else "Like", if (actions.rating == 1) R.drawable.yt_fs_like_on else R.drawable.yt_fs_like, actions.onLike)
        Slot(if (actions.rating == -1) "Remove dislike" else "Dislike", if (actions.rating == -1) R.drawable.yt_fs_dislike_on else R.drawable.yt_fs_dislike, actions.onDislike)
        Slot("Comments", R.drawable.yt_fs_comment, actions.onComments)
        Slot("Save", if (actions.saved) R.drawable.yt_fs_save_on else R.drawable.yt_fs_save, actions.onSave)
        Slot("Share", R.drawable.yt_fs_share, actions.onShare)
        Box {
            Slot("More", R.drawable.yt_fs_more) { onOverflow(true) }
            DropdownMenu(expanded = overflowOpen, onDismissRequest = { onOverflow(false) }) {
                DropdownMenuItem(text = { Text("Download") }, onClick = { onOverflow(false); actions.onDownload() })
                DropdownMenuItem(text = { Text("Copy link") }, onClick = { onOverflow(false); actions.onCopyLink() })
                DropdownMenuItem(text = { Text("Open in YouTube") }, onClick = { onOverflow(false); actions.onOpenInYouTube() })
            }
        }
    }
}

@Composable private fun CircleButton(label: String, size: Int, touch: Int, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(touch.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(size.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f)).graphicsLayer { alpha = if (enabled) 1f else .4f }, contentAlignment = Alignment.Center) { content() }
    }
}

/** White player icons drawn in code (the project only ships the core Material icon set). */
@Composable internal fun PlayerGlyph(icon: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val white = Color.White
        fun triangle(x0: Float, x1: Float) = Path().apply { moveTo(w * x0, h * .1f); lineTo(w * x1, h * .5f); lineTo(w * x0, h * .9f); close() }
        when (icon) {
            "play" -> drawPath(triangle(.18f, .92f), white)
            "pause" -> { drawRoundRect(white, Offset(w * .2f, h * .12f), Size(w * .2f, h * .76f), CornerRadius(w * .04f)); drawRoundRect(white, Offset(w * .6f, h * .12f), Size(w * .2f, h * .76f), CornerRadius(w * .04f)) }
            "next" -> { drawPath(triangle(.12f, .74f), white); drawRect(white, Offset(w * .76f, h * .1f), Size(w * .12f, h * .8f)) }
            "previous" -> { drawRect(white, Offset(w * .12f, h * .1f), Size(w * .12f, h * .8f)); drawPath(Path().apply { moveTo(w * .88f, h * .1f); lineTo(w * .26f, h * .5f); lineTo(w * .88f, h * .9f); close() }, white) }
            "forward" -> { drawPath(triangle(.05f, .5f), white); drawPath(triangle(.5f, .95f), white) }
            "replay" -> {
                val stroke = w * .1f
                drawArc(white, startAngle = -60f, sweepAngle = 300f, useCenter = false, topLeft = Offset(w * .15f, h * .15f), size = Size(w * .7f, h * .7f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawPath(Path().apply { moveTo(w * .62f, h * .02f); lineTo(w * .80f, h * .22f); lineTo(w * .55f, h * .30f); close() }, white)
            }
            "share" -> {
                val stroke = w * .09f
                drawLine(white, Offset(w * .3f, h * .5f), Offset(w * .72f, h * .25f), stroke); drawLine(white, Offset(w * .3f, h * .5f), Offset(w * .72f, h * .75f), stroke)
                listOf(Offset(.25f, .5f), Offset(.75f, .22f), Offset(.75f, .78f)).forEach { drawCircle(white, w * .12f, Offset(it.x * w, it.y * h)) }
            }
            "fullscreen", "exitFullscreen" -> {
                val stroke = w * .11f; val a = .1f; val b = .38f; val inward = icon == "exitFullscreen"
                listOf(Offset(a, a) to Offset(1f, 1f), Offset(1 - a, a) to Offset(-1f, 1f), Offset(a, 1 - a) to Offset(1f, -1f), Offset(1 - a, 1 - a) to Offset(-1f, -1f)).forEach { (corner, dir) ->
                    val c = if (inward) Offset(corner.x + dir.x * (b - a), corner.y + dir.y * (b - a)) else corner
                    val sx = if (inward) -dir.x else dir.x; val sy = if (inward) -dir.y else dir.y
                    drawLine(white, Offset(c.x * w, c.y * h), Offset((c.x + sx * (b - a)) * w, c.y * h), stroke, StrokeCap.Square)
                    drawLine(white, Offset(c.x * w, c.y * h), Offset(c.x * w, (c.y + sy * (b - a)) * h), stroke, StrokeCap.Square)
                }
            }
        }
    }
}

internal fun playerClock(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}
