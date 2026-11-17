package id.flufclip.player

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.unit.Dp
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
internal fun OfflineFloatingMini(saved: SavedDownload, player: ExoPlayer, onExpand: () -> Unit, onClose: () -> Unit, bottomBar: Dp = 88.dp) {
    val scope = rememberCoroutineScope()
    val state = remember(player) { MiniPlayerState(scope) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val limits = MiniLimits.of(maxWidth, maxHeight, bottomBar)
        state.limits = limits
        LaunchedEffect(limits) { state.settle(animate = true) }
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
    private fun playRelative(delta: Int): Boolean {
        val current = session ?: return false
        save(current)
        val queue = SavedDownloads.list(current.context)
        val index = queue.indexOfFirst { it.uri == current.saved.uri }
        val next = queue.getOrNull(index + delta)?.takeIf { index >= 0 && isLocalDownloadUri(it.uri) } ?: return false
        session = current.copy(saved = next)
        current.player.setMediaSource(ProgressiveMediaSource.Factory(DefaultDataSource.Factory(current.context)).createMediaSource(
            MediaItem.Builder().setUri(next.uri).setMimeType(next.mime).setMediaId(next.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(next.title).setArtist(next.video.uploader).build()).build()))
        current.player.prepare(); current.player.play()
        PlaybackSessionHost.syncService(current.context)
        return true
    }
    fun saveCurrent() { session?.let(::save) }
    fun offer(context: Context, saved: SavedDownload, player: ExoPlayer, autoplay: Boolean) {
        close()
        val appContext = context.applicationContext
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        player.setHandleAudioBecomingNoisy(true)
        session = Session(saved, player, appContext, autoplay)
        listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                PlaybackSessionHost.syncService(appContext)
            }
            override fun onPlaybackStateChanged(state: Int) {
                val current = session ?: return
                PlaybackSessionHost.syncService(appContext)
                if (state != Player.STATE_ENDED || !current.autoplay) return
                playRelative(1)
            }
        }.also { player.addListener(it) }
        PlaybackSessionHost.setTarget(
            PlaybackSessionHost.Target(
                tag = "offline",
                player = player,
                hasNext = {
                    val current = session ?: return@Target false
                    val queue = SavedDownloads.list(current.context)
                    val index = queue.indexOfFirst { it.uri == current.saved.uri }
                    index >= 0 && index + 1 < queue.size
                },
                hasPrevious = {
                    val current = session ?: return@Target false
                    val queue = SavedDownloads.list(current.context)
                    queue.indexOfFirst { it.uri == current.saved.uri } > 0
                },
                onNext = { playRelative(1) },
                onPrevious = { playRelative(-1) },
                onClose = { player.pause() },
                contentIntent = { ctx ->
                    Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
            )
        )
        PlaybackSessionHost.syncService(appContext)
    }
    fun take(uri: String?): ExoPlayer? {
        val current = session ?: return null
        if (current.saved.uri != uri) { close(); return null }
        listener?.let { current.player.removeListener(it) }
        listener = null
        session = null
        if (PlaybackSessionHost.activeTarget?.tag == "offline") PlaybackSessionHost.setTarget(null)
        return current.player
    }
    fun pause() { session?.let { save(it); it.player.pause() } }
    fun close() {
        val current = session ?: return
        save(current)
        listener?.let { current.player.removeListener(it) }
        listener = null
        session = null
        if (PlaybackSessionHost.activeTarget?.tag == "offline") {
            PlaybackSessionHost.setTarget(null)
            PlaybackSessionHost.syncService(current.context)
        }
        current.player.release()
    }
}

@Composable
internal fun OfflineMiniOverlay(bottomBar: Dp = 88.dp) {
    val current = OfflinePlaybackBridge.session ?: return
    val context = LocalContext.current
    OfflineFloatingMini(current.saved, current.player,
        onExpand = { context.startActivity(SavedDownloads.openIntent(context, current.saved)) },
        onClose = { OfflinePlaybackBridge.close() },
        bottomBar = bottomBar)
}
