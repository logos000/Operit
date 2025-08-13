package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.model.ToolParameter

import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionalPromptManager
import com.ai.assistance.operit.data.preferences.PromptFunctionType
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import com.ai.assistance.operit.core.tools.ComputerDesktopActionResultData




/** 处理会话相关功能的服务类，包括会话总结、偏好处理和对话切割准备 */
class ConversationService(private val context: Context) {

    companion object {
        private const val TAG = "ConversationService"
    }

    private val apiPreferences = ApiPreferences(context)
    private val functionalPromptManager = FunctionalPromptManager(context)
    private val conversationMutex = Mutex()

    /**
     * 生成对话总结
     * @param messages 要总结的消息列表
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            multiServiceManager: MultiServiceManager
    ): String {
        return generateSummary(messages, null, multiServiceManager)
    }

    /**
     * 生成对话总结，并且包含上一次的总结内容
     * @param messages 要总结的消息列表
     * @param previousSummary 上一次的总结内容，可以为null
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            previousSummary: String?,
            multiServiceManager: MultiServiceManager
    ): String {
        try {
            // 使用更结构化、更详细的提示词
            var systemPrompt = FunctionalPrompts.SUMMARY_PROMPT.trimIndent()

            // 如果存在上一次的摘要，将其添加到系统提示中，为模型提供更明确的上下文。
            if (previousSummary != null && previousSummary.isNotBlank()) {
                systemPrompt +=
                        """

                上一次的摘要（用于继承上下文）：
                ${previousSummary.trim()}
                请将以上摘要中的关键信息，与本次新的对话内容相融合，生成一份全新的、更完整的摘要。
                """
                Log.d(TAG, "添加上一条摘要内容到系统提示")
            }

            val finalMessages = listOf(Pair("system", systemPrompt)) + messages

            // Get all model parameters from preferences (with enabled state)
            val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }

            // 获取SUMMARY功能类型的AIService实例
            val summaryService = multiServiceManager.getServiceForFunction(FunctionType.SUMMARY)

            // 使用summaryService发送请求，收集完整响应
            val contentBuilder = StringBuilder()

            // 使用新的Stream API
            val stream =
                    summaryService.sendMessage(
                            message = "请按照要求总结对话内容",
                            chatHistory = finalMessages,
                            modelParameters = modelParameters
                    )

            // 收集流中的所有内容
            stream.collect { content -> contentBuilder.append(content) }

            // 获取完整的总结内容
            val summaryContent = ChatUtils.removeThinkingContent(contentBuilder.toString().trim())

            // 如果内容为空，返回默认消息
            if (summaryContent.isBlank()) {
                return "对话摘要：未能生成有效摘要。"
            }

            // 获取本次总结生成的token统计
            val inputTokens = summaryService.inputTokenCount
            val outputTokens = summaryService.outputTokenCount

            // 将总结token计数添加到用户偏好分析的token统计中
            try {
                Log.d(TAG, "总结生成使用了输入token: $inputTokens, 输出token: $outputTokens")
                apiPreferences.updateTokensForFunction(FunctionType.SUMMARY, inputTokens, outputTokens)
                Log.d(TAG, "已将总结token统计添加到用户偏好分析token计数中")
            } catch (e: Exception) {
                Log.e(TAG, "更新token统计失败", e)
            }

            return summaryContent
        } catch (e: Exception) {
            Log.e(TAG, "生成总结时出错", e)
            // return "对话摘要：生成摘要时出错，但对话仍在继续。"
            throw e
        }
    }

    /**
     * 为聊天准备对话历史记录
     * @param chatHistory 原始聊天历史
     * @param processedInput 处理后的用户输入
     * @param workspacePath 当前绑定的工作区路径，可以为null
     * @param packageManager 包管理器
     * @param promptFunctionType 提示函数类型
     * @param thinkingGuidance 是否需要思考指导
     * @return 准备好的对话历史列表
     */
    suspend fun prepareConversationHistory(
            chatHistory: List<Pair<String, String>>,
            processedInput: String,
            workspacePath: String?,
            packageManager: PackageManager,
            promptFunctionType: PromptFunctionType,
            thinkingGuidance: Boolean = false
    ): List<Pair<String, String>> {
        val preparedHistory = mutableListOf<Pair<String, String>>()
        conversationMutex.withLock {
            // Add system prompt if not already present
            if (!chatHistory.any { it.first == "system" }) {
                // Check if planning is enabled
                val planningEnabled = apiPreferences.enableAiPlanningFlow.first()

                // Get prompts for the specific function type from FunctionalPromptManager
                val (introPrompt, tonePrompt) =
                        functionalPromptManager.getPromptForFunction(promptFunctionType)

                // 获取系统提示词，现在传入workspacePath
                val systemPrompt =
                        SystemPromptConfig.getSystemPromptWithCustomPrompts(
                        packageManager,
                        workspacePath,
                        planningEnabled,
                        introPrompt,
                        tonePrompt,
                        thinkingGuidance
                )
                // 仅加入系统提示词，不再拼接用户偏好描述
                preparedHistory.add(0, Pair("system", systemPrompt))
            }

            // Process each message in chat history
            chatHistory.forEachIndexed { index, message ->
                val role = message.first
                val content = message.second

                // If it's an assistant message, check for tool results
                if (role == "assistant") {
                    val xmlTags = splitXmlTag(content)
                    if (xmlTags.isNotEmpty()) {
                        // Process the message with tool results
                        processChatMessageWithTools(content, xmlTags, preparedHistory, index, chatHistory.size)
                    } else {
                        // Add the message as is
                        preparedHistory.add(message)
                    }
                } else {
                    // Add user or system messages as is
                    preparedHistory.add(message)
                }
            }
        }
        return preparedHistory
    }

