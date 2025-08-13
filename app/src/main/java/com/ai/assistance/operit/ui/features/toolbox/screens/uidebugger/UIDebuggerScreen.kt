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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
                // Edit Mode Toggle
                FloatingActionButton(
                    onClick = { viewModel.toggleEditMode() },
                    containerColor = if (uiState.isEditMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑模式")
                }

                // Add Node
                if (uiState.isEditMode) {
                    FloatingActionButton(
                        onClick = { showAddNodeDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加节点")
                    }
                }

                // Add Connection
                if (uiState.isEditMode) {
                    FloatingActionButton(
                        onClick = { showAddConnectionDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "添加连接")
                    }
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
                isEditMode = uiState.isEditMode,
                onNodeClick = { node -> viewModel.selectNode(node.id) },
                onConnectionClick = { connection -> viewModel.selectConnection(connection.id) },
                onNodeDrag = { nodeId, newPosition -> viewModel.updateNodePosition(nodeId, newPosition) },
                onNodeDelete = { nodeId -> viewModel.deleteNode(nodeId) },
                onConnectionDelete = { connectionId -> viewModel.deleteConnection(connectionId) },
                onCanvasClick = { position -> 
                    if (uiState.isEditMode) {
                        // 在空白处点击时可以添加新节点
                        viewModel.addNode("新节点", "点击编辑内容", position)
                    }
                }
            )

            // Info Panel
            val selectedNode = uiState.selectedNodeId?.let { id -> uiState.nodes.find { it.id == id } }
            val selectedConnection = uiState.selectedConnectionId?.let { id -> uiState.connections.find { it.id == id } }

            if (selectedNode != null || selectedConnection != null) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    InfoPanel(
                        node = selectedNode,
                        connection = selectedConnection,
                        onDismiss = { viewModel.clearSelection() }
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
    }
}

@Composable
fun MindMapCanvas(
    nodes: List<MindMapNode>,
    connections: List<MindMapConnection>,
    selectedNodeId: String?,
    selectedConnectionId: String?,
    isEditMode: Boolean,
    onNodeClick: (MindMapNode) -> Unit,
    onConnectionClick: (MindMapConnection) -> Unit,
    onNodeDrag: (String, Offset) -> Unit,
    onNodeDelete: (String) -> Unit,
    onConnectionDelete: (String) -> Unit,
    onCanvasClick: (Offset) -> Unit
) {
    // 缩放和平移状态
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var draggedNodeId by remember { mutableStateOf<String?>(null) }
    var dragStartNodePosition by remember { mutableStateOf(Offset.Zero) }
    
    val nodeSize = Size(120f, 80f)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isEditMode) {
                if (!isEditMode) {
                    // 非编辑模式：缩放和平移
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (!isDragging) {
                            val oldScale = scale
                            val newScale = (scale * zoom).coerceIn(0.2f, 3f)
                            // 使用与 GraphVisualizer 相同的正确公式
                            offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                            scale = newScale
                        }
                }
        } else {
                    // 编辑模式：节点拖拽
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            // 转换到画布坐标系
                            val canvasOffset = (startOffset - offset) / scale
                            draggedNodeId = nodes.find { node ->
                                val nodeRect = Rect(
                                    node.position - Offset(nodeSize.width / 2, nodeSize.height / 2),
                                    nodeSize
                                )
                                nodeRect.contains(canvasOffset)
                            }?.id
                            
                            if (draggedNodeId != null) {
                                isDragging = true
                                val node = nodes.find { it.id == draggedNodeId }
                                dragStartNodePosition = node?.position ?: Offset.Zero
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            draggedNodeId = null
                        }
                    ) { _, dragAmount ->
                        draggedNodeId?.let { nodeId ->
                            val node = nodes.find { it.id == nodeId }
                            if (node != null) {
                                // 将拖拽量转换到画布坐标系
                                val scaledDragAmount = dragAmount / scale
                                onNodeDrag(nodeId, node.position + scaledDragAmount)
                            }
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    // 转换到画布坐标系
                    val canvasOffset = (tapOffset - offset) / scale
                    
                    // 检查是否点击了节点
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
                        // 检查是否点击了连接线
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
            val borderWidth = if (isSelected) 3f else 1f
            
            // 计算屏幕坐标和缩放尺寸
            val screenPosition = node.position * scale + offset
            val scaledNodeSize = nodeSize * scale
            
            // 绘制节点背景
            drawRoundRect(
                color = node.color,
                topLeft = screenPosition - Offset(scaledNodeSize.width / 2, scaledNodeSize.height / 2),
                size = scaledNodeSize,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * scale, 8f * scale)
            )
            
            // 绘制节点边框
            drawRoundRect(
                color = borderColor,
                topLeft = screenPosition - Offset(scaledNodeSize.width / 2, scaledNodeSize.height / 2),
                size = scaledNodeSize,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * scale, 8f * scale),
                style = Stroke(width = borderWidth * scale)
            )
            
            // 绘制节点文本
            drawText(
                text = node.title,
                position = screenPosition,
                color = Color.White
            )
            
            // 在编辑模式下绘制删除按钮
            if (isEditMode) {
                val deleteButtonPos = screenPosition + Offset(scaledNodeSize.width / 2 - 15 * scale, -scaledNodeSize.height / 2 + 15 * scale)
                drawCircle(
                    color = Color.Red,
                    radius = 12f * scale,
                    center = deleteButtonPos
                )
                drawText(
                    text = "×",
                    position = deleteButtonPos,
                    color = Color.White
                )
            }
        }
    }
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
    color: Color
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            this.color = color.value.toInt()
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 32f
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
    connection: MindMapConnection?,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 250.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (node != null) "节点信息" else "连接信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (node != null) {
                    Text("标题: ${node.title}", style = MaterialTheme.typography.bodySmall)
                    Text("内容: ${node.content}", style = MaterialTheme.typography.bodySmall)
                    Text("类型: ${node.nodeType}", style = MaterialTheme.typography.bodySmall)
                    Text("位置: (${node.position.x.toInt()}, ${node.position.y.toInt()})", style = MaterialTheme.typography.bodySmall)
                } else if (connection != null) {
                    Text("从: ${connection.fromNodeId}", style = MaterialTheme.typography.bodySmall)
                    Text("到: ${connection.toNodeId}", style = MaterialTheme.typography.bodySmall)
                    Text("标签: ${connection.label.ifEmpty { "无" }}", style = MaterialTheme.typography.bodySmall)
                }
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