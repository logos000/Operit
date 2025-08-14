package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.UIDebuggerViewModel
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.UIElement

@Composable
fun UIDebuggerOverlay(
    viewModelStoreOwner: ViewModelStoreOwner,
    onClose: () -> Unit,
    onMinimize: (() -> Unit)? = null
) {
    val viewModel: UIDebuggerViewModel = viewModel(viewModelStoreOwner = viewModelStoreOwner)
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var isOverlayVisible by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf<UIElement?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isOverlayVisible) {
            ElementHighlightOverlay(
                elements = uiState.elements,
                onElementClick = { element ->
                    selectedElement = element
                }
            )
        }

        // Element info panel (floating)
        selectedElement?.let { element ->
            ElementInfoPanel(
                element = element,
                onDismiss = { selectedElement = null },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .zIndex(10f)
            )
        }

        // Control buttons - 调整布局让最小化按钮更容易点击
        Box(modifier = Modifier.fillMaxSize()) {
            // Import/Export buttons - 新增在左侧
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(16.dp)
            ) {
                // Import button
                SmallFloatingActionButton(
                    onClick = { viewModel.showImportDialog() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "导入配置",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                // Export button
                SmallFloatingActionButton(
                    onClick = { viewModel.showExportDialog() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "导出配置",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            // Minimize button (if callback provided) - 放在右下角，更大更显眼
            onMinimize?.let { minimizeCallback ->
                FloatingActionButton(
                    onClick = minimizeCallback,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "最小化",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Close button - 放在右上角，更小避免误点
            SmallFloatingActionButton(
                onClick = {
                    if (isOverlayVisible) {
                        onClose()
                    } else {
                        viewModel.refreshUI()
                        isOverlayVisible = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (isOverlayVisible) Icons.Default.Close else Icons.Default.Build,
                    contentDescription = if (isOverlayVisible) "关闭" else "开始分析",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Import Export Dialogs
        if (uiState.showImportDialog) {
            ImportConfigDialog(
                availableConfigs = uiState.availableConfigs,
                isLoading = uiState.isImporting,
                message = uiState.importExportMessage,
                onDismiss = { viewModel.hideImportDialog() },
                onImportFile = { filePath -> viewModel.importConfigFromFile(filePath) },
                onClearMessage = { viewModel.clearImportExportMessage() }
            )
        }

        if (uiState.showExportDialog) {
            ExportConfigDialog(
                isLoading = uiState.isExporting,
                message = uiState.importExportMessage,
                onDismiss = { viewModel.hideExportDialog() },
                onExport = { appName, packageName, description ->
                    viewModel.exportCurrentUIAsConfig(appName, packageName, description)
                },
                onClearMessage = { viewModel.clearImportExportMessage() }
            )
        }

        // Success/Error Message
        uiState.importExportMessage?.let { message ->
            if (!uiState.showImportDialog && !uiState.showExportDialog) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .zIndex(20f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.contains("成功") || message.startsWith("Successfully")) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
            Text(
                            text = message,
                            modifier = Modifier.weight(1f),
                            color = if (message.contains("成功") || message.startsWith("Successfully")) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                        IconButton(onClick = { viewModel.clearImportExportMessage() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = if (message.contains("成功") || message.startsWith("Successfully")) 
                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ElementHighlightOverlay(
        elements: List<UIElement>,
    onElementClick: (UIElement) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(elements) {
                detectTapGestures { offset ->
                    // Find the smallest element that contains the tap point
                    // This ensures we get the most specific/innermost element
                    val tappedElement = elements
                        .filter { element ->
                            element.bounds?.let { bounds ->
                                offset.x >= bounds.left && 
                                offset.x <= bounds.right && 
                                offset.y >= bounds.top && 
                                offset.y <= bounds.bottom
                            } ?: false
                        }
                        .minByOrNull { element ->
                            // Find the element with smallest area
                            element.bounds?.let { bounds ->
                                bounds.width() * bounds.height()
                            } ?: Int.MAX_VALUE
                        }
                    
                    tappedElement?.let(onElementClick)
                }
            }
    ) {
        // Draw elements sorted by area (largest first, smallest last)
        // This ensures smaller elements are drawn on top of larger ones
        elements
            .sortedByDescending { element ->
                element.bounds?.let { bounds ->
                    bounds.width() * bounds.height()
                } ?: 0
            }
            .forEach { element ->
                element.bounds?.let { bounds ->
                    drawElementHighlight(bounds)
                }
            }
    }
}

private fun DrawScope.drawElementHighlight(
    bounds: android.graphics.Rect
) {
    // Always use red color like the original
    val color = Color.Red
    
    // Draw highlight border
    drawRect(
        color = color,
        topLeft = Offset(bounds.left.toFloat(), bounds.top.toFloat()),
        size = Size(bounds.width().toFloat(), bounds.height().toFloat()),
        style = Stroke(width = 2.dp.toPx())
    )
}

@Composable
fun ElementInfoPanel(
        element: UIElement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 300.dp)
            .heightIn(max = 400.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                    Text(
                    text = "控件信息",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Element type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = element.typeDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable content
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false)
            ) {
                // Basic info with smaller text
            Text(
                    text = element.getFullDetails(),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Additional size info if available
                if (element.bounds != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "尺寸: ${element.bounds.width()}×${element.bounds.height()}px",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
} 

@Composable
fun ImportConfigDialog(
    availableConfigs: List<ImportableConfig>,
    isLoading: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onImportFile: (String) -> Unit,
    onClearMessage: () -> Unit
) {
    var selectedFilePath by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入UI路由配置") },
        text = {
            Column {
                Text("选择要导入的配置文件路径：")
                Spacer(modifier = Modifier.height(8.dp))
                
                TextField(
                    value = selectedFilePath,
                    onValueChange = { selectedFilePath = it },
                    label = { Text("文件路径") },
                    placeholder = { Text("/storage/emulated/0/config.json") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("可用配置:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                Column(
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    availableConfigs.forEach { config ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = config.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = config.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = config.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = if (config.isBuiltIn) "内置" else "用户导入",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (config.isBuiltIn) 
                                        MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = if (it.startsWith("Successfully")) 
                            MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (selectedFilePath.isNotEmpty()) {
                        onImportFile(selectedFilePath)
                    }
                },
                enabled = selectedFilePath.isNotEmpty() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.width(16.dp))
                } else {
                    Text("导入")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun ExportConfigDialog(
    isLoading: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onExport: (String, String, String) -> Unit,
    onClearMessage: () -> Unit
) {
    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出UI路由配置") },
        text = {
            Column {
                Text("输入应用信息以生成路由配置：")
                Spacer(modifier = Modifier.height(16.dp))
                
                TextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("应用名称") },
                    placeholder = { Text("微信") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("包名") },
                    placeholder = { Text("com.tencent.mm") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    placeholder = { Text("微信应用的UI自动化配置") },
                    modifier = Modifier.fillMaxWidth()
                )

                message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = if (it.contains("成功")) 
                            MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (appName.isNotEmpty() && packageName.isNotEmpty()) {
                        onExport(appName, packageName, description.ifEmpty { "${appName}的UI自动化配置" })
                    }
                },
                enabled = appName.isNotEmpty() && packageName.isNotEmpty() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.width(16.dp))
                } else {
                    Text("导出")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
} 