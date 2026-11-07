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
import androidx.compose.ui.graphics.ClipOp
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
// DARK LIQUID GLASS PILL v2 (iOS 26 Style: Calm Dark Smoked Idle + Clear Lens Drag)
// ============================================================================

private const val PILL_REST_BLUR_SCALE = 0.50f
private const val PILL_LENS_BLUR_SCALE = 0.80f

private val SharedPillRestVibrancy: ColorFilter by lazy {
    val cm = ColorMatrix().apply { setSaturation(1.22f) }
    ColorMatrixColorFilter(cm).asComposeColorFilter()
}

private val SharedPillLensVibrancy: ColorFilter by lazy {
    val cm = ColorMatrix().apply { setSaturation(1.45f) }
    ColorMatrixColorFilter(cm).asComposeColorFilter()
}

private val SharedPillRestBlurEffect: RenderEffect? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Blur lembut saat diam agar tidak ada garis/warna belang tajam di belakang ikon putih
        val radiusPx = 34f * PILL_REST_BLUR_SCALE
        AndroidRenderEffect.createBlurEffect(
            radiusPx,
            radiusPx,
            Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
    } else null
}

private val SharedPillLensBlurEffect: RenderEffect? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Polish sangat tipis saat di-drag agar pembiasan lensa cair terlihat jernih & tajam (tanpa noise piksel)
        val radiusPx = 3.2f * PILL_LENS_BLUR_SCALE
        AndroidRenderEffect.createBlurEffect(
            radiusPx,
            radiusPx,
            Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
    } else null
}

