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
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/** UI调试工具的ViewModel，负责处理与AITool的交互 */
class UIDebuggerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UIDebuggerState())
    val uiState: StateFlow<UIDebuggerState> = _uiState.asStateFlow()

    private lateinit var toolHandler: AIToolHandler
    private var statusBarHeight: Int = 0
    private val TAG = "UIDebuggerViewModel"
    private var windowInteractionController: ((Boolean) -> Unit)? = null

    /**
     * 设置窗口交互控制器
     * @param controller 一个函数，输入Boolean值，用于控制窗口的交互状态
     */
    fun setWindowInteractionController(controller: ((Boolean) -> Unit)?) {
        this.windowInteractionController = controller
    }

    /** 初始化ViewModel */
    fun initialize(context: Context) {
        toolHandler = AIToolHandler.getInstance(context)
        // 获取状态栏高度
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }
        // No initial refresh, refresh is triggered by the button
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
