package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.viewmodel.UIRouteViewModel
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.viewmodel.UIRouteViewModelFactory
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.viewmodel.MindMapNode
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.viewmodel.MindMapConnection
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageInfo
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIDebuggerScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: UIRouteViewModel = viewModel(
        factory = UIRouteViewModelFactory(context)
    )
    val uiState by viewModel.uiState.collectAsState()
    
    var showAddNodeDialog by remember { mutableStateOf(false) }
    var showAddConnectionDialog by remember { mutableStateOf(false) }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    // 使用新的URI导入方法
                    viewModel.importMindMapFromUri(it.toString())
                } catch (e: Exception) {
                    // 处理错误 - 这里可以通过viewModel设置错误状态
                    android.util.Log.e("UIDebuggerScreen", "Failed to import file", e)
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("思维导图编辑器") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Add Root Node Button
                FloatingActionButton(
                    onClick = { viewModel.addNode("根节点", "这是新的根节点", Offset(400f, 400f)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加根节点")
                }

                // Import Config
                FloatingActionButton(
                    onClick = { 
                        // 直接打开文件选择器，支持JSON文件
                        filePickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = "导入配置")
                }

                // Import from Built-in Configs
                FloatingActionButton(
                    onClick = { viewModel.showBuiltInConfigDialog() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Apps, contentDescription = "内置配置")
                }

                // Export Config  
                FloatingActionButton(
                    onClick = { viewModel.showExportDialog() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "导出配置")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Mind Map Canvas
            MindMapCanvas(
                nodes = uiState.nodes,
                connections = uiState.connections,
                selectedNodeId = uiState.selectedNodeId,
                selectedConnectionId = uiState.selectedConnectionId,
                onNodeClick = { node -> viewModel.selectNode(node.id) },
                onNodeLongPress = { nodeId ->
                    // Long press to start dragging
                },
                onConnectionClick = { connection -> viewModel.selectConnection(connection.id) },
                onCanvasClick = { position ->
                    viewModel.clearSelection()
                },
                onAddChildNode = { parentNodeId -> viewModel.addChildNode(parentNodeId) },
                onNodeDelete = { nodeId -> viewModel.deleteNode(nodeId) }
            )

            // Info Panel for Editing
            val selectedNode = uiState.selectedNodeId?.let { id -> uiState.nodes.find { it.id == id } }

            if (selectedNode != null) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    InfoPanel(
                        node = selectedNode,
                        onDismiss = { viewModel.clearSelection() },
                        onNodeUpdate = { nodeId, title, content ->
                            viewModel.updateNode(nodeId, title, content)
                        }
                    )
                }
            }

            // Error display
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Import Export Message
            uiState.importExportMessage?.let { message ->
                if (!uiState.showImportDialog && !uiState.showExportDialog) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                            .zIndex(10f),
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.contains("成功")) 
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
                                color = if (message.contains("成功")) 
                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                else MaterialTheme.colorScheme.onErrorContainer
                            )
                            IconButton(onClick = { viewModel.clearImportExportMessage() }) {
                        Icon(
                                    Icons.Default.Close,
                                    contentDescription = "关闭",
                                    tint = if (message.contains("成功")) 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Node Dialog
        if (showAddNodeDialog) {
            AddNodeDialog(
                onDismiss = { showAddNodeDialog = false },
                onConfirm = { title, content ->
                    viewModel.addNode(title, content, Offset(400f, 300f))
                    showAddNodeDialog = false
                }
            )
        }

        // Add Connection Dialog
        if (showAddConnectionDialog) {
            AddConnectionDialog(
                nodes = uiState.nodes,
                onDismiss = { showAddConnectionDialog = false },
                onConfirm = { fromNodeId, toNodeId, label ->
                    viewModel.addConnection(fromNodeId, toNodeId, label)
                    showAddConnectionDialog = false
                }
            )
        }

        // Import Dialog
        if (uiState.showImportDialog) {
            MindMapImportDialog(
                isLoading = uiState.isImporting,
                message = uiState.importExportMessage,
                onDismiss = { viewModel.hideImportDialog() },
                onImport = { uriString -> viewModel.importMindMapFromUri(uriString) },
                onClearMessage = { viewModel.clearImportExportMessage() }
            )
        }

        // Export Dialog
        if (uiState.showExportDialog) {
            MindMapExportDialog(
                isLoading = uiState.isExporting,
                message = uiState.importExportMessage,
                onDismiss = { viewModel.hideExportDialog() },
                onExportMindMap = { appName, packageName, description ->
                    viewModel.exportMindMapToFile(appName, packageName, description)
                },
                onExportUIConfig = { appName, packageName, description ->
                    viewModel.exportAsUIRouteConfig(appName, packageName, description)
                },
                onClearMessage = { viewModel.clearImportExportMessage() }
            )
        }

        // Built-in Config Selection Dialog
        if (uiState.showBuiltInConfigDialog) {
            BuiltInConfigSelectionDialog(
                packages = uiState.availablePackages,
                isLoading = uiState.isImporting,
                message = uiState.importExportMessage,
                onDismiss = { viewModel.hideBuiltInConfigDialog() },
                onSelectPackage = { packageInfo -> 
                    viewModel.importFromAutomationPackage(packageInfo)
                },
                onClearMessage = { viewModel.clearImportExportMessage() }
            )
        }
    }
}

@Composable
fun MindMapCanvas(
    nodes: List<MindMapNode>,
    connections: List<MindMapConnection>,
    selectedNodeId: String?,
    selectedConnectionId: String?,
    onNodeClick: (MindMapNode) -> Unit,
    onNodeLongPress: (String) -> Unit,
    onConnectionClick: (MindMapConnection) -> Unit,
    onCanvasClick: (Offset) -> Unit,
    onAddChildNode: (String) -> Unit,
    onNodeDelete: (String) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    
    val nodeSize = Size(140f, 90f) // Increased node size for better readability

    // Resolve colors from the theme before entering the DrawScope
    val contextMenuButtonBackgroundColor = MaterialTheme.colorScheme.secondaryContainer
    val contextMenuButtonBorderColor = MaterialTheme.colorScheme.onSecondaryContainer
    val contextMenuButtonIconColor = MaterialTheme.colorScheme.onSecondaryContainer

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (scale * zoom).coerceIn(0.2f, 3f)
                    offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    // 只处理画布拖拽
                    offset += dragAmount
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        val canvasOffset = (tapOffset - offset) / scale

                        // Check for context menu clicks first
                        val selectedNode = selectedNodeId?.let { id -> nodes.find { it.id == id } }
                        if (selectedNode != null) {
                            val contextMenuHandled = handleContextMenuClick(
                                tapOffset = tapOffset, // Use screen coordinates
                                node = selectedNode,
                                scale = scale,
                                offset = offset,
                                onAddChild = { onAddChildNode(selectedNode.id) },
                                onDelete = { onNodeDelete(selectedNode.id) }
                            )
                            if (contextMenuHandled) return@detectTapGestures
                        }

                        val clickedNode = nodes.find { node ->
                            val nodeRect = Rect(
                                node.position - Offset(nodeSize.width / 2, nodeSize.height / 2),
                                nodeSize
                            )
                            nodeRect.contains(canvasOffset)
                        }

                        if (clickedNode != null) {
                            onNodeClick(clickedNode)
                        } else {
                            val clickedConnection = connections.find { connection ->
                                val fromNode = nodes.find { it.id == connection.fromNodeId }
                                val toNode = nodes.find { it.id == connection.toNodeId }
                                if (fromNode != null && toNode != null) {
                                    isPointNearLine(canvasOffset, fromNode.position, toNode.position, 10f)
                                } else false
                            }

                            if (clickedConnection != null) {
                                onConnectionClick(clickedConnection)
                            } else {
                                onCanvasClick(canvasOffset)
                            }
                        }
                    }
                )
            }
    ) {
        // 绘制连接线 - 使用与 GraphVisualizer 相同的坐标计算方式
        connections.forEach { connection ->
            val fromNode = nodes.find { it.id == connection.fromNodeId }
            val toNode = nodes.find { it.id == connection.toNodeId }
            
            if (fromNode != null && toNode != null) {
                val isSelected = connection.id == selectedConnectionId
                val color = if (isSelected) Color.Red else connection.color
                val strokeWidth = if (isSelected) 4f else 2f
                
                // 计算屏幕坐标
                val start = fromNode.position * scale + offset
                val end = toNode.position * scale + offset
                
                drawConnection(
                    from = start,
                    to = end,
                    color = color,
                    strokeWidth = strokeWidth
                )
                
                // 绘制连接标签
                if (connection.label.isNotEmpty()) {
                    val center = (start + end) / 2f
                    drawText(
                        text = connection.label,
                        position = center,
                        color = color
                    )
                }
            }
        }

        // 绘制节点 - 使用与 GraphVisualizer 相同的坐标计算方式
        nodes.forEach { node ->
            val isSelected = node.id == selectedNodeId
            val borderColor = if (isSelected) Color.Red else Color.Black
            val borderWidth = if (isSelected) 4f else 1.5f // Thicker border for selected
            
            // 计算屏幕坐标和缩放尺寸
            val screenPosition = node.position * scale + offset
            val scaledNodeSize = nodeSize * scale
            
            // Draw Node Shadow
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.2f),
                topLeft = (screenPosition - Offset(scaledNodeSize.width / 2, scaledNodeSize.height / 2)) + Offset(4f, 4f),
                size = scaledNodeSize,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f * scale, 12f * scale)
            )
            
            // 绘制节点背景
            drawRoundRect(
                color = node.color,
                topLeft = screenPosition - Offset(scaledNodeSize.width / 2, scaledNodeSize.height / 2),
                size = scaledNodeSize,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f * scale, 12f * scale)
            )
            
            // 绘制节点边框
            drawRoundRect(
                color = borderColor,
                topLeft = screenPosition - Offset(scaledNodeSize.width / 2, scaledNodeSize.height / 2),
                size = scaledNodeSize,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f * scale, 12f * scale),
                style = Stroke(width = borderWidth * scale)
            )
            
            // 绘制节点文本
            drawText(
                text = node.title,
                position = screenPosition,
                color = Color.Black,
                fontSize = 16.sp.toPx() * scale // Scale font size
            )
        }

        // Draw context menu for selected node
        selectedNodeId?.let { nodeId ->
            val node = nodes.find { it.id == nodeId }
            if (node != null) {
                drawContextMenu(
                    node = node,
                    scale = scale,
                    offset = offset,
                    onAddChild = { onAddChildNode(node.id) },
                    onDelete = { onNodeDelete(node.id) },
                    buttonBackgroundColor = contextMenuButtonBackgroundColor,
                    buttonBorderColor = contextMenuButtonBorderColor,
                    buttonIconColor = contextMenuButtonIconColor
                )
            }
        }
    }
}

