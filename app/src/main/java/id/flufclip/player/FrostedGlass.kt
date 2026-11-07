package id.flufclip.player

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
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
import kotlin.math.abs
import kotlin.math.max
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
                size = Size(w - strokePx, h - strokePx),
                cornerRadius = cornerRadius,
                style = Stroke(width = strokePx),
                blendMode = if (isDark) BlendMode.Plus else BlendMode.SrcOver
            )
        }
}

// ============================================================================
// GOOGLE / iOS LIQUID GLASS BAR + BORDERLESS SMOKED FLUID PILL
// (Kapsul luar: kaca cair jernih membiaskan latar di sekeliling bibir kapsul;
//  Pill aktif: bantalan smoked gelap tanpa garis putih + animasi cair halus)
// ============================================================================

private const val LIQUID_BAR_BLUR_SCALE = 0.60f

private val SharedLiquidBarVibrancy: ColorFilter by lazy {
    val cm = ColorMatrix().apply { setSaturation(1.36f) }
    ColorMatrixColorFilter(cm).asComposeColorFilter()
}

private val SharedLiquidBarBlurEffect: RenderEffect? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Blur optik medium (~22px di layar) supaya warna & siluet latar tetap hidup dan terlihat melengkung di tepi kaca
        val radiusPx = 22f * LIQUID_BAR_BLUR_SCALE
        AndroidRenderEffect.createBlurEffect(
            radiusPx,
            radiusPx,
            Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
    } else null
}

/**
 * Shader AGSL (Android 13+ / API 33+) untuk kapsul utama bergaya Google / Apple Liquid Glass:
 * - Seluruh kapsul navigasi bersifat jernih berwarna (veil tipis ~24%) dengan tone-mapping lembut agar ikon putih tetap terbaca.
 * - Di sekeliling tepi atas, bawah, kiri, dan kanan kapsul (`uBevelWidth`), profil lensa busur lingkaran (`1 - sqrt(1 - t^2)`)
 *   menarik dan membelokkan piksel latar secara nyata + dispersi prisma halus.
 */
