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
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColor
import androidx.compose.ui.draw.scale
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import android.widget.Toast
import io.github.gracethings.bubblenotice.ui.theme.BubbleNoticeTheme

@Composable
fun SettingsScreen(onNavigateToSelector: () -> Unit, onSendNotification: () -> Unit) {
    var selectedCount by remember { mutableStateOf(0) }
    var hasListenerPermission by remember { mutableStateOf(false) }
    var isTakeOver by remember { mutableStateOf(false) }
    var isAutoJump by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }

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
                    isTakeOver = AppUtils.isTakeOverNotifications(context)
                    isAutoJump = AppUtils.isAutoJumpEnabled(context)
                    
                    val prefs = context.getSharedPreferences("bubble_prefs", android.content.Context.MODE_PRIVATE)
                    val guideShown = prefs.getBoolean("permission_guide_shown", false)
                    if (!hasListenerPermission && !guideShown) {
                        showGuideDialog = true
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
    val permissionLauncher = if (!isPreview) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) onSendNotification()
        }
    } else null

    val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val middleShape = RoundedCornerShape(4.dp)
    val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        // removed verticalArrangement = Arrangement.spacedBy(12.dp) to use our custom groups
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge, // changed to headlineLarge to match about screen title
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        // 常规 (General) Group
        Text(
            text = stringResource(R.string.setting_group_general),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 8.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingCard(
                title = stringResource(R.string.setting_permission_title),
                subtitle = if (hasListenerPermission) stringResource(R.string.setting_permission_granted) else stringResource(R.string.setting_permission_denied),
                subtitleColor = if (hasListenerPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                highlight = !hasListenerPermission,
                shape = topShape,
                onClick = {
                    val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                }
            )

            SettingCard(
                title = stringResource(R.string.setting_bubble_title),
                subtitle = stringResource(R.string.setting_bubble_desc),
                shape = bottomShape,
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        onSendNotification()
                    } else {
                        permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }

        // 个性化 (Personalization) Group
        Text(
            text = stringResource(R.string.setting_group_personalization),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingSwitchCard(
                title = stringResource(R.string.setting_auto_jump_title),
                subtitle = stringResource(R.string.setting_auto_jump_desc),
                checked = isAutoJump,
                shape = topShape,
                onCheckedChange = {
                    isAutoJump = it
                    AppUtils.setAutoJumpEnabled(context, it)
                }
            )

            SettingSwitchCard(
                title = stringResource(R.string.setting_take_over_title),
                subtitle = stringResource(R.string.setting_take_over_desc),
                checked = isTakeOver,
                shape = middleShape,
                onCheckedChange = {
                    isTakeOver = it
                    AppUtils.setTakeOverNotifications(context, it)
                }
            )

            SettingCard(
                title = stringResource(R.string.setting_apps_title),
                subtitle = if (selectedCount > 0) stringResource(id = R.string.setting_apps_count, selectedCount) else stringResource(R.string.setting_apps_empty),
                shape = bottomShape,
                onClick = onNavigateToSelector
            )
        }
    }

    if (showGuideDialog) {
        PermissionGuideDialog(
            onDismiss = {
                val prefs = context.getSharedPreferences("bubble_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("permission_guide_shown", true).apply()
                showGuideDialog = false
            },
            onGoToSettings = {
                val prefs = context.getSharedPreferences("bubble_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("permission_guide_shown", true).apply()
                showGuideDialog = false
                val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        )
    }
}

@Composable
fun SettingCard(
    title: String, 
    subtitle: String, 
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, 
    highlight: Boolean = false, 
    shape: Shape = RoundedCornerShape(4.dp),
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (highlight) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val color by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        targetValue = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color"
    )

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (highlight) color else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (highlight) scale else 1f)
            .clip(shape)
            .clickable { onClick() }
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(subtitle, color = subtitleColor) },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.desc_enter),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}

@Composable
fun SettingSwitchCard(
    title: String, 
    subtitle: String, 
    checked: Boolean, 
    shape: Shape = RoundedCornerShape(4.dp),
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
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