private fun handleContextMenuClick(
    tapOffset: Offset,
    node: MindMapNode,
    scale: Float,
    offset: Offset,
    onAddChild: () -> Unit,
    onDelete: () -> Unit
): Boolean {
    val scaledNodeSize = Size(140f, 90f) * scale
    val radius = 24f * scale
    val distance = 40f * scale
    
    val screenPosition = node.position * scale + offset
    
    val buttonPositions = listOf(
        screenPosition + Offset(0f, -scaledNodeSize.height / 2 - distance), // Top (Add)
        screenPosition + Offset(scaledNodeSize.width / 2 + distance, 0f)    // Right (Delete)
    )

    // Add Child Button
    if ((tapOffset - buttonPositions[0]).getDistance() <= radius) {
        onAddChild()
        return true
    }

    // Delete Button
    if ((tapOffset - buttonPositions[1]).getDistance() <= radius) {
        onDelete()
        return true
    }

    return false
}

private fun DrawScope.drawContextMenu(
    node: MindMapNode,
    scale: Float,
    offset: Offset,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
    buttonBackgroundColor: Color,
    buttonBorderColor: Color,
    buttonIconColor: Color
) {
    val screenPosition = node.position * scale + offset
    val scaledNodeSize = Size(140f, 90f) * scale
    val radius = 24f * scale
    val distance = 40f * scale

    val positions = listOf(
        screenPosition + Offset(0f, -scaledNodeSize.height / 2 - distance), // Top
        screenPosition + Offset(scaledNodeSize.width / 2 + distance, 0f)   // Right
    )

    val icons = listOf(Icons.Default.Add, Icons.Default.Delete)
    
    icons.forEachIndexed { index, icon ->
        val buttonCenter = positions[index]
        drawCircle(
            color = buttonBackgroundColor,
            radius = radius,
            center = buttonCenter
        )
        drawCircle(
            color = buttonBorderColor,
            radius = radius,
            center = buttonCenter,
            style = Stroke(width = 1.5f * scale)
        )
        // This is a simplified way to draw icons. For real apps, consider a better approach.
        // For now, we just draw a placeholder symbol.
        val iconText = when (icon) {
            Icons.Default.Add -> "+"
            Icons.Default.Delete -> "X"
            else -> "?"
        }
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                this.color = buttonIconColor.value.toInt()
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = 24f * scale
                isAntiAlias = true
            }
            drawText(iconText, buttonCenter.x, buttonCenter.y + 8f * scale, paint)
        }
    }

    // Since we can't directly handle clicks here, we rely on the parent composable's
    // tap gesture detection to infer clicks on these buttons based on their positions.
}


