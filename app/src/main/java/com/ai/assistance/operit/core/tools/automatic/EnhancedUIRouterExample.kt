package com.ai.assistance.operit.core.tools.automatic

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 这是一个展示如何使用增强后的 `UIRouter` 系统来完成一个端到端任务的示例。
 *
 * 演示流程：
 * 1. 初始化 `UIRouter`。
 * 2. 加载一个应用的路由配置（例如微信）。
 * 3. 从路由器获取所有可用的“功能”列表。
 * 4. 选择一个目标功能，例如“发送微信消息”。
 * 5. 让路由器为这个功能规划一个完整的执行计划 (`RoutePlan`)。
 * 6. 检查计划，看看执行它需要哪些参数（例如：聊天对象、消息内容）。
 * 7. 提供这些参数。
 * 8. 执行计划，并打印结果。
 */
class EnhancedUIRouterExample(
    private val context: Context,
    private val toolHandler: AIToolHandler,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val uiRouter = UIRouter(context, toolHandler)

    companion object {
        private const val TAG = "UIRouterExample"
    }

    fun runFullDemo() {
        scope.launch {
            // 1. 加载配置
            Log.d(TAG, "正在加载微信路由配置...")
            val wechatConfig = UIRouteConfig.loadWeChatConfig()
            uiRouter.loadConfig(wechatConfig)
            Log.d(TAG, "配置加载完成。")

            // 2. 获取并展示可用功能
            val availableFunctions = uiRouter.getAvailableFunctions()
            Log.d(TAG, "当前可用的功能 (${availableFunctions.size}个):")
            availableFunctions.forEach { function ->
                Log.d(TAG, "  - ${function.name}: ${function.description}")
            }

            // 3. 选择一个功能进行规划
            val targetFunctionName = "发送微信消息"
            Log.d(TAG, "\n选择功能: '$targetFunctionName'")
            Log.d(TAG, "正在为功能规划路径...")

            // 4. 规划功能
            // UIRouter会自己搞清楚当前在哪，然后规划出“导航到聊天页 -> 执行发送”的完整计划
            val plan = uiRouter.planFunction(targetFunctionName)

            if (plan == null) {
                Log.e(TAG, "无法为功能 '$targetFunctionName' 创建执行计划。请检查日志。")
                return@launch
            }

            Log.d(TAG, "规划成功！")

            // 5. 检查并提供所需参数
            val neededParams = plan.requiredInputs
            Log.d(TAG, "执行此计划需要以下参数:")
            neededParams.forEach { (key, description) ->
                Log.d(TAG, "  - 参数名: '$key', 描述: '$description'")
            }

            // 6. 模拟用户提供参数
            val userProvidedParams = mapOf(
                "target_user" to "AI助手小欧", // 这个参数用于导航阶段
                "input_text" to "你好，这是来自自动化路由系统的问候！" // 这个参数用于功能执行阶段
            )
            Log.d(TAG, "\n已提供参数: $userProvidedParams")

            // 7. 执行计划
            if (plan.areParametersMet(userProvidedParams)) {
                Log.d(TAG, "参数齐全，开始执行计划...")
                val result = plan.execute(userProvidedParams)

                // 8. 处理结果
                if (result.success) {
                    Log.i(TAG, "任务执行成功！最终消息: ${result.message}")
                    Log.d(TAG, "最终所在页面状态: ${result.finalState?.nodeId}")
                } else {
                    Log.e(TAG, "任务执行失败！错误: ${result.error}")
                }
            } else {
                val missing = plan.requiredParameters.filterNot { userProvidedParams.containsKey(it.key) }
                Log.e(TAG, "参数不匹配，无法执行。缺少的参数: ${missing.joinToString { it.key }}")
            }
        }
    }
} 