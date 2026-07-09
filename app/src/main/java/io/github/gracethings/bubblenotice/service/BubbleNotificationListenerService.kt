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
package io.github.gracethings.bubblenotice.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.os.Process
import androidx.core.graphics.drawable.toBitmap
import io.github.gracethings.bubblenotice.BubbleActivity
import io.github.gracethings.bubblenotice.MainActivity
import io.github.gracethings.bubblenotice.R
import io.github.gracethings.bubblenotice.util.AppUtils
import io.github.gracethings.bubblenotice.util.UnreadMessageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class BubbleNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val MAIN_BUBBLE_NOTIFICATION_ID = 1001

        // 用于判断是否为新消息的追踪变�?/ Track variables to determine if it is a new message.
        private var lastMessagePkg: String? = null
        private var lastMessageTitle: String? = null
        private var lastMessageText: String? = null
        private var lastEventTime: Long = 0L
        private var isBubbleDismissed = false
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.user != Process.myUserHandle()) {
            return
        }

        val pkg = sbn.packageName
        if (pkg == packageName) return

        val notification = sbn.notification
        if (sbn.isOngoing || (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)) {
            return
        }

        val selectedApps = AppUtils.getSelectedApps(this)
        if (selectedApps.contains(pkg)) {
            serviceScope.launch {

                val appName = AppUtils.getAppName(this@BubbleNotificationListenerService, pkg)
                val extras = notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE) ?: appName
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

                // 提取通知时间戳进行比�?/ Extract timestamp for comparison.
                val msgTime = if (notification.`when` != 0L) notification.`when` else sbn.postTime

                // 判断是否为新消息 / Check if it is a new message.
                val isNewMessage = pkg != lastMessagePkg || title != lastMessageTitle || text != lastMessageText || msgTime != lastEventTime

                val originalIntent = notification.contentIntent
                val originalSmallIcon = notification.smallIcon

                // 如果用户已经手动移除了当前气泡，且没有新消息，则不重新显示气�?/ If user dismissed the bubble and no new message, do not show again.
                if (isBubbleDismissed && !isNewMessage) {
                    return@launch
                }

                // 如果是新消息，重置气泡手动移除状态并更新追踪 / If it is a new message, reset dismissal status and update tracking.
                if (isNewMessage) {
                    lastMessagePkg = pkg
                    lastMessageTitle = title
                    lastMessageText = text
                    lastEventTime = msgTime
                    isBubbleDismissed = false
                    UnreadMessageManager.addMessage(pkg, title, text, msgTime, originalIntent)
                    
                    if (AppUtils.isAutoJumpEnabled(this@BubbleNotificationListenerService)) {
                        AppUtils.setPendingAutoJump(originalIntent)
                    }
                }

                val isTakeOver = AppUtils.isTakeOverNotifications(this@BubbleNotificationListenerService)

                if (isTakeOver) {
                    cancelNotification(sbn.key)
                }

                updateMainBubble(pkg, appName, title, text, msgTime, isUpdate = !isNewMessage, isTakeOver = isTakeOver, originalIntent = originalIntent, originalSmallIcon = originalSmallIcon)
            }
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {
        super.onNotificationRemoved(sbn, rankingMap, reason)

        if (sbn.packageName != packageName || sbn.id != MAIN_BUBBLE_NOTIFICATION_ID) {
            return
        }

        val isUserDismissal = reason == REASON_CANCEL ||
                reason == REASON_CANCEL_ALL ||
                reason == REASON_USER_STOPPED

        if (isUserDismissal) {
            isBubbleDismissed = true
        }
    }

    private fun updateMainBubble(
        pkg: String,
        appName: String,
        title: String,
        text: String,
        msgTime: Long,
        isUpdate: Boolean,
        isTakeOver: Boolean,
        originalIntent: PendingIntent?,
        originalSmallIcon: android.graphics.drawable.Icon?
    ) {
        val channelId = if (!isUpdate) {
            AppUtils.BUBBLE_CHANNEL_ALERT_ID
        } else {
            AppUtils.BUBBLE_CHANNEL_SILENT_ID
        }
        val shortcutId = "bubble_split_shortcut"

        val appIconDrawable = try {
            packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground)!!
        }
        val iconBitmap = appIconDrawable.toBitmap(144, 144)
        val icon = IconCompat.createWithBitmap(iconBitmap)

        val chatPartner = Person.Builder()
            .setName(appName)
            .setIcon(icon)
            .setImportant(true)
            .build()

        // 气泡行为意图 / Bubble action intent: open BubbleActivity as the split-screen console.
        val targetIntent = Intent(this, BubbleActivity::class.java).apply {
            putExtra("EXTRA_PACKAGE_NAME", pkg)
            putExtra("EXTRA_TITLE", title)
            putExtra("EXTRA_TEXT", text)
            putExtra("EXTRA_TIME", msgTime)
        }
        val bubbleIntent = PendingIntent.getActivity(
            this, 0, targetIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val bubbleData = NotificationCompat.BubbleMetadata.Builder(bubbleIntent, icon)
            .setDesiredHeight(600)
            .setAutoExpandBubble(false) // 默认不强行弹�?/ Let Android decide when to expand.
            .setSuppressNotification(false) // 确保不抑制通知显示 / Ensure notification is not suppressed.
            .build()

        val shortcutIntent = Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN }
        val shortcut = ShortcutInfoCompat.Builder(this, shortcutId)
            .setCategories(setOf("android.shortcut.conversation"))
            .setIntent(shortcutIntent)
            .setLongLived(true)
            .setShortLabel(appName)
            .setIcon(icon)
            .setPerson(chatPartner)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)

        val style = NotificationCompat.MessagingStyle(chatPartner)
            .addMessage("$title: $text", System.currentTimeMillis(), chatPartner)

        // 通知主体意图 / Notification body intent: launch the target app directly.
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: Intent(this, MainActivity::class.java)

        val finalContentIntent = originalIntent ?: PendingIntent.getActivity(
            this, 1, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openAppAction = NotificationCompat.Action.Builder(
            0, getString(R.string.action_open_app), finalContentIntent
        ).build()

        val smallIconCompat = originalSmallIcon?.let {
            try {
                IconCompat.createFromIcon(this, it)
            } catch (e: Exception) {
                null
            }
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(finalContentIntent) // 点击通知主体 / Tap the notification body.
            .setStyle(style)
            .setBubbleMetadata(bubbleData)        // 绑定气泡入口 / Bind the bubble entry point.
            .setShortcutId(shortcutId)
            .addPerson(chatPartner)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 设置高优先级以便弹出文本 / High priority for heads-up notification.
            .setOnlyAlertOnce(isUpdate) // 更新时静�?/ Quietly update repeated messages.
            .setAutoCancel(true)        // 点击后清除通知 / Clear after tapping the notification.
            .addAction(openAppAction)   // 提供明确的打开应用按钮 / Provide explicit button to bypass bubble expansion.

        if (smallIconCompat != null) {
            builder.setSmallIcon(smallIconCompat)
        } else {
            builder.setSmallIcon(R.drawable.ic_notification)
        }

        val isDndMode = AppUtils.isBubbleDndModeEnabled(this)
        if (!isDndMode && !isUpdate) {
            // 如果未开启免打扰，且是新消息，则先取消旧通知以强制触发横幅弹�?/ Force heads-up by canceling the old notification
            try {
                NotificationManagerCompat.from(this).cancel(MAIN_BUBBLE_NOTIFICATION_ID)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            NotificationManagerCompat.from(this).notify(MAIN_BUBBLE_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
