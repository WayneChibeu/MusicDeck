package com.wayne.musicdeck.utils

import android.content.Context
import com.wayne.musicdeck.R

object ThemeHelper {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME = "key_theme"

    const val THEME_VIOLET = "violet"
    const val THEME_OCEAN = "ocean"
    const val THEME_ROSE = "rose"
    const val THEME_NEON = "neon"
    const val THEME_AMBER = "amber"
    const val THEME_SKY = "sky"

    fun saveTheme(context: Context, theme: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_VIOLET) ?: THEME_VIOLET
    }
    
    fun getThemeResId(theme: String): Int {
        return when (theme) {
            THEME_OCEAN -> R.style.Theme_Musicdeck_Ocean
            THEME_ROSE -> R.style.Theme_Musicdeck_Rose
            THEME_NEON -> R.style.Theme_Musicdeck_Neon
            THEME_AMBER -> R.style.Theme_Musicdeck_Amber
            THEME_SKY -> R.style.Theme_Musicdeck_Sky
            else -> R.style.Theme_Musicdeck_Violet
        }
    }
}
