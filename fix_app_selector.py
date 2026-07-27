import re

with open('app/src/main/java/io/github/gracethings/bubblenotice/ui/screen/AppSelectorScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Add OptIn to AppSelectorScreen
content = content.replace('@OptIn(ExperimentalMaterial3Api::class)', '@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)')

# 2. Replace PrimaryTabRow with ButtonGroup
tab_row_pattern = r'PrimaryTabRow\(selectedTabIndex = selectedTab\) \{\s*Tab\(\s*selected = selectedTab == 0,\s*onClick = \{ selectedTab = 0 \},\s*text = \{ Text\(\"Personal\"\) \}\s*\)\s*Tab\(\s*selected = selectedTab == 1,\s*onClick = \{ selectedTab = 1 \},\s*text = \{ Text\(\"Work\"\) \}\s*\)\s*\}'

button_group = '''Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                ButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    overflowIndicator = { m -> ButtonGroupDefaults.OverflowIndicator(m) }
                ) {
                    toggleableItem(
                        checked = selectedTab == 0,
                        onCheckedChange = { selectedTab = 0 },
                        label = "Personal"
                    )
                    toggleableItem(
                        checked = selectedTab == 1,
                        onCheckedChange = { selectedTab = 1 },
                        label = "Work"
                    )
                }
            }'''

content = re.sub(tab_row_pattern, button_group, content, flags=re.MULTILINE)

# 3. Replace CircularProgressIndicator with LoadingIndicator
content = content.replace('CircularProgressIndicator(', 'LoadingIndicator(')
content = content.replace('androidx.compose.material3.CircularProgressIndicator', 'androidx.compose.material3.LoadingIndicator')
content = re.sub(r'LoadingIndicator\(\s*modifier = Modifier\.size\(48\.dp\),\s*trackColor = MaterialTheme\.colorScheme\.surfaceVariant\s*\)', 'LoadingIndicator(modifier = Modifier.size(48.dp))', content)

# 4. Fix ListItem deprecation
list_item_pattern = r'ListItem\(\s*colors = ListItemDefaults\.colors\(containerColor = Color\.Transparent\),\s*leadingContent = \{\s*Image\(\s*bitmap = app\.icon\.toBitmap\(100, 100\)\.asImageBitmap\(\),\s*contentDescription = app\.name,\s*modifier = Modifier\.size\(44\.dp\)\s*\)\s*\},\s*headlineContent = \{ Text\(app\.name, fontWeight = FontWeight\.Bold\) \},\s*supportingContent = \{ Text\(app\.packageName, style = MaterialTheme\.typography\.labelMedium\) \},\s*trailingContent = \{ Switch\(checked = isSelected, onCheckedChange = null\) \}\s*\)'

new_list_item = '''ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = {
                                    Image(
                                        bitmap = app.icon.toBitmap(100, 100).asImageBitmap(),
                                        contentDescription = app.name,
                                        modifier = Modifier.size(44.dp)
                                    )
                                },
                                supportingContent = { Text(app.packageName, style = MaterialTheme.typography.labelMedium) },
                                trailingContent = { Switch(checked = isSelected, onCheckedChange = null) }
                            ) { Text(app.name, fontWeight = FontWeight.Bold) }'''

content = re.sub(list_item_pattern, new_list_item, content)

with open('app/src/main/java/io/github/gracethings/bubblenotice/ui/screen/AppSelectorScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")