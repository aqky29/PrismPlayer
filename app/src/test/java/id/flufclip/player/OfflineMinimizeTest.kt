package id.flufclip.player

import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.unit.dp

class OfflineMinimizeTest {
    @Test fun shortOrUpwardMovesDoNotDismissThePlayer() {
        assertFalse(offlineMinimizeCommit(-100f, 1200f, 200f))
        assertFalse(offlineMinimizeCommit(0f, 1200f, 200f))
        assertFalse(offlineMinimizeCommit(10f, 200f, 200f))
        assertFalse(offlineMinimizeCommit(100f, -801f, 200f))
    }
    @Test fun DownwardDistanceOrFlingCommitsAfterTheGesturePassedTouchSlop() {
        assertTrue(offlineMinimizeCommit(71f, 0f, 200f))
        assertTrue(offlineMinimizeCommit(20f, 801f, 200f))
        assertFalse(offlineMinimizeCommit(63f, 0f, 100f))
        assertTrue(offlineMinimizeCommit(64f, 0f, 100f))
    }
    @Test fun miniSizeLeavesSpaceForTheNavigationBar() {
        val limits = MiniLimits.of(800.dp, 480.dp, 56.dp)
        assertTrue(limits.default in limits.min..limits.max)
        assertTrue(limits.default * 9f / 16f + 56f + 2 * limits.inset <= 480f)
        assertTrue(limits.max <= 800f - 2 * limits.inset)
    }
}
