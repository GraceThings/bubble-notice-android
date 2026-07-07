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
    private const val KEY_TAKE_OVER_NOTIFICATIONS = "take_over_notifications"
    private const val KEY_AUTO_JUMP = "auto_jump_enabled"

    // 临时拉起目标状态 / One-shot auto-launch target state.
    private var pendingAutoJumpIntent: android.app.PendingIntent? = null

    // 读取已选应用包名 / Read saved selected package names.
    fun getSelectedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SELECTED_APPS, emptySet()) ?: emptySet()
    }

    // 保存已选应用包名 / Save package names selected by the user.
    fun saveSelectedApps(context: Context, packages: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SELECTED_APPS, packages).apply()
    }

    // 异步加载桌面可启动应用 / Asynchronously load launcher apps.
    suspend fun loadInstalledApps(context: Context): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        resolveInfos.map { info ->
            AppItem(
                name = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm)
            )
        }.sortedBy { it.name }
    }

    // 按包名获取应用名称 / Get app name by package name.
    fun getAppName(context: Context, packageName: String): String {
        val pm = context.packageManager
        return try {
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    // 按包名获取应用图标 Bitmap / Get app icon bitmap by package name.
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

    // 安全地触发 PendingIntent，并显式授予后台启动权限 (兼容 Android 14+)
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

    // 加载已选应用 / Load only selected apps.
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
                // 忽略已卸载应用 / Ignore packages that no longer exist.
                e.printStackTrace()
            }
        }
        // 按名称排序 / Return sorted by app name.
        result.sortedBy { it.name }
    }

    // 读取自动跳转开关 / Read the auto jump toggle.
    fun isAutoJumpEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_JUMP, false)
    }

    // 保存自动跳转开关 / Save the auto jump toggle.
    fun setAutoJumpEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_AUTO_JUMP, enabled) }
    }

    // 读取接管通知开关 / Read the notification takeover toggle.
    fun isTakeOverNotifications(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_TAKE_OVER_NOTIFICATIONS, false)
    }

    // 保存接管通知开关 / Save the notification takeover toggle.
    fun setTakeOverNotifications(context: Context, takeOver: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_TAKE_OVER_NOTIFICATIONS, takeOver) }
    }

}
