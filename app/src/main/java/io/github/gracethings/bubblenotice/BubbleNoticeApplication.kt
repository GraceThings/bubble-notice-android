package io.github.gracethings.bubblenotice

import android.app.Application
import io.github.gracethings.bubblenotice.util.AppLogger
import io.github.gracethings.bubblenotice.util.CrashHandler

class BubbleNoticeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
        try {
            AppLogger.init(this)
            AppLogger.i("BubbleNoticeApplication", "Application started, logger initialized.")
        } catch (t: Throwable) {
            CrashHandler.handleException(this, t)
        }
    }
}
