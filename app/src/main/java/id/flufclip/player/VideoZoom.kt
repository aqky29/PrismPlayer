package id.flufclip.player

import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Format
import androidx.media3.common.VideoSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Crop-to-fill zoom for the watch player, following YouTube's pinch-to-fill gesture.
 *
 * [zoom] is the scale applied to the player view: 1 = the video fits inside the player box with
 * letterbox bars, [fillScale] = the video exactly covers the box, anything above that is a manual
 * crop zoom. The view is scaled around its centre and clipped by its host, so the picture is
 * cropped instead of squashed; [pan] slides the enlarged picture inside that crop.
 */
internal class VideoZoomState {
    private var settleScope: CoroutineScope? = null
    private var settleJob: Job? = null
    private var sourceAspect = 16f / 9f
    private var viewport = IntSize.Zero
    private var target: View? = null

    var zoom = 1f
        private set
    var pan = Offset.Zero
        private set
    var fillScale = 1f
        private set
    var maxZoom = 4f
        private set

    fun bindScope(scope: CoroutineScope) {
        settleScope = scope
    }

    /** The player view that is scaled. Re-attaching the same view is a no-op. */
    fun attach(view: View?) {
        if (target === view) return
        target = view
        apply()
    }

    fun setViewport(size: IntSize) {
        if (size == viewport) return
        viewport = size
        recompute()
    }

    fun setVideoAspect(aspect: Float) {
        if (!aspect.isFinite() || aspect <= 0f || abs(aspect - sourceAspect) < 0.0001f) return
        sourceAspect = aspect
        recompute()
    }

    fun onVideoSize(videoSize: VideoSize) {
        setVideoAspect(videoAspectOf(videoSize))
    }

    /** Applies one pinch frame: pan by the centroid movement, then scale around the fingers. */
    fun transform(zoomChange: Float, panDelta: Offset, focus: Offset) {
        if (viewport.width <= 0 || viewport.height <= 0) return
        pan += panDelta
        val previous = zoom
        val next = (previous * zoomChange).coerceIn(1f, maxZoom)
        if (next != previous) {
            val centreX = viewport.width / 2f
            val centreY = viewport.height / 2f
            val focusX = focus.x - centreX
            val focusY = focus.y - centreY
            val factor = next / previous
            pan = Offset(
                focusX + (pan.x - focusX) * factor,
                focusY + (pan.y - focusY) * factor,
            )
            zoom = next
        }
        clampPan()
        apply()
    }

    fun panBy(delta: Offset) {
        pan += delta
        clampPan()
        apply()
    }

    fun setZoom(value: Float) {
        zoom = value.coerceIn(1f, maxZoom)
        if (zoom <= 1.0001f) pan = Offset.Zero
        clampPan()
        apply()
    }

    /**
     * Zoom to snap to when the fingers lift, with the same generous zones YouTube uses: anything
     * below 1.05 falls back to "fit", and the whole band from there up to [fillScale] + 0.2 snaps
     * to the exact fill scale. That way a short pinch out is enough to land perfectly on "fill",
     * and only past that band is the zoom kept exactly as the user left it.
     */
    fun snapTarget(): Float = when {
        zoom <= 1.05f -> 1f
        // A video that already covers the box has no distinct fill point to snap to.
        fillScale > 1.04f && zoom <= fillScale + 0.2f -> fillScale
        else -> zoom
    }

