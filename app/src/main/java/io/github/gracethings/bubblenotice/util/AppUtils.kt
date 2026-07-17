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
package io.github.gracethings.bubblenotice.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.drawable.toBitmap
import android.os.Process
import androidx.core.content.edit
import io.github.gracethings.bubblenotice.model.AppItem

object AppUtils {
    const val BUBBLE_CHANNEL_SILENT_ID = "bubble_popup_silent_v1"
    const val BUBBLE_CHANNEL_ALERT_ID = "bubble_popup_alert_v1"
    const val BUBBLE_CHANNEL_ID = BUBBLE_CHANNEL_SILENT_ID
    private const val PREFS_NAME = "bubble_prefs"
    private const val KEY_SELECTED_APPS = "selected_apps"
    private const val KEY_PINNED_APPS = "pinned_apps"
    private const val KEY_PIN_TUTORIAL_SHOWN = "pin_tutorial_shown"
    private const val KEY_TAKE_OVER_NOTIFICATIONS = "take_over_notifications"
    private const val KEY_AUTO_JUMP = "auto_jump_enabled"
    private const val KEY_BUBBLE_DND = "bubble_dnd_enabled"

    // 临时拉起目标状?/ One-shot auto-launch target state.
    private var pendingAutoJumpIntent: android.app.PendingIntent? = null

    fun hasShownPinTutorial(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PIN_TUTORIAL_SHOWN, false)
    }

    fun setPinTutorialShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PIN_TUTORIAL_SHOWN, true).apply()
    }

    // 读取已置顶应用包名
    fun getPinnedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PINNED_APPS, emptySet()) ?: emptySet()
    }

    // 保存已置顶应用包名
    fun savePinnedApps(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_PINNED_APPS, packages).apply()
    }

    // 读取已选应用包?/ Read saved selected package names.
    fun getSelectedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
    }

    // 保存已选应用包?/ Save package names selected by the user.
    fun saveSelectedApps(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SELECTED_APPS, packages).apply()
    }

    // 异步加载桌面可启动应?/ Asynchronously load launcher apps.
    suspend fun loadInstalledApps(context: Context): List<AppItem> = withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        val density = context.resources.displayMetrics.densityDpi
        val apps = mutableListOf<AppItem>()

        for (profile in userManager.userProfiles) {
            val isWork = profile != android.os.Process.myUserHandle()
            val activities = launcherApps.getActivityList(null, profile)
            for (activity in activities) {
                apps.add(
                    AppItem(
                        name = activity.label.toString(),
                        packageName = activity.applicationInfo.packageName,
                        icon = activity.getBadgedIcon(density),
                        isWorkProfile = isWork
                    )
                )
            }
        }
        apps.distinctBy { it.packageName + "_" + it.isWorkProfile }.sortedBy { it.name }
    }

    // 按包名获取应用名�?/ Get app name by package name.
    fun getAppName(context: Context, packageName: String): String {
        val pm = context.packageManager
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    // 按包名获取应用图�?Bitmap / Get app icon bitmap by package name.
    fun getAppIconBitmap(context: Context, packageName: String): android.graphics.Bitmap? {
        val pm = context.packageManager
        return try {
            val drawable = pm.getApplicationIcon(packageName)
            drawable.toBitmap(150, 150)
        } catch (e: Exception) {
            null
        }
    }

    fun setPendingAutoJump(intent: android.app.PendingIntent?) {
        pendingAutoJumpIntent = intent
    }

    fun consumePendingAutoJump(): android.app.PendingIntent? {
        val target = pendingAutoJumpIntent
        pendingAutoJumpIntent = null
        return target
    }

    // 安全地触�?PendingIntent，并显式授予后台启动权限 (兼容 Android 14+)
    fun sendPendingIntentAllowed(context: Context, pendingIntent: android.app.PendingIntent) {
        try {
            val options = android.app.ActivityOptions.makeBasic()
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                options.setPendingIntentBackgroundActivityStartMode(
                    android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
            }
            pendingIntent.send(context, 0, null, null, null, null, options.toBundle())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 加载已选应�?/ Load only selected apps.
    suspend fun loadSelectedAppsOnly(context: Context, packageNames: Set<String>): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val result = mutableListOf<AppItem>()

        for (pkg in packageNames) {
            try {
                // 精准读取指定包名 / Read only the requested package info.
                val info = pm.getApplicationInfo(pkg, 0)
                result.add(
                    AppItem(
                        name = pm.getApplicationLabel(info).toString(),
                        packageName = pkg,
                        icon = pm.getApplicationIcon(info)
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // 忽略已卸载应�?/ Ignore packages that no longer exist.
                e.printStackTrace()
            }
        }
        // 按名称排�?/ Return sorted by app name.
        result.sortedBy { it.name }
    }

    // 读取自动跳转开�?/ Read the auto jump toggle.
    fun isAutoJumpEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_JUMP, false)
    }

    // 保存自动跳转开�?/ Save the auto jump toggle.
    fun setAutoJumpEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_AUTO_JUMP, enabled) }
    }

    // 读取气泡免打扰开�?/ Read the bubble DND toggle. Default is false (always popup).
    fun isBubbleDndModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BUBBLE_DND, false)
    }

    // 保存气泡免打扰开�?/ Save the bubble DND toggle.
    fun setBubbleDndModeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_BUBBLE_DND, enabled) }
    }

    // 读取接管通知开�?/ Read the notification takeover toggle.
    fun isTakeOverNotifications(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TAKE_OVER_NOTIFICATIONS, false)
    }

    // 保存接管通知开�?/ Save the notification takeover toggle.
    fun setTakeOverNotifications(context: Context, takeOver: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_TAKE_OVER_NOTIFICATIONS, takeOver) }
    }

}

