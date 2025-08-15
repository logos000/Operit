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
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageInfo
import com.ai.assistance.operit.core.tools.automatic.UIRouteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*
import kotlinx.coroutines.delay

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
    val actionType: String = "", // 操作类型：click, input, swipe等
    val targetElement: String = "", // 目标元素信息
    val condition: String = "", // 触发条件
    val color: Color = Color.Gray
)

data class MindMapUiState(
    val nodes: List<MindMapNode> = emptyList(),
    val connections: List<MindMapConnection> = emptyList(),
    val selectedNodeId: String? = null,
    val selectedConnectionId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // 导入导出状态
    val showImportDialog: Boolean = false,
    val showExportDialog: Boolean = false,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val importExportMessage: String? = null,
    // 内置配置包选择
    val showBuiltInConfigDialog: Boolean = false,
    val availablePackages: List<AutomationPackageInfo> = emptyList(),
    // 删除确认对话框
    val showDeleteConfirmDialog: Boolean = false,
    val nodeToDelete: String? = null
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
            title = "应用启动",
            content = "com.example.app",
            position = Offset(400f, 300f),
            color = Color(0xFF2196F3),
            nodeType = "START"
        )

        val node1 = MindMapNode(
            id = "node1",
            title = "登录页面",
            content = "com.example.app.LoginActivity",
            position = Offset(200f, 200f),
            color = Color(0xFF4CAF50),
            nodeType = "CLICK"
        )

        val node2 = MindMapNode(
            id = "node2",
            title = "用户输入",
            content = "com.example.app.MainActivity",
            position = Offset(600f, 200f),
            color = Color(0xFFFF9800),
            nodeType = "INPUT"
        )

        val node3 = MindMapNode(
            id = "node3",
            title = "首页滑动",
            content = "com.example.app.HomeActivity",
            position = Offset(400f, 500f),
            color = Color(0xFF9C27B0),
            nodeType = "SWIPE"
        )

        val connections = listOf(
            MindMapConnection(
                id = "conn1",
                fromNodeId = "center",
                toNodeId = "node1",
                label = "点击登录",
                actionType = "CLICK",
                targetElement = "登录按钮",
                condition = "页面加载完成"
            ),
            MindMapConnection(
                id = "conn2",
                fromNodeId = "center",
                toNodeId = "node2",
                label = "输入用户名",
                actionType = "INPUT",
                targetElement = "用户名输入框",
                condition = "输入框可见"
            ),
            MindMapConnection(
                id = "conn3",
                fromNodeId = "center",
                toNodeId = "node3",
                label = "滑动页面",
                actionType = "SWIPE",
                targetElement = "主页面",
                condition = "内容超出屏幕"
            )
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

    fun clearSelection() {
        _uiState.update {
            it.copy(selectedNodeId = null, selectedConnectionId = null)
        }
    }

    fun addNode(title: String, content: String, position: Offset, nodeType: String = "NORMAL") {
        val newNode = MindMapNode(
            id = "node_${System.currentTimeMillis()}",
            title = title,
            content = content,
            position = position,
            color = getNodeColorByNodeType(nodeType),
            nodeType = nodeType
        )

        _uiState.update { 
            it.copy(nodes = it.nodes + newNode)
        }
        
        // 自动应用树形布局
        applyTreeLayout()
    }

    fun addFunctionNode(title: String, content: String, position: Offset) {
        addNode(title, content, position, "FUNCTION")
    }

    private fun getNodeColorByNodeType(nodeType: String): Color {
        return when (nodeType) {
            "APP_HOME" -> Color(0xFF4CAF50) // Green
            "LIST_PAGE" -> Color(0xFF2196F3) // Blue  
            "DETAIL_PAGE" -> Color(0xFFFF9800) // Orange
            "SETTING_PAGE" -> Color(0xFF9C27B0) // Purple
            "SYSTEM_PAGE" -> Color(0xFF607D8B) // Blue Grey
            "FUNCTION" -> Color(0xFFE91E63) // Pink for functions
            "DIALOG" -> Color(0xFFFF5722) // Deep Orange
            else -> getRandomColor() // Random for others
        }
    }

    fun addChildNode(parentNodeId: String) {
        val newNodeId = "node_${System.currentTimeMillis()}"
        val newConnectionId = "conn_${System.currentTimeMillis()}"
        
        _uiState.update { state ->
            val parentNode = state.nodes.find { it.id == parentNodeId } ?: return@update state
            
            // 创建新节点，暂时使用临时位置
            val newNode = MindMapNode(
                id = newNodeId,
                title = "新节点",
                content = "点击编辑内容",
                position = parentNode.position + Offset(100f, 100f), // 临时位置
                color = getRandomColor()
            )
            
            val newConnection = MindMapConnection(
                id = newConnectionId,
                fromNodeId = parentNode.id,
                toNodeId = newNode.id
            )

            state.copy(
                nodes = state.nodes + newNode,
                connections = state.connections + newConnection,
                selectedNodeId = newNode.id, // select the new node
                selectedConnectionId = null
            )
        }
        
        // 立即重新应用树形布局
        applyTreeLayout()
    }

    // 注释掉节点位置更新功能，因为已经移除了节点拖拽
    // fun updateNodePosition(nodeId: String, newPosition: Offset) {
    //     _uiState.update { 
    //         it.copy(
    //             nodes = it.nodes.map { node ->
    //                 if (node.id == nodeId) node.copy(position = newPosition)
    //                 else node
    //             }
    //         )
    //     }
    // }

    fun updateNode(nodeId: String, title: String, content: String) {
        _uiState.update { 
            it.copy(
                nodes = it.nodes.map { node ->
                    if (node.id == nodeId) node.copy(title = title, content = content)
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
        
        // 自动应用树形布局
        applyTreeLayout()
    }

    fun deleteNode(nodeId: String) {
        _uiState.update { 
            it.copy(
                showDeleteConfirmDialog = true,
                nodeToDelete = nodeId
            )
        }
    }
    
    fun confirmDeleteNode() {
        val nodeToDelete = _uiState.value.nodeToDelete
        if (nodeToDelete != null) {
            _uiState.update { 
                it.copy(
                    nodes = it.nodes.filter { it.id != nodeToDelete },
                connections = it.connections.filter { 
                        it.fromNodeId != nodeToDelete && it.toNodeId != nodeToDelete 
                    },
                    showDeleteConfirmDialog = false,
                    nodeToDelete = null,
                    selectedNodeId = null // 清除选择状态
                )
            }
            
            // 自动应用树形布局
            applyTreeLayout()
        }
    }
    
    fun cancelDeleteNode() {
        _uiState.update { 
            it.copy(
                showDeleteConfirmDialog = false,
                nodeToDelete = null
            )
        }
    }

    fun deleteConnection(connectionId: String) {
        _uiState.update { 
            it.copy(
                connections = it.connections.filter { it.id != connectionId }
            )
        }
        
        // 自动应用树形布局
        applyTreeLayout()
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

    /** 显示内置配置包选择对话框 */
    fun showBuiltInConfigDialog() {
        val packages = packageManager.getAllPackageInfo()
        _uiState.update { 
            it.copy(
                showBuiltInConfigDialog = true, 
                availablePackages = packages,
                importExportMessage = null
            ) 
        }
    }

    /** 隐藏内置配置包选择对话框 */
    fun hideBuiltInConfigDialog() {
        _uiState.update { it.copy(showBuiltInConfigDialog = false) }
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
                    
                    // 尝试解析为思维导图数据
                    try {
                        val data = json.decodeFromString<SerializableMindMapData>(jsonContent)
                        convertSerializableToMindMap(data)
                    } catch (e: Exception) {
                        // 如果失败，尝试解析为UI自动化配置
                        try {
                            val uiConfig = json.decodeFromString<JsonUIRouteConfig>(jsonContent)
                            convertUIRouteConfigToMindMap(uiConfig)
                        } catch (e2: Exception) {
                            throw Exception("无法解析文件格式，请确保是有效的思维导图或UI配置文件")
                        }
                    }
                }
                
                _uiState.update { 
                    it.copy(
                        nodes = result.first,
                        connections = result.second,
                        isImporting = false,
                        importExportMessage = "成功导入数据",
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

    /** 获取所有可用的内置自动化配置 */
    fun getAvailableAutomationPackages(): List<AutomationPackageInfo> {
        return packageManager.getAllPackageInfo()
    }

    /** 从内置自动化配置导入思维导图 */
    fun importFromAutomationPackage(packageInfo: AutomationPackageInfo) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isImporting = true, importExportMessage = null) }
                
                val result = withContext(Dispatchers.IO) {
                    val config = packageManager.getConfigByAppPackageName(packageInfo.packageName)
                        ?: throw Exception("无法加载配置包: ${packageInfo.name}")
                    
                    // 将UIRouteConfig转换为JsonUIRouteConfig格式
                    val jsonConfig = JsonUIRouteConfig(
                        appName = packageInfo.name,
                        packageName = packageInfo.packageName,
                        description = packageInfo.description,
                        nodes = config.nodeDefinitions.values.map { node ->
                            JsonUINode(
                                name = node.name,
                                description = "UI节点: ${node.name}",
                                activityName = node.activityName,
                                nodeType = node.nodeType.name
                            )
                        },
                        edges = config.edgeDefinitions.flatMap { (fromName, edgeList) ->
                            edgeList.map { edge ->
                                JsonUIEdge(
                                    from = fromName,
                                    to = edge.toNodeName,
                                    operations = convertUIOperationsToJson(edge.operations),
                                    weight = edge.weight
                                )
                            }
                        },
                        functions = config.functionDefinitions.values.map { function ->
                            JsonUIFunction(
                                name = function.name,
                                description = function.description,
                                targetNodeName = function.targetNodeName
                            )
                        }
                    )
                    
                    convertUIRouteConfigToMindMap(jsonConfig)
                }
                
                _uiState.update { 
                    it.copy(
                        nodes = result.first,
                        connections = result.second,
                        isImporting = false,
                        importExportMessage = "成功导入配置包: ${packageInfo.name}",
                        showImportDialog = false,
                        showBuiltInConfigDialog = false // 导入成功后关闭内置配置对话框
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

    /** 将可序列化的思维导图数据转换为内部数据结构 */
    private fun convertSerializableToMindMap(data: SerializableMindMapData): Pair<List<MindMapNode>, List<MindMapConnection>> {
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
        
        return Pair(nodes, connections)
    }

    /** 操作信息数据类 */
    private data class OperationInfo(
        val label: String,
        val actionType: String,
        val targetElement: String,
        val condition: String
    )

    /** 提取操作信息 */
    private fun extractOperationInfo(operations: List<JsonUIOperation>): OperationInfo {
        if (operations.isEmpty()) {
            return OperationInfo("导航", "", "", "")
        }
        
        val primaryOperation = operations.first()
        val actionType = when (primaryOperation) {
            is JsonUIOperation.Click -> "CLICK"
            is JsonUIOperation.Input -> "INPUT"
            is JsonUIOperation.LaunchApp -> "LAUNCH"
            is JsonUIOperation.PressKey -> "PRESS_KEY"
            is JsonUIOperation.Wait -> "WAIT"
            is JsonUIOperation.Sequential -> "SEQUENTIAL"
            is JsonUIOperation.ValidateElement -> "VALIDATE"
        }
        
        val targetElement = when (primaryOperation) {
            is JsonUIOperation.Click -> extractSelectorText(primaryOperation.selector)
            is JsonUIOperation.Input -> extractSelectorText(primaryOperation.selector)
            is JsonUIOperation.LaunchApp -> primaryOperation.packageName
            is JsonUIOperation.PressKey -> primaryOperation.keyCode
            is JsonUIOperation.Wait -> "${primaryOperation.durationMs}ms"
            is JsonUIOperation.Sequential -> "多步操作(${primaryOperation.operations.size})"
            is JsonUIOperation.ValidateElement -> extractSelectorText(primaryOperation.selector)
        }
        
        val label = primaryOperation.description ?: when (primaryOperation) {
            is JsonUIOperation.Click -> "点击"
            is JsonUIOperation.Input -> "输入"
            is JsonUIOperation.LaunchApp -> "启动应用"
            is JsonUIOperation.PressKey -> "按键"
            is JsonUIOperation.Wait -> "等待"
            is JsonUIOperation.Sequential -> "顺序操作"
            is JsonUIOperation.ValidateElement -> "验证元素"
        }
        
        val condition = if (operations.size > 1) {
            "多步操作：共${operations.size}步"
        } else {
            ""
        }
        
        return OperationInfo(label, actionType, targetElement, condition)
    }

    /** 提取选择器的文本描述 */
    private fun extractSelectorText(selector: JsonUISelector): String {
        return when (selector.type) {
            "ByText" -> selector.text ?: selector.value ?: "文本元素"
            "ByResourceId" -> selector.id ?: selector.value ?: "ID元素"
            "ByClassName" -> selector.name ?: selector.value ?: "类名元素"
            "ByContentDescription" -> selector.desc ?: selector.value ?: "描述元素"
            "ByBounds" -> "坐标元素"
            "ByXPath" -> "XPath元素"
            "Compound" -> "复合选择器"
            else -> "未知元素"
        }
    }

    /** 将UIOperation转换为JsonUIOperation */
    private fun convertUIOperationsToJson(operations: List<UIOperation>): List<JsonUIOperation> {
        return operations.map { operation ->
            when (operation) {
                is UIOperation.Click -> JsonUIOperation.Click(
                    selector = convertUISelectorToJson(operation.selector),
                    description = operation.description,
                    relativeX = operation.relativeX,
                    relativeY = operation.relativeY
                )
                is UIOperation.Input -> JsonUIOperation.Input(
                    selector = convertUISelectorToJson(operation.selector),
                    textVariableKey = operation.textVariableKey,
                    description = operation.description
                )
                is UIOperation.LaunchApp -> JsonUIOperation.LaunchApp(
                    packageName = operation.packageName,
                    description = operation.description
                )
                is UIOperation.KillApp -> JsonUIOperation.LaunchApp(
                    packageName = operation.packageName,
                    description = operation.description
                )
                is UIOperation.PressKey -> JsonUIOperation.PressKey(
                    keyCode = operation.keyCode,
                    description = operation.description
                )
                is UIOperation.Wait -> JsonUIOperation.Wait(
                    durationMs = operation.durationMs,
                    description = operation.description
                )
                is UIOperation.WaitForPage -> JsonUIOperation.Wait(
                    durationMs = operation.timeoutMs,
                    description = operation.description
                )
                is UIOperation.Sequential -> JsonUIOperation.Sequential(
                    operations = convertUIOperationsToJson(operation.operations),
                    description = operation.description
                )
                is UIOperation.ValidateElement -> JsonUIOperation.ValidateElement(
                    selector = convertUISelectorToJson(operation.selector),
                    expectedValueKey = operation.expectedValueKey,
                    validationType = operation.validationType.name,
                    description = operation.description
                )
                is UIOperation.ValidateState -> JsonUIOperation.ValidateElement(
                    selector = JsonUISelector(type = "ByText", value = "state"),
                    expectedValueKey = "validated_state",
                    validationType = "EXISTS",
                    description = operation.description
                )
                is UIOperation.Swipe -> JsonUIOperation.Sequential(
                    operations = emptyList(),
                    description = operation.description
                )
                is UIOperation.NoOp -> JsonUIOperation.Wait(
                    durationMs = 0,
                    description = operation.description
                )
            }
        }
    }

    /** 将UISelector转换为JsonUISelector */
    private fun convertUISelectorToJson(selector: UISelector): JsonUISelector {
        return when (selector) {
            is UISelector.ByText -> JsonUISelector(
                type = "ByText",
                text = selector.text,
                value = selector.text
            )
            is UISelector.ByResourceId -> JsonUISelector(
                type = "ByResourceId",
                id = selector.id,
                value = selector.id
            )
            is UISelector.ByClassName -> JsonUISelector(
                type = "ByClassName",
                name = selector.name,
                value = selector.name
            )
            is UISelector.ByContentDesc -> JsonUISelector(
                type = "ByContentDescription",
                desc = selector.desc,
                value = selector.desc
            )
            is UISelector.ByBounds -> JsonUISelector(
                type = "ByBounds",
                bounds = selector.bounds,
                value = selector.bounds
            )
            is UISelector.ByXPath -> JsonUISelector(
                type = "ByXPath",
                xpath = selector.xpath,
                value = selector.xpath
            )
            is UISelector.Compound -> JsonUISelector(
                type = "Compound",
                selectors = selector.selectors.map { convertUISelectorToJson(it) },
                operator = selector.operator
            )
        }
    }

    /** 将UI路由配置转换为思维导图数据结构 */
    private fun convertUIRouteConfigToMindMap(config: JsonUIRouteConfig): Pair<List<MindMapNode>, List<MindMapConnection>> {
        val nodeMap = mutableMapOf<String, MindMapNode>()
        val connections = mutableListOf<MindMapConnection>()
        
        // 创建节点
        config.nodes.forEachIndexed { index, jsonNode ->
            val position = calculateNodePosition(index, config.nodes.size)
            val nodeColor = getNodeColorByType(jsonNode.nodeType)
            
            val node = MindMapNode(
                id = "node_${jsonNode.name}",
                title = jsonNode.name,
                content = "${jsonNode.description}\n类型: ${jsonNode.nodeType}${
                    if (jsonNode.activityName != null) "\n活动: ${jsonNode.activityName}" else ""
                }",
                position = position,
                color = nodeColor,
                nodeType = jsonNode.nodeType
            )
            nodeMap[jsonNode.name] = node
        }
        
        // 创建边连接
        config.edges.forEach { edge ->
            val fromNode = nodeMap[edge.from]
            val toNode = nodeMap[edge.to]
            
            if (fromNode != null && toNode != null) {
                // 提取操作信息
                val operationInfo = extractOperationInfo(edge.operations)
                
                val connection = MindMapConnection(
                    id = "conn_${edge.from}_${edge.to}",
                    fromNodeId = fromNode.id,
                    toNodeId = toNode.id,
                    label = operationInfo.label,
                    actionType = operationInfo.actionType,
                    targetElement = operationInfo.targetElement,
                    condition = operationInfo.condition,
                    color = Color.Gray
                )
                connections.add(connection)
            }
        }
        
        // 添加功能节点
        config.functions.forEach { function ->
            val targetNode = nodeMap[function.targetNodeName]
            if (targetNode != null) {
                val functionNode = MindMapNode(
                    id = "func_${function.name}",
                    title = "功能: ${function.name}",
                    content = "${function.description}\n目标: ${function.targetNodeName}",
                    position = Offset(targetNode.position.x + 200f, targetNode.position.y - 100f),
                    color = Color(0xFFE91E63), // Pink for functions
                    nodeType = "FUNCTION"
                )
                nodeMap["func_${function.name}"] = functionNode
                
                // 连接功能到目标节点
                val connection = MindMapConnection(
                    id = "conn_func_${function.name}_${function.targetNodeName}",
                    fromNodeId = functionNode.id,
                    toNodeId = targetNode.id,
                    label = "执行",
                    color = Color(0xFFE91E63)
                )
                connections.add(connection)
            }
        }
        
        return Pair(nodeMap.values.toList(), connections)
    }

    /** 根据节点索引计算节点位置 */
    private fun calculateNodePosition(index: Int, totalNodes: Int): Offset {
        val radius = 300f
        val centerX = 500f
        val centerY = 400f
        
        if (totalNodes == 1) {
            return Offset(centerX, centerY)
        }
        
        val angle = (2 * Math.PI * index / totalNodes).toFloat()
        val x = centerX + radius * cos(angle)
        val y = centerY + radius * sin(angle)
        
        return Offset(x, y)
    }

    /** 根据节点类型获取颜色 */
    private fun getNodeColorByType(nodeType: String): Color {
        return when (nodeType) {
            "APP_HOME" -> Color(0xFF4CAF50) // Green
            "LIST_PAGE" -> Color(0xFF2196F3) // Blue
            "DETAIL_PAGE" -> Color(0xFFFF9800) // Orange
            "SETTING_PAGE" -> Color(0xFF9C27B0) // Purple
            "SYSTEM_PAGE" -> Color(0xFF607D8B) // Blue Grey
            "FUNCTION" -> Color(0xFFE91E63) // Pink
            else -> Color(0xFF795548) // Brown
        }
    }

    /** 清除导入导出消息 */
    fun clearImportExportMessage() {
        _uiState.update { it.copy(importExportMessage = null) }
    }

    // 自动布局相关功能
    
    /**
     * 自动布局所有节点
     */
    fun autoLayout(layoutType: LayoutType = LayoutType.FORCE_DIRECTED) {
        when (layoutType) {
            LayoutType.FORCE_DIRECTED -> applyForceDirectedLayout()
            LayoutType.TREE -> applyTreeLayout()
            LayoutType.CIRCLE -> applyCircularLayout()
            LayoutType.HIERARCHICAL -> applyHierarchicalLayout()
        }
    }
    
    /**
     * 力导向布局算法
     */
    fun applyForceDirectedLayout() {
        val state = _uiState.value
        if (state.nodes.isEmpty()) return
        
        // 参数设置
        val iterations = 50
        val cooling = 0.95f
        val repulsion = 30000f
        val attraction = 0.1f
        val damping = 0.9f
        
        val nodePositions = state.nodes.associate { it.id to it.position.copy() }.toMutableMap()
        val velocities = state.nodes.associate { it.id to Offset.Zero }.toMutableMap()
        
        repeat(iterations) { iteration ->
            val forces = mutableMapOf<String, Offset>()
            
            // 初始化力
            state.nodes.forEach { node ->
                forces[node.id] = Offset.Zero
            }
            
            // 计算排斥力（所有节点之间）
            state.nodes.forEach { node1 ->
                state.nodes.forEach { node2 ->
                    if (node1.id != node2.id) {
                        val pos1 = nodePositions[node1.id]!!
                        val pos2 = nodePositions[node2.id]!!
                        val distance = sqrt((pos1.x - pos2.x).pow(2) + (pos1.y - pos2.y).pow(2))
                        
                        if (distance > 0) {
                            val force = repulsion / (distance * distance)
                            val direction = Offset(
                                (pos1.x - pos2.x) / distance,
                                (pos1.y - pos2.y) / distance
                            )
                            forces[node1.id] = forces[node1.id]!! + direction * force
                        }
                    }
                }
            }
            
            // 计算吸引力（连接的节点之间）
            state.connections.forEach { connection ->
                val pos1 = nodePositions[connection.fromNodeId]
                val pos2 = nodePositions[connection.toNodeId]
                
                if (pos1 != null && pos2 != null) {
                    val distance = sqrt((pos1.x - pos2.x).pow(2) + (pos1.y - pos2.y).pow(2))
                    
                    if (distance > 0) {
                        val force = attraction * distance
                        val direction = Offset(
                            (pos2.x - pos1.x) / distance,
                            (pos2.y - pos1.y) / distance
                        )
                        
                        forces[connection.fromNodeId] = forces[connection.fromNodeId]!! + direction * force
                        forces[connection.toNodeId] = forces[connection.toNodeId]!! - direction * force
                    }
                }
            }
            
            // 应用力和更新位置
            val temperature = 1.0f * cooling.pow(iteration.toFloat())
            
            state.nodes.forEach { node ->
                val force = forces[node.id]!!
                val velocity = velocities[node.id]!! * damping + force * 0.01f
                velocities[node.id] = velocity
                
                val displacement = velocity * temperature
                val newPosition = nodePositions[node.id]!! + displacement
                
                // 边界约束
                nodePositions[node.id] = Offset(
                    newPosition.x.coerceIn(50f, 1400f),
                    newPosition.y.coerceIn(50f, 800f)
                )
            }
        }
        
        // 更新节点位置
        _uiState.update { state ->
            state.copy(
                nodes = state.nodes.map { node ->
                    node.copy(position = nodePositions[node.id]!!)
                }
            )
        }
    }
    
    /**
     * 改进的树形布局算法
     * 更好地处理连接关系，支持多根节点和森林结构
     */
    private fun applyTreeLayout() {
        val state = _uiState.value
        if (state.nodes.isEmpty()) return
        
        val positions = mutableMapOf<String, Offset>()
        val visited = mutableSetOf<String>()
        
        // 布局参数
        val levelHeight = 180f
        val nodeSpacing = 250f
        val forestSpacing = 150f // 不同树之间的间距
        var currentForestX = 0f
        
        // 找到所有根节点（没有入边的节点）
        val rootNodes = findAllRootNodes(state)
        
        if (rootNodes.isEmpty()) {
            // 如果没有明确的根节点（可能存在循环），选择第一个节点作为根
            val firstNode = state.selectedNodeId?.let { selectedId ->
                state.nodes.find { it.id == selectedId }
            } ?: state.nodes.first()
            
            val tree = buildTreeFromNode(state, firstNode.id, visited)
            layoutTreeNode(tree, 0, currentForestX, positions, levelHeight, nodeSpacing)
        } else {
            // 为每个根节点构建并布局其树
            rootNodes.forEach { rootNode ->
                if (rootNode.id !in visited) {
                    val tree = buildTreeFromNode(state, rootNode.id, visited)
                    val treeWidth = layoutTreeNode(tree, 0, currentForestX, positions, levelHeight, nodeSpacing)
                    currentForestX += treeWidth + forestSpacing
                }
            }
        }
        
        // 处理剩余的孤立节点
        state.nodes.forEach { node ->
            if (node.id !in positions) {
                positions[node.id] = Offset(currentForestX, 0f)
                currentForestX += nodeSpacing
            }
        }
        
        // 居中整个布局
        val minX = positions.values.minOfOrNull { it.x } ?: 0f
        val maxX = positions.values.maxOfOrNull { it.x } ?: 0f
        val centerX = 700f // 画布中心
        val offsetX = centerX - (minX + maxX) / 2
        
        // 更新节点位置
        _uiState.update { state ->
            state.copy(
                nodes = state.nodes.map { node ->
                    val pos = positions[node.id]
                    if (pos != null) {
                        node.copy(position = Offset(pos.x + offsetX, pos.y + 100f))
                    } else {
                        node
                    }
                }
            )
        }
    }
    
    /**
     * 圆形布局算法
     */
    fun applyCircularLayout() {
        val state = _uiState.value
        if (state.nodes.isEmpty()) return
        
        val centerX = 700f
        val centerY = 400f
        val radius = 250f
        
        val positions = mutableMapOf<String, Offset>()
        
        if (state.nodes.size == 1) {
            positions[state.nodes.first().id] = Offset(centerX, centerY)
        } else {
            state.nodes.forEachIndexed { index, node ->
                val angle = 2 * PI * index / state.nodes.size
                val x = centerX + radius * cos(angle).toFloat()
                val y = centerY + radius * sin(angle).toFloat()
                positions[node.id] = Offset(x, y)
            }
        }
        
        // 更新节点位置
        _uiState.update { state ->
            state.copy(
                nodes = state.nodes.map { node ->
                    node.copy(position = positions[node.id] ?: node.position)
                }
            )
        }
    }
    
    /**
     * 网格布局算法
     */
    fun applyGridLayout() {
        val state = _uiState.value
        if (state.nodes.isEmpty()) return
        
        val gridSize = ceil(sqrt(state.nodes.size.toDouble())).toInt()
        val nodeSpacing = 200f
        val startX = 300f
        val startY = 200f
        
        val positions = mutableMapOf<String, Offset>()
        
        state.nodes.forEachIndexed { index, node ->
            val row = index / gridSize
            val col = index % gridSize
            val x = startX + col * nodeSpacing
            val y = startY + row * nodeSpacing
            positions[node.id] = Offset(x, y)
        }
        
        // 更新节点位置
        _uiState.update { state ->
            state.copy(
                nodes = state.nodes.map { node ->
                    node.copy(position = positions[node.id] ?: node.position)
                }
            )
        }
    }
    
    /**
     * 分层布局算法
     */
    fun applyHierarchicalLayout() {
        val state = _uiState.value
        if (state.nodes.isEmpty()) return
        
        // 使用拓扑排序确定层级
        val layers = topologicalSort(state)
        val positions = mutableMapOf<String, Offset>()
        
        val layerHeight = 150f
        val nodeSpacing = 200f
        val startY = 100f
        
        layers.forEachIndexed { layerIndex, layer ->
            val y = startY + layerIndex * layerHeight
            val totalWidth = (layer.size - 1) * nodeSpacing
            val startX = 700f - totalWidth / 2 // 居中
            
            layer.forEachIndexed { nodeIndex, nodeId ->
                val x = startX + nodeIndex * nodeSpacing
                positions[nodeId] = Offset(x, y)
            }
        }
        
        // 更新节点位置
        _uiState.update { state ->
            state.copy(
                nodes = state.nodes.map { node ->
                    node.copy(position = positions[node.id] ?: node.position)
                }
            )
        }
    }
    
    // 辅助方法
    
    private fun findRootNode(state: MindMapUiState): MindMapNode? {
        val nodesWithIncomingEdges = state.connections.map { it.toNodeId }.toSet()
        return state.nodes.find { it.id !in nodesWithIncomingEdges }
            ?: state.nodes.find { it.id == state.selectedNodeId }
    }
    
    /**
     * 找到所有根节点（没有入边的节点）
     */
    private fun findAllRootNodes(state: MindMapUiState): List<MindMapNode> {
        val nodesWithIncomingEdges = state.connections.map { it.toNodeId }.toSet()
        return state.nodes.filter { it.id !in nodesWithIncomingEdges }
    }
    
    /**
     * 从指定节点构建树结构，避免重复访问
     */
    private fun buildTreeFromNode(state: MindMapUiState, rootId: String, visited: MutableSet<String>): TreeNode {
        visited.add(rootId)
        
        val children = state.connections
            .filter { it.fromNodeId == rootId && it.toNodeId !in visited }
            .map { buildTreeFromNode(state, it.toNodeId, visited) }
        
        return TreeNode(rootId, children)
    }
    
    private fun buildTree(state: MindMapUiState, rootId: String): TreeNode {
        val children = state.connections
            .filter { it.fromNodeId == rootId }
            .map { buildTree(state, it.toNodeId) }
        
        return TreeNode(rootId, children)
    }
    
    private fun layoutTreeNode(
        tree: TreeNode,
        level: Int,
        startX: Float,
        positions: MutableMap<String, Offset>,
        levelHeight: Float,
        nodeSpacing: Float
    ): Float {
        val y = level * levelHeight
        
        if (tree.children.isEmpty()) {
            positions[tree.nodeId] = Offset(startX, y)
            return startX + nodeSpacing
        }
        
        var currentX = startX
        val childPositions = mutableListOf<Float>()
        
        tree.children.forEach { child ->
            val childEndX = layoutTreeNode(child, level + 1, currentX, positions, levelHeight, nodeSpacing)
            // 记录子节点的实际X位置
            val childActualX = positions[child.nodeId]?.x ?: currentX
            childPositions.add(childActualX)
            currentX = childEndX
        }
        
        val nodeX = if (childPositions.isNotEmpty()) {
            (childPositions.first() + childPositions.last()) / 2
        } else {
            startX
        }
        
        positions[tree.nodeId] = Offset(nodeX, y)
        return currentX
    }
    
    private fun topologicalSort(state: MindMapUiState): List<List<String>> {
        val inDegree = mutableMapOf<String, Int>()
        val outEdges = mutableMapOf<String, MutableList<String>>()
        
        // 初始化
        state.nodes.forEach { node ->
            inDegree[node.id] = 0
            outEdges[node.id] = mutableListOf()
        }
        
        // 计算入度和出边
        state.connections.forEach { connection ->
            inDegree[connection.toNodeId] = (inDegree[connection.toNodeId] ?: 0) + 1
            outEdges[connection.fromNodeId]?.add(connection.toNodeId)
        }
        
        val layers = mutableListOf<List<String>>()
        val remaining = inDegree.toMutableMap()
        
        while (remaining.isNotEmpty()) {
            val currentLayer = remaining.filter { it.value == 0 }.keys.toList()
            if (currentLayer.isEmpty()) break // 避免循环
            
            layers.add(currentLayer)
            
            currentLayer.forEach { nodeId ->
                remaining.remove(nodeId)
                outEdges[nodeId]?.forEach { targetId ->
                    remaining[targetId] = (remaining[targetId] ?: 1) - 1
                }
            }
        }
        
        // 添加剩余节点（处理循环情况）
        if (remaining.isNotEmpty()) {
            layers.add(remaining.keys.toList())
        }
        
        return layers
    }
    
    // 数据类
    private data class TreeNode(
        val nodeId: String,
        val children: List<TreeNode>
    )
    
    enum class LayoutType {
        FORCE_DIRECTED,  // 力导向布局
        TREE,           // 树形布局
        CIRCLE,         // 圆形布局
        HIERARCHICAL    // 分层布局
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