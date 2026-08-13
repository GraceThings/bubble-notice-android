package io.github.gracethings.bubblenotice.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var logDir: File? = null
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024L // 5MB limit
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days in milliseconds
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun init(context: Context) {
        logDir = context.cacheDir
        cleanOldLogs()
    }

    private fun cleanOldLogs() {
        val dir = logDir ?: return
        val threshold = System.currentTimeMillis() - MAX_AGE_MS
        
        // Clean legacy file if exists
        val legacyFile = File(dir, "app_logs.txt")
        if (legacyFile.exists()) {
            legacyFile.delete()
        }

        // Clean old daily files
        val files = dir.listFiles { _, name -> name.startsWith("app_logs_") && name.endsWith(".txt") }
        files?.forEach { file ->
            if (file.lastModified() < threshold) {
                file.delete()
            }
        }
    }

    private fun getCurrentLogFile(): File? {
        val dir = logDir ?: return null
        val dateString = fileDateFormat.format(Date())
        val file = File(dir, "app_logs_$dateString.txt")
        if (file.exists() && file.length() > MAX_LOG_SIZE) {
            file.delete()
        }
        return file
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

    fun getLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles { _, name -> name.startsWith("app_logs_") && name.endsWith(".txt") }
            ?.sortedBy { it.name } ?: emptyList()
    }

    private fun writeToFile(level: String, tag: String, msg: String) {
        val file = getCurrentLogFile() ?: return
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
