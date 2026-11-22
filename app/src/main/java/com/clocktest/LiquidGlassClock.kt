package com.clocktest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

internal fun computeCropDestRect(
    bitmapW: Int,
    bitmapH: Int,
    canvasW: Float,
    canvasH: Float,
    extraScale: Float = 1f,
    offsetXFraction: Float = 0f,
    offsetYFraction: Float = 0f,
): CropRect {
    if (bitmapW <= 0 || bitmapH <= 0 || canvasW <= 0f || canvasH <= 0f) {
        return CropRect(0f, 0f, canvasW, canvasH)
    }
    val scale = max(canvasW / bitmapW.toFloat(), canvasH / bitmapH.toFloat()) * extraScale
    val drawW = bitmapW * scale
    val drawH = bitmapH * scale
    // Allow panning both when zoomed in (overflow) and zoomed out (repositioning inside the extended canvas).
    val maxPanX = abs(drawW - canvasW) * 0.5f
    val maxPanY = abs(drawH - canvasH) * 0.5f
    val left = (canvasW - drawW) * 0.5f + offsetXFraction.coerceIn(-1f, 1f) * maxPanX
    val top = (canvasH - drawH) * 0.5f + offsetYFraction.coerceIn(-1f, 1f) * maxPanY
    return CropRect(left, top, left + drawW, top + drawH)
}

/**
 * Draws the framed wallpaper background. When zoomed out below 1.0x (e.g., fitting a wide selfie),
 * fills the full screen with a seamless blurred backdrop and softly feathers exposed top/bottom edges
 * just like iOS's Wallpaper Extend effect.
 */
