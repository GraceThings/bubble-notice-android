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
import io.github.gracethings.bubblenotice.util.AppLogger
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
        val pkg = sbn.packageName
        if (pkg == packageName) return

        val notification = sbn.notification
        if (sbn.isOngoing || (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)) {
            return
        }
        
        val isWorkProfile = sbn.user != android.os.Process.myUserHandle()
        val pkgId = "${pkg}:${if (isWorkProfile) 1 else 0}"

        val selectedApps = AppUtils.getSelectedApps(this)
        if (selectedApps.contains(pkgId)) {
            AppLogger.d("BubbleService", "Intercepted notification from: $pkg")
            serviceScope.launch {

                val appName = AppUtils.getAppName(this@BubbleNotificationListenerService, pkg)
                val extras = notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE) ?: appName
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

                // 提取通知时间戳进行比�?/ Extract timestamp for comparison.
                val msgTime = if (notification.`when` != 0L) notification.`when` else sbn.postTime

                  val isSameContent = pkgId == lastMessagePkg && title == lastMessageTitle && text == lastMessageText
                  val isNewMessage = !isSameContent

                val originalIntent = notification.contentIntent
                val originalSmallIcon = notification.smallIcon
                
                // Extract avatar (largeIcon) or MessagingStyle person icon
                var originalLargeIcon = notification.getLargeIcon()
                if (originalLargeIcon == null) {
                    // Try to extract from MessagingStyle
                    val messagingStyle = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                    messagingStyle?.messages?.lastOrNull()?.person?.icon?.let { iconCompat ->
                        originalLargeIcon = iconCompat.toIcon(this@BubbleNotificationListenerService)
                    }
                }

                // 如果用户已经手动移除了当前气泡，且没有新消息，则不重新显示气�?/ If user dismissed the bubble and no new message, do not show again.
                if (isBubbleDismissed && !isNewMessage) {
                    AppLogger.d("BubbleService", "Ignored notification from $pkg: Bubble was dismissed and no new message.")
                    return@launch
                }

                // 如果是新消息，重置气泡手动移除状态并更新追踪 / If it is a new message, reset dismissal status and update tracking.
                val actions = notification.actions?.toList() ?: emptyList()
                if (isNewMessage) {
                    AppLogger.i("BubbleService", "New message detected from $pkg")
                    lastMessagePkg = pkgId
                    lastMessageTitle = title
                    lastMessageText = text
                    lastEventTime = msgTime
                    isBubbleDismissed = false
                    UnreadMessageManager.addMessage(pkgId, title, text, msgTime, originalIntent, actions)
                    
                    if (AppUtils.isAutoJumpEnabled(this@BubbleNotificationListenerService)) {
                        AppUtils.setPendingAutoJump(originalIntent)
                    }
                }

                val isTakeOver = AppUtils.isTakeOverNotifications(this@BubbleNotificationListenerService)
                val shouldBeUpdate = !isNewMessage

                if (isTakeOver) {
                    cancelNotification(sbn.key)
                }

                updateMainBubble(pkg, pkgId, appName, title, text, msgTime, isUpdate = shouldBeUpdate, isTakeOver = isTakeOver, originalIntent = originalIntent, originalSmallIcon = originalSmallIcon, originalLargeIcon = originalLargeIcon, actions = actions)
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
            AppLogger.d("BubbleService", "Main bubble was dismissed by user")
        }
    }

    private fun updateMainBubble(
        pkg: String,
        pkgId: String,
        appName: String,
        title: String,
        text: String,
        msgTime: Long,
        isUpdate: Boolean,
        isTakeOver: Boolean,
        originalIntent: PendingIntent?,
        originalSmallIcon: android.graphics.drawable.Icon?,
        originalLargeIcon: android.graphics.drawable.Icon? = null,
        actions: List<android.app.Notification.Action> = emptyList()
    ) {
        val channelId = AppUtils.BUBBLE_CHANNEL_ALERT_ID
        val shortcutId = "bubble_notice_shortcut"

        val icon = if (originalLargeIcon != null) {
            try {
                IconCompat.createFromIcon(this, originalLargeIcon)!!
            } catch (e: Exception) {
                // Fallback to app icon if conversion fails
                val appIconDrawable = try {
                    packageManager.getApplicationIcon(pkg)
                } catch (ex: Exception) {
                    androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground)!!
                }
                IconCompat.createWithBitmap(appIconDrawable.toBitmap(144, 144))
            }
        } else {
            val appIconDrawable = try {
                packageManager.getApplicationIcon(pkg)
            } catch (e: Exception) {
                androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground)!!
            }
            IconCompat.createWithBitmap(appIconDrawable.toBitmap(144, 144))
        }

        val chatPartner = Person.Builder()
            .setName(appName)
            .setIcon(icon)
            .setImportant(true)
            .build()

        // 气泡点击意图 / Bubble action intent: open BubbleActivity as the bubble-notice console.
        val targetIntent = Intent(this, BubbleActivity::class.java).apply {
            setPackage(packageName)
            putExtra("EXTRA_PACKAGE_NAME", pkgId)
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
            .setAutoExpandBubble(false) // 默认不强行弹?/ Let Android decide when to expand.
            .setSuppressNotification(false) // 确保不抑制通知显示 / Ensure notification is not suppressed.
            .build()

        val shortcutIntent = Intent(this, MainActivity::class.java).apply { 
            action = Intent.ACTION_MAIN 
            setPackage(packageName)
        }
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

        // 通知体意图 / Notification body intent: launch the target app directly.
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(pkg)
        } ?: Intent(this, MainActivity::class.java).apply { setPackage(packageName) }

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

        actions.forEach { nativeAction ->
            val actionBuilder = NotificationCompat.Action.Builder(
                0, 
                nativeAction.title,
                nativeAction.actionIntent
            )
            val remoteInputs = nativeAction.remoteInputs
            if (remoteInputs != null) {
                for (ri in remoteInputs) {
                    val compatRi = androidx.core.app.RemoteInput.Builder(ri.resultKey)
                        .setLabel(ri.label)
                        .setChoices(ri.choices)
                        .setAllowFreeFormInput(ri.allowFreeFormInput)
                        .build()
                    actionBuilder.addRemoteInput(compatRi)
                }
            }
            builder.addAction(actionBuilder.build())
        }

        if (smallIconCompat != null) {
            builder.setSmallIcon(smallIconCompat)
        } else {
            builder.setSmallIcon(R.drawable.ic_notification)
        }

        if (!isUpdate) {
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








