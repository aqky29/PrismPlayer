package id.flufclip.player

import androidx.media3.common.Player

/** play() alone cannot restart a player that stopped after a source error. */
internal fun resumeOrRetryPlayback(player: Player, retrySource: (() -> Unit)? = null) {
    if (player.playerError != null && retrySource != null) {
        retrySource()
        return
    }
    if (player.playbackState == Player.STATE_IDLE) player.prepare()
    player.play()
}
