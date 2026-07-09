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

import android.app.ActivityOptions
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import io.github.gracethings.bubblenotice.model.AppItem
import io.github.gracethings.bubblenotice.util.AppUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.Crossfade
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight
import io.github.gracethings.bubblenotice.util.UnreadMessageManager
import io.github.gracethings.bubblenotice.ui.theme.BubbleNoticeTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Apps

data class SenderGroup(
    val packageName: String,
    val senderName: String,
    val messages: List<UnreadMessageManager.Message>,
    val latestTimestamp: Long
)

class BubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            BubbleNoticeTheme(dynamicColor = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val config = LocalConfiguration.current
                    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    val coroutineScope = rememberCoroutineScope()

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val pendingIntent = AppUtils.consumePendingAutoJump()
                                if (pendingIntent != null) {
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(150) // Wait for bubble expansion animation to finish
                                        try {
                                            AppUtils.sendPendingIntentAllowed(this@BubbleActivity, pendingIntent)
                                            moveTaskToBack(true)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    val messages by UnreadMessageManager.messagesFlow.collectAsState()
                    var selectedTab by remember { mutableStateOf(0) }

                    // 如果未读消息为空，则强制切换到应用列�?tab / If messages are empty, default to tab 1 (Apps).
                    val activeTab = if (messages.isEmpty()) 1 else selectedTab

                    Column(modifier = Modifier.fillMaxSize()) {
                        // 仅当有未读消息时才显示选项�?/ Show tabs only when there are unread messages.
                        if (messages.isNotEmpty()) {
                            SecondaryTabRow(
                                selectedTabIndex = activeTab,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.primary,
                                indicator = {
                                    TabRowDefaults.SecondaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(activeTab),
                                        color = MaterialTheme.colorScheme.primary,
                                        height = 3.dp
                                    )
                                },
                                divider = {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            ) {
                                Tab(
                                    selected = activeTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = {
                                        Text(
                                            text = stringResource(R.string.tab_unread_messages, messages.size),
                                            fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Tab(
                                    selected = activeTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = {
                                        Text(
                                            text = stringResource(R.string.tab_quick_launch),
                                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 带动画切换面板和选择�?/ Animate content switching.
                        Crossfade(targetState = activeTab, label = "BubbleTabTransition") { tab ->
                            when (tab) {
                                0 -> UnreadMessagesDashboard(
                                    isLandscape = isLandscape
                                )
                                1 -> AppSelectionContent(isLandscape)
                            }
                        }
                    }
                }
            }
        }
    }

    // 气泡后台再次打开 / Called when the background bubble is opened again.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val pkg = intent.getStringExtra("EXTRA_PACKAGE_NAME")
        val title = intent.getStringExtra("EXTRA_TITLE")
        val text = intent.getStringExtra("EXTRA_TEXT")
        if (pkg != null && title != null && text != null) {
            UnreadMessageManager.addMessage(pkg, title, text, System.currentTimeMillis())
        }
    }

    @Composable
    private fun UnreadMessagesDashboard(
        isLandscape: Boolean
    ) {
        val context = LocalContext.current
        val messages by UnreadMessageManager.messagesFlow.collectAsState()

        val grouped = remember(messages) {
            messages.groupBy { it.packageName to it.senderName }
                .map { (key, msgList) ->
                    val sortedMsgs = msgList.sortedByDescending { it.timestamp }
                    val latestTimestamp = sortedMsgs.firstOrNull()?.timestamp ?: 0L
                    SenderGroup(
                        packageName = key.first,
                        senderName = key.second,
                        messages = sortedMsgs.take(3),
                        latestTimestamp = latestTimestamp
                    )
                }
                .sortedByDescending { it.latestTimestamp }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.06f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 顶部操作�?/ Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.title_unread_messages),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = { UnreadMessageManager.clearAll() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.btn_clear_all),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_clear_all),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 未读消息列表 / Scrollable list of groups
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(grouped) { group ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val pendingIntent = group.messages.firstOrNull()?.contentIntent
                                if (pendingIntent != null) {
                                    AppUtils.sendPendingIntentAllowed(context, pendingIntent)
                                } else {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(group.packageName)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                    }
                                }
                                UnreadMessageManager.clearMessagesForSender(group.packageName, group.senderName)
                                (context as? android.app.Activity)?.moveTaskToBack(true)
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            // 发送人头部信息 / Sender details header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val appIcon = remember(group.packageName) {
                                    try {
                                        context.packageManager.getApplicationIcon(group.packageName).toBitmap(96, 96).asImageBitmap()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (appIcon != null) {
                                    Image(
                                        bitmap = appIcon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = group.senderName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val appName = remember(group.packageName) {
                                        AppUtils.getAppName(context, group.packageName)
                                    }
                                    Text(
                                        text = appName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // 移除该联系人所有未�?/ Clear messages for this sender
                                IconButton(
                                    onClick = {
                                        UnreadMessageManager.clearMessagesForSender(group.packageName, group.senderName)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.btn_clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 该发送人期间的所有未读消息内容卡�?/ Message content box -> Message Bubbles
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                group.messages.forEach { msg ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(
                                                    topStart = 4.dp,
                                                    topEnd = 16.dp,
                                                    bottomEnd = 16.dp,
                                                    bottomStart = 16.dp
                                                )
                                            )
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = msg.messageText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    @Composable
    private fun AppSelectionContent(isLandscape: Boolean) {
        var filteredAppList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }

        val context = LocalContext.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        // 刷新触发�?/ Refresh trigger for app list reloads.
        var refreshTrigger by remember { mutableStateOf(0) }

        // 监听气泡展开 / Increment the trigger whenever the bubble resumes.
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshTrigger++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        // 回到前台时刷新应用列�?/ Reload selected apps whenever the bubble returns to foreground.
        LaunchedEffect(refreshTrigger) {
            isLoading = true
            val selectedPackages = AppUtils.getSelectedApps(context)
            filteredAppList = AppUtils.loadSelectedAppsOnly(context, selectedPackages)
            isLoading = false
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredAppList.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.empty_quick_launch),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.bubble_empty_state),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 90.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAppList) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(launchIntent)
                                    (context as? android.app.Activity)?.moveTaskToBack(true)
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 1.dp,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Image(
                                    bitmap = app.icon.toBitmap(120, 120).asImageBitmap(),
                                    contentDescription = app.name,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }


}
