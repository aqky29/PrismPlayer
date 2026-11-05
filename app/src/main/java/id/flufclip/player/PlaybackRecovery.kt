package id.flufclip.player

import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Only undo automatic audio fallback; never override a manual quality or audio choice. */
internal fun canRecoverAutomaticAudioFallback(
    forceDirect: Boolean, hasAutomaticFallback: Boolean, manualAudio: Boolean,
    qualityMode: String, hasHls: Boolean, isLive: Boolean, isShort: Boolean,
): Boolean = forceDirect && hasAutomaticFallback && !manualAudio && qualityMode == "auto" && hasHls && !isLive && !isShort

/** Keep one recovery loop alive until media is ready or the user leaves this video. */
internal suspend fun retryPlaybackRecovery(
    stillCurrent: () -> Boolean,
    pause: suspend (Long) -> Unit = { delay(it) },
    attempt: suspend () -> Boolean,
): Boolean {
    var backoff = 1_000L
    while (stillCurrent()) {
        if (attempt()) return stillCurrent()
        if (!stillCurrent()) return false
        pause(backoff)
        backoff = (backoff * 2).coerceAtMost(30_000L)
    }
    return false
}

/** A prepared source is not a successful recovery until the player actually reaches READY. */
internal suspend fun awaitPlaybackReady(player: Player, mediaId: String): Boolean = suspendCancellableCoroutine { continuation ->
    val listener = object : Player.Listener {
        fun check() {
            val sameItem = player.currentMediaItem?.mediaId == mediaId
            val ready = sameItem && player.playbackState == Player.STATE_READY && player.playerError == null
            if (ready || !sameItem || player.playerError != null) {
                player.removeListener(this)
                if (continuation.isActive) continuation.resume(ready)
            }
        }
        override fun onEvents(player: Player, events: Player.Events) = check()
    }
    player.addListener(listener)
    continuation.invokeOnCancellation { player.removeListener(listener) }
    listener.check()
}
