# UI自动化路由系统 (UI Automation Routing System)

## 概述: 您的UI智能助理

想象一下，您不是在对机器下达一步步的指令，而是在向一位聪明的助理下达一个**目标**。您不会说“打开微信、点击‘微信’标签、找到张三、点击进去...”，您只会说：“给张三发条微信”。

本框架正是基于这一理念构建的。它将复杂的UI操作流程，抽象成了人类易于理解的**“功能”（Function）**。您只需定义好一个个具体的功能（如“发朋友圈”、“发送邮件”），剩下的交给系统：它会自动感知当前所处的位置，智能规划出到达功能起点的路径，然后无缝衔接执行功能本身的操作，最终完成您的目标。

这是一个从“要怎么做”到“要做什么”的思维转变，让UI自动化变得前所未有的直观和强大。

## 工作原理: 从一个目标到一系列动作

整个系统就像一个高效的自动化厨房，将您的“订单”（目标）变成一道“成品”（完成的UI任务）。

```mermaid
graph TD
    subgraph "定义阶段: 编写菜谱"
        A[UIRouteConfig] -->|定义地点、路径和功能| B((菜谱: App能力地图))
    end

    subgraph "规划阶段: 大厨规划"
        C[用户/AI] -->|下单: "我要发微信"| D{UIRouter: 大厨}
        D -->|1. 查看菜谱| B
        D -->|2. 我现在在哪?| E(AIToolHandler: 感知环境)
        E -->|3. 返回当前UI状态| D
        D -->|4. 规划从'现在'到'聊天页'的路径<br/>+ 附加'输入/发送'操作| F(StatefulGraph: 导航)
        D -->|5. 生成完整计划<br/>并告知需要'聊天对象'和'内容'| G[RoutePlan: 执行计划]
    end
    
    subgraph "执行阶段: 动手执行"
        C -->|6. 提供参数| G
        G -->|7. 开始执行| H(UIOperationExecutor: 执行者)
        H -->|8. 操作UI (点击,输入)| E
        E -->|9. 返回结果/新状态| H
        H -->|10. 验证是否符合预期?| E
        H -->|11. 重复直到任务完成| H
    end

    style A fill:#D5E8D4
    style D fill:#cde4ff
    style G fill:#FFF2CC
    style H fill:#F8CECC
```

1.  **编写“菜谱” (`UIRouteConfig`)**: 首先，我们需要告诉系统一个App能做什么。我们通过 `UIRouteConfig` 来定义：
    *   **地点 (`UINode`)**: App里的各个页面，我们用它独一无二的 `name` 来标识，如“微信主页”、“聊天列表”。
    *   **路径 (`UIEdge`)**: 页面之间的跳转操作，我们用页面的 `name` 来连接起点和终点，比如从“微信主页”可以到达“聊天列表”。这里的操作，例如点击，输入等，都是由 `UIOperation` 定义的，它本身也是一种 `StateTransform`。
    *   **功能 (`UIFunction`)**: 一个完整的、有业务价值的任务。比如“发送微信消息”这个功能，它也用自己的 `name` 作为标识，并定义了**目标地点**是“聊天窗口”，以及**后续操作**是“输入文本”和“点击发送”。

2.  **大厨“规划” (`UIRouter`)**: 当您下达一个“发送微信消息”的指令时，`UIRouter` 作为大厨开始工作：
    *   它首先查看菜谱，通过功能名称“发送微信消息”找到对应的定义，得知需要在“聊天窗口”页面才能执行。
    *   然后它环顾四周，通过 `AIToolHandler` 确认自己当前在哪个页面（比如“系统桌面”）。
    *   接着，它在大脑中的地图（一个 `StatefulGraph`）上规划出一条从“系统桌面”到“聊天窗口”的最佳导航路径。
    *   最后，它将“输入文本”和“点击发送”这两个操作附加到导航路径的末尾，形成一个完整的、端到端的执行计划 `RoutePlan`。
    *   在递上这份计划时，它还会贴心地告诉您：“要完成这个任务，我需要知道‘聊天对象是谁’(`target_user`)以及‘消息内容是什么’(`input_text`)”。

