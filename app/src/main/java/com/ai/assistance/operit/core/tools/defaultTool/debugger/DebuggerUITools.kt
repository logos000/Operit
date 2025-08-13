package com.ai.assistance.operit.core.tools.defaultTool.debugger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.UIActionResultData
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.defaultTool.accessbility.AccessibilityUITools
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** 调试级别的UI工具，通过Shell命令实现UI操作，继承无障碍版本 */
open class DebuggerUITools(context: Context) : AccessibilityUITools(context) {

    companion object {
        private const val TAG = "DebuggerUITools"
    }

    /** 使用Shell命令实现点击操作 */
    override suspend fun tap(tool: AITool): ToolResult {
        if (UIHierarchyManager.isAccessibilityServiceEnabled(context)) {
            Log.d(TAG, "无障碍服务已启用，使用无障碍点击")
            return super.tap(tool)
        }

        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull()
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull()

        if (x == null || y == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "Missing or invalid coordinates. Both 'x' and 'y' must be valid integers."
            )
        }

        // 显示点击反馈（在主线程上执行）
        withContext(Dispatchers.Main) { operationOverlay.showTap(x, y) }

        // 使用Shell命令执行点击
        try {
            Log.d(TAG, "Attempting to tap at coordinates: ($x, $y) via shell command")
            val command = "input tap $x $y"
            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                Log.d(TAG, "Tap successful at coordinates: ($x, $y)")
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "tap",
                                        actionDescription =
                                                "Successfully tapped at coordinates ($x, $y) via shell command",
                                        coordinates = Pair(x, y)
                                ),
                        error = ""
                )
            } else {
                Log.e(TAG, "Tap failed at coordinates: ($x, $y), error: ${result.stderr}")
                withContext(Dispatchers.Main) {
                    operationOverlay.hide() // 隐藏反馈（在主线程上执行）
                }
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "Failed to tap at coordinates ($x, $y): ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error tapping at coordinates ($x, $y)", e)
            withContext(Dispatchers.Main) {
                operationOverlay.hide() // 隐藏反馈（在主线程上执行）
            }
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error tapping at coordinates: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** 使用Shell命令实现滑动操作 */
    override suspend fun swipe(tool: AITool): ToolResult {
        if (UIHierarchyManager.isAccessibilityServiceEnabled(context)) {
            Log.d(TAG, "无障碍服务已启用，使用无障碍滑动")
            return super.swipe(tool)
        }

        val startX = tool.parameters.find { it.name == "start_x" }?.value?.toIntOrNull()
        val startY = tool.parameters.find { it.name == "start_y" }?.value?.toIntOrNull()
        val endX = tool.parameters.find { it.name == "end_x" }?.value?.toIntOrNull()
        val endY = tool.parameters.find { it.name == "end_y" }?.value?.toIntOrNull()
        val duration = tool.parameters.find { it.name == "duration" }?.value?.toIntOrNull() ?: 300

        if (startX == null || startY == null || endX == null || endY == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "Missing or invalid coordinates. 'start_x', 'start_y', 'end_x', and 'end_y' must be valid integers."
            )
        }

        // 显示滑动反馈（在主线程上执行）
        withContext(Dispatchers.Main) { operationOverlay.showSwipe(startX, startY, endX, endY) }

        try {
            Log.d(
                    TAG,
                    "Attempting to swipe from ($startX, $startY) to ($endX, $endY) with duration $duration ms via shell command"
            )
            val command = "input swipe $startX $startY $endX $endY $duration"
            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                Log.d(TAG, "Swipe successful from ($startX, $startY) to ($endX, $endY)")
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "swipe",
                                        actionDescription =
                                                "Successfully performed swipe from ($startX, $startY) to ($endX, $endY) via shell command"
                                ),
                        error = ""
                )
            } else {
                Log.e(TAG, "Swipe failed: ${result.stderr}")
                withContext(Dispatchers.Main) {
                    operationOverlay.hide() // 隐藏反馈（在主线程上执行）
                }
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to perform swipe: ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing swipe", e)
            withContext(Dispatchers.Main) {
                operationOverlay.hide() // 隐藏反馈（在主线程上执行）
            }
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error performing swipe: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** 使用Shell命令点击元素 */
    override suspend fun clickElement(tool: AITool): ToolResult {
        if (UIHierarchyManager.isAccessibilityServiceEnabled(context)) {
            Log.d(TAG, "无障碍服务已启用，使用无障碍点击元素")
            return super.clickElement(tool)
        }

        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0
        val bounds = tool.parameters.find { it.name == "bounds" }?.value

        if (resourceId == null && className == null && bounds == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "Missing element identifier. Provide at least one of: 'resourceId', 'className', or 'bounds'."
            )
        }

        // 如果提供了边界坐标，直接点击
        if (bounds != null) {
            try {
                // 解析边界坐标格式 [left,top][right,bottom]
                val boundsPattern = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
                val matchResult = boundsPattern.find(bounds)

                if (matchResult == null || matchResult.groupValues.size < 5) {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Invalid bounds format. Should be: [left,top][right,bottom]"
                    )
                }

                // 提取坐标
                val x1 = matchResult.groupValues[1].toInt()
                val y1 = matchResult.groupValues[2].toInt()
                val x2 = matchResult.groupValues[3].toInt()
                val y2 = matchResult.groupValues[4].toInt()

                // 计算中心点
                val centerX = (x1 + x2) / 2
                val centerY = (y1 + y2) / 2

                // 利用tap方法点击中心点
                val tapTool =
                        AITool(
                                name = "tap",
                                parameters =
                                        listOf(
                                                com.ai.assistance.operit.data.model.ToolParameter(
                                                        "x",
                                                        centerX.toString()
                                                ),
                                                com.ai.assistance.operit.data.model.ToolParameter(
                                                        "y",
                                                        centerY.toString()
                                                )
                                        )
                        )

                return tap(tapTool)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing bounds", e)
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Error processing bounds: ${e.message}"
                )
            }
        }

        // 使用uiautomator获取和点击元素
        return clickElementWithUiautomator(tool)
    }

    /** 使用Shell命令设置输入文本 */
    override suspend fun setInputText(tool: AITool): ToolResult {
        if (UIHierarchyManager.isAccessibilityServiceEnabled(context)) {
            Log.d(TAG, "无障碍服务已启用，使用无障碍设置文本")
            return super.setInputText(tool)
        }

        val text = tool.parameters.find { it.name == "text" }?.value ?: ""

        try {
            // 获取屏幕中心作为文本输入的位置
            val displayMetrics = context.resources.displayMetrics
            val centerX = displayMetrics.widthPixels / 2
            val centerY = displayMetrics.heightPixels / 2

            // 显示文本输入反馈（在主线程上执行）
            withContext(Dispatchers.Main) { operationOverlay.showTextInput(centerX, centerY, text) }

            // 使用KEYCODE_CLEAR清除字段，这比模拟CTRL+A和DEL更直接
            Log.d(TAG, "Clearing text field with KEYCODE_CLEAR")
            val clearCommand = "input keyevent KEYCODE_CLEAR"
            AndroidShellExecutor.executeShellCommand(clearCommand)

            // 短暂延迟
            kotlinx.coroutines.delay(300)

            // 如果文本为空，只需清除字段
            if (text.isEmpty()) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "textInput",
                                        actionDescription =
                                                "Successfully cleared input field via shell command"
                                ),
                        error = ""
                )
            }

            // 使用原生复制和ADB粘贴来输入文本，这比'input text'更可靠
            Log.d(TAG, "Setting text to clipboard and pasting via ADB: $text")
            withContext(Dispatchers.Main) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("operit_input", text)
                clipboard.setPrimaryClip(clip)
            }

            // 短暂延迟以确保剪贴板操作完成
            kotlinx.coroutines.delay(100)

            // 执行粘贴命令
            val pasteCommand = "input keyevent KEYCODE_PASTE"
            val pasteResult = AndroidShellExecutor.executeShellCommand(pasteCommand)

            if (pasteResult.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "textInput",
                                        actionDescription =
                                                "Successfully set input text to: $text via clipboard paste"
                                ),
                        error = ""
                )
            } else {
                withContext(Dispatchers.Main) {
                    operationOverlay.hide() // 隐藏反馈（在主线程上执行）
                }
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to paste text from clipboard: ${pasteResult.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting input text", e)
            withContext(Dispatchers.Main) {
                operationOverlay.hide() // 隐藏反馈（在主线程上执行）
            }
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error setting input text: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** 使用Shell命令实现按键操作 */
    override suspend fun pressKey(tool: AITool): ToolResult {
        if (UIHierarchyManager.isAccessibilityServiceEnabled(context)) {
            Log.d(TAG, "无障碍服务已启用，使用无障碍按键")
            return super.pressKey(tool)
        }

        val keyCode = tool.parameters.find { it.name == "key_code" }?.value

        if (keyCode == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Missing 'key_code' parameter."
            )
        }

        val command = "input keyevent $keyCode"

        return try {
            val result = AndroidShellExecutor.executeShellCommand(command)

            if (result.success) {
                ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "keyPress",
                                        actionDescription =
                                                "Successfully pressed key: $keyCode via shell command"
                                ),
                        error = ""
                )
            } else {
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to press key: ${result.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pressing key", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error pressing key: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** 查找UI元素 */
    override suspend fun findElement(tool: AITool): ToolResult {
        if (UIHierarchyManager.isAccessibilityServiceEnabled(context)) {
            Log.d(TAG, "无障碍服务已启用，使用无障碍查找元素")
            return super.findElement(tool)
        }

        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val text = tool.parameters.find { it.name == "text" }?.value
        val contentDesc = tool.parameters.find { it.name == "contentDesc" }?.value
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull()

        if (resourceId == null && className == null && text == null && contentDesc == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "At least one of resourceId, className, text, or contentDesc must be provided."
            )
        }

        // 使用uiautomator查找元素
        return findElementWithUiautomator(tool)
    }

    /** 使用Shell命令获取页面信息 */
    override suspend fun getPageInfo(tool: AITool): ToolResult {
        if (UIHierarchyManager.isAccessibilityServiceEnabled(context)) {
            Log.d(TAG, "无障碍服务已启用，使用无障碍获取页面信息")
            return super.getPageInfo(tool)
        }

        val format = tool.parameters.find { it.name == "format" }?.value ?: "xml"
        val detail = tool.parameters.find { it.name == "detail" }?.value ?: "summary"

        if (format !in listOf("xml", "json")) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Invalid format specified. Must be 'xml' or 'json'."
            )
        }

        return try {
            // 获取UI数据
            val uiData = getUIDataFromShell()
            if (uiData == null) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to retrieve UI data."
                )
            }

            // 解析当前窗口信息
            val focusInfo = extractFocusInfoFromShell(uiData.windowInfo)

            // 简化布局信息
            val simplifiedLayout = simplifyLayoutFromXml(uiData.uiXml)

            // 创建结构化数据
            val resultData =
                    UIPageResultData(
                            packageName = focusInfo.packageName ?: "Unknown",
                            activityName = focusInfo.activityName ?: "Unknown",
                            uiElements = simplifiedLayout
                    )

            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            Log.e(TAG, "Error getting page info", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error getting page info: ${e.message}"
            )
        }
    }

    /** UI数据类，保存XML和窗口信息 */
    private data class UIData(val uiXml: String, val windowInfo: String)

    /** 获取UI数据，使用Shell命令 */
    private suspend fun getUIDataFromShell(): UIData? {
        try {
            // 使用ADB命令获取UI dump
            Log.d(TAG, "使用ADB命令获取UI数据")

            // 执行UI dump命令
            val dumpCommand = "uiautomator dump /sdcard/window_dump.xml"
            val dumpResult = AndroidShellExecutor.executeShellCommand(dumpCommand)
            if (!dumpResult.success) {
                Log.e(TAG, "uiautomator dump失败: ${dumpResult.stderr}")
                return null
            }
            Log.d(TAG, "uiautomator dump成功: ${dumpResult.stdout}")

            // 读取dump文件内容
            val readCommand = "cat /sdcard/window_dump.xml"
            val readResult = AndroidShellExecutor.executeShellCommand(readCommand)
            if (!readResult.success) {
                Log.e(TAG, "读取UI dump文件失败: ${readResult.stderr}")
                return null
            }

            // 获取窗口信息
            var windowInfo = getWindowInfoFromShell()

            // 如果窗口信息为空，尝试延迟后重试一次
            if (windowInfo.isEmpty()) {
                Log.w(TAG, "首次获取窗口信息失败，延迟500ms后重试")
                kotlinx.coroutines.delay(500)
                windowInfo = getWindowInfoFromShell()
            }

            return UIData(readResult.stdout, windowInfo)
        } catch (e: Exception) {
            Log.e(TAG, "获取UI数据时出错", e)
            return null
        }
    }

    /** 获取窗口信息，使用多种命令尝试 */
    private suspend fun getWindowInfoFromShell(): String {
        // 尝试多种命令来获取窗口信息
        val commands =
                listOf(
                        // 标准命令，获取当前焦点和焦点应用
                        "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'",
                        // 备用命令，只获取当前焦点
                        "dumpsys window | grep -E 'mCurrentFocus'",
                        // 备用命令，只获取焦点应用
                        "dumpsys window | grep -E 'mFocusedApp'",
                        // 最后的备用命令，尝试获取任何窗口信息
                        "dumpsys window | grep -E 'Window #|Focus'",
                        // 极端情况下，尝试获取前台应用包名
                        "dumpsys activity recents | grep 'Recent #0' -A2"
                )

        // 依次尝试每个命令，直到有一个成功
        for (command in commands) {
            try {
                val result = AndroidShellExecutor.executeShellCommand(command)
                if (result.success && result.stdout.isNotEmpty()) {
                    Log.d(TAG, "成功获取窗口信息: ${result.stdout.take(100)}")
                    return result.stdout
                }
                // 如果命令执行失败或返回空结果，尝试下一个命令
                Log.w(TAG, "窗口信息命令 '$command' 失败或返回空结果")
            } catch (e: Exception) {
                Log.e(TAG, "执行窗口信息命令 '$command' 出错", e)
                // 继续尝试下一个命令
            }
        }

        // 所有命令都失败时，尝试获取topActivity作为最后的手段
        try {
            val topActivityCommand =
                    "dumpsys activity activities | grep -E 'topResumedActivity|topActivity'"
            val result = AndroidShellExecutor.executeShellCommand(topActivityCommand)
            if (result.success && result.stdout.isNotEmpty()) {
                Log.d(TAG, "使用topActivity作为窗口信息替代: ${result.stdout.take(100)}")
                return result.stdout
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取topActivity失败", e)
        }

        Log.e(TAG, "所有获取窗口信息的尝试均失败")
        return ""
    }

    /** UI节点数据类，仅在Shell实现中使用 */
    private data class UINodeShell(
            val className: String?,
            val text: String?,
            val contentDesc: String?,
            val resourceId: String?,
            val bounds: String?,
            val isClickable: Boolean,
            val children: MutableList<UINodeShell> = mutableListOf()
    )

    /** 简化布局，从XML中提取UI元素，Shell版本 */
    private fun simplifyLayoutFromXml(xml: String): SimplifiedUINode {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }

        val nodeStack = mutableListOf<UINodeShell>()
        var rootNode: UINodeShell? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "node") {
                        val newNode = createNodeShell(parser)
                        if (rootNode == null) {
                            rootNode = newNode
                            nodeStack.add(newNode)
                        } else {
                            nodeStack.lastOrNull()?.children?.add(newNode)
                            nodeStack.add(newNode)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "node") {
                        nodeStack.removeLastOrNull()
                    }
                }
            }
            parser.next()
        }

        // 创建默认根节点，如果解析失败
        return rootNode?.toUINodeSimplified()
                ?: SimplifiedUINode(
                        className = null,
                        text = null,
                        contentDesc = null,
                        resourceId = null,
                        bounds = null,
                        isClickable = false,
                        children = emptyList()
                )
    }

    // 转换内部UINodeShell为SimplifiedUINode
    private fun UINodeShell.toUINodeSimplified(): SimplifiedUINode {
        return SimplifiedUINode(
                className = className,
                text = text,
                contentDesc = contentDesc,
                resourceId = resourceId,
                bounds = bounds,
                isClickable = isClickable,
                children = children.map { it.toUINodeSimplified() }
        )
    }

    private fun createNodeShell(parser: XmlPullParser): UINodeShell {
        // 解析关键属性
        val className = parser.getAttributeValue(null, "class")?.substringAfterLast('.')
        val text = parser.getAttributeValue(null, "text")?.replace("&#10;", "\n")
        val contentDesc = parser.getAttributeValue(null, "content-desc")
        val resourceId = parser.getAttributeValue(null, "resource-id")
        val bounds = parser.getAttributeValue(null, "bounds")
        val isClickable = parser.getAttributeValue(null, "clickable") == "true"

        return UINodeShell(
                className = className,
                text = text,
                contentDesc = contentDesc,
                resourceId = resourceId,
                bounds = bounds,
                isClickable = isClickable
        )
    }

    /** 窗口焦点信息数据类 */
    private data class FocusInfoShell(
            var packageName: String? = null,
            var activityName: String? = null
    )

    /** 从窗口焦点数据中提取包名和活动名 */
    private fun extractFocusInfoFromShell(windowInfo: String): FocusInfoShell {
        val result = FocusInfoShell()

        try {
            if (windowInfo.isBlank()) {
                Log.w(TAG, "Window info is empty, cannot extract focus information")
                // 即使窗口信息为空，也设置默认值，确保不会返回Unknown
                result.packageName = "android"
                result.activityName = "ForegroundActivity"
                return result
            }

            Log.d(TAG, "Window info for extraction: ${windowInfo.take(200)}")

            // 尝试不同的提取方法，按照特异性顺序
            if (!extractFromCurrentFocusShell(windowInfo, result) &&
                            !extractFromFocusedAppShell(windowInfo, result) &&
                            !extractFromLauncherInfoShell(windowInfo, result) &&
                            !extractFromTopActivityShell(windowInfo, result) &&
                            !extractUsingGenericPatternsShell(windowInfo, result)
            ) {

                Log.w(TAG, "Could not extract focus information using any method")
            }

            // 最后的回退：如果我们仍然无法确定任何信息，使用默认值
            if (result.packageName == null) {
                if (windowInfo.contains("statusbar") || windowInfo.contains("SystemUI")) {
                    result.packageName = "com.android.systemui"
                    result.activityName = "SystemUI"
                    Log.d(TAG, "Using SystemUI fallback")
                } else if (windowInfo.contains("recents")) {
                    result.packageName = "com.android.systemui"
                    result.activityName = "Recents"
                    Log.d(TAG, "Using Recents fallback")
                } else {
                    result.packageName = "android"
                    result.activityName = "ForegroundActivity"
                    Log.d(TAG, "Using last-resort fallback values")
                }
            }

            // 记录提取结果
            Log.d(
                    TAG,
                    "Final extraction result - package: ${result.packageName}, activity: ${result.activityName}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing window info", e)
            // 确保即使出现异常，我们也有至少一些默认值
            if (result.packageName == null) result.packageName = "android"
            if (result.activityName == null) result.activityName = "ForegroundActivity"
        }

        return result
    }

    /**
     * 从mCurrentFocus格式提取 例如：mCurrentFocus=Window{1234567 u0
     * com.example.app/com.example.app.MainActivity}
     */
    private fun extractFromCurrentFocusShell(windowInfo: String, result: FocusInfoShell): Boolean {
        // 尝试多种mCurrentFocus格式模式
        val currentFocusPatterns =
                listOf(
                        // 标准格式，具有包/活动
                        "mCurrentFocus=.*?\\{.*?\\s+([a-zA-Z0-9_.]+)/([^\\s}]+)".toRegex(),
                        // 有时会看到的替代格式
                        "mCurrentFocus=.*?\\s+([a-zA-Z0-9_.]+)/([^\\s}]+)\\}".toRegex(),
                        // 只有包名的格式（活动将单独处理）
                        "mCurrentFocus=.*?\\{.*?\\s+([a-zA-Z0-9_.]+)(?:/|\\s)".toRegex()
                )

        for (pattern in currentFocusPatterns) {
            val match = pattern.find(windowInfo)
            if (match != null) {
                if (match.groupValues.size >= 3) {
                    // 包含包和活动的模式
                    result.packageName = match.groupValues[1]
                    result.activityName = match.groupValues[2]
                    Log.d(TAG, "Extracted from mCurrentFocus pattern (full): ${pattern.pattern}")
                    return true
                } else if (match.groupValues.size >= 2) {
                    // 只有包名的模式
                    result.packageName = match.groupValues[1]
                    Log.d(TAG, "Extracted package from mCurrentFocus pattern: ${pattern.pattern}")
                    // 返回false以允许其他方法提取活动名称
                    return false
                }
            }
        }
        return false
    }

    /**
     * 从mFocusedApp格式提取 例如：mFocusedApp=AppWindowToken{token=Token{12345 ActivityRecord{67890 u0
     * com.example.app/.MainActivity t123}}}
     */
    private fun extractFromFocusedAppShell(windowInfo: String, result: FocusInfoShell): Boolean {
        // mFocusedApp格式的多种模式
        val focusedAppPatterns =
                listOf(
                        // 带有ActivityRecord的标准格式
                        "mFocusedApp=.*?ActivityRecord\\{.*?\\s+([a-zA-Z0-9_.]+)/\\.?([^\\s}]+)".toRegex(),
                        // 有时会看到的替代格式
                        "mFocusedApp=.*?\\s+([a-zA-Z0-9_.]+)/\\.?([^\\s}]+)\\s".toRegex(),
                        // 只有包名的格式
                        "mFocusedApp=.*?\\s+([a-zA-Z0-9_.]+)(?:/|\\s)".toRegex()
                )

        for (pattern in focusedAppPatterns) {
            val match = pattern.find(windowInfo)
            if (match != null) {
                if (match.groupValues.size >= 3) {
                    // 包含包和活动的完全匹配
                    result.packageName = match.groupValues[1]
                    result.activityName = match.groupValues[2]
                    Log.d(TAG, "Extracted from mFocusedApp pattern (full): ${pattern.pattern}")
                    return true
                } else if (match.groupValues.size >= 2) {
                    // 只有包的部分匹配
                    result.packageName = match.groupValues[1]
                    Log.d(TAG, "Extracted package from mFocusedApp pattern: ${pattern.pattern}")
                    // 返回false以允许通过其他方法提取活动名称
                    return false
                }
            }
        }
        return false
    }

    /** 为启动器窗口提取信息 例如：mCurrentFocus=Window{1a23bc4 u0 Launcher} */
    private fun extractFromLauncherInfoShell(windowInfo: String, result: FocusInfoShell): Boolean {
        // 查找启动器特定模式
        if (windowInfo.contains("mCurrentFocus") && windowInfo.contains("Launcher")) {
            val launcherPatterns =
                    listOf(
                            "\\{.*?\\s+([a-zA-Z0-9_.]+\\.launcher)/".toRegex(),
                            "\\{.*?\\s+([a-zA-Z0-9_.]+\\.home)/".toRegex(),
                            "\\{.*?\\s+Launcher\\}".toRegex()
                    )

            for (pattern in launcherPatterns) {
                val match = pattern.find(windowInfo)
                if (match != null && match.groupValues.size >= 2) {
                    result.packageName = match.groupValues[1]
                    result.activityName = "Launcher"
                    Log.d(TAG, "Extracted launcher info")
                    return true
                }
            }

            // 如果我们检测到Launcher但无法提取特定的包，
            // 使用默认的启动器包和名称
            result.packageName = "com.android.launcher3"
            result.activityName = "Launcher"
            Log.d(TAG, "Using default launcher info")
            return true
        }
        return false
    }

    /**
     * 从topActivity/topResumedActivity输出格式提取
     * 例如：topActivity=ComponentInfo{com.example.app/.MainActivity}
     */
    private fun extractFromTopActivityShell(windowInfo: String, result: FocusInfoShell): Boolean {
        // topActivity格式的模式
        val topActivityPatterns =
                listOf(
                        "topActivity=ComponentInfo\\{([a-zA-Z0-9_.]+)/\\.?([^}]+)\\}".toRegex(),
                        "topResumedActivity=ComponentInfo\\{([a-zA-Z0-9_.]+)/\\.?([^}]+)\\}".toRegex(),
                        "ResumedActivity:\\s+\\{([a-zA-Z0-9_.]+)/\\.?([^\\s}]+)".toRegex()
                )

        for (pattern in topActivityPatterns) {
            val match = pattern.find(windowInfo)
            if (match != null && match.groupValues.size >= 3) {
                result.packageName = match.groupValues[1]
                result.activityName = match.groupValues[2]
                Log.d(TAG, "Extracted from topActivity pattern: ${pattern.pattern}")
                return true
            }
        }

        // 还要查找Recent tasks格式
        val recentPattern = "Recent #0.*?\\{([a-zA-Z0-9_.]+)/\\.?([^\\s}]+)".toRegex()
        val recentMatch = recentPattern.find(windowInfo)
        if (recentMatch != null && recentMatch.groupValues.size >= 3) {
            result.packageName = recentMatch.groupValues[1]
            result.activityName = recentMatch.groupValues[2]
            Log.d(TAG, "Extracted from Recent tasks pattern")
            return true
        }

        return false
    }

    /** 作为后备使用更通用的模式提取 */
    private fun extractUsingGenericPatternsShell(
            windowInfo: String,
            result: FocusInfoShell
    ): Boolean {
        var foundAny = false

        // 尝试用各种模式提取包名
        if (result.packageName == null) {
            // 查找常见的包模式，如com.android.something
            val packagePatterns =
                    listOf(
                            "\\s([a-zA-Z][a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+){2,})/".toRegex(), // com.example.app/
                            "\\s([a-zA-Z][a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+){2,})\\s".toRegex(), // com.example.app (空格后)
                            "([a-zA-Z][a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+){2,})".toRegex() // 只查找任何包名类似的名称
                    )

            for (pattern in packagePatterns) {
                val match = pattern.find(windowInfo)
                if (match != null && match.groupValues.size >= 2) {
                    val potentialPackage = match.groupValues[1]
                    // 验证这看起来像一个真实的包（避免匹配随机字符串）
                    if (potentialPackage.split(".").size >= 3 &&
                                    !potentialPackage.contains("@") &&
                                    !potentialPackage.startsWith("1") &&
                                    !potentialPackage.startsWith("0")
                    ) {

                        result.packageName = potentialPackage
                        foundAny = true
                        Log.d(TAG, "Found package name using fallback pattern: ${pattern.pattern}")
                        break
                    }
                }
            }
        }

        // 如果我们还没有活动名称，尝试提取它
        if (result.activityName == null) {
            // 查找活动名称模式
            val activityPatterns =
                    listOf(
                            "/\\.?([A-Z][a-zA-Z0-9_]+Activity)".toRegex(), // /.MainActivity or
                            // /MainActivity
                            "/([^\\s/}]+)".toRegex(), // 斜杠后的任何段
                            "\\.([A-Z][a-zA-Z0-9_]+)".toRegex() // .MainActivity
                    )

            for (pattern in activityPatterns) {
                val match = pattern.find(windowInfo)
                if (match != null && match.groupValues.size >= 2) {
                    val activityName = match.groupValues[1]
                    // 验证它看起来像一个活动名称（以大写字母开头）
                    if (activityName.isNotEmpty() &&
                                    activityName[0].isUpperCase() &&
                                    !activityName.contains("@")
                    ) {

                        result.activityName = activityName
                        foundAny = true
                        Log.d(TAG, "Found activity name using fallback pattern: ${pattern.pattern}")
                        break
                    }
                }
            }
        }

        // 特殊情况处理：如果我们有包名但没有活动名称
        if (result.packageName != null && result.activityName == null) {
            // 尝试根据包猜测主活动名称
            val packageParts = result.packageName!!.split(".")
            if (packageParts.isNotEmpty()) {
                val lastPart = packageParts.last().capitalize()
                result.activityName = "${lastPart}Activity"
                Log.d(TAG, "Guessed activity name from package: ${result.activityName}")
                foundAny = true
            }
        }

        // 如果我们找到了包或活动，将其视为部分成功
        return foundAny
    }

    /** 使用uiautomator查找元素 */
    private suspend fun findElementWithUiautomator(tool: AITool): ToolResult {
        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val text = tool.parameters.find { it.name == "text" }?.value
        val contentDesc = tool.parameters.find { it.name == "contentDesc" }?.value
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull()

        try {
            // 使用uiautomator dump命令获取UI层次结构
            Log.d(TAG, "Using uiautomator dump to find element")
            val dumpCommand = "uiautomator dump /sdcard/window_dump.xml"
            val process = Runtime.getRuntime().exec(dumpCommand)
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to execute UI dump command. Exit code: $exitCode"
                )
            }

            // 读取dump的XML文件
            val catCommand = "cat /sdcard/window_dump.xml"
            val catProcess = Runtime.getRuntime().exec(catCommand)
            val xmlContent = catProcess.inputStream.bufferedReader().use { it.readText() }
            catProcess.waitFor()

            if (xmlContent.isBlank()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "UI dump file is empty or could not be read."
                )
            }

            // 解析XML获取所有匹配的元素
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            val matchedElements = mutableListOf<SimplifiedUINode>()
            var depth = 0

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        depth++
                        val nodeName = parser.name
                        if (nodeName == "node") {
                            val nodeResourceId = parser.getAttributeValue(null, "resource-id") ?: ""
                            val nodeClassName =
                                    parser.getAttributeValue(null, "class")?.substringAfterLast('.')
                                            ?: ""
                            val nodeText =
                                    parser.getAttributeValue(null, "text")?.replace("&#10;", "\n")
                                            ?: ""
                            val nodeContentDesc =
                                    parser.getAttributeValue(null, "content-desc") ?: ""
                            val nodeBounds = parser.getAttributeValue(null, "bounds") ?: ""
                            val nodeEnabled = parser.getAttributeValue(null, "enabled") == "true"
                            val nodeClickable =
                                    parser.getAttributeValue(null, "clickable") == "true"

                            // 检查是否匹配搜索条件
                            var matches = true
                            val partialMatch =
                                    tool.parameters
                                            .find { it.name == "partialMatch" }
                                            ?.value
                                            ?.toBoolean()
                                            ?: false

                            if (resourceId != null &&
                                            (partialMatch && !nodeResourceId.contains(resourceId) ||
                                                    !partialMatch && nodeResourceId != resourceId)
                            ) {
                                matches = false
                            }
                            if (matches &&
                                            className != null &&
                                            (partialMatch && !nodeClassName.contains(className) ||
                                                    !partialMatch && nodeClassName != className)
                            ) {
                                matches = false
                            }
                            if (matches &&
                                            text != null &&
                                            (partialMatch && !nodeText.contains(text) ||
                                                    !partialMatch && nodeText != text)
                            ) {
                                matches = false
                            }
                            if (matches &&
                                            contentDesc != null &&
                                            (partialMatch &&
                                                    !nodeContentDesc.contains(contentDesc) ||
                                                    !partialMatch && nodeContentDesc != contentDesc)
                            ) {
                                matches = false
                            }

                            if (matches) {
                                val currentNode =
                                        SimplifiedUINode(
                                                resourceId = nodeResourceId,
                                                className = nodeClassName,
                                                text = nodeText,
                                                contentDesc = nodeContentDesc,
                                                bounds = nodeBounds,
                                                isClickable = nodeClickable,
                                                children = emptyList()
                                        )
                                matchedElements.add(currentNode)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        depth--
                    }
                }
                eventType = parser.next()
            }

            // 如果找到了匹配的元素
            if (matchedElements.isNotEmpty()) {
                // 如果指定了索引，则返回该索引处的元素
                val targetElement =
                        if (index != null && index >= 0 && index < matchedElements.size) {
                            matchedElements[index]
                        } else {
                            // 否则返回第一个匹配的元素
                            matchedElements[0]
                        }

                // 将元素转换为JSON
                val elementJson = JSONObject()
                elementJson.put("resourceId", targetElement.resourceId)
                elementJson.put("className", targetElement.className)
                elementJson.put("text", targetElement.text)
                elementJson.put("contentDesc", targetElement.contentDesc)
                elementJson.put("bounds", targetElement.bounds)
                elementJson.put("isClickable", targetElement.isClickable)

                // 还要返回找到的所有元素数量
                val resultJson = JSONObject()
                resultJson.put("element", elementJson)
                resultJson.put("totalMatches", matchedElements.size)

                // 清理临时文件
                Runtime.getRuntime().exec("rm /sdcard/window_dump.xml")

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData(resultJson.toString()),
                        error = ""
                )
            } else {
                // 清理临时文件
                Runtime.getRuntime().exec("rm /sdcard/window_dump.xml")

                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "No matching elements found."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding element with uiautomator", e)
            // 尝试清理临时文件
            try {
                Runtime.getRuntime().exec("rm /sdcard/window_dump.xml")
            } catch (cleanupEx: Exception) {
                Log.e(TAG, "Error cleaning up temp file", cleanupEx)
            }

            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error finding element: ${e.message ?: "Unknown exception"}"
            )
        }
    }

    /** 使用uiautomator点击元素 */
    private suspend fun clickElementWithUiautomator(tool: AITool): ToolResult {
        Log.d(TAG, "Using uiautomator to click element")

        val resourceId = tool.parameters.find { it.name == "resourceId" }?.value
        val className = tool.parameters.find { it.name == "className" }?.value
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull() ?: 0

        try {
            // 先尝试获取UI dump
            Log.d(TAG, "Dumping UI hierarchy to find element")
            val dumpCommand = "uiautomator dump /sdcard/window_dump.xml"
            val result = AndroidShellExecutor.executeShellCommand(dumpCommand)

            if (!result.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to dump UI hierarchy: ${result.stderr ?: "Unknown error"}"
                )
            }

            // 读取dump文件
            val readCommand = "cat /sdcard/window_dump.xml"
            val readResult = AndroidShellExecutor.executeShellCommand(readCommand)

            if (!readResult.success) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to read UI dump: ${readResult.stderr ?: "Unknown error"}"
                )
            }

            val xml = readResult.stdout

            // 在XML中查找匹配的元素
            val partialMatch =
                    tool.parameters.find { it.name == "partialMatch" }?.value?.toBoolean() ?: false

            // 构建搜索模式
            val resourceIdPattern =
                    if (resourceId != null) {
                        if (partialMatch)
                                "resource-id=\".*?${Regex.escape(resourceId)}.*?\"".toRegex()
                        else "resource-id=\"(?:.*?:id/)?${Regex.escape(resourceId)}\"".toRegex()
                    } else null

            val classNamePattern =
                    if (className != null) {
                        if (partialMatch) "class=\".*?${Regex.escape(className)}.*?\"".toRegex()
                        else "class=\".*?${Regex.escape(className)}\"".toRegex()
                    } else null

            val boundsPattern = "bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"".toRegex()

            // 提取节点元素
            val nodeRegexPattern = StringBuilder("<node[^>]*?")
            if (resourceIdPattern != null)
                    nodeRegexPattern.append(".*?${resourceIdPattern.pattern}")
            if (classNamePattern != null) nodeRegexPattern.append(".*?${classNamePattern.pattern}")
            nodeRegexPattern.append("[^>]*?>")

            val nodeRegex = nodeRegexPattern.toString().toRegex()
            val matchingNodes = nodeRegex.findAll(xml).toList()

            if (matchingNodes.isEmpty()) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "No matching element found."
                )
            }

            if (index < 0 || index >= matchingNodes.size) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error =
                                "Index out of range. Found ${matchingNodes.size} elements, but requested index $index."
                )
            }

            // 获取指定索引的节点
            val nodeText = matchingNodes[index].value

            // 提取边界坐标
            val boundsMatch = boundsPattern.find(nodeText)
            if (boundsMatch == null || boundsMatch.groupValues.size < 5) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to extract bounds from the element."
                )
            }

            // 提取坐标
            val x1 = boundsMatch.groupValues[1].toInt()
            val y1 = boundsMatch.groupValues[2].toInt()
            val x2 = boundsMatch.groupValues[3].toInt()
            val y2 = boundsMatch.groupValues[4].toInt()

            // 计算中心点
            val centerX = (x1 + x2) / 2
            val centerY = (y1 + y2) / 2

            // 执行点击（在主线程上显示反馈）
            withContext(Dispatchers.Main) { operationOverlay.showTap(centerX, centerY) }

            val tapCommand = "input tap $centerX $centerY"
            val tapResult = AndroidShellExecutor.executeShellCommand(tapCommand)

            if (tapResult.success) {
                val identifierDescription =
                        when {
                            resourceId != null -> " with resource ID: $resourceId"
                            else -> " with class name: $className"
                        }

                val matchCount =
                        if (matchingNodes.size > 1) {
                            " (index $index of ${matchingNodes.size} matches)"
                        } else ""

                return ToolResult(
                        toolName = tool.name,
                        success = true,
                        result =
                                UIActionResultData(
                                        actionType = "click",
                                        actionDescription =
                                                "Successfully clicked element$identifierDescription$matchCount at coordinates ($centerX, $centerY) via shell command",
                                        coordinates = Pair(centerX, centerY),
                                        elementId = resourceId ?: className
                                ),
                        error = ""
                )
            } else {
                withContext(Dispatchers.Main) {
                    operationOverlay.hide() // 隐藏反馈（在主线程上执行）
                }
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to click element: ${tapResult.stderr ?: "Unknown error"}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clicking element with uiautomator", e)
            withContext(Dispatchers.Main) {
                operationOverlay.hide() // 隐藏反馈（在主线程上执行）
            }
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error clicking element: ${e.message ?: "Unknown exception"}"
            )
        } finally {
            // 清理临时文件
            try {
                Runtime.getRuntime().exec("rm /sdcard/window_dump.xml")
            } catch (cleanupEx: Exception) {
                Log.e(TAG, "Error cleaning up temp file", cleanupEx)
            }
        }
    }

    /** 从边界字符串提取中心坐标 返回中心点坐标，或null如果格式无效 */
    protected fun extractCenterCoordinates(bounds: String): Pair<Int, Int>? {
        val boundsPattern = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
        val matchResult = boundsPattern.find(bounds) ?: return null

        if (matchResult.groupValues.size < 5) return null

        // 提取坐标
        val x1 = matchResult.groupValues[1].toInt()
        val y1 = matchResult.groupValues[2].toInt()
        val x2 = matchResult.groupValues[3].toInt()
        val y2 = matchResult.groupValues[4].toInt()

        // 计算并返回中心点
        val centerX = (x1 + x2) / 2
        val centerY = (y1 + y2) / 2

        return Pair(centerX, centerY)
    }
}
