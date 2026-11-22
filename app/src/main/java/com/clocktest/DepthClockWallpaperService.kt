package com.clocktest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.view.WindowInsets
import java.time.LocalDateTime
import java.util.concurrent.Executors

/**
 * Pure Canvas Live Wallpaper for Depth Clock:
 * - Draws background, styled clock (with true refracted Liquid Glass), and foreground cutout on one surface.
 * - Zero blink, zero persistent notification, pixel-perfect depth alignment.
 * - Redraws only when visible and when the minute changes or settings are edited.
 */
class DepthClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = DepthClockEngine()

    private inner class DepthClockEngine : Engine() {
        private val ioExecutor = Executors.newSingleThreadExecutor()
        private var config: ClockStyleConfig = ClockStyleConfig()
        private var wallpaper: DepthWallpaper? = null
        private var loadedVersion: Long = -1L
        private var isVisibleSurface: Boolean = false
        private var surfaceReady: Boolean = false
        private var statusBarInsetPx: Float = 0f
        private var receiverRegistered: Boolean = false

        private val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (isVisibleSurface && surfaceReady) drawFrame()
            }
        }

        private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            reloadAndDraw()
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            ClockConfigStore.prefs(applicationContext)
                .registerOnSharedPreferenceChangeListener(prefsListener)
            registerTimeReceiver()
            reloadAndDraw()
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)
            statusBarInsetPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top.toFloat()
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop.toFloat()
            }
            if (isVisibleSurface && surfaceReady) drawFrame()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceReady = true
            if (isVisibleSurface) drawFrame()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (isVisibleSurface) drawFrame()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady = false
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisibleSurface = visible
            if (visible) reloadAndDraw()
        }

        override fun onDestroy() {
            ClockConfigStore.prefs(applicationContext)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
            if (receiverRegistered) {
                runCatching { unregisterReceiver(timeReceiver) }
                receiverRegistered = false
            }
            ioExecutor.shutdownNow()
            super.onDestroy()
        }

        private fun registerTimeReceiver() {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(timeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(timeReceiver, filter)
            }
            receiverRegistered = true
        }

        private fun reloadAndDraw() {
            config = ClockConfigStore.loadConfig(applicationContext)
            val version = ClockConfigStore.loadWallpaperVersion(applicationContext)
            if (wallpaper == null || version != loadedVersion) {
                loadedVersion = version
                ioExecutor.execute {
                    wallpaper = ClockConfigStore.loadSavedWallpaperOrDemo(applicationContext)
                    if (isVisibleSurface && surfaceReady) drawFrame()
                }
            } else if (isVisibleSurface && surfaceReady) {
                drawFrame()
            }
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            if (!surfaceReady) return
            val wp = wallpaper ?: return
            val cfg = config

            val canvas: Canvas = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
            } catch (_: Throwable) {
                runCatching { holder.lockCanvas() }.getOrNull() ?: return
            } ?: return

            try {
                val w = canvas.width.toFloat()
                val h = canvas.height.toFloat()
                if (w <= 0f || h <= 0f) return

                val metrics = resources.displayMetrics
                val density = metrics.density
                val scaledDensity = density * resources.configuration.fontScale
                val topInsetDp = if (statusBarInsetPx > 0f) statusBarInsetPx / density else 28f

                canvas.drawColor(Color.BLACK)

                val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

                // Layer 1: Framed wallpaper background (supports zoom-out with seamless blurred sky extend)
                drawFramedWallpaperBackground(
                    canvas = canvas,
                    wallpaper = wp,
                    canvasW = w,
                    canvasH = h,
                    zoom = cfg.photoZoom,
                    offsetX = cfg.photoOffsetX,
                    offsetY = cfg.photoOffsetY,
                )

                // Layer 2: Date + styled clock (with true refracted Liquid Glass)
                val now = LocalDateTime.now()
                val dateTopDp = topInsetDp + cfg.clockTopFraction * 380f
                val clockTopDp = dateTopDp + if (cfg.showDate) 34f else 0f
                val tintArgb = cfg.tintPreset.resolveArgb(wp.accentColorArgb)

                drawDateAndClockOnCanvas(
                    context = applicationContext,
                    canvas = canvas,
                    canvasW = w,
                    canvasH = h,
                    density = density,
                    scaledDensity = scaledDensity,
                    timeText = formatLockTime(now),
                    dateText = if (cfg.showDate) formatLockDate(now) else null,
                    dateTopOffsetDp = dateTopDp,
                    clockTopOffsetDp = clockTopDp,
                    blurredWallpaper = wp.blurredBackground,
                    preset = cfg.font,
                    weight = cfg.font.resolveWeight(cfg.weightFraction),
                    fontSizeSp = cfg.clockSizeSp,
                    stretchY = cfg.stretchY,
                    materialStyle = cfg.materialStyle,
                    tintArgb = tintArgb,
                    glassIntensity = cfg.glassIntensity,
                    photoZoom = cfg.photoZoom,
                    photoOffsetX = cfg.photoOffsetX,
                    photoOffsetY = cfg.photoOffsetY,
                )

                // Layer 3: Pixel-aligned foreground subject cutout on top of the clock
                val fg = wp.foreground
                if (cfg.depthEnabled && fg != null) {
                    val fgRect = computeCropDestRect(
                        bitmapW = fg.width,
                        bitmapH = fg.height,
                        canvasW = w,
                        canvasH = h,
                        extraScale = cfg.photoZoom,
                        offsetXFraction = cfg.photoOffsetX,
                        offsetYFraction = cfg.photoOffsetY,
                    ).toRectF()
                    canvas.drawBitmap(fg, null, fgRect, bitmapPaint)
                }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}

