package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.automatic.UIEdgeDefinition
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageManager
import com.ai.assistance.operit.core.tools.automatic.UIRouteConfig
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageInfo
import com.ai.assistance.operit.data.model.AITool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.UUID
import com.ai.assistance.operit.core.tools.automatic.UINode
import com.ai.assistance.operit.core.tools.automatic.UINodeType
import com.ai.assistance.operit.core.tools.automatic.UIOperation
import com.ai.assistance.operit.core.tools.automatic.UISelector
import com.ai.assistance.operit.core.tools.automatic.UIFunction

/** UI调试工具的ViewModel，负责处理与AITool的交互 */
class UIDebuggerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UIDebuggerState())
    val uiState: StateFlow<UIDebuggerState> = _uiState.asStateFlow()

    private lateinit var toolHandler: AIToolHandler
    private lateinit var packageManager: AutomationPackageManager
    private var statusBarHeight: Int = 0
    private val TAG = "UIDebuggerViewModel"
    private var windowInteractionController: ((Boolean) -> Unit)? = null
    private lateinit var context: Context

    /**
     * 设置窗口交互控制器
     */
    fun setWindowInteractionController(controller: ((Boolean) -> Unit)?) {
        this.windowInteractionController = controller
    }

    /** 初始化ViewModel */
    fun initialize(context: Context) {
        this.context = context
        toolHandler = AIToolHandler.getInstance(context)
        packageManager = AutomationPackageManager.getInstance(context)
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }
        loadAvailablePackages()
    }

    /** 加载可用的自动化配置包 */
    fun loadAvailablePackages() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingPackages = true) }
                val packages = withContext(Dispatchers.IO) {
                    packageManager.getAllPackageInfo()
                }
                _uiState.update { 
                    it.copy(
                        builtInPackages = packages.filter { it.isBuiltIn },
                        externalPackages = packages.filter { !it.isBuiltIn },
                        isLoadingPackages = false,
                        errorMessage = null
                    )
                }
                Log.d(TAG, "加载了 ${packages.size} 个配置包")
            } catch (e: Exception) {
                Log.e(TAG, "加载配置包失败", e)
                _uiState.update { 
                    it.copy(
                        isLoadingPackages = false,
                        errorMessage = "加载配置包失败"
                    )
                }
            }
        }
    }

    /** 选择配置包并加载其节点信息 */
    fun selectPackage(packageInfo: AutomationPackageInfo) {
        viewModelScope.launch {
            try {
                val config = packageManager.getConfigByAppPackageName(packageInfo.packageName)
                if (config != null) {
                    _uiState.update {
                        it.copy(
                            selectedPackage = packageInfo,
                            packageConfig = config,
                            packageNodes = config.nodeDefinitions.values.toList(),
                            // 默认选中第一个节点
                            selectedNodeName = config.nodeDefinitions.values.firstOrNull()?.name,
                            errorMessage = null
                        )
                    }
                    // No need for feedback here, the dialog opening is the feedback
                } else {
                    _uiState.update {
                        it.copy(
                            errorMessage = "无法加载 ${packageInfo.name} 的配置。",
                            selectedPackage = packageInfo, // Still show it's selected
                            packageConfig = null,
                            packageNodes = emptyList()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "选择配置包失败", e)
                showActionFeedback("选择配置包失败")
            }
        }
    }

    /** 清除所选的配置包 */
    fun clearSelectedPackage() {
        _uiState.update {
            it.copy(
                selectedPackage = null,
                packageConfig = null,
                packageNodes = emptyList(),
                selectedNodeName = null
            )
        }
    }

    /** 显示/隐藏配置包选择对话框 */
    fun togglePackageDialog() {
        _uiState.update { it.copy(showPackageDialog = !it.showPackageDialog) }
    }

    /** 显示/隐藏导入导出对话框 */
    fun toggleImportExportDialog() {
        _uiState.update { it.copy(showImportExportDialog = !it.showImportExportDialog) }
    }

    /** 设置导入导出模式 */
    fun setImportExportMode(mode: ImportExportMode) {
        _uiState.update { it.copy(importExportMode = mode) }
    }

    /** 从外部文件导入配置包 */
    fun importPackageFromFile(filePath: String) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    packageManager.importPackage(filePath)
                }
                showActionFeedback(result)
                // 重新加载配置包列表
                loadAvailablePackages()
            } catch (e: Exception) {
                Log.e(TAG, "导入配置包失败", e)
                showActionFeedback("导入失败")
            }
        }
    }

    /** 准备导出，将包信息存储到State中 */
    fun prepareExport(packageInfo: AutomationPackageInfo) {
        _uiState.update { it.copy(selectedPackageForExport = packageInfo) }
    }

    /** 导出配置包到文件 */
    fun exportPackageToFile(packageInfo: AutomationPackageInfo, uriString: String) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    packageManager.exportPackage(packageInfo, uriString)
                }
                showActionFeedback("导出成功: $uriString")
            } catch (e: Exception) {
                Log.e(TAG, "导出配置包失败", e)
                showActionFeedback("导出失败: ${e.message}")
            }
        }
    }

    /** 选择UI元素 */
    fun selectElement(elementId: String?) {
        _uiState.update { it.copy(selectedElementId = elementId) }
    }

    /** 执行元素操作 */
    fun performElementAction(element: UIElement, action: UIElementAction) {
        viewModelScope.launch {
            try {
                when (action) {
                    UIElementAction.CLICK -> {
                        val clickTool = AITool(
                            name = "click_element",
                            parameters = listOf(
                                com.ai.assistance.operit.data.model.ToolParameter(
                                    name = "selector_type",
                                    value = if (element.resourceId != null) "ByResourceId" else "ByText"
                                ),
                                com.ai.assistance.operit.data.model.ToolParameter(
                                    name = "selector_value",
                                    value = element.resourceId ?: element.text
                                )
                            )
                        )
                        val result = withContext(Dispatchers.IO) {
                            toolHandler.executeTool(clickTool)
                        }
                        showActionFeedback(if (result.success) "点击成功" else "点击失败")
                    }
                    UIElementAction.HIGHLIGHT -> {
                        selectElement(element.id)
                        showActionFeedback("已高亮元素")
                    }
                    UIElementAction.INSPECT -> {
                        selectElement(element.id)
                        showActionFeedback("已选择元素")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "执行元素操作失败", e)
                showActionFeedback("操作失败")
            }
        }
    }

    /** 显示操作反馈 */
    private fun showActionFeedback(message: String) {
        _uiState.update { 
            it.copy(
                showActionFeedback = true, 
                actionFeedbackMessage = message
            ) 
        }
        viewModelScope.launch {
            delay(3000)
            _uiState.update { it.copy(showActionFeedback = false) }
        }
    }

    /** 刷新UI元素 */
    fun refreshUI() {
        viewModelScope.launch {
            try {
                windowInteractionController?.invoke(false)
                delay(300)

                val elements = withContext(Dispatchers.IO) {
                    val pageInfoTool = AITool(name = "get_page_info", parameters = listOf())
                    val result = toolHandler.executeTool(pageInfoTool)
                    if (result.success) {
                        val resultData = result.result
                        if (resultData is UIPageResultData) {
                            convertToUIElements(resultData.uiElements)
                        } else {
                            throw Exception("返回数据类型错误")
                        }
                    } else {
                        throw Exception("获取UI信息失败")
                    }
                }

                _uiState.update { it.copy(elements = elements, errorMessage = null) }
            } catch (e: Exception) {
                Log.e(TAG, "刷新UI元素失败", e)
                _uiState.update { it.copy(errorMessage = "刷新失败") }
            } finally {
                windowInteractionController?.invoke(true)
            }
        }
    }

    /**
     * 设置当前选中的节点。
     * @param nodeName 要选中的节点的名称。
     */
    fun selectNode(nodeName: String) {
        _uiState.update { it.copy(selectedNodeName = nodeName) }
    }

    /**
     * 设置视图模式（节点或功能）。
     * @param mode 要设置的视图模式。
     */
    fun setViewMode(mode: UIDebuggerViewMode) {
        _uiState.update { it.copy(currentViewMode = mode) }
    }

    /**
     * 显示操作详情对话框
     * @param edge 要显示详情的边
     * @param fromNodeName 起始节点名称
     */
    fun showOperationDetails(edge: UIEdgeDefinition, fromNodeName: String) {
        _uiState.update { 
            it.copy(
                showOperationDetailsDialog = true,
                selectedEdgeForDetails = edge,
                selectedFromNodeForDetails = fromNodeName
            ) 
        }
    }

    /**
     * 隐藏操作详情对话框
     */
    fun hideOperationDetails() {
        _uiState.update { 
            it.copy(
                showOperationDetailsDialog = false,
                selectedEdgeForDetails = null,
                selectedFromNodeForDetails = null
            ) 
        }
    }

    /**
     * 处理功能点击，跳转到对应的节点
     * @param function 被点击的功能
     */
    fun onFunctionClick(function: com.ai.assistance.operit.core.tools.automatic.UIFunction) {
        // 切换到节点视图
        _uiState.update { it.copy(currentViewMode = UIDebuggerViewMode.NODES) }
        // 选择目标节点
        selectNode(function.targetNodeName)
    }

    /**
     * 获取指定节点的功能列表
     * @param nodeName 节点名称
     * @return 该节点的功能列表
     */
    fun getFunctionsForNode(nodeName: String): List<com.ai.assistance.operit.core.tools.automatic.UIFunction> {
        return _uiState.value.packageConfig?.functionDefinitions?.values?.filter { function ->
            function.targetNodeName == nodeName
        } ?: emptyList()
    }

    /**
     * 显示功能详情对话框
     * @param function 要显示详情的功能
     */
    fun showFunctionDetails(function: com.ai.assistance.operit.core.tools.automatic.UIFunction) {
        _uiState.update { 
            it.copy(
                showFunctionDetailsDialog = true,
                selectedFunctionForDetails = function
            ) 
        }
    }

    /**
     * 隐藏功能详情对话框
     */
    fun hideFunctionDetails() {
        _uiState.update { 
            it.copy(
                showFunctionDetailsDialog = false,
                selectedFunctionForDetails = null
            ) 
        }
    }

    // 编辑相关方法

    /**
     * 显示/隐藏创建包对话框
     */
    fun toggleCreatePackageDialog() {
        _uiState.update { it.copy(showCreatePackageDialog = !it.showCreatePackageDialog) }
    }

    /**
     * 创建新的配置包
     */
    fun createNewPackage(appName: String, packageName: String, description: String) {
        viewModelScope.launch {
            try {
                val newPackageInfo = withContext(Dispatchers.IO) {
                    packageManager.createNewPackage(appName, packageName, description)
                }
                showActionFeedback("配置包创建成功")
                loadAvailablePackages()
                selectPackage(newPackageInfo)
            } catch (e: Exception) {
                Log.e(TAG, "创建配置包失败", e)
                showActionFeedback("创建失败: ${e.message}")
            }
        }
    }

    /**
     * 开始编辑节点
     */
    fun startEditNode(node: UINode? = null) {
        _uiState.update {
            it.copy(
                editMode = if (node == null) EditMode.ADD else EditMode.EDIT,
                editTarget = EditTarget.NODE,
                showEditDialog = true,
                editingNode = node
            )
        }
    }

    /**
     * 开始编辑边
     */
    fun startEditEdge(fromNodeName: String, edge: UIEdgeDefinition? = null) {
        val editingEdge = if (edge == null) {
            EditingEdge(fromNodeName = fromNodeName)
        } else {
            EditingEdge(
                fromNodeName = fromNodeName,
                originalEdge = edge,
                toNodeName = edge.toNodeName,
                operations = edge.operations,
                validation = edge.validation,
                conditions = edge.conditions,
                weight = edge.weight
            )
        }
        
        _uiState.update {
            it.copy(
                editMode = if (edge == null) EditMode.ADD else EditMode.EDIT,
                editTarget = EditTarget.EDGE,
                showEditDialog = true,
                editingEdge = editingEdge
            )
        }
    }

    /**
     * 开始编辑功能
     */
    fun startEditFunction(function: UIFunction? = null) {
        _uiState.update {
            it.copy(
                editMode = if (function == null) EditMode.ADD else EditMode.EDIT,
                editTarget = EditTarget.FUNCTION,
                showEditDialog = true,
                editingFunction = function
            )
        }
    }

    /**
     * 隐藏编辑对话框
     */
    fun hideEditDialog() {
        _uiState.update {
            it.copy(
                showEditDialog = false,
                editMode = EditMode.NONE,
                editingNode = null,
                editingEdge = null,
                editingFunction = null
            )
        }
    }

    /**
     * 保存节点
     */
    fun saveNode(name: String, activityName: String?, nodeType: UINodeType) {
        val currentState = _uiState.value
        val packageInfo = currentState.selectedPackage ?: return
        val config = currentState.packageConfig ?: return

        val newNode = UINode(
            name = name,
            packageName = packageInfo.packageName,
            activityName = activityName,
            nodeType = nodeType
        )

        // 如果是编辑模式，需要先删除旧节点
        if (currentState.editMode == EditMode.EDIT && currentState.editingNode != null) {
            val oldNodeName = currentState.editingNode!!.name
            if (oldNodeName != name) {
                // 节点名称发生变化，需要更新相关的边和功能
                updateReferencesToNode(config, oldNodeName, name)
            }
            config.nodeDefinitions.remove(oldNodeName)
        }

        config.defineNode(newNode)
        saveConfigAndRefresh(config, packageInfo)
        hideEditDialog()
    }

    /**
     * 删除节点
     */
    fun deleteNode(nodeName: String) {
        val currentState = _uiState.value
        val packageInfo = currentState.selectedPackage ?: return
        val config = currentState.packageConfig ?: return

        // 删除节点
        config.nodeDefinitions.remove(nodeName)
        
        // 删除相关的边
        config.edgeDefinitions.remove(nodeName)
        config.edgeDefinitions.values.forEach { edges ->
            edges.removeAll { it.toNodeName == nodeName }
        }
        
        // 删除相关的功能
        config.functionDefinitions.values.removeAll { it.targetNodeName == nodeName }

        saveConfigAndRefresh(config, packageInfo)
        showActionFeedback("节点已删除")
    }

    /**
     * 保存边
     */
    fun saveEdge(editingEdge: EditingEdge) {
        val currentState = _uiState.value
        val packageInfo = currentState.selectedPackage ?: return
        val config = currentState.packageConfig ?: return

        // 如果是编辑模式，先删除旧边
        if (currentState.editMode == EditMode.EDIT && editingEdge.originalEdge != null) {
            val edges = config.edgeDefinitions[editingEdge.fromNodeName]
            edges?.remove(editingEdge.originalEdge)
        }

        // 添加新边
        config.defineEdge(
            fromNodeName = editingEdge.fromNodeName,
            toNodeName = editingEdge.toNodeName,
            operations = editingEdge.operations,
            validation = editingEdge.validation,
            conditions = editingEdge.conditions,
            weight = editingEdge.weight
        )

        saveConfigAndRefresh(config, packageInfo)
        hideEditDialog()
    }

    /**
     * 删除边
     */
    fun deleteEdge(fromNodeName: String, edge: UIEdgeDefinition) {
        val currentState = _uiState.value
        val packageInfo = currentState.selectedPackage ?: return
        val config = currentState.packageConfig ?: return

        val edges = config.edgeDefinitions[fromNodeName]
        edges?.remove(edge)

        saveConfigAndRefresh(config, packageInfo)
        showActionFeedback("边已删除")
    }

    /**
     * 保存功能
     */
    fun saveFunction(name: String, description: String, targetNodeName: String, operation: UIOperation) {
        val currentState = _uiState.value
        val packageInfo = currentState.selectedPackage ?: return
        val config = currentState.packageConfig ?: return

        // 如果是编辑模式，删除旧功能
        if (currentState.editMode == EditMode.EDIT && currentState.editingFunction != null) {
            val oldFunctionName = currentState.editingFunction!!.name
            config.functionDefinitions.remove(oldFunctionName)
        }

        val newFunction = UIFunction(
            name = name,
            description = description,
            targetNodeName = targetNodeName,
            operation = operation
        )

        config.defineFunction(newFunction)
        saveConfigAndRefresh(config, packageInfo)
        hideEditDialog()
    }

    /**
     * 删除功能
     */
    fun deleteFunction(functionName: String) {
        val currentState = _uiState.value
        val packageInfo = currentState.selectedPackage ?: return
        val config = currentState.packageConfig ?: return

        config.functionDefinitions.remove(functionName)
        saveConfigAndRefresh(config, packageInfo)
        showActionFeedback("功能已删除")
    }

    /**
     * 更新节点引用
     */
    private fun updateReferencesToNode(config: UIRouteConfig, oldName: String, newName: String) {
        // 更新边中的引用
        config.edgeDefinitions[oldName]?.let { edges ->
            config.edgeDefinitions[newName] = edges
            config.edgeDefinitions.remove(oldName)
        }
        
        config.edgeDefinitions.values.forEach { edges ->
            val updatedEdges = edges.map { edge ->
                if (edge.toNodeName == oldName) {
                    edge.copy(toNodeName = newName)
                } else {
                    edge
                }
            }
            edges.clear()
            edges.addAll(updatedEdges)
        }
        
        // 更新功能中的引用
        val updatedFunctions = config.functionDefinitions.values.map { function ->
            if (function.targetNodeName == oldName) {
                function.copy(targetNodeName = newName)
            } else {
                function
            }
        }
        config.functionDefinitions.clear()
        updatedFunctions.forEach { config.defineFunction(it) }
    }

    /**
     * 保存配置并刷新
     */
    private fun saveConfigAndRefresh(config: UIRouteConfig, packageInfo: AutomationPackageInfo) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    packageManager.saveConfig(config, packageInfo)
                }
                showActionFeedback(result)
                // 重新选择包以刷新数据
                selectPackage(packageInfo)
                _uiState.update { it.copy(isConfigModified = false) }
            } catch (e: Exception) {
                Log.e(TAG, "保存配置失败", e)
                showActionFeedback("保存失败: ${e.message}")
            }
        }
    }

    /**
     * 标记配置已修改
     */
    fun markConfigModified() {
        _uiState.update { it.copy(isConfigModified = true) }
    }

    /**
     * 创建简单的点击操作
     */
    fun createClickOperation(selectorType: String, selectorValue: String, description: String): UIOperation.Click {
        val selector = when (selectorType) {
            "ByResourceId" -> UISelector.ByResourceId(selectorValue)
            "ByText" -> UISelector.ByText(selectorValue)
            "ByContentDesc" -> UISelector.ByContentDesc(selectorValue)
            "ByClassName" -> UISelector.ByClassName(selectorValue)
            else -> UISelector.ByText(selectorValue)
        }
        return UIOperation.Click(selector, description)
    }

    /**
     * 创建输入操作
     */
    fun createInputOperation(selectorType: String, selectorValue: String, textVariableKey: String): UIOperation.Input {
        val selector = when (selectorType) {
            "ByResourceId" -> UISelector.ByResourceId(selectorValue)
            "ByText" -> UISelector.ByText(selectorValue)
            "ByContentDesc" -> UISelector.ByContentDesc(selectorValue)
            "ByClassName" -> UISelector.ByClassName(selectorValue)
            else -> UISelector.ByText(selectorValue)
        }
        return UIOperation.Input(selector, textVariableKey)
    }

    private fun convertToUIElements(
        node: com.ai.assistance.operit.core.tools.SimplifiedUINode
    ): List<UIElement> {
        val elements = mutableListOf<UIElement>()
        processNode(node, elements)
        return elements
    }

    private fun processNode(
        node: com.ai.assistance.operit.core.tools.SimplifiedUINode,
        elements: MutableList<UIElement>
    ) {
        val element = createUiElement(node)
        elements.add(element)
        node.children.forEach { childNode -> processNode(childNode, elements) }
    }

    private fun createUiElement(
        node: com.ai.assistance.operit.core.tools.SimplifiedUINode
    ): UIElement {
        val bounds = node.bounds?.let {
            try {
                val coordsPattern = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
                val matchResult = coordsPattern.find(it)
                if (matchResult != null) {
                    val (left, top, right, bottom) = matchResult.destructured
                    Rect(left.toInt(), top.toInt() - statusBarHeight, right.toInt(), bottom.toInt() - statusBarHeight)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析边界失败", e)
                null
            }
        }

        return UIElement(
            id = UUID.randomUUID().toString(),
            className = node.className ?: "Unknown",
            resourceId = node.resourceId,
            contentDesc = node.contentDesc,
            text = node.text ?: "",
            bounds = bounds,
            isClickable = node.isClickable
        )
    }
}
