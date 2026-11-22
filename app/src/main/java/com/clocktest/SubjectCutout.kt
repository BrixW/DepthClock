package com.clocktest

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class DepthWallpaper(
    val background: Bitmap,
    val blurredBackground: Bitmap,
    val foreground: Bitmap?,
    val accentColorArgb: Int,
    val statusNote: String? = null,
)

suspend fun segmentWallpaper(context: Context, uri: Uri): DepthWallpaper = withContext(Dispatchers.Default) {
    val source = decodeScaledBitmap(context, uri, maxEdge = 1600)
    val blurred = createBlurredBitmap(source)
    val accent = extractWallpaperAccentArgb(source)
    val options = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .build()
    val segmenter = SubjectSegmentation.getClient(options)
    try {
        val result = segmenter.process(InputImage.fromBitmap(source, 0)).await()
        val cutout = result.foregroundBitmap
        val note = if (cutout == null) "No foreground subject detected" else null
        DepthWallpaper(
            background = source,
            blurredBackground = blurred,
            foreground = cutout,
            accentColorArgb = accent,
            statusNote = note,
        )
    } catch (e: Exception) {
        // shortcut: relies on Play Services ML Kit model download; falls back to flat wallpaper if unavailable.
        DepthWallpaper(
            background = source,
            blurredBackground = blurred,
            foreground = null,
            accentColorArgb = accent,
            statusNote = "Depth cutout unavailable (${e.localizedMessage ?: "ML Kit error"})",
        )
    } finally {
        segmenter.close()
    }
}

// Built-in scenic mountain wallpaper + matching peak cutout so depth occlusion works offline on first launch.
fun createDemoWallpaper(width: Int = 1080, height: Int = 2340): DepthWallpaper {
    val bg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val fg = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    val w = width.toFloat()
    val h = height.toFloat()

    // Sky gradient + distant ridge on background
    val bgCanvas = Canvas(bg)
    val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, h * 0.72f,
            intArrayOf(
                Color.parseColor("#0F2038"),
                Color.parseColor("#2B4C7E"),
                Color.parseColor("#8CA6C8"),
                Color.parseColor("#F0C29C"),
            ),
            floatArrayOf(0f, 0.35f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    bgCanvas.drawRect(0f, 0f, w, h, skyPaint)

    // Soft distant mountain range behind the main peak
    val backRidge = Path().apply {
        moveTo(0f, h * 0.56f)
        lineTo(w * 0.24f, h * 0.43f)
        lineTo(w * 0.48f, h * 0.52f)
        lineTo(w * 0.78f, h * 0.40f)
        lineTo(w, h * 0.50f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    bgCanvas.drawPath(backRidge, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#354F6B")
    })

    // Main foreground peak rising into the clock band (y ~ 22% of screen height)
    val peakPath = Path().apply {
        moveTo(0f, h * 0.62f)
        lineTo(w * 0.18f, h * 0.48f)
        lineTo(w * 0.34f, h * 0.38f)
        lineTo(w * 0.52f, h * 0.21f)
        lineTo(w * 0.68f, h * 0.36f)
        lineTo(w * 0.85f, h * 0.44f)
        lineTo(w, h * 0.54f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, h * 0.21f, 0f, h,
            intArrayOf(
                Color.parseColor("#DCE6F2"),
                Color.parseColor("#3E5066"),
                Color.parseColor("#18212E"),
                Color.parseColor("#0B0F17"),
            ),
            floatArrayOf(0f, 0.24f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    // Snow highlight facet on the left slope of the peak
    val snowFacet = Path().apply {
        moveTo(w * 0.52f, h * 0.21f)
        lineTo(w * 0.34f, h * 0.38f)
        lineTo(w * 0.46f, h * 0.35f)
        lineTo(w * 0.50f, h * 0.29f)
        close()
    }
    val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEF4FA")
    }

    // Draw the peak onto both the full background and the transparent foreground cutout
    listOf(bgCanvas, Canvas(fg)).forEach { canvas ->
        canvas.drawPath(peakPath, peakPaint)
        canvas.drawPath(snowFacet, snowPaint)
    }

    return DepthWallpaper(
        background = bg,
        blurredBackground = createBlurredBitmap(bg),
        foreground = fg,
        accentColorArgb = extractWallpaperAccentArgb(bg),
    )
}

internal fun computeScaleFactor(width: Int, height: Int, maxEdge: Int): Float {
    val largest = maxOf(width, height)
    return if (largest <= maxEdge || largest <= 0) 1f else maxEdge.toFloat() / largest.toFloat()
}

private fun decodeScaledBitmap(context: Context, uri: Uri, maxEdge: Int): Bitmap {
    val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    val scale = computeScaleFactor(raw.width, raw.height, maxEdge)
    if (scale >= 1f) return raw
    val scaled = Bitmap.createScaledBitmap(
        raw,
        (raw.width * scale).toInt().coerceAtLeast(1),
        (raw.height * scale).toInt().coerceAtLeast(1),
        true,
    )
    if (scaled != raw) raw.recycle()
    return scaled
}

