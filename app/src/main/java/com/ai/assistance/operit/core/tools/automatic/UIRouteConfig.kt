package com.ai.assistance.operit.core.tools.automatic

/**
 * UI功能定义。
 * 代表一个高阶的用户目标，例如“发送消息”或“发朋友圈”。
 *
 * @property name 人类可读的功能名称，作为其唯一标识符。
 * @property description 功能的详细描述。
 * @property targetNodeName 执行此功能前必须到达的UI节点的名称。
 * @property operation 到达目标节点后，为完成此功能需要执行的UI操作。
 */
data class UIFunction(
    val name: String,
    val description: String,
    val targetNodeName: String,
    val operation: UIOperation
)

/**
 * UI路由配置中心。
 * 在这里定义所有支持的应用的节点（页面）、边（操作）和功能。
 * 整个配置使用人类可读的名称作为ID，更易于维护。
 */
class UIRouteConfig {
    // 存储节点定义，Key是节点的name
    val nodeDefinitions = mutableMapOf<String, UINode>()
    // 存储边定义，Key是起点的name
    val edgeDefinitions = mutableMapOf<String, MutableList<UIEdgeDefinition>>()
    // 存储功能定义，Key是功能的name
    val functionDefinitions = mutableMapOf<String, UIFunction>()

    /**
     * 定义一个UI节点（页面/状态），使用其name作为唯一标识。
     */
    fun defineNode(node: UINode) {
        nodeDefinitions[node.name] = node
    }

    /**
     * 定义一条边，即一个从起点到终点的UI操作。
     * @param fromNodeName 起始节点的名称。
     * @param toNodeName 目标节点的名称。
     */
    fun defineEdge(fromNodeName: String, toNodeName: String, operation: UIOperation, conditions: Set<String> = emptySet(), weight: Double = 1.0) {
        val edge = UIEdgeDefinition(toNodeName, operation, conditions, weight)
        edgeDefinitions.computeIfAbsent(fromNodeName) { mutableListOf() }.add(edge)
    }

    /**
     * 定义一个高阶功能，使用其name作为唯一标识。
     */
    fun defineFunction(function: UIFunction) {
        functionDefinitions[function.name] = function
    }

    companion object {
        /**
         * 加载一个预设的微信路由配置。
         * 这是一个如何使用名称来定义应用路由的示例。
         */
        fun loadWeChatConfig(): UIRouteConfig {
            val config = UIRouteConfig()

            // 1. 定义节点 (Pages/States) - 使用 name 作为唯一标识
            config.defineNode(UINode(
                name = "系统桌面",
                packageName = "com.android.launcher", // 示例包名，可能需要调整
                nodeType = UINodeType.SYSTEM_PAGE
            ))

            config.defineNode(UINode(
                name = "微信主页",
                packageName = "com.tencent.mm",
                activityName = ".ui.LauncherUI",
                nodeType = UINodeType.APP_HOME
            ))

            config.defineNode(UINode(
                name = "微信聊天列表",
                packageName = "com.tencent.mm",
                nodeType = UINodeType.LIST_PAGE
            ))

            config.defineNode(UINode(
                name = "微信聊天窗口",
                packageName = "com.tencent.mm",
                nodeType = UINodeType.DETAIL_PAGE
            ))

            // 2. 定义边 (Navigation/Actions) - 使用 name 引用节点
            config.defineEdge("系统桌面", "微信主页",
                UIOperation.LaunchApp("com.tencent.mm")
            )

            config.defineEdge("微信主页", "微信聊天列表",
                UIOperation.Click(
                    UISelector.ByText("微信"), // 假设底部Tab的文字是"微信"
                    description = "切换到聊天列表"
                )
            )

            config.defineEdge("微信聊天列表", "微信聊天窗口",
                UIOperation.Click(
                    // 这里使用模板，让执行器动态填充
                    UISelector.ByText("{{target_user}}"),
                    description = "打开与用户的聊天窗口"
                )
            )

            // 3. 定义功能 (Functions) - 使用 name 作为唯一标识
            config.defineFunction(UIFunction(
                name = "发送微信消息",
                description = "导航到指定用户的聊天窗口并发送一条消息。",
                targetNodeName = "微信聊天窗口", // 直接引用目标页面的名字
                operation = UIOperation.Sequential(
                    operations = listOf(
                        UIOperation.Input(
                            UISelector.ByResourceId("com.tencent.mm:id/input_box"), // 假设的资源ID
                            textVariableKey = "input_text",
                            description = "输入消息内容"
                        ),
                        UIOperation.Click(
                            UISelector.ByResourceId("com.tencent.mm:id/send_btn"), // 假设的资源ID
                            description = "点击发送按钮"
                        )
                    ),
                    description = "发送一条完整的消息"
                )
            ))

            return config
        }
    }
}

/**
 * UI节点定义。
 *
 * @property name 节点的唯一名称，例如 "微信主页"。
 * @property packageName 所属应用的包名。
 * @property activityName 关联的Activity名称，可选。
 * @property nodeType 节点的类型。
 */
data class UINode(
    val name: String,
    val packageName: String,
    val activityName: String? = null,
    val nodeType: UINodeType
)

enum class UINodeType {
    APP_HOME, LIST_PAGE, DETAIL_PAGE, DIALOG, INPUT_PAGE, SYSTEM_PAGE
}

/**
 * 边（操作）的定义。
 * @param toNodeName 目标节点的名称。
 */
data class UIEdgeDefinition(
    val toNodeName: String,
    val operation: UIOperation,
    val conditions: Set<String>,
    val weight: Double
) 