package id.flufclip.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

/** Android's OvershootInterpolator(1f), which YouTube's mini player uses for its 500 ms snap animation. */
internal val OvershootEasing = Easing { t -> val s = t - 1f; s * s * (2f * s + 1f) + 1f }
private val snapSpec = tween<Float>(500, easing = OvershootEasing)
private val snapOffsetSpec = tween<Offset>(500, easing = OvershootEasing)

/** Size limits for the floating mini player, all in dp, following YouTube's `qxo` formulas. */
internal data class MiniLimits(val areaWidth: Float, val areaHeight: Float, val bottomBar: Float, val inset: Float, val min: Float, val max: Float, val default: Float, val large: Float) {
    companion object {
        fun of(maxWidth: Dp, maxHeight: Dp, bottomBar: Dp): MiniLimits {
            val w = maxWidth.value; val h = maxHeight.value; val bar = bottomBar.value
            val inset = if (w >= 840f) 12f else 8f
            val shortSide = minOf(w, h)
            val usableHeight = (h - bar - 2 * inset).coerceAtLeast(60f)
            val max = minOf(if (w >= 1024f) 720f else 640f, shortSide - 2 * inset, usableHeight * 16f / 9f).coerceAtLeast(100f)
            val min = minOf(192f, max)
            return MiniLimits(w, h, bar, inset, min, max, (shortSide / 2f - inset).coerceIn(min, max), minOf(shortSide - 2 * inset, max))
        }
    }
}

/**
 * Position (top-left, dp) and width of the mini player. It snaps to one of four corners with an overshoot, can be
 * pinched around the fingers' focal point with a rubber band past its limits, and can be stashed off the left/right edge.
 */
@Stable internal class MiniPlayerState(private val scope: CoroutineScope) {
    var limits = MiniLimits(360f, 640f, 56f, 8f, 192f, 344f, 192f, 344f)
    /** Bit 0 = right, bit 1 = bottom. YouTube starts bottom right. */
    var corner by mutableIntStateOf(3)
    /** -1 stashed off the left edge, 1 off the right edge, 0 visible. */
    var stashed by mutableIntStateOf(0)
    private var preferred: Float? = null
    private var rawWidth = 0f
    val width = Animatable(0f)
    val position = Animatable(Offset.Zero, Offset.VectorConverter)
    val height get() = width.value * 9f / 16f
    val large get() = width.value >= limits.large * .8f

    private fun target() = (preferred ?: limits.default).coerceIn(limits.min, limits.max)
    private fun top() = limits.inset
    private fun bottom(w: Float) = (limits.areaHeight - limits.bottomBar - limits.inset - w * 9f / 16f).coerceAtLeast(top())
    private fun cornerPosition(c: Int, w: Float) = Offset(if (c and 1 == 1) limits.areaWidth - limits.inset - w else limits.inset, if (c and 2 == 2) bottom(w) else top())
    private fun restingPosition(w: Float) = if (stashed == 0) cornerPosition(corner, w)
        else Offset(if (stashed < 0) -w else limits.areaWidth, (position.value.y).coerceIn(top(), bottom(w)))

    private fun launchNow(block: suspend CoroutineScope.() -> Unit) = scope.launch(start = CoroutineStart.UNDISPATCHED, block = block)

    /** Moves to the resting place for the current corner/size, animated with YouTube's overshoot or instantly. */
    fun settle(animate: Boolean) {
        val w = target()
        val p = restingPosition(w)
        if (!animate || width.value == 0f) launchNow { width.snapTo(w); position.snapTo(p) }
        else { launchNow { width.animateTo(w, snapSpec) }; launchNow { position.animateTo(p, snapOffsetSpec) } }
    }

    fun show() { stashed = 0; settle(animate = false) }
    fun unstash() { stashed = 0; settle(animate = true) }
    fun toggleSize() { preferred = if (large) limits.default else limits.large; settle(animate = true) }

    fun drag(delta: Offset) { launchNow { position.snapTo(position.value + delta) } }

    /** Finger lifted after a drag: stash if pushed past a side edge, otherwise snap to the nearest corner (velocity in dp/s). */
    fun release(velocity: Offset) {
        val w = width.value
        val center = position.value + Offset(w / 2f, height / 2f)
        val projected = center + velocity * .12f
        stashed = when { center.x < 0f || projected.x < -w / 2f -> -1; center.x > limits.areaWidth || projected.x > limits.areaWidth + w / 2f -> 1; else -> 0 }
        val right = projected.x > limits.areaWidth / 2f
        val lower = projected.y > (limits.areaHeight - limits.bottomBar) / 2f
        corner = (if (right) 1 else 0) or (if (lower) 2 else 0)
        settle(animate = true)
    }

    fun pinchStart() { rawWidth = width.value }

    /** Scales around the focal point (dp, relative to the player); past the limits the size grows as scale^0.25. */
    fun pinch(scale: Float, focus: Offset) {
        val old = width.value.takeIf { it > 0f } ?: return
        rawWidth *= scale
        val next = when {
            rawWidth > limits.max -> limits.max * (rawWidth / limits.max).pow(.25f)
            rawWidth < limits.min -> limits.min * (rawWidth / limits.min).pow(.25f)
            else -> rawWidth
        }
        val k = next / old
        launchNow { width.snapTo(next); position.snapTo(position.value + focus * (1f - k)) }
    }

    fun pinchEnd() {
        preferred = rawWidth.coerceIn(limits.min, limits.max)
        release(Offset.Zero)
    }
}

