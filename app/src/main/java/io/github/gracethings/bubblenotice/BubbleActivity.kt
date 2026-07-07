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
import io.github.gracethings.bubblenotice.util.ShizukuUtils
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

                    val messages by UnreadMessageManager.messagesFlow.collectAsState()
                    var selectedTab by remember { mutableStateOf(0) }

                    // 如果未读消息为空，则强制切换到应用列表 tab / If messages are empty, default to tab 1 (Apps).
                    val activeTab = if (messages.isEmpty()) 1 else selectedTab

                    Column(modifier = Modifier.fillMaxSize()) {
                        // 仅当有未读消息时才显示选项卡 / Show tabs only when there are unread messages.
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
                                            text = "未读消息 (${messages.size})",
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
                                            text = "快捷启动",
                                            fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 带动画切换面板和选择器 / Animate content switching.
                        Crossfade(targetState = activeTab, label = "BubbleTabTransition") { tab ->
                            when (tab) {
                                0 -> UnreadMessagesDashboard(
                                    isLandscape = isLandscape,
                                    onSplitReply = { pkg ->
                                        val launchMode = AppUtils.getLaunchMode(this@BubbleActivity)
                                        if (launchMode == "freeform" && ShizukuUtils.isShizukuAvailable() && ShizukuUtils.isShizukuPermissionGranted()) {
                                            launchFreeformDirectly(pkg)
                                        } else {
                                            launchTrampoline(pkg)
                                        }
                                    }
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

    // 提取 Intent 中的消息详情并加入未读消息管理器 / Extract message details and add them to the manager.
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
        isLandscape: Boolean,
        onSplitReply: (String) -> Unit
    ) {
        val context = LocalContext.current
        val messages by UnreadMessageManager.messagesFlow.collectAsState()

        var launchMode by remember { mutableStateOf("split") }
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    launchMode = AppUtils.getLaunchMode(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val grouped = remember(messages) {
            messages.groupBy { it.packageName to it.senderName }
                .map { (key, msgList) ->
                    val sortedMsgs = msgList.sortedByDescending { it.timestamp }
                    val latestTimestamp = sortedMsgs.firstOrNull()?.timestamp ?: 0L
                    SenderGroup(
                        packageName = key.first,
                        senderName = key.second,
                        messages = sortedMsgs,
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
            // 顶部操作栏 / Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "未读消息清单",
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
                        contentDescription = "Clear All",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "全部清除",
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
                        modifier = Modifier.fillMaxWidth(),
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

                                // 移除该联系人所有未读 / Clear messages for this sender
                                IconButton(
                                    onClick = {
                                        UnreadMessageManager.clearMessagesForSender(group.packageName, group.senderName)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // 分屏回复 / Split Reply
                                FilledTonalButton(
                                    onClick = {
                                        onSplitReply(group.packageName)
                                        UnreadMessageManager.clearMessagesForSender(group.packageName, group.senderName)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Launch,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (launchMode == "freeform") "小窗" else "分屏",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 该发送人期间的所有未读消息内容卡片 / Message content box -> Message Bubbles
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

    private fun launchTrampoline(targetPackage: String) {
        val intent = Intent(this, TrampolineActivity::class.java).apply {
            putExtra("EXTRA_TARGET_PACKAGE", targetPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchFreeformDirectly(targetPackage: String) {
        var widthRatio = AppUtils.getFreeformWidthRatio(this)
        var heightRatio = AppUtils.getFreeformHeightRatio(this)

        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val temp = widthRatio
            widthRatio = heightRatio
            heightRatio = temp
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val width = (screenWidth * widthRatio / 100).coerceIn(100, screenWidth)
        val height = (screenHeight * heightRatio / 100).coerceIn(100, screenHeight)
        val left = (screenWidth - width) / 2
        val top = (screenHeight - height) / 2
        val right = left + width
        val bottom = top + height

        lifecycleScope.launch(Dispatchers.IO) {
            val success = ShizukuUtils.launchInFreeform(this@BubbleActivity, targetPackage, left, top, right, bottom)
            withContext(Dispatchers.Main) {
                if (success) {
                    moveTaskToBack(true)
                } else {
                    // 失败时回退到分屏模式启动 / Fallback to TrampolineActivity if freeform launch fails
                    launchTrampoline(targetPackage)
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

        // 刷新触发器 / Refresh trigger for app list reloads.
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

        // 回到前台时刷新应用列表 / Reload selected apps whenever the bubble returns to foreground.
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
                    text = "暂无快捷启动应用",
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
                                val launchMode = AppUtils.getLaunchMode(context)
                                if (launchMode == "freeform" && ShizukuUtils.isShizukuAvailable() && ShizukuUtils.isShizukuPermissionGranted()) {
                                    launchFreeformDirectly(app.packageName)
                                } else {
                                    launchAppInHalfScreen(app.packageName, isLandscape)
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

    private fun launchAppInHalfScreen(packageName: String, isLandscape: Boolean) {
        // 使用生命周期协程 / Launch work in the Activity lifecycle scope.
        lifecycleScope.launch {

            // 前台冲突时退出 / Abort if the target app is already foreground.
            if (AppUtils.hasUsageStatsPermission(this@BubbleActivity)) {
                if (AppUtils.isAppInForeground(this@BubbleActivity, packageName)) {
                    Toast.makeText(this@BubbleActivity, getString(R.string.toast_cannot_split), Toast.LENGTH_SHORT).show()
                    moveTaskToBack(true)
                    return@launch
                }
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return@launch

            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
            )

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val bounds = if (isLandscape) {
                Rect(0, 0, screenWidth / 2, screenHeight)
            } else {
                Rect(0, 0, screenWidth, screenHeight / 2)
            }

            val options = ActivityOptions.makeBasic().setLaunchBounds(bounds)

            try {
                startActivity(launchIntent, options.toBundle())
                moveTaskToBack(true)
            } catch (e: Exception) {
                e.printStackTrace()
                startActivity(launchIntent)
                moveTaskToBack(true)
            }
        }
    }
}