private fun DrawScope.drawConnection(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float
) {
    // 绘制直线连接
    drawLine(
        color = color,
        start = from,
        end = to,
        strokeWidth = strokeWidth
    )
    
    // 绘制箭头
    val arrowLength = 20f
    val arrowAngle = 0.5f
    val angle = atan2(to.y - from.y, to.x - from.x)
    
    val arrowEnd1 = Offset(
        to.x - arrowLength * cos(angle - arrowAngle),
        to.y - arrowLength * sin(angle - arrowAngle)
    )
    val arrowEnd2 = Offset(
        to.x - arrowLength * cos(angle + arrowAngle),
        to.y - arrowLength * sin(angle + arrowAngle)
    )
    
    drawLine(color = color, start = to, end = arrowEnd1, strokeWidth = strokeWidth)
    drawLine(color = color, start = to, end = arrowEnd2, strokeWidth = strokeWidth)
}

private fun DrawScope.drawText(
    text: String,
    position: Offset,
    color: Color,
    fontSize: Float = 32f // Default size
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            this.color = color.value.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
            this.textSize = fontSize
            isAntiAlias = true
        }
        
        drawText(
            text,
            position.x,
            position.y + 10f, // 稍微向下偏移
            paint
        )
    }
}

private fun isPointNearLine(point: Offset, lineStart: Offset, lineEnd: Offset, threshold: Float): Boolean {
    val lineLength = (lineEnd - lineStart).getDistance()
    if (lineLength == 0f) return false
    
    val t = ((point - lineStart).dot(lineEnd - lineStart)) / (lineLength * lineLength)
    val projection = lineStart + (lineEnd - lineStart) * t.coerceIn(0f, 1f)
    val distance = (point - projection).getDistance()
    
    return distance <= threshold
}

