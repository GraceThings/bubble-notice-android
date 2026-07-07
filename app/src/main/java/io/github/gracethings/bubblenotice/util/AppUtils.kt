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
    private const val KEY_LAUNCH_MODE = "launch_mode"
    private const val KEY_FREEFORM_WIDTH_RATIO = "freeform_width_ratio"
    private const val KEY_FREEFORM_HEIGHT_RATIO = "freeform_height_ratio"

    // 读取当前启动模式 / Read current launch mode (split or freeform).
    fun getLaunchMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAUNCH_MODE, "split") ?: "split"
    }

    // 保存当前启动模式 / Save launch mode.
    fun setLaunchMode(context: Context, mode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAUNCH_MODE, mode).apply()
    }

    fun getFreeformWidthRatio(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FREEFORM_WIDTH_RATIO, 60)
    }

    fun setFreeformWidthRatio(context: Context, ratio: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FREEFORM_WIDTH_RATIO, ratio).apply()
    }

    fun getFreeformHeightRatio(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FREEFORM_HEIGHT_RATIO, 60)
    }

    fun setFreeformHeightRatio(context: Context, ratio: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_FREEFORM_HEIGHT_RATIO, ratio).apply()
    }

    // 临时拉起目标状态 / Time-limited auto-launch target state.
    private var pendingTargetPkg: String? = null
    private var targetExpiryTime: Long = 0L

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

    fun setAutoLaunchTarget(pkg: String, validDurationMs: Long = 10000L) {
        pendingTargetPkg = pkg
        // 记录过期时间 / Record the expiry timestamp.
        targetExpiryTime = System.currentTimeMillis() + validDurationMs
    }

    fun consumeAutoLaunchTarget(): String? {
        val current = System.currentTimeMillis()
        val target = pendingTargetPkg
        // 读取后立即清空 / Clear the one-shot target immediately after reading.
        pendingTargetPkg = null

        // 仅返回未过期目标 / Return the target only while it is still valid.
        return if (target != null && current <= targetExpiryTime) {
            target
        } else {
            null
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

    // 检查使用情况权限 / Check the "View app usage" permission.
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // 检查真实前台应用 / Check whether the target app is the real foreground app.
    fun isAppInForeground(context: Context, targetPackage: String): Boolean {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()

        // 拉长检测窗口到 2 小时 / Extend the detection window to 2 hours.
        val startTime = endTime - 1000L * 60 * 60 * 2

        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        var currentApp: String? = null
        var previousApp: String? = null

        // 追踪应用切换轨迹 / Track the package switch trail.
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            // 页面进入前台 / ACTIVITY_RESUMED means an activity moved foreground.
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                // 仅在包名变化时更新 / Update only when the package actually changes.
                if (currentApp != event.packageName) {
                    previousApp = currentApp
                    currentApp = event.packageName
                }
            }
        }

        val realForegroundApp = if (currentApp == context.packageName) previousApp else currentApp

        return realForegroundApp == targetPackage
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
