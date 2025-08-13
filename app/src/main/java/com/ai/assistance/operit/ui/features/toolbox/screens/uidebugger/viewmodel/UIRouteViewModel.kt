package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import com.ai.assistance.operit.core.tools.automatic.*
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// 简化的思维导图数据结构
data class MindMapNode(
    val id: String,
    val title: String,
    val content: String,
    val position: Offset,
    val color: Color,
    val nodeType: String = "NORMAL"
)

data class MindMapConnection(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
    val color: Color = Color.Gray
)

data class MindMapUiState(
    val nodes: List<MindMapNode> = emptyList(),
    val connections: List<MindMapConnection> = emptyList(),
    val selectedNodeId: String? = null,
    val selectedConnectionId: String? = null,
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    // 导入导出状态
    val showImportDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val importExportMessage: String? = null
)

// 用于序列化的数据类
@Serializable
data class SerializableMindMapNode(
    val id: String,
    val title: String,
    val content: String,
    val positionX: Float,
    val positionY: Float,
    val colorValue: Long,
    val nodeType: String = "NORMAL"
)

@Serializable
data class SerializableMindMapConnection(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
    val colorValue: Long = Color.Gray.value.toLong()
)

@Serializable
data class SerializableMindMapData(
    val appName: String,
    val packageName: String,
    val description: String,
    val nodes: List<SerializableMindMapNode>,
    val connections: List<SerializableMindMapConnection>
)

class UIRouteViewModel(private val context: Context) : ViewModel() {

    private val _uiState = MutableStateFlow(MindMapUiState())
    val uiState: StateFlow<MindMapUiState> = _uiState.asStateFlow()

    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val packageManager by lazy { AutomationPackageManager.getInstance(context) }

    init {
        // 初始化一些示例数据
        createSampleMindMap()
    }

    private fun createSampleMindMap() {
        val centerNode = MindMapNode(
            id = "center",
            title = "UI自动化",
            content = "核心概念",
            position = Offset(400f, 300f),
            color = Color(0xFF2196F3),
            nodeType = "CENTER"
        )

        val node1 = MindMapNode(
            id = "node1",
            title = "页面识别",
            content = "识别UI元素和页面结构",
            position = Offset(200f, 200f),
            color = Color(0xFF4CAF50)
        )

        val node2 = MindMapNode(
            id = "node2",
            title = "操作执行",
            content = "点击、输入、滑动等操作",
            position = Offset(600f, 200f),
            color = Color(0xFFFF9800)
        )

        val node3 = MindMapNode(
            id = "node3",
            title = "验证检查",
            content = "验证操作结果和页面状态",
            position = Offset(400f, 500f),
            color = Color(0xFF9C27B0)
        )

        val connections = listOf(
            MindMapConnection("conn1", "center", "node1", "包含"),
            MindMapConnection("conn2", "center", "node2", "包含"),
            MindMapConnection("conn3", "center", "node3", "包含")
        )

        _uiState.update { 
            it.copy(
                nodes = listOf(centerNode, node1, node2, node3),
                connections = connections
            )
        }
    }

    fun selectNode(nodeId: String?) {
        _uiState.update { 
            it.copy(
                selectedNodeId = nodeId,
                selectedConnectionId = null
            ) 
        }
    }

    fun selectConnection(connectionId: String?) {
        _uiState.update { 
            it.copy(
                selectedConnectionId = connectionId,
                selectedNodeId = null
            ) 
        }
    }

    fun toggleEditMode() {
        _uiState.update { it.copy(isEditMode = !it.isEditMode) }
    }

    fun addNode(title: String, content: String, position: Offset) {
        val newNode = MindMapNode(
            id = "node_${System.currentTimeMillis()}",
            title = title,
            content = content,
            position = position,
            color = getRandomColor()
        )

        _uiState.update { 
            it.copy(nodes = it.nodes + newNode)
        }
    }

    fun updateNodePosition(nodeId: String, newPosition: Offset) {
        _uiState.update { 
            it.copy(
                nodes = it.nodes.map { node ->
                    if (node.id == nodeId) node.copy(position = newPosition)
                    else node
                }
            )
        }
    }

    fun addConnection(fromNodeId: String, toNodeId: String, label: String = "") {
        val newConnection = MindMapConnection(
            id = "conn_${System.currentTimeMillis()}",
            fromNodeId = fromNodeId,
            toNodeId = toNodeId,
            label = label
        )

        _uiState.update { 
            it.copy(connections = it.connections + newConnection)
        }
    }

    fun deleteNode(nodeId: String) {
        _uiState.update { 
            it.copy(
                nodes = it.nodes.filter { it.id != nodeId },
                connections = it.connections.filter { 
                    it.fromNodeId != nodeId && it.toNodeId != nodeId 
                }
            )
        }
    }

