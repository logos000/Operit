package com.ai.assistance.operit.ui.features.packages.dialogs

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.core.tools.automatic.UIFunction
import com.ai.assistance.operit.core.tools.automatic.UIRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.core.tools.automatic.UIRouteConfig
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationFunctionExecutionDialog(
    function: UIFunction,
    uiRouter: UIRouter?,
    packageManager: AutomationPackageManager,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var parameterValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isExecuting by remember { mutableStateOf(false) }
    var executionResults by remember { mutableStateOf<List<ExecutionResult>>(emptyList()) }

    val needsParameters = remember(function) {
        analyzeIfNeedsParameters(function)
    }

    LaunchedEffect(function) {
        if (needsParameters) {
            val paramKeys = extractParameterKeys(function)
            parameterValues = paramKeys.associateWith { "" }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                // 紧凑的标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "执行自动化功能",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                ) {
                    // 功能信息卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = function.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = function.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Navigation,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "目标: ${function.targetNodeName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // 参数输入区域
                    if (needsParameters && parameterValues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "参数配置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        parameterValues.keys.forEach { paramKey ->
                            OutlinedTextField(
                                value = parameterValues[paramKey] ?: "",
                                onValueChange = { newValue ->
                                    parameterValues = parameterValues + (paramKey to newValue)
                                },
                                label = { Text(paramKey) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // 执行结果
                    if (executionResults.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "执行结果",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(executionResults) { result ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (result.success) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (result.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (result.success) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = result.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("取消")
                    }

                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                executeAutomationFunction(
                                    uiRouter = uiRouter,
                                    packageManager = packageManager,
                                    function = function,
                                    parameters = parameterValues,
                                    onExecuting = { isExecuting = it },
                                    onResult = { result ->
                                        executionResults = executionResults + result
                                    }
                                )
                            }
                        },
                        enabled = !isExecuting
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("执行中")
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("执行")
                        }
                    }
                }
            }
        }
    }
}

// Data class for execution results
data class ExecutionResult(
    val success: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

// Helper functions
private fun analyzeIfNeedsParameters(function: UIFunction): Boolean {
    val operationText = function.operation.toString()
    return operationText.contains("{{") || operationText.contains("textVariableKey")
}

private fun extractParameterKeys(function: UIFunction): List<String> {
    val operationText = function.operation.toString()
    val parameterPattern = "\\{\\{([^}]+)\\}\\}".toRegex()
    return parameterPattern.findAll(operationText)
        .map { it.groupValues[1] }
        .distinct()
        .toList()
}

private suspend fun executeAutomationFunction(
    uiRouter: UIRouter?,
    packageManager: AutomationPackageManager,
    function: UIFunction,
    parameters: Map<String, String>,
    onExecuting: (Boolean) -> Unit,
    onResult: (ExecutionResult) -> Unit
) {
    Log.d("AutomationExecutionDialog", "Executing function: ${function.name} with params: $parameters")
    
    withContext(Dispatchers.Main) {
        onExecuting(true)
    }

    try {
        withContext(Dispatchers.IO) {
            if (uiRouter == null) {
                onResult(ExecutionResult(false, "错误：UIRouter未初始化"))
                return@withContext
            }

            val allPackages = packageManager.getAllPackageInfo()
            var foundConfig: UIRouteConfig? = null
            
            for (packageInfo in allPackages) {
                val config = packageManager.getConfigByAppPackageName(packageInfo.packageName)
                if (config?.functionDefinitions?.containsKey(function.name) == true) {
                    foundConfig = config
                    Log.d("AutomationExecutionDialog", "Found function '${function.name}' in package: ${packageInfo.name}")
                    break
                }
            }
            
            if (foundConfig == null) {
                onResult(ExecutionResult(false, "错误：无法找到包含功能 '${function.name}' 的配置"))
                return@withContext
            }
            
            Log.d("AutomationExecutionDialog", "Loading config into UIRouter for function: ${function.name}")
            uiRouter.loadConfig(foundConfig, merge = false)
            
            val availableFunctions = uiRouter.getAvailableFunctions()
            if (availableFunctions.none { it.name == function.name }) {
                onResult(ExecutionResult(false, "错误：功能 '${function.name}' 加载失败"))
                return@withContext
            }
            
            Log.d("AutomationExecutionDialog", "Function '${function.name}' successfully loaded. Planning execution...")

            val plan = uiRouter.planFunction(function.name, parameters)
            if (plan == null) {
                onResult(ExecutionResult(false, "错误：无法创建执行计划"))
                return@withContext
            }

            Log.d("AutomationExecutionDialog", "Execution plan created. Executing...")
            val result = plan.execute(parameters)
            
            if (result.success) {
                onResult(ExecutionResult(true, "执行成功：${function.name}"))
                Log.i("AutomationExecutionDialog", "Function executed successfully")
            } else {
                onResult(ExecutionResult(false, "执行失败：${result.error ?: "未知错误"}"))
                Log.e("AutomationExecutionDialog", "Function execution failed: ${result.error}")
            }
        }
    } catch (e: Exception) {
        onResult(ExecutionResult(false, "执行异常：${e.message}"))
        Log.e("AutomationExecutionDialog", "Exception during execution", e)
    } finally {
        withContext(Dispatchers.Main) {
            onExecuting(false)
        }
    }
} 