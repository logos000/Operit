package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.data.model.PromptProfile
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.PromptPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ai.assistance.operit.data.preferences.preferencesManager
import org.json.JSONObject
import com.ai.assistance.operit.data.preferences.FunctionalPromptManager
import com.ai.assistance.operit.data.preferences.PromptFunctionType
import com.ai.assistance.operit.data.preferences.PromptSuffixPreferencesManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun ModelPromptsSettingsScreen(
        onBackPressed: () -> Unit = {},
        onNavigateToFunctionalPrompts: () -> Unit = {},
        onNavigateToMarket: () -> Unit = {}
) {
    val context = LocalContext.current
    val apiPreferences = remember { ApiPreferences(context) }
    val promptPreferencesManager = remember { PromptPreferencesManager(context) }
    val suffixPreferencesManager = remember { PromptSuffixPreferencesManager(context) }
    val scope = rememberCoroutineScope()
    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    // 提示词配置文件列表
    val profileList =
            promptPreferencesManager.profileListFlow.collectAsState(initial = listOf("default"))
                    .value

    // 后缀配置文件列表
    val suffixProfileList = suffixPreferencesManager.profileListFlow.collectAsState(initial = listOf("default")).value

    // 对话框状态
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    // 选中的配置文件
    var selectedProfileId by remember { mutableStateOf(profileList.firstOrNull() ?: "default") }
    val selectedProfile = remember { mutableStateOf<PromptProfile?>(null) }

    // 选中的后缀配置
    var selectedSuffixId by remember { mutableStateOf(suffixProfileList.firstOrNull() ?: "default") }
    var selectedSuffixContent by remember { mutableStateOf("") }

    // 编辑状态
    var editMode by remember { mutableStateOf(false) }

    // 默认提示词
    val defaultIntroPrompt = promptPreferencesManager.defaultIntroPrompt
    val defaultTonePrompt = promptPreferencesManager.defaultTonePrompt
    val defaultCombinedPrompt = remember(defaultIntroPrompt, defaultTonePrompt) {
        "$defaultIntroPrompt\n\n$defaultTonePrompt"
    }

    // 合并后的单一提示词输入
    var combinedPromptInput by remember { mutableStateOf(defaultCombinedPrompt) }
    // 新增：提示词后缀输入
    var suffixPromptInput by remember { mutableStateOf("") }

    // 动画状态
    val listState = rememberLazyListState()

    // 下拉菜单状态
    var isDropdownExpanded by remember { mutableStateOf(false) }
    // 后缀下拉菜单状态
    var isSuffixDropdownExpanded by remember { mutableStateOf(false) }

    // 获取所有配置文件的名称映射(id -> name)
    val profileNameMap = remember { mutableStateMapOf<String, String>() }
    val suffixNameMap = remember { mutableStateMapOf<String, String>() }

    // 渲染下拉所用的后缀ID列表（已去重并排序，默认后缀置顶）
    val suffixIdsForMenu = remember { mutableStateListOf<String>() }

    // 加载所有配置文件名称
    LaunchedEffect(profileList) {
        profileList.forEach { profileId ->
            val profile = promptPreferencesManager.getPromptProfileFlow(profileId).first()
            profileNameMap[profileId] = profile.name
        }
    }

    // 初始化提示词/后缀配置
    LaunchedEffect(Unit) {
        promptPreferencesManager.initializeIfNeeded()
        suffixPreferencesManager.initializeIfNeeded()
        selectedSuffixId = suffixPreferencesManager.activeProfileIdFlow.first()
    }

    // 加载选中的配置文件
    LaunchedEffect(selectedProfileId) {
        promptPreferencesManager.getPromptProfileFlow(selectedProfileId).collect { profile ->
            selectedProfile.value = profile
            val tone = profile.tonePrompt.trim()
            combinedPromptInput = if (tone.isNotEmpty()) {
                profile.introPrompt + "\n\n" + profile.tonePrompt
            } else {
                profile.introPrompt
            }
            // 使用独立后缀系统，不再从提示词配置内读取
            // 保持 suffixPromptInput 由独立后缀选择器赋值
        }
    }

    // 监听后缀ID变化，实时刷新下方文本框（不再受是否为空限制）
    LaunchedEffect(selectedSuffixId) {
        val sp = suffixPreferencesManager.getSuffixProfileFlow(selectedSuffixId).first()
        suffixNameMap[selectedSuffixId] = sp.name
        selectedSuffixContent = sp.content
        suffixPromptInput = sp.content
    }

    // 点击展开后缀选择时，动态检索最新的后缀列表并去重，且将“默认后缀(空)”置顶
    fun refreshSuffixMenuOptions() {
        scope.launch {
            suffixPreferencesManager.initializeIfNeeded()
            val ids = suffixPreferencesManager.profileListFlow.first()
            suffixNameMap.clear()
            val uniqueIds = LinkedHashSet<String>()
            ids.forEach { id ->
                val sp = suffixPreferencesManager.getSuffixProfileFlow(id).first()
                suffixNameMap[id] = sp.name
                uniqueIds.add(id)
            }
            val ordered = buildList {
                if (uniqueIds.contains(PromptSuffixPreferencesManager.DEFAULT_SUFFIX_ID)) add(PromptSuffixPreferencesManager.DEFAULT_SUFFIX_ID)
                addAll(uniqueIds.filter { it != PromptSuffixPreferencesManager.DEFAULT_SUFFIX_ID })
            }
            suffixIdsForMenu.clear()
            suffixIdsForMenu.addAll(ordered)
            if (!suffixIdsForMenu.contains(selectedSuffixId)) {
                selectedSuffixId = PromptSuffixPreferencesManager.DEFAULT_SUFFIX_ID
            }
        }
    }

    // 保存提示词函数
    fun savePrompts() {
        val profile = selectedProfile.value
        if (profile != null) {
            scope.launch {
                promptPreferencesManager.updatePromptProfile(
                    profileId = profile.id,
                    introPrompt = combinedPromptInput,
                    tonePrompt = " "
                )
                suffixPreferencesManager.updateProfile(selectedSuffixId, content = suffixPromptInput)
                suffixPreferencesManager.setActiveProfile(selectedSuffixId)
                showSaveSuccessMessage = true
                editMode = false
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (editMode) { savePrompts() } else { editMode = true } },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) { Icon(if (editMode) Icons.Default.Check else Icons.Default.Edit, contentDescription = if (editMode) stringResource(R.string.save_prompts) else stringResource(R.string.edit_preferences)) }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp).verticalScroll(rememberScrollState())
            ) {
                // 顶部：提示词配置选择 + 提示词编辑
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        
                        // 配置选择器区 - 标签和新建按钮放在一行
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 配置文件选择标签 - 更大的字体
                            Text(
                                stringResource(R.string.select_prompt_config),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextButton(
                                    onClick = onNavigateToMarket,
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("提示词市场", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { showAddProfileDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(stringResource(R.string.create_new), fontSize = 12.sp, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        val selectedProfileName = profileNameMap[selectedProfileId] ?: stringResource(R.string.unnamed_config)

                        Box {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { isDropdownExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                tonalElevation = 0.5.dp,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = selectedProfileName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    AnimatedContent(targetState = isDropdownExpanded, transitionSpec = { fadeIn() + scaleIn() with fadeOut() + scaleOut() }) { expanded ->
                                        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.select_model), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = isDropdownExpanded,
                                onDismissRequest = { isDropdownExpanded = false },
                                modifier = Modifier.width(280.dp),
                                properties = PopupProperties(focusable = true)
                            ) {
                                profileList.forEach { profileId ->
                                    val profileName = profileNameMap[profileId] ?: stringResource(R.string.unnamed_config)
                                    val isSelected = profileId == selectedProfileId
                                    DropdownMenuItem(
                                        text = { Text(text = profileName, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                        leadingIcon = null,
                                        trailingIcon = if (isSelected) { { Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) } } else null,
                                        onClick = { selectedProfileId = profileId; isDropdownExpanded = false; editMode = false },
                                        colors = MenuDefaults.itemColors(textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    if (profileId != profileList.last()) Divider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp)
                                }


                            }
                        }

                        // 操作按钮
                        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (selectedProfileId != "default") {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            promptPreferencesManager.deleteProfile(selectedProfileId)
                                            selectedProfileId = profileList.firstOrNull { it != selectedProfileId } ?: "default"
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.delete_config), fontSize = 14.sp)
                                }
                            }
                        }

                        // 提示词编辑区域
                        Spacer(Modifier.height(8.dp))
                        Text(text = "完整提示词", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                        OutlinedTextField(
                            value = combinedPromptInput,
                            onValueChange = { if (editMode) combinedPromptInput = it },
                            label = { Text("请输入完整提示词") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            placeholder = { Text(defaultCombinedPrompt) },
                            minLines = 6,
                            enabled = editMode
                        )

                        if (editMode) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { combinedPromptInput = defaultCombinedPrompt; suffixPromptInput = selectedSuffixContent }) { Text(stringResource(R.string.restore_default_prompts)) }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { savePrompts() }) { Text(stringResource(R.string.save_prompts)) }
                            }
                        }
                    }
                }

                // 底部：后缀选择 + 后缀编辑
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            Icon(imageVector = Icons.Default.PostAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.custom_prompt_suffix_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        val selectedSuffixName = suffixNameMap[selectedSuffixId] ?: "默认后缀"
                        Box {
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    refreshSuffixMenuOptions()
                                    isSuffixDropdownExpanded = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                tonalElevation = 0.5.dp,
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(selectedSuffixName, style = MaterialTheme.typography.bodyMedium)
                                    AnimatedContent(targetState = isSuffixDropdownExpanded, transitionSpec = { fadeIn() + scaleIn() with fadeOut() + scaleOut() }) { expanded ->
                                        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = isSuffixDropdownExpanded,
                                onDismissRequest = { isSuffixDropdownExpanded = false },
                                modifier = Modifier.width(280.dp),
                                properties = PopupProperties(focusable = true)
                            ) {
                                suffixIdsForMenu.forEach { id ->
                                    val name = suffixNameMap[id] ?: "默认后缀"
                                    val isSelected = id == selectedSuffixId
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        trailingIcon = if (isSelected) { { Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) } } else null,
                                        onClick = {
                                            selectedSuffixId = id
                                            // 选择后立即刷新文本框内容
                                            scope.launch {
                                                val sp = suffixPreferencesManager.getSuffixProfileFlow(id).first()
                                                suffixPromptInput = sp.content
                                            }
                                            isSuffixDropdownExpanded = false
                                        }
                                    )
                                    if (id != suffixIdsForMenu.lastOrNull()) Divider()
                                }
                            }
                        }

                        // 后缀编辑
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = suffixPromptInput,
                            onValueChange = { if (editMode) suffixPromptInput = it },
                            label = { Text(stringResource(R.string.custom_prompt_suffix_hint)) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            placeholder = { Text(stringResource(R.string.custom_prompt_suffix_placeholder)) },
                            minLines = 4,
                            enabled = editMode
                        )
                    }
                }

                // 提示词解释卡片
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.about_system_prompts),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = stringResource(R.string.system_prompts_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            PromptInfoRow(
                                title = stringResource(R.string.ai_self_introduction), 
                                description = stringResource(R.string.intro_prompt_desc)
                            )

                        }
                        
                        Text(
                            text = stringResource(R.string.prompt_tip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 成功保存消息
                if (showSaveSuccessMessage) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.prompts_saved),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LaunchedEffect(showSaveSuccessMessage) {
                        kotlinx.coroutines.delay(3000)
                        showSaveSuccessMessage = false
                    }
                }
            }
        }
    }
    
    // 新建配置文件对话框
    if (showAddProfileDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddProfileDialog = false
                newProfileName = ""
            },
            title = {
                Text(
                    stringResource(R.string.new_prompt_config_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.new_prompt_config_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = newProfileName, onValueChange = { newProfileName = it }, label = { Text(stringResource(R.string.config_name), fontSize = 12.sp) }, placeholder = { Text(stringResource(R.string.config_name_placeholder), fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), singleLine = true)
                    Spacer(modifier = Modifier.height(8.dp))

                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            scope.launch {
                                        val profileId =
                                                promptPreferencesManager.createProfile(
                                                        newProfileName
                                                )
                                selectedProfileId = profileId
                                showAddProfileDialog = false
                                newProfileName = ""
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                ) { Text(stringResource(R.string.create), fontSize = 13.sp) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddProfileDialog = false
                        newProfileName = ""
                    }
                ) { Text(stringResource(R.string.cancel), fontSize = 13.sp) }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun PromptInfoRow(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
    }
}