    /**
     * 提取内容中的XML标签
     * @param content 要处理的内容
     * @return 提取的XML标签列表，每项包含[标签名称, 标签内容]
     */
    fun splitXmlTag(content: String): List<List<String>> {
        val results = mutableListOf<List<String>>()

        // 使用StreamXmlPlugin处理XML标签
        val plugins = listOf(StreamXmlPlugin(includeTagsInOutput = true))

        try {
            // 将内容转换为Stream<Char>然后用插件拆分
            val contentStream = content.stream()
            val tagContents = mutableListOf<String>() // 标签内容
            val tagNames = mutableListOf<String>() // 标签名称

            // 使用协程作用域收集拆分结果
            kotlinx.coroutines.runBlocking {
                contentStream.splitBy(plugins).collect { group ->
                    if (group.tag is StreamXmlPlugin) {
                        val sb = StringBuilder()
                        var isFirstChar = true

                        // 收集完整的XML元素内容
                        group.stream.collect { charString ->
                            if (isFirstChar) {
                                isFirstChar = false
                            }
                            sb.append(charString)
                        }

                        val fullContent = sb.toString()

                        // 提取标签名称
                        val tagNameMatch = Regex("<([a-zA-Z0-9_]+)[\\s>]").find(fullContent)
                        val tagName = tagNameMatch?.groupValues?.getOrNull(1) ?: "unknown"

                        tagNames.add(tagName)
                        tagContents.add(fullContent)
                    } else {
                        // 处理纯文本内容
                        val sb = StringBuilder()

                        // 收集纯文本内容
                        group.stream.collect { charString -> sb.append(charString) }

                        val textContent = sb.toString()
                        if (textContent.isNotBlank()) {
                            // 对于纯文本，将其作为text标签处理
                            tagNames.add("text")
                            tagContents.add(textContent)
                        }
                    }
                }
            }

            // 将收集到的XML标签转换为二维列表
            for (i in tagNames.indices) {
                results.add(listOf(tagNames[i], tagContents[i]))
            }
        } catch (e: Exception) {
            Log.e(TAG, "使用Stream解析XML标签时出错", e)
        }

        return results
    }

