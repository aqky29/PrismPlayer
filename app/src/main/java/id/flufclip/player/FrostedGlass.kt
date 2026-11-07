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
import kotlin.math.hypot
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
// LIQUID / FLUID GLASS PILL (AGSL RuntimeShader + Fluid Droplet Optics)
// ============================================================================

private const val LIQUID_PRE_BLUR_SCALE = 0.65f

private val SharedLiquidVibrancyFilter: ColorFilter by lazy {
    val cm = ColorMatrix().apply { setSaturation(1.48f) }
    ColorMatrixColorFilter(cm).asComposeColorFilter()
}

private val SharedLiquidPreBlurEffect: RenderEffect? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Blur jauh lebih tipis daripada kapsul luar (Frosted) agar area di bawah pill tampak seperti tetesan kaca jernih
        val radiusPx = 11f * LIQUID_PRE_BLUR_SCALE
        AndroidRenderEffect.createBlurEffect(
            radiusPx,
            radiusPx,
            Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
    } else null
}

/**
 * Shader AGSL (Android 13+ / API 33+) untuk efek Liquid Glass sejati pada kapsul aktif:
 * - Menghitung Capsule Signed Distance Field (SDF) & vektor normal lengkungan meniskus 2D/3D.
 * - Membelokkan (refraction) piksel backdrop di sekitar bibir kaca cembung + magnifikasi halus di kubah tengah.
 * - Memisahkan tipis saluran R/G/B (chromatic dispersion / efek prisma) di tepi lengkungan.
 * - Menambahkan kilau Fresnel 3D serta pantulan spekular kembar (kiri-atas utama & pantulan balik kanan-bawah)
 *   yang miring secara dinamis mengikuti kecepatan geser pill.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader content;
uniform float2 uCenter;
uniform float2 uHalfSize;
uniform float2 uBufSize;
uniform float uRefraction;
uniform float uDispersion;
uniform float uSwell;
uniform float uVelocityTilt;
uniform float uIsDark;

half4 main(float2 fragCoord) {
    float capRadius = max(min(uHalfSize.x, uHalfSize.y), 1.0);
    float segHalfX = max(uHalfSize.x - capRadius, 0.0);

    float2 p = fragCoord - uCenter;
    float2 q = float2(clamp(p.x, -segHalfX, segHalfX), 0.0);
    float2 dVec = p - q;
    float dist = length(dVec);
    float sdf = dist - capRadius;

    float mask = 1.0 - smoothstep(-0.85, 0.65, sdf);
    if (mask <= 0.001) {
        return half4(0.0, 0.0, 0.0, 0.0);
    }

    float2 normal2D = (dist > 0.0001) ? (dVec / dist) : float2(0.0, -1.0);
    float inset = max(-sdf, 0.0);
    float normDepth = clamp(inset / capRadius, 0.0, 1.0);

    // Profil lengkungan meniskus (bevel) tetesan kaca cembung
    float edgeT = 1.0 - smoothstep(0.0, 0.50, normDepth);
    float bevelCurve = edgeT * edgeT;

    // Pembiasan kubah tengah (magnifikasi halus) + pembiasan tepi meniskus (Snell's law)
    float domeZoom = (1.0 - bevelCurve) * (0.09 + 0.14 * uSwell);
    float refractAmt = uRefraction * (1.0 + 0.36 * uSwell) * bevelCurve;
    float2 baseOffset = (-normal2D * refractAmt) - (p * domeZoom);

    // Dispersi kromatik tipis (efek prisma R/G/B di lengkungan tepi kaca)
    float2 dispVec = normal2D * (uDispersion * (1.0 + 0.42 * uSwell) * bevelCurve);

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

    // Badan kristal jernih: lebih terang & hidup dibanding dock frosted buram di sekelilingnya
    float3 liftedBackdrop = refracted * mix(1.03, 1.16, uIsDark);
    float3 glassTint = mix(float3(0.96, 0.98, 1.0), float3(0.28, 0.30, 0.35), uIsDark);
    float tintAlpha = mix(0.32, 0.25 + 0.05 * uSwell, uIsDark);
    float3 col = mix(liftedBackdrop, glassTint, tintAlpha);

    // Efek Fresnel di sekeliling lengkungan kaca (ketebalan optik 3D)
    float fresnel = bevelCurve * edgeT;
    col += float3(0.80, 0.88, 1.0) * (fresnel * (0.18 + 0.08 * uSwell));

    // Kilau kubah atas (wet dome sheen) & bayangan ketebalan bawah
    float topGrad = clamp((-p.y / capRadius) * 0.5 + 0.5, 0.0, 1.0);
    float domeSheen = topGrad * topGrad * (1.0 - edgeT * 0.45);
    col += float3(1.0, 1.0, 1.0) * (domeSheen * (0.085 + 0.04 * uSwell));

    float bottomDepth = clamp(p.y / capRadius, 0.0, 1.0) * (1.0 - edgeT);
    col *= (1.0 - bottomDepth * 0.08);

    // Pantulan spekular arah cahaya dinamis (kiri-atas utama + pantulan balik kanan-bawah)
    float2 rawL1 = float2(-0.46 + uVelocityTilt * 0.38, -0.89);
    float2 light1 = rawL1 / length(rawL1);
    float2 rawL2 = float2(0.50 - uVelocityTilt * 0.30, 0.86);
    float2 light2 = rawL2 / length(rawL2);

    float spec1 = max(dot(normal2D, light1), 0.0);
    float spec2 = max(dot(normal2D, light2), 0.0);
    float spec1Pow = spec1 * spec1 * spec1;
    float spec2Pow = spec2 * spec2 * spec2 * spec2;

    float outerRim = 1.0 - smoothstep(0.0, 2.5, inset);
    float innerGlint = smoothstep(0.0, 1.6, inset) * (1.0 - smoothstep(1.6, 6.2, inset));

    col += float3(1.0, 1.0, 1.0) * (outerRim * (0.19 + 0.48 * spec1Pow) + innerGlint * (0.15 * spec1Pow));
    col += float3(0.85, 0.93, 1.0) * (outerRim * (0.28 * spec2Pow) + bevelCurve * (0.07 * spec2Pow));

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
        refractionPx: Float,
        dispersionPx: Float,
        swell: Float,
        velocityTilt: Float,
        isDark: Boolean,
    ): RenderEffect {
        shader.setFloatUniform("uCenter", centerX, centerY)
        shader.setFloatUniform("uHalfSize", halfW, halfH)
        shader.setFloatUniform("uBufSize", bufW, bufH)
        shader.setFloatUniform("uRefraction", refractionPx)
        shader.setFloatUniform("uDispersion", dispersionPx)
        shader.setFloatUniform("uSwell", swell)
        shader.setFloatUniform("uVelocityTilt", velocityTilt)
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

    var lastCenterX: Float = Float.NaN
    var lastCenterY: Float = Float.NaN
    var lastHalfW: Float = Float.NaN
    var lastHalfH: Float = Float.NaN
    var lastSwell: Float = Float.NaN
    var lastTilt: Float = Float.NaN
}

/**
 * Menggambar kapsul indikator aktif bergaya **Liquid / Fluid Glass**:
 * - **API 33+ (Android 13+)**: Lensa AGSL `RuntimeShader` dengan pembiasan tepi cembung, dispersi prisma R/G/B,
 *   kilau Fresnel, dan pantulan spekular dinamis di atas backdrop jernih ber-vibrancy tinggi.
 * - **API 31–32 (Android 12)**: Potongan backdrop ber-vibrancy ringan + gradasi lensa & spekular kembar Canvas.
 * - **API < 31**: Permukaan kristal translusen dengan gradasi kubah & highlight spekular dinamis.
 * - Seluruh perubahan bentuk cair (`swell`, `stretch`, `subPixelOffsetX`, `velocityTilt`) dievaluasi di fase Draw
 *   tanpa memicu Recomposition atau realokasi ukuran `GraphicsLayer`.
 */
