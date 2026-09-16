package com.wayne.musicdeck.utils

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.wayne.musicdeck.R
import java.util.concurrent.Executors

object ThemeHelper {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME = "key_theme"
    private const val KEY_WALLPAPER_SEED_COLOR = "key_wallpaper_seed_color"

    const val THEME_DYNAMIC = "dynamic"
    const val THEME_VIOLET = "violet"
    const val THEME_OCEAN = "ocean"
    const val THEME_ROSE = "rose"
    const val THEME_NEON = "neon"
    const val THEME_AMBER = "amber"
    const val THEME_SKY = "sky"
    const val THEME_SUNSET = "sunset"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class ThemeItem(
        val id: String,
        val name: String,
        val colorResId: Int,
        val isDynamic: Boolean = false,
        var customColor: Int? = null
    )

    /**
     * Dynamic wallpaper extraction is supported on Android 8.1+ (API 27+)
     * via WallpaperColors or direct Palette extraction.
     */
    fun isDynamicSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

    fun getAvailableThemes(): List<ThemeItem> {
        val list = mutableListOf<ThemeItem>()
        if (isDynamicSupported()) {
            list.add(ThemeItem(THEME_DYNAMIC, "Wallpaper Match", R.color.colorPrimaryUnified, isDynamic = true))
        }
        list.add(ThemeItem(THEME_VIOLET, "Vibrant Violet", R.color.colorViolet))
        list.add(ThemeItem(THEME_OCEAN, "Ocean Cyan", R.color.colorOcean))
        list.add(ThemeItem(THEME_ROSE, "Neon Rose", R.color.colorRose))
        list.add(ThemeItem(THEME_NEON, "Emerald Mint", R.color.colorNeon))
        list.add(ThemeItem(THEME_AMBER, "Solar Amber", R.color.colorAmber))
        list.add(ThemeItem(THEME_SKY, "Electric Sky", R.color.colorSky))
        list.add(ThemeItem(THEME_SUNSET, "Sunset Coral", R.color.colorSunset))
        return list
    }

