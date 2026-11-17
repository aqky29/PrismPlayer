package id.flufclip.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class HomeFeedStartupTest {
    private fun sampleItem(id: String): StreamInfoItem =
        StreamInfoItem(0, "https://www.youtube.com/watch?v=$id", "Sample $id", StreamType.VIDEO_STREAM)

    @Test
    fun shouldLoadHomeOnStartOnlyWhenCachedEntriesAreEmpty() {
        assertTrue(shouldLoadHomeOnStart(emptyList()))
        assertFalse(shouldLoadHomeOnStart(listOf(HomeEntry.Video(sampleItem("dQw4w9WgXcQ")))))
    }

    @Test
    fun pullRefreshIndicatorShownOnlyForExplicitFeedPullRefresh() {
        assertFalse(shouldShowFeedPullRefreshIndicator(isFeed = true, loading = true, pullRefreshing = false))
        assertFalse(shouldShowFeedPullRefreshIndicator(isFeed = true, loading = false, pullRefreshing = true))
        assertFalse(shouldShowFeedPullRefreshIndicator(isFeed = false, loading = true, pullRefreshing = true))
        assertTrue(shouldShowFeedPullRefreshIndicator(isFeed = true, loading = true, pullRefreshing = true))
    }

    @Test
    fun cachedChipsRoundTripAndRestoreSelectedDefaultChip() {
        val chips = listOf(
            HomeChip(title = "All", token = "tok_all", params = "p_all", selected = true),
            HomeChip(title = "Gaming", token = "tok_gaming", params = "p_gaming", selected = false),
            HomeChip(title = "Live", token = null, params = null, selected = false)
        )
        val encoded = HomeFeedCache.encodeChipsJson(chips + HomeChip(title = "   "))
        val decoded = HomeFeedCache.decodeChipsJson(encoded)
        assertEquals(chips, decoded)

        val state = HomeFeedState(initialLoggedIn = true, initialSessionRevision = 1)
        state.restoreCachedChips(decoded)
        assertEquals(chips, state.homeChips)
        assertEquals("All", state.homeChipTitle)
        assertEquals("All", state.category)
        assertEquals("tok_all", state.selectedChip?.token)

        val emptyState = HomeFeedState(initialLoggedIn = false, initialSessionRevision = 0)
        emptyState.restoreCachedChips(emptyList())
        assertTrue(emptyState.homeChips.isEmpty())
        assertNull(emptyState.selectedChip)
    }
}
