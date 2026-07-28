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

import android.app.Activity
import android.os.Bundle
import io.github.gracethings.bubblenotice.util.AppUtils
import io.github.gracethings.bubblenotice.util.UnreadMessageManager
import io.github.gracethings.bubblenotice.util.AppLogger

/**
 * Lightweight transparent proxy activity for "Open App" notification action.
 * Completely separate from BubbleActivity to avoid polluting the bubble's task stack.
 * 轻量级透明代理 Activity，用于"打开应用"通知操作。
 * 与 BubbleActivity 完全分离，避免污染气泡的任务栈。
 */
class ShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent.getStringExtra("EXTRA_PACKAGE_NAME")
        val originalIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("EXTRA_ORIGINAL_INTENT", android.app.PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("EXTRA_ORIGINAL_INTENT") as? android.app.PendingIntent
        }

        if (pkg != null) {
            AppLogger.d("ShortcutActivity", "Clearing messages for pkg: $pkg")
            UnreadMessageManager.clearMessagesForPackage(pkg)

            if (originalIntent != null) {
                AppLogger.d("ShortcutActivity", "Sending original intent")
                AppUtils.sendPendingIntentAllowed(this, originalIntent)
            } else {
                AppLogger.d("ShortcutActivity", "Launching app by package")
                AppUtils.launchApp(this, pkg)
            }

            // 仅隐藏通知栏中的通知，保留气泡 / Hide notification from shade, keep bubble alive
            io.github.gracethings.bubblenotice.service.BubbleNotificationListenerService.suppressNotificationInShade(this)
        } else {
            // Fallback: open main bubble notification / 回退：打开主气泡通知
            MainActivity.sendBubbleNotification(this)
        }
        finish()
    }
}
