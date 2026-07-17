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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val coroutineScope = rememberCoroutineScope()

    var appList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(!isPreview) }
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Personal, 1 = Work

    LaunchedEffect(Unit) {
        if (!isPreview) {
            appList = AppUtils.loadInstalledApps(context)
            selectedPackages = AppUtils.getSelectedApps(context)
            isLoading = false
        } else {
            // Preview data
            appList = listOf(
                AppItem("Settings", "com.android.settings", ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!, false),
                AppItem("Work App", "com.work.app", ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!, true)
            )
            selectedPackages = setOf("com.android.settings")
        }
    }

    val hasWorkApps = remember(appList) { appList.any { it.isWorkProfile } }
    
    // Filter by tab and search
    val displayedApps = remember(appList, selectedPackages, selectedTab, searchQuery, hasWorkApps) {
        val filtered = appList.filter { app ->
            val matchTab = if (!hasWorkApps) true else {
                if (selectedTab == 0) !app.isWorkProfile else app.isWorkProfile
            }
            val matchSearch = app.name.contains(searchQuery, ignoreCase = true) || app.packageName.contains(searchQuery, ignoreCase = true)
            matchTab && matchSearch
        }
        
        val (selected, unselected) = filtered.partition { selectedPackages.contains(it.packageName) }
        
        // Sort alphabetically
        val sortedSelected = selected.sortedBy { it.name }
        val sortedUnselected = unselected.sortedBy { it.name }
        
        sortedSelected + sortedUnselected
    }

    // Alphabet index mapping
    val alphabetMap = remember(displayedApps) {
        val map = mutableMapOf<String, Int>()
        displayedApps.forEachIndexed { index, app ->
            val firstChar = app.name.firstOrNull()?.uppercase() ?: "#"
            // If it's not A-Z, map to #
            val key = if (firstChar.matches(Regex("[A-Z]"))) firstChar else "#"
            if (!map.containsKey(key)) {
                map[key] = index
            }
        }
        map
    }

    val alphabetList = ('A'..'Z').map { it.toString() } + listOf("#")
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
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

        // Search Bar
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

        // Tabs
        if (hasWorkApps) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Personal") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Work") }
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
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

                // Alphabet scroll bar
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

                    // The floating indicator
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
