package com.clocktest

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            MaterialTheme {
                LockScreenPrototype()
            }
        }
    }
}

internal fun formatLockDate(now: LocalDateTime, locale: Locale = Locale.getDefault()): String =
    now.format(DateTimeFormatter.ofPattern("EEE MMM d", locale))

internal fun formatLockTime(now: LocalDateTime, locale: Locale = Locale.getDefault()): String =
    now.format(DateTimeFormatter.ofPattern("h:mm", locale))

@Composable
private fun LockScreenPrototype() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val initialConfig = remember { ClockConfigStore.loadConfig(context) }
    var wallpaper by remember { mutableStateOf(createDemoWallpaper()) }
    var depthEnabled by remember { mutableStateOf(initialConfig.depthEnabled) }
    var selectedFont by remember { mutableStateOf(initialConfig.font) }
    var weightFraction by remember { mutableFloatStateOf(initialConfig.weightFraction) }
    var clockSizeSp by remember { mutableFloatStateOf(initialConfig.clockSizeSp) }
    var stretchY by remember { mutableFloatStateOf(initialConfig.stretchY) }
    var clockTopFraction by remember { mutableFloatStateOf(initialConfig.clockTopFraction) }
    var materialStyle by remember { mutableStateOf(initialConfig.materialStyle) }
    var tintPreset by remember { mutableStateOf(initialConfig.tintPreset) }
    var glassIntensity by remember { mutableFloatStateOf(initialConfig.glassIntensity) }
    var showDate by remember { mutableStateOf(initialConfig.showDate) }
    var photoZoom by remember { mutableFloatStateOf(initialConfig.photoZoom) }
    var photoOffsetX by remember { mutableFloatStateOf(initialConfig.photoOffsetX) }
    var photoOffsetY by remember { mutableFloatStateOf(initialConfig.photoOffsetY) }
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Font, 1 = Style, 2 = Photo
    var controlsVisible by remember { mutableStateOf(true) }
    var busyMessage by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    val currentConfig = ClockStyleConfig(
        font = selectedFont,
        weightFraction = weightFraction,
        clockSizeSp = clockSizeSp,
        stretchY = stretchY,
        clockTopFraction = clockTopFraction,
        materialStyle = materialStyle,
        tintPreset = tintPreset,
        glassIntensity = glassIntensity,
        depthEnabled = depthEnabled,
        showDate = showDate,
        photoZoom = photoZoom,
        photoOffsetX = photoOffsetX,
        photoOffsetY = photoOffsetY,
    )

    LaunchedEffect(Unit) {
        wallpaper = withContext(Dispatchers.IO) {
            ClockConfigStore.loadSavedWallpaperOrDemo(context)
        }
    }

    // Live-sync tweaks directly to the active DepthClockWallpaperService.
    LaunchedEffect(currentConfig) {
        ClockConfigStore.saveConfig(context, currentConfig)
    }

    val resolvedWeight = selectedFont.resolveWeight(weightFraction)
    val resolvedTintArgb = tintPreset.resolveArgb(wallpaper.accentColorArgb)
    val dateWeight = selectedFont.resolveWeight(0.58f)
    val dateFontFamily = remember(selectedFont, dateWeight) {
        selectedFont.buildFontFamily(dateWeight)
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000L)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busyMessage = "Cutting out subject…"
                val segmented = segmentWallpaper(context, uri)
                wallpaper = segmented
                photoZoom = 1.0f
                photoOffsetX = 0f
                photoOffsetY = 0f
                ClockConfigStore.saveWallpaper(context, segmented)
                busyMessage = null
            }
        }
    }

    val statusBarTopDp = with(density) { WindowInsets.statusBars.getTop(density).toDp().value }
    val dateTopOffsetDp = clockTopFraction * 380f
    val clockTopOffsetDp = statusBarTopDp + dateTopOffsetDp + if (showDate) 34f else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val nextZoom = (photoZoom * zoomChange).coerceIn(0.45f, 2.5f)
                    photoZoom = nextZoom
                    if (size.width > 0 && size.height > 0) {
                        val sensX = 2.2f / size.width.toFloat()
                        val sensY = 2.2f / size.height.toFloat()
                        photoOffsetX = (photoOffsetX + pan.x * sensX).coerceIn(-1f, 1f)
                        photoOffsetY = (photoOffsetY + pan.y * sensY).coerceIn(-1f, 1f)
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
    ) {
        // Layer 1: Framed wallpaper background (supports zoom-out with seamless blurred sky extend)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawFramedWallpaperBackground(
                canvas = drawContext.canvas.nativeCanvas,
                wallpaper = wallpaper,
                canvasW = size.width,
                canvasH = size.height,
                zoom = photoZoom,
                offsetX = photoOffsetX,
                offsetY = photoOffsetY,
            )
        }

        // Layer 2a: Compact date line harmonized with the selected clock tint
        if (showDate) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Text(
                    text = formatLockDate(now),
                    style = TextStyle(
                        color = Color(resolvedTintArgb).copy(alpha = 0.92f),
                        fontSize = 21.sp,
                        fontWeight = FontWeight(dateWeight),
                        fontFamily = dateFontFamily,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.28f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f,
                        ),
                    ),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = dateTopOffsetDp.dp),
                )
            }
        }

        // Layer 2b: Styled clock (Liquid Glass, Frosted, Vibrant, Solid, Outline)
        LiquidGlassClockLayer(
            timeText = formatLockTime(now),
            blurredWallpaper = wallpaper.blurredBackground,
            preset = selectedFont,
            weight = resolvedWeight,
            fontSizeSp = clockSizeSp,
            stretchY = stretchY,
            topOffsetDp = clockTopOffsetDp,
            materialStyle = materialStyle,
            tintArgb = resolvedTintArgb,
            glassIntensity = glassIntensity,
            photoZoom = photoZoom,
            photoOffsetX = photoOffsetX,
            photoOffsetY = photoOffsetY,
        )

        // Layer 3: Foreground subject cutout drawn above the clock (locked to photo zoom & pan)
        val fg = wallpaper.foreground
        if (depthEnabled && fg != null) {
            TransformedBitmapLayer(
                bitmap = fg,
                zoom = photoZoom,
                offsetX = photoOffsetX,
                offsetY = photoOffsetY,
            )
        }

        busyMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(message, color = Color.White, fontSize = 14.sp)
                }
            }
        }

        // Subtle "Edit Clock" pill when controls are hidden
        AnimatedVisibility(
            visible = !controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Surface(
                onClick = { controlsVisible = true },
                shape = RoundedCornerShape(22.dp),
                color = Color.Black.copy(alpha = 0.56f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.28f)),
            ) {
                Text(
                    text = "Edit Clock",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                )
            }
        }

        // Compact 2-tab customizer sheet (tap anywhere on the photo to hide/show)
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Color.Black.copy(alpha = 0.58f),
                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    wallpaper.statusNote?.let { note ->
                        Text(
                            text = note,
                            color = Color(0xFFFFD180),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                            .padding(3.dp),
                    ) {
                        TabPill("Font", activeTab == 0, { activeTab = 0 }, Modifier.weight(1f))
                        TabPill("Style", activeTab == 1, { activeTab = 1 }, Modifier.weight(1f))
                        TabPill("Photo", activeTab == 2, { activeTab = 2 }, Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(10.dp))

                    when (activeTab) {
                        0 -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            ) {
                                ClockFontPreset.entries.forEach { preset ->
                                    val isSelected = preset == selectedFont
                                    val previewWeight = preset.resolveWeight(preset.defaultWeightFraction)
                                    val previewFamily = remember(preset) { preset.buildFontFamily(previewWeight) }
                                    Surface(
                                        onClick = {
                                            selectedFont = preset
                                            weightFraction = preset.defaultWeightFraction
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (isSelected) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
                                        border = if (isSelected) BorderStroke(1.5.dp, Color.White) else null,
                                        modifier = Modifier.width(66.dp),
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.padding(vertical = 8.dp),
                                        ) {
                                            Text(
                                                text = "12",
                                                color = Color.White,
                                                fontSize = 22.sp,
                                                fontFamily = previewFamily,
                                                fontWeight = FontWeight(previewWeight),
                                            )
                                            Text(
                                                text = preset.label,
                                                color = Color.White.copy(alpha = 0.72f),
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            ControlSliderRow("Stretch", stretchY, { stretchY = it }, 1.0f..2.2f)
                            ControlSliderRow("Weight", weightFraction, { weightFraction = it }, 0f..1f)
                            ControlSliderRow("Size", clockSizeSp, { clockSizeSp = it }, 84f..160f)
                            ControlSliderRow("Height", clockTopFraction, { clockTopFraction = it }, 0.02f..0.42f)
                        }

                        1 -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                ClockMaterialStyle.entries.forEach { style ->
                                    val isSelected = style == materialStyle
                                    Surface(
                                        onClick = { materialStyle = style },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
                                        border = if (isSelected) BorderStroke(1.2.dp, Color.White) else null,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 9.dp),
                                        ) {
                                            Text(
                                                text = style.label,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                ClockTintPreset.entries.forEach { swatch ->
                                    val isSelected = swatch == tintPreset
                                    val swatchColor = swatch.previewColor ?: Color(wallpaper.accentColorArgb)
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Surface(
                                            onClick = { tintPreset = swatch },
                                            shape = CircleShape,
                                            color = swatchColor,
                                            border = BorderStroke(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                                            ),
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            if (swatch == ClockTintPreset.AUTO) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("A", color = Color.Black.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = swatch.label,
                                            color = Color.White.copy(alpha = if (isSelected) 0.95f else 0.6f),
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            ControlSliderRow("Effect", glassIntensity, { glassIntensity = it }, 0f..1f)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Show Date", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                                Switch(
                                    checked = showDate,
                                    onCheckedChange = { showDate = it },
                                )
                            }
                        }

                        else -> {
                            Text(
                                text = "Pinch out below 1.0x to fit wide photos with seamless sky extend:",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                            ControlSliderRow("Zoom", photoZoom, { photoZoom = it }, 0.45f..2.5f)
                            ControlSliderRow("Pan X", photoOffsetX, { photoOffsetX = it }, -1f..1f)
                            ControlSliderRow("Pan Y", photoOffsetY, { photoOffsetY = it }, -1f..1f)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        // Fit full photo width cleanly inside the screen with extended sky above
                                        val bw = wallpaper.background.width.toFloat().coerceAtLeast(1f)
                                        val bh = wallpaper.background.height.toFloat().coerceAtLeast(1f)
                                        val screenW = context.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
                                        val screenH = context.resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
                                        val cropScale = maxOf(screenW / bw, screenH / bh)
                                        val fitWScale = screenW / bw
                                        photoZoom = (fitWScale / cropScale).coerceIn(0.45f, 1.0f)
                                        photoOffsetX = 0f
                                        photoOffsetY = 0.35f
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.20f),
                                        contentColor = Color.White,
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Fit Width", fontSize = 12.sp)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        photoZoom = 1.0f
                                        photoOffsetX = 0f
                                        photoOffsetY = 0f
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.14f),
                                        contentColor = Color.White,
                                    ),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Reset", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.16f),
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text("Photo", fontSize = 13.sp)
                            }

                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        busyMessage = "Preparing wallpaper…"
                                        ClockConfigStore.saveWallpaper(context, wallpaper)
                                        ClockConfigStore.saveConfig(context, currentConfig)
                                        busyMessage = null
                                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                            putExtra(
                                                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                                ComponentName(context, DepthClockWallpaperService::class.java),
                                            )
                                        }
                                        runCatching { context.startActivity(intent) }
                                            .onFailure {
                                                context.startActivity(
                                                    Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
                                                )
                                            }
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.28f),
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text("Set Wallpaper", fontSize = 13.sp)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Depth", color = Color.White, fontSize = 13.sp)
                            Switch(
                                checked = depthEnabled,
                                onCheckedChange = { depthEnabled = it },
                                enabled = wallpaper.foreground != null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformedBitmapLayer(
    bitmap: Bitmap,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    modifier: Modifier = Modifier,
) {
    val paint = remember { AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG) }
    Canvas(modifier = modifier.fillMaxSize()) {
        val rect = computeCropDestRect(
            bitmapW = bitmap.width,
            bitmapH = bitmap.height,
            canvasW = size.width,
            canvasH = size.height,
            extraScale = zoom,
            offsetXFraction = offsetX,
            offsetYFraction = offsetY,
        ).toRectF()
        drawContext.canvas.nativeCanvas.drawBitmap(bitmap, null, rect, paint)
    }
}

@Composable
private fun TabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(11.dp),
        color = if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 7.dp),
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = if (selected) 1f else 0.7f),
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ControlSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            modifier = Modifier.width(48.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier.weight(1f),
        )
    }
}
