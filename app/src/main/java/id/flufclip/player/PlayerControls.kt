package id.flufclip.player

import android.content.Context
import android.content.SharedPreferences
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
import androidx.media3.exoplayer.ExoPlayer
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import org.schabi.newpipe.extractor.stream.Frameset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PlayedRed = Color(0xFFFF0033)

/** Player preferences that YouTube exposes in its settings (currently the double-tap seek length). */
internal object PlayerSettings {
    val doubleTapChoices = listOf(5, 10, 15, 20, 30, 60)
    var doubleTapSeconds by mutableIntStateOf(10)
        private set
    private var preferences: SharedPreferences? = null
    fun load(context: Context) {
        if (preferences != null) return
        preferences = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE).also { doubleTapSeconds = it.getInt("double_tap_seconds", 10) }
    }
    fun updateDoubleTap(seconds: Int) { doubleTapSeconds = seconds; preferences?.edit()?.putInt("double_tap_seconds", seconds)?.apply() }
}

/**
 * YouTube-like controls on top of the inline/fullscreen video: tap toggles, double tap seeks (further taps on the same
 * side keep adding), holding plays at 2x (or rewinds at 2x on the left third), centre previous/play/next, chaptered
 * red time bar whose scrubber can be pulled up for precise seeking.
 */
