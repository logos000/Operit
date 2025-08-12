package com.ai.assistance.operit.core.tools.automatic

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * 自动化工具集，提供UI自动化相关的AI工具
 */
class AutomationTools(
    private val context: Context,
    private val toolHandler: AIToolHandler
) {
    companion object {
        private const val TAG = "AutomationTools"
    }

    private val packageManager by lazy { AutomationPackageManager.getInstance(context) }
    private val uiRouter by lazy { UIRouter(context, toolHandler) }
    
    // 缓存当前的执行计划
    private var currentPlan: RoutePlan? = null
    private var currentFunctionName: String? = null

    /**
     * 搜索是否存在对应包名/应用名称的自动化配置
     */
    suspend fun searchAutomationConfig(tool: AITool): ToolResult {
        Log.d(TAG, "Searching automation config")
        
        val packageName = tool.parameters.find { it.name == "package_name" }?.value
        val appName = tool.parameters.find { it.name == "app_name" }?.value
        
        if (packageName.isNullOrBlank() && appName.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供 package_name 或 app_name 参数"
            )
        }

        try {
            val allPackages = packageManager.getAllPackageInfo()
            val matchingPackages = mutableListOf<AutomationConfigSearchResult.ConfigInfo>()

            // 按包名搜索
            if (!packageName.isNullOrBlank()) {
                val packageMatches = allPackages.filter { 
                    it.packageName.contains(packageName, ignoreCase = true) 
                }
                packageMatches.forEach { packageInfo ->
                    matchingPackages.add(
                        AutomationConfigSearchResult.ConfigInfo(
                            appName = packageInfo.name,
                            packageName = packageInfo.packageName,
                            description = packageInfo.description,
                            isBuiltIn = packageInfo.isBuiltIn,
                            fileName = packageInfo.fileName,
                            matchType = "packageName"
                        )
                    )
                }
            }

            // 按应用名搜索
            if (!appName.isNullOrBlank()) {
                val nameMatches = allPackages.filter { 
                    it.name.contains(appName, ignoreCase = true) 
                }
                nameMatches.forEach { packageInfo ->
                    // 避免重复添加
                    if (matchingPackages.none { it.packageName == packageInfo.packageName }) {
                        matchingPackages.add(
                            AutomationConfigSearchResult.ConfigInfo(
                                appName = packageInfo.name,
                                packageName = packageInfo.packageName,
                                description = packageInfo.description,
                                isBuiltIn = packageInfo.isBuiltIn,
                                fileName = packageInfo.fileName,
                                matchType = "appName"
                            )
                        )
                    }
                }
            }

            val searchResult = AutomationConfigSearchResult(
                searchPackageName = packageName,
                searchAppName = appName,
                foundConfigs = matchingPackages,
                totalFound = matchingPackages.size
            )

            Log.d(TAG, "Found ${matchingPackages.size} matching automation configs")
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = searchResult
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error searching automation configs", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "搜索自动化配置时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 获取指定功能的执行计划所需的参数
     */
    suspend fun getPlanParameters(tool: AITool): ToolResult {
        Log.d(TAG, "Getting plan parameters")
        
        val functionName = tool.parameters.find { it.name == "function_name" }?.value
        val packageName = tool.parameters.find { it.name == "package_name" }?.value
        
        if (functionName.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供 function_name 参数"
            )
        }

        try {
            // 如果提供了包名，加载对应的配置
            if (!packageName.isNullOrBlank()) {
                val config = packageManager.getConfigByAppPackageName(packageName)
                if (config != null) {
                    uiRouter.loadConfig(config, merge = true)
                    Log.d(TAG, "Loaded config for package: $packageName")
                } else {
                    Log.w(TAG, "No config found for package: $packageName")
                }
            }

            // 规划功能执行路径
            val plan = uiRouter.planFunction(functionName)
            if (plan == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "无法为功能 '$functionName' 生成执行计划。请检查功能是否存在，或当前应用是否支持该功能。"
                )
            }

            // 缓存计划以供后续执行使用
            currentPlan = plan
            currentFunctionName = functionName

            val parameterInfo = AutomationPlanParametersResult(
                functionName = functionName,
                targetPackageName = packageName,
                requiredParameters = plan.requiredParameters.map { param ->
                    AutomationPlanParametersResult.ParameterInfo(
                        key = param.key,
                        description = param.description,
                        type = param.type.simpleName,
                        isRequired = param.isRequired,
                        defaultValue = param.defaultValue?.toString()
                    )
                },
                planSteps = plan.path.edges.size,
                planDescription = "执行 '$functionName' 功能需要 ${plan.path.edges.size} 个步骤"
            )

            Log.d(TAG, "Generated plan for '$functionName' with ${plan.requiredParameters.size} required parameters")
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = parameterInfo
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting plan parameters", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "获取执行计划参数时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 根据提供的参数执行自动化计划
     */
    suspend fun executePlan(tool: AITool): ToolResult {
        Log.d(TAG, "Executing automation plan")
        
        val plan = currentPlan
        val functionName = currentFunctionName
        
        if (plan == null || functionName == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "没有可执行的计划。请先调用 get_plan_parameters 生成执行计划。"
            )
        }

        try {
            // 从工具参数中提取执行参数
            val parametersJson = tool.parameters.find { it.name == "parameters" }?.value
            if (parametersJson.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "必须提供 parameters 参数（JSON格式）"
                )
            }

            val json = Json { ignoreUnknownKeys = true }
            val providedParameters = try {
                json.decodeFromString<Map<String, String>>(parametersJson)
            } catch (e: Exception) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "参数格式错误，请提供有效的JSON格式: ${e.message}"
                )
            }

            // 检查是否提供了所有必需的参数
            if (!plan.areParametersMet(providedParameters)) {
                val missingParams = plan.requiredParameters
                    .filterNot { providedParameters.containsKey(it.key) }
                    .joinToString(", ") { it.key }
                
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "缺少必需的参数: $missingParams"
                )
            }

            // 执行计划
            val result = plan.execute(providedParameters)
            
            val executionResult = AutomationExecutionResult(
                functionName = functionName,
                providedParameters = providedParameters,
                executionSuccess = result.success,
                executionMessage = result.message,
                executionError = result.error,
                finalState = result.finalState?.let { state ->
                    AutomationExecutionResult.UIStateInfo(
                        nodeId = state.nodeId,
                        packageName = state.packageName ?: "",
                        activityName = state.currentActivity ?: ""
                    )
                },
                executionSteps = result.path?.edges?.size ?: 0
            )

            // 清除缓存的计划
            currentPlan = null
            currentFunctionName = null

            Log.d(TAG, "Plan execution completed. Success: ${result.success}")
            return ToolResult(
                toolName = tool.name,
                success = result.success,
                result = executionResult,
                error = if (result.success) null else result.error
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing plan", e)
            // 清除缓存的计划
            currentPlan = null
            currentFunctionName = null
            
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "执行自动化计划时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 获取可用的自动化功能列表
     */
    suspend fun getAvailableFunctions(tool: AITool): ToolResult {
        Log.d(TAG, "Getting available automation functions")
        
        try {
            val packageName = tool.parameters.find { it.name == "package_name" }?.value
            
            // 如果提供了包名，加载对应的配置
            if (!packageName.isNullOrBlank()) {
                val config = packageManager.getConfigByAppPackageName(packageName)
                if (config != null) {
                    uiRouter.loadConfig(config, merge = true)
                    Log.d(TAG, "Loaded config for package: $packageName")
                }
            }

            val functions = uiRouter.getAvailableFunctions()
            val functionList = AutomationFunctionListResult(
                packageName = packageName,
                functions = functions.map { func ->
                    AutomationFunctionListResult.FunctionInfo(
                        name = func.name,
                        description = func.description,
                        targetNodeName = func.targetNodeName
                    )
                },
                totalCount = functions.size
            )

            Log.d(TAG, "Found ${functions.size} available automation functions")
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = functionList
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available functions", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "获取可用功能列表时发生错误: ${e.message}"
            )
        }
    }
} 