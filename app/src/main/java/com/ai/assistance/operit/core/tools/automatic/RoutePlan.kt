package com.ai.assistance.operit.core.tools.automatic

import com.ai.assistance.operit.util.map.StatefulPath

/**
 * UI自动化路由的执行计划。
 * 封装了从起点到终点的完整路径、所需的参数以及执行逻辑。
 *
 * @property path 规划好的状态路径 (StatefulPath)。
 * @property requiredParameters 执行此路径所需的所有参数的列表。
 * @property executor 用于实际执行UI操作的执行器。
 */
class RoutePlan(
    val path: StatefulPath,
    val requiredParameters: List<RouteParameter<*>>,
    private val executor: UIOperationExecutor
) {
    /**
     * 检查当前是否已满足所有执行所需的参数。
     * @param providedParameters 用户或系统当前已提供的参数。
     * @return 如果所有必需参数都已提供，则返回true。
     */
    fun areParametersMet(providedParameters: Map<String, Any>): Boolean {
        return requiredParameters.all { param -> providedParameters.containsKey(param.key) }
    }

    /**
     * 执行路由计划。
     * 在调用此方法之前，应确保所有必需的参数都已提供。
     *
     * @param providedParameters 包含所有必需参数值的Map。
     * @return 返回一个封装了执行结果的 [RouteResult]。
     * @throws IllegalStateException 如果必需的参数未提供。
     */
    suspend fun execute(providedParameters: Map<String, Any>): RouteResult {
        if (!areParametersMet(providedParameters)) {
            val missingParams = requiredParameters.filterNot { providedParameters.containsKey(it.key) }
            throw IllegalStateException("执行失败：缺少必要的参数: ${missingParams.joinToString { it.key }}")
        }

        // 将提供的参数与路径中的状态进行合并，更新上下文
        val initialNodeState = path.startState.withVariables(providedParameters)
        val updatedPath = path.withNewStartState(initialNodeState)

        // 使用执行器执行更新后的路径
        return executor.executePath(updatedPath, providedParameters)
    }

    /**
     * 一个便捷的属性，用于以Map的形式获取所有需要的参数及其描述。
     * Key是参数的key，Value是参数的描述。
     * 这可以用于向用户展示需要输入哪些信息。
     */
    val requiredInputs: Map<String, String> by lazy {
        requiredParameters.associate { it.key to it.description }
    }
}

/**
 * 定义路由执行所需的参数。
 *
 * @param T 参数的类型。
 * @property key 参数的唯一标识符，例如 "target_user", "message_content"。
 * @property description 对该参数的人类可读描述，用于提示用户。
 * @property type 参数的类型 (如 String::class, Int::class)。
 * @property isRequired 是否为必需参数。
 * @property defaultValue 如果参数非必需，可以提供一个默认值。
 */
data class RouteParameter<T>(
    val key: String,
    val description: String,
    val type: Class<T>,
    val isRequired: Boolean = true,
    val defaultValue: T? = null
)

/**
 * 封装路由执行的结果。
 *
 * @property success 执行是否成功。
 * @property finalState 执行完成后的最终UI状态。
 * @property message 描述执行结果的消息。
 * @property error 如果执行失败，包含错误信息。
 * @property path 如果执行成功，包含实际执行的路径。
 */
data class RouteResult(
    val success: Boolean,
    val finalState: UIState?,
    val message: String,
    val error: String? = null,
    val path: StatefulPath? = null
) {
    companion object {
        fun success(finalState: UIState, path: StatefulPath, message: String) = RouteResult(
            success = true,
            finalState = finalState,
            message = message,
            path = path
        )

        fun failure(error: String, finalState: UIState? = null) = RouteResult(
            success = false,
            finalState = finalState,
            message = "路由执行失败",
            error = error
        )
    }
} 