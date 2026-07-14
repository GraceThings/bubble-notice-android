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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

private const val PROJECT_URL = "https://github.com/GraceThings/bubble-notice-android"
private const val ISSUES_URL = "https://github.com/GraceThings/bubble-notice-android/issues"

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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )

        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.about_version_format, BuildConfig.VERSION_NAME),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                AboutListItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    title = stringResource(R.string.about_developer_label),
                    subtitle = stringResource(R.string.about_developer_name)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutListItem(
                    icon = { Icon(Icons.Default.Code, contentDescription = null) },
                    title = stringResource(R.string.about_project_url),
                    subtitle = PROJECT_URL,
                    modifier = Modifier.clickable { uriHandler.openUri(PROJECT_URL) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutListItem(
                    icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    title = stringResource(R.string.about_bug_report),
                    subtitle = ISSUES_URL,
                    modifier = Modifier.clickable { uriHandler.openUri(ISSUES_URL) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AboutListItem(
                    icon = { Icon(Icons.Default.Download, contentDescription = null) },
                    title = stringResource(R.string.about_export_logs),
                    subtitle = logFileName,
                    modifier = Modifier.clickable {
                        exportLogsLauncher.launch(logFileName)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AboutListItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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