/**
 * Shader AGSL v2 (Android 13+ / API 33+) bergaya iOS 26 Dark Liquid Glass:
 * - Tanpa kabut putih susu (0 inner glow / 0 fresnel fog) sehingga bagian dalam kaca bersih & tajam.
 * - Menggunakan profil lengkungan busur lingkaran (`1 - sqrt(1 - t^2)`) khusus di bibir tepi kaca (`uBevelWidth`),
 *   sehingga bagian tengah rata & jernih sementara bibir tepinya membelokkan cahaya secara tajam + dispersi prisma R/G/B.
 * - Saat diam (`uActiveLens = 0`): Smoked Dark Glass kalem dengan kompresi luma agar kontras ikon putih 100% terjaga.
 * - Saat disentuh/di-drag (`uActiveLens -> 1`): Membuka menjadi lensa cair bening yang membiaskan konten di bawahnya.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader content;
uniform float2 uCenter;
uniform float2 uHalfSize;
uniform float2 uBufSize;
uniform float uBevelWidth;
uniform float uRefraction;
uniform float uDispersion;
uniform float uActiveLens;
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

    // Profil meniskus busur lingkaran (circular-arc lens): rata di tengah, melengkung tajam di bibir tepi kaca
    float bevelW = max(min(uBevelWidth, capRadius * 0.85), 1.0);
    float t = 1.0 - clamp(inset / bevelW, 0.0, 1.0);
    float lensFactor = 1.0 - sqrt(max(1.0 - t * t, 0.0001));

    // Magnifikasi kubah halus saat lensa aktif di-drag + pembiasan tepi tajam
    float domeZoom = (1.0 - lensFactor) * (0.10 * uActiveLens);
    float2 baseOffset = (-normal2D * (uRefraction * lensFactor)) - (p * domeZoom);

    // Dispersi kromatik tipis (efek prisma R/G/B khusus di bibir lengkungan kaca)
    float2 dispVec = normal2D * (uDispersion * lensFactor);

    float2 minB = float2(1.0, 1.0);
    float2 maxB = max(uBufSize - float2(1.0, 1.0), minB);
    half4 sR = content.eval(clamp(fragCoord + baseOffset + dispVec, minB, maxB));
    half4 sG = content.eval(clamp(fragCoord + baseOffset, minB, maxB));
    half4 sB = content.eval(clamp(fragCoord + baseOffset - dispVec, minB, maxB));

    float3 sampleRgb = float3(
        float(sR.r) / max(float(sR.a), 0.001),
        float(sG.g) / max(float(sG.a), 0.001),
        float(sB.b) / max(float(sB.a), 0.001)
    );

    // Saat diam, tahan sorotan latar yang terlalu terang agar tidak pernah belang di belakang ikon putih
    float maxLumaRest = mix(0.95, 0.40, uIsDark);
    float3 calmRgb = min(sampleRgb, float3(maxLumaRest, maxLumaRest, maxLumaRest));
    float3 lensRgb = sampleRgb * mix(1.02, 1.08, uIsDark);
    float3 baseBackdrop = mix(calmRgb, lensRgb, uActiveLens);

    // Smoked Dark Glass kalem saat diam (#2B2C31 @ 76%) -> membuka jadi lensa jernih (@ 26%) saat di-drag
    float3 darkTint = mix(float3(0.170, 0.175, 0.195), float3(0.130, 0.140, 0.160), uActiveLens);
    float3 lightTint = float3(0.95, 0.97, 1.0);
    float3 glassTint = mix(lightTint, darkTint, uIsDark);

    float darkAlpha = mix(0.76, 0.26, uActiveLens);
    float lightAlpha = mix(0.70, 0.28, uActiveLens);
    float tintAlpha = mix(lightAlpha, darkAlpha, uIsDark);

    float3 col = mix(baseBackdrop, glassTint, tintAlpha);

    // Gradasi vertikal super halus untuk kedalaman kaca tanpa kabut putih
    float topToBottom = clamp((-p.y / capRadius) * 0.5 + 0.5, 0.0, 1.0);
    col += float3(1.0, 1.0, 1.0) * (topToBottom * (0.028 + 0.018 * uActiveLens));

    col = clamp(col, float3(0.0, 0.0, 0.0), float3(1.0, 1.0, 1.0));
    return half4(half3(col * mask), half(mask));
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LiquidGlassApi33Holder {
    private val shader = RuntimeShader(LIQUID_GLASS_AGSL)

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
        activeLens: Float,
        isDark: Boolean,
    ): RenderEffect {
        shader.setFloatUniform("uCenter", centerX, centerY)
        shader.setFloatUniform("uHalfSize", halfW, halfH)
        shader.setFloatUniform("uBufSize", bufW, bufH)
        shader.setFloatUniform("uBevelWidth", bevelWidthPx)
        shader.setFloatUniform("uRefraction", refractionPx)
        shader.setFloatUniform("uDispersion", dispersionPx)
        shader.setFloatUniform("uActiveLens", activeLens)
        shader.setFloatUniform("uIsDark", if (isDark) 1f else 0f)
        return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

private class LiquidGlassPillCache {
    var lastSourceRev: Int = -1
    var lastRelX: Int = Int.MIN_VALUE
    var lastRelY: Int = Int.MIN_VALUE
    var lastBufW: Int = -1
    var lastBufH: Int = -1
    var lastHadBackdrop: Boolean = false
    var lastIsDark: Boolean = true
    var clearRecordedForCurrentSource: Boolean = false
    var lastCompositedLens: Float = Float.NaN

    var lastCenterX: Float = Float.NaN
    var lastCenterY: Float = Float.NaN
    var lastHalfW: Float = Float.NaN
    var lastHalfH: Float = Float.NaN
    var lastActiveLens: Float = Float.NaN
}

/**
 * Menggambar kapsul indikator aktif bergaya **Dark Liquid Glass (iOS 26)**:
 * - **Saat diam**: Smoked Dark Glass kalem dengan garis kilau vektor setipis rambut (`0.8.dp`) tanpa kabut putih.
 * - **Saat di-drag / meluncur**: Bertransformasi mulus menjadi lensa tetesan kaca bening (`activeLens -> 1f`)
 *   dengan pembiasan busur lingkaran tajam (`18.dp`) & dispersi prisma di bibir kaca.
 */