private const val LIQUID_BAR_AGSL = """
uniform shader content;
uniform float2 uCenter;
uniform float2 uHalfSize;
uniform float2 uBufSize;
uniform float uBevelWidth;
uniform float uRefraction;
uniform float uDispersion;
uniform float uIsDark;

half4 main(float2 fragCoord) {
    float capRadius = max(min(uHalfSize.x, uHalfSize.y), 1.0);
    float segHalfX = max(uHalfSize.x - capRadius, 0.0);

    float2 p = fragCoord - uCenter;
    float2 q = float2(clamp(p.x, -segHalfX, segHalfX), 0.0);
    float2 dVec = p - q;
    float dist = length(dVec);
    float sdf = dist - capRadius;

    float mask = 1.0 - smoothstep(-0.75, 0.65, sdf);
    if (mask <= 0.001) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }

    float2 normal2D = (dist > 0.0001) ? (dVec / dist) : float2(0.0, -1.0);
    float inset = max(-sdf, 0.0);

    // Profil meniskus busur lingkaran di sekeliling bibir kapsul utama
    float bevelW = max(min(uBevelWidth, capRadius * 0.90), 1.0);
    float t = 1.0 - clamp(inset / bevelW, 0.0, 1.0);
    float lensFactor = 1.0 - sqrt(max(1.0 - t * t, 0.0001));

    // Pembiasan optik melengkungkan latar di sepanjang pinggiran atas, bawah, kiri, dan kanan bar
    float2 baseOffset = -normal2D * (uRefraction * lensFactor);
    float2 dispVec = normal2D * (uDispersion * lensFactor);

    float2 minB = float2(1.0, 1.0);
    float2 maxB = max(uBufSize - float2(1.0, 1.0), minB);
    half4 sR = content.eval(clamp(fragCoord + baseOffset + dispVec, minB, maxB));
    half4 sG = content.eval(clamp(fragCoord + baseOffset, minB, maxB));
    half4 sB = content.eval(clamp(fragCoord + baseOffset - dispVec, minB, maxB));

    float3 refracted = float3(
        float(sR.r) / max(float(sR.a), 0.001),
        float(sG.g) / max(float(sG.a), 0.001),
        float(sB.b) / max(float(sB.a), 0.001)
    );

    // Soft-shoulder tone curve: warna merah/biru/ungu tembus kuat (~76%), sorotan putih ditahan lembut agar ikon putih kontras
    float3 darkToned = refracted / (float3(1.0, 1.0, 1.0) + refracted * 0.24);
    float3 baseRgb = mix(refracted, darkToned, uIsDark);

    // Veil kaca translusen tipis + kilau tipis khusus di bibir meniskus terluar (tanpa kabut putih di tengah)
    float3 veilColor = mix(float3(0.96, 0.97, 1.0), float3(0.12, 0.13, 0.18), uIsDark);
    float veilAlpha = mix(0.34, 0.23, uIsDark);
    float3 col = mix(baseRgb, veilColor, veilAlpha);
    float lipSheen = lensFactor * lensFactor * lensFactor * 0.065;
    col += mix(float3(0.02, 0.02, 0.02), float3(0.028, 0.032, 0.042), uIsDark) + float3(lipSheen, lipSheen, lipSheen * 1.05);

    col = clamp(col, float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));
    return half4(half3(col * mask), half(mask));
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LiquidBarApi33Holder {
    private val shader = RuntimeShader(LIQUID_BAR_AGSL)

    fun createRenderEffect(
        centerX: Float,
        centerY: Float,
        halfW: Float,
        halfH: Float,
        bufW: Float,
        bufH: Float,
        bevelWidthPx: Float,
        refractionPx: Float,
        dispersionPx: Float,
        isDark: Boolean,
    ): RenderEffect {
        shader.setFloatUniform("uCenter", centerX, centerY)
        shader.setFloatUniform("uHalfSize", halfW, halfH)
        shader.setFloatUniform("uBufSize", bufW, bufH)
        shader.setFloatUniform("uBevelWidth", bevelWidthPx)
        shader.setFloatUniform("uRefraction", refractionPx)
        shader.setFloatUniform("uDispersion", dispersionPx)
        shader.setFloatUniform("uIsDark", if (isDark) 1f else 0f)
        return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

private class LiquidBarCache {
    var lastSourceRev: Int = -1
    var lastRelX: Int = Int.MIN_VALUE
    var lastRelY: Int = Int.MIN_VALUE
    var lastBufW: Int = -1
    var lastBufH: Int = -1
    var lastHadBackdrop: Boolean = false
    var lastIsDark: Boolean = true
}

/**
 * Menggambar kapsul utama navigasi mengambang bergaya **Google / iOS Liquid Glass**:
 * - Translusen jernih berwarna (bukan abu-abu pekat) dengan *soft-focus blur* + *vibrancy* tinggi.
 * - Pembiasan meniskus AGSL (`bevel = 12.5.dp`, `refraction = 22.dp`) di sekeliling bibir atas, bawah, kiri, dan kanan bar.
 * - Garis tepi kaca tipis (`1.dp`) berkilau di sepanjang sisi atas dan bawah silinder kapsul.
 */
@Composable
internal fun Modifier.liquidGlassBar(
    isDark: Boolean = true,
    borderStrokeWidth: Dp = 1.dp,
    rootPositionProvider: (() -> Offset)? = null,
): Modifier {
    val backdrop = LocalFrostedGlassBackdrop.current
    val preBlurLayer = rememberGraphicsLayer()
    val upscaledLayer = rememberGraphicsLayer()
    val lensLayer = rememberGraphicsLayer()

    val cache = remember(preBlurLayer, upscaledLayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            preBlurLayer.renderEffect = SharedLiquidBarBlurEffect
            preBlurLayer.colorFilter = SharedLiquidBarVibrancy
            upscaledLayer.compositingStrategy = CompositingStrategy.Offscreen
        }
        LiquidBarCache()
    }
    val clipPath = remember { Path() }
    var fallbackOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    val api33Holder = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LiquidBarApi33Holder()
        } else null
    }

    return this
        .onGloballyPositioned { coords ->
            fallbackOriginInRoot = coords.positionInRoot()
        }
        .drawBehind {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@drawBehind

            val radius = min(w, h) * 0.5f
            val cornerRadius = CornerRadius(radius, radius)
            val originInRoot = rootPositionProvider?.invoke() ?: fallbackOriginInRoot

            val hasBackdrop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                backdrop != null &&
                backdrop.contentSize.width > 0 &&
                backdrop.contentSize.height > 0

            val padPx = 8.dp.toPx().roundToInt()
            val intW = w.roundToInt().coerceAtLeast(1)
            val intH = h.roundToInt().coerceAtLeast(1)
            val bufW = intW + padPx * 2
            val bufH = intH + padPx * 2

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val relX = if (backdrop != null) (originInRoot.x - backdrop.contentOriginInRoot.x).roundToInt() - padPx else -padPx
                val relY = if (backdrop != null) (originInRoot.y - backdrop.contentOriginInRoot.y).roundToInt() - padPx else -padPx
                val srcRev = backdrop?.sourceRevision ?: 0

                val changed = srcRev != cache.lastSourceRev ||
                    relX != cache.lastRelX ||
                    relY != cache.lastRelY ||
                    bufW != cache.lastBufW ||
                    bufH != cache.lastBufH ||
                    hasBackdrop != cache.lastHadBackdrop ||
                    isDark != cache.lastIsDark

                if (changed) {
                    cache.lastSourceRev = srcRev
                    cache.lastRelX = relX
                    cache.lastRelY = relY
                    cache.lastBufW = bufW
                    cache.lastBufH = bufH
                    cache.lastHadBackdrop = hasBackdrop
                    cache.lastIsDark = isDark

                    val scaledW = (bufW * LIQUID_BAR_BLUR_SCALE).roundToInt().coerceAtLeast(1)
                    val scaledH = (bufH * LIQUID_BAR_BLUR_SCALE).roundToInt().coerceAtLeast(1)
                    val sx = scaledW.toFloat() / bufW.toFloat()
                    val sy = scaledH.toFloat() / bufH.toFloat()
                    val baseFill = if (isDark) Color(0xFF14161D) else Color(0xFFF3F5F8)

                    preBlurLayer.record(IntSize(scaledW, scaledH)) {
                        drawRect(color = baseFill)
                        if (hasBackdrop && backdrop != null) {
                            scale(sx, sy, pivot = Offset.Zero) {
                                translate(left = -relX.toFloat(), top = -relY.toFloat()) {
                                    drawLayer(backdrop.contentLayer)
                                }
                            }
                        }
                    }
                    upscaledLayer.record(IntSize(bufW, bufH)) {
                        scale(1f / sx, 1f / sy, pivot = Offset.Zero) {
                            drawLayer(preBlurLayer)
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && api33Holder != null) {
                        lensLayer.renderEffect = api33Holder.createRenderEffect(
                            centerX = padPx.toFloat() + w * 0.5f,
                            centerY = padPx.toFloat() + h * 0.5f,
                            halfW = w * 0.5f,
                            halfH = h * 0.5f,
                            bufW = bufW.toFloat(),
                            bufH = bufH.toFloat(),
                            bevelWidthPx = 12.5.dp.toPx(),
                            refractionPx = 22.dp.toPx(),
                            dispersionPx = 1.6.dp.toPx(),
                            isDark = isDark
                        )
                        lensLayer.record(IntSize(bufW, bufH)) {
                            drawLayer(upscaledLayer)
                        }
                    }
                }
            }

            // 1. Gambar badan kapsul Liquid Glass
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && api33Holder != null) {
                translate(left = -padPx.toFloat(), top = -padPx.toFloat()) {
                    drawLayer(lensLayer)
                }
            } else {
                clipPath.reset()
                clipPath.addRoundRect(RoundRect(Rect(0f, 0f, w, h), cornerRadius))
                clipPath(clipPath) {
                    if (hasBackdrop) {
                        translate(left = -padPx.toFloat(), top = -padPx.toFloat()) {
                            drawLayer(upscaledLayer)
                        }
                        val veil = if (isDark) Color(0xFF1E212B).copy(alpha = 0.34f) else Color.White.copy(alpha = 0.45f)
                        drawRect(color = veil)
                    } else {
                        val fallback = if (isDark) Color(0xFF20222A).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.92f)
                        drawRect(color = fallback)
                    }
                }
            }

            // 2. Garis kilau tepi silinder kaca (terang di tepi lurus atas & bawah, lembut di lengkungan ujung kiri/kanan)
            val strokePx = borderStrokeWidth.toPx()
            val rimTop = if (isDark) Color.White.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.78f)
            val rimMid = if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.42f)
            val rimBottom = if (isDark) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.64f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.0f to rimTop,
                    0.50f to rimMid,
                    1.0f to rimBottom,
                    startY = 0f,
                    endY = h
                ),
                topLeft = Offset(strokePx * 0.5f, strokePx * 0.5f),
                size = Size(max(1f, w - strokePx), max(1f, h - strokePx)),
                cornerRadius = cornerRadius,
                style = Stroke(width = strokePx),
                blendMode = if (isDark) BlendMode.Plus else BlendMode.SrcOver
            )
        }
}

/**
 * Menggambar pill tab aktif bergaya **Google Liquid Glass Active Cushion**:
 * - **Tanpa border putih saat diam** (0 garis putih di dalam kapsul sehingga bersih seperti referensi Google iOS).
 * - Bantalan kaca smoked gelap translusen (`#0A0E19` @ ~52%) yang memberikan kontras tinggi untuk ikon aktif
 *   sekaligus membiarkan rona warna Liquid Glass bar di bawahnya tetap menembus halus.
 * - Tetap mendukung deformasi fisika cair (*stretch*, *squish*, *swell*, & *sub-pixel glide*) di fase Draw (0 recompose).
 */
@Composable
internal fun Modifier.smokedFluidPill(
    baseWidth: Dp,
    baseHeight: Dp,
    isDark: Boolean = true,
    subPixelOffsetX: () -> Float = { 0f },
    swellProvider: () -> Float = { 0f },
    dragGlowProvider: () -> Float = { 0f },
    stretchProvider: () -> Float = { 0f },
): Modifier = this.drawBehind {
    val boxW = size.width
    val boxH = size.height
    if (boxW <= 0f || boxH <= 0f) return@drawBehind

    val swell = swellProvider().coerceIn(0f, 1f)
    val dragGlow = dragGlowProvider().coerceIn(0f, 1f)
    val stretch = stretchProvider().coerceIn(0f, 0.26f)
    val subX = subPixelOffsetX().coerceIn(-1f, 1f)

    val minMarginPx = 1.dp.toPx()
    val rawW = baseWidth.toPx() * (1f + stretch) + (4.dp.toPx() * swell)
    val rawH = baseHeight.toPx() * (1f - stretch * 0.20f) + (3.dp.toPx() * swell)
    val pillW = rawW.coerceIn(16f, max(16f, boxW - minMarginPx * 2f))
    val pillH = rawH.coerceIn(16f, max(16f, boxH - minMarginPx * 2f))

    val centerX = boxW * 0.5f + subX
    val centerY = boxH * 0.5f
    val halfW = pillW * 0.5f
    val halfH = pillH * 0.5f
    val pillLeft = centerX - halfW
    val pillTop = centerY - halfH
    val radius = min(halfW, halfH)
    val cornerRadius = CornerRadius(radius, radius)

    val topFill = if (isDark) {
        Color(0xFF0A0E19).copy(alpha = 0.50f - 0.06f * dragGlow)
    } else {
        Color(0xFF1A2233).copy(alpha = 0.14f)
    }
    val bottomFill = if (isDark) {
        Color(0xFF070A12).copy(alpha = 0.56f - 0.06f * dragGlow)
    } else {
        Color(0xFF1A2233).copy(alpha = 0.18f)
    }

    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(topFill, bottomFill),
            startY = pillTop,
            endY = pillTop + pillH
        ),
        topLeft = Offset(pillLeft, pillTop),
        size = Size(pillW, pillH),
        cornerRadius = cornerRadius
    )

    // Saat sedang di-drag jari, munculkan kilau taktil super tipis yang hilang 100% ketika pill diam
    if (dragGlow > 0.01f) {
        val strokePx = 0.75.dp.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = 0.16f * dragGlow),
            topLeft = Offset(pillLeft + strokePx * 0.5f, pillTop + strokePx * 0.5f),
            size = Size(max(1f, pillW - strokePx), max(1f, pillH - strokePx)),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokePx)
        )
    }
}