private fun Offset.dot(other: Offset): Float = x * other.x + y * other.y

@Composable
fun InfoPanel(
    node: MindMapNode?,
    onDismiss: () -> Unit,
    onNodeUpdate: (String, String, String) -> Unit = { _, _, _ -> }
) {
    var editTitle by remember(node) { mutableStateOf(node?.title ?: "") }
    var editContent by remember(node) { mutableStateOf(node?.content ?: "") }

    // Automatically update state when node changes
    LaunchedEffect(node) {
        if (node != null) {
            editTitle = node.title
            editContent = node.content
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .heightIn(min = 200.dp, max = 400.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "编辑节点",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = editTitle,
                onValueChange = { editTitle = it },
                label = { Text("标题") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = editContent,
                onValueChange = { editContent = it },
                label = { Text("内容") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Take available space
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (node != null && editTitle.isNotEmpty()) {
                        onNodeUpdate(node.id, editTitle, editContent)
                        onDismiss() // Close panel on save
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = node != null && editTitle.isNotEmpty()
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
fun AddNodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加新节点") },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, content) },
                enabled = title.isNotEmpty()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun AddConnectionDialog(
    nodes: List<MindMapNode>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var fromNodeId by remember { mutableStateOf("") }
    var toNodeId by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加新连接") },
        text = {
            Column {
                Text("从节点:")
                nodes.forEach { node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { fromNodeId = node.id }
                            .padding(8.dp)
                            .background(
                                if (fromNodeId == node.id) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                    ) {
                        Text(node.title)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("到节点:")
                nodes.forEach { node ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toNodeId = node.id }
                            .padding(8.dp)
                            .background(
                                if (toNodeId == node.id) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                    ) {
                        Text(node.title)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("标签（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(fromNodeId, toNodeId, label) },
                enabled = fromNodeId.isNotEmpty() && toNodeId.isNotEmpty() && fromNodeId != toNodeId
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
} 

@Composable
fun MindMapImportDialog(
    isLoading: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onClearMessage: () -> Unit
) {
    var selectedFilePath by remember { mutableStateOf("") }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                selectedFilePath = it.toString()
                // 直接导入文件
                try {
                    onImport(it.toString())
                } catch (e: Exception) {
                    android.util.Log.e("MindMapImportDialog", "Failed to import file", e)
                }
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入思维导图") },
        text = {
            Column {
                Text("选择思维导图文件进行导入：")
                Spacer(modifier = Modifier.height(16.dp))
                
                // 文件选择按钮
                OutlinedButton(
                    onClick = { 
                        filePickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择文件")
                }
                
                // 显示选择的文件路径
                if (selectedFilePath.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "已选择: ${selectedFilePath.substringAfterLast("/", selectedFilePath)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "支持的文件格式：JSON格式的思维导图数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            // 不需要手动确认按钮，选择文件后自动导入
            if (isLoading) {
                Box(
                    modifier = Modifier.padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text(if (isLoading) "导入中..." else "关闭")
            }
        }
    )
}

@Composable
fun MindMapExportDialog(
    isLoading: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onExportMindMap: (String, String, String) -> Unit,
    onExportUIConfig: (String, String, String) -> Unit,
    onClearMessage: () -> Unit
) {
    var appName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var exportType by remember { mutableStateOf("mindmap") } // "mindmap" or "uiconfig"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出思维导图") },
        text = {
            Column {
                Text("选择导出类型和输入信息：")
                Spacer(modifier = Modifier.height(16.dp))
                
                // Export type selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { exportType = "mindmap" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (exportType == "mindmap") 
                                MaterialTheme.colorScheme.primaryContainer 
                            else Color.Transparent
                        )
                    ) {
                        Text("思维导图", fontSize = 12.sp)
                    }
                    
                    OutlinedButton(
                        onClick = { exportType = "uiconfig" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (exportType == "uiconfig") 
                                MaterialTheme.colorScheme.primaryContainer 
                            else Color.Transparent
                        )
                    ) {
                        Text("UI配置", fontSize = 12.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("应用名称") },
                    placeholder = { Text("我的应用") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("包名") },
                    placeholder = { Text("com.example.app") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    placeholder = { Text("描述这个${if (exportType == "mindmap") "思维导图" else "UI配置"}...") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (exportType == "mindmap") 
                        "将导出为思维导图JSON格式" 
                    else "将导出为UI自动化路由配置JSON格式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        val desc = description.ifEmpty { 
                            "${appName}的${if (exportType == "mindmap") "思维导图" else "UI配置"}"
                        }
                        if (exportType == "mindmap") {
                            onExportMindMap(appName, packageName, desc)
                        } else {
                            onExportUIConfig(appName, packageName, desc)
                        }
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

@Composable
fun BuiltInConfigSelectionDialog(
    packages: List<AutomationPackageInfo>,
    isLoading: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onSelectPackage: (AutomationPackageInfo) -> Unit,
    onClearMessage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择内置配置包") },
        text = {
            Column {
                Text("选择一个内置配置包进行导入：")
                Spacer(modifier = Modifier.height(16.dp))
                
                if (packages.isEmpty()) {
                    Text("没有可用的内置配置包。")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(packages) { packageInfo ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPackage(packageInfo) }
                                    .padding(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (packageInfo.isBuiltIn) 
                                        MaterialTheme.colorScheme.surfaceVariant 
                                    else MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = packageInfo.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = packageInfo.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = packageInfo.description,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = if (packageInfo.isBuiltIn) "内置" else "用户导入",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "选择一个包后，将导入其所有UI自动化路由配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            if (isLoading) {
                Button(
                    onClick = { },
                    enabled = false
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入中...")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("关闭")
            }
        }
    )
} 