import re

with open('app/src/main/java/io/github/gracethings/bubblenotice/ui/screen/AppSelectorScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# We need to replace SingleChoiceSegmentedButtonRow with ButtonGroup with weight=1f
pattern = r'SingleChoiceSegmentedButtonRow\(.*?\)\s*\{.*?\n\s*\}'

button_group = '''Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
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
            }'''

content = re.sub(pattern, button_group, content, flags=re.MULTILINE|re.DOTALL)

with open('app/src/main/java/io/github/gracethings/bubblenotice/ui/screen/AppSelectorScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
print("Done")