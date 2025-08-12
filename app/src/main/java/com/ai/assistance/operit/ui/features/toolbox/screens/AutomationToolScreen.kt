package com.ai.assistance.operit.ui.features.toolbox.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.automatic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

private const val TAG = "AutomationToolScreen"

/**
 * UI自动化工具屏幕
 * 提供一键触发UI自动化功能的界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationToolScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toolHandler = AIToolHandler.getInstance(context)
    
    // UI状态
    var isLoading by remember { mutableStateOf(false) }
    var availableFunctions by remember { mutableStateOf<List<UIFunction>>(emptyList()) }
    var executionResult by remember { mutableStateOf<String?>(null) }
    var selectedFunction by remember { mutableStateOf<UIFunction?>(null) }
    var showParameterDialog by remember { mutableStateOf(false) }
    var functionParameters by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    // 初始化UIRouter（这里使用示例配置）
    val uiRouter = remember {
        Log.d(TAG, "Initializing UIRouter.")
        UIRouter(context, toolHandler).apply {
            // 加载示例配置
            Log.d(TAG, "Loading sample config...")
            val sampleConfig = createSampleConfig()
            loadConfig(sampleConfig)
            Log.d(TAG, "Sample config loaded.")

            // 加载并合并其他配置
            Log.d(TAG, "Loading and merging pay app exit config...")
            val payAppExitConfig = UIRouteConfig.loadPayAppExitConfig()
            loadConfig(payAppExitConfig, merge = true)
            Log.d(TAG, "Pay app exit config merged.")
        }
    }

    // 获取可用功能
    LaunchedEffect(uiRouter) {
        if (uiRouter != null) {
            Log.d(TAG, "LaunchedEffect triggered. Getting available functions.")
            availableFunctions = uiRouter.getAvailableFunctions()
            Log.d(TAG, "Available functions updated: ${availableFunctions.size} functions.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "UI自动化工具",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 工具说明卡片
            ToolDescriptionCard()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 功能列表标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "可用功能",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                
                // 刷新按钮
                IconButton(
                    onClick = {
                        scope.launch {
                            Log.d(TAG, "Refresh button clicked.")
                            isLoading = true
                            if (uiRouter != null) {
                                availableFunctions = uiRouter.getAvailableFunctions()
                                Log.d(TAG, "Functions refreshed. Count: ${availableFunctions.size}")
                            } else {
                                Log.w(TAG, "Refresh failed: uiRouter is null.")
                            }
                            isLoading = false
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 功能列表
            if (isLoading) {
                LoadingIndicator()
            } else if (availableFunctions.isEmpty()) {
                EmptyFunctionList()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableFunctions) { function ->
                        FunctionCard(
                            function = function,
                            onExecute = { func ->
                                selectedFunction = func
                                Log.d(TAG, "Executing function: ${func.name}")
                                // 这里可以分析功能是否需要参数
                                val needsParams = analyzeIfNeedsParameters(func)
                                Log.d(TAG, "Function '${func.name}' needs parameters: $needsParams")
                                if (needsParams) {
                                    showParameterDialog = true
                                } else {
                                    // 直接执行
                                    Log.d(TAG, "Executing function directly without parameters.")
                                    scope.launch(Dispatchers.IO) {
                                        executeFunction(uiRouter, func, emptyMap()) { result ->
                                            executionResult = result
                                            Log.d(TAG, "Direct execution finished. Result: $result")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
            
            // 执行结果显示
            executionResult?.let { result ->
                Spacer(modifier = Modifier.height(16.dp))
                ExecutionResultCard(
                    result = result,
                    onDismiss = { executionResult = null }
                )
            }
        }
    }
    
    // 参数输入对话框
    if (showParameterDialog && selectedFunction != null) {
        ParameterInputDialog(
            function = selectedFunction!!,
            parameters = functionParameters,
            onParametersChange = { functionParameters = it },
            onExecute = { func, params ->
                Log.d(TAG, "Executing function '${func.name}' from dialog with params: $params")
                showParameterDialog = false
                scope.launch(Dispatchers.IO) {
                    executeFunction(uiRouter, func, params) { result ->
                        executionResult = result
                        Log.d(TAG, "Execution from dialog finished. Result: $result")
                    }
                }
            },
            onDismiss = { 
                Log.d(TAG, "Parameter input dialog dismissed.")
                showParameterDialog = false
                selectedFunction = null
            }
        )
    }
}

/**
 * 工具说明卡片
 */
