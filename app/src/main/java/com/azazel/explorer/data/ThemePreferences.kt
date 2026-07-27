package com.azazel.explorer.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode", enabled).apply()
        applyTheme(enabled)
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean("dark_mode", true) // Default to dark
    }

    fun applyTheme(enabled: Boolean) {
        if (enabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}
