package com.wayne.musicdeck.utils

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import com.wayne.musicdeck.R

object ThemeHelper {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME = "key_theme"

    const val THEME_DYNAMIC = "dynamic"
    const val THEME_VIOLET = "violet"
    const val THEME_OCEAN = "ocean"
    const val THEME_ROSE = "rose"
    const val THEME_NEON = "neon"
    const val THEME_AMBER = "amber"
    const val THEME_SKY = "sky"
    const val THEME_SUNSET = "sunset"

    data class ThemeItem(
        val id: String,
        val name: String,
        val colorResId: Int,
        val isDynamic: Boolean = false
    )

    fun isDynamicSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun getAvailableThemes(): List<ThemeItem> {
        val list = mutableListOf<ThemeItem>()
        if (isDynamicSupported()) {
            list.add(ThemeItem(THEME_DYNAMIC, "Dynamic (System)", R.color.colorPrimaryUnified, isDynamic = true))
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

    fun applyTheme(activity: Activity) {
        val themeSlug = getTheme(activity)
        activity.setTheme(getThemeResId(themeSlug))
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