fun drawFramedWallpaperBackground(
    canvas: AndroidCanvas,
    wallpaper: DepthWallpaper,
    canvasW: Float,
    canvasH: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val bgRect = computeCropDestRect(
        bitmapW = wallpaper.background.width,
        bitmapH = wallpaper.background.height,
        canvasW = canvasW,
        canvasH = canvasH,
        extraScale = zoom,
        offsetXFraction = offsetX,
        offsetYFraction = offsetY,
    ).toRectF()

    val needsExtend = bgRect.left > 1f || bgRect.top > 1f || bgRect.right < canvasW - 1f || bgRect.bottom < canvasH - 1f
    if (!needsExtend) {
        canvas.drawBitmap(wallpaper.background, null, bgRect, paint)
        return
    }

    // 1. Full-bleed blurred backdrop matching the photo's sky/colors (zero black bars)
    val backdropRect = computeCropDestRect(
        bitmapW = wallpaper.blurredBackground.width,
        bitmapH = wallpaper.blurredBackground.height,
        canvasW = canvasW,
        canvasH = canvasH,
        extraScale = 1.06f,
    ).toRectF()
    canvas.drawBitmap(wallpaper.blurredBackground, null, backdropRect, paint)

    // 2. Softly feather top/bottom edges that sit inside the screen so the photo melts into the extended sky
    val featherTop = bgRect.top > 1f
    val featherBottom = bgRect.bottom < canvasH - 1f
    if (!featherTop && !featherBottom) {
        canvas.drawBitmap(wallpaper.background, null, bgRect, paint)
        return
    }

    val layerId = canvas.saveLayer(bgRect, null)
    canvas.drawBitmap(wallpaper.background, null, bgRect, paint)
    val fadeTopStop = if (featherTop) 0.14f else 0f
    val fadeBottomStop = if (featherBottom) 0.86f else 1f
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        shader = LinearGradient(
            bgRect.left, bgRect.top, bgRect.left, bgRect.bottom,
            intArrayOf(
                if (featherTop) AndroidColor.TRANSPARENT else AndroidColor.BLACK,
                AndroidColor.BLACK,
                AndroidColor.BLACK,
                if (featherBottom) AndroidColor.TRANSPARENT else AndroidColor.BLACK,
            ),
            floatArrayOf(0f, fadeTopStop, fadeBottomStop, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(bgRect, maskPaint)
    canvas.restoreToCount(layerId)
}

/** Scales content cleanly to fit inside (maxWidth, maxHeight) without ever clipping. */
internal fun computeFitScale(contentWidth: Float, maxWidth: Float): Float {
    if (contentWidth <= 0f || maxWidth <= 0f || contentWidth <= maxWidth) return 1f
    return maxWidth / contentWidth
}

internal fun computeBoxFitScale(
    contentWidth: Float,
    contentHeight: Float,
    maxWidth: Float,
    maxHeight: Float,
): Float {
    val sx = computeFitScale(contentWidth, maxWidth)
    val sy = computeFitScale(contentHeight, maxHeight)
    return minOf(sx, sy)
}

/** Lifts an average RGB color into a bright, pastel-luminous lock screen clock tint while preserving hue. */
internal fun liftToLuminousTint(r: Int, g: Int, b: Int): Int {
    val nr = (140f + r.coerceIn(0, 255) * 0.45f).roundToInt().coerceIn(140, 255)
    val ng = (140f + g.coerceIn(0, 255) * 0.45f).roundToInt().coerceIn(140, 255)
    val nb = (140f + b.coerceIn(0, 255) * 0.45f).roundToInt().coerceIn(140, 255)
    return (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
}

/** Samples the upper 45% of the wallpaper to derive an adaptive iOS-style tint color. */
fun extractWallpaperAccentArgb(source: Bitmap): Int {
    val sample = Bitmap.createScaledBitmap(source, 24, 36, true)
    val maxY = (sample.height * 0.45f).toInt().coerceAtLeast(1)
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0L
    for (y in 0 until maxY) {
        for (x in 0 until sample.width) {
            val c = sample.getPixel(x, y)
            rSum += (c shr 16) and 0xFF
            gSum += (c shr 8) and 0xFF
            bSum += c and 0xFF
            count++
        }
    }
    if (sample != source) sample.recycle()
    if (count == 0L) return AndroidColor.WHITE
    return liftToLuminousTint((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
}

/**
 * Fast, dependency-free frosted blur on a downscaled copy of the wallpaper.
 * Drawn bilinearly scaled up inside clipped glyph paths for smooth Liquid Glass frost.
 */
fun createBlurredBitmap(source: Bitmap, downscale: Int = 5, radius: Int = 6): Bitmap {
    val sw = (source.width / downscale).coerceAtLeast(16)
    val sh = (source.height / downscale).coerceAtLeast(16)
    val small = Bitmap.createScaledBitmap(source, sw, sh, true)
    val pixels = IntArray(sw * sh)
    small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
    val temp = IntArray(sw * sh)

    repeat(3) {
        blurHorizontal(pixels, temp, sw, sh, radius)
        blurVertical(temp, pixels, sw, sh, radius)
    }
    small.setPixels(pixels, 0, sw, 0, 0, sw, sh)
    return small
}

private fun blurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val window = r * 2 + 1
    for (y in 0 until h) {
        val row = y * w
        var rSum = 0
        var gSum = 0
        var bSum = 0
        for (i in -r..r) {
            val c = src[row + i.coerceIn(0, w - 1)]
            rSum += (c shr 16) and 0xFF
            gSum += (c shr 8) and 0xFF
            bSum += c and 0xFF
        }
        for (x in 0 until w) {
            dst[row + x] = (0xFF shl 24) or ((rSum / window) shl 16) or ((gSum / window) shl 8) or (bSum / window)
            val remove = src[row + (x - r).coerceIn(0, w - 1)]
            val add = src[row + (x + r + 1).coerceIn(0, w - 1)]
            rSum += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            gSum += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            bSum += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun blurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val window = r * 2 + 1
    for (x in 0 until w) {
        var rSum = 0
        var gSum = 0
        var bSum = 0
        for (i in -r..r) {
            val c = src[i.coerceIn(0, h - 1) * w + x]
            rSum += (c shr 16) and 0xFF
            gSum += (c shr 8) and 0xFF
            bSum += c and 0xFF
        }
        for (y in 0 until h) {
            dst[y * w + x] = (0xFF shl 24) or ((rSum / window) shl 16) or ((gSum / window) shl 8) or (bSum / window)
            val remove = src[(y - r).coerceIn(0, h - 1) * w + x]
            val add = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
            rSum += ((add shr 16) and 0xFF) - ((remove shr 16) and 0xFF)
            gSum += ((add shr 8) and 0xFF) - ((remove shr 8) and 0xFF)
            bSum += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

@Composable
fun LiquidGlassClockLayer(
    timeText: String,
    blurredWallpaper: Bitmap,
    preset: ClockFontPreset,
    weight: Int,
    fontSizeSp: Float,
    stretchY: Float,
    topOffsetDp: Float,
    materialStyle: ClockMaterialStyle,
    tintArgb: Int,
    glassIntensity: Float,
    photoZoom: Float = 1f,
    photoOffsetX: Float = 0f,
    photoOffsetY: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val typeface = remember(preset, weight) { resolveClockTypeface(context, preset, weight) }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawDateAndClockOnCanvas(
            context = context,
            canvas = drawContext.canvas.nativeCanvas,
            canvasW = size.width,
            canvasH = size.height,
            density = density.density,
            scaledDensity = density.density * density.fontScale,
            timeText = timeText,
            dateText = null,
            dateTopOffsetDp = 0f,
            clockTopOffsetDp = topOffsetDp,
            blurredWallpaper = blurredWallpaper,
            preset = preset,
            weight = weight,
            fontSizeSp = fontSizeSp,
            stretchY = stretchY,
            materialStyle = materialStyle,
            tintArgb = tintArgb,
            glassIntensity = glassIntensity,
            cachedTypeface = typeface,
            photoZoom = photoZoom,
            photoOffsetX = photoOffsetX,
            photoOffsetY = photoOffsetY,
        )
    }
}

internal fun resolveClockTypeface(context: Context, preset: ClockFontPreset, weight: Int): Typeface {
    val resId = preset.fontRes
    return if (resId == null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, weight, false)
        } else {
            if (weight >= 600) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    } else {
        context.resources.getFont(resId)
    }
}

/**
 * Shared native Canvas renderer used by both the Compose editor preview and
 * DepthClockWallpaperService so the live home screen matches the preview 1:1.
 */
fun drawDateAndClockOnCanvas(
    context: Context,
    canvas: AndroidCanvas,
    canvasW: Float,
    canvasH: Float,
    density: Float,
    scaledDensity: Float,
    timeText: String,
    dateText: String?,
    dateTopOffsetDp: Float,
    clockTopOffsetDp: Float,
    blurredWallpaper: Bitmap?,
    preset: ClockFontPreset,
    weight: Int,
    fontSizeSp: Float,
    stretchY: Float,
    materialStyle: ClockMaterialStyle,
    tintArgb: Int,
    glassIntensity: Float,
    cachedTypeface: Typeface? = null,
    centerVerticallyInBox: Boolean = false,
    photoZoom: Float = 1f,
    photoOffsetX: Float = 0f,
    photoOffsetY: Float = 0f,
) {
    val typeface = cachedTypeface ?: resolveClockTypeface(context, preset, weight)
    val fontSizePx = fontSizeSp * preset.sizeMultiplier * scaledDensity
    val rimWidthPx = 1.25f * density
    val innerGlowPx = 6.5f * density
    val shadowOffsetPx = 4.5f * density
    val shadowRadiusPx = 14f * density

    val dateSizePx = 19f * scaledDensity
    val dateGapPx = if (dateText != null) 10f * density else 0f
    val datePaint = if (dateText != null) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val dateWeight = preset.resolveWeight(0.58f)
            this.typeface = resolveClockTypeface(context, preset, dateWeight)
            textSize = dateSizePx
            textAlign = Paint.Align.CENTER
            color = withAlpha(tintArgb, 240)
            preset.variationAxisString(dateWeight)?.let { fontVariationSettings = it }
            setShadowLayer(8f * density, 0f, 2f * density, AndroidColor.argb(95, 0, 0, 0))
        }
    } else {
        null
    }
    val dateHeightPx = if (datePaint != null) {
        val fm = datePaint.fontMetrics
        (fm.descent - fm.ascent)
    } else {
        0f
    }

    val parts = timeText.split(":")
    val hours = parts.getOrElse(0) { timeText }
    val minutes = parts.getOrNull(1)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = fontSizePx
        letterSpacing = preset.letterSpacingEm
        fontFeatureSettings = "tnum"
        preset.variationAxisString(weight)?.let { fontVariationSettings = it }
    }

    val hourPath = Path()
    val minutePath = Path()
    textPaint.getTextPath(hours, 0, hours.length, 0f, 0f, hourPath)
    if (minutes != null) {
        textPaint.getTextPath(minutes, 0, minutes.length, 0f, 0f, minutePath)
    }

    val scaleMatrix = Matrix().apply { setScale(1f, stretchY) }
    hourPath.transform(scaleMatrix)
    minutePath.transform(scaleMatrix)

    val hourBounds = computePathBounds(hourPath)
    val minuteBounds = computePathBounds(minutePath)

    val digitHeight = max(hourBounds.height(), minuteBounds.height()).coerceAtLeast(1f)
    val colonRadius = (fontSizePx * 0.068f).coerceAtLeast(5f)
    val colonGap = fontSizePx * 0.085f
    val colonSlotWidth = if (minutes != null) colonRadius * 2f + colonGap * 2f else 0f

    val rawWidth = hourBounds.width() + colonSlotWidth + minuteBounds.width()
    val safeMarginPx = shadowRadiusPx + rimWidthPx * 2f
    val maxAllowedW = (canvasW - safeMarginPx * 2f).coerceAtLeast(canvasW * 0.84f)
    val maxAllowedH = if (centerVerticallyInBox) {
        (canvasH - safeMarginPx * 2f - dateHeightPx - dateGapPx).coerceAtLeast(1f)
    } else {
        Float.MAX_VALUE
    }

    val rawCombined = Path()
    hourPath.offset(-hourBounds.left, -hourBounds.top)
    rawCombined.addPath(hourPath)

    if (minutes != null) {
        val colonCenterX = hourBounds.width() + colonSlotWidth * 0.5f
        rawCombined.addCircle(colonCenterX, digitHeight * 0.33f, colonRadius, Path.Direction.CW)
        rawCombined.addCircle(colonCenterX, digitHeight * 0.67f, colonRadius, Path.Direction.CW)

        minutePath.offset(hourBounds.width() + colonSlotWidth - minuteBounds.left, -minuteBounds.top)
        rawCombined.addPath(minutePath)
    }

    val rawBounds = computePathBounds(rawCombined)
    val fitScale = if (centerVerticallyInBox) {
        // In widget mode, scale BOTH up or down so the clock fills the widget cleanly with zero clipping.
        minOf(maxAllowedW / rawBounds.width().coerceAtLeast(1f), maxAllowedH / rawBounds.height().coerceAtLeast(1f))
            .coerceIn(0.25f, 3.0f)
    } else {
        computeFitScale(rawWidth, maxAllowedW)
    }

    if (fitScale != 1f) {
        val fitMatrix = Matrix().apply { setScale(fitScale, fitScale) }
        rawCombined.transform(fitMatrix)
    }

    val fittedBounds = computePathBounds(rawCombined)
    val centerOffsetX = (canvasW - fittedBounds.width()) * 0.5f - fittedBounds.left
    val targetClockTopPx = if (centerVerticallyInBox) {
        val totalBlockH = dateHeightPx + dateGapPx + fittedBounds.height()
        val blockTop = ((canvasH - totalBlockH) * 0.5f).coerceAtLeast(safeMarginPx)
        if (dateText != null && datePaint != null) {
            val dateBaselineY = blockTop - datePaint.fontMetrics.ascent
            canvas.drawText(dateText, canvasW * 0.5f, dateBaselineY, datePaint)
        }
        blockTop + dateHeightPx + dateGapPx
    } else {
        if (dateText != null && datePaint != null) {
            val dateBaselineY = dateTopOffsetDp * density - datePaint.fontMetrics.ascent
            canvas.drawText(dateText, canvasW * 0.5f, dateBaselineY, datePaint)
        }
        clockTopOffsetDp * density
    }

    rawCombined.offset(centerOffsetX, targetClockTopPx - fittedBounds.top)

    // Dissolve internal overlapping contours from variable fonts so Outline and glass rims stay clean.
    val combinedPath = simplifyGlyphPath(rawCombined)
    val bounds = computePathBounds(combinedPath)
    val intensity = glassIntensity.coerceIn(0f, 1f)

    when (materialStyle) {
        ClockMaterialStyle.LIQUID -> drawLiquidGlassPath(
            canvas = canvas,
            path = combinedPath,
            bounds = bounds,
            blurredWallpaper = blurredWallpaper,
            canvasW = canvasW,
            canvasH = canvasH,
            tintArgb = tintArgb,
            intensity = intensity,
            rimWidthPx = rimWidthPx,
            innerGlowPx = innerGlowPx,
            shadowOffsetPx = shadowOffsetPx,
            shadowRadiusPx = shadowRadiusPx,
            photoZoom = photoZoom,
            photoOffsetX = photoOffsetX,
            photoOffsetY = photoOffsetY,
        )

        ClockMaterialStyle.FROSTED -> drawFrostedGlassPath(
            canvas = canvas,
            path = combinedPath,
            bounds = bounds,
            blurredWallpaper = blurredWallpaper,
            canvasW = canvasW,
            canvasH = canvasH,
            tintArgb = tintArgb,
            intensity = intensity,
            rimWidthPx = rimWidthPx,
            shadowOffsetPx = shadowOffsetPx,
            shadowRadiusPx = shadowRadiusPx,
            photoZoom = photoZoom,
            photoOffsetX = photoOffsetX,
            photoOffsetY = photoOffsetY,
        )

        ClockMaterialStyle.VIBRANT -> drawVibrantPath(
            canvas = canvas,
            path = combinedPath,
            bounds = bounds,
            tintArgb = tintArgb,
            intensity = intensity,
            shadowOffsetPx = shadowOffsetPx,
            shadowRadiusPx = shadowRadiusPx,
        )

        ClockMaterialStyle.SOLID -> drawSolidPath(
            canvas = canvas,
            path = combinedPath,
            tintArgb = tintArgb,
            shadowOffsetPx = shadowOffsetPx,
            shadowRadiusPx = shadowRadiusPx,
        )

        ClockMaterialStyle.OUTLINE -> drawOutlineGlassPath(
            canvas = canvas,
            path = combinedPath,
            bounds = bounds,
            tintArgb = tintArgb,
            intensity = intensity,
            rimWidthPx = rimWidthPx,
            shadowRadiusPx = shadowRadiusPx,
        )
    }
}

@Suppress("DEPRECATION")
private fun computePathBounds(path: Path): RectF = RectF().also { path.computeBounds(it, true) }

private fun simplifyGlyphPath(raw: Path): Path {
    raw.fillType = Path.FillType.WINDING
    return Path().apply { op(raw, Path.Op.UNION) }
}

private fun withAlpha(argb: Int, alpha: Int): Int =
    (alpha.coerceIn(0, 255) shl 24) or (argb and 0x00FFFFFF)

private val glassLiftColorFilter = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            1.06f, 0f, 0f, 0f, 24f,
            0f, 1.06f, 0f, 0f, 25f,
            0f, 0f, 1.08f, 0f, 28f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private fun drawLiquidGlassPath(
    canvas: AndroidCanvas,
    path: Path,
    bounds: RectF,
    blurredWallpaper: Bitmap?,
    canvasW: Float,
    canvasH: Float,
    tintArgb: Int,
    intensity: Float,
    rimWidthPx: Float,
    innerGlowPx: Float,
    shadowOffsetPx: Float,
    shadowRadiusPx: Float,
    photoZoom: Float = 1f,
    photoOffsetX: Float = 0f,
    photoOffsetY: Float = 0f,
) {
    // 1. Soft ambient drop shadow behind sculpted glass
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(14, 0, 0, 0)
        style = Paint.Style.FILL
        setShadowLayer(shadowRadiusPx, 0f, shadowOffsetPx, AndroidColor.argb(82, 0, 0, 0))
    }
    canvas.drawPath(path, shadowPaint)

    // 2. Clipped optical lens refraction + luminous sheen + 3D meniscus bevel
    canvas.save()
    canvas.clipPath(path)

    if (blurredWallpaper != null) {
        val extraZoom = (1.04f + 0.06f * intensity) * photoZoom
        val refractedRect = computeCropDestRect(
            bitmapW = blurredWallpaper.width,
            bitmapH = blurredWallpaper.height,
            canvasW = canvasW,
            canvasH = canvasH,
            extraScale = extraZoom,
            offsetXFraction = photoOffsetX,
            offsetYFraction = photoOffsetY,
        ).toRectF()
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = glassLiftColorFilter
            alpha = (210 + 35 * intensity).roundToInt()
        }
        canvas.drawBitmap(blurredWallpaper, null, refractedRect, bitmapPaint)
    }

    val topAlpha = (if (blurredWallpaper == null) 130 else 90) + (55 * intensity).roundToInt()
    val midAlpha = (if (blurredWallpaper == null) 58 else 24) + (26 * intensity).roundToInt()
    val bottomAlpha = (if (blurredWallpaper == null) 96 else 56) + (34 * intensity).roundToInt()
    val glassSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(
            bounds.left, bounds.top, bounds.right * 0.85f, bounds.bottom,
            intArrayOf(
                withAlpha(tintArgb, topAlpha),
                withAlpha(tintArgb, midAlpha),
                withAlpha(tintArgb, (midAlpha * 0.6f).roundToInt()),
                withAlpha(tintArgb, bottomAlpha),
            ),
            floatArrayOf(0f, 0.34f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(bounds, glassSheenPaint)

    // Top-left luminous meniscus glow
    val innerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = innerGlowPx * (0.95f + 0.45f * intensity)
        color = withAlpha(tintArgb, (125 + 65 * intensity).roundToInt())
        maskFilter = BlurMaskFilter(innerGlowPx * 0.75f, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.save()
    canvas.translate(innerGlowPx * 0.18f, innerGlowPx * 0.22f)
    canvas.drawPath(path, innerHighlightPaint)
    canvas.restore()

    // Bottom-right subtle dark depth rim
    val innerDepthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = innerGlowPx * 0.85f
        color = AndroidColor.argb((42 + 30 * intensity).roundToInt(), 12, 24, 38)
        maskFilter = BlurMaskFilter(innerGlowPx * 0.65f, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.save()
    canvas.translate(-innerGlowPx * 0.16f, -innerGlowPx * 0.22f)
    canvas.drawPath(path, innerDepthPaint)
    canvas.restore()

    canvas.restore()

    // 3. Razor-thin specular rim with subtle prism highlight
    val specularRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidthPx * (0.85f + 0.4f * intensity)
        strokeJoin = Paint.Join.ROUND
        shader = LinearGradient(
            bounds.left, bounds.top, bounds.right, bounds.bottom,
            intArrayOf(
                AndroidColor.argb(248, 255, 255, 255),
                withAlpha(tintArgb, 125),
                withAlpha(tintArgb, 215),
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawPath(path, specularRimPaint)
}

private fun drawFrostedGlassPath(
    canvas: AndroidCanvas,
    path: Path,
    bounds: RectF,
    blurredWallpaper: Bitmap?,
    canvasW: Float,
    canvasH: Float,
    tintArgb: Int,
    intensity: Float,
    rimWidthPx: Float,
    shadowOffsetPx: Float,
    shadowRadiusPx: Float,
    photoZoom: Float = 1f,
    photoOffsetX: Float = 0f,
    photoOffsetY: Float = 0f,
) {
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(16, 0, 0, 0)
        style = Paint.Style.FILL
        setShadowLayer(shadowRadiusPx, 0f, shadowOffsetPx, AndroidColor.argb(72, 0, 0, 0))
    }
    canvas.drawPath(path, shadowPaint)

    canvas.save()
    canvas.clipPath(path)

    if (blurredWallpaper != null) {
        val cropRect = computeCropDestRect(
            bitmapW = blurredWallpaper.width,
            bitmapH = blurredWallpaper.height,
            canvasW = canvasW,
            canvasH = canvasH,
            extraScale = 1.02f * photoZoom,
            offsetXFraction = photoOffsetX,
            offsetYFraction = photoOffsetY,
        ).toRectF()
        canvas.drawBitmap(blurredWallpaper, null, cropRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    val veilAlpha = (120 + 75 * intensity).roundToInt()
    val frostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(
            bounds.left, bounds.top, bounds.left, bounds.bottom,
            intArrayOf(withAlpha(tintArgb, veilAlpha), withAlpha(tintArgb, (veilAlpha * 0.78f).roundToInt())),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(bounds, frostPaint)
    canvas.restore()

    val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidthPx
        color = withAlpha(tintArgb, 185)
    }
    canvas.drawPath(path, rimPaint)
}

private fun drawVibrantPath(
    canvas: AndroidCanvas,
    path: Path,
    bounds: RectF,
    tintArgb: Int,
    intensity: Float,
    shadowOffsetPx: Float,
    shadowRadiusPx: Float,
) {
    val topAlpha = (145 + 75 * intensity).roundToInt()
    val bottomAlpha = (110 + 70 * intensity).roundToInt()
    val vibrantPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = LinearGradient(
            bounds.left, bounds.top, bounds.left, bounds.bottom,
            intArrayOf(withAlpha(tintArgb, topAlpha), withAlpha(tintArgb, bottomAlpha)),
            null,
            Shader.TileMode.CLAMP,
        )
        setShadowLayer(shadowRadiusPx, 0f, shadowOffsetPx, AndroidColor.argb(76, 0, 0, 0))
    }
    canvas.drawPath(path, vibrantPaint)
}

private fun drawSolidPath(
    canvas: AndroidCanvas,
    path: Path,
    tintArgb: Int,
    shadowOffsetPx: Float,
    shadowRadiusPx: Float,
) {
    val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = withAlpha(tintArgb, 245)
        style = Paint.Style.FILL
        setShadowLayer(shadowRadiusPx, 0f, shadowOffsetPx, AndroidColor.argb(76, 0, 0, 0))
    }
    canvas.drawPath(path, solidPaint)
}

private fun drawOutlineGlassPath(
    canvas: AndroidCanvas,
    path: Path,
    bounds: RectF,
    tintArgb: Int,
    intensity: Float,
    rimWidthPx: Float,
    shadowRadiusPx: Float,
) {
    // Soft tinted neon/glass halo
    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidthPx * (3.2f + 2.4f * intensity)
        strokeJoin = Paint.Join.ROUND
        color = withAlpha(tintArgb, (95 + 65 * intensity).roundToInt())
        maskFilter = BlurMaskFilter(shadowRadiusPx * 0.45f, BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawPath(path, glowPaint)

    // Crisp luminous outer stroke
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = rimWidthPx * (1.8f + 1.2f * intensity)
        strokeJoin = Paint.Join.ROUND
        shader = LinearGradient(
            bounds.left, bounds.top, bounds.right, bounds.bottom,
            intArrayOf(AndroidColor.WHITE, withAlpha(tintArgb, 235), AndroidColor.WHITE),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawPath(path, strokePaint)
}
