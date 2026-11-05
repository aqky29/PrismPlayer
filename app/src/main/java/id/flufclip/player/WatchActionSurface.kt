package id.flufclip.player

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/** Cached, dithered tonal fill and a feathered upper rim; no blur or offscreen shader. */
@Composable
internal fun Modifier.raisedWatchAction(selected: Boolean = false, interaction: MutableInteractionSource? = null): Modifier {
    val pressed = interaction?.collectIsPressedAsState()?.value == true
    val top by animateColorAsState(when { pressed -> Color(0xFF404040); selected -> Color(0xFF3A3A3A); else -> Color(0xFF343434) }, tween(160), label = "actionSurfaceTop")
    val bottom by animateColorAsState(when { pressed -> Color(0xFF353535); selected -> Color(0xFF303030); else -> Color(0xFF2B2B2B) }, tween(160), label = "actionSurfaceBottom")
    val rimStrength by animateFloatAsState(if (selected || pressed) .09f else .065f, tween(160), label = "actionRim")
    val elevation by animateDpAsState(if (pressed) .25.dp else .5.dp, tween(160), label = "actionElevation")
    val shape = RoundedCornerShape(50)
    return shadow(elevation, shape, clip = false)
        .clip(shape)
        .drawWithCache {
            // Neutral tone profile fitted to the supplied YouTube Share-button screenshot.
            val stops = floatArrayOf(0f, .14f, .36f, .64f, .86f, 1f)
            val blend = floatArrayOf(0f, .20f, .52f, .78f, .94f, 1f)
            val height = size.height.coerceAtLeast(1f)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                shader = LinearGradient(0f, 0f, 0f, height,
                    IntArray(blend.size) { lerp(top, bottom, blend[it]).toArgb() }, stops, Shader.TileMode.CLAMP)
            }
            // The rim fades into the surface, without a contrasting black lower outline.
            val rimShader = LinearGradient(0f, 0f, 0f, height,
                intArrayOf(Color.White.copy(alpha = rimStrength).toArgb(),
                    Color.White.copy(alpha = rimStrength * .65f).toArgb(),
                    Color.White.copy(alpha = rimStrength * .15f).toArgb(),
                    Color.White.copy(alpha = 0f).toArgb()),
                floatArrayOf(0f, .22f, .58f, 1f), Shader.TileMode.CLAMP)
            val rim = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                shader = rimShader
                style = Paint.Style.STROKE
                strokeWidth = .5.dp.toPx()
            }
            val feather = Paint(rim).apply { strokeWidth = 1.dp.toPx(); alpha = 24 }
            val bounds = RectF(0f, 0f, size.width, size.height)
            val inset = .45.dp.toPx()
            val rimBounds = RectF(inset, inset, (size.width - inset).coerceAtLeast(inset), (size.height - inset).coerceAtLeast(inset))
            val radius = size.height / 2
            val rimRadius = (radius - inset).coerceAtLeast(0f)
            onDrawBehind {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRoundRect(bounds, radius, radius, fill)
                    canvas.nativeCanvas.drawRoundRect(rimBounds, rimRadius, rimRadius, feather)
                    canvas.nativeCanvas.drawRoundRect(rimBounds, rimRadius, rimRadius, rim)
                }
            }
        }
}
