package id.flufclip.player

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class PlaybackTrackingFinalTest {
    @Test fun finalUpdateCarriesTheActualPausedPositionEvenWithoutANewSegment() {
        val oldLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            val params = PlaybackTracker.finalWatchTimeParams("session", 42_375, 597, 43_200)
            assertEquals("42.375", params["cmt"])
            assertEquals("42.375", params["st"])
            assertEquals("42.375", params["et"])
            assertEquals("597", params["len"])
            assertEquals("43.2", params["rt"])
            assertEquals("paused", params["state"])
            assertEquals("1", params["final"])
        } finally {
            Locale.setDefault(oldLocale)
        }
    }

    @Test fun negativePositionsAndDurationsAreNotSent() {
        val params = PlaybackTracker.finalWatchTimeParams("session", -1, -1, -1)
        assertEquals("0.000", params["cmt"])
        assertEquals("0", params["len"])
        assertEquals("0.0", params["rt"])
    }
}