@Composable internal fun PlayerControlsOverlay(player: ExoPlayer, playing: Boolean, buffering: Boolean, fullscreen: Boolean, title: String, previewFrames: List<Frameset>, segments: List<StreamSegment>,
    onBack: () -> Unit, onSettings: () -> Unit, onShare: () -> Unit, onFullscreen: () -> Unit, onNext: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    remember { PlayerSettings.load(context) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var ended by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var fineScrub by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var seekSide by remember { mutableIntStateOf(0) }
    var seekSeconds by remember { mutableIntStateOf(0) }
    var seekSequence by remember { mutableIntStateOf(0) }
    var seekTap by remember { mutableStateOf(Offset.Zero) }
    // 1 = holding for 2x forward, -1 = holding for 2x rewind.
    var speedHold by remember { mutableIntStateOf(0) }
    var normalSpeed by remember { mutableFloatStateOf(1f) }
    var resumeAfterRewind by remember { mutableStateOf(false) }
    var rewindJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            buffered = player.bufferedPosition.coerceAtLeast(0L)
            duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            ended = player.playbackState == Player.STATE_ENDED
            delay(200)
        }
    }
    LaunchedEffect(controlsVisible, playing, interaction, scrubbing) {
        if (controlsVisible && playing && !scrubbing) { delay(3000); controlsVisible = false }
    }
    LaunchedEffect(seekSequence) { if (seekSide != 0) { delay(700); seekSide = 0; seekSeconds = 0 } }
    LaunchedEffect(ended) { if (ended) controlsVisible = true }
    fun seek(side: Int, tap: Offset) {
        if (!player.isCurrentMediaItemSeekable) return
        val step = PlayerSettings.doubleTapSeconds
        val end = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
        val destination = (player.currentPosition + side * step * 1000L).coerceAtLeast(0L).let { if (end != null) it.coerceAtMost(end) else it }
        player.seekTo(destination); position = destination
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
            else -> player.play()
        }
    }
    val chapter = segments.lastOrNull { it.startTimeSeconds * 1000L <= (if (scrubbing) scrubPosition else position) }?.title?.takeIf { it.isNotBlank() }
    val scrim by animateFloatAsState(if (controlsVisible && speedHold == 0) 1f else 0f, tween(200), label = "scrim")
    BoxWithConstraints(modifier.fillMaxSize()) {
        val topGradient = minOf(180.dp, maxHeight * .6f)
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
                    if (press.x < size.width / 3f && player.isCurrentMediaItemSeekable && !ended) {
                        resumeAfterRewind = player.playWhenReady; player.pause(); speedHold = -1; controlsVisible = false
                        rewindJob = scope.launch {
                            while (true) {
                                val target = (player.currentPosition - 200L).coerceAtLeast(0L)
                                player.seekTo(target); position = target
                                if (target == 0L) break
                                delay(100)
                            }
                        }
                    } else if (playing) { normalSpeed = player.playbackParameters.speed; player.setPlaybackSpeed(2f); speedHold = 1; controlsVisible = false }
                },
                onPress = {
                    tryAwaitRelease()
                    when (speedHold) {
                        1 -> player.setPlaybackSpeed(normalSpeed)
                        -1 -> { rewindJob?.cancel(); rewindJob = null; if (resumeAfterRewind) player.play() }
                    }
                    speedHold = 0
                }
            )
        })
        if (seekSide != 0) SeekRipple(seekSide, seekSeconds, seekTap, seekSequence)
        if (speedHold != 0) Row(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (speedHold < 0) { PlayerGlyph("forward", Modifier.size(14.dp).graphicsLayer { scaleX = -1f }); Spacer(Modifier.width(4.dp)) }
            Text("2x", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (speedHold > 0) { Spacer(Modifier.width(4.dp)); PlayerGlyph("forward", Modifier.size(14.dp)) }
        }
        AnimatedVisibility(controlsVisible && speedHold == 0, enter = fadeIn(tween(150)), exit = fadeOut(tween(200)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                if (!scrubbing) Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp, vertical = if (fullscreen) 12.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    ControlButton("Minimalkan video", { interaction++; onBack() }) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    if (fullscreen) Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    else Spacer(Modifier.weight(1f))
                    ControlButton("Bagikan video", { interaction++; onShare() }) { PlayerGlyph("share", Modifier.size(22.dp)) }
                    ControlButton("Pengaturan video", { interaction++; onSettings() }) { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                }
                // YouTube: 56 dp play/pause in an 80 dp touch area, 40 dp previous/next in 48 dp, 24 dp apart (40 dp fullscreen).
                if (!scrubbing) Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 40.dp else 24.dp)) {
                    val canRestart = position > 3_000L
                    CircleButton("Video sebelumnya", 40, 48, enabled = canRestart, onClick = { interaction++; player.seekTo(0) }) { PlayerGlyph("previous", Modifier.size(18.dp)) }
                    if (buffering && !ended) Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(44.dp), color = Color.White, strokeWidth = 3.dp) }
                    else CircleButton(if (ended) "Putar ulang" else if (playing) "Jeda" else "Putar", 56, 80, onClick = { togglePlay() }) {
                        PlayerGlyph(if (ended) "replay" else if (playing) "pause" else "play", Modifier.size(26.dp))
                    }
                    CircleButton("Video berikutnya", 40, 48, enabled = onNext != null, onClick = { interaction++; onNext?.invoke() }) { PlayerGlyph("next", Modifier.size(18.dp)) }
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = if (fullscreen) 16.dp else 12.dp, end = 4.dp, bottom = if (fullscreen) 40.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(playerClock(if (scrubbing) scrubPosition else position), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(" / ${if (duration > 0) playerClock(duration) else "--:--"}", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                    if (chapter != null && !scrubbing) Text("  •  $chapter  ›", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(1f))
                    ControlButton(if (fullscreen) "Keluar layar penuh" else "Layar penuh", { interaction++; onFullscreen() }) { PlayerGlyph(if (fullscreen) "exitFullscreen" else "fullscreen", Modifier.size(20.dp)) }
                }
            }
        }
        if (speedHold == 0) TimeBar(position = if (scrubbing) scrubPosition else position, buffered = buffered, duration = duration, segments = segments, expanded = controlsVisible, fullscreen = fullscreen,
            enabled = duration > 0 && player.isCurrentMediaItemSeekable,
            onScrub = { value, fine -> scrubbing = true; fineScrub = fine; scrubPosition = value; controlsVisible = true; interaction++ },
            onScrubEnd = { player.seekTo(scrubPosition); position = scrubPosition; scrubbing = false; fineScrub = false; interaction++ },
            onTap = { if (!controlsVisible) { controlsVisible = true; interaction++ } else scope.launch { player.seekTo(it); position = it; interaction++ } },
            modifier = Modifier.align(Alignment.BottomCenter))
        if (scrubbing) ScrubPreview(previewFrames, scrubPosition, duration, chapter, fineScrub, maxWidth, maxHeight, fullscreen)
    }
}

