package io.github.gracethings.bubblenotice.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.gracethings.bubblenotice.MainActivity
import io.github.gracethings.bubblenotice.service.BubbleNotificationListenerService
import io.github.gracethings.bubblenotice.util.AppLogger
import io.github.gracethings.bubblenotice.util.AppUtils
import io.github.gracethings.bubblenotice.util.UnreadMessageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d("NotificationActionReceiver", "Received intent: ")
        if (intent.action == "io.github.gracethings.bubblenotice.ACTION_LAUNCH_APP") {
            val pkg = intent.getStringExtra("EXTRA_PACKAGE_NAME")
            val senderName = intent.getStringExtra("EXTRA_SENDER_NAME")
            val originalIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("EXTRA_ORIGINAL_INTENT", android.app.PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("EXTRA_ORIGINAL_INTENT") as? android.app.PendingIntent
            }
            if (pkg != null) {
                val sentOriginalIntent = originalIntent != null &&
                    AppUtils.sendPendingIntentAllowed(context, originalIntent)
                if (senderName != null) {
                    UnreadMessageManager.clearMessagesForSender(pkg, senderName)
                } else {
                    UnreadMessageManager.clearMessagesForPackage(pkg)
                }
                BubbleNotificationListenerService.suppressNotificationInShade(context, pkg)
                BubbleNotificationListenerService.autoCloseBubbleIfEmpty(context, pkg)

                val pendingResult = goAsync()
                val appContext = context.applicationContext
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        AppUtils.ensureAppForegroundAfterLaunch(
                            appContext,
                            pkg,
                            sentOriginalIntent
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                MainActivity.sendBubbleNotification(context)
            }
        }
    }
}
