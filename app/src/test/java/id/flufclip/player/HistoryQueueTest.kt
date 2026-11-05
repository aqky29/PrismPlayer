package id.flufclip.player

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryQueueTest {
    @Test fun playNextInsertsAfterCurrentAndKeepsTheRest() {
        val result = enqueueHistoryVideo("b", listOf("a", "b", "c"), "b", "new", true) { it }
        assertEquals(listOf("a", "b", "new", "c"), result)
    }

    @Test fun playLastAppendsWithoutChangingCurrentPosition() {
        val result = enqueueHistoryVideo("b", listOf("a", "b", "c"), "b", "new", false) { it }
        assertEquals(listOf("a", "b", "c", "new"), result)
    }

    @Test fun playerOutsideQueueStartsAQueueWithTheCurrentItem() {
        val result = enqueueHistoryVideo("playing", listOf("old"), "playing", "new", true) { it }
        assertEquals(listOf("playing", "new"), result)
    }

    @Test fun anExistingQueuedVideoMovesInsteadOfCreatingAnAmbiguousDuplicate() {
        val result = enqueueHistoryVideo("a", listOf("a", "b", "c"), "a", "c", true) { it }
        assertEquals(listOf("a", "c", "b"), result)
    }

    @Test fun currentlyPlayingVideoIsNotQueuedAgain() {
        val result = enqueueHistoryVideo("a", listOf("a", "b"), "a", "a", true) { it }
        assertEquals(listOf("a", "b"), result)
    }
}
