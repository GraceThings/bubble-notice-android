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
import io.github.gracethings.bubblenotice.receiver.NotificationActionReceiver

import io.github.gracethings.bubblenotice.R
import io.github.gracethings.bubblenotice.util.AppUtils
import io.github.gracethings.bubblenotice.util.UnreadMessageManager
import io.github.gracethings.bubblenotice.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect

class BubbleNotificationListenerService : NotificationListenerService() {

    companion object {
        var instance: BubbleNotificationListenerService? = null
            private set

        private const val MAIN_BUBBLE_NOTIFICATION_ID = 1001
        private const val PER_APP_BUBBLE_NOTIFICATION_ID_BASE = 20000
        private const val PER_APP_BUBBLE_NOTIFICATION_ID_RANGE = 8000
        // Android 没有暴露确切的气泡栈上限；多数系统允许约五个气泡，因此这里采用保守的尽力而为预算。 / Android does not expose the exact bubble stack limit. Most Android builds allow about five bubble slots, so this is a conservative best-effort budget.
        private const val TOTAL_BUBBLE_BUDGET = 5
        private const val RESERVED_BUBBLE_SLOTS_FOR_OTHER_APPS = 1
        private const val MIN_PER_APP_BUBBLES = 0

        data class PackageState(
            val title: String,
            val text: String,
            val msgTime: Long,
            val styleTime: Long,
            val messageCount: Int
        )

        private val packageStateMap = mutableMapOf<String, PackageState>()
        private var isBubbleDismissed = false
        private val dismissedPackages = mutableSetOf<String>()
        private val activePerAppBubbles = linkedMapOf<String, Long>()
        private val perAppNotificationIds = mutableMapOf<String, Int>()
        private val notificationIdToPackage = mutableMapOf<Int, String>()
        private val perAppShortcutIds = mutableMapOf<String, String>()
        private val perAppBubbleData = mutableMapOf<String, BubbleState>()
        private val perAppStateLock = Any()
        private val programmaticCancellationIds = mutableSetOf<Int>()

        internal val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // 存储最后一次气泡通知的数据，用于仅隐藏通知栏但保留气泡。 / Store the last bubble notification data for suppressing the shade while keeping the bubble alive.
        private var lastBubbleIntent: PendingIntent? = null
        private var lastBubbleIcon: IconCompat? = null
        private var lastBuilder: NotificationCompat.Builder? = null

        data class BubbleState(
            val intent: PendingIntent,
            val icon: IconCompat,
            val builder: NotificationCompat.Builder
        )

        /** 仅隐藏通知栏中的通知，保留气泡。 / Suppress the notification from the shade while keeping the bubble alive. */
        fun suppressNotificationInShade(context: android.content.Context, packageId: String? = null) {
            if (packageId != null && AppUtils.isPerAppBubblesEnabled(context)) {
                val snapshot = synchronized(perAppStateLock) {
                    val notificationId = perAppNotificationIds[packageId] ?: return@synchronized null
                    val state = perAppBubbleData[packageId] ?: return@synchronized null
                    notificationId to state
                } ?: return
                val (notificationId, state) = snapshot
                val suppressed = buildSuppressedBubbleMetadata(state.intent, state.icon)
                state.builder.setBubbleMetadata(suppressed)
                try {
                    NotificationManagerCompat.from(context).notify(notificationId, state.builder.build())
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
                return
            }

            val builder = lastBuilder ?: return
            val intent = lastBubbleIntent ?: return
            val icon = lastBubbleIcon ?: return

            val suppressed = buildSuppressedBubbleMetadata(intent, icon)
            builder.setBubbleMetadata(suppressed)
            try {
                NotificationManagerCompat.from(context).notify(MAIN_BUBBLE_NOTIFICATION_ID, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        private fun buildSuppressedBubbleMetadata(
            intent: PendingIntent,
            icon: IconCompat
        ): NotificationCompat.BubbleMetadata {
            return NotificationCompat.BubbleMetadata.Builder(intent, icon)
                .setDesiredHeight(600)
                .setAutoExpandBubble(false)
                .setSuppressNotification(true)
                .build()
        }

        private fun cancelOwnNotification(context: android.content.Context, notificationId: Int) {
            val isActive = try {
                NotificationManagerCompat.from(context).activeNotifications.any {
                    it.id == notificationId
                }
            } catch (e: Exception) {
                false
            }

            if (isActive) {
                synchronized(perAppStateLock) {
                    programmaticCancellationIds.add(notificationId)
                }
            }
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        private fun cancelPerAppBubbleLocked(context: android.content.Context, pkgId: String) {
            val notificationId = perAppNotificationIds.remove(pkgId)
            val shortcutId = perAppShortcutIds.remove(pkgId)
            activePerAppBubbles.remove(pkgId)
            perAppBubbleData.remove(pkgId)
            dismissedPackages.remove(pkgId)
            if (notificationId != null) {
                notificationIdToPackage.remove(notificationId)
                cancelOwnNotification(context, notificationId)
            }
            if (shortcutId != null) {
                try {
                    ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(shortcutId))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun cancelPerAppBubble(context: android.content.Context, pkgId: String) {
            synchronized(perAppStateLock) {
                cancelPerAppBubbleLocked(context, pkgId)
            }
        }

        fun cancelAllPerAppBubbles(context: android.content.Context) {
            synchronized(perAppStateLock) {
                val pkgIds = activePerAppBubbles.keys.toList()
                for (pkgId in pkgIds) {
                    cancelPerAppBubbleLocked(context, pkgId)
                }
            }
        }

        fun autoCloseBubbleIfEmpty(
            context: android.content.Context,
            packageFilter: String?
        ): Boolean {
            if (!AppUtils.isCloseBubbleAfterClearEnabled(context)) return false

            val isPerAppBubblesEnabled = AppUtils.isPerAppBubblesEnabled(context)
            val hasRemainingMessages = if (isPerAppBubblesEnabled && packageFilter != null) {
                UnreadMessageManager.hasMessagesForPackage(packageFilter)
            } else {
                UnreadMessageManager.messagesFlow.value.isNotEmpty()
            }
            if (hasRemainingMessages) return false

            if (isPerAppBubblesEnabled) {
                if (packageFilter != null) {
                    cancelPerAppBubble(context, packageFilter)
                } else {
                    cancelAllPerAppBubbles(context)
                }
            } else {
                cancelMainBubble(context)
            }
            return true
        }

        /** 在展开的 Activity 结束后再关闭气泡，避免 SystemUI 仍持有气泡表面时取消通知，从而在某些设备上留下卡住或透明的窗口。 / Close the bubble after its expanded activity has finished. This avoids cancelling the notification while SystemUI still owns the bubble surface, which can leave a stale or transparent window on some devices. */
        fun closeAfterActivityCleared(
            context: Context,
            packageFilter: String?
        ) {
            val activity = context as? android.app.Activity
            if (activity == null) {
                appScope.launch {
                    kotlinx.coroutines.delay(150L)
                    closeBubbleIfEmpty(context, packageFilter)
                }
                return
            }

            val activityRef = java.lang.ref.WeakReference(activity)
            appScope.launch {
                val startedAt = System.currentTimeMillis()
                while (System.currentTimeMillis() - startedAt < 3000L) {
                    val currentActivity = activityRef.get()
                    if (currentActivity == null ||
                        currentActivity.isDestroyed ||
                        currentActivity.isFinishing
                    ) {
                        break
                    }
                    kotlinx.coroutines.delay(50L)
                }
                kotlinx.coroutines.delay(150L)
                if (!AppUtils.isCloseBubbleAfterClearEnabled(context)) {
                    return@launch
                }
                if (AppUtils.isPerAppBubblesEnabled(context)) {
                    if (packageFilter != null) {
                        if (!UnreadMessageManager.hasMessagesForPackage(packageFilter)) {
                            cancelPerAppBubble(context, packageFilter)
                        }
                    } else if (UnreadMessageManager.messagesFlow.value.isEmpty()) {
                        cancelAllPerAppBubbles(context)
                    }
                } else if (UnreadMessageManager.messagesFlow.value.isEmpty()) {
                    cancelMainBubble(context)
                }
            }
        }

        private fun closeBubbleIfEmpty(context: Context, packageFilter: String?) {
            val isPerAppEnabled = AppUtils.isPerAppBubblesEnabled(context)
            val hasMessages = if (isPerAppEnabled && packageFilter != null) {
                UnreadMessageManager.hasMessagesForPackage(packageFilter)
            } else {
                UnreadMessageManager.messagesFlow.value.isNotEmpty()
            }
            if (hasMessages) {
                return
            }

            if (isPerAppEnabled) {
                if (packageFilter != null) {
                    cancelPerAppBubble(context, packageFilter)
                } else {
                    cancelAllPerAppBubbles(context)
                }
            } else {
                cancelMainBubble(context)
            }
        }

        /** 当应用没有未读卡片时关闭单个应用气泡；与 autoCloseBubbleIfEmpty 不同，这里不会清除无关的独立应用气泡。 / Close one app bubble when that app has no unread cards. Unlike autoCloseBubbleIfEmpty, this never clears unrelated per-app bubbles. */
        fun clearPerAppBubbleIfEmpty(context: android.content.Context, pkgId: String): Boolean {
            if (!AppUtils.isPerAppBubblesEnabled(context)) return false
            if (AppUtils.isCloseBubbleAfterClearEnabled(context)) {
                if (UnreadMessageManager.hasMessagesForPackage(pkgId)) return false
                cancelPerAppBubble(context, pkgId)
                return true
            }
            return false
        }

        fun cancelMainBubble(context: android.content.Context) {
            synchronized(perAppStateLock) {
                lastBubbleIntent = null
                lastBubbleIcon = null
                lastBuilder = null
                isBubbleDismissed = false
            }
            cancelOwnNotification(context, MAIN_BUBBLE_NOTIFICATION_ID)
        }

        fun notificationIdForPackage(pkgId: String): Int {
            synchronized(perAppStateLock) {
                perAppNotificationIds[pkgId]?.let { return it }

                var candidate = PER_APP_BUBBLE_NOTIFICATION_ID_BASE +
                        (pkgId.hashCode() and Int.MAX_VALUE) % PER_APP_BUBBLE_NOTIFICATION_ID_RANGE
                while (notificationIdToPackage.containsKey(candidate)) {
                    candidate += 1
                    if (candidate >= PER_APP_BUBBLE_NOTIFICATION_ID_BASE + PER_APP_BUBBLE_NOTIFICATION_ID_RANGE) {
                        candidate = PER_APP_BUBBLE_NOTIFICATION_ID_BASE
                    }
                }

                perAppNotificationIds[pkgId] = candidate
                notificationIdToPackage[candidate] = pkgId
                return candidate
            }
        }

        fun shortcutIdForPackage(pkgId: String): String {
            synchronized(perAppStateLock) {
                perAppShortcutIds[pkgId]?.let { return it }

                val sanitized = pkgId.map { char ->
                    if (char.isLetterOrDigit() || char == '_') char else '_'
                }.joinToString("")
                val shortcutId = "bubble_notice_$sanitized"
                perAppShortcutIds[pkgId] = shortcutId
                return shortcutId
            }
        }

        fun markActivePerAppBubble(pkgId: String) {
            synchronized(perAppStateLock) {
                activePerAppBubbles.remove(pkgId)
                activePerAppBubbles[pkgId] = System.currentTimeMillis()
            }
        }

        fun ensurePerAppBubbleCapacity(context: Context, maxAllowed: Int) {
            synchronized(perAppStateLock) {
                while (activePerAppBubbles.isNotEmpty() && activePerAppBubbles.size >= maxAllowed) {
                    val evictedPkgId = activePerAppBubbles.keys.firstOrNull { pkgId ->
                        !UnreadMessageManager.hasMessagesForPackage(pkgId)
                    } ?: activePerAppBubbles.keys.first()
                    cancelPerAppBubbleLocked(context, evictedPkgId)
                }
            }
        }

        fun getAppIconBitmap(context: Context, packageName: String): Bitmap {
            val realPkg = packageName.substringBefore(":")
            val drawable = try {
                context.packageManager.getApplicationIcon(realPkg)
            } catch (e: Exception) {
                androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!
            }
            return drawable.toBitmap(144, 144)
        }

        fun createCircularBitmap(context: Context, originalIcon: android.graphics.drawable.Icon): Bitmap? {
            val drawable = try {
                originalIcon.loadDrawable(context)
            } catch (e: Exception) {
                null
            } ?: return null

            val rawBmp = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 144
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 144
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }

            val minEdge = Math.min(rawBmp.width, rawBmp.height).coerceAtLeast(1)
            val output = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
            }
            val destRect = Rect(0, 0, 144, 144)

            canvas.drawARGB(0, 0, 0, 0)
            canvas.drawCircle(72f, 72f, 72f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

            val srcRect = Rect(
                (rawBmp.width - minEdge) / 2,
                (rawBmp.height - minEdge) / 2,
                (rawBmp.width + minEdge) / 2,
                (rawBmp.height + minEdge) / 2
            )
            canvas.drawBitmap(rawBmp, srcRect, destRect, paint)
            return output
        }

        fun createCircularIcon(context: Context, originalIcon: android.graphics.drawable.Icon): IconCompat {
            val bitmap = createCircularBitmap(context, originalIcon)
            return if (bitmap != null) {
                IconCompat.createWithBitmap(bitmap)
            } else {
                IconCompat.createFromIcon(context, originalIcon) ?: IconCompat.createWithBitmap(getAppIconBitmap(context, context.packageName))
            }
        }

        fun updateMainBubble(
            context: Context,
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
            avatarIcon: IconCompat? = null,
            actions: List<android.app.Notification.Action> = emptyList(),
            notificationId: Int = MAIN_BUBBLE_NOTIFICATION_ID,
            shortcutId: String = "bubble_notice_shortcut",
            filterByPackage: Boolean = false,
            storeAsPerApp: Boolean = false,
            suppressNotification: Boolean = false
        ) {
            val channelId = AppUtils.BUBBLE_CHANNEL_ALERT_ID

            val icon = avatarIcon ?: if (originalLargeIcon != null) {
                try {
                    createCircularIcon(context, originalLargeIcon)
                } catch (e: Exception) {
                    IconCompat.createWithBitmap(getAppIconBitmap(context, pkg))
                }
            } else {
                IconCompat.createWithBitmap(getAppIconBitmap(context, pkg))
            }

            val chatPartner = Person.Builder()
                .setName(appName)
                .setIcon(icon)
                .setImportant(true)
                .build()

            // 气泡点击意图 / Bubble action intent: open BubbleActivity as the bubble-notice console.
            val targetIntent = Intent(context, BubbleActivity::class.java).apply {
                setPackage(context.packageName)
                if (filterByPackage) {
                    putExtra("EXTRA_PACKAGE_NAME", pkgId)
                }
                putExtra("EXTRA_TITLE", title)
                putExtra("EXTRA_TEXT", text)
                putExtra("EXTRA_TIME", msgTime)
            }
            val bubbleIntent = PendingIntent.getActivity(
                context, if (filterByPackage) pkgId.hashCode() else 0, targetIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val bubbleData = NotificationCompat.BubbleMetadata.Builder(bubbleIntent, icon)
                .setDesiredHeight(600)
                .setAutoExpandBubble(false) // 不强制自动展开气泡 / Do not force the bubble to expand automatically.
                .setSuppressNotification(suppressNotification)
                .build()

            val shortcutIntent = Intent(context, MainActivity::class.java).apply { 
                action = Intent.ACTION_MAIN 
                setPackage(context.packageName)
            }
            val shortcut = ShortcutInfoCompat.Builder(context, shortcutId)
                .setCategories(setOf("android.shortcut.conversation"))
                .setIntent(shortcutIntent)
                .setLongLived(true)
                .setShortLabel(appName)
                .setIcon(icon)
                .setPerson(chatPartner)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)

            val style = NotificationCompat.MessagingStyle(chatPartner)
                .addMessage("$title: $text", System.currentTimeMillis(), chatPartner)

            // “打开应用”快捷操作意图，不通过透明 Activity 处理。 / "Open App" action intent, handled without a transparent Activity.
            val openAppIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = "io.github.gracethings.bubblenotice.ACTION_LAUNCH_APP"
                putExtra("EXTRA_PACKAGE_NAME", pkgId)
                putExtra("EXTRA_SENDER_NAME", title)
                if (originalIntent != null) {
                    putExtra("EXTRA_ORIGINAL_INTENT", originalIntent)
                }
            }

            val openAppPendingIntent = PendingIntent.getBroadcast(
                context, pkgId.hashCode(), openAppIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val openAppAction = NotificationCompat.Action.Builder(
                0, context.getString(R.string.action_open_app), openAppPendingIntent
            ).build()

            val smallIconCompat = originalSmallIcon?.let {
                try {
                    IconCompat.createFromIcon(context, it)
                } catch (e: Exception) {
                    null
                }
            }

            // 通知主体点击意图：正常打开气泡，与点击气泡图标一致；不使用 ACTION_LAUNCH_APP，避免污染气泡任务栈。 / Notification body tap intent: open the bubble normally, the same as tapping the bubble icon. Do not use ACTION_LAUNCH_APP to avoid polluting the bubble task stack.
            val contentIntent = PendingIntent.getActivity(
                context, if (filterByPackage) pkgId.hashCode() else 0,
                Intent(context, BubbleActivity::class.java).apply {
                    setPackage(context.packageName)
                    if (filterByPackage) {
                        putExtra("EXTRA_PACKAGE_NAME", pkgId)
                    }
                },
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent) // 点击通知主体 → 正常打开气泡 / Tap notification body → open bubble normally
                .setStyle(style)
                .setBubbleMetadata(bubbleData)        // 绑定气泡入口 / Bind the bubble entry point.
                .setShortcutId(shortcutId)
                .addPerson(chatPartner)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // 设置高优先级以便弹出文本 / High priority for heads-up notification.
                .setOnlyAlertOnce(isUpdate) // 更新时保持静默 / Quietly update repeated messages.
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

            val hasExistingNotification = if (storeAsPerApp) {
                synchronized(perAppStateLock) { activePerAppBubbles.containsKey(pkgId) }
            } else {
                synchronized(perAppStateLock) { lastBuilder != null }
            }
            if (!isUpdate && hasExistingNotification) {
                // 如果未开启免打扰且是新消息，先取消旧通知以强制触发横幅弹出。 / Force heads-up by cancelling the old notification when DND is off and this is a new message.
                cancelOwnNotification(context, notificationId)
            }

            // 保存气泡数据，以便后续调用 suppressNotificationInShade。 / Save the bubble data for later suppression by suppressNotificationInShade.
            synchronized(perAppStateLock) {
                if (storeAsPerApp) {
                    perAppBubbleData[pkgId] = BubbleState(bubbleIntent, icon, builder)
                } else {
                    lastBubbleIntent = bubbleIntent
                    lastBubbleIcon = icon
                    lastBuilder = builder
                }
            }

            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        fun updatePerAppBubble(
            context: Context,
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
            avatarIcon: IconCompat? = null,
            actions: List<android.app.Notification.Action> = emptyList(),
            suppressNotification: Boolean = false
        ) {
            val notificationId = notificationIdForPackage(pkgId)
            val shortcutId = shortcutIdForPackage(pkgId)

            updateMainBubble(
                context = context,
                pkg = pkg,
                pkgId = pkgId,
                appName = appName,
                title = title,
                text = text,
                msgTime = msgTime,
                isUpdate = isUpdate,
                isTakeOver = isTakeOver,
                originalIntent = originalIntent,
                originalSmallIcon = originalSmallIcon,
                originalLargeIcon = originalLargeIcon,
                avatarIcon = avatarIcon,
                actions = actions,
                notificationId = notificationId,
                shortcutId = shortcutId,
                filterByPackage = true,
                storeAsPerApp = true,
                suppressNotification = suppressNotification
            )
        }

        fun updateBubbleToLatestRemaining(context: Context, packageId: String? = null) {
            val isPerAppBubbles = AppUtils.isPerAppBubblesEnabled(context)
            if (isPerAppBubbles) {
                if (packageId != null) {
                    updateSinglePerAppBubbleToLatest(context, packageId)
                } else {
                    val activeKeys = synchronized(perAppStateLock) { activePerAppBubbles.keys.toList() }
                    for (pkg in activeKeys) {
                        updateSinglePerAppBubbleToLatest(context, pkg)
                    }
                }
            } else {
                updateMainBubbleToLatest(context)
            }
        }

        private fun updateSinglePerAppBubbleToLatest(context: Context, pkgId: String) {
            val isActive = synchronized(perAppStateLock) {
                activePerAppBubbles.containsKey(pkgId) && !dismissedPackages.contains(pkgId)
            }
            if (!isActive) return

            val latestMsg = UnreadMessageManager.getLatestMessageForPackage(pkgId)
            val realPkg = pkgId.substringBefore(":")
            val appName = AppUtils.getAppName(context, pkgId)

            if (latestMsg != null) {
                val icon = if (latestMsg.avatarBitmap != null) {
                    IconCompat.createWithBitmap(latestMsg.avatarBitmap)
                } else {
                    IconCompat.createWithBitmap(getAppIconBitmap(context, realPkg))
                }
                updatePerAppBubble(
                    context = context,
                    pkg = realPkg,
                    pkgId = pkgId,
                    appName = appName,
                    title = latestMsg.senderName,
                    text = latestMsg.messageText,
                    msgTime = latestMsg.timestamp,
                    isUpdate = true,
                    isTakeOver = AppUtils.isTakeOverNotifications(context),
                    originalIntent = latestMsg.contentIntent,
                    originalSmallIcon = latestMsg.smallIcon,
                    avatarIcon = icon,
                    actions = latestMsg.actions,
                    suppressNotification = true
                )
            } else {
                if (AppUtils.isCloseBubbleAfterClearEnabled(context)) {
                    cancelPerAppBubble(context, pkgId)
                } else {
                    val icon = IconCompat.createWithBitmap(getAppIconBitmap(context, realPkg))
                    updatePerAppBubble(
                        context = context,
                        pkg = realPkg,
                        pkgId = pkgId,
                        appName = appName,
                        title = appName,
                        text = context.getString(R.string.msg_all_caught_up),
                        msgTime = System.currentTimeMillis(),
                        isUpdate = true,
                        isTakeOver = AppUtils.isTakeOverNotifications(context),
                        originalIntent = null,
                        originalSmallIcon = null,
                        avatarIcon = icon,
                        actions = emptyList(),
                        suppressNotification = true
                    )
                }
            }
        }

        private fun updateMainBubbleToLatest(context: Context) {
            val isMainActive = synchronized(perAppStateLock) {
                !isBubbleDismissed && lastBuilder != null
            }
            if (!isMainActive) return

            val latestMsg = UnreadMessageManager.getLatestMessage()
            if (latestMsg != null) {
                val realPkg = latestMsg.packageName.substringBefore(":")
                val appName = AppUtils.getAppName(context, latestMsg.packageName)
                val icon = if (latestMsg.avatarBitmap != null) {
                    IconCompat.createWithBitmap(latestMsg.avatarBitmap)
                } else {
                    IconCompat.createWithBitmap(getAppIconBitmap(context, realPkg))
                }
                updateMainBubble(
                    context = context,
                    pkg = realPkg,
                    pkgId = latestMsg.packageName,
                    appName = appName,
                    title = latestMsg.senderName,
                    text = latestMsg.messageText,
                    msgTime = latestMsg.timestamp,
                    isUpdate = true,
                    isTakeOver = AppUtils.isTakeOverNotifications(context),
                    originalIntent = latestMsg.contentIntent,
                    originalSmallIcon = latestMsg.smallIcon,
                    avatarIcon = icon,
                    actions = latestMsg.actions,
                    suppressNotification = true
                )
            } else {
                if (AppUtils.isCloseBubbleAfterClearEnabled(context)) {
                    cancelMainBubble(context)
                } else {
                    val icon = IconCompat.createWithBitmap(getAppIconBitmap(context, context.packageName))
                    updateMainBubble(
                        context = context,
                        pkg = context.packageName,
                        pkgId = "${context.packageName}:0",
                        appName = context.getString(R.string.app_name),
                        title = context.getString(R.string.app_name),
                        text = context.getString(R.string.msg_all_caught_up),
                        msgTime = System.currentTimeMillis(),
                        isUpdate = true,
                        isTakeOver = AppUtils.isTakeOverNotifications(context),
                        originalIntent = null,
                        originalSmallIcon = null,
                        avatarIcon = icon,
                        actions = emptyList(),
                        suppressNotification = true
                    )
                }
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

        // 跳过全屏通知（如来电、闹钟、计时器），避免黑屏或卡死。 / Skip full-screen notifications such as calls, alarms, and timers to prevent black screens or freezes.
        if (notification.fullScreenIntent != null) {
            AppLogger.d("BubbleService", "Skipped full-screen notification from: ${sbn.packageName}")
            return
        }

        // 跳过通话类通知（如语音或视频来电）；部分应用不使用 fullScreenIntent，但会设置 CATEGORY_CALL，接管可能导致 SystemUI 气泡渲染管线污染。 / Skip call-category notifications such as voice or video calls. Some apps do not use a fullScreenIntent but do set CATEGORY_CALL; intercepting these can corrupt the SystemUI bubble pipeline.
        if (notification.category == Notification.CATEGORY_CALL ||
            notification.category == Notification.CATEGORY_MISSED_CALL) {
            AppLogger.d("BubbleService", "Skipped call notification from: ${sbn.packageName} (category=${notification.category})")
            return
        }
        
        val isWorkProfile = sbn.user != android.os.Process.myUserHandle()
        val pkgId = "${pkg}:${if (isWorkProfile) 1 else 0}"

        val selectedApps = AppUtils.getSelectedApps(this)
        if (!selectedApps.contains(pkgId) &&
            AppUtils.isPerAppBubblesEnabled(this) &&
            notification.getBubbleMetadata() != null
        ) {
            serviceScope.launch {
                reconcilePerAppBubbleCapacity()
            }
            return
        }

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
                
                // 如果存在 MessagingStyle，则提取全文以模仿原生堆叠通知。 / Extract the full text when MessagingStyle exists to mimic native stacked notifications.
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
                
                // 提取头像（largeIcon）或 MessagingStyle 人物图标。 / Extract the largeIcon or the MessagingStyle person icon.
                var originalLargeIcon = notification.getLargeIcon()
                if (originalLargeIcon == null) {
                    lastStyleMessage?.person?.icon?.let { iconCompat ->
                        originalLargeIcon = iconCompat.toIcon(this@BubbleNotificationListenerService)
                    }
                }

                val avatarBitmap = if (originalLargeIcon != null) {
                    createCircularBitmap(this@BubbleNotificationListenerService, originalLargeIcon)
                } else null

                val isPerAppBubbles = AppUtils.isPerAppBubblesEnabled(this@BubbleNotificationListenerService)
                val wasDismissed = if (isPerAppBubbles) {
                    dismissedPackages.contains(pkgId)
                } else {
                    isBubbleDismissed
                }

                // 如果用户已经手动移除了当前气泡，且没有新消息，则不重新显示气泡 / If user dismissed the bubble and no new message, do not show again.
                if (wasDismissed && !isNewMessage) {
                    AppLogger.d("BubbleService", "Ignored notification from $pkg: Bubble was dismissed and no new message.")
                    return@launch
                }

                // 如果是新消息，重置气泡手动移除状态并更新追踪 / If it is a new message, reset dismissal status and update tracking.
                val actions = notification.actions?.toList() ?: emptyList()
                if (isNewMessage) {
                    AppLogger.i("BubbleService", "New message detected from $pkg")
                    packageStateMap[pkgId] = PackageState(title, text, msgTime, styleTime, messageCount)
                    isBubbleDismissed = false
                    dismissedPackages.remove(pkgId)
                    UnreadMessageManager.addMessage(
                        pkgId,
                        title,
                        text,
                        msgTime,
                        originalIntent,
                        actions,
                        avatarBitmap,
                        originalSmallIcon
                    )
                    
                    if (AppUtils.isAutoJumpEnabled(this@BubbleNotificationListenerService)) {
                        AppUtils.setPendingAutoJump(originalIntent, pkgId, title)
                    }
                }

                val isTakeOver = AppUtils.isTakeOverNotifications(this@BubbleNotificationListenerService)
                val shouldBeUpdate = !isNewMessage

                if (isTakeOver) {
                    cancelNotification(sbn.key)
                }

                val avatarIcon = avatarBitmap?.let { IconCompat.createWithBitmap(it) }

                if (isPerAppBubbles) {
                    var shouldUpdateBubble = false
                    synchronized(perAppStateLock) {
                        val wasActive = activePerAppBubbles.containsKey(pkgId)
                        val maxAllowed = maxPerAppBubbles()
                        if (isNewMessage && !wasActive) {
                            ensurePerAppBubbleCapacity(this@BubbleNotificationListenerService, maxAllowed)
                            shouldUpdateBubble = maxAllowed > 0
                        } else if (wasActive) {
                            shouldUpdateBubble = maxAllowed > 0
                            if (!shouldUpdateBubble) {
                                cancelPerAppBubbleLocked(this@BubbleNotificationListenerService, pkgId)
                            }
                        }
                        if (shouldUpdateBubble) {
                            updatePerAppBubble(
                                context = this@BubbleNotificationListenerService,
                                pkg = pkg,
                                pkgId = pkgId,
                                appName = appName,
                                title = title,
                                text = text,
                                msgTime = msgTime,
                                isUpdate = shouldBeUpdate,
                                isTakeOver = isTakeOver,
                                originalIntent = originalIntent,
                                originalSmallIcon = originalSmallIcon,
                                originalLargeIcon = originalLargeIcon,
                                avatarIcon = avatarIcon,
                                actions = actions,
                                suppressNotification = false
                            )
                            markActivePerAppBubble(pkgId)
                        }
                    }
                } else {
                    updateMainBubble(
                        context = this@BubbleNotificationListenerService,
                        pkg = pkg,
                        pkgId = pkgId,
                        appName = appName,
                        title = title,
                        text = text,
                        msgTime = msgTime,
                        isUpdate = shouldBeUpdate,
                        isTakeOver = isTakeOver,
                        originalIntent = originalIntent,
                        originalSmallIcon = originalSmallIcon,
                        originalLargeIcon = originalLargeIcon,
                        avatarIcon = avatarIcon,
                        actions = actions,
                        suppressNotification = false
                    )
                }
            }
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int
    ) {
        super.onNotificationRemoved(sbn, rankingMap, reason)

        if (sbn.packageName != packageName) {
            return
        }

        val ignoredProgrammaticCancellation = synchronized(perAppStateLock) {
            programmaticCancellationIds.remove(sbn.id)
        }
        if (ignoredProgrammaticCancellation) {
            AppLogger.d("BubbleService", "Ignored programmatic cancellation for notification ${sbn.id}")
            return
        }

        val isUserDismissal = reason == REASON_CANCEL ||
                reason == REASON_CANCEL_ALL ||
                reason == REASON_USER_STOPPED

        if (sbn.id == MAIN_BUBBLE_NOTIFICATION_ID && isUserDismissal) {
            isBubbleDismissed = true
            lastBubbleIntent = null
            lastBubbleIcon = null
            lastBuilder = null
            AppUtils.clearPendingAutoJump(null)
            AppLogger.d("BubbleService", "Main bubble was dismissed by user")
            return
        }

        synchronized(perAppStateLock) {
            val pkgId = notificationIdToPackage[sbn.id]
            if (pkgId != null && isUserDismissal) {
                dismissedPackages.add(pkgId)
                activePerAppBubbles.remove(pkgId)
                AppUtils.clearPendingAutoJump(pkgId)
                perAppBubbleData.remove(pkgId)
                notificationIdToPackage.remove(sbn.id)
                perAppNotificationIds.remove(pkgId)
                val shortcutId = perAppShortcutIds.remove(pkgId)
                if (shortcutId != null) {
                    try {
                        ShortcutManagerCompat.removeDynamicShortcuts(this, listOf(shortcutId))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                AppLogger.d("BubbleService", "Per-app bubble was dismissed by user: $pkgId")
            }
        }
    }


    private fun reconcilePerAppBubbleCapacity() {
        synchronized(perAppStateLock) {
            val maxAllowed = maxPerAppBubbles()
            while (activePerAppBubbles.size > maxAllowed) {
                val evictedPkgId = activePerAppBubbles.keys.firstOrNull { pkgId ->
                    !UnreadMessageManager.hasMessagesForPackage(pkgId)
                } ?: activePerAppBubbles.keys.first()
                cancelPerAppBubbleLocked(this@BubbleNotificationListenerService, evictedPkgId)
            }
        }
    }

    private fun maxPerAppBubbles(): Int {
        val otherBubbleCount = try {
            activeNotifications.count { sbn ->
                sbn.packageName != packageName && sbn.notification.getBubbleMetadata() != null
            }
        } catch (e: Exception) {
            0
        }
        return (TOTAL_BUBBLE_BUDGET - otherBubbleCount - RESERVED_BUBBLE_SLOTS_FOR_OTHER_APPS)
            .coerceAtLeast(MIN_PER_APP_BUBBLES)
    }
}