    /**
     * Animates to the snap target. "Fit" and "Fill" are automatic framings, so the crop is eased back
     * to the centre instead of keeping the pan the fingers left behind; manual zooms keep their pan.
     */
    fun settle(targetZoom: Float) {
        cancelSettle()
        val end = targetZoom.coerceIn(1f, maxZoom)
        val automatic = end <= 1.0001f || (fillScale > 1.04f && abs(end - fillScale) < 0.001f)
        val startZoom = zoom
        val startPan = pan
        val endPan = if (automatic) Offset.Zero else pan
        if (abs(end - startZoom) < 0.002f && (!automatic || startPan.getDistance() < 0.5f)) {
            zoom = end
            pan = endPan
            clampPan()
            apply()
            return
        }
        val scope = settleScope
        if (scope == null) {
            zoom = end
            pan = endPan
            clampPan()
            apply()
            return
        }
        settleJob = scope.launch {
            animate(0f, 1f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { fraction, _ ->
                zoom = startZoom + (end - startZoom) * fraction
                pan = lerp(startPan, endPan, fraction)
                clampPan()
                apply()
            }
            zoom = end
            pan = endPan
            clampPan()
            apply()
        }
    }

    fun cancelSettle() {
        settleJob?.cancel()
        settleJob = null
    }

    fun reset() {
        cancelSettle()
        zoom = 1f
        pan = Offset.Zero
        apply()
    }

    private fun recompute() {
        if (viewport.width <= 0 || viewport.height <= 0) return
        val containerAspect = viewport.width.toFloat() / viewport.height
        fillScale = max(containerAspect / sourceAspect, sourceAspect / containerAspect)
        maxZoom = max(4f, min(8f, fillScale * 2f))
        zoom = zoom.coerceIn(1f, maxZoom)
        clampPan()
        apply()
    }

    /**
     * Keeps the video covering the crop: the picture may only slide by the part of the frame that
     * overflows the player box. At [fillScale] one axis becomes exactly 0, so "fill" is centred.
     */
    private fun clampPan() {
        if (viewport.width <= 0 || viewport.height <= 0) return
        val containerAspect = viewport.width.toFloat() / viewport.height
        val fittedWidth: Float
        val fittedHeight: Float
        if (sourceAspect >= containerAspect) {
            fittedWidth = viewport.width.toFloat()
            fittedHeight = fittedWidth / sourceAspect
        } else {
            fittedHeight = viewport.height.toFloat()
            fittedWidth = fittedHeight * sourceAspect
        }
        val limitX = ((fittedWidth * zoom - viewport.width) / 2f).coerceAtLeast(0f)
        val limitY = ((fittedHeight * zoom - viewport.height) / 2f).coerceAtLeast(0f)
        pan = Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))
    }

    private fun apply() {
        val view = target ?: return
        view.scaleX = zoom
        view.scaleY = zoom
        view.translationX = pan.x
        view.translationY = pan.y
    }
}

/**
 * Watch-page gestures in one handler: two pointers pinch/pan the crop zoom, one pointer pans while
 * zoomed, and a downward swipe at fit keeps the existing "swipe to minimise/leave fullscreen"
 * behaviour. Runs on the Initial pass so the controls overlay cannot steal the pinch.
 */
