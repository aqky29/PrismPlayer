package id.flufclip.player

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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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

/**
 * YouTube-like controls on top of the inline/fullscreen video: tap toggles, double tap seeks 10s (further taps on the
 * same side keep adding), long press plays at 2x, centre previous/play/next, red time bar with scrubber.
 */
@Composable internal fun PlayerControlsOverlay(player: ExoPlayer, playing: Boolean, buffering: Boolean, fullscreen: Boolean, title: String, previewFrames: List<Frameset>,
    onBack: () -> Unit, onSettings: () -> Unit, onShare: () -> Unit, onFullscreen: () -> Unit, onNext: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var ended by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var seekSide by remember { mutableIntStateOf(0) }
    var seekSeconds by remember { mutableIntStateOf(0) }
    var seekSequence by remember { mutableIntStateOf(0) }
    var fastForward by remember { mutableStateOf(false) }
    var normalSpeed by remember { mutableFloatStateOf(1f) }
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
    fun seek(side: Int) {
        if (!player.isCurrentMediaItemSeekable) return
        val end = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
        val destination = (player.currentPosition + side * 10_000L).coerceAtLeast(0L).let { if (end != null) it.coerceAtMost(end) else it }
        player.seekTo(destination); position = destination
        seekSeconds = if (seekSide == side) seekSeconds + 10 else 10
        seekSide = side
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
    val scrim by animateFloatAsState(if (controlsVisible && !fastForward) .45f else 0f, tween(200), label = "scrim")
    BoxWithConstraints(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim)).pointerInput(player) {
            detectTapGestures(
                onTap = { tap ->
                    val side = if (tap.x < size.width / 2f) -1 else 1
                    // Right after a double tap YouTube treats further single taps on the same side as more seeking.
                    if (seekSide == side) seek(side) else { controlsVisible = !controlsVisible; interaction++ }
                },
                onDoubleTap = { tap -> controlsVisible = false; seek(if (tap.x < size.width / 2f) -1 else 1) },
                onLongPress = {
                    if (playing) { normalSpeed = player.playbackParameters.speed; player.setPlaybackSpeed(2f); fastForward = true; controlsVisible = false }
                },
                onPress = {
                    tryAwaitRelease()
                    if (fastForward) { player.setPlaybackSpeed(normalSpeed); fastForward = false }
                }
            )
        })
        if (seekSide != 0) SeekRipple(seekSide, seekSeconds)
        if (fastForward) Row(Modifier.align(Alignment.TopCenter).padding(top = 12.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("2x", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(4.dp)); PlayerGlyph("forward", Modifier.size(14.dp))
        }
        AnimatedVisibility(controlsVisible && !fastForward, enter = fadeIn(tween(150)), exit = fadeOut(tween(200)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp, vertical = if (fullscreen) 12.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    ControlButton("Minimalkan video", { interaction++; onBack() }) { Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    if (fullscreen) Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    else Spacer(Modifier.weight(1f))
                    ControlButton("Bagikan video", { interaction++; onShare() }) { PlayerGlyph("share", Modifier.size(22.dp)) }
                    ControlButton("Pengaturan video", { interaction++; onSettings() }) { Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                }
                if (!scrubbing) Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 64.dp else 36.dp)) {
                    val canRestart = position > 3_000L
                    CircleButton("Video sebelumnya", 44, enabled = canRestart, onClick = { interaction++; player.seekTo(0) }) { PlayerGlyph("previous", Modifier.size(20.dp)) }
                    if (buffering && !ended) Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(44.dp), color = Color.White, strokeWidth = 3.dp) }
                    else CircleButton(if (ended) "Putar ulang" else if (playing) "Jeda" else "Putar", 60, onClick = { togglePlay() }) {
                        PlayerGlyph(if (ended) "replay" else if (playing) "pause" else "play", Modifier.size(30.dp))
                    }
                    CircleButton("Video berikutnya", 44, enabled = onNext != null, onClick = { interaction++; onNext?.invoke() }) { PlayerGlyph("next", Modifier.size(20.dp)) }
                }
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = if (fullscreen) 40.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(playerClock(if (scrubbing) scrubPosition else position), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(" / ${if (duration > 0) playerClock(duration) else "--:--"}", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    ControlButton(if (fullscreen) "Keluar layar penuh" else "Layar penuh", { interaction++; onFullscreen() }) { PlayerGlyph(if (fullscreen) "exitFullscreen" else "fullscreen", Modifier.size(20.dp)) }
                }
            }
        }
        if (!fastForward) TimeBar(position = if (scrubbing) scrubPosition else position, buffered = buffered, duration = duration, expanded = controlsVisible, fullscreen = fullscreen,
            enabled = duration > 0 && player.isCurrentMediaItemSeekable,
            onScrub = { scrubbing = true; scrubPosition = it; controlsVisible = true; interaction++ },
            onScrubEnd = { player.seekTo(scrubPosition); position = scrubPosition; scrubbing = false; interaction++ },
            onTap = { if (!controlsVisible) { controlsVisible = true; interaction++ } else scope.launch { player.seekTo(it); position = it; interaction++ } },
            modifier = Modifier.align(Alignment.BottomCenter))
        if (scrubbing) ScrubPreview(previewFrames, scrubPosition, duration, maxWidth, maxHeight, fullscreen)
    }
}

