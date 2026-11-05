package id.flufclip.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
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
internal data class MiniLimits(val areaWidth: Float, val areaHeight: Float, val bottomBar: Float, val inset: Float, val min: Float, val max: Float, val default: Float) {
    companion object {
        fun of(maxWidth: Dp, maxHeight: Dp, bottomBar: Dp): MiniLimits {
            val w = maxWidth.value; val h = maxHeight.value; val bar = bottomBar.value
            val inset = if (w >= 840f || (w >= 600f && w > h)) 12f else 8f
            val shortSide = minOf(w, h)
            val usableHeight = (h - bar - 2 * inset).coerceAtLeast(60f)
            val max = minOf(if (w >= 1024f) 720f else 640f, shortSide - 2 * inset, usableHeight * 16f / 9f).coerceAtLeast(100f)
            val min = minOf(192f, max)
            return MiniLimits(w, h, bar, inset, min, max, (shortSide / 2f - inset).coerceIn(min, max))
        }
    }
}

/**
 * Position (top-left, dp) and width of the mini player. It snaps to one of four corners with an overshoot, can be
 * pinched around the fingers' focal point with a rubber band past its limits, and can be stashed off the left/right edge.
 */
@Stable internal class MiniPlayerState(private val scope: CoroutineScope) {
    var limits = MiniLimits(360f, 640f, 56f, 8f, 192f, 344f, 192f)
    /** Bit 0 = right, bit 1 = bottom. YouTube starts bottom right. */
    var corner by mutableIntStateOf(3)
    /** -1 stashed off the left edge, 1 off the right edge, 0 visible. */
    var stashed by mutableIntStateOf(0)
    private var preferred: Float? = null
    private var rawWidth = 0f
    val width = Animatable(0f)
    val position = Animatable(Offset.Zero, Offset.VectorConverter)
    val height get() = width.value * 9f / 16f

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

    /** Double tap: switches between the default and the "large" width, like YouTube's `qxo.P()`. */
    fun toggleSize() {
        if (stashed != 0) return
        val large = minOf(minOf(limits.areaWidth, limits.areaHeight) - 2 * limits.inset, limits.max)
        preferred = if (target() >= large) limits.default else large
        settle(animate = true)
    }
}

/** Overlay of the floating mini player, matching YouTube 21.43's `modern_miniplayer_controls_overlay` (driven by `oix`):
 * the controls stay on screen (no scrim, no auto-hide). Close sits at the top-right and the 48 dp play/pause/replay
 * button (24 dp icon) at the top-left: `oix.n()` drops its end/bottom constraints, leaving it pinned top-start. Neither has a filled background, only a
 * circular ripple, and the icons carry YouTube's soft drop shadow. The expand and ±10 s buttons exist in the layout but
 * this version never shows them: a tap on the video expands instead. Taps/drag/pinch go through [gestureModifier]. */
@Composable internal fun MiniPlayerControls(player: ExoPlayer, playing: Boolean, buffering: Boolean, onClose: () -> Unit, gestureModifier: Modifier = Modifier, recovering: Boolean = false, onRetryPlayback: (() -> Unit)? = null) {
    var fraction by remember { mutableFloatStateOf(0f) }
    var ended by remember { mutableStateOf(false) }
    var playWhenReady by remember(player) { mutableStateOf(player.playWhenReady) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(value: Boolean, reason: Int) { playWhenReady = value }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            fraction = if (duration != null) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            ended = player.playbackState == Player.STATE_ENDED
            delay(250)
        }
    }
    // scrim_overlay: full-size ytOverlayBackgroundMedium (black 60%), faded in by okz while the video is not playing.
    // playWhenReady rather than isPlaying, so buffering does not flash the scrim.
    val scrim by animateFloatAsState(if (ended || !playing && !playWhenReady) 1f else 0f, tween(250), label = "mini scrim")
    val wantsPlayback = playing || (buffering && playWhenReady)
    Box(Modifier.fillMaxSize().then(gestureModifier)) {
        if (scrim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f * scrim)))
        if (buffering && !recovering) androidx.compose.material3.CircularProgressIndicator(
            Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 3.dp)
        MiniButton("Close miniplayer", onClose, Modifier.align(Alignment.TopEnd)) { ShadowIcon(R.drawable.yt_mini_close) }
        val (icon, label) = when {
            player.playerError != null -> R.drawable.yt_replay to "Try again"
            ended -> R.drawable.yt_replay to "Replay"
            wantsPlayback -> R.drawable.yt_pause to "Pause video"
            else -> R.drawable.yt_play to "Play video"
        }
        MiniButton(label, {
            if (!recovering) when {
                ended -> { player.seekTo(0L); player.play() }
                wantsPlayback -> player.pause()
                else -> resumeOrRetryPlayback(player, onRetryPlayback)
            }
        }, Modifier.align(Alignment.TopStart)) {
            if (recovering) androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            else ShadowIcon(icon)
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = .20f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color(0xFFFF0000)))
        }
    }
}

/** Pill shown at the screen edge while the mini player is stashed; tapping it brings the player back. */
@Composable internal fun MiniPlayerHandle(side: Int, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = if (side < 0) RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp) else RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
    Box(modifier.size(if (wide) 32.dp else 24.dp, 108.dp).clip(shape).background(Color(0xE6282828)).clickable(onClick = onClick)
        .semantics { contentDescription = "Show miniplayer" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(12.dp).graphicsLayer { scaleX = if (side < 0) 1f else -1f }) {
            val stroke = size.width * .18f
            drawLine(Color.White, Offset(size.width * .3f, size.height * .1f), Offset(size.width * .75f, size.height * .5f), stroke, StrokeCap.Round)
            drawLine(Color.White, Offset(size.width * .75f, size.height * .5f), Offset(size.width * .3f, size.height * .9f), stroke, StrokeCap.Round)
        }
    }
}

/** 48 dp touch target with only a circular ripple, like YouTube's `YouTubePlayerButton` style (`touch_feedback_overlay`). */
@Composable private fun MiniButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) { content() }
}

/** 24 dp white icon with YouTube's icon shadow (`shadow_icon_*`: black at alpha 166, 1 dp down, 4 dp blur). */
@Composable private fun ShadowIcon(id: Int) {
    val painter = painterResource(id)
    Box(Modifier.size(24.dp)) {
        Image(painter, null, Modifier.fillMaxSize().offset(y = 1.dp).blur(2.dp, BlurredEdgeTreatment.Unbounded),
            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 166 / 255f)))
        Image(painter, null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(Color.White))
    }
}
