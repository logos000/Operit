package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.automatic.UINode
import com.ai.assistance.operit.core.tools.automatic.UINodeType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNodeDialog(
    node: UINode?,
    isEditing: Boolean,
    onSave: (String, String?, UINodeType) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(node) { mutableStateOf(node?.name ?: "") }
    var activityName by remember(node) { mutableStateOf(node?.activityName ?: "") }
    var nodeType by remember(node) { mutableStateOf(node?.nodeType ?: UINodeType.APP_HOME) }
    var showNodeTypeDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "编辑节点" else "添加节点")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = activityName,
                    onValueChange = { activityName = it },
                    label = { Text("Activity名称（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = showNodeTypeDropdown,
                    onExpandedChange = {
                        showNodeTypeDropdown = !showNodeTypeDropdown
                    }
                ) {
                    OutlinedTextField(
                        value = nodeType.name,
                        onValueChange = { },
                        label = { Text("节点类型") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showNodeTypeDropdown)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = showNodeTypeDropdown,
                        onDismissRequest = { showNodeTypeDropdown = false }
                    ) {
                        UINodeType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    nodeType = type
                                    showNodeTypeDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name, activityName.takeIf { it.isNotBlank() }, nodeType)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}