@Composable
internal fun Modifier.watchPlayerGestures(
    state: VideoZoomState,
    enabled: Boolean,
    videoAspect: () -> Float,
    rootOffset: () -> Offset,
    travelDistance: () -> Float,
    onSwipeDrag: (Float) -> Unit,
    onSwipeRelease: (Float, Float) -> Unit,
    onSwipeCancel: () -> Unit,
    onZoomChanged: (Float) -> Unit,
): Modifier {
    val aspect = rememberUpdatedState(videoAspect)
    val origin = rememberUpdatedState(rootOffset)
    val travel = rememberUpdatedState(travelDistance)
    val drag = rememberUpdatedState(onSwipeDrag)
    val release = rememberUpdatedState(onSwipeRelease)
    val cancel = rememberUpdatedState(onSwipeCancel)
    val zoomChanged = rememberUpdatedState(onZoomChanged)
    return if (!enabled) this else pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.isConsumed) {
                var released = false
                while (!released) {
                    released = awaitPointerEvent(PointerEventPass.Initial).changes.none { it.pressed }
                }
                return@awaitEachGesture
            }
            state.cancelSettle()
            state.setViewport(size)
            state.setVideoAspect(aspect.value())

            var pinching = false
            var panning = false
            var dragging = false
            var completed = false
            var anchor = down.position + origin.value()
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, anchor) }
            var total = Offset.Zero
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    val primary = event.changes.firstOrNull { it.id == down.id }
                    if (pressed.size >= 2) {
                        pinching = true
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val focus = event.calculateCentroid(useCurrent = true)
                        if (zoomChange.isFinite() && zoomChange > 0f) {
                            state.transform(
                                zoomChange,
                                if (panChange.isFinite) panChange else Offset.Zero,
                                if (focus.isSpecified) focus else Offset(size.width / 2f, size.height / 2f),
                            )
                            // Preview the snap target ("Fit"/"Fill") while the fingers are still down.
                            zoomChanged.value(state.snapTarget())
                        }
                        event.changes.forEach { it.consume() }
                    } else if (pinching) {
                        // One finger lifted mid-pinch: keep panning the enlarged picture.
                        val change = pressed.firstOrNull()
                        if (change != null) {
                            val delta = change.positionChange()
                            if (delta.isFinite) state.panBy(delta)
                            change.consume()
                        }
                    } else if (primary != null) {
                        val current = primary.position + origin.value()
                        val delta = primary.positionChange()
                        total += delta
                        if (!panning && !dragging) {
                            if (state.zoom > 1.001f) {
                                if (total.getDistance() > viewConfiguration.touchSlop) panning = true
                            } else if (current.y - anchor.y > viewConfiguration.touchSlop) {
                                dragging = true
                                anchor = current
                            }
                        }
                        if (panning) {
                            if (delta.isFinite) state.panBy(delta)
                            primary.consume()
                        } else if (dragging) {
                            tracker.addPosition(primary.uptimeMillis, current)
                            primary.consume()
                            val fraction = ((current.y - anchor.y) / travel.value().coerceAtLeast(1f)).coerceIn(0f, 1f)
                            if (primary.pressed) drag.value(fraction) else {
                                completed = true
                                release.value(fraction, tracker.calculateVelocity().y / density)
                            }
                        }
                    }
                    if (pressed.isEmpty()) break
                }
            } finally {
                if (dragging && !completed) cancel.value()
                if (pinching) {
                    val target = state.snapTarget()
                    if (abs(target - state.zoom) > 0.002f) state.settle(target) else state.setZoom(target)
                    zoomChanged.value(target)
                }
            }
        }
    }
}

/** Small centred chip that shows the pinch scale, then disappears on its own. */
@Composable
internal fun ZoomHud(text: MutableState<String?>, modifier: Modifier = Modifier) {
    val value = text.value ?: return
    LaunchedEffect(value) {
        delay(850)
        if (text.value == value) text.value = null
    }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

internal fun zoomLabel(zoom: Float, fillScale: Float): String = when {
    zoom <= 1.02f -> "Fit"
    abs(zoom - fillScale) <= 0.015f -> "Fill"
    else -> "${(zoom * 100).roundToInt()}%"
}

internal fun videoAspectOf(format: Format?): Float {
    if (format == null || format.width <= 0 || format.height <= 0) return 16f / 9f
    val ratio = if (format.pixelWidthHeightRatio > 0f) format.pixelWidthHeightRatio else 1f
    var aspect = format.width * ratio / format.height
    if (format.rotationDegrees == 90 || format.rotationDegrees == 270) aspect = 1f / aspect
    return aspect.takeIf { it.isFinite() && it > 0f } ?: 16f / 9f
}

internal fun videoAspectOf(size: VideoSize): Float {
    if (size.width <= 0 || size.height <= 0) return 16f / 9f
    val ratio = if (size.pixelWidthHeightRatio > 0f) size.pixelWidthHeightRatio else 1f
    var aspect = size.width * ratio / size.height
    if (size.unappliedRotationDegrees == 90 || size.unappliedRotationDegrees == 270) aspect = 1f / aspect
    return aspect.takeIf { it.isFinite() && it > 0f } ?: 16f / 9f
}
