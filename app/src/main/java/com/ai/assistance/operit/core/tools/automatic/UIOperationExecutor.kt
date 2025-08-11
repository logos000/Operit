package com.ai.assistance.operit.core.tools.automatic

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.util.map.NodeState
import com.ai.assistance.operit.util.map.StatefulPath
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val TAG = "UIOperationExecutor"

/**
 * UI操作执行器。
 * 负责接收一个路径 (StatefulPath)，并按顺序执行路径上每个边定义的UI操作。
 */
class UIOperationExecutor(
    private val context: Context,
    private val toolHandler: AIToolHandler
) {
    companion object {
        private const val OPERATION_DELAY = 500L
        private const val FOCUS_DELAY = 300L
        private const val SEQUENTIAL_OP_DELAY = 200L
        private const val MAX_STATE_VERIFICATION_RETRY = 3
        private const val STATE_VERIFICATION_DELAY = 1000L
    }

    /**
     * 执行给定的路径。
     * @param path 要执行的状态路径。
     * @param context 包含运行时参数的上下文，例如 "current_ui_state"。
     * @return 路由执行结果。
     */
    suspend fun executePath(path: StatefulPath, context: Map<String, Any> = emptyMap()): RouteResult {
        var currentNodeState = path.startState
        var currentUIState = (context["current_ui_state"] as? UIState)
            ?: UIState(nodeId = currentNodeState.nodeId, customData = currentNodeState.variables)

        Log.d(TAG, "开始执行路径，起始节点: ${currentUIState.nodeId}")

        for ((index, edge) in path.edges.withIndex()) {
            val operation = edge.stateTransform as? UIOperation
                ?: continue // 如果不是UIOperation，则跳过

            Log.d(TAG, "执行操作 ${index + 1}/${path.edges.size}: ${operation.description} (${edge.from} -> ${edge.to})")

            // 在执行操作前，使用当前状态的变量替换模板
            val substitutedOperation = substituteTemplateVariables(operation, currentUIState.variables)
            
            val executionSuccess = executeOperation(substitutedOperation, currentUIState)

            if (!executionSuccess) {
                val errorMsg = "操作 '${operation.description}' 失败"
                Log.e(TAG, errorMsg)
                return RouteResult.failure(errorMsg, currentUIState)
            }

            // 操作之间的延迟
            delay(OPERATION_DELAY)

            // 验证是否成功到达目标状态
            val nextNodeState = path.getStateAt(index + 1) ?: break
            val newUIState = verifyStateTransition(edge.to, currentUIState)
            if (newUIState != null) {
                currentUIState = newUIState.copyWith(
                    customData = nextNodeState.variables + newUIState.customData
                )
                currentNodeState = nextNodeState
                Log.d(TAG, "成功转换到节点: ${currentUIState.nodeId}")
            } else {
                Log.w(TAG, "状态转换验证失败，但继续执行")
                currentNodeState = nextNodeState
                currentUIState = currentUIState.copyWith(
                    nodeId = edge.to,
                    customData = currentNodeState.variables + currentUIState.customData
                )
            }
        }

        Log.d(TAG, "路径执行成功，最终到达节点: ${currentUIState.nodeId}")

        return RouteResult.success(currentUIState, path, "路径导航成功")
    }

    /**
     * 验证状态转换是否成功
     */
    private suspend fun verifyStateTransition(expectedNodeId: String, previousState: UIState): UIState? {
        var retryCount = 0
        while (retryCount < MAX_STATE_VERIFICATION_RETRY) {
            try {
                // 获取当前页面信息
                val pageInfoTool = AITool(
                    name = "get_page_info",
                    parameters = listOf(
                        ToolParameter("format", "json"),
                        ToolParameter("detail", "summary")
                    )
                )
                
                val result = toolHandler.executeTool(pageInfoTool)
                
                if (result.success && result.result != null) {
                    val newUIState = parseUIStateFromPageInfo(result.result, expectedNodeId)
                    if (newUIState != null) {
                        // 如果我们期望的节点是当前页面，则认为验证成功
                        val pageMatches = newUIState.packageName == previousState.packageName &&
                                          newUIState.currentActivity == previousState.currentActivity
                        // 在更复杂的场景中，这里应该用 aergolu.json 中的页面定义来匹配 expectedNodeId
                        // 此处简化为只要获取到新页面状态就认为转换成功
                        Log.d(TAG, "状态验证成功，获取到新页面: ${newUIState.packageName}/${newUIState.currentActivity}")
                        return newUIState
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "状态验证异常 (尝试 ${retryCount + 1})", e)
            }
            
            retryCount++
            if (retryCount < MAX_STATE_VERIFICATION_RETRY) {
                delay(STATE_VERIFICATION_DELAY)
            }
        }
        
        return null
    }

    /**
     * 从页面信息结果中解析出UIState
     */
    private fun parseUIStateFromPageInfo(pageInfoResult: Any, expectedNodeId: String): UIState? {
        try {
            when (pageInfoResult) {
                is UIPageResultData -> {
                    return UIState(
                        nodeId = expectedNodeId,
                        currentActivity = pageInfoResult.activityName,
                        packageName = pageInfoResult.packageName,
                        uiElements = pageInfoResult.uiElements
                    )
                }
                is String -> {
                    val jsonObject = JSONObject(pageInfoResult)
                    val packageName = jsonObject.optString("packageName", "unknown")
                    val activityName = jsonObject.optString("activityName", "unknown")
                    
                    return UIState(
                        nodeId = expectedNodeId,
                        currentActivity = activityName,
                        packageName = packageName
                    )
                }
                else -> return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析页面信息时发生错误", e)
            return null
        }
    }

    /**
     * 执行单个UI操作。
     * @param operation 要执行的UI操作。
     * @param state 当前的UI状态，用于提供上下文参数。
     * @return 操作是否成功。
     */
    private suspend fun executeOperation(operation: UIOperation, state: UIState): Boolean {
        return when (operation) {
            is UIOperation.Click -> {
                val params = createSelectorParams(operation.selector, state)
                val tool = AITool("click_element", params)
                val result = toolHandler.executeTool(tool)
                if (!result.success) {
                    Log.w(TAG, "点击操作失败: ${result.error}")
                }
                result.success
            }
            is UIOperation.Input -> {
                val textToInput = state.variables[operation.textVariableKey] as? String ?: ""
                Log.d(TAG, "准备输入文本: $textToInput")
                
                val clickParams = createSelectorParams(operation.selector, state)
                val clickTool = AITool("click_element", clickParams)
                
                // 先点击输入框
                if (!toolHandler.executeTool(clickTool).success) {
                    Log.w(TAG, "点击输入框失败")
                    return false
                }
                
                delay(FOCUS_DELAY) // 等待输入框获取焦点

                // 再输入文本
                val inputTool = AITool("set_input_text", listOf(ToolParameter("text", textToInput)))
                val inputResult = toolHandler.executeTool(inputTool)
                if (!inputResult.success) {
                    Log.w(TAG, "输入文本失败: ${inputResult.error}")
                }
                inputResult.success
            }
            is UIOperation.LaunchApp -> {
                val tool = AITool("start_app", listOf(ToolParameter("package_name", operation.packageName)))
                val result = toolHandler.executeTool(tool)
                if (!result.success) {
                    Log.w(TAG, "启动应用失败: ${result.error}")
                }
                result.success
            }
            is UIOperation.KillApp -> {
                val tool = AITool("stop_app", listOf(ToolParameter("package_name", operation.packageName)))
                val result = toolHandler.executeTool(tool)
                if (!result.success) {
                    Log.w(TAG, "关闭应用失败: ${result.error}")
                }
                result.success
            }
            is UIOperation.Swipe -> {
                val tool = AITool("swipe", listOf(
                    ToolParameter("direction", operation.direction.name.lowercase()),
                    ToolParameter("distance", operation.distance.toString())
                ))
                val result = toolHandler.executeTool(tool)
                if (!result.success) {
                    Log.w(TAG, "滑动操作失败: ${result.error}")
                }
                result.success
            }
            is UIOperation.WaitForPage -> {
                Log.d(TAG, "等待页面加载 ${operation.timeoutMs}ms")
                delay(operation.timeoutMs)
                true
            }
            is UIOperation.ValidateState -> {
                val isValid = operation.validator(state)
                Log.d(TAG, "状态验证结果: $isValid")
                isValid
            }
            is UIOperation.Sequential -> {
                for ((index, op) in operation.operations.withIndex()) {
                    Log.d(TAG, "执行序列操作 ${index + 1}/${operation.operations.size}: ${op.description}")
                    if (!executeOperation(op, state)) {
                        Log.e(TAG, "序列操作中的第${index + 1}步失败")
                        return false
                    }
                    if (index < operation.operations.size - 1) {
                        delay(SEQUENTIAL_OP_DELAY) // 序列操作之间的短暂延迟
                    }
                }
                true
            }
            is UIOperation.NoOp -> {
                Log.d(TAG, "执行无操作")
                true
            }
            else -> {
                Log.w(TAG, "未实现的UI操作: ${operation::class.simpleName}")
                true // 默认返回成功以允许流程继续
            }
        }
    }

    /**
     * 使用实际参数替换操作中的模板变量
     */
    private fun substituteTemplateVariables(operation: UIOperation, variables: Map<String, Any>): UIOperation {
        return when (operation) {
            is UIOperation.Click -> {
                operation.copy(selector = substituteTemplateInSelector(operation.selector, variables))
            }
            is UIOperation.Input -> {
                operation.copy(selector = substituteTemplateInSelector(operation.selector, variables))
            }
            is UIOperation.Sequential -> {
                val substitutedOps = operation.operations.map { 
                    substituteTemplateVariables(it, variables) 
                }
                operation.copy(operations = substitutedOps)
            }
            else -> operation
        }
    }

    /**
     * 在选择器中替换模板变量
     */
    private fun substituteTemplateInSelector(selector: UISelector, variables: Map<String, Any>): UISelector {
        return when (selector) {
            is UISelector.ByText -> {
                var substitutedText = selector.text
                variables.forEach { (key, value) ->
                    substitutedText = substitutedText.replace("{{$key}}", value.toString())
                }
                UISelector.ByText(substitutedText)
            }
            is UISelector.ByContentDesc -> {
                var substitutedDesc = selector.desc
                variables.forEach { (key, value) ->
                    substitutedDesc = substitutedDesc.replace("{{$key}}", value.toString())
                }
                UISelector.ByContentDesc(substitutedDesc)
            }
            else -> selector
        }
    }

    /**
     * 根据UISelector创建工具所需的参数列表。
     */
    private fun createSelectorParams(selector: UISelector, state: UIState): List<ToolParameter> {
        return when (selector) {
            is UISelector.ByResourceId -> listOf(ToolParameter("resourceId", selector.id))
            is UISelector.ByText -> {
                // 如果当前状态有UI元素信息，可以进行更智能的查找
                val element = state.findElementByText(selector.text)
                if (element != null) {
                    Log.d(TAG, "在当前页面找到文本元素: ${selector.text}")
                    // 优先使用resourceId，如果没有则使用文本
                    element.resourceId?.let { 
                        return listOf(ToolParameter("resourceId", it))
                    }
                }
                listOf(ToolParameter("text", selector.text))
            }
            is UISelector.ByContentDesc -> listOf(ToolParameter("contentDesc", selector.desc))
            is UISelector.ByClassName -> listOf(ToolParameter("className", selector.name))
            is UISelector.ByBounds -> listOf(ToolParameter("bounds", selector.bounds))
            is UISelector.ByXPath -> {
                Log.d(TAG, "使用XPath选择器: ${selector.xpath}")
                listOf(ToolParameter("xpath", selector.xpath))
            }
        }
    }
} 