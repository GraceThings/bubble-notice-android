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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.gracethings.bubblenotice.R
import io.github.gracethings.bubblenotice.model.AppItem
import io.github.gracethings.bubblenotice.util.AppUtils
import io.github.gracethings.bubblenotice.ui.theme.BubbleNoticeTheme

@Composable
fun AppSelectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current

    var appList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(!isPreview) }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            appList = AppUtils.loadInstalledApps(context)
            selectedPackages = AppUtils.getSelectedApps(context)
            isLoading = false
        } else {
            // 预览测试数据 / Preview-only test data.
            appList = listOf(
                AppItem("微信", "com.tencent.mm", ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!),
                AppItem("哔哩哔哩", "tv.danmaku.bili", ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!)
            )
            selectedPackages = setOf("com.tencent.mm")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.selector_back))
            }
            Text(
                text = stringResource(R.string.selector_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.selector_done), fontWeight = FontWeight.Bold)
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(appList) { app ->
                    val isSelected = selectedPackages.contains(app.packageName)
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                val newSelection = selectedPackages.toMutableSet()
                                if (isSelected) newSelection.remove(app.packageName) else newSelection.add(app.packageName)
                                selectedPackages = newSelection
                                if (!isPreview) {
                                    AppUtils.saveSelectedApps(context, newSelection)
                                }
                            }
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Image(
                                    bitmap = app.icon.toBitmap(100, 100).asImageBitmap(),
                                    contentDescription = app.name,
                                    modifier = Modifier.size(44.dp)
                                )
                            },
                            headlineContent = { Text(app.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(app.packageName, style = MaterialTheme.typography.labelMedium) },
                            trailingContent = { Switch(checked = isSelected, onCheckedChange = null) }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "AppSelectorScreen Preview")
@Composable
fun PreviewAppSelectorScreen() {
    BubbleNoticeTheme {
        Surface {
            AppSelectorScreen(onBack = {})
        }
    }
}