    fun deleteConnection(connectionId: String) {
        _uiState.update { 
            it.copy(
                connections = it.connections.filter { it.id != connectionId }
            )
        }
    }

    fun clearSelection() {
        _uiState.update { 
            it.copy(
                selectedNodeId = null,
                selectedConnectionId = null
            )
        }
    }

    private fun getRandomColor(): Color {
        val colors = listOf(
            Color(0xFF4CAF50), // Green
            Color(0xFF2196F3), // Blue
            Color(0xFFFF9800), // Orange
            Color(0xFF9C27B0), // Purple
            Color(0xFF607D8B), // Blue Grey
            Color(0xFF795548)  // Brown
        )
        return colors.random()
    }

    fun showError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 显示导入对话框 */
    fun showImportDialog() {
        _uiState.update { it.copy(showImportDialog = true, importExportMessage = null) }
    }

    /** 隐藏导入对话框 */
    fun hideImportDialog() {
        _uiState.update { it.copy(showImportDialog = false) }
    }

    /** 显示导出对话框 */
    fun showExportDialog() {
        _uiState.update { it.copy(showExportDialog = true, importExportMessage = null) }
    }

    /** 隐藏导出对话框 */
    fun hideExportDialog() {
        _uiState.update { it.copy(showExportDialog = false) }
    }

