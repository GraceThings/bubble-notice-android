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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.gracethings.bubblenotice.R
import io.github.gracethings.bubblenotice.ui.theme.BubbleNoticeTheme
import io.github.gracethings.bubblenotice.ui.theme.ThemeAccent
import io.github.gracethings.bubblenotice.ui.theme.ThemeMode
import io.github.gracethings.bubblenotice.ui.theme.colorPair

@Composable
fun AppearanceScreen(
    appearance: io.github.gracethings.bubblenotice.ui.theme.AppearanceState,
    onAppearanceChanged: (io.github.gracethings.bubblenotice.ui.theme.AppearanceState) -> Unit
) {
    var currentState by remember { mutableStateOf(appearance) }

    val cardShape = RoundedCornerShape(8.dp)
    val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val middleShape = RoundedCornerShape(4.dp)
    val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_appearance_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )

        // 白天/黑夜/跟随系统 / Light, dark or follow the system.
        Text(
            text = stringResource(R.string.appearance_theme_mode_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 8.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AppearanceOptionCard(
                title = stringResource(R.string.appearance_theme_mode_system),
                selected = currentState.themeMode == ThemeMode.SYSTEM,
                shape = topShape,
                onSelected = { currentState = currentState.copy(themeMode = ThemeMode.SYSTEM); onAppearanceChanged(currentState) }
            )
            AppearanceOptionCard(
                title = stringResource(R.string.appearance_theme_mode_light),
                selected = currentState.themeMode == ThemeMode.LIGHT,
                shape = middleShape,
                onSelected = { currentState = currentState.copy(themeMode = ThemeMode.LIGHT); onAppearanceChanged(currentState) }
            )
            AppearanceOptionCard(
                title = stringResource(R.string.appearance_theme_mode_dark),
                selected = currentState.themeMode == ThemeMode.DARK,
                shape = bottomShape,
                onSelected = { currentState = currentState.copy(themeMode = ThemeMode.DARK); onAppearanceChanged(currentState) }
            )
        }

        // 主题色 / Theme color.
        Text(
            text = stringResource(R.string.appearance_accent_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val accentNames = listOf(
                stringResource(R.string.appearance_accent_dynamic),
                stringResource(R.string.appearance_accent_ocean),
                stringResource(R.string.appearance_accent_forest),
                stringResource(R.string.appearance_accent_sunset),
                stringResource(R.string.appearance_accent_plum)
            )
            val accentValues = listOf(
                ThemeAccent.DYNAMIC,
                ThemeAccent.OCEAN,
                ThemeAccent.FOREST,
                ThemeAccent.SUNSET,
                ThemeAccent.PLUM
            )
            accentValues.forEachIndexed { index, accent ->
                val swatch = accentColor(accent, currentState.themeMode)
                AppearanceAccentCard(
                    title = accentNames[index],
                    swatch = swatch,
                    selected = currentState.accent == accent,
                    shape = when (index) {
                        0 -> topShape
                        accentValues.lastIndex -> bottomShape
                        else -> middleShape
                    },
                    onSelected = {
                        currentState = currentState.copy(accent = accent)
                        onAppearanceChanged(currentState)
                    }
                )
            }
        }
    }
}

@Composable
private fun AppearanceOptionCard(
    title: String,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onSelected: () -> Unit
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onSelected() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun AppearanceAccentCard(
    title: String,
    swatch: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onSelected: () -> Unit
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onSelected() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(swatch)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            )
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun accentColor(
    accent: ThemeAccent,
    themeMode: ThemeMode
): androidx.compose.ui.graphics.Color {
    val isDark = themeMode != ThemeMode.LIGHT
    return accent.colorPair()?.let { colors ->
        if (isDark) colors.darkPrimary else colors.lightPrimary
    } ?: MaterialTheme.colorScheme.primary
}

@Preview(showBackground = true, name = "AppearanceScreen Preview")
@Composable
fun PreviewAppearanceScreen() {
    BubbleNoticeTheme {
        Surface {
            AppearanceScreen(
                appearance = io.github.gracethings.bubblenotice.ui.theme.AppearanceState(),
                onAppearanceChanged = {}
            )
        }
    }
}
