package id.flufclip.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavAutoHideTest {
    @Test
    fun hidesOnDownwardScrollAndShowsOnUpwardScroll() {
        val state = BottomNavAutoHideState(hideThresholdPx = 36f, showThresholdPx = 20f)
        val conn = state.nestedScrollConnection

        assertTrue(state.visible)

        // Small downward scroll below threshold does not hide yet
        conn.onPostScroll(consumed = Offset(0f, -20f), available = Offset.Zero, source = NestedScrollSource.UserInput)
        assertTrue(state.visible)

        // Additional downward scroll crosses threshold -> hides
        conn.onPostScroll(consumed = Offset(0f, -20f), available = Offset.Zero, source = NestedScrollSource.UserInput)
        assertFalse(state.visible)

        // Small upward scroll below threshold does not show yet
        conn.onPostScroll(consumed = Offset(0f, 10f), available = Offset.Zero, source = NestedScrollSource.UserInput)
        assertFalse(state.visible)

        // Additional upward scroll crosses threshold -> shows
        conn.onPostScroll(consumed = Offset(0f, 12f), available = Offset.Zero, source = NestedScrollSource.UserInput)
        assertTrue(state.visible)
    }

    @Test
    fun horizontalScrollDoesNotHideBottomNav() {
        val state = BottomNavAutoHideState(hideThresholdPx = 36f, showThresholdPx = 20f)
        val conn = state.nestedScrollConnection

        conn.onPostScroll(consumed = Offset(-150f, 0f), available = Offset(0f, -50f), source = NestedScrollSource.UserInput)
        assertTrue(state.visible)
    }

    @Test
    fun pullingDownAtTopShowsHiddenBottomNav() {
        val state = BottomNavAutoHideState(hideThresholdPx = 36f, showThresholdPx = 20f)
        val conn = state.nestedScrollConnection

        conn.onPostScroll(consumed = Offset(0f, -40f), available = Offset.Zero, source = NestedScrollSource.UserInput)
        assertFalse(state.visible)

        // Even when list is at top (consumed.y == 0), pulling down (available.y > 0) restores visibility
        conn.onPostScroll(consumed = Offset.Zero, available = Offset(0f, 25f), source = NestedScrollSource.UserInput)
        assertTrue(state.visible)
    }
}
