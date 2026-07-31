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

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CleaningServices
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.gracethings.bubblenotice.BuildConfig
import io.github.gracethings.bubblenotice.R
import io.github.gracethings.bubblenotice.ui.theme.BubbleNoticeTheme
import io.github.gracethings.bubblenotice.util.AppLogger
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PROJECT_URL = "https://github.com/GraceThings/bubble-notice-android"
private const val ISSUES_URL = "https://github.com/GraceThings/bubble-notice-android/issues"
private const val LICENSE_URL = "https://github.com/GraceThings/bubble-notice-android/blob/master/LICENSE.txt"
private const val PRIVACY_URL = "https://github.com/GraceThings/bubble-notice-android/blob/master/SECURITY.md"

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val dateFormat = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()) }
    val logFileName = remember { "BubbleNotice_Logs_${dateFormat.format(Date())}.txt" }

    var isIconVisible by remember { mutableStateOf(false) }
    var isFlyoutVisible by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300) // Wait for page transition
        isIconVisible = true
        delay(150) // Wait for icon to start popping up before showing flyout
        isFlyoutVisible = true
    }

    val exportLogsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val logFile = AppLogger.getLogFile()
            if (logFile != null && logFile.exists()) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(logFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    Toast.makeText(context, R.string.toast_logs_exported, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    AppLogger.e("AboutScreen", "Failed to export logs", e)
                }
            } else {
                Toast.makeText(context, R.string.toast_logs_empty, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
        )

        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        
        val iconScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isIconVisible) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "IconScale"
        )
        
        val flyoutScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isFlyoutVisible) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "FlyoutScale"
        )
        
        val flyoutAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isFlyoutVisible) 1f else 0f,
            animationSpec = tween(300),
            label = "FlyoutAlpha"
        )

        val pressScale by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isPressed) 0.95f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "PressScale"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {}
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Round Icon
            if (iconScale > 0f) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(48.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(64.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Bubble Notification Flyout
            if (flyoutAlpha > 0f) {
                Card(
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 24.dp,
                        bottomEnd = 24.dp,
                        bottomStart = 24.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer {
                            scaleX = flyoutScale
                            scaleY = flyoutScale
                            alpha = flyoutAlpha
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            val middleShape = RoundedCornerShape(4.dp)
            val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

            AboutListItem(
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                title = stringResource(R.string.about_developer_label),
                subtitle = stringResource(R.string.about_developer_name),
                showTrailingArrow = false,
                shape = topShape
            )
            AboutListItem(
                icon = { Icon(Icons.Default.Code, contentDescription = null) },
                title = stringResource(R.string.about_project_url),
                subtitle = stringResource(R.string.about_subtitle_project_url),
                showTrailingArrow = true,
                shape = middleShape,
                onClick = { uriHandler.openUri(PROJECT_URL) }
            )
            AboutListItem(
                icon = { Icon(Icons.Default.Description, contentDescription = null) },
                title = stringResource(R.string.about_license),
                subtitle = stringResource(R.string.about_subtitle_license),
                showTrailingArrow = true,
                shape = middleShape,
                onClick = { uriHandler.openUri(LICENSE_URL) }
            )
            AboutListItem(
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                title = stringResource(R.string.about_privacy_policy),
                subtitle = stringResource(R.string.about_subtitle_privacy_policy),
                showTrailingArrow = true,
                shape = bottomShape,
                onClick = { uriHandler.openUri(PRIVACY_URL) }
            )
        }

        // Feedback 分区 / Feedback section
        Text(
            text = stringResource(R.string.about_section_feedback),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

            AboutListItem(
                icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                title = stringResource(R.string.about_bug_report),
                subtitle = stringResource(R.string.about_subtitle_bug_report),
                showTrailingArrow = true,
                shape = topShape,
                onClick = { uriHandler.openUri(ISSUES_URL) }
            )
            AboutListItem(
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                title = stringResource(R.string.about_export_logs),
                subtitle = stringResource(R.string.about_subtitle_export_logs),
                showTrailingArrow = true,
                shape = bottomShape,
                onClick = { exportLogsLauncher.launch(logFileName) }
            )
        }

        // Debug 分区 / Debug section
        Text(
            text = stringResource(R.string.about_section_debug),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val singleShape = RoundedCornerShape(24.dp)

            AboutListItem(
                icon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                title = stringResource(R.string.about_clear_bubble_cache),
                subtitle = stringResource(R.string.about_subtitle_clear_bubble_cache),
                showTrailingArrow = false,
                shape = singleShape,
                onClick = { showClearCacheDialog = true }
            )
        }

        if (showClearCacheDialog) {
            AlertDialog(
                onDismissRequest = { showClearCacheDialog = false },
                title = { Text(text = stringResource(R.string.dialog_clear_cache_title)) },
                text = { Text(text = stringResource(R.string.dialog_clear_cache_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showClearCacheDialog = false
                        try {
                            val intent = Intent("android.settings.NOTIFICATION_SETTINGS")
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_SETTINGS)
                            context.startActivity(intent)
                        }
                    }) {
                        Text(text = stringResource(R.string.dialog_clear_cache_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheDialog = false }) {
                        Text(text = stringResource(R.string.dialog_clear_cache_cancel))
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AboutListItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    showTrailingArrow: Boolean = false,
    shape: Shape = RoundedCornerShape(4.dp),
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.padding(end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showTrailingArrow) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "AboutScreen Preview")
@Composable
fun PreviewAboutScreen() {
    BubbleNoticeTheme {
        Surface {
            AboutScreen()
        }
    }
}