    fun saveTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_VIOLET) ?: THEME_VIOLET
    }

    fun isDynamicTheme(context: Context): Boolean {
        return getTheme(context) == THEME_DYNAMIC && isDynamicSupported()
    }

    /**
     * Retrieves the cached wallpaper seed color or extracts it synchronously if not yet cached.
     */
    fun getWallpaperSeedColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getInt(KEY_WALLPAPER_SEED_COLOR, 0)
        if (saved != 0) return saved

        val extracted = extractWallpaperColor(context)
        prefs.edit().putInt(KEY_WALLPAPER_SEED_COLOR, extracted).apply()
        return extracted
    }

    fun saveWallpaperSeedColor(context: Context, color: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_WALLPAPER_SEED_COLOR, color).apply()
    }

    /**
     * Asynchronously samples the wallpaper, tunes the color for AMOLED contrast,
     * caches it, and returns the result to the caller on the Main thread.
     */
    fun refreshWallpaperColorAsync(context: Context, onExtracted: (Int) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val color = extractWallpaperColor(appContext)
            saveWallpaperSeedColor(appContext, color)
            mainHandler.post {
                onExtracted(color)
            }
        }
    }

    /**
     * Direct Wallpaper Palette Extraction (Universal across OEM skins).
     * 1. First tries WallpaperColors API (API 27+) with zero storage permissions.
     * 2. Falls back to WallpaperManager.drawable thumbnail sampled with AndroidX Palette.
     * 3. Fallback to default brand Violet.
     */
    fun extractWallpaperColor(context: Context): Int {
        // Strategy 1: System WallpaperColors API (API 27+) - Zero permissions needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val wm = context.getSystemService(Context.WALLPAPER_SERVICE) as? WallpaperManager
                val colors = wm?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                if (colors != null) {
                    val primary = colors.primaryColor.toArgb()
                    val secondary = colors.secondaryColor?.toArgb()
                    val chosen = if (secondary != null && isMoreVibrant(secondary, primary)) {
                        secondary
                    } else {
                        primary
                    }
                    if (chosen != 0) {
                        return tuneForDarkTheme(chosen)
                    }
                }
            } catch (e: Exception) {
                // Ignore and fall back to drawable palette
            }
        }

        // Strategy 2: Direct Palette extraction from Wallpaper Drawable thumbnail
        try {
            val wm = context.getSystemService(Context.WALLPAPER_SERVICE) as? WallpaperManager
            val drawable = wm?.drawable
            if (drawable != null) {
                val width = 64
                val height = 64
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)

                val palette = Palette.from(bitmap).generate()
                val candidate = palette.vibrantSwatch?.rgb
                    ?: palette.lightVibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: palette.darkVibrantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb

                bitmap.recycle()
                if (candidate != null && candidate != 0) {
                    return tuneForDarkTheme(candidate)
                }
            }
        } catch (e: Exception) {
            // Ignore security or memory exceptions
        }

        // Strategy 3: Standard fallback
        return ContextCompat.getColor(context, R.color.colorViolet)
    }

    /**
     * Perceptual HSL Contrast Harmonizer.
     * Ensures wallpaper colors pop vibrantly and remain 100% legible against
     * MusicDeck's pitch-black AMOLED (#000000) background.
     */
    fun tuneForDarkTheme(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)

        // If almost monochrome, transform into modern high-contrast silver/frost
        if (hsl[1] < 0.15f) {
            hsl[1] = 0.0f
            hsl[2] = 0.88f
            return ColorUtils.HSLToColor(hsl)
        }

        // Boost saturation for vivid, crisp playback accent
        if (hsl[1] < 0.50f) {
            hsl[1] = 0.55f
        }

        // Clamp lightness for optimal AMOLED readability (never too dark, never washed out)
        if (hsl[2] < 0.56f) {
            hsl[2] = 0.62f
        } else if (hsl[2] > 0.78f) {
            hsl[2] = 0.74f
        }

        return ColorUtils.HSLToColor(hsl)
    }

    private fun isMoreVibrant(c1: Int, c2: Int): Boolean {
        val hsl1 = FloatArray(3)
        val hsl2 = FloatArray(3)
        ColorUtils.colorToHSL(c1, hsl1)
        ColorUtils.colorToHSL(c2, hsl2)
        val score1 = hsl1[1] * (1f - Math.abs(hsl1[2] - 0.6f))
        val score2 = hsl2[1] * (1f - Math.abs(hsl2[2] - 0.6f))
        return score1 > score2
    }

    fun applyTheme(activity: Activity) {
        val themeSlug = getTheme(activity)
        activity.setTheme(getThemeResId(themeSlug))

        if (isDynamicTheme(activity)) {
            val seedColor = getWallpaperSeedColor(activity)
            try {
                val seedBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(seedColor)
                }
                val options = DynamicColorsOptions.Builder()
                    .setContentBasedSource(seedBitmap)
                    .build()
                DynamicColors.applyToActivityIfAvailable(activity, options)
            } catch (e: Exception) {
                // Fallback to standard dynamic colors if content-based seeding is unsupported
                DynamicColors.applyToActivityIfAvailable(activity)
            }
        }
    }

    fun getThemeResId(theme: String): Int {
        return when (theme) {
            THEME_OCEAN -> R.style.Theme_Musicdeck_Ocean
            THEME_ROSE -> R.style.Theme_Musicdeck_Rose
            THEME_NEON -> R.style.Theme_Musicdeck_Neon
            THEME_AMBER -> R.style.Theme_Musicdeck_Amber
            THEME_SKY -> R.style.Theme_Musicdeck_Sky
            THEME_SUNSET -> R.style.Theme_Musicdeck_Sunset
            THEME_DYNAMIC -> R.style.Theme_Musicdeck
            else -> R.style.Theme_Musicdeck_Violet
        }
    }
}