@Composable
private fun ToolDescriptionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "UI自动化工具",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "通过预定义的操作流程，自动执行复杂的UI操作序列。支持跨应用导航、参数化操作、状态验证等高级功能。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * 功能卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FunctionCard(
    function: UIFunction,
    onExecute: (UIFunction) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "scale"
    )
    
    Card(
        onClick = { onExecute(function) },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        function.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        function.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 目标页面信息
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "目标页面: ${function.targetNodeName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // 执行按钮
                FilledTonalButton(
                    onClick = { onExecute(function) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "执行",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("执行")
                }
            }
        }
    }
}

/**
 * 加载指示器
 */
@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "加载功能列表中...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 空功能列表提示
 */
@Composable
private fun EmptyFunctionList() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Apps,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "暂无可用功能",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "请检查配置或联系开发者添加功能",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * 参数输入对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterInputDialog(
    function: UIFunction,
    parameters: Map<String, String>,
    onParametersChange: (Map<String, String>) -> Unit,
    onExecute: (UIFunction, Map<String, Any>) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("输入参数 - ${function.name}")
        },
        text = {
            Column {
                Text(
                    "此功能需要以下参数：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 这里应该根据function分析需要的参数
                // 目前使用示例参数
                listOf("target_user", "input_text").forEach { paramKey ->
                    OutlinedTextField(
                        value = parameters[paramKey] ?: "",
                        onValueChange = { newValue ->
                            onParametersChange(parameters + (paramKey to newValue))
                        },
                        label = { Text(paramKey) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onExecute(function, parameters)
                }
            ) {
                Text("执行")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 执行结果卡片
 */
@Composable
private fun ExecutionResultCard(
    result: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (result.contains("成功")) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "执行结果",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                result,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 创建示例配置
 */
private fun createSampleConfig(): UIRouteConfig {
    val config = UIRouteConfig()
    
    // 定义示例节点
    config.defineNode(UINode(
        name = "微信主页",
        packageName = "com.tencent.mm",
        activityName = "LauncherUI",
        nodeType = UINodeType.APP_HOME
    ))
    
    config.defineNode(UINode(
        name = "聊天列表",
        packageName = "com.tencent.mm",
        activityName = "MainTabUI",
        nodeType = UINodeType.LIST_PAGE
    ))
    
    config.defineNode(UINode(
        name = "聊天窗口",
        packageName = "com.tencent.mm",
        activityName = "ChattingUI",
        nodeType = UINodeType.DETAIL_PAGE
    ))
    
    // 定义示例边
    config.defineEdge(
        "微信主页",
        "聊天列表",
        UIOperation.Click(UISelector.ByText("微信"), "打开微信")
    )
    
    config.defineEdge(
        "聊天列表", 
        "聊天窗口",
        UIOperation.Click(UISelector.ByText("{{target_user}}"), "打开聊天")
    )
    
    // 定义示例功能
    config.defineFunction(UIFunction(
        name = "发送微信消息",
        description = "自动导航到指定联系人并发送消息",
        targetNodeName = "聊天窗口",
        operation = UIOperation.Sequential(
            listOf(
                UIOperation.Input(
                    UISelector.ByClassName("android.widget.EditText"),
                    "input_text",
                    "输入消息内容"
                ),
                UIOperation.Click(UISelector.ByText("发送"), "点击发送按钮")
            ),
            "发送消息操作序列"
        )
    ))
    
    return config
}

/**
 * 分析功能是否需要参数
 */
private fun analyzeIfNeedsParameters(function: UIFunction): Boolean {
    // 简单的参数检测逻辑
    val operationText = function.operation.toString()
    return operationText.contains("{{") || operationText.contains("textVariableKey")
}

/**
 * 执行功能
 */
private suspend fun executeFunction(
    uiRouter: UIRouter?,
    function: UIFunction,
    parameters: Map<String, Any>,
    onResult: (String) -> Unit
) {
    Log.d(TAG, "executeFunction called for '${function.name}' with params: $parameters")
    try {
        if (uiRouter == null) {
            val errorMsg = "错误：UIRouter未初始化"
            Log.e(TAG, errorMsg)
            onResult(errorMsg)
            return
        }

        val plan = uiRouter.planFunction(function.name, parameters)
        if (plan == null) {
            val errorMsg = "错误：无法创建执行计划 for ${function.name}"
            Log.e(TAG, errorMsg)
            onResult(errorMsg)
            return
        }
        
        Log.d(TAG, "Execution plan created for '${function.name}'. Executing now...")
        val result = plan.execute(parameters)
        if (result.success) {
            val successMsg = "执行成功：${function.name}"
            Log.i(TAG, successMsg)
            onResult(successMsg)
        } else {
            val errorMsg = "执行失败：${result.error ?: "未知错误"}"
            Log.e(TAG, errorMsg)
            onResult(errorMsg)
        }
    } catch (e: Exception) {
        val errorMsg = "执行异常：${e.message}"
        Log.e(TAG, errorMsg, e)
        onResult(errorMsg)
    }
} 