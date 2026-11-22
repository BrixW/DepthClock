package com.clocktest

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ClockStyleConfig(
    val font: ClockFontPreset = ClockFontPreset.SYSTEM,
    val weightFraction: Float = ClockFontPreset.SYSTEM.defaultWeightFraction,
    val clockSizeSp: Float = 128f,
    val stretchY: Float = 1.0f,
    val clockTopFraction: Float = 0.11f,
    val materialStyle: ClockMaterialStyle = ClockMaterialStyle.LIQUID,
    val tintPreset: ClockTintPreset = ClockTintPreset.CLEAR,
    val glassIntensity: Float = 0.65f,
    val depthEnabled: Boolean = true,
    val showDate: Boolean = true,
    val photoZoom: Float = 1.0f,
    val photoOffsetX: Float = 0f,
    val photoOffsetY: Float = 0f,
)

object ClockConfigStore {
    private const val PREFS_NAME = "depth_clock_prefs"
    private const val KEY_FONT = "font"
    private const val KEY_WEIGHT = "weight"
    private const val KEY_SIZE = "size"
    private const val KEY_STRETCH = "stretch"
    private const val KEY_TOP = "top"
    private const val KEY_STYLE = "style"
    private const val KEY_TINT = "tint"
    private const val KEY_INTENSITY = "intensity"
    private const val KEY_DEPTH = "depth"
    private const val KEY_SHOW_DATE = "show_date"
    private const val KEY_PHOTO_ZOOM = "photo_zoom"
    private const val KEY_PHOTO_OFFSET_X = "photo_offset_x"
    private const val KEY_PHOTO_OFFSET_Y = "photo_offset_y"
    private const val KEY_APPLIED_FLAGS = "applied_flags"
    private const val KEY_STATUS_BAR_DP = "status_bar_dp"
    private const val KEY_WALLPAPER_VERSION = "wallpaper_version"

    private const val BG_FILE = "wallpaper_bg.png"
    private const val FG_FILE = "wallpaper_fg.png"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAppliedFlags(context: Context): Int = prefs(context).getInt(KEY_APPLIED_FLAGS, 0)

    fun saveAppliedFlags(context: Context, flags: Int, statusBarTopDp: Float? = null) {
        val editor = prefs(context).edit().putInt(KEY_APPLIED_FLAGS, flags)
        if (statusBarTopDp != null && statusBarTopDp > 0f) {
            editor.putFloat(KEY_STATUS_BAR_DP, statusBarTopDp)
        }
        editor.apply()
    }

    fun loadStatusBarTopDp(context: Context): Float = prefs(context).getFloat(KEY_STATUS_BAR_DP, 28f)

    fun loadWallpaperVersion(context: Context): Long = prefs(context).getLong(KEY_WALLPAPER_VERSION, 0L)

    fun loadConfig(context: Context): ClockStyleConfig {
        val p = prefs(context)
        val font = parseEnum(p.getString(KEY_FONT, null), ClockFontPreset.SYSTEM)
        val style = parseEnum(p.getString(KEY_STYLE, null), ClockMaterialStyle.LIQUID)
        val tint = parseEnum(p.getString(KEY_TINT, null), ClockTintPreset.CLEAR)
        return ClockStyleConfig(
            font = font,
            weightFraction = p.getFloat(KEY_WEIGHT, font.defaultWeightFraction).coerceIn(0f, 1f),
            clockSizeSp = p.getFloat(KEY_SIZE, 128f).coerceIn(84f, 160f),
            stretchY = p.getFloat(KEY_STRETCH, 1.0f).coerceIn(1.0f, 2.2f),
            clockTopFraction = p.getFloat(KEY_TOP, 0.11f).coerceIn(0.02f, 0.42f),
            materialStyle = style,
            tintPreset = tint,
            glassIntensity = p.getFloat(KEY_INTENSITY, 0.65f).coerceIn(0f, 1f),
            depthEnabled = p.getBoolean(KEY_DEPTH, true),
            showDate = p.getBoolean(KEY_SHOW_DATE, true),
            photoZoom = p.getFloat(KEY_PHOTO_ZOOM, 1.0f).coerceIn(0.45f, 2.5f),
            photoOffsetX = p.getFloat(KEY_PHOTO_OFFSET_X, 0f).coerceIn(-1f, 1f),
            photoOffsetY = p.getFloat(KEY_PHOTO_OFFSET_Y, 0f).coerceIn(-1f, 1f),
        )
    }

    fun saveConfig(context: Context, config: ClockStyleConfig) {
        prefs(context).edit()
            .putString(KEY_FONT, config.font.name)
            .putFloat(KEY_WEIGHT, config.weightFraction)
            .putFloat(KEY_SIZE, config.clockSizeSp)
            .putFloat(KEY_STRETCH, config.stretchY)
            .putFloat(KEY_TOP, config.clockTopFraction)
            .putString(KEY_STYLE, config.materialStyle.name)
            .putString(KEY_TINT, config.tintPreset.name)
            .putFloat(KEY_INTENSITY, config.glassIntensity)
            .putBoolean(KEY_DEPTH, config.depthEnabled)
            .putBoolean(KEY_SHOW_DATE, config.showDate)
            .putFloat(KEY_PHOTO_ZOOM, config.photoZoom)
            .putFloat(KEY_PHOTO_OFFSET_X, config.photoOffsetX)
            .putFloat(KEY_PHOTO_OFFSET_Y, config.photoOffsetY)
            .apply()
    }

    suspend fun saveWallpaper(context: Context, wallpaper: DepthWallpaper) = withContext(Dispatchers.IO) {
        val bgFile = File(context.filesDir, BG_FILE)
        val fgFile = File(context.filesDir, FG_FILE)

        bgFile.outputStream().use { out ->
            wallpaper.background.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val fg = wallpaper.foreground
        if (fg != null) {
            fgFile.outputStream().use { out ->
                fg.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } else if (fgFile.exists()) {
            fgFile.delete()
        }
        prefs(context).edit().putLong(KEY_WALLPAPER_VERSION, System.currentTimeMillis()).commit()
    }

    fun loadSavedForegroundMask(context: Context): Bitmap? {
        val fgFile = File(context.filesDir, FG_FILE)
        return if (fgFile.exists()) BitmapFactory.decodeFile(fgFile.absolutePath) else null
    }

    fun loadSavedWallpaperOrDemo(context: Context): DepthWallpaper {
        val bgFile = File(context.filesDir, BG_FILE)
        if (!bgFile.exists()) return createDemoWallpaper()
        val bg = BitmapFactory.decodeFile(bgFile.absolutePath) ?: return createDemoWallpaper()
        val fgFile = File(context.filesDir, FG_FILE)
        val fg = if (fgFile.exists()) BitmapFactory.decodeFile(fgFile.absolutePath) else null
        return DepthWallpaper(
            background = bg,
            blurredBackground = createBlurredBitmap(bg),
            foreground = fg,
            accentColorArgb = extractWallpaperAccentArgb(bg),
        )
    }

    internal inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
