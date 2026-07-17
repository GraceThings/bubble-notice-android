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
package io.github.gracethings.bubblenotice
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.animation.core.*

import androidx.compose.material.icons.filled.PushPin

import androidx.compose.foundation.lazy.grid.GridItemSpan

import androidx.compose.foundation.combinedClickable

import androidx.compose.foundation.ExperimentalFoundationApi

import android.app.ActivityOptions
import android.content.Context
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

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
import io.github.gracethings.bubblenotice.model.AppItem
import io.github.gracethings.bubblenotice.util.AppUtils
import io.github.gracethings.bubblenotice.util.AppLogger
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.Crossfade
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.font.FontWeight
import io.github.gracethings.bubblenotice.util.UnreadMessageManager
import io.github.gracethings.bubblenotice.ui.theme.BubbleNoticeTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import io.github.gracethings.bubblenotice.util.TimeUtils
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.SpringSpec

data class SenderGroup(
    val packageName: String,
    val senderName: String,
    val messages: List<UnreadMessageManager.Message>,
    val latestTimestamp: Long
)

enum class RevealValue { Default, Revealed }

class BubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            BubbleNoticeTheme(dynamicColor = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val config = LocalConfiguration.current
                    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    val coroutineScope = rememberCoroutineScope()
                    val context = LocalContext.current

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                val pendingIntent = AppUtils.consumePendingAutoJump()
                                if (pendingIntent != null) {
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(150)
                                        try {
                                            AppUtils.sendPendingIntentAllowed(this@BubbleActivity, pendingIntent)
                                            moveTaskToBack(true)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    val messages by UnreadMessageManager.messagesFlow.collectAsState()
                    var selectedTab by remember { mutableStateOf(if (messages.isEmpty()) 1 else 0) }
                    var showAppSelector by remember { mutableStateOf(false) }

                    // Sync state if messages list becomes empty/populated? 
                    // To keep it simple, we only set it on launch. User can switch manually.

                    Box(modifier = Modifier.fillMaxSize()) {
                        
                        Box(modifier = Modifier.fillMaxSize().padding(bottom = if (showAppSelector) 0.dp else 80.dp)) {
                            if (showAppSelector) {
                                io.github.gracethings.bubblenotice.ui.screen.AppSelectorScreen(
                                    onBack = { showAppSelector = false }
                                )
                            } else {
                                Crossfade(targetState = selectedTab, label = "BubbleTabTransition") { tab ->
                                    when (tab) {
                                        0 -> UnreadMessagesDashboard(isLandscape = isLandscape)
                                        1 -> AppSelectionContent(isLandscape)
                                    }
                                }
                            }
                        }

                        // Bottom Navigation and FAB
                        if (!showAppSelector) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .padding(end = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .width(64.dp)
                                            .clip(CircleShape)
                                            .background(if (selectedTab == 0) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                            .clickable { selectedTab = 0 },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Chat,
                                            contentDescription = stringResource(R.string.title_unread_messages),
                                            tint = if (selectedTab == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .width(64.dp)
                                            .clip(CircleShape)
                                            .background(if (selectedTab == 1) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                            .clickable { selectedTab = 1 },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Apps,
                                            contentDescription = stringResource(R.string.tab_quick_launch),
                                            tint = if (selectedTab == 1) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            FloatingActionButton(
                                onClick = {
                                    if (selectedTab == 0) {
                                        UnreadMessageManager.clearAll()
                                    } else {
                                        showAppSelector = true
                                    }
                                },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Default.ClearAll else Icons.Default.Add,
                                    contentDescription = if (selectedTab == 0) "Clear All" else "Add App"
                                )
                            }
                        }
                        } // end if (!showAppSelector)

                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val pkg = intent.getStringExtra("EXTRA_PACKAGE_NAME")
        val title = intent.getStringExtra("EXTRA_TITLE")
        val text = intent.getStringExtra("EXTRA_TEXT")
        if (pkg != null && title != null && text != null) {
            UnreadMessageManager.addMessage(pkg, title, text, System.currentTimeMillis())
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun UnreadMessagesDashboard(isLandscape: Boolean) {
        val context = LocalContext.current
        val messages by UnreadMessageManager.messagesFlow.collectAsState()

        val grouped = remember(messages) {
            messages.groupBy { it.packageName to it.senderName }
                .map { (key, msgList) ->
                    val sortedMsgs = msgList.sortedByDescending { it.timestamp }
                    val latestTimestamp = sortedMsgs.firstOrNull()?.timestamp ?: 0L
                    SenderGroup(
                        packageName = key.first,
                        senderName = key.second,
                        messages = sortedMsgs,
                        latestTimestamp = latestTimestamp
                    )
                }
                .sortedByDescending { it.latestTimestamp }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.06f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (grouped.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.msg_all_caught_up),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp) // M3e minimal gap
            ) {
                itemsIndexed(grouped, key = { _, group -> group.packageName + group.senderName }) { index, group ->
                    val isFirst = index == 0
                    val isLast = index == grouped.size - 1

                    val shape = RoundedCornerShape(
                        topStart = if (isFirst) 24.dp else 4.dp,
                        topEnd = if (isFirst) 24.dp else 4.dp,
                        bottomStart = if (isLast) 24.dp else 4.dp,
                        bottomEnd = if (isLast) 24.dp else 4.dp
                    )

                    MessageGroupCard(
                        group = group, 
                        shape = shape,
                        modifier = Modifier.animateItem(
                            placementSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    )
                }
            }
            } // end else
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    @Composable
    private fun MessageGroupCard(group: SenderGroup, shape: RoundedCornerShape, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        var expanded by remember { mutableStateOf(false) }
        val visibleMessages = if (expanded) group.messages else group.messages.take(3)
        val coroutineScope = rememberCoroutineScope()

        val density = LocalDensity.current
        val revealOffset = with(density) { -100.dp.toPx() }
        
        val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
        val scope = rememberCoroutineScope()

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
        ) {
            // Background Action (Delete)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable { 
                        coroutineScope.launch {
                            offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                            UnreadMessageManager.clearMessagesForSender(group.packageName, group.senderName)
                        }
                    }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.btn_clear),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Foreground Content
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (offsetX.value < revealOffset / 2) {
                                        offsetX.animateTo(revealOffset, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                    } else {
                                        offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    val newOffset = (offsetX.value + dragAmount).coerceIn(revealOffset, 0f)
                                    offsetX.snapTo(newOffset)
                                }
                            }
                        )
                    }
                    .clickable {
                        val pendingIntent = group.messages.firstOrNull()?.contentIntent
                        if (pendingIntent != null) {
                            AppUtils.sendPendingIntentAllowed(context, pendingIntent)
                        } else {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(group.packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(launchIntent)
                            }
                        }
                        UnreadMessageManager.clearMessagesForSender(group.packageName, group.senderName)
                        (context as? android.app.Activity)?.moveTaskToBack(true)
                    },
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                        .padding(14.dp)
                ) {
                    // Sender details header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val appIcon = remember(group.packageName) {
                            try {
                                context.packageManager.getApplicationIcon(group.packageName).toBitmap(96, 96).asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (appIcon != null) {
                            Image(
                                bitmap = appIcon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = group.senderName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val appName = remember(group.packageName) {
                                AppUtils.getAppName(context, group.packageName)
                            }
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Message Bubbles
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        visibleMessages.forEach { msg ->
                            SingleMessageView(msg)
                        }
                    }

                    // Expand/Collapse Button
                    if (group.messages.size > 3) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (expanded) stringResource(R.string.btn_collapse) else stringResource(R.string.btn_view_more),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SingleMessageView(msg: UnreadMessageManager.Message) {
        var msgExpanded by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 16.dp,
                        bottomEnd = 16.dp,
                        bottomStart = 16.dp
                    )
                )
                .clickable { msgExpanded = !msgExpanded }
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = msg.messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (msgExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = TimeUtils.formatMessageTime(context, msg.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun AppSelectionContent(isLandscape: Boolean) {
        var filteredAppList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
        var pinnedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
        var isLoading by remember { mutableStateOf(true) }
        var showPinTutorial by remember { mutableStateOf(false) }

        val context = LocalContext.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

        var refreshTrigger by remember { mutableStateOf(0) }

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

        LaunchedEffect(refreshTrigger) {
            isLoading = true
            val selectedPackages = AppUtils.getSelectedApps(context)
            filteredAppList = AppUtils.loadSelectedAppsOnly(context, selectedPackages)
            pinnedPackages = AppUtils.getPinnedApps(context)
            
            val hasShown = AppUtils.hasShownPinTutorial(context)
            val unpinnedAppsCount = filteredAppList.count { !pinnedPackages.contains(it.packageName) }
            if (!hasShown && unpinnedAppsCount > 0 && pinnedPackages.isEmpty()) {
                showPinTutorial = true
            }
            
            isLoading = false
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredAppList.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.empty_quick_launch),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.bubble_empty_state),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val (pinned, unpinned) = filteredAppList.partition { pinnedPackages.contains(it.packageName) }
            val pinnedApps = pinned.sortedBy { it.name }
            val unpinnedApps = unpinned.sortedBy { it.name }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 90.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pinnedApps.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.title_pinned_apps),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp, top = 8.dp, start = 8.dp)
                        )
                    }
                    
                    items(pinnedApps, key = { "pinned_${it.packageName}" }) { app ->
                        AppGridItem(
                            app = app,
                            isPinned = true,
                            pinnedPackages = pinnedPackages,
                            context = context,
                            onPinnedChange = { newSelection ->
                                pinnedPackages = newSelection
                                AppUtils.savePinnedApps(context, newSelection)
                            }
                        )
                    }
                    
                    if (unpinnedApps.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                itemsIndexed(unpinnedApps, key = { _, app -> "unpinned_${app.packageName}" }) { index, app ->
                    val isTutorialTarget = index == 0 && showPinTutorial
                    AppGridItem(
                        app = app,
                        isPinned = false,
                        pinnedPackages = pinnedPackages,
                        context = context,
                        isTutorialTarget = isTutorialTarget,
                        onTutorialDismiss = {
                            if (showPinTutorial) {
                                showPinTutorial = false
                                AppUtils.setPinTutorialShown(context)
                            }
                        },
                        onPinnedChange = { newSelection ->
                            pinnedPackages = newSelection
                            AppUtils.savePinnedApps(context, newSelection)
                            if (showPinTutorial) {
                                showPinTutorial = false
                                AppUtils.setPinTutorialShown(context)
                            }
                        }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun AppGridItem(
        app: AppItem,
        isPinned: Boolean,
        pinnedPackages: Set<String>,
        context: Context,
        isTutorialTarget: Boolean = false,
        onTutorialDismiss: () -> Unit = {},
        onPinnedChange: (Set<String>) -> Unit
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse_scale"
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulse_alpha"
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            (context as? android.app.Activity)?.moveTaskToBack(true)
                        }
                    },
                    onLongClick = {
                        val newSelection = pinnedPackages.toMutableSet()
                        if (isPinned) newSelection.remove(app.packageName) else newSelection.add(app.packageName)
                        onPinnedChange(newSelection)
                    }
                )
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 1.dp,
                modifier = Modifier.size(56.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isTutorialTarget) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .scale(pulseScale)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha), RoundedCornerShape(14.dp))
                        )
                        
                        Popup(
                            alignment = Alignment.TopCenter,
                            offset = IntOffset(0, -140),
                            properties = PopupProperties(focusable = false, dismissOnClickOutside = false)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .clickable { onTutorialDismiss() }
                            ) {
                                Text(
                                    text = stringResource(R.string.pin_tutorial_text),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    Image(
                        bitmap = app.icon.toBitmap(120, 120).asImageBitmap(),
                        contentDescription = app.name,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = app.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
