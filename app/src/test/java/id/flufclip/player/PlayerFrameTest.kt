package id.flufclip.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFrameTest {
    @Test
    fun testCalculateFullscreen() {
        val limits = MiniLimits.of(400.dp, 800.dp, 78.dp)
        val frame = PlayerFrameLayout.calculate(
            maxWidth = 400.dp,
            maxHeight = 800.dp,
            screenWidth = 400.dp,
            screenHeight = 800.dp,
            statusBarHeight = 24.dp,
            rawVideoAspect = 16f / 9f,
            fullscreen = true,
            exitProgress = 0f,
            collapse = 0f,
            verticalProgress = 0f,
            miniWidth = 240.dp,
            miniHeight = 135.dp,
            miniPositionX = 0f,
            miniPositionY = 0f,
            limits = limits,
        )
        assertFalse(frame.isWide)
        assertEquals(400.dp, frame.width)
        assertEquals(800.dp, frame.height)
        assertEquals(0.dp, frame.x)
        assertEquals(0.dp, frame.y)
    }

    @Test
    fun testCalculatePending() {
        val limits = MiniLimits.of(400.dp, 800.dp, 78.dp)
        val frame = PlayerFrameLayout.calculatePending(
            maxWidth = 400.dp,
            maxHeight = 800.dp,
            collapse = 0f,
            verticalProgress = 0f,
            miniWidth = 240.dp,
            miniHeight = 135.dp,
            miniPositionX = 10f,
            miniPositionY = 20f,
            limits = limits,
        )
        assertFalse(frame.isWide)
        assertEquals(400.dp, frame.width)
        assertEquals(400.dp * 9f / 16f, frame.height)
        assertEquals(0.dp, frame.x)
        assertEquals(0.dp, frame.y)
    }
}