    /** 处理包含工具结果的聊天消息，并按顺序重新组织消息 任务完成和等待用户响应的status标签算作AI消息，其他status和warning算作用户消息 工具结果为用户消息 */
    suspend fun processChatMessageWithTools(
            content: String,
            xmlTags: List<List<String>>,
            conversationHistory: MutableList<Pair<String, String>>,
            messageIndex: Int,
            totalMessages: Int
    ) {
        if (xmlTags.isEmpty()) {
            // 如果没有XML标签，直接添加为AI消息
            conversationHistory.add(Pair("assistant", content))
            return
        }

        // 按顺序处理标签
        val segments = mutableListOf<Pair<String, String>>() // 角色, 内容

        for (tag in xmlTags) {
            val tagName = tag[0]
            var tagContent = tag[1]

            // 对于text类型（纯文本），直接作为AI消息
            if (tagName == "text") {
                if (tagContent.isNotBlank()) {
                    segments.add(Pair("assistant", tagContent))
                }
                continue
            }

            // 应用内存优化: 只有当消息不是最近25条时才触发
            val distanceFromEnd = totalMessages - 1 - messageIndex
            if (distanceFromEnd > 25 && tagContent.length > 1000 && tagName == "tool_result") {
                 Log.d(TAG, "Optimizing tool result for message at index $messageIndex (distance from end: $distanceFromEnd)")
                tagContent = optimizeToolResult(tagContent)
            }

            // 根据标签类型分配角色
            when (tagName) {
                "status" -> {
                    // 判断status类型
                    if (tagContent.contains("type=\"complete\"") ||
                                    tagContent.contains("type=\"wait_for_user_need\"")
                    ) {
                        segments.add(Pair("assistant", tagContent))
                    } else {
                        segments.add(Pair("user", tagContent))
                    }
                }
                "tool_result" -> {
                    segments.add(Pair("user", tagContent))
                }
                else -> {
                    segments.add(Pair("assistant", tagContent))
                }
            }
        }

        // 合并连续的相同角色消息
        val mergedSegments = mutableListOf<Pair<String, String>>()
        var currentRole = ""
        var currentContent = StringBuilder()

        for (segment in segments) {
            if (segment.first == currentRole) {
                // 如果角色与当前角色相同，则合并内容
                currentContent.append("\n").append(segment.second)
            } else {
                // 角色不同，先保存当前内容（如果有）
                if (currentContent.isNotEmpty()) {
                    mergedSegments.add(Pair(currentRole, currentContent.toString().trim()))
                    currentContent.clear()
                }
                // 更新当前角色和内容
                currentRole = segment.first
                currentContent.append(segment.second)
            }
        }

        // 添加最后一条消息
        if (currentContent.isNotEmpty()) {
            mergedSegments.add(Pair(currentRole, currentContent.toString().trim()))
        }

        // 将合并后的消息添加到对话历史
        conversationHistory.addAll(mergedSegments)
    }

    /**
     * Optimize tool result by selecting the most important parts This helps with memory management
     * for long tool outputs
     */
    fun optimizeToolResult(toolResult: String): String {
        // 如果结果不够长，直接返回
        if (toolResult.length <= 1000) return toolResult

        // 提取工具名称
        val nameMatch = Regex("name=\"([^\"]+)\"").find(toolResult)
        val toolName = nameMatch?.groupValues?.getOrNull(1) ?: "unknown"

        // 为特定工具类型保留完整内容
        if (toolName == "use_package") {
            return toolResult
        }

        // 提取内容
        val tagContent =
                Regex("<[^>]*>(.*?)</[^>]*>", RegexOption.DOT_MATCHES_ALL)
                        .find(toolResult)
                        ?.groupValues
                        ?.getOrNull(1)

        val sb = StringBuilder()

        // 添加工具名称前缀
        sb.append("<tool_result name=\"$toolName\">")

        // 处理提取的内容
        if (!tagContent.isNullOrEmpty()) {
            // 对于XML内容，最多保留800个字符
            val maxContentLength = 800
            val content =
                    if (tagContent.length > maxContentLength) {
                        tagContent.substring(0, 400) +
                                "\n... [content truncated for memory optimization] ...\n" +
                                tagContent.substring(tagContent.length - 400)
                    } else {
                        tagContent
                    }
            sb.append(content)
        } else {
            // 对于非XML内容，从头部和尾部保留重要部分
            sb.append(toolResult.substring(0, 400))
            sb.append("\n... [content truncated for memory optimization] ...\n")
            sb.append(toolResult.substring(toolResult.length - 400))
        }

        // 添加结束标签
        sb.append("</tool_result>")

        return sb.toString()
    }

    /** Build a formatted preferences text string from a PreferenceProfile */
    fun buildPreferencesText(profile: PreferenceProfile): String {
        val parts = mutableListOf<String>()

        if (profile.gender.isNotEmpty()) {
            parts.add("性别: ${profile.gender}")
        }

        if (profile.birthDate > 0) {
            // Convert timestamp to age and format as text
            val today = Calendar.getInstance()
            val birthCal = Calendar.getInstance().apply { timeInMillis = profile.birthDate }
            var age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
            // Adjust age if birthday hasn't occurred yet this year
            if (today.get(Calendar.MONTH) < birthCal.get(Calendar.MONTH) ||
                            (today.get(Calendar.MONTH) == birthCal.get(Calendar.MONTH) &&
                                    today.get(Calendar.DAY_OF_MONTH) <
                                            birthCal.get(Calendar.DAY_OF_MONTH))
            ) {
                age--
            }
            parts.add("年龄: ${age}岁")

            // Also add birth date for more precise information
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            parts.add("出生日期: ${dateFormat.format(java.util.Date(profile.birthDate))}")
        }

        if (profile.personality.isNotEmpty()) {
            parts.add("性格特点: ${profile.personality}")
        }

        if (profile.identity.isNotEmpty()) {
            parts.add("身份认同: ${profile.identity}")
        }

        if (profile.occupation.isNotEmpty()) {
            parts.add("职业: ${profile.occupation}")
        }

        if (profile.aiStyle.isNotEmpty()) {
            parts.add("期待的AI风格: ${profile.aiStyle}")
        }

        return parts.joinToString("; ")
    }