@Composable
internal fun Modifier.liquidGlassPill(
    baseWidth: Dp,
    baseHeight: Dp,
    isDark: Boolean = true,
    rootPositionProvider: (() -> Offset)? = null,
    subPixelOffsetX: () -> Float = { 0f },
    swellProvider: () -> Float = { 0f },
    activeLensProvider: () -> Float = swellProvider,
    stretchProvider: () -> Float = { 0f },
    velocityTiltProvider: () -> Float = { 0f },
): Modifier {
    val backdrop = LocalFrostedGlassBackdrop.current
    val calmBlurLayer = rememberGraphicsLayer()
    val clearLensLayer = rememberGraphicsLayer()
    val upscaledSourceLayer = rememberGraphicsLayer()
    val lensLayer = rememberGraphicsLayer()

    val cache = remember(calmBlurLayer, clearLensLayer, upscaledSourceLayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            calmBlurLayer.renderEffect = SharedPillRestBlurEffect
            calmBlurLayer.colorFilter = SharedPillRestVibrancy
            clearLensLayer.renderEffect = SharedPillLensBlurEffect
            clearLensLayer.colorFilter = SharedPillLensVibrancy
            upscaledSourceLayer.compositingStrategy = CompositingStrategy.Offscreen
        }
        LiquidGlassPillCache()
    }
    val clipPath = remember { Path() }
    var fallbackOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    val api33Holder = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LiquidGlassApi33Holder()
        } else null
    }

    return this
        .onGloballyPositioned { coords ->
            fallbackOriginInRoot = coords.positionInRoot()
        }
        .drawBehind {
            val boxW = size.width
            val boxH = size.height
            if (boxW <= 0f || boxH <= 0f) return@drawBehind

            val swell = swellProvider().coerceIn(0f, 1f)
            val activeLens = activeLensProvider().coerceIn(0f, 1f)
            val stretch = stretchProvider().coerceIn(0f, 0.28f)
            val tilt = velocityTiltProvider().coerceIn(-1f, 1f)
            val subX = subPixelOffsetX().coerceIn(-1f, 1f)

            val minMarginPx = 1.dp.toPx()
            val rawW = baseWidth.toPx() * (1f + stretch) + (6.dp.toPx() * swell)
            val rawH = baseHeight.toPx() * (1f - stretch * 0.22f) + (5.dp.toPx() * swell)
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

            val pillRect = Rect(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
            clipPath.reset()
            clipPath.addRoundRect(RoundRect(pillRect, cornerRadius))

            // 1. Bayangan kontak halus di luar siluet kapsul (0 bocor ke bagian dalam kaca)
            val shadowDropY = (1.dp + 1.5.dp * swell).toPx()
            val shadowExpand = (1.dp + 1.5.dp * swell).toPx()
            val shadowBaseAlpha = if (isDark) (0.18f + 0.12f * activeLens) else (0.09f + 0.06f * activeLens)
            clipPath(clipPath, clipOp = ClipOp.Difference) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = shadowBaseAlpha * 0.55f),
                    topLeft = Offset(pillLeft - shadowExpand, pillTop + shadowDropY - shadowExpand * 0.25f),
                    size = Size(pillW + shadowExpand * 2f, pillH + shadowExpand * 1.5f),
                    cornerRadius = CornerRadius(radius + shadowExpand, radius + shadowExpand)
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = shadowBaseAlpha),
                    topLeft = Offset(pillLeft, pillTop + shadowDropY * 0.70f),
                    size = Size(pillW, pillH),
                    cornerRadius = cornerRadius
                )
            }

            val originInRoot = rootPositionProvider?.invoke() ?: fallbackOriginInRoot
            val hasBackdrop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                backdrop != null &&
                backdrop.contentSize.width > 0 &&
                backdrop.contentSize.height > 0

            val bufW = boxW.roundToInt().coerceAtLeast(1)
            val bufH = boxH.roundToInt().coerceAtLeast(1)

            // 2. Rekam backdrop: calmBlurLayer (selalu lembut saat diam) + clearLensLayer (jernih hanya saat activeLens > 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val relX = if (backdrop != null) (originInRoot.x - backdrop.contentOriginInRoot.x).roundToInt() else 0
                val relY = if (backdrop != null) (originInRoot.y - backdrop.contentOriginInRoot.y).roundToInt() else 0
                val srcRev = backdrop?.sourceRevision ?: 0

                val sourceChanged = srcRev != cache.lastSourceRev ||
                    relX != cache.lastRelX ||
                    relY != cache.lastRelY ||
                    bufW != cache.lastBufW ||
                    bufH != cache.lastBufH ||
                    hasBackdrop != cache.lastHadBackdrop ||
                    isDark != cache.lastIsDark

                val baseFill = if (isDark) Color(0xFF141414) else Color(0xFFF2F2F2)
                val calmScaledW = (bufW * PILL_REST_BLUR_SCALE).roundToInt().coerceAtLeast(1)
                val calmScaledH = (bufH * PILL_REST_BLUR_SCALE).roundToInt().coerceAtLeast(1)
                val sxCalm = calmScaledW.toFloat() / bufW.toFloat()
                val syCalm = calmScaledH.toFloat() / bufH.toFloat()

                val clearScaledW = (bufW * PILL_LENS_BLUR_SCALE).roundToInt().coerceAtLeast(1)
                val clearScaledH = (bufH * PILL_LENS_BLUR_SCALE).roundToInt().coerceAtLeast(1)
                val sxClear = clearScaledW.toFloat() / bufW.toFloat()
                val syClear = clearScaledH.toFloat() / bufH.toFloat()

                if (sourceChanged) {
                    cache.lastSourceRev = srcRev
                    cache.lastRelX = relX
                    cache.lastRelY = relY
                    cache.lastBufW = bufW
                    cache.lastBufH = bufH
                    cache.lastHadBackdrop = hasBackdrop
                    cache.lastIsDark = isDark
                    cache.clearRecordedForCurrentSource = false
                    cache.lastCompositedLens = Float.NaN

                    calmBlurLayer.record(IntSize(calmScaledW, calmScaledH)) {
                        drawRect(color = baseFill)
                        if (hasBackdrop && backdrop != null) {
                            scale(sxCalm, syCalm, pivot = Offset.Zero) {
                                translate(left = -relX.toFloat(), top = -relY.toFloat()) {
                                    drawLayer(backdrop.contentLayer)
                                }
                            }
                        }
                    }
                }

                val needClearLayer = activeLens > 0.005f
                if (needClearLayer && !cache.clearRecordedForCurrentSource) {
                    cache.clearRecordedForCurrentSource = true
                    cache.lastCompositedLens = Float.NaN

                    clearLensLayer.record(IntSize(clearScaledW, clearScaledH)) {
                        drawRect(color = baseFill)
                        if (hasBackdrop && backdrop != null) {
                            scale(sxClear, syClear, pivot = Offset.Zero) {
                                translate(left = -relX.toFloat(), top = -relY.toFloat()) {
                                    drawLayer(backdrop.contentLayer)
                                }
                            }
                        }
                    }
                }

                val compositeChanged = cache.lastCompositedLens.isNaN() ||
                    abs(activeLens - cache.lastCompositedLens) > 0.005f

                if (compositeChanged) {
                    cache.lastCompositedLens = activeLens
                    cache.lastCenterX = Float.NaN

                    upscaledSourceLayer.record(IntSize(bufW, bufH)) {
                        scale(1f / sxCalm, 1f / syCalm, pivot = Offset.Zero) {
                            drawLayer(calmBlurLayer)
                        }
                        if (needClearLayer) {
                            clearLensLayer.alpha = activeLens
                            scale(1f / sxClear, 1f / syClear, pivot = Offset.Zero) {
                                drawLayer(clearLensLayer)
                            }
                        }
                    }
                }
            }

            // 3A. Badan Kaca & Pembiasan Lensa: Android 13+ (AGSL RuntimeShader)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && api33Holder != null) {
                val uniformsChanged = cache.lastCenterX.isNaN() ||
                    abs(centerX - cache.lastCenterX) > 0.05f ||
                    abs(centerY - cache.lastCenterY) > 0.05f ||
                    abs(halfW - cache.lastHalfW) > 0.05f ||
                    abs(halfH - cache.lastHalfH) > 0.05f ||
                    abs(activeLens - cache.lastActiveLens) > 0.003f

                if (uniformsChanged) {
                    cache.lastCenterX = centerX
                    cache.lastCenterY = centerY
                    cache.lastHalfW = halfW
                    cache.lastHalfH = halfH
                    cache.lastActiveLens = activeLens

                    val bevelPx = (5.dp + 3.5.dp * activeLens).toPx()
                    val refractionPx = (3.5.dp + 14.5.dp * activeLens).toPx()
                    val dispersionPx = (0.35.dp + 2.45.dp * activeLens).toPx()

                    lensLayer.renderEffect = api33Holder.createRenderEffect(
                        centerX = centerX,
                        centerY = centerY,
                        halfW = halfW,
                        halfH = halfH,
                        bufW = bufW.toFloat(),
                        bufH = bufH.toFloat(),
                        bevelWidthPx = bevelPx,
                        refractionPx = refractionPx,
                        dispersionPx = dispersionPx,
                        activeLens = activeLens,
                        isDark = isDark
                    )
                    lensLayer.record(IntSize(bufW, bufH)) {
                        drawLayer(upscaledSourceLayer)
                    }
                }

                drawLayer(lensLayer)
            } else {
                // 3B. Fallback Badan Kaca Android 8–12 (API 26–32)
                clipPath(clipPath) {
                    if (hasBackdrop) {
                        drawLayer(upscaledSourceLayer)
                    }
                    val darkAlphaTop = if (hasBackdrop) (0.74f - 0.36f * activeLens) else 0.84f
                    val darkAlphaBottom = if (hasBackdrop) (0.78f - 0.38f * activeLens) else 0.88f
                    val fillTop = if (isDark) Color(0xFF303238).copy(alpha = darkAlphaTop) else Color.White.copy(alpha = 0.78f)
                    val fillBottom = if (isDark) Color(0xFF25262B).copy(alpha = darkAlphaBottom) else Color(0xFFF0F4FA).copy(alpha = 0.72f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(fillTop, fillBottom),
                            startY = pillTop,
                            endY = pillTop + pillH
                        ),
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillW, pillH)
                    )
                }
            }

            // 4. Garis Kilau Tepi Vektor Setipis Rambut (0.8.dp) ala iOS 26 — tajam tanpa kabut putih!
            val rimStroke = 0.8.dp.toPx()
            val shiftX = tilt * halfW * 0.55f
            val specTopLeft = if (isDark) {
                Color.White.copy(alpha = 0.36f + 0.28f * activeLens)
            } else {
                Color.White.copy(alpha = 0.80f)
            }
            val specMiddle = if (isDark) {
                Color.White.copy(alpha = 0.04f + 0.04f * activeLens)
            } else {
                Color.Black.copy(alpha = 0.06f)
            }
            val specBottomRight = if (isDark) {
                Color(0xFFE0EEFF).copy(alpha = 0.15f + 0.18f * activeLens)
            } else {
                Color.White.copy(alpha = 0.45f)
            }

            drawRoundRect(
                brush = Brush.linearGradient(
                    0.0f to specTopLeft,
                    0.45f to specMiddle,
                    0.62f to specMiddle,
                    1.0f to specBottomRight,
                    start = Offset(pillLeft + shiftX, pillTop),
                    end = Offset(pillLeft + pillW - shiftX, pillTop + pillH)
                ),
                topLeft = Offset(pillLeft + rimStroke * 0.5f, pillTop + rimStroke * 0.5f),
                size = Size(max(1f, pillW - rimStroke), max(1f, pillH - rimStroke)),
                cornerRadius = cornerRadius,
                style = Stroke(width = rimStroke),
                blendMode = if (isDark) BlendMode.Plus else BlendMode.SrcOver
            )
        }
}
