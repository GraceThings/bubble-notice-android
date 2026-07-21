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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
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

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.size(56.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary)
                    )
                }

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
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
                showTrailingArrow = true,
                shape = middleShape,
                onClick = { uriHandler.openUri(PROJECT_URL) }
            )
            AboutListItem(
                icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                title = stringResource(R.string.about_bug_report),
                showTrailingArrow = true,
                shape = middleShape,
                onClick = { uriHandler.openUri(ISSUES_URL) }
            )
            AboutListItem(
                icon = { Icon(Icons.Default.Description, contentDescription = null) },
                title = stringResource(R.string.about_license),
                showTrailingArrow = true,
                shape = middleShape,
                onClick = { uriHandler.openUri(LICENSE_URL) }
            )
            AboutListItem(
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                title = stringResource(R.string.about_privacy_policy),
                showTrailingArrow = true,
                shape = middleShape,
                onClick = { uriHandler.openUri(PRIVACY_URL) }
            )
            AboutListItem(
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                title = stringResource(R.string.about_export_logs),
                showTrailingArrow = true,
                shape = bottomShape,
                onClick = { exportLogsLauncher.launch(logFileName) }
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
