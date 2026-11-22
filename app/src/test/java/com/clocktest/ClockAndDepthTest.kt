package com.clocktest

import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ClockAndDepthTest {
    @Test
    fun formatsLockScreenDateAndTime() {
        val sample = LocalDateTime.of(2026, 11, 21, 9, 41)
        assertEquals("Sat Nov 21", formatLockDate(sample, Locale.US))
        assertEquals("9:41", formatLockTime(sample, Locale.US))
    }

    @Test
    fun computesCenterCropRectForWallpaperAndRefraction() {
        val exact = computeCropDestRect(1080, 2160, 1080f, 2160f)
        assertEquals(0f, exact.left, 0.01f)
        assertEquals(0f, exact.top, 0.01f)
        assertEquals(1080f, exact.width(), 0.01f)

        // Landscape photo cropped into portrait screen centers horizontally
        val crop = computeCropDestRect(2000, 1000, 1000f, 2000f)
        assertEquals(-1500f, crop.left, 0.01f)
        assertEquals(0f, crop.top, 0.01f)
        assertEquals(4000f, crop.width(), 0.01f)
        assertEquals(2000f, crop.height(), 0.01f)

        // Panning to +1 shifts image to align left edge at 0 without exposing black bars
        val pannedRight = computeCropDestRect(2000, 1000, 1000f, 2000f, extraScale = 1f, offsetXFraction = 1f)
        assertEquals(0f, pannedRight.left, 0.01f)

        // Zooming 2x allows vertical panning within clamped bounds
        val zoomed = computeCropDestRect(1000, 2000, 1000f, 2000f, extraScale = 2f, offsetYFraction = -1f)
        assertEquals(-2000f, zoomed.top, 0.01f)
        assertEquals(2000f, zoomed.bottom, 0.01f)

        // Zooming out below 1x allows repositioning a wide photo vertically inside the extended canvas
        val zoomedOut = computeCropDestRect(2000, 1000, 1000f, 2000f, extraScale = 0.5f, offsetYFraction = 1f)
        assertEquals(1000f, zoomedOut.top, 0.01f)
        assertEquals(2000f, zoomedOut.bottom, 0.01f)
    }

    @Test
    fun scalesOversizedImagesDownToMaxEdge() {
        assertEquals(1f, computeScaleFactor(1080, 1600, 1600), 0.0001f)
        assertEquals(0.5f, computeScaleFactor(3200, 2400, 1600), 0.0001f)
        assertEquals(1f, computeScaleFactor(0, 0, 1600), 0.0001f)
    }

    @Test
    fun fitsWideFourDigitTimesWithinScreenWidth() {
        assertEquals(1f, computeFitScale(800f, 950f), 0.0001f)
        assertEquals(0.75f, computeFitScale(1200f, 900f), 0.0001f)
        assertEquals(1f, computeFitScale(0f, 900f), 0.0001f)
        assertEquals(0.5f, computeBoxFitScale(1000f, 600f, 800f, 300f), 0.0001f)
    }

    @Test
    fun liftsWallpaperColorIntoBrightClockTint() {
        val lifted = liftToLuminousTint(20, 60, 120)
        val r = (lifted shr 16) and 0xFF
        val g = (lifted shr 8) and 0xFF
        val b = lifted and 0xFF
        assertEquals(0xFF, (lifted ushr 24) and 0xFF)
        assertEquals(true, b > g && g > r && r >= 140)
    }

    @Test
    fun parsesSavedEnumSafelyWithFallback() {
        assertEquals(ClockFontPreset.SERIF, ClockConfigStore.parseEnum("SERIF", ClockFontPreset.SYSTEM))
        assertEquals(ClockFontPreset.SYSTEM, ClockConfigStore.parseEnum("UNKNOWN_FONT", ClockFontPreset.SYSTEM))
        assertEquals(ClockMaterialStyle.VIBRANT, ClockConfigStore.parseEnum(null, ClockMaterialStyle.VIBRANT))
    }

    @Test
    fun resolvesVariableFontWeightWithinPresetBounds() {
        assertEquals(100, ClockFontPreset.SYSTEM.resolveWeight(0f))
        assertEquals(null, ClockFontPreset.SYSTEM.variationAxisString(700))
        assertEquals(200, ClockFontPreset.ROUNDED.resolveWeight(0f))
        assertEquals(900, ClockFontPreset.ROUNDED.resolveWeight(1f))
        assertEquals(300, ClockFontPreset.SOFT.resolveWeight(0f))
        assertEquals(450, ClockFontPreset.TALL.resolveWeight(0.5f))
        assertEquals(400, ClockFontPreset.SERIF.resolveWeight(-0.2f))
        assertEquals(700, ClockFontPreset.MODERN.resolveWeight(1.5f))
    }
}

