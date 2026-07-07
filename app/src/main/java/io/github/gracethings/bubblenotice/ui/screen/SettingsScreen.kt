package io.github.gracethings.bubblenotice.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.gracethings.bubblenotice.R
import io.github.gracethings.bubblenotice.util.AppUtils
import io.github.gracethings.bubblenotice.util.ShizukuUtils
import io.github.gracethings.bubblenotice.ui.theme.BubbleNoticeTheme
import android.widget.Toast
import rikka.shizuku.Shizuku

@Composable
fun SettingsScreen(onNavigateToSelector: () -> Unit, onSendNotification: () -> Unit) {
    var selectedCount by remember { mutableStateOf(0) }
    var hasListenerPermission by remember { mutableStateOf(false) }
    var hasUsagePermission by remember { mutableStateOf(false) }
    var isTakeOver by remember { mutableStateOf(false) }
    var launchMode by remember { mutableStateOf("split") }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current

    if (!isPreview) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    selectedCount = AppUtils.getSelectedApps(context).size
                    val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
                    hasListenerPermission = enabledListeners.contains(context.packageName)
                    hasUsagePermission = AppUtils.hasUsageStatsPermission(context)
                    isTakeOver = AppUtils.isTakeOverNotifications(context)
                    launchMode = AppUtils.getLaunchMode(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // 监听 Shizuku 权限申请结果 / Listen to Shizuku permission request results.
        val shizukuListener = remember {
            Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == 101) {
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        AppUtils.setLaunchMode(context, "freeform")
                        launchMode = "freeform"
                        Toast.makeText(context, "Shizuku 授权成功，已启用小窗模式", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Shizuku 授权失败，无法启用小窗模式", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        DisposableEffect(Unit) {
            Shizuku.addRequestPermissionResultListener(shizukuListener)
            onDispose {
                Shizuku.removeRequestPermissionResultListener(shizukuListener)
            }
        }
    }

    val permissionLauncher = if (!isPreview) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) onSendNotification()
        }
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        SettingCard(
            title = stringResource(R.string.setting_permission_title),
            subtitle = if (hasListenerPermission) stringResource(R.string.setting_permission_granted) else stringResource(R.string.setting_permission_denied),
            subtitleColor = if (hasListenerPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            onClick = {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        )

        SettingCard(
            title = stringResource(R.string.setting_bubble_title),
            subtitle = stringResource(R.string.setting_bubble_desc),
            onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    onSendNotification()
                } else {
                    permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )

        SettingCard(
            title = stringResource(R.string.setting_prevent_dupsplit),
            subtitle = if (hasUsagePermission) stringResource(R.string.setting_usage_granted) else stringResource(R.string.setting_usage_denied),
            subtitleColor = if (hasUsagePermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            onClick = {
                val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                context.startActivity(intent)
            }
        )

        SettingSwitchCard(
            title = stringResource(R.string.setting_take_over_title),
            subtitle = stringResource(R.string.setting_take_over_desc),
            checked = isTakeOver,
            onCheckedChange = {
                isTakeOver = it
                AppUtils.setTakeOverNotifications(context, it)
            }
        )

        SettingCard(
            title = "回复启动模式",
            subtitle = if (launchMode == "freeform") "小窗模式 (需要 Shizuku 运行且授权)" else "分屏模式 (默认)",
            subtitleColor = if (launchMode == "freeform") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = {
                if (launchMode == "split") {
                    if (!ShizukuUtils.isShizukuAvailable()) {
                        Toast.makeText(context, "Shizuku 未运行，请先启动 Shizuku 应用", Toast.LENGTH_LONG).show()
                    } else if (Shizuku.isPreV11()) {
                        Toast.makeText(context, "不支持低于 v11 的 Shizuku 版本", Toast.LENGTH_LONG).show()
                    } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        AppUtils.setLaunchMode(context, "freeform")
                        launchMode = "freeform"
                        Toast.makeText(context, "已切换为小窗启动模式", Toast.LENGTH_SHORT).show()
                    } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                        Toast.makeText(context, "Shizuku 权限已被拒绝且不再询问，请前往系统设置或 Shizuku 应用中手动授予权限", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "正在向 Shizuku 请求授权...", Toast.LENGTH_SHORT).show()
                        try {
                            Shizuku.requestPermission(101)
                        } catch (e: Exception) {
                            Toast.makeText(context, "请求授权失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    AppUtils.setLaunchMode(context, "split")
                    launchMode = "split"
                    Toast.makeText(context, "已恢复为分屏启动模式", Toast.LENGTH_SHORT).show()
                }
            }
        )

        if (launchMode == "freeform") {
            var widthRatio by remember { mutableStateOf(AppUtils.getFreeformWidthRatio(context)) }
            var heightRatio by remember { mutableStateOf(AppUtils.getFreeformHeightRatio(context)) }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "小窗大小调节",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                widthRatio = 60
                                heightRatio = 60
                                AppUtils.setFreeformWidthRatio(context, 60)
                                AppUtils.setFreeformHeightRatio(context, 60)
                                Toast.makeText(context, "已恢复默认大小 (60% x 60%)", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "恢复默认大小",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "小窗宽度比例", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "$widthRatio%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = widthRatio.toFloat(),
                            onValueChange = {
                                widthRatio = it.toInt()
                                AppUtils.setFreeformWidthRatio(context, widthRatio)
                            },
                            valueRange = 30f..100f
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "小窗高度比例", style = MaterialTheme.typography.bodyMedium)
                            Text(text = "$heightRatio%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = heightRatio.toFloat(),
                            onValueChange = {
                                heightRatio = it.toInt()
                                AppUtils.setFreeformHeightRatio(context, heightRatio)
                            },
                            valueRange = 30f..100f
                        )
                    }
                }
            }
        }

        SettingCard(
            title = stringResource(R.string.setting_apps_title),
            subtitle = if (selectedCount > 0) stringResource(id = R.string.setting_apps_count, selectedCount) else stringResource(R.string.setting_apps_empty),
            onClick = onNavigateToSelector
        )
    }
}

@Composable
fun SettingCard(title: String, subtitle: String, subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(subtitle, color = subtitleColor) },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Enter",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
fun SettingSwitchCard(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCheckedChange(!checked) } // 点击卡片切换 / Toggle when the whole card is tapped.
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = null // 外层 Card 接管点击 / The outer Card owns click handling.
                )
            }
        )
    }
}

@Preview(showBackground = true, name = "SettingsScreen Preview")
@Composable
fun PreviewSettingsScreen() {
    BubbleNoticeTheme {
        Surface {
            SettingsScreen(onNavigateToSelector = {}, onSendNotification = {})
        }
    }
}
