package com.velviagris.bubblesplit

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
import com.velviagris.bubblesplit.model.AppItem
import com.velviagris.bubblesplit.util.AppUtils
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Launch

data class ActiveMessage(
    val packageName: String,
    val senderName: String,
    val messageText: String
)

class BubbleActivity : ComponentActivity() {

    // 追踪当前活跃的消息内容 / Track current active message details.
    private var activeMessage by mutableStateOf<ActiveMessage?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    val config = LocalConfiguration.current
                    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

                    // 带动画切换微型消息面板和应用选择列表 / Animate transitions between panel and selector.
                    Crossfade(targetState = activeMessage, label = "BubbleContentTransition") { msg ->
                        if (msg != null) {
                            MicroMessagePanel(
                                activeMsg = msg,
                                isLandscape = isLandscape,
                                onSplitReply = { launchTrampoline(msg.packageName) },
                                onBackToAppList = { activeMessage = null }
                            )
                        } else {
                            AppSelectionContent(isLandscape)
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

    // 提取 Intent 中的消息详情 / Extract message details from intent.
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val pkg = intent.getStringExtra("EXTRA_PACKAGE_NAME")
        val title = intent.getStringExtra("EXTRA_TITLE")
        val text = intent.getStringExtra("EXTRA_TEXT")
        if (pkg != null && title != null && text != null) {
            activeMessage = ActiveMessage(pkg, title, text)
        }
    }

    @Composable
    private fun MicroMessagePanel(
        activeMsg: ActiveMessage,
        isLandscape: Boolean,
        onSplitReply: () -> Unit,
        onBackToAppList: () -> Unit
    ) {
        val context = LocalContext.current
        val appIcon = remember(activeMsg.packageName) {
            try {
                context.packageManager.getApplicationIcon(activeMsg.packageName).toBitmap(144, 144).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
        val appName = remember(activeMsg.packageName) {
            AppUtils.getAppName(context, activeMsg.packageName)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            // 返回应用选择列表按钮 / Back button to return to app selector.
            IconButton(
                onClick = onBackToAppList,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                    contentDescription = "Back to Apps",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    // 应用图标 / App Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .padding(12.dp)
                    ) {
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = appName,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 联系人名称 / Sender
                    Text(
                        text = activeMsg.senderName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 消息气泡卡片 / Message bubble
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = activeMsg.messageText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 分屏回复动作按钮 / Split screen reply action button
                Button(
                    onClick = onSplitReply,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Launch,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "分屏回复",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
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
            activeMessage = null // 启动后清空状态 / Reset state after launch.
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.bubble_empty_state),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredAppList) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { launchAppInHalfScreen(app.packageName, isLandscape) }
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = app.icon.toBitmap(120, 120).asImageBitmap(),
                            contentDescription = app.name,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.labelMedium,
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
