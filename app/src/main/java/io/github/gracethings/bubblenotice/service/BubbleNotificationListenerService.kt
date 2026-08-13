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
import io.github.gracethings.bubblenotice.ShortcutActivity

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
        var instance: BubbleNotificationListenerService? = null
            private set

        private const val MAIN_BUBBLE_NOTIFICATION_ID = 1001

        data class PackageState(
            val title: String,
            val text: String,
            val msgTime: Long,
            val styleTime: Long,
            val messageCount: Int
        )

        private val packageStateMap = mutableMapOf<String, PackageState>()
        private var isBubbleDismissed = false

        // 存储最后一次气泡通知的数据，用于仅隐藏通知栏但保留气泡
        // Store last bubble notification data for suppressing shade while keeping bubble
        private var lastBubbleIntent: PendingIntent? = null
        private var lastBubbleIcon: IconCompat? = null
        private var lastBuilder: NotificationCompat.Builder? = null

        /**
         * 仅隐藏通知栏中的通知，保留气泡
         * Suppress the notification from the shade while keeping the bubble alive.
         */
        fun suppressNotificationInShade(context: android.content.Context) {
            val builder = lastBuilder ?: return
            val intent = lastBubbleIntent ?: return
            val icon = lastBubbleIcon ?: return

            val suppressed = NotificationCompat.BubbleMetadata.Builder(intent, icon)
                .setDesiredHeight(600)
                .setAutoExpandBubble(false)
                .setSuppressNotification(true)
                .build()

            builder.setBubbleMetadata(suppressed)
            try {
                NotificationManagerCompat.from(context).notify(MAIN_BUBBLE_NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
    }

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

        // 跳过全屏通知（来电、闹钟、计时器等），避免黑屏/卡死
        // Skip full-screen notifications (calls, alarms, timers) to prevent black screen bugs.
        if (notification.fullScreenIntent != null) {
            AppLogger.d("BubbleService", "Skipped full-screen notification from: ${sbn.packageName}")
            return
        }

        // 跳过通话类通知（语音/视频来电等），部分应用（如微信）不使用 fullScreenIntent，
        // 但会设置 CATEGORY_CALL。接管此类通知会导致 SystemUI 气泡渲染管线污染。
        // Skip call-category notifications. Some apps (e.g. WeChat) don't use fullScreenIntent
        // but do set CATEGORY_CALL. Intercepting these corrupts the SystemUI bubble pipeline.
        if (notification.category == Notification.CATEGORY_CALL ||
            notification.category == Notification.CATEGORY_MISSED_CALL) {
            AppLogger.d("BubbleService", "Skipped call notification from: ${sbn.packageName} (category=${notification.category})")
            return
        }
        
        val isWorkProfile = sbn.user != android.os.Process.myUserHandle()
        val pkgId = "${pkg}:${if (isWorkProfile) 1 else 0}"

        val selectedApps = AppUtils.getSelectedApps(this)
        if (selectedApps.contains(pkgId)) {
            val channelId = notification.channelId
            if (channelId != null) {
                AppUtils.addKnownChannel(this, pkgId, channelId)
            }
            
            val disabledChannels = AppUtils.getDisabledChannels(this, pkgId)
            if (channelId != null && disabledChannels.contains(channelId)) {
                AppLogger.d("BubbleService", "Skipped notification from: $pkgId (disabled channel: $channelId)")
                return
            }

            AppLogger.d("BubbleService", "Intercepted notification from: $pkg")
            serviceScope.launch {

                val appName = AppUtils.getAppName(this@BubbleNotificationListenerService, pkgId)
                val extras = notification.extras
                val title = extras.getString(Notification.EXTRA_TITLE) ?: appName
                var text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

                val messagingStyle = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
                val lastStyleMessage = messagingStyle?.messages?.lastOrNull()
                val styleTime = lastStyleMessage?.timestamp ?: 0L
                val messageCount = messagingStyle?.messages?.size ?: -1
                
                // Extract full text if MessagingStyle exists to mimic native stacked notifications (如果存在 MessagingStyle，则提取全文以模仿原生的堆叠通知)
                if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
                    text = messagingStyle.messages.joinToString("\n") { it.text ?: "" }
                }

                // 提取通知时间戳进行比较 / Extract timestamp for comparison.
                val msgTime = if (styleTime != 0L) styleTime else if (notification.`when` != 0L) notification.`when` else sbn.postTime

                val lastState = packageStateMap[pkgId]
                val isSameContent = if (lastState != null) {
                    if (messageCount != -1 && lastState.messageCount != -1) {
                        lastState.messageCount == messageCount && lastState.styleTime == styleTime && lastState.text == text
                    } else {
                        lastState.title == title && lastState.text == text && lastState.msgTime == msgTime
                    }
                } else false

                val isNewMessage = !isSameContent

                AppLogger.d("BubbleService", "Received notification: pkg=$pkgId, title=$title, text=$text, msgTime=$msgTime, styleTime=$styleTime, messageCount=$messageCount, isSameContent=$isSameContent, lastState=$lastState")

                val originalIntent = notification.contentIntent
                val originalSmallIcon = notification.smallIcon
                
                // Extract avatar (largeIcon) or MessagingStyle person icon (提取头像 (largeIcon) 或 MessagingStyle 个人图标)
                var originalLargeIcon = notification.getLargeIcon()
                if (originalLargeIcon == null) {
                    lastStyleMessage?.person?.icon?.let { iconCompat ->
                        originalLargeIcon = iconCompat.toIcon(this@BubbleNotificationListenerService)
                    }
                }

                // 如果用户已经手动移除了当前气泡，且没有新消息，则不重新显示气泡 / If user dismissed the bubble and no new message, do not show again.
                if (isBubbleDismissed && !isNewMessage) {
                    AppLogger.d("BubbleService", "Ignored notification from $pkg: Bubble was dismissed and no new message.")
                    return@launch
                }

                // 如果是新消息，重置气泡手动移除状态并更新追踪 / If it is a new message, reset dismissal status and update tracking.
                val actions = notification.actions?.toList() ?: emptyList()
                if (isNewMessage) {
                    AppLogger.i("BubbleService", "New message detected from $pkg")
                    packageStateMap[pkgId] = PackageState(title, text, msgTime, styleTime, messageCount)
                    isBubbleDismissed = false
                    UnreadMessageManager.addMessage(pkgId, title, text, msgTime, originalIntent, actions)
                    
                    if (AppUtils.isAutoJumpEnabled(this@BubbleNotificationListenerService)) {
                        AppUtils.setPendingAutoJump(originalIntent, pkgId, title)
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


    private fun createCircularIcon(context: android.content.Context, originalIcon: android.graphics.drawable.Icon): androidx.core.graphics.drawable.IconCompat {
        val drawable = originalIcon.loadDrawable(context)
            ?: return androidx.core.graphics.drawable.IconCompat.createFromIcon(context, originalIcon)!!
        
        var bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
            drawable.bitmap
        } else {
            val bmp = android.graphics.Bitmap.createBitmap(Math.max(drawable.intrinsicWidth, 144), Math.max(drawable.intrinsicHeight, 144), android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }

        val size = Math.min(bitmap.width, bitmap.height)
        val output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
        }
        val rect = android.graphics.Rect(0, 0, size, size)
        val rectF = android.graphics.RectF(rect)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        
        // Center crop
        val srcRect = android.graphics.Rect(
            (bitmap.width - size) / 2,
            (bitmap.height - size) / 2,
            (bitmap.width + size) / 2,
            (bitmap.height + size) / 2
        )
        canvas.drawBitmap(bitmap, srcRect, rect, paint)

        return androidx.core.graphics.drawable.IconCompat.createWithBitmap(output)
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
                createCircularIcon(this, originalLargeIcon)
            } catch (e: Exception) {
                // Fallback to app icon if conversion fails (如果转换失败，则回退到应用图标)
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

        // "打开应用" 快捷操作意图 / "Open App" action intent: uses ShortcutActivity (separate task stack)
        // 使用独立的 ShortcutActivity 而非 BubbleActivity，避免污染气泡的任务栈
        val openAppIntent = Intent(this, ShortcutActivity::class.java).apply {
            action = "io.github.gracethings.bubblenotice.ACTION_LAUNCH_APP"
            putExtra("EXTRA_PACKAGE_NAME", pkgId)
            putExtra("EXTRA_SENDER_NAME", title)
            if (originalIntent != null) {
                putExtra("EXTRA_ORIGINAL_INTENT", originalIntent)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openAppPendingIntent = PendingIntent.getActivity(
            this, pkgId.hashCode(), openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openAppAction = NotificationCompat.Action.Builder(
            0, getString(R.string.action_open_app), openAppPendingIntent
        ).build()

        val smallIconCompat = originalSmallIcon?.let {
            try {
                IconCompat.createFromIcon(this, it)
            } catch (e: Exception) {
                null
            }
        }

        // 通知体点击意图 / Notification body tap: open bubble normally (same as tapping the bubble icon)
        // 不使用 ACTION_LAUNCH_APP，避免污染气泡任务栈
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, BubbleActivity::class.java).apply { setPackage(packageName) },
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent) // 点击通知主体 → 正常打开气泡 / Tap notification body → open bubble normally
            .setStyle(style)
            .setBubbleMetadata(bubbleData)        // 绑定气泡入口 / Bind the bubble entry point.
            .setShortcutId(shortcutId)
            .addPerson(chatPartner)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 设置高优先级以便弹出文本 / High priority for heads-up notification.
            .setOnlyAlertOnce(isUpdate) // 更新时静?/ Quietly update repeated messages.
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

        // 保存气泡数据，以便后续调用 suppressNotificationInShade
        // Save bubble data for later suppression
        lastBubbleIntent = bubbleIntent
        lastBubbleIcon = icon
        lastBuilder = builder

        try {
            NotificationManagerCompat.from(this).notify(MAIN_BUBBLE_NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}








