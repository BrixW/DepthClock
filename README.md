# Depth Clock (Android)

An Apple-style depth-effect lock/home screen clock for Android built with **Kotlin**, **Jetpack Compose**, **Canvas**, and **Google ML Kit Subject Segmentation**.

## Features

- **3-Layer Depth Effect (`WallpaperService`):** Draws your background photo, styled clock, and ML Kit foreground subject cutout on one hardware-accelerated `Canvas` — pixel-aligned occlusion, zero screen blinking, no persistent notification, and battery-friendly minute-only redraws (`ACTION_TIME_TICK`).
- **On-Device AI Subject Cutout:** Picks any photo via Android's Photo Picker (zero storage permissions required) and segments the foreground subject locally using `play-services-mlkit-subject-segmentation`. Includes an offline procedural mountain demo on first launch.
- **7 Fonts with Variable Weight & Vertical Stretch:**
  - Android **System** default (`Typeface.DEFAULT`)
  - 6 bundled variable `.ttf` fonts: **Classic** (`Inter`), **Rounded** (`Nunito`), **Soft** (`Fredoka`), **Tall** (`Oswald`), **Serif** (`Playfair Display`), and **Modern** (`Space Grotesk`)
  - Automatic `Path.Op.UNION` contour simplification so variable-font outlines and glass rims render cleanly without internal overlap seams, plus circular colon dots that stay round when stretching digits.
- **5 Clock Styles & 7 Color Tints:**
  - **Liquid** (refracted blurred wallpaper clipped inside the digits + sculpted inner meniscus bevel + specular rim)
  - **Frosted**, **Vibrant**, **Solid**, and **Outline**
  - **Clear**, **Auto** (samples the upper sky of your photo into a luminous pastel tint), **Ice**, **Mint**, **Gold**, **Rose**, and **Lilac**
  - Optional **Show Date** toggle (`EEE MMM d`).
- **Photo Framing & Sky Extend:** Pinch-to-zoom (`0.45x–2.5x`) and drag directly on the photo, or use the **Photo** tab (**Zoom**, **Pan X**, **Pan Y**, **Fit Width**, **Reset**). Zooming out below `1.0x` on wide/landscape selfies fills the screen with a color-matched blurred backdrop and softly feathers the photo edges into the sky.
- **Adaptive Icon:** Custom vector adaptive icon (twilight sky + Liquid Glass `"12"` behind a snow-lit peak) with Android 13+ monochrome themed icon support.

## Architecture

| File | Responsibility |
| --- | --- |
| `app/src/main/java/com/clocktest/MainActivity.kt` | Edge-to-edge Jetpack Compose editor, pinch/pan gestures, live preview, and photo picker |
| `app/src/main/java/com/clocktest/DepthClockWallpaperService.kt` | Pure-Canvas `WallpaperService` rendering all 3 layers on minute ticks & config updates |
| `app/src/main/java/com/clocktest/LiquidGlassClock.kt` | Shared Canvas renderer (glyph paths, Liquid Glass refraction, 5 styles, sky-extend framing, blur & tint helpers) |
| `app/src/main/java/com/clocktest/ClockTypography.kt` | Font presets, variable `wght`/`opsz` axes, material styles, and tint swatches |
| `app/src/main/java/com/clocktest/SubjectCutout.kt` | ML Kit Subject Segmentation wrapper + offline demo wallpaper generator |
| `app/src/main/java/com/clocktest/ClockConfigStore.kt` | `SharedPreferences` + internal PNG persistence for instant sync between editor and wallpaper service |
| `app/src/test/java/com/clocktest/ClockAndDepthTest.kt` | Unit tests for date/time formatting, crop/zoom/pan math, tint lifting, weight clamping, and enum parsing |

## Requirements & Build

- **Android:** `minSdk 26` (Android 8.0+), `compileSdk 35`, `targetSdk 35`
- **JDK:** 17+ (tested with JDK 22)
- **Android SDK:** `platforms;android-35` and `build-tools;35.0.0`

```bash
# Run unit tests and build the R8-minified release APK (~2.6 MB)
./gradlew testDebugUnitTest assembleRelease
```

The built APK is output to `app/build/outputs/apk/release/app-release.apk` (and prebuilt APKs are attached to GitHub Releases).

## Tips for Samsung One UI Lock Screen

Android does not let third-party apps hide the system lock screen clock. On Samsung One UI 7+, you can make the default clock invisible in a few taps:

1. Long-press your **Lock screen** → tap the **Clock**.
2. Pick a single-color clock style → open **Color** → tap the custom **color wheel**.
3. Drag **Opacity / Transparency** to **0%** and tap **Done**.

## Notes

- Release builds currently sign with the local debug keystore for easy sideloading; replace `signingConfig` in `app/build.gradle.kts` with a dedicated release keystore before Play Store distribution.
- ML Kit Subject Segmentation downloads its on-device model via Google Play Services on first use.
