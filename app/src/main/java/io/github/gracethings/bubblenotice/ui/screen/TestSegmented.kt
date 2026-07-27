package io.github.gracethings.bubblenotice.ui.screen
import androidx.compose.runtime.Composable
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TestSegmentedGroup() { SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) { SegmentedButton(selected=true, onClick={}, shape=SegmentedButtonDefaults.itemShape(index=0, count=2)) { Text("A") }; SegmentedButton(selected=false, onClick={}, shape=SegmentedButtonDefaults.itemShape(index=1, count=2)) { Text("B") } } }