/** Loads storyboard sprite sheets once and keeps them for the frames drawn while scrubbing. */
@Composable private fun rememberStoryboardSheets(urls: List<String>): Map<String, ImageBitmap> {
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

@Composable private fun StoryboardFrame(frameset: Frameset, position: Long, sheets: Map<String, ImageBitmap>, modifier: Modifier) {
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
@Composable private fun BoxScope.ScrubPreview(framesets: List<Frameset>, position: Long, duration: Long, chapter: String?, fine: Boolean, maxWidth: Dp, maxHeight: Dp, fullscreen: Boolean) {
    val frameset = remember(framesets) { framesets.filter { it.frameWidth <= 320 }.maxByOrNull { it.frameWidth } ?: framesets.minByOrNull { it.frameWidth } }
    val step = frameset?.durationPerFrame?.toLong()?.takeIf { it > 0 } ?: 2_000L
    val stripCount = if (maxWidth >= 600.dp) 7 else 5
    val stripTimes = if (fine) (-(stripCount / 2)..(stripCount / 2)).map { (position + it * step).coerceIn(0L, duration.coerceAtLeast(0L)) } else listOf(position)
    val urls = remember(frameset, stripTimes) {
        frameset?.let { set -> stripTimes.mapNotNull { t -> runCatching { set.getFrameBoundsAt(t) }.getOrNull()?.let { set.urls.getOrNull(it[0]) } }.distinct() }.orEmpty()
    }
    val sheets = rememberStoryboardSheets(urls)
    val aspect = if (frameset != null && frameset.frameWidth > 0) frameset.frameHeight.toFloat() / frameset.frameWidth else 9f / 16f
    val bottom = if (fullscreen) 64.dp else 26.dp
    val label: @Composable (Boolean) -> Unit = { big ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            if (chapter != null) Text(chapter, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playerClock(position), color = Color.White, fontSize = if (big) 22.sp else 14.sp, fontWeight = FontWeight.Medium)
        }
    }
    if (!fine && maxHeight >= 150.dp) Text("Tarik ke atas untuk pencarian presisi", color = Color.White, fontSize = 12.sp,
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
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
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
    onScrub: (Long, Boolean) -> Unit, onScrubEnd: () -> Unit, onTap: (Long) -> Unit, modifier: Modifier = Modifier) {
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    val dot by animateDpAsState(if (dragging) 20.dp else if (expanded) 12.dp else 0.dp, tween(150), label = "scrubber")
    val thickness = if (expanded || dragging) 3.dp else 2.dp
    val side = with(LocalDensity.current) { (if (fullscreen) 16.dp else 0.dp).toPx() }
    val currentScrub by rememberUpdatedState(onScrub)
    val currentEnd by rememberUpdatedState(onScrubEnd)
    val currentTap by rememberUpdatedState(onTap)
    fun span() = (width - 2 * side).coerceAtLeast(1f)
    fun at(x: Float): Long = if (duration <= 0) 0L else (((x - side) / span()).coerceIn(0f, 1f) * duration).toLong()
    val chapterStarts = remember(segments, duration) { if (duration <= 0) emptyList() else segments.map { it.startTimeSeconds * 1000f / duration }.filter { it > 0f && it < 1f } }
    Box(modifier.fillMaxWidth().height(if (fullscreen) 56.dp else 22.dp).padding(bottom = if (fullscreen) 24.dp else 0.dp).onSizeChanged { width = it.width }
        .pointerInput(enabled, duration) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                val slop = viewConfiguration.touchSlop
                val pull = 40.dp.toPx()
                var scrubbing = false
                var moved = false
                var released = false
                var value = at(down.position.x)
                var lastX = down.position.x
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) { released = true; if (scrubbing) change.consume(); break }
                    val dx = change.position.x - down.position.x
                    val dy = change.position.y - down.position.y
                    if (!scrubbing) {
                        if (abs(dx) > slop || abs(dy) > slop) moved = true
                        if (abs(dx) > slop && abs(dx) >= abs(dy)) { scrubbing = true; dragging = true; value = at(change.position.x); lastX = change.position.x; currentScrub(value, false) }
                        else if (moved) break
                    } else {
                        change.consume()
                        val fine = down.position.y - change.position.y > pull
                        value = if (fine) (value + ((change.position.x - lastX) / span() * duration * .2f).toLong()).coerceIn(0L, duration) else at(change.position.x)
                        lastX = change.position.x
                        currentScrub(value, fine)
                    }
                }
                if (scrubbing) { dragging = false; currentEnd() } else if (released && !moved) currentTap(at(down.position.x))
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val y = if (fullscreen) size.height / 2f else size.height - thickness.toPx() / 2f
            val left = side; val right = size.width - side; val span = right - left
            fun x(value: Long) = left + if (duration > 0) span * (value.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val h = thickness.toPx()
            val gap = (if (dragging) 3.dp else 2.dp).toPx()
            val edges = listOf(left) + chapterStarts.map { left + span * it } + right
            fun piece(color: Color, until: Float) {
                for (i in 0 until edges.size - 1) {
                    val a = edges[i] + if (i > 0) gap / 2 else 0f
                    val b = minOf(edges[i + 1] - if (i < edges.size - 2) gap / 2 else 0f, until)
                    if (b > a) drawRect(color, Offset(a, y - h / 2), Size(b - a, h))
                }
            }
            piece(Color.White.copy(alpha = .3f), right)
            piece(Color.White.copy(alpha = .5f), x(buffered))
            piece(PlayedRed, x(position))
            if (dot > 0.dp) drawCircle(PlayedRed, radius = dot.toPx() / 2, center = Offset(x(position), if (fullscreen) y else y - dot.toPx() / 2 + h / 2))
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
                Text("$seconds detik", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable private fun ControlButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) { content() }
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
