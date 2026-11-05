package id.flufclip.player

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChatReadRetryTest {
    @Test fun temporary503RecoversWithoutRestartingTheSession() = runBlocking {
        val waits = mutableListOf<Long>()
        var calls = 0
        val result = retryChatRead(pause = { waits += it }) {
            if (++calls <= 7) throw ChatHttpException(503)
            "same-continuation-result"
        }
        assertEquals("same-continuation-result", result)
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L), waits)
    }

    @Test fun respectsServerRetryAfter() = runBlocking {
        val waits = mutableListOf<Long>()
        var calls = 0
        retryChatRead(pause = { waits += it }) {
            if (++calls == 1) throw ChatHttpException(503, 15_000L)
        }
        assertEquals(listOf(15_000L), waits)
    }

    @Test fun authorizationErrorsDoNotLoop() = runBlocking {
        var waits = 0
        try {
            retryChatRead(pause = { waits++ }) { throw ChatHttpException(403) }
            fail("403 must propagate")
        } catch (e: ChatHttpException) { assertEquals(403, e.status) }
        assertEquals(0, waits)
    }

    @Test fun leavingChatCancelsBackgroundRetries() = runBlocking {
        var calls = 0
        try {
            retryChatRead(pause = { throw CancellationException("chat closed") }) {
                calls++
                throw IOException("offline")
            }
            fail("cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }
}