/** Storyboard frame (when YouTube provides one) and time above the scrubber, following the finger like YouTube. */
@Composable private fun BoxScope.ScrubPreview(framesets: List<Frameset>, position: Long, duration: Long, maxWidth: Dp, maxHeight: Dp, fullscreen: Boolean) {
    val frameset = remember(framesets) { framesets.filter { it.frameWidth <= 320 }.maxByOrNull { it.frameWidth } ?: framesets.minByOrNull { it.frameWidth } }
    val bounds = remember(frameset, position) { frameset?.let { runCatching { it.getFrameBoundsAt(position) }.getOrNull() } }
    val sheetUrl = bounds?.let { frameset?.urls?.getOrNull(it[0]) }
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    LaunchedEffect(sheetUrl) {
        if (sheetUrl == null || sheet?.first == sheetUrl) return@LaunchedEffect
        val result = context.imageLoader.execute(ImageRequest.Builder(context).data(sheetUrl).allowHardware(false).build())
        val bitmap = (result as? SuccessResult)?.drawable?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
        if (bitmap != null) sheet = sheetUrl to bitmap.asImageBitmap()
    }
    val image = sheet?.takeIf { it.first == sheetUrl }?.second
    val previewWidth = if (fullscreen) 200.dp else minOf(150.dp, maxWidth * .4f)
    val previewHeight = if (frameset != null && frameset.frameWidth > 0) previewWidth * (frameset.frameHeight.toFloat() / frameset.frameWidth) else previewWidth * 9f / 16f
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val x = (maxWidth * fraction - previewWidth / 2).coerceIn(8.dp, (maxWidth - previewWidth - 8.dp).coerceAtLeast(8.dp))
    val bottom = if (fullscreen) 64.dp else 26.dp
    val showImage = bounds != null && (previewHeight + 28.dp + bottom) < maxHeight
    Column(Modifier.align(Alignment.BottomStart).offset(x = x).padding(bottom = bottom).width(previewWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        if (showImage) Box(Modifier.size(previewWidth, previewHeight).clip(RoundedCornerShape(8.dp)).background(Color(0xFF202020)).border(1.5.dp, Color.White, RoundedCornerShape(8.dp))) {
            if (image != null && bounds != null) Canvas(Modifier.fillMaxSize()) {
                drawImage(image, srcOffset = IntOffset(bounds[1], bounds[2]), srcSize = IntSize(bounds[3] - bounds[1], bounds[4] - bounds[2]),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            }
        }
        Text(playerClock(position), color = Color.White, fontSize = if (showImage) 14.sp else 22.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable private fun TimeBar(position: Long, buffered: Long, duration: Long, expanded: Boolean, fullscreen: Boolean, enabled: Boolean,
    onScrub: (Long) -> Unit, onScrubEnd: () -> Unit, onTap: (Long) -> Unit, modifier: Modifier = Modifier) {
    var width by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    val dot by animateDpAsState(if (dragging) 18.dp else if (expanded) 12.dp else 0.dp, tween(150), label = "scrubber")
    val thickness = if (expanded || dragging) 3.dp else 2.dp
    val side = with(LocalDensity.current) { (if (fullscreen) 16.dp else 0.dp).toPx() }
    fun at(x: Float): Long = if (duration <= 0) 0L else (((x - side) / (width - 2 * side).coerceAtLeast(1f)).coerceIn(0f, 1f) * duration).toLong()
    Box(modifier.fillMaxWidth().height(if (fullscreen) 56.dp else 22.dp).padding(bottom = if (fullscreen) 24.dp else 0.dp).onSizeChanged { width = it.width }
        .pointerInput(enabled, duration) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(onDragStart = { dragging = true; onScrub(at(it.x)) }, onDragEnd = { dragging = false; onScrubEnd() },
                onDragCancel = { dragging = false; onScrubEnd() }) { change, _ -> change.consume(); onScrub(at(change.position.x)) }
        }
        .pointerInput(enabled, duration) { if (enabled) detectTapGestures { onTap(at(it.x)) } }) {
        Canvas(Modifier.fillMaxSize()) {
            val y = if (fullscreen) size.height / 2f else size.height - thickness.toPx() / 2f
            val left = side; val right = size.width - side; val span = right - left
            fun x(value: Long) = left + if (duration > 0) span * (value.toFloat() / duration).coerceIn(0f, 1f) else 0f
            val h = thickness.toPx()
            drawRect(Color.White.copy(alpha = .3f), Offset(left, y - h / 2), Size(span, h))
            drawRect(Color.White.copy(alpha = .5f), Offset(left, y - h / 2), Size(x(buffered) - left, h))
            drawRect(PlayedRed, Offset(left, y - h / 2), Size(x(position) - left, h))
            if (dot > 0.dp) drawCircle(PlayedRed, radius = dot.toPx() / 2, center = Offset(x(position), if (fullscreen) y else y - dot.toPx() / 2 + h / 2))
        }
    }
}

@Composable private fun BoxScope.SeekRipple(side: Int, seconds: Int) {
    val shape = if (side < 0) RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50) else RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
    Box(Modifier.align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(.4f).clip(shape).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.graphicsLayer { scaleX = if (side < 0) -1f else 1f }) { repeat(3) { PlayerGlyph("play", Modifier.size(12.dp)) } }
            Text("$seconds detik", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable private fun ControlButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) { content() }
}

@Composable private fun CircleButton(label: String, size: Int, enabled: Boolean = true, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f)).clickable(enabled = enabled, onClick = onClick).graphicsLayer { alpha = if (enabled) 1f else .4f }
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) { content() }
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
