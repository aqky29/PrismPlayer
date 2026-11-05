package id.flufclip.player

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Arsitektur kaca buram (Frosted / Liquid Glass) ringan dengan AGSL/GraphicsLayer:
 * 1. Sumber backdrop direkam sekali ke satu [GraphicsLayer] ([contentLayer]).
 * 2. Backdrop bersama untuk kapsul direkam pada resolusi rendah (`BLUR_SCALE = 0.5f`) dengan `BlurEffect` +
 *    `ColorMatrixColorFilter` (vibrancy), dibungkus dengan layer `CompositingStrategy.Offscreen` kedua ([upscaledLayer])
 *    supaya proses blur benar-benar terjadi di resolusi rendah.
 * 3. `RenderEffect` dan `ColorFilter` diinisialisasi sekali (`lazy`) dan dibagikan, tidak pernah dikompilasi ulang per frame.
 * 4. Rekam ulang hanya terjadi bila sumber atau koordinat berubah (idle = 0 fps).
 * 5. Fallback otomatis ke mode solid bila API < 31 (`Build.VERSION_CODES.S`).
 */
@Stable
internal class FrostedGlassBackdropState(
    val contentLayer: GraphicsLayer,
    val blurLayer: GraphicsLayer,
    val upscaledLayer: GraphicsLayer
) {
    var contentOriginInRoot by mutableStateOf(Offset.Zero)
    var contentSize by mutableStateOf(IntSize.Zero)
    var sourceRevision by mutableIntStateOf(0)

    // Cache kunci rekaman terakhir supaya tidak merekam ulang saat idle
    var lastRecordedSourceRev: Int = -1
    var lastRecordedRelX: Int = Int.MIN_VALUE
    var lastRecordedRelY: Int = Int.MIN_VALUE
    var lastRecordedW: Int = -1
    var lastRecordedH: Int = -1
}

internal val LocalFrostedGlassBackdrop = compositionLocalOf<FrostedGlassBackdropState?> { null }

private const val BLUR_SCALE = 0.5f

private val SharedVibrancyFilter: ColorFilter by lazy {
    val cm = ColorMatrix().apply { setSaturation(1.38f) }
    ColorMatrixColorFilter(cm).asComposeColorFilter()
}

private val SharedBlurEffect by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radiusPx = 42f * BLUR_SCALE
        AndroidRenderEffect.createBlurEffect(
            radiusPx,
            radiusPx,
            Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
    } else null
}

/**
 * Menyediakan state backdrop kaca buram untuk seluruh sub-tree komposisi.
 */
@Composable
internal fun FrostedGlassBackdropHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val contentLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    val upscaledLayer = rememberGraphicsLayer()

    val state = remember(contentLayer, blurLayer, upscaledLayer) {
        FrostedGlassBackdropState(contentLayer, blurLayer, upscaledLayer).also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurLayer.renderEffect = SharedBlurEffect
                blurLayer.colorFilter = SharedVibrancyFilter
                upscaledLayer.compositingStrategy = CompositingStrategy.Offscreen
            }
        }
    }

    CompositionLocalProvider(LocalFrostedGlassBackdrop provides state) {
        Box(modifier = modifier) {
            content()
        }
    }
}

/**
 * Merekam konten halaman di bawah elemen kaca ke `contentLayer` sumber.
 */
