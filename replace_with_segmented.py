import re

with open('app/src/main/java/io/github/gracethings/bubblenotice/ui/screen/AppSelectorScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

start_idx = content.find('ButtonGroup(')
if start_idx != -1:
    end_idx = content.find('            }', start_idx)
    
    if end_idx != -1:
        end_idx += len('            }')
        
        segmented_button = '''SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Personal")
                    }
                    SegmentedButton(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Work")
                    }
                }'''
        
        content = content[:start_idx] + segmented_button + content[end_idx:]
        
        with open('app/src/main/java/io/github/gracethings/bubblenotice/ui/screen/AppSelectorScreen.kt', 'w', encoding='utf-8') as f:
            f.write(content)
        print("Replaced successfully")
    else:
        print("End index not found")
else:
    print("Start index not found")