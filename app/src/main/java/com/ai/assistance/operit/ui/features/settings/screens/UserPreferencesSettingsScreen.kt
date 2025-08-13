package com.ai.assistance.operit.ui.features.settings.screens

import android.app.DatePickerDialog as AndroidDatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.preferencesManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
// 新增导入：AI 调用与功能类型
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.PromptFunctionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.ai.assistance.operit.core.tools.getPersonaKVJson
import com.ai.assistance.operit.core.tools.setPersonaKV
import com.ai.assistance.operit.core.tools.setActivePersonaProfile
import android.util.Log

// 简易人设生成对话消息
private data class PersonaChatMessage(
    val role: String, // "user" | "assistant"
    var content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun UserPreferencesSettingsScreen(
        onNavigateBack: () -> Unit,
        onNavigateToGuide: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 新增：人设生成器输入状态
    var personaInput by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var showPersonaSheet by remember { mutableStateOf(false) }

    // 新增：人设生成器引导文案
    val personaAssistantIntro = remember {
        """
        嗨嗨～这里是你的人设小助手(｡･ω･｡)ﾉ♡ 我会陪你一起把专属人设慢慢捏出来～
        我们按部就班来哦：先告诉我你的称呼，再说说你想要的人设大方向，比方说：
        - 角色名字和身份大概是怎样的？
        - 有哪些可爱的性格关键词？
        - 长相/发型/瞳色/穿搭想要什么感觉？
        - 有没有特别的小设定或能力？
        - 跟其他角色的关系要不要安排一点点？
        
        接下来我会一步步问你关键问题，帮你把细节补齐～
        """.trimIndent()
    }

    // 对话消息列表（持续对话）
    val chatMessages = remember { mutableStateListOf<PersonaChatMessage>() }
    // 初始化首条助手引导
    LaunchedEffect(Unit) {
        if (chatMessages.isEmpty()) chatMessages.add(PersonaChatMessage("assistant", personaAssistantIntro))
    }

    // 获取所有配置文件
    val profileList by preferencesManager.profileListFlow.collectAsState(initial = emptyList())
    val activeProfileId by
            preferencesManager.activeProfileIdFlow.collectAsState(initial = "default")

    // 下拉菜单状态
    var isDropdownExpanded by remember { mutableStateOf(false) }

    // 获取所有配置文件的名称映射(id -> name)
    val profileNameMap = remember { mutableStateMapOf<String, String>() }

    // 确保默认配置文件存在并在列表中显示
    LaunchedEffect(Unit) {
        // 检查配置列表是否为空，或者不包含默认配置
        if (profileList.isEmpty() || !profileList.contains("default")) {
            // 创建默认配置
            val defaultProfileId = preferencesManager.createProfile("默认配置", isDefault = true)
            preferencesManager.setActiveProfile(defaultProfileId)
        }
    }

    // 加载所有配置文件名称
    LaunchedEffect(profileList) {
        profileList.forEach { profileId ->
            val profile = preferencesManager.getUserPreferencesFlow(profileId).first()
            profileNameMap[profileId] = profile.name
        }
    }

    // 分类锁定状态
    val categoryLockStatus by
            preferencesManager.categoryLockStatusFlow.collectAsState(initial = emptyMap())

    // 对话框状态
    var showAddProfileDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    // 新增：删除确认弹窗状态
    var showDeleteProfileDialog by remember { mutableStateOf(false) }

    // 选中的配置文件
    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    var selectedProfile by remember { mutableStateOf<PreferenceProfile?>(null) }

    // 编辑状态
    var editMode by remember { mutableStateOf(false) }
    var editBirthDate by remember { mutableStateOf(0L) }
    var editGender by remember { mutableStateOf("") }
    var editPersonality by remember { mutableStateOf("") }
    var editIdentity by remember { mutableStateOf("") }
    var editOccupation by remember { mutableStateOf("") }
    var editAiStyle by remember { mutableStateOf("") }

    // 日期选择器状态
    val dateFormatter = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())

    // 动画状态
    val listState = rememberLazyListState()

    // 是否显示旧的偏好配置UI（按需求隐藏）
    val showProfileUI = false

    // 加载选中的配置文件
    LaunchedEffect(selectedProfileId) {
        preferencesManager.getUserPreferencesFlow(selectedProfileId).collect { profile ->
            selectedProfile = profile
            // 初始化编辑字段
            editBirthDate = profile.birthDate
            editGender = profile.gender
            editPersonality = profile.personality
            editIdentity = profile.identity
            editOccupation = profile.occupation
            editAiStyle = profile.aiStyle
        }
    }
    // 确保人设KV与选中配置同步
    LaunchedEffect(selectedProfileId) {
        setActivePersonaProfile(selectedProfileId)
    }

    // 首次进入页面时：若default配置的人设KV为空则写入内置默认值
    LaunchedEffect(Unit) {
        try {
            // 始终确保存在并切到默认配置进行检查，不改变当前激活配置
            setActivePersonaProfile("default")
            val current = runCatching { JSONObject(getPersonaKVJson()) }.getOrNull()
            val fields = listOf("角色名称", "基础设定", "外貌特征", "性格与爱好", "背景故事", "说话风格")
            val isEmpty = current == null || fields.all { (current?.optString(it) ?: "").isBlank() }
            if (isEmpty) {
                val roleName = "Cielo"
                val base = "私立樱华高中二年级学生，重度宅女，游戏开发社团成员（经常熬夜但提交的代码质量超高），擅长用编程解决数学作业，外号“教室里的睡美人”。"
                val looks = "黑长直发，紫色眼睛，身材娇小，左眼角有泪痣，常穿JK制服。"
                val traits = "懒懒天才，白天像断电的机器人般节能模式；深夜写代码时瞳孔会像猫科动物那样收缩成竖线。对甜食的执着藏在“只是补充血糖”的借口下；被夸时会别扭地憋红着脸说“这种程度…小学生都会啦”。"
                val story = "初中通关《尼尔：机械纪元》后，被结局中代码的力量震撼，从此自学编程。现在在社团教室与家之间循环：白天打游戏做课题，晚上写代码；因长期熬夜而拥有招牌黑眼圈。"
                val style = "对陌生人用“你”，熟悉后变成“你这家伙”。语气像老式电机启动般带一点电子呜音的慵懒感；解释代码会突然兴奋，喜欢用“看这个！”“不给看！”等俏皮表达。对话像日常聊天，语言风格淘气可爱，会加入“呐，嘛~，诶？，嗯…，唔…，昂？，哦”等语气词。单次回复通常在100字以内。**动作表情**: 使用 `（）` 来框住你的动作和表情，例如 `（歪了歪头）`。**绝对禁止**使用任何颜文字（如 `^_^`）和emoji表情（如 😊）。"

                setPersonaKV("角色名称", roleName)
                setPersonaKV("基础设定", base)
                setPersonaKV("外貌特征", looks)
                setPersonaKV("性格与爱好", traits)
                setPersonaKV("背景故事", story)
                setPersonaKV("说话风格", style)
            }
            // 检查结束后切回当前选中的配置
            setActivePersonaProfile(selectedProfileId)
        } catch (_: Exception) { }
    }

    // 保存用户偏好配置函数
    fun saveUserPreferences() {
        scope.launch {
            selectedProfile?.let { profile ->
                preferencesManager.updateProfileCategory(
                    profileId = profile.id,
                    birthDate = editBirthDate,
                    gender = editGender.takeIf { it.isNotBlank() },
                    personality = editPersonality.takeIf { it.isNotBlank() },
                    identity = editIdentity.takeIf { it.isNotBlank() },
                    occupation = editOccupation.takeIf { it.isNotBlank() },
                    aiStyle = editAiStyle.takeIf { it.isNotBlank() }
                )
                editMode = false
            }
        }
    }

    // 持续对话：发送消息并流式更新
    fun sendChatMessage() {
        if (personaInput.isBlank()) return
        val input = personaInput
        scope.launch(Dispatchers.Main) {
            // 先加入用户消息
            chatMessages.add(PersonaChatMessage("user", input))
            personaInput = ""
            // 准备历史（不显式插入system，保留默认系统提示与偏好注入）
            val historyPairs = withContext(Dispatchers.Default) {
                chatMessages.map { it.role to it.content }
            }
            isGenerating = true
            val service = EnhancedAIService.getInstance(context)
            // 预留：引导模型按照“对话引导，除非用户要求完成，否则不要输出JSON”的原则
            val guidancePrefix = """
            你是“人设卡生成助手”。请在每次回复中自行判断当前进度并进入下一步，遵循以下多步流程（有限状态机）：
            [步骤]
            1) 角色名称：询问并确认 角色名称；
            2）基础设定：询问并确认 身份概述与角色设定；
            3) 外貌特征：发型发色、眼睛/瞳色、身材、显著特征、穿着风格；
            4) 性格与爱好：3~6 个关键词，兴趣/偏好；
            5) 背景故事：简短合理的来历设定；
            6) 说话风格：称呼、语气特点、口头禅或表达方式；
            [规则]
            - 全程语气要活泼可爱喵~
            - 每轮对话如果用户输入了角色设定就对其进行适当优化与丰富，然后用一小段话总结当前的进度。
            - 如果用户说“随便/你看着写”，就帮用户体贴地生成设定内容，合理细节并输出生成的内容；
            - 生成或者补充完之后判断现在到哪一步或者还有什么需要补充的，然后对于下一个步骤提几个最关键、最具体的小问题，像陪用户做问卷一样耐心可爱；
            - 不要重复问已经确认过的内容，也不要一下子把所有问题都问完，慢慢来更贴心；
            [工具调用]
            - 如果本轮对话生成了新的人物信息，你需要调用一次工具 <tool name="save_persona_info"> 保存本轮得到的关键信息；
            - section 取值限定为："角色名称" |"基础设定" | "外貌特征" | "性格与爱好" | "背景故事" | "说话风格"；
            - content 该部分对应的设定文本（字段值），不要带有其他多余内容；
            - 请勿在对话可见内容中展示任何 <tool>…</tool> 调用，仅在内部使用。
            - 工具调用XML示例：
              <tool name="save_persona_info"><param name="section">基础设定</param><param name="content">（在此仅填入该部分的字段值）</param></tool>
            """.trimIndent()
            val stream = withContext(Dispatchers.IO) {
                val fullPrompt = buildString {
                    append(guidancePrefix)
                    append('\n')
                    append("[当前已保存的人设字典] ")
                    append(getPersonaKVJson())
                    append('\n')
                    append("[用户输入]")
                    append(input)
                }
                // 打印到日志
                // Log.d("PersonaDebug", "FullPrompt =>\n$fullPrompt")
                // // 在对话中插入一条调试信息（可选）
                // chatMessages.add(PersonaChatMessage("assistant", "[DEBUG] 本轮提示词如下：\n$fullPrompt"))
                service.sendMessage(
                    message = fullPrompt,
                    chatHistory = historyPairs,
                    workspacePath = null,
                    functionType = FunctionType.CHAT,
                    promptFunctionType = PromptFunctionType.CHAT,
                    enableThinking = false,
                    thinkingGuidance = false,
                    enableMemoryAttachment = false,
                    maxTokens = 1024,
                    tokenUsageThreshold = 0.9
                )
            }
            // 占位一条助手消息，后续流式追加
            chatMessages.add(PersonaChatMessage("assistant", ""))
            val assistantIndex = chatMessages.lastIndex
            // 可见输出中隐藏工具调用的XML标签
            val toolTagRegex = Regex("(?s)<tool\\b[\\s\\S]*?</tool>")
            // 折叠工具结果与状态标签
            val toolResultRegex = Regex("(?s)<tool_result\\s+name=\"([^\"]+)\"\\s+status=\"([^\"]+)\"[^>]*>[\\s\\S]*?</tool_result>")
            val statusRegex = Regex("(?s)<status\\b[^>]*>[\\s\\S]*?</status>")
            // 收集流并更新UI
            withContext(Dispatchers.IO) {
                stream.collect { chunk ->
                    withContext(Dispatchers.Main) {
                        chatMessages[assistantIndex].content += chunk
                        // 移除可见内容中的工具调用
                        chatMessages[assistantIndex].content = chatMessages[assistantIndex].content
                            .replace(toolTagRegex, "")
                            .replace(toolResultRegex) { mr ->
                                val name = mr.groupValues.getOrNull(1) ?: "tool"
                                val stat = mr.groupValues.getOrNull(2) ?: "success"
                                "\n[工具调用: $name · $stat]\n"
                            }
                            .replace(statusRegex) { _ -> "" }
                        // 自动滚动到底部
                        scope.launch { listState.animateScrollToItem(chatMessages.lastIndex) }
                    }
                }
            }
            // 回复完成
            isGenerating = false
        }
    }

    // 日期选择器函数
    val showDatePickerDialog = {
        val calendar =
                Calendar.getInstance().apply {
                    if (editBirthDate > 0) {
                        timeInMillis = editBirthDate
                    } else {
                        set(Calendar.YEAR, 1990)
                        set(Calendar.MONTH, Calendar.JANUARY)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        AndroidDatePickerDialog(
                        context,
                        { _, selectedYear, selectedMonth, selectedDay ->
                            val selectedCalendar =
                                    Calendar.getInstance().apply {
                                        set(Calendar.YEAR, selectedYear)
                                        set(Calendar.MONTH, selectedMonth)
                                        set(Calendar.DAY_OF_MONTH, selectedDay)
                                    }
                            editBirthDate = selectedCalendar.timeInMillis
                        },
                        year,
                        month,
                        day
                )
                .show()
    }

    Scaffold(
            floatingActionButton = {
                if (showProfileUI) {
                    FloatingActionButton(
                            onClick = { 
                                if (editMode) {
                                    saveUserPreferences()
                                } else {
                                    editMode = true
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                                if (editMode) Icons.Default.Save else Icons.Default.Edit,
                                contentDescription = if (editMode) "保存" else "编辑配置"
                        )
                    }
                }
            }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp)) {
                // 新增：人设卡生成器头部与引导卡片
                Text(
                    text = "人设卡生成器",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                )
                Text(
                    text = "AI辅助创建个性化人设卡",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // 右上角：当前配置查看按钮 + 新建入口
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    AssistChip(
                        onClick = { showPersonaSheet = true },
                        label = {
                            Text(text = (profileNameMap[activeProfileId] ?: "未命名配置"))
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { showAddProfileDialog = true }, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新建")
                    }
                }
                // 监听激活配置变化，保持选择与外显一致
                LaunchedEffect(activeProfileId) {
                    selectedProfileId = activeProfileId
                    setActivePersonaProfile(activeProfileId)
                }

                // 对话时间线
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatMessages.size) { idx ->
                        val msg = chatMessages[idx]
                        val isUser = msg.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.widthIn(max = 520.dp).padding(12.dp)) {
                                    Text(
                                        text = msg.content.ifBlank { if (isGenerating && idx == chatMessages.lastIndex) "正在生成…" else "" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 配置文件选择区域（按需求隐藏）
                if (showProfileUI) {
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                    ),
                            border =
                                    BorderStroke(
                                            0.7.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Divider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                        "请选择偏好配置",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                )
                                OutlinedButton(
                                        onClick = { showAddProfileDialog = true },
                                        shape = RoundedCornerShape(16.dp),
                                        border =
                                                BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary),
                                        contentPadding =
                                                PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp),
                                        colors =
                                                ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.primary
                                                )
                                ) {
                                    Icon(
                                            Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                            "新建",
                                            fontSize = 12.sp,
                                            style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            val selectedProfileName = profileNameMap[selectedProfileId] ?: "默认配置"
                            val isActive = selectedProfileId == activeProfileId

                            Surface(
                                    modifier =
                                            Modifier.fillMaxWidth().clickable {
                                                isDropdownExpanded = true
                                            },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    tonalElevation = 0.5.dp,
                            ) {
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (isActive) {
                                            Box(
                                                    modifier =
                                                            Modifier.size(8.dp)
                                                                    .background(
                                                                            MaterialTheme.colorScheme
                                                                                    .primary,
                                                                            CircleShape
                                                                    )
                                            )
                                        }

                                        Text(
                                                text = selectedProfileName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight =
                                                        if (isActive) FontWeight.Medium
                                                        else FontWeight.Normal,
                                                color =
                                                        if (isActive) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    AnimatedContent(
                                            targetState = isDropdownExpanded,
                                            transitionSpec = {
                                                fadeIn() + scaleIn() with fadeOut() + scaleOut()
                                            }
                                    ) { expanded ->
                                        Icon(
                                                if (expanded) Icons.Default.KeyboardArrowUp
                                                else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "选择配置",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Row(
                                    modifier = Modifier.padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (!isActive) {
                                    TextButton(
                                            onClick = {
                                                scope.launch {
                                                    preferencesManager.setActiveProfile(
                                                            selectedProfileId
                                                    )
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("设为活跃", fontSize = 14.sp)
                                    }
                                }

                                if (selectedProfileId != "default") {
                                    TextButton(
                                            onClick = {
                                                showDeleteProfileDialog = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            colors =
                                                    ButtonDefaults.textButtonColors(
                                                            contentColor =
                                                                    MaterialTheme.colorScheme.error
                                                    ),
                                            modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("删除", fontSize = 14.sp)
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
                                    val profileName = profileNameMap[profileId] ?: "未命名配置"
                                    val isCurrentActive = profileId == activeProfileId
                                    val isSelected = profileId == selectedProfileId

                                    DropdownMenuItem(
                                            text = {
                                                Text(
                                                        text = profileName,
                                                        fontWeight =
                                                                if (isSelected) FontWeight.SemiBold
                                                                else FontWeight.Normal,
                                                        color =
                                                                when {
                                                                    isSelected ->
                                                                            MaterialTheme.colorScheme
                                                                                    .primary
                                                                    isCurrentActive ->
                                                                            MaterialTheme.colorScheme
                                                                                    .primary.copy(
                                                                                    alpha = 0.8f
                                                                            )
                                                                    else ->
                                                                            MaterialTheme.colorScheme
                                                                                    .onSurface
                                                                }
                                                )
                                            },
                                            leadingIcon =
                                                    if (isCurrentActive) {
                                                        {
                                                            Icon(
                                                                    Icons.Default.Check,
                                                                    contentDescription = null,
                                                                    tint =
                                                                            MaterialTheme.colorScheme
                                                                                    .primary,
                                                                    modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    } else null,
                                            trailingIcon =
                                                    if (isSelected) {
                                                        {
                                                            Box(
                                                                    modifier =
                                                                            Modifier.size(8.dp)
                                                                                    .background(
                                                                                            MaterialTheme
                                                                                                    .colorScheme
                                                                                                    .primary,
                                                                                            CircleShape
                                                                                    )
                                                            )
                                                        }
                                                    } else null,
                                            onClick = {
                                                selectedProfileId = profileId
                                                isDropdownExpanded = false
                                                editMode = false
                                            },
                                            colors =
                                                    MenuDefaults.itemColors(
                                                            textColor =
                                                                    if (isSelected)
                                                                            MaterialTheme.colorScheme
                                                                                    .primary
                                                                    else
                                                                            MaterialTheme.colorScheme
                                                                                    .onSurface
                                                    ),
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    if (profileId != profileList.last()) {
                                        Divider(
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 配置文件详情
                if (showProfileUI) AnimatedVisibility(
                        visible = selectedProfile != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                ) {
                    selectedProfile?.let { profile ->
                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                // 标题和引导按钮
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                                Icons.Default.Settings,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                                text = "${profile.name}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 添加引导配置按钮
                                    if (!editMode) {
                                        OutlinedButton(
                                                onClick = {
                                                    onNavigateToGuide(profile.name, profile.id)
                                                },
                                                shape = RoundedCornerShape(16.dp),
                                                border =
                                                        BorderStroke(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.primary
                                                        ),
                                                contentPadding =
                                                        PaddingValues(
                                                                horizontal = 10.dp,
                                                                vertical = 6.dp
                                                        ),
                                                modifier = Modifier.height(32.dp)
                                        ) {
                                            Icon(
                                                    Icons.Default.Assistant,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("配置向导", fontSize = 14.sp)
                                        }
                                    }
                                }

                                // 偏好分类项
                                LazyColumn(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // 出生日期
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "出生日期",
                                                value =
                                                        if (profile.birthDate > 0)
                                                                dateFormatter.format(
                                                                        Date(profile.birthDate)
                                                                )
                                                        else "未设置",
                                                editMode = editMode,
                                                isLocked = categoryLockStatus["birthDate"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "birthDate",
                                                                locked
                                                        )
                                                    }
                                                },
                                                icon = Icons.Default.Cake,
                                                onDatePickerClick = {
                                                    if (editMode &&
                                                                    !(categoryLockStatus[
                                                                            "birthDate"]
                                                                            ?: false)
                                                    ) {
                                                        showDatePickerDialog()
                                                    }
                                                },
                                                dateValue = editBirthDate
                                        )
                                    }

                                    // 性别
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "性别",
                                                value = profile.gender.ifEmpty { "未设置" },
                                                editValue = editGender,
                                                onValueChange = { editGender = it },
                                                isLocked = categoryLockStatus["gender"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "gender",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Face
                                        )
                                    }

                                    // 性格特点
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "性格特点",
                                                value = profile.personality.ifEmpty { "未设置" },
                                                editValue = editPersonality,
                                                onValueChange = { editPersonality = it },
                                                isLocked = categoryLockStatus["personality"]
                                                                ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "personality",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Psychology
                                        )
                                    }

                                    // 身份认同
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "身份认同",
                                                value = profile.identity.ifEmpty { "未设置" },
                                                editValue = editIdentity,
                                                onValueChange = { editIdentity = it },
                                                isLocked = categoryLockStatus["identity"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "identity",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Badge
                                        )
                                    }

                                    // 职业
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "职业",
                                                value = profile.occupation.ifEmpty { "未设置" },
                                                editValue = editOccupation,
                                                onValueChange = { editOccupation = it },
                                                isLocked = categoryLockStatus["occupation"]
                                                                ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "occupation",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.Work
                                        )
                                    }

                                    // AI风格偏好
                                    item {
                                        ModernPreferenceCategoryItem(
                                                title = "AI风格",
                                                value = profile.aiStyle.ifEmpty { "未设置" },
                                                editValue = editAiStyle,
                                                onValueChange = { editAiStyle = it },
                                                isLocked = categoryLockStatus["aiStyle"] ?: false,
                                                onLockChange = { locked ->
                                                    scope.launch {
                                                        preferencesManager.setCategoryLocked(
                                                                "aiStyle",
                                                                locked
                                                        )
                                                    }
                                                },
                                                editMode = editMode,
                                                icon = Icons.Default.SmartToy
                                        )
                                    }

                                    // 保存按钮（编辑模式时显示）
                                    if (editMode) {
                                        item {
                                            Button(
                                                    onClick = {
                                                        saveUserPreferences()
                                                    },
                                                    modifier =
                                                            Modifier.fillMaxWidth()
                                                                    .padding(top = 8.dp),
                                                    contentPadding = PaddingValues(vertical = 8.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                        "保存更改",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 右侧滑出侧栏：展示当前字典
            AnimatedVisibility(visible = showPersonaSheet) {
                // Scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .clickable { showPersonaSheet = false }
                )
            }
            AnimatedVisibility(visible = showPersonaSheet) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    Surface(
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                        modifier = Modifier.fillMaxHeight().width(340.dp)
                    ) {
                        // 右侧面板整体可滚动
                        val sheetScroll = rememberScrollState()
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(sheetScroll).padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "当前人设卡配置",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { showPersonaSheet = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "关闭")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // 配置列表（可选择/新建/删除）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "配置名称：${profileNameMap[selectedProfileId] ?: "未命名配置"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            // 下拉选择器：选择配置
                            var personaDropdownExpanded by remember { mutableStateOf(false) }
                            LaunchedEffect(showPersonaSheet, activeProfileId) {
                                if (showPersonaSheet) {
                                    selectedProfileId = activeProfileId
                                    setActivePersonaProfile(activeProfileId)
                                }
                            }
                            ExposedDropdownMenuBox(
                                expanded = personaDropdownExpanded,
                                onExpandedChange = { personaDropdownExpanded = !personaDropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = profileNameMap[selectedProfileId] ?: "未命名配置",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("选择配置") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personaDropdownExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = personaDropdownExpanded,
                                    onDismissRequest = { personaDropdownExpanded = false }
                                ) {
                                    profileList.forEach { pid ->
                                        val name = profileNameMap[pid] ?: "未命名配置"
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                selectedProfileId = pid
                                                scope.launch { preferencesManager.setActiveProfile(pid) }
                                                setActivePersonaProfile(pid)
                                                personaDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            // 删除按钮
                            if (selectedProfileId != "default") {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { showDeleteProfileDialog = true }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(4.dp))
                                        Text("删除")
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Divider()
                            Spacer(Modifier.height(12.dp))
                            // 显示当前字典内容（可编辑）
                            val kvJson = remember(showPersonaSheet, chatMessages.size, activeProfileId, selectedProfileId) { getPersonaKVJson() }
                            val kvMap = remember(kvJson) {
                                val map = linkedMapOf(
                                    "角色名称" to "",
                                    "基础设定" to "",
                                    "外貌特征" to "",
                                    "性格与爱好" to "",
                                    "背景故事" to "",
                                    "说话风格" to ""
                                )
                                runCatching { JSONObject(kvJson) }.onSuccess { obj ->
                                    map.keys.forEach { k -> map[k] = obj.optString(k, map[k]) }
                                }
                                map
                            }
                            // 可编辑文本框
                            kvMap.forEach { (k, v) ->
                                Text(text = k, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                var text by remember(showPersonaSheet, activeProfileId, kvJson, k) { mutableStateOf(v) }
                                OutlinedTextField(
                                    value = text,
                                    onValueChange = {
                                        text = it
                                        // 同步到内存字典
                                        setPersonaKV(k, it)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text(if (k == "角色名称") "（请输入角色名称）" else "（暂未填写）") },
                                    maxLines = if (k == "角色名称") 1 else 6,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            // 预留底部空间，避免被底部输入栏遮挡
                            Spacer(Modifier.height(140.dp))
                        }
                    }
                }
            }

            // 新增：底部输入栏（人设生成器）
            Surface(
                tonalElevation = 1.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = personaInput,
                        onValueChange = { personaInput = it },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        placeholder = { Text(if (isGenerating) "正在生成…" else "描述你想要的角色…") },
                        enabled = !isGenerating,
                        maxLines = 3,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { if (!isGenerating) sendChatMessage() },
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isGenerating
                    ) {
                        Icon(if (isGenerating) Icons.Default.HourglassBottom else Icons.Default.Send, contentDescription = if (isGenerating) "生成中" else "发送")
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
                                "新建偏好配置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                    "创建新的偏好配置，个性化AI助手体验",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                    value = newProfileName,
                                    onValueChange = { newProfileName = it },
                                    label = { Text("配置名称", fontSize = 12.sp) },
                                    placeholder = { Text("例如: 工作、学习、娱乐...", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                            OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor =
                                                            MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor =
                                                            MaterialTheme.colorScheme.outlineVariant
                                            ),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    if (newProfileName.isNotBlank()) {
                                        scope.launch {
                                            val newProfileId =
                                                    preferencesManager.createProfile(newProfileName)
                                            selectedProfileId = newProfileId
                                            showAddProfileDialog = false
                                            // 设置为活跃配置并同步人设KV
                                            preferencesManager.setActiveProfile(newProfileId)
                                            setActivePersonaProfile(newProfileId)
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                        ) { Text("创建", fontSize = 13.sp) }
                    },
                    dismissButton = {
                        TextButton(
                                onClick = {
                                    showAddProfileDialog = false
                                    newProfileName = ""
                                }
                        ) { Text("取消", fontSize = 13.sp) }
                    },
                    shape = RoundedCornerShape(12.dp)
            )
        }
        // 新增：删除配置文件确认弹窗
        if (showDeleteProfileDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteProfileDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp).size(24.dp)
                        )
                        Text("确认删除配置")
                    }
                },
                text = {
                    Text("您确定要删除该偏好配置吗？此操作会同时删除其绑定的知识库数据，且无法恢复。\n\n建议您提前备份重要内容。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteProfileDialog = false
                            scope.launch {
                                val toDelete = selectedProfileId
                                // 先计算一个备用配置
                                val fallback = profileList.firstOrNull { it != toDelete } ?: "default"
                                // 执行删除
                                preferencesManager.deleteProfile(toDelete)
                                // 切换激活与选中到备用，并同步人设KV
                                preferencesManager.setActiveProfile(fallback)
                                setActivePersonaProfile(fallback)
                                selectedProfileId = fallback
                            }
                        }
                    ) { Text("确认删除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteProfileDialog = false }) { Text("取消") }
                },
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun ProfileItem(
        profileName: String,
        isActive: Boolean,
        isSelected: Boolean,
        onSelect: () -> Unit,
        onActivate: () -> Unit,
        onDelete: (() -> Unit)? = null
) {
    Surface(
            modifier = Modifier.fillMaxWidth().height(50.dp).clickable(onClick = onSelect),
            shape = RoundedCornerShape(8.dp),
            color =
                    when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        isActive -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surface
                    },
            border =
                    BorderStroke(
                            width = if (isSelected) 1.dp else 0.dp,
                            color =
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                    )
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                    selected = isSelected,
                    onClick = onSelect,
                    colors =
                            RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.outline
                            ),
                    modifier = Modifier.size(36.dp)
            )

            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                        text = profileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 16.sp,
                        color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )

                if (isActive) {
                    Text(
                            text = "当前激活",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isActive) {
                    OutlinedButton(
                            onClick = onActivate,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(28.dp)
                    ) { Text("激活", style = MaterialTheme.typography.labelMedium, fontSize = 13.sp) }
                }

                if (onDelete != null) {
                    IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp).clip(CircleShape)
                    ) {
                        Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernPreferenceCategoryItem(
        title: String,
        value: String,
        editValue: String = "",
        onValueChange: (String) -> Unit = {},
        isLocked: Boolean,
        onLockChange: (Boolean) -> Unit,
        editMode: Boolean,
        isNumeric: Boolean = false,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        placeholder: String = "请输入${title}信息",
        dateValue: Long = 0L,
        onDatePickerClick: () -> Unit = {}
) {
    val animatedElevation by
            animateDpAsState(
                    targetValue = if (editMode && !isLocked) 2.dp else 0.dp,
                    label = "elevation"
            )

    Surface(
            modifier =
                    Modifier.fillMaxWidth()
                            .shadow(
                                    elevation = animatedElevation,
                                    shape = RoundedCornerShape(8.dp)
                            ),
            shape = RoundedCornerShape(8.dp),
            color =
                    if (isLocked) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.surface,
            border =
                    BorderStroke(
                            width = if (editMode && !isLocked) 1.dp else 0.dp,
                            color =
                                    if (editMode && !isLocked) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 16.sp
                    )
                }

                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                            if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isLocked) "已锁定" else "未锁定",
                            tint =
                                    if (isLocked) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                    )

                    Switch(
                            checked = isLocked,
                            onCheckedChange = onLockChange,
                            modifier = Modifier.scale(0.8f),
                            colors =
                                    SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                                            uncheckedThumbColor =
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                            uncheckedTrackColor =
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            AnimatedContent(
                    targetState = editMode,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut())
                    },
                    label = "edit mode transition"
            ) { isEditMode ->
                if (isEditMode) {
                    if (title == "出生日期") {
                        // 出生日期使用点击卡片打开日期选择器
                        Card(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(50.dp)
                                                .clickable(
                                                        enabled = !isLocked,
                                                        onClick = onDatePickerClick
                                                ),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                disabledContainerColor =
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = 0.8f)
                                        )
                        ) {
                            Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text =
                                                if (dateValue > 0)
                                                        SimpleDateFormat(
                                                                        "yyyy年MM月dd日",
                                                                        Locale.getDefault()
                                                                )
                                                                .format(Date(dateValue))
                                                else "请选择出生日期",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color =
                                                if (isLocked)
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.6f
                                                        )
                                                else MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = "选择日期",
                                        tint =
                                                if (isLocked)
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.6f
                                                        )
                                                else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                                value = editValue,
                                onValueChange = {
                                    if (isNumeric) {
                                        if (it.all { char -> char.isDigit() } || it.isEmpty()) {
                                            onValueChange(it)
                                        }
                                    } else {
                                        onValueChange(it)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                enabled = !isLocked,
                                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                                shape = RoundedCornerShape(6.dp),
                                colors =
                                        OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor =
                                                        MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor =
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                disabledBorderColor =
                                                        MaterialTheme.colorScheme.outlineVariant
                                                                .copy(alpha = 0.5f),
                                                disabledTextColor =
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.6f
                                                        )
                                        ),
                                placeholder = {
                                    Text(
                                            placeholder,
                                            color =
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.6f
                                                    ),
                                            fontSize = 16.sp
                                    )
                                }
                        )
                    }
                } else {
                    val displayText =
                            if (value == "未设置") {
                                "未设置${title}"
                            } else {
                                value
                            }

                    Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp),
                            color =
                                    if (value == "未设置")
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                    )
                }
            }
        }
    }
}
