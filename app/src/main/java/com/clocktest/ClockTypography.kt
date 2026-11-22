package com.clocktest

import androidx.annotation.FontRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

enum class ClockFontPreset(
    val label: String,
    @FontRes val fontRes: Int?,
    val minWeight: Int,
    val maxWeight: Int,
    val defaultWeightFraction: Float,
    val letterSpacingEm: Float,
    val sizeMultiplier: Float = 1f,
    val opticalSize: Int? = null,
) {
    SYSTEM("System", null, 100, 900, 0.72f, -0.03f, 1.0f),
    CLASSIC("Classic", R.font.inter_var, 100, 900, 0.56f, -0.04f, 1.0f, opticalSize = 32),
    ROUNDED("Rounded", R.font.nunito_var, 200, 900, 0.75f, -0.035f, 1.02f),
    SOFT("Soft", R.font.fredoka_var, 300, 700, 0.72f, -0.025f, 1.04f),
    TALL("Tall", R.font.oswald_var, 200, 700, 0.68f, -0.015f, 1.16f),
    SERIF("Serif", R.font.playfair_var, 400, 900, 0.62f, -0.02f, 0.96f),
    MODERN("Modern", R.font.space_grotesk_var, 300, 700, 0.68f, -0.04f, 1.0f),
    ;

    fun variationAxisString(weight: Int): String? = when {
        fontRes == null -> null
        opticalSize != null -> "'wght' $weight, 'opsz' $opticalSize"
        else -> "'wght' $weight"
    }

    fun resolveWeight(fraction: Float): Int {
        val clamped = fraction.coerceIn(0f, 1f)
        val raw = minWeight + (maxWeight - minWeight) * clamped
        return ((raw / 10f).roundToInt() * 10).coerceIn(minWeight, maxWeight)
    }

    @OptIn(ExperimentalTextApi::class)
    fun buildFontFamily(weight: Int): FontFamily =
        if (fontRes == null) {
            FontFamily.Default
        } else {
            FontFamily(
                Font(
                    resId = fontRes,
                    weight = FontWeight(weight),
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight),
                    ),
                ),
            )
        }
}

enum class ClockMaterialStyle(val label: String) {
    LIQUID("Liquid"),
    FROSTED("Frosted"),
    VIBRANT("Vibrant"),
    SOLID("Solid"),
    OUTLINE("Outline"),
}

enum class ClockTintPreset(val label: String, val previewColor: Color?) {
    CLEAR("Clear", Color(0xFFFFFFFF)),
    AUTO("Auto", null),
    ICE("Ice", Color(0xFFA8E6FF)),
    MINT("Mint", Color(0xFFA9F5D0)),
    GOLD("Gold", Color(0xFFFFD8A3)),
    ROSE("Rose", Color(0xFFFFB8D2)),
    LAVENDER("Lilac", Color(0xFFD6C2FF)),
    ;

    fun resolveArgb(wallpaperAccentArgb: Int): Int =
        previewColor?.toArgb() ?: wallpaperAccentArgb
}
