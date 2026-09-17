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
package io.github.gracethings.bubblenotice

import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

// Multiplies the edge-to-edge behavior without relying on the deprecated Window APIs.

/**
 * Handles the edge-to-edge behavior with the Android 11+ system APIs.
 *
 * This intentionally avoids the deprecations that can be carried into the APK through the
 * androidx.activity helper when it selects its API 28/29/35 branches.
 */
fun ComponentActivity.enableEdgeToEdgeCompat() {
    val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    window.isNavigationBarContrastEnforced = false

    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
    insetsController.isAppearanceLightStatusBars = !isDarkMode
    insetsController.isAppearanceLightNavigationBars = !isDarkMode
}
