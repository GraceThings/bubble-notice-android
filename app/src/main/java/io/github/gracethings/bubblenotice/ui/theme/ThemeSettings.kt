/*
 * Copyright (C) 2026 Grace Chan <velviagris@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.gracethings.bubblenotice.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ThemeAccent { DEFAULT, OCEAN, FOREST, SUNSET, PLUM }

// Snapshot of appearance preferences used by the app theme and settings previews.
data class AppearanceState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accent: ThemeAccent = ThemeAccent.DEFAULT
)

// Keep the app's visual settings grouped together so both appearance preview and theme can use them.
object ThemeSettings {
    private const val PREFS_NAME = "bubble_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
    private const val KEY_ACCENT = "theme_accent"

    fun getThemeMode(context: Context): ThemeMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun isDynamicColorEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
    }

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun getAccent(context: Context): ThemeAccent {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACCENT, ThemeAccent.DEFAULT.name)
            ?.let { runCatching { ThemeAccent.valueOf(it) }.getOrNull() }
            ?: ThemeAccent.DEFAULT
    }

    fun setAccent(context: Context, accent: ThemeAccent) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCENT, accent.name).apply()
    }

    fun getAppearanceState(context: Context): AppearanceState {
        return AppearanceState(
            themeMode = getThemeMode(context),
            dynamicColor = isDynamicColorEnabled(context),
            accent = getAccent(context)
        )
    }
}

// Material 3 color pairs for pre-set themes.
data class ThemeAccentColors(
    val lightPrimary: Color,
    val lightSecondary: Color,
    val lightTertiary: Color,
    val darkPrimary: Color,
    val darkSecondary: Color,
    val darkTertiary: Color
)

fun ThemeAccent.colorPair(): ThemeAccentColors? = when (this) {
    ThemeAccent.OCEAN -> ThemeAccentColors(
        lightPrimary = Color(0xFF006A60),
        lightSecondary = Color(0xFF4A6366),
        lightTertiary = Color(0xFF50626D),
        darkPrimary = Color(0xFF50D7C7),
        darkSecondary = Color(0xFFA0CED3),
        darkTertiary = Color(0xFFB3C6D9)
    )

    ThemeAccent.FOREST -> ThemeAccentColors(
        lightPrimary = Color(0xFF3D6939),
        lightSecondary = Color(0xFF4F6350),
        lightTertiary = Color(0xFF3C6474),
        darkPrimary = Color(0xFFB1D9AF),
        darkSecondary = Color(0xFFAECBAA),
        darkTertiary = Color(0xFFA4CCD8)
    )

    ThemeAccent.SUNSET -> ThemeAccentColors(
        lightPrimary = Color(0xFF9C4343),
        lightSecondary = Color(0xFF66603C),
        lightTertiary = Color(0xFF7B5D0F),
        darkPrimary = Color(0xFFFFB3B1),
        darkSecondary = Color(0xFFE5DCB9),
        darkTertiary = Color(0xFFE8B94A)
    )

    ThemeAccent.PLUM -> ThemeAccentColors(
        lightPrimary = Color(0xFF6750A4),
        lightSecondary = Color(0xFF625B71),
        lightTertiary = Color(0xFF7D5260),
        darkPrimary = Color(0xFFD0BCFF),
        darkSecondary = Color(0xFFCCC2DC),
        darkTertiary = Color(0xFFEFB8C8)
    )

    ThemeAccent.DEFAULT -> null
}