/** Overlay of the floating mini player: close, maximize, ±10 s, centre play/pause with buffering ring, 2 dp time bar. */
@Composable internal fun MiniPlayerControls(player: ExoPlayer, playing: Boolean, buffering: Boolean, width: Dp, large: Boolean, onExpand: () -> Unit, onClose: () -> Unit, onToggleSize: () -> Unit) {
    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var fraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            fraction = if (duration != null) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(250)
        }
    }
    LaunchedEffect(visible, playing, interaction) { if (visible && playing) { delay(3000); visible = false } }
    fun touch() { interaction++ }
    fun skip(ms: Long) {
        touch()
        val end = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
        player.seekTo((player.currentPosition + ms).coerceAtLeast(0L).let { if (end != null) it.coerceAtMost(end) else it })
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { visible = !visible; interaction++ }, onDoubleTap = { onExpand() }) }) {
        AnimatedVisibility(visible || !playing, enter = fadeIn(tween(150)), exit = fadeOut(tween(200)), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f))) {
                MiniButton("Buka pemutar", { onExpand() }, Modifier.align(Alignment.TopStart)) { MiniGlyph("maximize", Modifier.size(20.dp)) }
                MiniButton("Tutup video", onClose, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                MiniButton(if (large) "Perkecil mini player" else "Perbesar mini player", { touch(); onToggleSize() }, Modifier.align(Alignment.BottomEnd).padding(bottom = 2.dp)) {
                    MiniGlyph(if (large) "shrink" else "grow", Modifier.size(18.dp))
                }
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (width >= 300.dp) 24.dp else 8.dp)) {
                    if (width >= 240.dp) MiniButton("Mundur 10 detik", { skip(-10_000) }) { SkipGlyph(forward = false) }
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = .35f)).clickable { touch(); if (playing) player.pause() else player.play() }
                        .semantics { contentDescription = if (playing) "Jeda" else "Putar" }, contentAlignment = Alignment.Center) {
                        if (buffering) CircularProgressIndicator(Modifier.fillMaxSize(), color = Color.White, strokeWidth = 2.dp)
                        PlayerGlyph(if (playing) "pause" else "play", Modifier.size(20.dp))
                    }
                    if (width >= 240.dp) MiniButton("Maju 10 detik", { skip(10_000) }) { SkipGlyph(forward = true) }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = .3f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color(0xFFFF0033)))
        }
    }
}

/** Pill shown at the screen edge while the mini player is stashed; tapping it brings the player back. */
@Composable internal fun MiniPlayerHandle(side: Int, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = if (side < 0) RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp) else RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
    Box(modifier.size(if (wide) 32.dp else 24.dp, 108.dp).clip(shape).background(Color(0xE6282828)).clickable(onClick = onClick)
        .semantics { contentDescription = "Tampilkan mini player" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(12.dp).graphicsLayer { scaleX = if (side < 0) 1f else -1f }) {
            val stroke = size.width * .18f
            drawLine(Color.White, Offset(size.width * .3f, size.height * .1f), Offset(size.width * .75f, size.height * .5f), stroke, StrokeCap.Round)
            drawLine(Color.White, Offset(size.width * .75f, size.height * .5f), Offset(size.width * .3f, size.height * .9f), stroke, StrokeCap.Round)
        }
    }
}

@Composable private fun MiniButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) { content() }
}

/** Circular arrow with "10" in the middle, like YouTube's rewind/forward 10 s buttons. */
@Composable internal fun SkipGlyph(forward: Boolean, size: Dp = 24.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { scaleX = if (forward) 1f else -1f }) {
            val w = this.size.width; val stroke = w * .09f
            drawArc(Color.White, startAngle = -60f, sweepAngle = 290f, useCenter = false, topLeft = Offset(w * .1f, w * .1f), size = Size(w * .8f, w * .8f), style = Stroke(stroke, cap = StrokeCap.Round))
            drawPath(Path().apply { moveTo(w * .56f, 0f); lineTo(w * .80f, w * .17f); lineTo(w * .52f, w * .30f); close() }, Color.White)
        }
        Text("10", color = Color.White, fontSize = (size.value * .34f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun MiniGlyph(icon: String, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val stroke = w * .1f; val white = Color.White
        fun arrow(from: Offset, to: Offset) {
            drawLine(white, from, to, stroke, StrokeCap.Round)
            val dir = (to - from) / (to - from).getDistance()
            val back = to - dir * (w * .3f)
            drawLine(white, to, Offset(back.x, to.y), stroke, StrokeCap.Round)
            drawLine(white, to, Offset(to.x, back.y), stroke, StrokeCap.Round)
        }
        when (icon) {
            // Box with an arrow out of its top-left corner: "open the full player".
            "maximize" -> {
                drawRect(white, Offset(w * .3f, w * .3f), Size(w * .62f, w * .62f), style = Stroke(stroke))
                arrow(Offset(w * .62f, w * .62f), Offset(w * .08f, w * .08f))
            }
            "grow" -> { arrow(Offset(w * .55f, w * .45f), Offset(w * .92f, w * .08f)); arrow(Offset(w * .45f, w * .55f), Offset(w * .08f, w * .92f)) }
            "shrink" -> { arrow(Offset(w * .92f, w * .08f), Offset(w * .58f, w * .42f)); arrow(Offset(w * .08f, w * .92f), Offset(w * .42f, w * .58f)) }
        }
    }
}
