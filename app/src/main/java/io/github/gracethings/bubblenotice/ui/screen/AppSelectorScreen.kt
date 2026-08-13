/*
 * Copyright (C) 2026 Grace Chan <velviagris@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.gracethings.bubblenotice.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import android.app.NotificationChannel
import io.github.gracethings.bubblenotice.service.BubbleNotificationListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.gracethings.bubblenotice.R
import io.github.gracethings.bubblenotice.model.AppItem
import io.github.gracethings.bubblenotice.util.AppUtils
import io.github.gracethings.bubblenotice.ui.theme.BubbleNoticeTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSelectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val coroutineScope = rememberCoroutineScope()

    var appList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var initialSelectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Personal, 1 = Work (0 = 个人，1 = 工作)

    LaunchedEffect(Unit) {
        if (!isPreview) {
            appList = AppUtils.loadInstalledApps(context)
            val currentSelected = AppUtils.getSelectedApps(context)
            selectedPackages = currentSelected
            initialSelectedPackages = currentSelected
            isLoading = false
        } else {
            // Preview data (预览数据)
            appList = listOf(
                AppItem("Settings", "com.android.settings", ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!, false),
                AppItem("Work App", "com.work.app", ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!, true)
            )
            selectedPackages = setOf("com.android.settings")
        }
    }

    val hasWorkApps = remember(appList) { appList.any { it.isWorkProfile } }
    
    // Filter by tab and search (通过标签页和搜索进行过滤)
    val displayedApps = remember(appList, initialSelectedPackages, selectedTab, searchQuery, hasWorkApps) {
        val filtered = appList.filter { app ->
            val matchTab = if (!hasWorkApps) true else {
                if (selectedTab == 0) !app.isWorkProfile else app.isWorkProfile
            }
            val matchSearch = app.name.contains(searchQuery, ignoreCase = true) || app.packageName.contains(searchQuery, ignoreCase = true)
            matchTab && matchSearch
        }
        
        val (selected, unselected) = filtered.partition { initialSelectedPackages.contains(it.id) }
        
        // Sort alphabetically (按字母顺序排序)
        val sortedSelected = selected.sortedBy { it.name }
        val sortedUnselected = unselected.sortedBy { it.name }
        
        sortedSelected + sortedUnselected
    }

    // Alphabet index mapping (字母索引映射)
    val alphabetMap = remember(displayedApps) {
        val map = mutableMapOf<String, Int>()
        displayedApps.forEachIndexed { index, app ->
            val firstChar = app.name.firstOrNull()?.uppercase() ?: "#"
            // If it's not A-Z, map to # (如果不是A-Z，则映射到 #)
            val key = if (firstChar.matches(Regex("[A-Z]"))) firstChar else "#"
            if (!map.containsKey(key)) {
                map[key] = index
            }
        }
        map
    }

    val alphabetList = ('A'..'Z').map { it.toString() } + listOf("#")
    val listState = rememberLazyListState()
    val expandedApps = remember { mutableStateListOf<String>() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar (顶部栏)
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

        // Search Bar (搜索栏)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        )

        // Tabs (标签页)
        if (hasWorkApps) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                ButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    overflowIndicator = { m -> ButtonGroupDefaults.OverflowIndicator(m) }
                ) {
                    toggleableItem(
                        checked = selectedTab == 0,
                        onCheckedChange = { selectedTab = 0 },
                        label = "Personal",
                        weight = 1f
                    )
                    toggleableItem(
                        checked = selectedTab == 1,
                        onCheckedChange = { selectedTab = 1 },
                        label = "Work",
                        weight = 1f
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator(modifier = Modifier.size(48.dp))
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 16.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(displayedApps) { _, app ->
                        val isSelected = selectedPackages.contains(app.id)
                        val isExpanded = expandedApps.contains(app.id)
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Card(
                                shape = RoundedCornerShape(if (isExpanded) 16.dp else 16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (isExpanded) expandedApps.remove(app.id) else expandedApps.add(app.id)
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
                                    supportingContent = { Text(app.packageName, style = MaterialTheme.typography.labelMedium) },
                                    trailingContent = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    val newSelection = selectedPackages.toMutableSet()
                                                    if (checked) newSelection.add(app.id) else newSelection.remove(app.id)
                                                    selectedPackages = newSelection
                                                    if (!isPreview) {
                                                        AppUtils.saveSelectedApps(context, newSelection)
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Expand"
                                            )
                                        }
                                    }
                                ) { Text(app.name, fontWeight = FontWeight.Bold) }
                            }
                            
                            AnimatedVisibility(
                                visible = isExpanded && isSelected,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                var disabledChannels by remember { mutableStateOf<Set<String>>(emptySet()) }
                                
                                // data class internally mapped to channel ID and Name
                                var displayChannels by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
                                
                                LaunchedEffect(app.id) {
                                    if (!isPreview) {
                                        disabledChannels = AppUtils.getDisabledChannels(context, app.id)
                                        withContext(Dispatchers.IO) {
                                            val userHandle = AppUtils.getUserHandle(context, app.isWorkProfile)
                                            try {
                                                val sysChannels = BubbleNotificationListenerService.instance?.getNotificationChannels(app.packageName, userHandle)
                                                if (sysChannels != null) {
                                                    displayChannels = sysChannels.map { Pair(it.id, it.name?.toString() ?: it.id) }.distinctBy { it.first }
                                                } else {
                                                    throw SecurityException("Null returned from getNotificationChannels")
                                                }
                                            } catch (e: SecurityException) {
                                                // Fallback to known channels if we lack privileges
                                                val knownIds = AppUtils.getKnownChannels(context, app.id)
                                                displayChannels = knownIds.map { Pair(it, it) }.distinctBy { it.first }
                                            }
                                        }
                                    }
                                }
                                
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                                        if (displayChannels == null) {
                                            if (!isPreview) {
                                                Text("Loading channels...", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                                            }
                                        } else if (displayChannels!!.isEmpty()) {
                                            Text(stringResource(R.string.selector_channels_empty), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                                        } else {
                                            displayChannels!!.forEach { (channelId, channelName) ->
                                                val isChannelEnabled = !disabledChannels.contains(channelId)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        if (isChannelEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                                        contentDescription = null,
                                                        modifier = Modifier.padding(end = 8.dp).size(20.dp),
                                                        tint = if (isChannelEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(channelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                    }
                                                    Switch(
                                                        checked = isChannelEnabled,
                                                        onCheckedChange = { enabled ->
                                                            val newDisabled = disabledChannels.toMutableSet()
                                                            if (enabled) newDisabled.remove(channelId) else newDisabled.add(channelId)
                                                            disabledChannels = newDisabled
                                                            if (!isPreview) {
                                                                AppUtils.setChannelDisabled(context, app.id, channelId, !enabled)
                                                            }
                                                        },
                                                        modifier = Modifier.scale(0.8f)
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

                // Alphabet scroll bar (字母滚动条)
                var dragActive by remember { mutableStateOf(false) }
                var currentDragChar by remember { mutableStateOf<String?>(null) }
                var currentDragY by remember { mutableStateOf(0f) }
                val density = LocalDensity.current
                
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(40.dp)
                        .padding(vertical = 16.dp)
                ) {
                    val itemHeightPx = with(density) { (maxHeight / alphabetList.size).toPx() }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        dragActive = true
                                        currentDragY = offset.y
                                        val index = (offset.y / itemHeightPx).toInt().coerceIn(0, alphabetList.size - 1)
                                        currentDragChar = alphabetList[index]
                                        val targetIdx = alphabetMap[currentDragChar]
                                        if (targetIdx != null) {
                                            coroutineScope.launch {
                                                listState.scrollToItem(targetIdx)
                                            }
                                        }
                                    },
                                    onDragEnd = { dragActive = false; currentDragChar = null },
                                    onDragCancel = { dragActive = false; currentDragChar = null }
                                ) { change, _ ->
                                    currentDragY = change.position.y
                                    val index = (change.position.y / itemHeightPx).toInt().coerceIn(0, alphabetList.size - 1)
                                    val char = alphabetList[index]
                                    if (char != currentDragChar) {
                                        currentDragChar = char
                                        val targetIdx = alphabetMap[char]
                                        if (targetIdx != null) {
                                            coroutineScope.launch {
                                                listState.scrollToItem(targetIdx)
                                            }
                                        }
                                    }
                                }
                            },
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        alphabetList.forEach { char ->
                            val isHighlighted = dragActive && currentDragChar == char
                            Text(
                                text = char,
                                fontSize = if (isHighlighted) 14.sp else 10.sp,
                                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                                color = if (isHighlighted) MaterialTheme.colorScheme.primary else if (alphabetMap.containsKey(char)) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val targetIdx = alphabetMap[char]
                                        if (targetIdx != null) {
                                            coroutineScope.launch {
                                                listState.scrollToItem(targetIdx)
                                            }
                                        }
                                    }
                            )
                        }
                    }

                    // The floating indicator (浮动指示器)
                    if (dragActive && currentDragChar != null) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(-80, currentDragY.roundToInt() - 60) }
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentDragChar ?: "",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
