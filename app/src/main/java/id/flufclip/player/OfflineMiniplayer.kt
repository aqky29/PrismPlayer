package id.flufclip.player

import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.flow.update
import kotlin.math.abs

internal fun offlineMinimizeCommit(distanceDp: Float, velocityDp: Float, playerHeightDp: Float): Boolean =
    distanceDp > 0 && when {
        velocityDp > 800f -> true
        velocityDp < -800f -> false
        else -> distanceDp >= maxOf(64f, playerHeightDp * .35f)
    }

/** Observe the initial pass like the main watch player. Leave the time bar's precision scrub area alone. */
@Composable
internal fun Modifier.offlineMinimizeGesture(onMinimize: () -> Unit): Modifier {
    val currentMinimize by rememberUpdatedState(onMinimize)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.y >= size.height - 40.dp.toPx()) return@awaitEachGesture
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var distance = Offset.Zero
            var dragging = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } > 1) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                distance = change.position - down.position
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!dragging && distance.y > viewConfiguration.touchSlop && distance.y > abs(distance.x)) dragging = true
                if (dragging) change.consume()
                if (!change.pressed) {
                    if (dragging && offlineMinimizeCommit(distance.y / density, tracker.calculateVelocity().y / density, size.height / density)) currentMinimize()
                    break
                }
            } while (true)
        }
    }
}

/** Render the same draggable/pinchable/stashable mini player used by online playback. */
@Composable
internal fun OfflineFloatingMini(saved: SavedDownload, player: ExoPlayer, onExpand: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = remember(player) { MiniPlayerState(scope) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val limits = MiniLimits.of(maxWidth, maxHeight, 56.dp)
        LaunchedEffect(limits) { state.limits = limits; state.show() }
        val w = state.width.value.takeIf { it > 0 } ?: limits.default
        val p = state.position.value
        FloatingPlayer(
            player = player, title = saved.title, zoomKey = saved.uri, locked = false, onUnlock = onExpand, isLive = false,
            autoplay = false, hasSubtitles = false, subtitlesEnabled = false, previewFrames = emptyList(), segments = emptyList(),
            fullscreen = false, minimized = true, progress = 1f, travelDistance = maxHeight, miniState = state,
            modifier = Modifier.offset { IntOffset(p.x.dp.roundToPx(), p.y.dp.roundToPx()) }.size(w.dp, (w * 9f / 16f).dp),
            onRetryPlayback = { if (isLocalDownloadUri(saved.uri)) { player.prepare(); player.play() } },
            onNext = null, onPrevious = null, onAutoplay = onExpand, onSubtitles = onExpand,
            onBack = onExpand, onSettings = onExpand, onFullscreen = onExpand,
            onExpand = onExpand, onClose = onClose, onDrag = {}, onCancelDrag = {}, onRelease = { _, _ -> })
        val wide = isWideWatchLayout(maxWidth, maxHeight)
        if (state.stashed != 0) MiniPlayerHandle(state.stashed, wide,
            Modifier.offset { IntOffset(if (state.stashed < 0) 0 else (maxWidth - if (wide) 32.dp else 24.dp).roundToPx(), (p.y.dp + (w * 9f / 32f).dp - 54.dp).roundToPx()) }) { state.unstash() }
    }
}

/** A small ownership bridge when the user leaves Downloads through the app's bottom navigation. */
internal object OfflinePlaybackBridge {
    internal data class Session(val saved: SavedDownload, val player: ExoPlayer, val context: Context, val autoplay: Boolean)
    var session by mutableStateOf<Session?>(null)
        private set
    private var listener: Player.Listener? = null

    private fun save(current: Session) {
        val duration = current.player.duration.takeIf { it > 0 } ?: current.saved.video.durationMs
        current.context.getSharedPreferences("download_positions", 0).edit()
            .putLong(current.saved.uri, downloadResumePosition(current.player.currentPosition, duration))
            .putLong("duration:${current.saved.uri}", duration).apply()
        SavedDownloads.revision.update { it + 1 }
    }
    fun offer(context: Context, saved: SavedDownload, player: ExoPlayer, autoplay: Boolean) {
        close()
        session = Session(saved, player, context.applicationContext, autoplay)
        listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val current = session ?: return
                if (state != Player.STATE_ENDED || !current.autoplay) return
                save(current)
                val queue = SavedDownloads.list(current.context)
                val index = queue.indexOfFirst { it.uri == current.saved.uri }
                val next = queue.getOrNull(index + 1)?.takeIf { index >= 0 && isLocalDownloadUri(it.uri) } ?: return
                session = current.copy(saved = next)
                player.setMediaSource(ProgressiveMediaSource.Factory(DefaultDataSource.Factory(current.context)).createMediaSource(
                    MediaItem.Builder().setUri(next.uri).setMimeType(next.mime).setMediaId(next.uri).build()))
                player.prepare(); player.play()
            }
        }.also { player.addListener(it) }
    }
    fun take(uri: String?): ExoPlayer? {
        val current = session ?: return null
        if (current.saved.uri != uri) { close(); return null }
        listener?.let { current.player.removeListener(it) }
        listener = null
        session = null
        return current.player
    }
    fun pause() { session?.let { save(it); it.player.pause() } }
    fun close() {
        val current = session ?: return
        save(current)
        listener?.let { current.player.removeListener(it) }
        listener = null
        session = null
        current.player.release()
    }
}

@Composable
internal fun OfflineMiniOverlay() {
    val current = OfflinePlaybackBridge.session ?: return
    val context = LocalContext.current
    OfflineFloatingMini(current.saved, current.player,
        onExpand = { context.startActivity(SavedDownloads.openIntent(context, current.saved)) },
        onClose = { OfflinePlaybackBridge.close() })
}
