package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageManager
import com.ai.assistance.operit.core.tools.automatic.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File

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

    // JSON序列化配置
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true 
    }

    /**
     * 设置窗口交互控制器
     * @param controller 一个函数，输入Boolean值，用于控制窗口的交互状态
     */
    fun setWindowInteractionController(controller: ((Boolean) -> Unit)?) {
        this.windowInteractionController = controller
    }

    /** 初始化ViewModel */
    fun initialize(context: Context) {
        this.context = context
        toolHandler = AIToolHandler.getInstance(context)
        packageManager = AutomationPackageManager.getInstance(context)
        // 获取状态栏高度
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }
        // 加载可用配置
        loadAvailableConfigs()
    }

    /** 加载可用的自动化配置 */
    private fun loadAvailableConfigs() {
        viewModelScope.launch {
            try {
                val configs = withContext(Dispatchers.IO) {
                    packageManager.getAllPackageInfo().map { packageInfo ->
                        ImportableConfig(
                            appName = packageInfo.name,
                            packageName = packageInfo.packageName,
                            description = packageInfo.description,
                            fileName = packageInfo.fileName,
                            isBuiltIn = packageInfo.isBuiltIn
                        )
                    }
                }
                _uiState.update { it.copy(availableConfigs = configs) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load available configs", e)
            }
        }
    }

    /** 显示导入对话框 */
    fun showImportDialog() {
        _uiState.update { it.copy(showImportDialog = true, importExportMessage = null) }
        loadAvailableConfigs()
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

    /** 从文件导入配置 */
    fun importConfigFromFile(filePath: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isImporting = true, importExportMessage = null) }
                
                val result = withContext(Dispatchers.IO) {
                    packageManager.importPackage(filePath)
                }
                
                if (result.startsWith("Successfully")) {
                    _uiState.update { 
                        it.copy(
                            isImporting = false, 
                            importExportMessage = result,
                            showImportDialog = false
                        )
                    }
                    // 重新加载可用配置
                    loadAvailableConfigs()
                } else {
                    _uiState.update { 
                        it.copy(
                            isImporting = false, 
                            importExportMessage = result
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _uiState.update { 
                    it.copy(
                        isImporting = false, 
                        importExportMessage = "导入失败: ${e.message}"
                    )
                }
            }
        }
    }

    /** 导出当前UI结构为路由配置 */
    fun exportCurrentUIAsConfig(appName: String, packageName: String, description: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isExporting = true, importExportMessage = null) }
                
                val currentElements = _uiState.value.elements
                if (currentElements.isEmpty()) {
                    _uiState.update { 
                        it.copy(
                            isExporting = false, 
                            importExportMessage = "没有UI元素可导出，请先刷新UI"
                        )
                    }
                    return@launch
                }

                val config = withContext(Dispatchers.IO) {
                    generateUIRouteConfig(appName, packageName, description, currentElements)
                }

                // 保存到外部存储
                val fileName = "${packageName.replace(".", "_")}_config.json"
                val configsDir = File(context.getExternalFilesDir(null), "automation_configs")
                if (!configsDir.exists()) {
                    configsDir.mkdirs()
                }
                
                val configFile = File(configsDir, fileName)
                withContext(Dispatchers.IO) {
                    configFile.writeText(json.encodeToString(config))
                }

                _uiState.update { 
                    it.copy(
                        isExporting = false, 
                        importExportMessage = "配置已导出到: ${configFile.absolutePath}",
                        showExportDialog = false
                    )
                }
                
                // 重新加载可用配置
                loadAvailableConfigs()
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _uiState.update { 
                    it.copy(
                        isExporting = false, 
                        importExportMessage = "导出失败: ${e.message}"
                    )
                }
            }
        }
    }

    /** 将UI元素转换为路由配置 */
    private fun generateUIRouteConfig(
        appName: String, 
        packageName: String, 
        description: String, 
        elements: List<UIElement>
    ): JsonUIRouteConfig {
        // 创建主页节点
        val mainNode = JsonUINode(
            name = "${appName}_主页",
            description = "应用主页面",
            activityName = null,
            nodeType = "APP_HOME"
        )

        // 为每个可点击的元素创建一个可能的页面节点
        val clickableElements = elements.filter { it.isClickable && it.text.isNotEmpty() }
        val additionalNodes = clickableElements.map { element ->
            JsonUINode(
                name = "${element.text}_页面",
                description = "通过点击'${element.text}'到达的页面",
                activityName = null,
                nodeType = "DETAIL_PAGE"
            )
        }

        // 创建边（操作）
        val edges = clickableElements.map { element ->
            JsonUIEdge(
                from = "${appName}_主页",
                to = "${element.text}_页面",
                operations = listOf(
                    JsonUIOperation.Click(
                        selector = if (element.resourceId != null) {
                            JsonUISelector(type = "ByResourceId", value = element.resourceId)
                        } else {
                            JsonUISelector(type = "ByText", value = element.text)
                        },
                        description = "点击${element.text}"
                    )
                )
            )
        }

        // 创建一个示例功能
        val sampleFunction = JsonUIFunction(
            name = "导航到页面",
            description = "导航到指定页面",
            targetNodeName = "${appName}_主页",
            operation = JsonUIOperation.Click(
                selector = JsonUISelector(type = "ByText", value = "{{target_text}}"),
                description = "点击目标元素"
            )
        )

        return JsonUIRouteConfig(
            appName = appName,
            packageName = packageName,
            description = description,
            nodes = listOf(mainNode) + additionalNodes,
            edges = edges,
            functions = listOf(sampleFunction)
        )
    }

    /** 清除导入导出消息 */
    fun clearImportExportMessage() {
        _uiState.update { it.copy(importExportMessage = null) }
    }

    /** 刷新UI元素 */
    fun refreshUI() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                // Hide the floating window before capturing UI
                windowInteractionController?.invoke(false)
                delay(300) // Wait for the window to hide

                val elements =
                        withContext(Dispatchers.IO) {
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
                                throw Exception("获取UI信息失败: ${result.error}")
                            }
                        }

                _uiState.update { it.copy(isLoading = false, elements = elements) }
            } catch (e: Exception) {
                Log.e(TAG, "刷新UI元素失败", e)
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "刷新UI元素失败: ${e.message}")
                }
            } finally {
                // Restore the floating window
                windowInteractionController?.invoke(true)
            }
        }
    }

    /** 将SimplifiedUINode转换为UIElement列表 */
    private fun convertToUIElements(
            node: com.ai.assistance.operit.core.tools.SimplifiedUINode
    ): List<UIElement> {
        val elements = mutableListOf<UIElement>()
        processNode(node, elements)
        return elements
    }

    /** 处理节点并添加到列表 */
    private fun processNode(
            node: com.ai.assistance.operit.core.tools.SimplifiedUINode,
            elements: MutableList<UIElement>
    ) {
        val element = createUiElement(node)
        elements.add(element)
        node.children.forEach { childNode -> processNode(childNode, elements) }
    }

    /** 从SimplifiedUINode创建UIElement */
    private fun createUiElement(
            node: com.ai.assistance.operit.core.tools.SimplifiedUINode
    ): UIElement {
        val bounds =
                node.bounds?.let {
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
                        Log.e(TAG, "解析边界失败: $it", e)
                        null
                    }
                }

        return UIElement(
                id = UUID.randomUUID().toString(),
                className = node.className ?: "Unknown",
                resourceId = node.resourceId,
                packageName = null,
                contentDesc = node.contentDesc,
                text = node.text ?: "",
                bounds = bounds,
                isClickable = node.isClickable,
                isVisible = true,
                isCheckable = false,
                isChecked = false,
                isEnabled = true,
                isFocused = false,
                isScrollable = false,
                isLongClickable = false,
                isSelected = false
        )
    }
}
