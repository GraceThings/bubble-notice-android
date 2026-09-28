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

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import io.github.gracethings.bubblenotice.BuildConfig
import io.github.gracethings.bubblenotice.CrashReportActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object CrashHandler {

    private const val TAG = "CrashHandler"
    private const val CRASH_FILE_NAME = "latest_crash.txt"
    private var isInitialized = false

    fun init(application: Application) {
        if (isInitialized) return
        isInitialized = true

        // 避免在 :crash 独立进程中重复捕获导致死循环
        if (isCrashProcess(application)) {
            return
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                handleException(application, throwable, thread)
            } catch (e: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun isCrashProcess(context: Context): Boolean {
        return try {
            Application.getProcessName().endsWith(":crash")
        } catch (_: Throwable) {
            false
        }
    }

    fun handleException(context: Context, throwable: Throwable, thread: Thread? = null) {
        val targetThread = thread ?: Thread.currentThread()
        val crashReport = buildCrashReport(context, throwable, targetThread)

        // 写入本地持久化日志
        try {
            AppLogger.e(TAG, "Uncaught exception on thread ${targetThread.name}", throwable)
        } catch (_: Throwable) {
        }

        // 保存崩溃详情到缓存文件，防止 Intent 传输数据过大导致 TransactionTooLargeException
        val crashFile = try {
            val file = File(context.cacheDir, CRASH_FILE_NAME)
            file.writeText(crashReport)
            file
        } catch (_: Throwable) {
            null
        }

        // 启动独立的异常弹窗 Activity
        try {
            val intent = Intent(context, CrashReportActivity::class.java).apply {
                putExtra(CrashReportActivity.EXTRA_CRASH_INFO, crashReport)
                if (crashFile != null) {
                    putExtra(CrashReportActivity.EXTRA_CRASH_FILE_PATH, crashFile.absolutePath)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
        }

        // 立即杀死当前崩溃的进程，防止界面僵死或触发系统 ANR 弹窗
        Process.killProcess(Process.myPid())
        exitProcess(10)
    }

    fun buildCrashReport(context: Context, throwable: Throwable, thread: Thread): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val now = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.append("=== Bubble Notice Crash Report ===\n")
        sb.append("Time: ").append(now).append("\n\n")

        sb.append("--- Device Info ---\n")
        sb.append("Brand: ").append(Build.BRAND).append("\n")
        sb.append("Manufacturer: ").append(Build.MANUFACTURER).append("\n")
        sb.append("Model: ").append(Build.MODEL).append("\n")
        sb.append("Product: ").append(Build.PRODUCT).append("\n")
        sb.append("Device: ").append(Build.DEVICE).append("\n")
        sb.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Build Display: ").append(Build.DISPLAY).append("\n")
        sb.append("Supported ABIs: ").append(Build.SUPPORTED_ABIS.joinToString(", ")).append("\n\n")

        sb.append("--- App Info ---\n")
        sb.append("Package: ").append(context.packageName).append("\n")
        sb.append("Version Name: ").append(BuildConfig.VERSION_NAME).append("\n")
        sb.append("Version Code: ").append(BuildConfig.VERSION_CODE).append("\n")
        sb.append("Build Type: ").append(BuildConfig.BUILD_TYPE).append("\n\n")

        sb.append("--- Crash Details ---\n")
        @Suppress("DEPRECATION")
        val threadId = thread.id
        sb.append("Thread: ").append(thread.name).append(" (id: ").append(threadId).append(")\n")
        sb.append("Exception: ").append(throwable.javaClass.name).append("\n")
        sb.append("Message: ").append(throwable.message ?: "null").append("\n")
        sb.append("Stacktrace:\n")
        sb.append(throwable.stackTraceToString().trimEnd()).append("\n\n")

        sb.append("--- Recent App Logs ---\n")
        try {
            sb.append(AppLogger.getRecentLogs(60))
        } catch (e: Throwable) {
            sb.append("Unable to retrieve recent logs: ").append(e.message)
        }
        sb.append("\n")

        return sb.toString()
    }
}