    /** 导入思维导图数据 */
    fun importMindMapFromFile(filePath: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isImporting = true, importExportMessage = null) }
                
                val result = withContext(Dispatchers.IO) {
                    val file = File(filePath)
                    if (!file.exists()) {
                        throw Exception("文件不存在: $filePath")
                    }
                    
                    val jsonContent = file.readText()
                    val data = json.decodeFromString<SerializableMindMapData>(jsonContent)
                    
                    // 转换为内部数据结构
                    val nodes = data.nodes.map { serializable ->
                        MindMapNode(
                            id = serializable.id,
                            title = serializable.title,
                            content = serializable.content,
                            position = Offset(serializable.positionX, serializable.positionY),
                            color = Color(serializable.colorValue.toULong()),
                            nodeType = serializable.nodeType
                        )
                    }
                    
                    val connections = data.connections.map { serializable ->
                        MindMapConnection(
                            id = serializable.id,
                            fromNodeId = serializable.fromNodeId,
                            toNodeId = serializable.toNodeId,
                            label = serializable.label,
                            color = Color(serializable.colorValue.toULong())
                        )
                    }
                    
                    Pair(nodes, connections)
                }
                
                _uiState.update { 
                    it.copy(
                        nodes = result.first,
                        connections = result.second,
                        isImporting = false,
                        importExportMessage = "成功导入思维导图",
                        showImportDialog = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isImporting = false,
                        importExportMessage = "导入失败: ${e.message}"
                    )
                }
            }
        }
    }

    /** 从URI导入思维导图数据 */
    fun importMindMapFromUri(uriString: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isImporting = true, importExportMessage = null) }
                
                val result = withContext(Dispatchers.IO) {
                    val uri = android.net.Uri.parse(uriString)
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("无法打开文件")
                    
                    val jsonContent = inputStream.bufferedReader().use { it.readText() }
                    val data = json.decodeFromString<SerializableMindMapData>(jsonContent)
                    
                    // 转换为内部数据结构
                    val nodes = data.nodes.map { serializable ->
                        MindMapNode(
                            id = serializable.id,
                            title = serializable.title,
                            content = serializable.content,
                            position = Offset(serializable.positionX, serializable.positionY),
                            color = Color(serializable.colorValue.toULong()),
                            nodeType = serializable.nodeType
                        )
                    }
                    
                    val connections = data.connections.map { serializable ->
                        MindMapConnection(
                            id = serializable.id,
                            fromNodeId = serializable.fromNodeId,
                            toNodeId = serializable.toNodeId,
                            label = serializable.label,
                            color = Color(serializable.colorValue.toULong())
                        )
                    }
                    
                    Pair(nodes, connections)
                }
                
                _uiState.update { 
                    it.copy(
                        nodes = result.first,
                        connections = result.second,
                        isImporting = false,
                        importExportMessage = "成功导入思维导图",
                        showImportDialog = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isImporting = false,
                        importExportMessage = "导入失败: ${e.message}"
                    )
                }
            }
        }
    }

    /** 导出思维导图数据 */
    fun exportMindMapToFile(appName: String, packageName: String, description: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isExporting = true, importExportMessage = null) }
                
                val currentState = _uiState.value
                if (currentState.nodes.isEmpty()) {
                    _uiState.update { 
                        it.copy(
                            isExporting = false,
                            importExportMessage = "没有节点可导出"
                        )
                    }
                    return@launch
                }
                
                val filePath = withContext(Dispatchers.IO) {
                    // 转换为可序列化的数据结构
                    val serializableNodes = currentState.nodes.map { node ->
                        SerializableMindMapNode(
                            id = node.id,
                            title = node.title,
                            content = node.content,
                            positionX = node.position.x,
                            positionY = node.position.y,
                            colorValue = node.color.value.toLong(),
                            nodeType = node.nodeType
                        )
                    }
                    
                    val serializableConnections = currentState.connections.map { connection ->
                        SerializableMindMapConnection(
                            id = connection.id,
                            fromNodeId = connection.fromNodeId,
                            toNodeId = connection.toNodeId,
                            label = connection.label,
                            colorValue = connection.color.value.toLong()
                        )
                    }
                    
                    val data = SerializableMindMapData(
                        appName = appName,
                        packageName = packageName,
                        description = description,
                        nodes = serializableNodes,
                        connections = serializableConnections
                    )
                    
                    // 保存到文件
                    val fileName = "${packageName.replace(".", "_")}_mindmap.json"
                    val configsDir = File(context.getExternalFilesDir(null), "mindmaps")
                    if (!configsDir.exists()) {
                        configsDir.mkdirs()
                    }
                    
                    val file = File(configsDir, fileName)
                    file.writeText(json.encodeToString(data))
                    file.absolutePath
                }
                
                _uiState.update { 
                    it.copy(
                        isExporting = false,
                        importExportMessage = "思维导图已导出到: $filePath",
                        showExportDialog = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isExporting = false,
                        importExportMessage = "导出失败: ${e.message}"
                    )
                }
            }
        }
    }

    /** 导出为UI路由配置 */
    fun exportAsUIRouteConfig(appName: String, packageName: String, description: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isExporting = true, importExportMessage = null) }
                
                val currentState = _uiState.value
                if (currentState.nodes.isEmpty()) {
                    _uiState.update { 
                        it.copy(
                            isExporting = false,
                            importExportMessage = "没有节点可导出"
                        )
                    }
                    return@launch
                }
                
                val filePath = withContext(Dispatchers.IO) {
                    val config = generateUIRouteConfigFromMindMap(appName, packageName, description, currentState)
                    
                    // 保存到自动化配置目录
                    val fileName = "${packageName.replace(".", "_")}_config.json"
                    val configsDir = File(context.getExternalFilesDir(null), "automation_configs")
                    if (!configsDir.exists()) {
                        configsDir.mkdirs()
                    }
                    
                    val file = File(configsDir, fileName)
                    file.writeText(json.encodeToString(config))
                    file.absolutePath
                }
                
                _uiState.update { 
                    it.copy(
                        isExporting = false,
                        importExportMessage = "UI路由配置已导出到: $filePath",
                        showExportDialog = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isExporting = false,
                        importExportMessage = "导出失败: ${e.message}"
                    )
                }
            }
        }
    }

    /** 将思维导图转换为UI路由配置 */
    private fun generateUIRouteConfigFromMindMap(
        appName: String,
        packageName: String,
        description: String,
        state: MindMapUiState
    ): JsonUIRouteConfig {
        // 将节点转换为UI节点
        val nodes = state.nodes.map { node ->
            JsonUINode(
                name = node.title,
                description = node.content,
                activityName = null,
                nodeType = when (node.nodeType) {
                    "CENTER" -> "APP_HOME"
                    "NORMAL" -> "DETAIL_PAGE"
                    else -> "DETAIL_PAGE"
                }
            )
        }
        
        // 将连接转换为边
        val edges = state.connections.map { connection ->
            val fromNode = state.nodes.find { it.id == connection.fromNodeId }
            val toNode = state.nodes.find { it.id == connection.toNodeId }
            
            JsonUIEdge(
                from = fromNode?.title ?: "未知节点",
                to = toNode?.title ?: "未知节点",
                operations = listOf(
                    JsonUIOperation.Click(
                        selector = JsonUISelector(type = "ByText", value = "{{${connection.label.ifEmpty { "target_text" }}}}"),
                        description = connection.label.ifEmpty { "点击导航" }
                    )
                )
            )
        }
        
        // 创建示例功能
        val functions = listOf(
            JsonUIFunction(
                name = "导航功能",
                description = "基于思维导图的导航功能",
                targetNodeName = state.nodes.firstOrNull()?.title ?: "主页",
                operation = JsonUIOperation.Click(
                    selector = JsonUISelector(type = "ByText", value = "{{target}}"),
                    description = "点击目标元素"
                )
            )
        )
        
        return JsonUIRouteConfig(
            appName = appName,
            packageName = packageName,
            description = description,
            nodes = nodes,
            edges = edges,
            functions = functions
        )
    }

    /** 清除导入导出消息 */
    fun clearImportExportMessage() {
        _uiState.update { it.copy(importExportMessage = null) }
    }
}

class UIRouteViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UIRouteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UIRouteViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 