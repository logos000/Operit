package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.components


import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.core.tools.automatic.UIEdgeDefinition
import com.ai.assistance.operit.core.tools.automatic.UIFunction
import com.ai.assistance.operit.core.tools.automatic.UINode
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.ai.assistance.operit.core.tools.automatic.UINodeType
import com.ai.assistance.operit.core.tools.automatic.UIOperation
import com.ai.assistance.operit.core.tools.automatic.UISelector
import androidx.compose.ui.text.input.KeyboardType
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.getSelectorTypeName
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.getSelectorValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleSelectorEditor(
    selector: UISelector,
    onSelectorChange: (UISelector) -> Unit,
    onDelete: () -> Unit,
    showDeleteButton: Boolean
) {
    var selectorType by remember(selector) { mutableStateOf(getSelectorTypeName(selector)) }
    var selectorValue by remember(selector) { mutableStateOf(getSelectorValue(selector)) }
    var showSelectorTypeDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(selectorType, selectorValue) {
        val newSelector = when (selectorType) {
            "ByResourceId" -> UISelector.ByResourceId(selectorValue)
            "ByText" -> UISelector.ByText(selectorValue)
            "ByContentDesc" -> UISelector.ByContentDesc(selectorValue)
            "ByClassName" -> UISelector.ByClassName(selectorValue)
            else -> UISelector.ByText(selectorValue)
        }
        if (newSelector != selector) {
            onSelectorChange(newSelector)
        }
    }

    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("选择器", modifier = Modifier.weight(1f))
            if (showDeleteButton) {
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Remove, "删除选择器", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = showSelectorTypeDropdown,
            onExpandedChange = { showSelectorTypeDropdown = !showSelectorTypeDropdown }
        ) {
            OutlinedTextField(
                value = selectorType,
                onValueChange = {},
                label = { Text("选择器类型") },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSelectorTypeDropdown) }
            )
            ExposedDropdownMenu(
                expanded = showSelectorTypeDropdown,
                onDismissRequest = { showSelectorTypeDropdown = false }
            ) {
                listOf("ByText", "ByResourceId", "ByContentDesc", "ByClassName").forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type) },
                        onClick = {
                            selectorType = type
                            showSelectorTypeDropdown = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = selectorValue,
            onValueChange = { selectorValue = it },
            label = { Text("选择器值") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

