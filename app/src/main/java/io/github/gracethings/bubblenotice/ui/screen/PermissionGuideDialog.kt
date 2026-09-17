package io.github.gracethings.bubblenotice.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.gracethings.bubblenotice.R
import kotlinx.coroutines.delay

@Composable
fun PermissionGuideDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.setting_permission_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please follow the guide below to enable notification access for Bubble Notice, which is required for bubbles to work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 动画区域 / Animation area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    PermissionAnimationSimulation()
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Later")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onGoToSettings) {
                        Text("Go to Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionAnimationSimulation() {
    var step by remember { mutableStateOf(0) }
    
    // 手势位置 / Hand position
    val handX by animateFloatAsState(
        targetValue = when (step) {
            0 -> 0.5f // 从底部居中开始 / Start at center bottom
            1, 2 -> 0.5f // 移动到 "Bubble Notice" 列表项 / Move to the Bubble Notice list item
            3, 4 -> 0.8f // 移动到开关 / Move to the switch
            5, 6 -> 0.7f // 移动到对话框上的允许按钮 / Move to the Allow button on the dialog
            else -> 0.5f
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = ""
    )
    
    val handY by animateFloatAsState(
        targetValue = when (step) {
            0 -> 1.0f
            1, 2 -> 0.4f // 列表项 / List item
            3, 4 -> 0.3f // 开关 / Switch
            5, 6 -> 0.65f // 允许按钮 / Allow button
            else -> 1.0f
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = ""
    )
    
    // 手势点击缩放 / Hand click scale
    val handScale by animateFloatAsState(
        targetValue = if (step == 2 || step == 4 || step == 6) 0.8f else 1.2f,
        animationSpec = tween(200), label = ""
    )
    
    // 开关状态 / Switch state
    val switchChecked = step >= 4

    LaunchedEffect(Unit) {
        while (true) {
            step = 0; delay(500)
            step = 1; delay(800) // 移动到列表项 / Move to the list item
            step = 2; delay(300) // 点击列表项 / Click the list item
            step = 3; delay(800) // 移动到开关 / Move to the switch
            step = 4; delay(300) // 点击开关 / Click the switch
            step = 5; delay(800) // 移动到允许按钮 / Move to the Allow button
            step = 6; delay(300) // 点击允许 / Click Allow
            step = 7; delay(1500) // 保持 / Hold
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        
        // 屏幕 1：应用列表 / Screen 1: App list
        AnimatedVisibility(
            visible = step < 3,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text("Device & app notifications", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (switchChecked) "Allowed" else "Not allowed", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Other App", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Not allowed", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        
        // 屏幕 2：应用详情 / Screen 2: App detail
        AnimatedVisibility(
            visible = step >= 3,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.app_name), modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Allow notification access", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = switchChecked, onCheckedChange = null)
                }
            }
        }
        
        // 屏幕 3：系统弹窗覆盖层 / Screen 3: System dialog overlay
        AnimatedVisibility(
            visible = step >= 5,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Allow Bubble Notice?", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("This app will be able to read all notifications.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text("Deny", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Allow", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 手形光标 / Hand cursor
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = (w * handX) - 24.dp,
                    y = (h * handY) - 24.dp
                )
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .scale(handScale),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}