@Composable
internal fun Modifier.liquidGlassPill(
    baseWidth: Dp,
    baseHeight: Dp,
    isDark: Boolean = true,
    rootPositionProvider: (() -> Offset)? = null,
    subPixelOffsetX: () -> Float = { 0f },
    swellProvider: () -> Float = { 0f },
    stretchProvider: () -> Float = { 0f },
    velocityTiltProvider: () -> Float = { 0f },
): Modifier {
    val backdrop = LocalFrostedGlassBackdrop.current
    val preBlurLayer = rememberGraphicsLayer()
    val upscaledSourceLayer = rememberGraphicsLayer()
    val lensLayer = rememberGraphicsLayer()

    val cache = remember(preBlurLayer, upscaledSourceLayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            preBlurLayer.renderEffect = SharedLiquidPreBlurEffect
            preBlurLayer.colorFilter = SharedLiquidVibrancyFilter
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
            val stretch = stretchProvider().coerceIn(0f, 0.30f)
            val tilt = velocityTiltProvider().coerceIn(-1f, 1f)
            val subX = subPixelOffsetX().coerceIn(-1f, 1f)

            val minMarginPx = 2.dp.toPx()
            val rawW = baseWidth.toPx() * (1f + stretch) + (8.dp.toPx() * swell)
            val rawH = baseHeight.toPx() * (1f - stretch * 0.28f) + (5.dp.toPx() * swell)
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

            // 1. Bayangan kontak lembut di luar siluet kapsul agar pill tampak mengambang di atas dock buram
            val shadowDropY = (1.5.dp + 1.5.dp * swell).toPx()
            val shadowExpand = (1.5.dp + 1.5.dp * swell).toPx()
            val shadowBaseAlpha = if (isDark) (0.26f + 0.10f * swell) else (0.12f + 0.06f * swell)
            clipPath(clipPath, clipOp = ClipOp.Difference) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = shadowBaseAlpha * 0.50f),
                    topLeft = Offset(pillLeft - shadowExpand, pillTop + shadowDropY - shadowExpand * 0.3f),
                    size = Size(pillW + shadowExpand * 2f, pillH + shadowExpand * 1.6f),
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

            // Rekam sumber backdrop ber-vibrancy ringan bila posisi atau isi layar berubah
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

                if (sourceChanged) {
                    cache.lastSourceRev = srcRev
                    cache.lastRelX = relX
                    cache.lastRelY = relY
                    cache.lastBufW = bufW
                    cache.lastBufH = bufH
                    cache.lastHadBackdrop = hasBackdrop
                    cache.lastIsDark = isDark
                    // Paksa lensLayer memperbarui hasil saat sumber berubah
                    cache.lastCenterX = Float.NaN

                    val scaledW = (bufW * LIQUID_PRE_BLUR_SCALE).roundToInt().coerceAtLeast(1)
                    val scaledH = (bufH * LIQUID_PRE_BLUR_SCALE).roundToInt().coerceAtLeast(1)
                    val sx = scaledW.toFloat() / bufW.toFloat()
                    val sy = scaledH.toFloat() / bufH.toFloat()
                    val baseFill = if (isDark) Color(0xFF141414) else Color(0xFFF2F2F2)

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
                    upscaledSourceLayer.record(IntSize(bufW, bufH)) {
                        scale(1f / sx, 1f / sy, pivot = Offset.Zero) {
                            drawLayer(preBlurLayer)
                        }
                    }
                }
            }

            // 2A. Jalur Utama Android 13+ (API 33+): Lensa AGSL RuntimeShader penuh
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && api33Holder != null) {
                val uniformsChanged = cache.lastCenterX.isNaN() ||
                    abs(centerX - cache.lastCenterX) > 0.05f ||
                    abs(centerY - cache.lastCenterY) > 0.05f ||
                    abs(halfW - cache.lastHalfW) > 0.05f ||
                    abs(halfH - cache.lastHalfH) > 0.05f ||
                    abs(swell - cache.lastSwell) > 0.002f ||
                    abs(tilt - cache.lastTilt) > 0.004f

                if (uniformsChanged) {
                    cache.lastCenterX = centerX
                    cache.lastCenterY = centerY
                    cache.lastHalfW = halfW
                    cache.lastHalfH = halfH
                    cache.lastSwell = swell
                    cache.lastTilt = tilt

                    lensLayer.renderEffect = api33Holder.createRenderEffect(
                        centerX = centerX,
                        centerY = centerY,
                        halfW = halfW,
                        halfH = halfH,
                        bufW = bufW.toFloat(),
                        bufH = bufH.toFloat(),
                        refractionPx = 13.dp.toPx(),
                        dispersionPx = 2.2.dp.toPx(),
                        swell = swell,
                        velocityTilt = tilt,
                        isDark = isDark
                    )
                    lensLayer.record(IntSize(bufW, bufH)) {
                        drawLayer(upscaledSourceLayer)
                    }
                }

                drawLayer(lensLayer)
                return@drawBehind
            }

            // 2B. Fallback Android 8–12 (API 26–32): Klip kapsul dinamis + kubah kristal + spekular kembar miring
            clipPath(clipPath) {
                if (hasBackdrop) {
                    drawLayer(upscaledSourceLayer)
                }
                val domeTop = if (isDark) {
                    Color(0xFF687285).copy(alpha = if (hasBackdrop) 0.34f + 0.06f * swell else 0.56f)
                } else {
                    Color.White.copy(alpha = if (hasBackdrop) 0.55f else 0.88f)
                }
                val domeBottom = if (isDark) {
                    Color(0xFF2B2F38).copy(alpha = if (hasBackdrop) 0.28f else 0.48f)
                } else {
                    Color(0xFFE8EEF7).copy(alpha = if (hasBackdrop) 0.38f else 0.78f)
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(domeTop, domeBottom),
                        startY = pillTop,
                        endY = pillTop + pillH
                    ),
                    topLeft = Offset(pillLeft, pillTop),
                    size = Size(pillW, pillH)
                )
            }

            // Rim spekular kembar (kiri-atas terang -> tengah tipis -> kanan-bawah pantulan balik)
            val rimStroke = 1.2.dp.toPx()
            val shiftX = tilt * halfW * 0.45f
            val specPrimary = Color.White.copy(alpha = if (isDark) 0.62f + 0.12f * swell else 0.85f)
            val specMid = Color.White.copy(alpha = if (isDark) 0.10f else 0.25f)
            val specBounce = Color(0xFFD8ECFF).copy(alpha = if (isDark) 0.34f + 0.08f * swell else 0.55f)
            val diagLen = max(hypot(pillW, pillH), 1f)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(specPrimary, specMid, specBounce),
                    start = Offset(pillLeft + shiftX, pillTop),
                    end = Offset(pillLeft + pillW - shiftX, pillTop + pillH + diagLen * 0.05f)
                ),
                topLeft = Offset(pillLeft + rimStroke * 0.5f, pillTop + rimStroke * 0.5f),
                size = Size(max(1f, pillW - rimStroke), max(1f, pillH - rimStroke)),
                cornerRadius = cornerRadius,
                style = Stroke(width = rimStroke),
                blendMode = if (isDark) BlendMode.Plus else BlendMode.SrcOver
            )
        }
}
