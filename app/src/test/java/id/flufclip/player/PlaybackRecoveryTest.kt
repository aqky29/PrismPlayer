package id.flufclip.player

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryTest {
    @Test fun stalledAutomaticAudioFallbackCanReturnToHlsWithoutOverridingUserChoices() {
        fun check(direct: Boolean = true, automatic: Boolean = true, manualAudio: Boolean = false,
            mode: String = "auto", hls: Boolean = true, live: Boolean = false, short: Boolean = false) =
            canRecoverAutomaticAudioFallback(direct, automatic, manualAudio, mode, hls, live, short)
        assertTrue(check())
        assertFalse(check(direct = false))
        assertFalse(check(automatic = false))
        assertFalse(check(manualAudio = true))
        assertFalse(check(mode = "advanced"))
        assertFalse(check(hls = false))
        assertFalse(check(live = true))
        assertFalse(check(short = true))
    }
    @Test fun repeatedErrorsRecoverAutomaticallyWithBoundedBackoff() = runBlocking {
        val waits = mutableListOf<Long>()
        var attempts = 0
        val ready = retryPlaybackRecovery(stillCurrent = { true }, pause = { waits += it }) { ++attempts == 8 }
        assertTrue(ready)
        assertEquals(8, attempts)
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L), waits)
    }

    @Test fun changingVideoStopsRetriesOfTheOldSource() = runBlocking {
        var current = true
        var attempts = 0
        val ready = retryPlaybackRecovery(stillCurrent = { current }, pause = { current = false }) {
            attempts++
            false
        }
        assertFalse(ready)
        assertEquals(1, attempts)
    }

    @Test fun closingPlayerCancelsRecovery() = runBlocking {
        var attempts = 0
        try {
            retryPlaybackRecovery(stillCurrent = { true }, pause = { throw CancellationException("closed") }) {
                attempts++
                false
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, attempts)
    }

}
