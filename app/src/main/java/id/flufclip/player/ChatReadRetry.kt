package id.flufclip.player

import java.io.IOException
import kotlinx.coroutines.delay

internal class ChatHttpException(val status: Int, val retryAfterMs: Long? = null) : IOException("YouTube merespons $status")

/** Only read requests: retry in the background until successful or the chat scope is cancelled. */
internal suspend fun <T> retryChatRead(
    pause: suspend (Long) -> Unit = { delay(it) },
    operation: suspend () -> T,
): T {
    var backoff = 1_000L
    while (true) {
        try {
            return operation()
        } catch (e: IOException) {
            if (e is ChatHttpException && e.status !in setOf(429, 500, 502, 503, 504)) throw e
            val serverDelay = (e as? ChatHttpException)?.retryAfterMs?.coerceIn(0L, 120_000L) ?: 0L
            pause(maxOf(backoff, serverDelay))
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }
}