internal fun Modifier.frostedGlassSource(backdrop: FrostedGlassBackdropState?): Modifier {
    if (backdrop == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return this
        .onGloballyPositioned { coords ->
            backdrop.contentOriginInRoot = coords.positionInRoot()
            backdrop.contentSize = coords.size
        }
        .drawWithContent {
            val w = size.width.roundToInt()
            val h = size.height.roundToInt()
            if (w > 0 && h > 0) {
                backdrop.contentLayer.record(IntSize(w, h)) {
                    this@drawWithContent.drawContent()
                }
                drawLayer(backdrop.contentLayer)
                backdrop.sourceRevision++
            } else {
                drawContent()
            }
        }
}

/**
 * Menggambar permukaan kapsul kaca buram (Frosted Glass) dengan:
 * - Potongan backdrop ter-blur di resolusi rendah (0.5x)
 * - Tint kaca translusen gelap/terang
 * - Highlight tepi spekular (`BlendMode.Plus`) tanpa `BlurMaskFilter`
 * - Fallback penuh ke permukaan solid pada perangkat API < 31.
 */
@Composable
internal fun Modifier.frostedGlassCapsule(
    isDark: Boolean = true,
    borderStrokeWidth: Dp = 1.dp
): Modifier {
    val backdrop = LocalFrostedGlassBackdrop.current
    var capsuleOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    val clipPath = remember { Path() }

    val tintColor = if (isDark) Color(0xFF1E1E1E).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.72f)
    val solidFallbackColor = if (isDark) Color(0xFF212121).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f)
    val specularTop = if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.65f)
    val specularBottom = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.08f)

    return this
        .onGloballyPositioned { coords ->
            capsuleOriginInRoot = coords.positionInRoot()
        }
        .drawBehind {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@drawBehind

            val radius = min(w, h) / 2f
            val cornerRadius = CornerRadius(radius, radius)

            clipPath.reset()
            clipPath.addRoundRect(RoundRect(Rect(0f, 0f, w, h), cornerRadius))

            val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                backdrop != null &&
                backdrop.contentSize.width > 0 &&
                backdrop.contentSize.height > 0

            clipPath(clipPath) {
                if (canBlur && backdrop != null) {
                    val relX = (capsuleOriginInRoot.x - backdrop.contentOriginInRoot.x).roundToInt()
                    val relY = (capsuleOriginInRoot.y - backdrop.contentOriginInRoot.y).roundToInt()
                    val intW = w.roundToInt()
                    val intH = h.roundToInt()
                    val scaledW = (w * BLUR_SCALE).roundToInt().coerceAtLeast(1)
                    val scaledH = (h * BLUR_SCALE).roundToInt().coerceAtLeast(1)

                    // Rekam ulang hanya jika sumber atau koordinat berubah
                    if (backdrop.lastRecordedSourceRev != backdrop.sourceRevision ||
                        backdrop.lastRecordedRelX != relX ||
                        backdrop.lastRecordedRelY != relY ||
                        backdrop.lastRecordedW != intW ||
                        backdrop.lastRecordedH != intH
                    ) {
                        backdrop.lastRecordedSourceRev = backdrop.sourceRevision
                        backdrop.lastRecordedRelX = relX
                        backdrop.lastRecordedRelY = relY
                        backdrop.lastRecordedW = intW
                        backdrop.lastRecordedH = intH

                        backdrop.blurLayer.record(IntSize(scaledW, scaledH)) {
                            scale(BLUR_SCALE, BLUR_SCALE, pivot = Offset.Zero) {
                                translate(left = -relX.toFloat(), top = -relY.toFloat()) {
                                    drawLayer(backdrop.contentLayer)
                                }
                            }
                        }
                        backdrop.upscaledLayer.record(IntSize(intW, intH)) {
                            scale(1f / BLUR_SCALE, 1f / BLUR_SCALE, pivot = Offset.Zero) {
                                drawLayer(backdrop.blurLayer)
                            }
                        }
                    }

                    // 1. Gambar layer backdrop ter-blur
                    drawLayer(backdrop.upscaledLayer)
                    // 2. Gambar tint kaca buram
                    drawRect(color = tintColor)
                } else {
                    // Mode solid untuk API < 31 atau bila backdrop belum tersedia
                    drawRect(color = solidFallbackColor)
                }
            }

            // 3. Highlight tepi spekular kaca dengan BlendMode.Plus (tanpa BlurMaskFilter)
            val strokePx = borderStrokeWidth.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(specularTop, specularBottom)),
                topLeft = Offset(strokePx / 2f, strokePx / 2f),
                size = androidx.compose.ui.geometry.Size(w - strokePx, h - strokePx),
                cornerRadius = cornerRadius,
                style = Stroke(width = strokePx),
                blendMode = if (isDark) BlendMode.Plus else BlendMode.SrcOver
            )
        }
}
