package io.github.gracethings.bubblenotice.ui.screen
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable fun TestGroup() { ButtonGroup(modifier = Modifier.fillMaxWidth(), overflowIndicator = { m -> ButtonGroupDefaults.OverflowIndicator(m) }) { toggleableItem(checked=true, onCheckedChange={}, label="Personal", weight=1f) } }