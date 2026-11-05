package id.flufclip.player

import org.junit.Assert.*
import org.junit.Test

class WatchActionModelTest {
    @Test fun ongoingLiveDoesNotOfferFileDownloadOrReplay() {
        val actions = watchActions(live = true, chatAvailable = true, replayAvailable = true, downloadable = true)
        assertTrue(WatchActionKind.LIVE_CHAT in actions)
        assertFalse(WatchActionKind.REPLAY_CHAT in actions)
        assertFalse(WatchActionKind.DOWNLOAD in actions)
    }

    @Test fun replayWithMediaOffersItsOwnChatAndDownload() {
        val actions = watchActions(live = false, chatAvailable = true, replayAvailable = true, downloadable = true)
        assertTrue(WatchActionKind.REPLAY_CHAT in actions)
        assertFalse(WatchActionKind.LIVE_CHAT in actions)
        assertTrue(actions.indexOf(WatchActionKind.DOWNLOAD) > actions.indexOf(WatchActionKind.SHARE))
    }

    @Test fun missingCapabilitiesDoNotCreateDeadButtons() {
        val actions = watchActions(live = false, chatAvailable = false, replayAvailable = false, downloadable = false)
        assertFalse(WatchActionKind.DOWNLOAD in actions)
        assertFalse(WatchActionKind.LIVE_CHAT in actions)
        assertFalse(WatchActionKind.REPLAY_CHAT in actions)
        assertTrue(WatchActionKind.SHARE in actions)
        assertTrue(WatchActionKind.SAVE in actions)
    }
}
