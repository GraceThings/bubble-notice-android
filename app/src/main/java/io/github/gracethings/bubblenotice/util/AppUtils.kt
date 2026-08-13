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

import android.content.Context
import android.content.Intent
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
    private const val KEY_EXPERIMENTAL_COLLAPSE = "experimental_collapse_enabled"

    // 临时拉起目标状?/ One-shot auto-launch target state.
    private var pendingAutoJumpIntent: android.app.PendingIntent? = null
    private var pendingAutoJumpTimestamp: Long = 0L
    private var pendingAutoJumpPkgId: String? = null

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
        val raw = prefs.getStringSet(KEY_PINNED_APPS, emptySet()) ?: emptySet()
        return raw.map { if (it.contains(":")) it else "$it:0" }.toSet()
    }

    // 保存已置顶应用包名
    fun savePinnedApps(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_PINNED_APPS, packages).apply()
    }

        // 读取已选应用包?/ Read saved selected package names.
    fun getSelectedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
        return raw.map { if (it.contains(":")) it else "$it:0" }.toSet()
    }

    // 保存已选应用包?/ Save package names selected by the user.
    fun saveSelectedApps(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SELECTED_APPS, packages).apply()
    }

    // 记录已知的通知渠道（作为无权直接查询时的回退方案） / Record known notification channels
    fun addKnownChannel(context: Context, pkgId: String, channelId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "known_channels_$pkgId"
        val currentSet = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentSet.add(channelId)) {
            prefs.edit().putStringSet(key, currentSet).apply()
        }
    }

    fun getKnownChannels(context: Context, pkgId: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("known_channels_$pkgId", emptySet()) ?: emptySet()
    }

    // 获取特定应用被禁用的通知渠道 / Get disabled notification channels for a specific app
    fun getDisabledChannels(context: Context, pkgId: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("disabled_channels_$pkgId", emptySet()) ?: emptySet()
    }

    // 设置特定应用的某个通知渠道是否禁用 / Set whether a specific notification channel is disabled for an app
    fun setChannelDisabled(context: Context, pkgId: String, channelId: String, disabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "disabled_channels_$pkgId"
        val currentSet = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (disabled) {
            currentSet.add(channelId)
        } else {
            currentSet.remove(channelId)
        }
        prefs.edit().putStringSet(key, currentSet).apply()
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

    // 提取 UserHandle 辅助方法 / Extract UserHandle helper
    fun getUserHandle(context: Context, isWork: Boolean): android.os.UserHandle {
        val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        return userManager.userProfiles.firstOrNull { profile ->
            val profileIsWork = profile != android.os.Process.myUserHandle()
            profileIsWork == isWork
        } ?: android.os.Process.myUserHandle()
    }

    // 按包名获取应用名?/ Get app name by package name.
    fun getAppName(context: Context, packageName: String): String {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        
        val parts = packageName.split(":")
        val realPkg = parts[0]
        val isWork = if (parts.size > 1) parts[1] == "1" else false
        
        val targetProfile = userManager.userProfiles.firstOrNull { profile ->
            val profileIsWork = profile != android.os.Process.myUserHandle()
            profileIsWork == isWork
        } ?: android.os.Process.myUserHandle()
        
        return try {
            val activities = launcherApps.getActivityList(realPkg, targetProfile)
            if (activities.isNotEmpty()) {
                activities[0].label.toString()
            } else {
                val info = launcherApps.getApplicationInfo(realPkg, 0, targetProfile)
                context.packageManager.getApplicationLabel(info).toString()
            }
        } catch (e: Exception) {
            realPkg
        }
    }

    // 按包名获取应用图?Bitmap / Get app icon bitmap by package name.
    fun getAppIconBitmap(context: Context, packageName: String): android.graphics.Bitmap? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        val density = context.resources.displayMetrics.densityDpi
        
        val parts = packageName.split(":")
        val realPkg = parts[0]
        val isWork = if (parts.size > 1) parts[1] == "1" else false
        
        val targetProfile = userManager.userProfiles.firstOrNull { profile ->
            val profileIsWork = profile != android.os.Process.myUserHandle()
            profileIsWork == isWork
        } ?: android.os.Process.myUserHandle()

        return try {
            val activities = launcherApps.getActivityList(realPkg, targetProfile)
            if (activities.isNotEmpty()) {
                activities[0].getBadgedIcon(density).toBitmap(150, 150)
            } else {
                val info = launcherApps.getApplicationInfo(realPkg, 0, targetProfile)
                val drawable = context.packageManager.getApplicationIcon(info)
                drawable.toBitmap(150, 150)
            }
        } catch (e: Exception) {
            null
        }
    }
    
    // 启动指定应用 / Launch specific app.
    fun launchApp(context: Context, identifier: String): Boolean {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        
        val parts = identifier.split(":")
        val pkg = parts[0]
        val isWork = if (parts.size > 1) parts[1] == "1" else false
        
        val targetProfile = userManager.userProfiles.firstOrNull { profile ->
            val profileIsWork = profile != android.os.Process.myUserHandle()
            profileIsWork == isWork
        } ?: android.os.Process.myUserHandle()
        
        try {
            val activities = launcherApps.getActivityList(pkg, targetProfile)
            if (activities.isNotEmpty()) {
                launcherApps.startMainActivity(activities[0].componentName, targetProfile, null, null)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    // 自动跳转待处理数据 / Pending auto-jump data
    private var pendingAutoJumpSenderName: String? = null

    fun setPendingAutoJump(intent: android.app.PendingIntent?, pkgId: String?, senderName: String? = null) {
        pendingAutoJumpIntent = intent
        pendingAutoJumpPkgId = pkgId
        pendingAutoJumpSenderName = senderName
        pendingAutoJumpTimestamp = if (intent != null) System.currentTimeMillis() else 0L
    }

    /**
     * 返回 Triple(PendingIntent, pkgId, senderName)
     * Returns Triple(PendingIntent, pkgId, senderName)
     */
    fun consumePendingAutoJump(): Triple<android.app.PendingIntent, String?, String?>? {
        val target = pendingAutoJumpIntent
        val timestamp = pendingAutoJumpTimestamp
        val pkgId = pendingAutoJumpPkgId
        val senderName = pendingAutoJumpSenderName
        pendingAutoJumpIntent = null
        pendingAutoJumpTimestamp = 0L
        pendingAutoJumpPkgId = null
        pendingAutoJumpSenderName = null
        
        // 6000ms 阈值：只在气泡刚刚弹出（flyout 显示阶段）点击时触发自动跳转。
        // 结束后点击气泡本身，将只展开气泡不自动跳转。
        if (target != null && System.currentTimeMillis() - timestamp <= 6000L) {
            return Triple(target, pkgId, senderName)
        }
        return null
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

    // 加载已选应?/ Load only selected apps.
    suspend fun loadSelectedAppsOnly(context: Context, identifiers: Set<String>): List<AppItem> = withContext(Dispatchers.IO) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as android.os.UserManager
        val density = context.resources.displayMetrics.densityDpi
        val result = mutableListOf<AppItem>()

        val profiles = userManager.userProfiles
        for (identifier in identifiers) {
            val parts = identifier.split(":")
            val pkg = parts[0]
            val isWork = if (parts.size > 1) parts[1] == "1" else false

            val targetProfile = profiles.firstOrNull { profile ->
                val profileIsWork = profile != android.os.Process.myUserHandle()
                profileIsWork == isWork
            }

            if (targetProfile != null) {
                try {
                    val activities = launcherApps.getActivityList(pkg, targetProfile)
                    if (activities.isNotEmpty()) {
                        val activity = activities[0]
                        result.add(
                            AppItem(
                                name = activity.label.toString(),
                                packageName = pkg,
                                icon = activity.getBadgedIcon(density),
                                isWorkProfile = isWork
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        result.sortedBy { it.name }
    }

    // 读取自动跳转开?/ Read the auto jump toggle.
    fun isAutoJumpEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_JUMP, false)
    }

    // 保存自动跳转开?/ Save the auto jump toggle.
    fun setAutoJumpEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_AUTO_JUMP, enabled) }
    }

    // 读取实验性气泡折叠开关
    fun isExperimentalCollapseEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EXPERIMENTAL_COLLAPSE, false)
    }

    // 保存实验性气泡折叠开关
    fun setExperimentalCollapseEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_EXPERIMENTAL_COLLAPSE, enabled) }
    }

    // 读取气泡免打扰开?/ Read the bubble DND toggle. Default is false (always popup).
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

