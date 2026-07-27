package io.github.gracethings.bubblenotice.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var logFile: File? = null
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024L // 5MB limit (5MB 限制)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        val file = File(context.cacheDir, "app_logs.txt")
        if (file.exists() && file.length() > MAX_LOG_SIZE) {
            file.delete()
        }
        logFile = file
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        writeToFile("D", tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        writeToFile("I", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        writeToFile("W", tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
        val stackTrace = t?.stackTraceToString() ?: ""
        writeToFile("E", tag, "$msg\n$stackTrace")
    }

    fun getLogFile(): File? = logFile

    private fun writeToFile(level: String, tag: String, msg: String) {
        val file = logFile ?: return
        try {
            val timestamp = dateFormat.format(Date())
            val logLine = "$timestamp $level/$tag: $msg\n"
            FileOutputStream(file, true).use {
                it.write(logLine.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("AppLogger", "Failed to write log", e)
        }
    }
}
