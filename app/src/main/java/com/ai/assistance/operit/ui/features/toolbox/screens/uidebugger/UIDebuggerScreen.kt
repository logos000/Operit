package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.navigation.NavController
import com.ai.assistance.operit.services.UIDebuggerService
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ai.assistance.operit.core.tools.automatic.UINode
import com.ai.assistance.operit.core.tools.automatic.UIEdgeDefinition
import com.ai.assistance.operit.core.tools.automatic.UIFunction
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.components.EditEdgeDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.components.EditFunctionDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.components.EditNodeDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.components.FunctionDetailsDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.components.ImportExportDialog
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.components.OperationDetailsDialog
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIDebuggerScreen(
    navController: NavController,
    viewModel: UIDebuggerViewModel = UIDebuggerViewModel.getInstance()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importPackageFromFile(it.toString()) }
    }

    // 文件创建器（用于导出）
    val fileCreatorLauncher = rememberLauncherForActivityResult(
        contract = object : CreateDocument("application/json") {
            override fun createIntent(context: Context, input: String): Intent {
                val intent = super.createIntent(context, input)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val operitDir = File(downloadsDir, "Operit")
                if (!operitDir.exists()) {
                    operitDir.mkdirs()
                }
                // 此API级别 26+
                val operitDirUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:" + Environment.DIRECTORY_DOWNLOADS + "/Operit"
                )
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, operitDirUri)
                return intent
            }
        }
    ) { uri: Uri? ->
        uri?.let {
            uiState.selectedPackageForExport?.let { packageInfo ->
                viewModel.exportPackageToFile(packageInfo, it.toString())
            }
        }
    }

    // 当需要显示导入导出对话框时
    if (uiState.showImportExportDialog) {
        ImportExportDialog(
            mode = uiState.importExportMode,
            builtInPackages = uiState.builtInPackages,
            externalPackages = uiState.externalPackages,
            isLoading = uiState.isLoadingPackages,
            onModeChange = { viewModel.setImportExportMode(it) },
            onPackageSelected = {
                viewModel.selectPackage(it)
                viewModel.toggleImportExportDialog() // 选择后关闭对话框
            },
            onImportFromFile = {
                filePickerLauncher.launch("application/json")
            },
            onExportPackage = { packageInfo ->
                // 准备导出，先将包信息存起来
                viewModel.prepareExport(packageInfo)
                // 建议的文件名
                val suggestedFileName = "${packageInfo.name}.json"
                fileCreatorLauncher.launch(suggestedFileName)
            },
            onDismiss = { viewModel.toggleImportExportDialog() },
            onRefresh = { viewModel.loadAvailablePackages() }
        )
    }

    // 当需要显示操作详情对话框时
    if (uiState.showOperationDetailsDialog && uiState.selectedEdgeForDetails != null && uiState.selectedFromNodeForDetails != null) {
        OperationDetailsDialog(
            edge = uiState.selectedEdgeForDetails!!,
            fromNodeName = uiState.selectedFromNodeForDetails!!,
            onDismiss = { viewModel.hideOperationDetails() }
        )
    }

    // 当需要显示功能详情对话框时
    if (uiState.showFunctionDetailsDialog && uiState.selectedFunctionForDetails != null) {
        FunctionDetailsDialog(
            function = uiState.selectedFunctionForDetails!!,
            onDismiss = { viewModel.hideFunctionDetails() }
        )
    }

    // 当需要显示创建包对话框时
    if (uiState.showCreatePackageDialog) {
        CreatePackageDialog(
            onCreatePackage = { appName, packageName, description ->
                viewModel.createNewPackage(appName, packageName, description)
            },
            onDismiss = { viewModel.toggleCreatePackageDialog() }
        )
    }

    // 当需要显示编辑对话框时
    if (uiState.showEditDialog) {
        when (uiState.editTarget) {
            EditTarget.NODE -> {
                EditNodeDialog(
                    node = uiState.editingNode,
                    isEditing = uiState.editMode == EditMode.EDIT,
                    onSave = { name, activityName, nodeType, matchCriteria ->
                        viewModel.saveNode(name, activityName, nodeType, matchCriteria)
                    },
                    onDismiss = { viewModel.hideEditDialog() }
                )
            }
            EditTarget.EDGE -> {
                uiState.editingEdge?.let { editingEdge ->
                    EditEdgeDialog(
                        editingEdge = editingEdge,
                        packageNodes = uiState.packageNodes,
                        onSave = { edge ->
                            viewModel.saveEdge(edge)
                        },
                        onDismiss = { viewModel.hideEditDialog() }
                    )
                }
            }
            EditTarget.FUNCTION -> {
                EditFunctionDialog(
                    function = uiState.editingFunction,
                    packageNodes = uiState.packageNodes,
                    onSave = { name, description, targetNodeName, operation ->
                        viewModel.saveFunction(name, description, targetNodeName, operation)
                    },
                    onDismiss = { viewModel.hideEditDialog() }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Scaffold(
        floatingActionButton = {
            CollapsibleFloatingMenu(
                onImportExportClick = { viewModel.toggleImportExportDialog() },
                onCreatePackageClick = { viewModel.toggleCreatePackageDialog() },
                onLaunchFloatingClick = {
                    if (Settings.canDrawOverlays(context)) {
                        val intent = Intent(context, UIDebuggerService::class.java)
                        context.startService(intent)
                    } else {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (uiState.selectedPackage == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("请点击右上角的列表按钮选择一个配置包来查看节点。")
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 标题和包信息
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            uiState.selectedPackage!!.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            uiState.selectedPackage!!.packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // 当前选中节点显示
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.selectedNodeName != null) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.errorContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = null,
                                    tint = if (uiState.selectedNodeName != null) 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "当前选中节点:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (uiState.selectedNodeName != null) 
                                            MaterialTheme.colorScheme.onPrimaryContainer 
                                        else 
                                            MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = uiState.selectedNodeName ?: "未选择节点",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.selectedNodeName != null) 
                                            MaterialTheme.colorScheme.onPrimaryContainer 
                                        else 
                                            MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    // 视图模式切换
                    ViewModeSwitcher(
                        currentMode = uiState.currentViewMode,
                        onModeChange = { viewModel.setViewMode(it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 根据视图模式显示内容
                    when (uiState.currentViewMode) {
                        UIDebuggerViewMode.NODES -> NodesView(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNodeClick = { viewModel.selectNode(it.name) },
                            onEdgeClick = { viewModel.selectNode(it.toNodeName) },
                            onEditNode = { viewModel.startEditNode(it) },
                            onDeleteNode = { viewModel.deleteNode(it.name) },
                            onAddNode = { viewModel.startEditNode() },
                            onEditEdge = { fromNodeName, edge -> viewModel.startEditEdge(fromNodeName, edge) },
                            onDeleteEdge = { fromNodeName, edge -> viewModel.deleteEdge(fromNodeName, edge) },
                            onAddEdge = { fromNodeName -> viewModel.startEditEdge(fromNodeName) }
                        )
                        UIDebuggerViewMode.FUNCTIONS -> FunctionsView(
                            uiState = uiState,
                            viewModel = viewModel,
                            onFunctionClick = { viewModel.onFunctionClick(it) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeSwitcher(
    currentMode: UIDebuggerViewMode,
    onModeChange: (UIDebuggerViewMode) -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        SegmentedButton(
            selected = currentMode == UIDebuggerViewMode.NODES,
            onClick = { onModeChange(UIDebuggerViewMode.NODES) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) {
            Text("节点")
        }
        SegmentedButton(
            selected = currentMode == UIDebuggerViewMode.FUNCTIONS,
            onClick = { onModeChange(UIDebuggerViewMode.FUNCTIONS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) {
            Text("功能")
        }
    }
}

@Composable
private fun NodesView(
    uiState: UIDebuggerState,
    viewModel: UIDebuggerViewModel,
    onNodeClick: (UINode) -> Unit,
    onEdgeClick: (UIEdgeDefinition) -> Unit,
    onEditNode: (UINode) -> Unit,
    onDeleteNode: (UINode) -> Unit,
    onAddNode: () -> Unit,
    onEditEdge: (String, UIEdgeDefinition) -> Unit,
    onDeleteEdge: (String, UIEdgeDefinition) -> Unit,
    onAddEdge: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoadingPackages) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.packageNodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("此配置包中没有定义节点。")
            }
        } else {
            // 节点横向列表和添加按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.packageNodes) { node ->
                        NodeItemWithActions(
                            node = node,
                            isSelected = node.name == uiState.selectedNodeName,
                            onClick = { onNodeClick(node) },
                            onEdit = { onEditNode(node) },
                            onDelete = { onDeleteNode(node) }
                        )
                    }
                }
                
                // 添加节点按钮
                FloatingActionButton(
                    onClick = onAddNode,
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "添加节点",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 选中节点的边列表
            val selectedNodeEdges = uiState.selectedNodeName?.let {
                uiState.packageConfig?.edgeDefinitions?.get(it)
            } ?: emptyList()

            // 选中节点的功能列表
            val selectedNodeFunctions = uiState.selectedNodeName?.let {
                viewModel.getFunctionsForNode(it)
            } ?: emptyList()

            if (selectedNodeEdges.isEmpty() && selectedNodeFunctions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("该节点没有定义出向边和功能。")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 显示边
                    if (selectedNodeEdges.isNotEmpty() || uiState.selectedNodeName != null) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "出向边 (${selectedNodeEdges.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                
                                // 添加边按钮
                                uiState.selectedNodeName?.let { selectedNodeName ->
                                    IconButton(
                                        onClick = { onAddEdge(selectedNodeName) }
                                    ) {
                                        Icon(
                                            Icons.Default.Add,
                                            contentDescription = "添加边"
                                        )
                                    }
                                }
                            }
                        }
                        
                        items(selectedNodeEdges) { edge ->
                            EdgeItemWithActions(
                                fromNodeName = uiState.selectedNodeName!!,
                                edge = edge,
                                onClick = { onEdgeClick(edge) },
                                onShowDetails = { edgeForDetails, fromNodeForDetails ->
                                    viewModel.showOperationDetails(edgeForDetails, fromNodeForDetails)
                                },
                                onEdit = { onEditEdge(uiState.selectedNodeName!!, edge) },
                                onDelete = { onDeleteEdge(uiState.selectedNodeName!!, edge) }
                            )
                        }
                    }
                    
                    // 显示功能
                    if (selectedNodeFunctions.isNotEmpty()) {
                        item {
                            Text(
                                text = "可用功能 (${selectedNodeFunctions.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(selectedNodeFunctions) { function ->
                            NodeFunctionItem(
                                function = function,
                                onShowDetails = { viewModel.showFunctionDetails(function) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FunctionsView(
    uiState: UIDebuggerState,
    viewModel: UIDebuggerViewModel,
    onFunctionClick: (UIFunction) -> Unit
) {
    val functions = uiState.packageConfig?.functionDefinitions?.values?.toList() ?: emptyList()
    if (functions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("此配置包中没有定义功能。")
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(functions) { function ->
                FunctionItemWithActions(
                    function = function, 
                    onClick = { onFunctionClick(function) },
                    onShowDetails = { viewModel.showFunctionDetails(function) },
                    onEdit = { viewModel.startEditFunction(function) },
                    onDelete = { viewModel.deleteFunction(function.name) }
                )
            }
        }
    }
}

@Composable
private fun CollapsibleFloatingMenu(
    onImportExportClick: () -> Unit,
    onCreatePackageClick: () -> Unit,
    onLaunchFloatingClick: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End
    ) {
        // 展开时显示的菜单项
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // 导入导出按钮
                ExtendedFloatingActionButton(
                    onClick = {
                        onImportExportClick()
                        isExpanded = false
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.animateContentSize()
                ) {
                    Icon(
                        Icons.Default.List,
                        contentDescription = "导入导出"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入导出")
                }
                
                // 创建配置包按钮
                ExtendedFloatingActionButton(
                    onClick = {
                        onCreatePackageClick()
                        isExpanded = false
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.animateContentSize()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "创建配置包"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("创建配置包")
                }
                
                // 启动悬浮窗按钮
                ExtendedFloatingActionButton(
                    onClick = {
                        onLaunchFloatingClick()
                        isExpanded = false
                    },
                    modifier = Modifier.animateContentSize()
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = "启动悬浮窗")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("启动悬浮窗")
                }
            }
        }
        
        // 主菜单按钮
        FloatingActionButton(
            onClick = { isExpanded = !isExpanded },
            containerColor = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                if (isExpanded) Icons.Default.Close else Icons.Default.Menu,
                contentDescription = if (isExpanded) "关闭菜单" else "打开菜单",
                modifier = Modifier.rotate(if (isExpanded) 0f else 0f)
            )
        }
    }
} 