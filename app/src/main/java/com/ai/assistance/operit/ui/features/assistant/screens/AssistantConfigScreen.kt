package com.ai.assistance.operit.ui.features.assistant.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dragonbones.rememberDragonBonesController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.*
import com.ai.assistance.operit.ui.features.assistant.components.DragonBonesConfigSection
import com.ai.assistance.operit.ui.features.assistant.components.DragonBonesPreviewSection
import com.ai.assistance.operit.ui.features.assistant.components.HowToImportSection
import com.ai.assistance.operit.ui.features.assistant.components.SettingItem
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import com.ai.assistance.operit.ui.features.settings.screens.getFunctionDisplayName
import kotlinx.coroutines.launch

/** 助手配置屏幕 提供DragonBones模型预览和相关配置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConfigScreen(
        navigateToModelConfig: () -> Unit,
        navigateToModelPrompts: () -> Unit,
        navigateToFunctionalConfig: () -> Unit,
        navigateToFunctionalPrompts: () -> Unit,
        navigateToUserPreferences: () -> Unit
) {
        val context = LocalContext.current
        val viewModel: AssistantConfigViewModel =
                viewModel(factory = AssistantConfigViewModel.Factory(context))
        val uiState by viewModel.uiState.collectAsState()

        // Preferences Managers
        val functionalPromptManager = remember { FunctionalPromptManager(context) }
        val functionalConfigManager = remember { FunctionalConfigManager(context) }
        val modelConfigManager = remember { ModelConfigManager(context) }
        val promptPreferences = remember { PromptPreferencesManager(context) }

        // 启动文件选择器
        val zipFileLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                                result.data?.data?.let { uri ->
                                        // 导入选择的zip文件
                                        viewModel.importModelFromZip(uri)
                                }
                        }
                }

        // 打开文件选择器的函数
        val openZipFilePicker = {
                val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/zip"
                                putExtra(
                                        Intent.EXTRA_MIME_TYPES,
                                        arrayOf("application/zip", "application/x-zip-compressed")
                                )
                        }
                zipFileLauncher.launch(intent)
        }

        // State for the selected function type
        var selectedFunctionType by remember { mutableStateOf(PromptFunctionType.CHAT) }

        // 已移除：用户偏好入口

        // 根据所选功能获取数据
        val promptProfileId by
                functionalPromptManager
                        .getPromptProfileIdForFunction(selectedFunctionType)
                        .collectAsState(
                                initial =
                                        FunctionalPromptManager.getDefaultProfileIdForFunction(
                                                selectedFunctionType
                                        )
                        )
        val promptProfile by
                promptPreferences
                        .getPromptProfileFlow(promptProfileId)
                        .collectAsState(initial = null)

        val functionType =
                when (selectedFunctionType) {
                        PromptFunctionType.CHAT -> FunctionType.CHAT
                        PromptFunctionType.VOICE -> FunctionType.SUMMARY
                        PromptFunctionType.DESKTOP_PET -> FunctionType.PROBLEM_LIBRARY
                }
        val modelConfigId = remember { mutableStateOf(FunctionalConfigManager.DEFAULT_CONFIG_ID) }
        LaunchedEffect(selectedFunctionType) {
                modelConfigId.value = functionalConfigManager.getConfigIdForFunction(functionType)
        }
        val modelConfig by
                modelConfigManager
                        .getModelConfigFlow(modelConfigId.value)
                        .collectAsState(initial = null)

        val snackbarHostState = remember { SnackbarHostState() }
        val scrollState = rememberScrollState(initial = uiState.scrollPosition)
        val scope = rememberCoroutineScope()
        val dragonBonesController = rememberDragonBonesController()

        // 在 Composable 函数中获取字符串资源，以便在 LaunchedEffect 中使用
        val operationSuccessString = context.getString(R.string.operation_success)
        val errorOccurredString = context.getString(R.string.error_occurred_simple)

        // Sync ViewModel state with Controller
        LaunchedEffect(uiState.config) {
                uiState.config?.let {
                        dragonBonesController.scale = it.scale
                        dragonBonesController.translationX = it.translateX
                        dragonBonesController.translationY = it.translateY
                }
        }

        // Sync Controller changes back to ViewModel
        LaunchedEffect(
                dragonBonesController.scale,
                dragonBonesController.translationX,
                dragonBonesController.translationY
        ) {
                uiState.config?.let {
                        if (it.scale != dragonBonesController.scale ||
                                        it.translateX != dragonBonesController.translationX ||
                                        it.translateY != dragonBonesController.translationY
                        ) {
                                viewModel.updateScale(dragonBonesController.scale)
                                viewModel.updateTranslateX(dragonBonesController.translationX)
                                viewModel.updateTranslateY(dragonBonesController.translationY)
                        }
                }
        }

        LaunchedEffect(dragonBonesController) {
                dragonBonesController.onSlotTap = { slotName ->
                        scope.launch { snackbarHostState.showSnackbar("Tapped on: $slotName") }
                }
        }

        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        viewModel.updateScrollPosition(position)
                }
        }

        // 显示操作结果的 SnackBar
        LaunchedEffect(uiState.operationSuccess, uiState.errorMessage) {
                if (uiState.operationSuccess) {
                        snackbarHostState.showSnackbar(operationSuccessString)
                        viewModel.clearOperationSuccess()
                } else if (uiState.errorMessage != null) {
                        snackbarHostState.showSnackbar(uiState.errorMessage ?: errorOccurredString)
                        viewModel.clearErrorMessage()
                }
        }

        Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                        TopAppBar(
                                title = { Text(stringResource(R.string.assistant_config_title)) },
                                actions = {
                                        // 导入模型按钮
                                        IconButton(
                                                onClick = openZipFilePicker,
                                                enabled = !uiState.isImporting && !uiState.isLoading
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.FileUpload,
                                                        contentDescription = stringResource(R.string.import_model)
                                                )
                                        }

                                        // 刷新模型列表按钮
                                        IconButton(
                                                onClick = { viewModel.scanUserModels() },
                                                enabled =
                                                        !uiState.isImporting &&
                                                                !uiState.isLoading &&
                                                                !uiState.isScanning
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = stringResource(R.string.scan_user_models)
                                                )
                                        }
                                }
                        )
                }
        ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                        // 主要内容
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(paddingValues)
                                                .padding(horizontal = 12.dp)
                                                .verticalScroll(scrollState)
                        ) {
                                // DragonBones预览区域
                                DragonBonesPreviewSection(
                                        modifier = Modifier.fillMaxWidth().height(300.dp),
                                        controller = dragonBonesController,
                                        uiState = uiState,
                                        onDeleteCurrentModel =
                                                uiState.currentModel?.let { model ->
                                                        { viewModel.deleteUserModel(model.id) }
                                                }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                DragonBonesConfigSection(
                                        controller = dragonBonesController,
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        onImportClick = { openZipFilePicker() }
                                )

                                HowToImportSection()

                                Spacer(modifier = Modifier.height(12.dp))

                                // 功能配置区域
                                Column(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(bottom = 4.dp, start = 4.dp)
                                ) {
                                        Text(
                                                stringResource(R.string.function_config_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                                stringResource(R.string.function_config_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                                Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.1f
                                                )
                                ) {
                                        Column(
                                                modifier =
                                                        Modifier.padding(
                                                                vertical = 4.dp,
                                                                horizontal = 8.dp
                                                        )
                                        ) {
                                                // 功能切换器
                                                Row(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(vertical = 4.dp),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        PromptFunctionType.values().forEach {
                                                                functionType ->
                                                                FilterChip(
                                                                        selected =
                                                                                selectedFunctionType ==
                                                                                        functionType,
                                                                        onClick = {
                                                                                selectedFunctionType =
                                                                                        functionType
                                                                        },
                                                                        label = {
                                                                                Text(
                                                                                        when (functionType) {
                                                                                            PromptFunctionType.CHAT -> context.getString(R.string.chat_function)
                                                                                            PromptFunctionType.VOICE -> context.getString(R.string.voice_function)
                                                                                            PromptFunctionType.DESKTOP_PET -> context.getString(R.string.desktop_pet_function)
                                                                                        }
                                                                                )
                                                                        }
                                                                )
                                                        }
                                                }

                                                								Divider(
										modifier = Modifier.padding(vertical = 4.dp)
								)

								// 已移除：AI人设卡生成器入口

								// 提示词配置入口（跳转至模型提示词设置）
								SettingItem(
										icon = Icons.Default.Description,
										title = stringResource(R.string.settings_prompt_title),
										value = stringResource(R.string.settings_configure_prompt),
										onClick = navigateToModelPrompts
								)

								SettingItem(
										icon = Icons.Default.Message,
										title = stringResource(R.string.function_prompt),
										value = promptProfile?.name ?: stringResource(R.string.not_configured),
										onClick = navigateToFunctionalPrompts
								)

                                                SettingItem(
                                                        icon = Icons.Default.Api,
                                                        title = stringResource(R.string.function_model),
                                                        value = modelConfig?.name ?: stringResource(R.string.not_configured),
                                                        onClick = navigateToFunctionalConfig
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 底部空间
                                Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 加载指示器覆盖层
                        if (uiState.isLoading || uiState.isImporting) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.7f)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator()
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text =
                                                                if (uiState.isImporting) stringResource(R.string.importing_model)
                                                                else stringResource(R.string.processing),
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                        }
                                }
                        }
                }
        }
}
