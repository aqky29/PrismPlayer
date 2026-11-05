package id.flufclip.player

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackActionsTest {
    private fun player(state: Int, commands: MutableList<String>): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader, arrayOf(Player::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getPlaybackState" -> state
            "getPlayerError" -> null
            "prepare", "play" -> { commands += method.name; null }
            else -> throw AssertionError("Unexpected call: ${method.name}")
        }
    } as Player

    @Test fun idlePlayerIsPreparedBeforePlaying() {
        val commands = mutableListOf<String>()
        resumeOrRetryPlayback(player(Player.STATE_IDLE, commands))
        assertEquals(listOf("prepare", "play"), commands)
    }

    @Test fun ordinaryPauseResumesWithoutReloadingTheSource() {
        val commands = mutableListOf<String>()
        resumeOrRetryPlayback(player(Player.STATE_READY, commands))
        assertEquals(listOf("play"), commands)
    }
}