3.  **动手“执行” (`UIOperationExecutor`)**: 一旦您提供了必要的参数，`RoutePlan` 就会被交给 `UIOperationExecutor` 这个一丝不苟的执行者。它会严格按照计划，一步步执行UI操作（点击、输入等），并且每一步操作后都会进行验证，确保UI状态如预期一样发生了改变，极大地保证了任务执行的稳定性和成功率。

## 核心概念一览

*   **`UIFunction` (功能)**: 整个系统的核心与灵魂，代表一个高阶的用户意图。
*   **`UIRouter` (路由器/大脑)**: 负责理解意图，并将其翻译成可执行计划的规划者。
*   **`RoutePlan` (执行计划)**: 由路由器生成的、包含完整路径和所需参数的详细方案。
*   **`UIOperationExecutor` (执行器)**: 负责将计划付诸行动，并确保过程准确无误的实干家。
*   **`StatefulGraph` (状态图)**: 整个路由系统的底层数据结构，它不仅仅是一个静态的图，它的边 (`StatefulEdge`) 带有 `StateTransform`，可以在路径遍历过程中改变状态 (`NodeState`)。
*   **`StateTransform` (状态转换)**: 定义了当图中的边被遍历时，如何修改当前状态的规则。`UIOperation` 是它的一种特殊实现，专门用于UI操作。

## 快速上手

下面是一个完整的使用示例，展示了这套新流程的简洁与强大：

```kotlin
// 1. 初始化路由器
val uiRouter = UIRouter(context, toolHandler)

// 2. 加载应用的“地图”和“功能菜单”
// UIRouteConfig现在通过更灵活的DSL方式构建
val wechatConfig = UIRouteConfig.build {
    // 定义页面节点
    node("wechat_home", "微信主页", "com.tencent.mm")
    node("chat_list", "聊天列表", "com.tencent.mm", "LauncherUI")
    node("chat_window", "聊天窗口", "com.tencent.mm", "ChattingUI")

    // 定义页面跳转的边
    edge("wechat_home", "chat_list") {
        operation = UIOperations.click(UISelector.ByText("微信"))
    }
    edge("chat_list", "chat_window") {
        // 使用模板变量，执行时会被替换
        operation = UIOperations.click(UISelector.ByText("{{target_user}}"))
    }

    // 定义核心功能
    function("发送微信消息", "chat_window") {
        operation = UIOperations.sequential(
            UIOperations.input(UISelector.ByClassName("android.widget.EditText"), "{{input_text}}"),
            UIOperations.click(UISelector.ByText("发送"))
        )
    }
}
uiRouter.loadConfig(wechatConfig)

// 3. 查看有什么可用的功能
val availableFunctions = uiRouter.getAvailableFunctions()
// -> [ "发送微信消息", "发朋友圈", ... ]

// 4. 选择一个功能，让路由器规划如何完成它
//    (无需关心当前在哪，UIRouter会自己看)
val plan = uiRouter.planFunction("发送微信消息")

if (plan != null) {
    // 5. 路由器告诉我们需要哪些信息
    val neededParams = plan.requiredParameters
    // -> [RouteParameter(key=target_user, ...), RouteParameter(key=input_text, ...)]

    // 6. 我们提供这些信息
    val params = mapOf(
        "target_user" to "AI助手小欧",
        "input_text" to "你好，这是来自自动化路由系统的问候！"
    )

    // 7. 执行！
    // 参数检查现在是RoutePlan内部的逻辑
    val result = plan.execute(params)
    if (result.success) {
        println("任务成功!")
    } else {
        println("任务失败: ${result.error}")
    }
}
```

这套以“功能”为中心的架构，使得调用者可以完全聚焦于业务目标，而将所有复杂的UI导航、操作、验证的细节都交给了框架处理。

---

**版本**: 2.1.0  
**最后更新**: 2024年12月  
**维护者**: AI Assistance Team 