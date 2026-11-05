package id.flufclip.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hasil perhitungan ukuran dan posisi pemutar (baik floating utama maupun pending).
 */
internal data class PlayerFrame(
    val width: Dp,
    val height: Dp,
    val x: Dp,
    val y: Dp,
    val inlineWidth: Dp,
    val inlineHeight: Dp,
    val dragTravel: Dp,
    val isWide: Boolean,
)

internal object PlayerFrameLayout {
    fun calculate(
        maxWidth: Dp,
        maxHeight: Dp,
        screenWidth: Dp,
        screenHeight: Dp,
        statusBarHeight: Dp,
        rawVideoAspect: Float,
        fullscreen: Boolean,
        exitProgress: Float,
        collapse: Float,
        verticalProgress: Float,
        miniWidth: Dp,
        miniHeight: Dp,
        miniPositionX: Float,
        miniPositionY: Float,
        limits: MiniLimits,
    ): PlayerFrame {
        val wide = isWideWatchLayout(maxWidth, maxHeight)
        val currentAspect = if (rawVideoAspect > 0f) rawVideoAspect else (16f / 9f)
        val watchAspect = if (wide) (16f / 9f) else currentAspect.coerceIn(4f / 3f, 21f / 9f)
        val inlineWidth = if (wide) wideWatchPlayerWidth(maxWidth) else maxWidth
        val inlineHeight = inlineWidth / watchAspect
        val startX = 0.dp
        val exit = exitProgress
        val boxTop = if (fullscreen) 0.dp else statusBarHeight
        val baseX = startX * exit
        val baseY = statusBarHeight * exit - boxTop
        val baseWidth = screenWidth + (inlineWidth - screenWidth) * exit
        val baseHeight = screenHeight + (inlineHeight - screenHeight) * exit
        val width = baseWidth + (miniWidth - inlineWidth) * collapse
        val height = baseHeight + (miniHeight - inlineHeight) * collapse
        val x = baseX + (miniPositionX.dp - startX) * collapse
        val y = baseY + miniPositionY.dp * verticalProgress
        val travel = (limits.areaHeight - limits.bottomBar - limits.inset).dp - miniHeight
        val dragTravel = if (fullscreen) inlineHeight else travel
        return PlayerFrame(
            width = width,
            height = height,
            x = x,
            y = y,
            inlineWidth = inlineWidth,
            inlineHeight = inlineHeight,
            dragTravel = dragTravel,
            isWide = wide,
        )
    }

    fun calculatePending(
        maxWidth: Dp,
        maxHeight: Dp,
        collapse: Float,
        verticalProgress: Float,
        miniWidth: Dp,
        miniHeight: Dp,
        miniPositionX: Float,
        miniPositionY: Float,
        limits: MiniLimits,
    ): PlayerFrame {
        val wide = isWideWatchLayout(maxWidth, maxHeight)
        val inlineWidth = if (wide) wideWatchPlayerWidth(maxWidth) else maxWidth
        val inlineHeight = inlineWidth * 9f / 16f
        val width = inlineWidth + (miniWidth - inlineWidth) * collapse
        val height = inlineHeight + (miniHeight - inlineHeight) * collapse
        val x = miniPositionX.dp * collapse
        val y = miniPositionY.dp * verticalProgress
        val travel = ((limits.areaHeight - limits.bottomBar - limits.inset).dp - miniHeight).coerceAtLeast(180.dp)
        return PlayerFrame(
            width = width,
            height = height,
            x = x,
            y = y,
            inlineWidth = inlineWidth,
            inlineHeight = inlineHeight,
            dragTravel = travel,
            isWide = wide,
        )
    }
}