    /** Data class for search-replace operations, used for JSON deserialization. */
    private data class SearchReplaceOperation(val search: String, val replace: String)

    /**
     * Processes file binding using a two-step approach to balance token cost and reliability.
     * 1. Attempts a custom, low-token "loose text patch" that is whitespace-insensitive.
     * 2. If that fails, falls back to a robust but more token-intensive full-content merge.
     *
     * @param originalContent The original content of the file.
     * @param aiGeneratedCode The AI-generated code with placeholders, representing the desired
     * changes.
     * @param multiServiceManager The service manager for AI communication.
     * @return A Pair containing the final merged content and a diff string representing the
     * changes.
     */
    suspend fun processFileBinding(
            originalContent: String,
            aiGeneratedCode: String,
            multiServiceManager: MultiServiceManager
    ): Pair<String, String> {
        // --- Attempt 1: Custom Loose Text Patch (Low Token Cost) ---
        Log.d(TAG, "Attempt 1: Trying Custom Loose Text Patch...")
        try {
            val normalizedOriginalContent = originalContent.replace("\r\n", "\n")
            val normalizedAiGeneratedCode = aiGeneratedCode.replace("\r\n", "\n").trim()

            val systemPrompt = FunctionalPrompts.FILE_BINDING_PATCH_PROMPT.trimIndent()

            val userPrompt =
"""
**Original File Content:**
```
$normalizedOriginalContent
```
**AI's Edit Request:**
```
$normalizedAiGeneratedCode
```
Now, generate ONLY the patch in the custom format based on all the rules.
""".trimIndent()
            val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }
            val fileBindingService =
                    multiServiceManager.getServiceForFunction(FunctionType.FILE_BINDING)

            val contentBuilder = StringBuilder()
            fileBindingService.sendMessage(
                            userPrompt,
                            listOf(Pair("system", systemPrompt)),
                            modelParameters
                    )
                    .collect { content -> contentBuilder.append(content) }

            val patchResponse = ChatUtils.removeThinkingContent(contentBuilder.toString())

            val (success, patchedContent) =
                    applyLooseTextPatch(normalizedOriginalContent, patchResponse)

            if (success) {
                Log.d(TAG, "Attempt 1: Custom Loose Text Patch succeeded.")
                apiPreferences.updateTokensForFunction(
                        FunctionType.FILE_BINDING,
                        fileBindingService.inputTokenCount,
                        fileBindingService.outputTokenCount
                )

                val finalDiff =
                        UnifiedDiffUtils.generateUnifiedDiff(
                                        "a/file",
                                        "b/file",
                                        normalizedOriginalContent.lines(),
                                        DiffUtils.diff(
                                                normalizedOriginalContent.lines(),
                                                patchedContent.lines()
                                        ),
                                        3
                                )
                                .joinToString("\n")

                return Pair(patchedContent, finalDiff)
            } else {
                Log.w(
                        TAG,
                        "Attempt 1: Custom Loose Text Patch failed. Falling back to robust full merge."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Attempt 1: Error during Custom Loose Text Patch. Falling back...", e)
        }

        // --- Attempt 2: Robust Full-Content Merge (Fallback) ---
        Log.d(TAG, "Attempt 2 (Fallback): Trying robust full-content merge...")
        try {
            val normalizedOriginalContent = originalContent.replace("\r\n", "\n")
            val normalizedAiGeneratedCode = aiGeneratedCode.replace("\r\n", "\n").trim()
            val mergeSystemPrompt = FunctionalPrompts.FILE_BINDING_MERGE_PROMPT.trimIndent()

            val mergeUserPrompt =
"""
**Original File Content:**
```
$normalizedOriginalContent
```
**AI-Generated Code (with placeholders):**
```
$normalizedAiGeneratedCode
```
Now, generate ONLY the complete and final merged file content.
""".trimIndent()

            val modelParameters = runBlocking { apiPreferences.getAllModelParameters() }
            val fileBindingService =
                    multiServiceManager.getServiceForFunction(FunctionType.FILE_BINDING)

            val contentBuilder = StringBuilder()
            fileBindingService.sendMessage(
                            mergeUserPrompt,
                            listOf(Pair("system", mergeSystemPrompt)),
                            modelParameters
                    )
                    .collect { content -> contentBuilder.append(content) }

            val mergedContentFromAI =
                    ChatUtils.removeThinkingContent(contentBuilder.toString().trim())

            if (mergedContentFromAI.isBlank()) {
                Log.w(TAG, "Attempt 2: Full merge returned empty content. Returning original.")
                return Pair(originalContent, "")
            }

            val diffString =
                    UnifiedDiffUtils.generateUnifiedDiff(
                                    "a/file",
                                    "b/file",
                                    normalizedOriginalContent.lines(),
                                    DiffUtils.diff(
                                            normalizedOriginalContent.lines(),
                                            mergedContentFromAI.lines()
                                    ),
                                    3
                            )
                            .joinToString("\n")

            Log.d(TAG, "Attempt 2: Robust full-content merge successful.")
            apiPreferences.updateTokensForFunction(
                    FunctionType.FILE_BINDING,
                    fileBindingService.inputTokenCount,
                    fileBindingService.outputTokenCount
            )
            return Pair(mergedContentFromAI, diffString)
        } catch (e: Exception) {
            Log.e(TAG, "Attempt 2: Error during robust full-merge fallback.", e)
            return Pair(originalContent, "Error during fallback file binding: ${e.message}")
        }
    }

    /**
     * Normalizes a block of text for a "loose" comparison. This makes the comparison insensitive to
     * leading/trailing whitespace on each line, and also to the amount of internal whitespace
     * between non-whitespace characters. It preserves the number of lines (including blank ones) to
     * ensure an unambiguous replacement.
     */
    private fun normalizeBlock(block: String): String {
        return block.lines().joinToString("\n") { it.trim().replace("\\s+".toRegex(), " ") }
    }

    /**
     * Applies a series of search-and-replace operations from a custom patch format using a "loose"
     * matching algorithm that ignores leading/trailing whitespace on each line.
     *
     * @return A Pair of (Boolean, String) indicating success and the modified content.
     */
    private fun applyLooseTextPatch(
            originalContent: String,
            patchText: String
    ): Pair<Boolean, String> {
        var modifiedContent = originalContent
        try {
            val patchRegex =
                    """(?s)<<<<<<< SEARCH\n(.*?)\n=======\n(.*?)\n>>>>>>> REPLACE""".toRegex()
            val operations =
                    patchRegex
                            .findAll(patchText)
                            .map {
                                // groupValues[1] is the SEARCH block, groupValues[2] is the REPLACE
                                // block
                                it.groupValues[1].trim() to it.groupValues[2].trim()
                            }
                            .toList()

            if (operations.isEmpty()) {
                Log.w(TAG, "Custom patch was empty or did not match expected format.")
                return Pair(false, originalContent)
            }

            for ((searchBlock, replaceBlock) in operations) {
                val normalizedSearch = normalizeBlock(searchBlock)
                if (normalizedSearch.isEmpty()) continue

                val originalLines = modifiedContent.lines()
                val searchLinesCount = searchBlock.lines().size
                var matchIndex = -1

                // Find a unique loose match
                for (i in 0..(originalLines.size - searchLinesCount)) {
                    val windowBlock =
                            originalLines.subList(i, i + searchLinesCount).joinToString("\n")
                    if (normalizeBlock(windowBlock) == normalizedSearch) {
                        if (matchIndex != -1) { // Ambiguous match
                            Log.w(
                                    TAG,
                                    "Loose Patch failed: ambiguous match for search block:\n$searchBlock"
                            )
                            return Pair(false, originalContent)
                        }
                        matchIndex = i
                    }
                }

                if (matchIndex != -1) { // Unique match found
                    val modifiedLinesList = originalLines.toMutableList()
                    repeat(searchLinesCount) { modifiedLinesList.removeAt(matchIndex) }
                    modifiedLinesList.addAll(matchIndex, replaceBlock.lines())
                    modifiedContent = modifiedLinesList.joinToString("\n")
                } else { // No match found
                    Log.w(
                            TAG,
                            "Loose Patch failed: could not find a unique match for block:\n$searchBlock"
                    )
                    return Pair(false, originalContent)
                }
            }
            return Pair(true, modifiedContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply custom loose text patch.", e)
            return Pair(false, originalContent)
        }
    }

    /**
     * Flattens the hierarchical UI node structure into a simple, flat list of key elements.
     * This provides a much cleaner context for the AI to make decisions.
     */
    private fun flattenUiInfo(pageInfo: UIPageResultData): String {
        val clickableElements = mutableListOf<String>()
        val screenTexts = mutableListOf<String>()

        fun traverse(node: SimplifiedUINode) {
            // If the node is clickable, treat it as an atomic unit. We'll gather all text from
            // its entire subtree to form a comprehensive description for the AI.
            if (node.isClickable) {
                val parts = mutableListOf<String>()
                
                // Start by collecting standard properties like resource ID, class, and bounds.
                node.resourceId?.takeIf { it.isNotBlank() }?.let { parts.add("id: $it") }

                // --- NEW: Recursively find all text and content descriptions in the subtree ---
                val descriptiveTexts = mutableListOf<String>()
                fun findTextsRecursively(n: SimplifiedUINode) {
                    n.text?.takeIf { it.isNotBlank() }?.let { descriptiveTexts.add(it) }
                    n.contentDesc?.takeIf { it.isNotBlank() }?.let { descriptiveTexts.add(it) }
                    n.children.forEach(::findTextsRecursively)
                }
                findTextsRecursively(node)

                // Combine all found texts into a single descriptive string. This is crucial for
                // elements where the text label is in a child node of the clickable area.
                val combinedText = descriptiveTexts.distinct().joinToString(" | ")
                if (combinedText.isNotBlank()) {
                    // Using "desc" to signify this is a constructed description. Increased length.
                    parts.add("desc: \"${combinedText.replace("\"", "'").take(80)}\"")
                }
                // --- END NEW ---

                node.className?.let { parts.add("class: ${it.substringAfterLast('.')}") }
                node.bounds?.let { parts.add("bounds: ${it.replace(' ', ',')}") }

                // Only add the element if it has some identifiable information.
                if (parts.isNotEmpty()) {
                    clickableElements.add("[${parts.joinToString(", ")}]")
                }
                // Once an element is identified as clickable, we don't process its children separately.
            } else {
                // If the node is not clickable, add its text for general context and continue traversal.
                node.text?.takeIf { it.isNotBlank() }?.let {
                    screenTexts.add("\"${it.replace("\"", "'").take(70)}\"")
                }
                node.children.forEach(::traverse)
            }
        }

        traverse(pageInfo.uiElements)

        // Use distinct to remove duplicate text entries from non-clickable elements.
        val distinctScreenTexts = screenTexts.distinct()

        return """
        Package: ${pageInfo.packageName}
        Activity: ${pageInfo.activityName}
        Clickable Elements:
        ${clickableElements.joinToString("\n")}
        Screen Text (
        for context):
        ${distinctScreenTexts.joinToString("\n")}
        """.trimIndent()
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keysItr = this.keys()
        while (keysItr.hasNext()) {
            val key = keysItr.next()
            var value = this.get(key)
            if (value is JSONObject) {
                value = value.toMap()
            }
            if (value is JSONArray) {
                value = value.toList()
            }
            map[key] = value
        }
        return map
    }

    private fun JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until this.length()) {
            var value = this.get(i)
            if (value is JSONObject) {
                value = value.toMap()
            }
            if (value is JSONArray) {
                value = value.toList()
            }
            list.add(value)
        }
        return list
    }

    private fun createToolFromJson(type: String, arg: Any): AITool {
        val parameters = mutableListOf<ToolParameter>()
        when (arg) {
            is Map<*, *> -> {
                arg.forEach { (key, value) ->
                    val stringValue = when (value) {
                        is Double -> {
                            // If the double has no fractional part, convert to Int string to avoid parse errors.
                            if (value % 1.0 == 0.0) {
                                value.toInt().toString()
                            } else {
                                value.toString()
                            }
                        }
                        else -> value.toString()
                    }
                    parameters.add(ToolParameter(key.toString(), stringValue))
                }
            }
            is String -> {
                 // Fallback for when the AI returns a raw string instead of a JSON object.
                 when (type) {
                     "press_key" -> parameters.add(ToolParameter("key_code", arg))
                     "set_input_text" -> parameters.add(ToolParameter("text", arg))
                     "start_app" -> parameters.add(ToolParameter("package_name", arg))
                 }
            }
        }
        return AITool(type, parameters)
    }
}

