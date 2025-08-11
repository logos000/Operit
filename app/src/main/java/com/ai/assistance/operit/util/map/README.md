# 图和路径搜索模块 (Map Utils)

## 📋 概述

这个模块提供了强大的图数据结构和路径搜索功能，支持两种不同的图类型：

1. **标准图 (Standard Graph)** - 传统的节点-边图结构
2. **带状态图 (Stateful Graph)** - 支持节点状态变异的高级图结构

## 🏗️ 模块结构

### 核心组件

#### 标准图组件
- `Node.kt` - 图节点定义
- `Edge.kt` - 图边定义
- `Graph.kt` - 图数据结构
- `GraphBuilder.kt` - 图构建器和搜索器
- `Path.kt` - 路径表示和结果
- `PathFinder.kt` - 路径搜索算法

#### 带状态图组件

##### `NodeState.kt` - 节点状态定义

**作用**: 表示一个“角色”或“系统”在某个特定节点（地点）的瞬时状态。它不仅包含位置信息，还携带了一个可变的键值对集合，用于描述角色的属性（如：`拥有钥匙`、`生命值`、`任务阶段`等）。

**核心接口**:
- `nodeId: String`: 当前所在的节点ID。
- `variables: Map<String, Any>`: 存储状态变量的只读Map。
- `getVariable<T>(key: String): T?`: 安全地获取指定类型的变量值。
- `withVariable(key: String, value: Any): NodeState`: **返回一个新**的、更新了单个变量的`NodeState`实例（不可变性）。
- `withVariables(newVariables: Map<String, Any>): NodeState`: **返回一个新**的、更新了多个变量的`NodeState`实例。
- `getStateKey(): String`: 生成一个基于节点ID和所有变量的唯一字符串，用于在搜索中去重，避免重复访问完全相同的状态。

##### `StateTransform.kt` - 状态转换规则

**作用**: 定义了当通过一条边（执行一个动作）时，`NodeState`应该如何变化。它是状态变化的“引擎”。这是一个密封类，提供了多种预设的转换逻辑。

**核心接口 (伴生对象方法)**:
- `StateTransform.set(key, value)`: 设置一个变量的值。
- `StateTransform.remove(key)`: 移除一个变量。
- `StateTransform.compute(key, (NodeState) -> Any?)`: 根据当前状态计算一个新变量的值。例如，`hp = hp - 10`。
- `StateTransform.conditionalSet((NodeState) -> Boolean, key, value)`: 仅当满足某个条件时，才设置变量的值。
- `StateTransform.composite(...)`: 将多个转换组合成一个原子操作，按顺序执行。

##### `StatefulEdge.kt` - 带状态转换的边

**作用**: 代表一个“有条件的动作”或“规则路径”。它连接两个节点，并且包含一个`StateTransform`。当一个`NodeState`尝试“通过”这条边时，它的状态会根据`stateTransform`发生改变。

**核心接口**:
- `from: String`, `to: String`: 边的起始和目标节点。
- `action: String`: 描述这个动作（例如：“开门”、“拾取物品”）。
- `conditions: Set<String>`: 通过这条边需要的**外部条件**。例如，门需要钥匙，这个条件可以由玩家的`NodeState`中的`has_key`变量来满足。
- `stateTransform: StateTransform`: 当通过这条边时，应用到`NodeState`上的状态转换规则。
- `applyTransform(fromState, availableConditions)`: 尝试应用转换，如果满足所有条件，则返回一个新的、位于`to`节点的`NodeState`。

##### `StatefulGraph.kt` & `StatefulGraphBuilder.kt` - 图的构建与存储

**作用**: `StatefulGraph`是存储所有节点和`StatefulEdge`的容器。`StatefulGraphBuilder`则提供了一个流畅的API（链式调用）来方便地定义和构建这个图。

**`StatefulGraphBuilder`核心接口**:
- `addNode(id, ...)`: 添加一个地点。
- `addStatefulEdge(from, to, action, ...)`: 添加一个动作/路径，可以指定其`StateTransform`和`conditions`。
- 提供了多种便利方法，如`addSetVariableEdge`、`addComputeVariableEdge`等，简化`StateTransform`的创建。
- `build(): StatefulGraph`: 完成构建并返回图。
- `buildWithFinder(): StatefulGraphSearcher`: 构建图并直接返回一个附加的搜索器，方便立即进行路径查找。

##### `StatefulPathFinder.kt` - 带状态的路径搜索

**作用**: 这是整个库的“大脑”。它接收一个初始`NodeState`和一个目标，然后根据`StatefulGraph`中定义的规则，搜索出一条能够达成目标的、由一系列动作（`StatefulEdge`）和状态变化（`NodeState`）组成的有效路径。

**核心接口**:
- `findPath(startState, targetNodeId, targetStatePredicate, ...)`: 最核心的搜索方法。
  - `startState: NodeState`: 搜索的起点状态。
  - `targetNodeId: String`: 最终路径必须到达的**节点ID**。
  - `targetStatePredicate: ((NodeState) -> Boolean)?`: 一个可选的附加条件，最终状态必须满足这个断言。例如，`{ it.getVariable<Boolean>("任务完成") == true }`。
  - `availableConditions: Set<String>`: 在搜索开始时提供的外部条件。
  - `enableBacktrack: Boolean`: 是否启用回退搜索。对于复杂的状态依赖（如先拿A才能做B，但B失败了需要放弃A），回退是必需的。

##### `StatefulPath.kt` & `StatefulPathResult.kt` - 路径和结果

**作用**: `StatefulPath`是搜索的最终产物，它不仅仅是一个节点列表，而是一个详细的**状态序列**，记录了从起点到终点每一步的状态是如何变化的。`StatefulPathResult`则包装了搜索是否成功以及最终的路径。

**`StatefulPath`核心接口**:
- `states: List<NodeState>`: 路径上每一个步骤的状态快照列表。
- `edges: List<StatefulEdge>`: 路径上按顺序执行的边的列表。
- `startState: NodeState`, `endState: NodeState`: 路径的起始和最终状态。
- `isValid()`: 验证路径中的每一步状态转换是否都合法。

## 🚀 快速开始

### 标准图使用

#### 1. 创建简单图

```kotlin
val graph = GraphBuilder.create()
    .addNode("A", "起始点")
    .addNode("B", "中转点")
    .addNode("C", "目标点")
    .addEdge("A", "B", "移动", weight = 1.0)
    .addEdge("B", "C", "移动", weight = 2.0)
    .build()
```

#### 2. 路径搜索

```kotlin
val searcher = GraphBuilder.create()
    .addNode("A")
    .addNode("B")
    .addNode("C")
    .addBidirectionalEdge("A", "B", "连接")
    .addEdge("B", "C", "到达")
    .buildWithFinder()

val result = searcher.findShortestPath("A", "C")
if (result.success) {
    println("找到路径: ${result.path}")
    println("总权重: ${result.path?.totalWeight}")
}
```

#### 3. 条件边

```kotlin
val graph = GraphBuilder.create()
    .addNode("房间1")
    .addNode("房间2")
    .addEdge("房间1", "房间2", "开门", 
        conditions = setOf("有钥匙"))
    .buildWithFinder()

// 搜索时提供条件
val result = graph.findShortestPath("房间1", "房间2", 
    conditions = setOf("有钥匙"))
```

### 带状态图使用

#### 1. 基本状态图

```kotlin
val statefulGraph = StatefulGraphBuilder.create()
    .addNode("开始")
    .addNode("结束")
    .addSetVariableEdge("开始", "结束", "设置分数", "score", 100)
    .buildWithFinder()

val startState = NodeState("开始")
val result = statefulGraph.findPathTo("开始", "结束")

if (result.success) {
    val finalState = result.path!!.endState
    println("最终分数: ${finalState.getVariable<Int>("score")}")
}
```

#### 2. 复杂状态转换

```kotlin
val gameWorld = StatefulGraphBuilder.create()
    .addNode("村庄")
    .addNode("森林")
    .addNode("宝藏")
    
    // 复合状态转换：获得武器和经验
    .addCompositeTransformEdge("村庄", "森林", "探险", listOf(
        StateTransform.set("weapon", "剑"),
        StateTransform.set("experience", 10)
    ))
    
    // 条件转换：需要武器才能获得宝藏
    .addConditionalSetEdge("森林", "宝藏", "寻宝",
        condition = { state -> state.hasVariable("weapon") },
        "treasure", "黄金")
    
    .buildWithFinder()
```

#### 3. 变量计算

```kotlin
val calculator = StatefulGraphBuilder.create()
    .addNode("输入")
    .addNode("处理")
    .addNode("输出")
    
    // 计算总和
    .addComputeVariableEdge("输入", "处理", "相加", "sum") { state ->
        val a = state.getVariable<Int>("a") ?: 0
        val b = state.getVariable<Int>("b") ?: 0
        a + b
    }
    
    .buildWithFinder()

val startState = NodeState("输入", mapOf("a" to 10, "b" to 20))
val result = calculator.findPath(startState, "处理")
// 结果状态将包含 sum = 30
```

## 🎮 实际应用场景

### 1. RPG游戏系统

```kotlin
val gameWorld = StatefulGraphBuilder.create()
    // 游戏世界
    .addNode("village", "村庄")
    .addNode("forest", "森林")
    .addNode("dungeon", "地牢")
    .addNode("boss_room", "Boss房间")
    
    // 获得装备
    .addStatefulEdge("forest", "forest", "找到装备",
        StateTransform.composite(
            StateTransform.set("weapon", "魔法剑"),
            StateTransform.compute("attack") { state ->
                (state.getVariable<Int>("attack") ?: 10) + 50
            }
        ), 0.0, setOf("探索森林"))
    
    // 进入地牢需要足够攻击力
    .addStatefulEdge("forest", "dungeon", "进入地牢",
        StateTransform.IDENTITY, 1.0, setOf("攻击力足够"))
    
    // 击败Boss
    .addStatefulEdge("dungeon", "boss_room", "挑战Boss",
        StateTransform.composite(
            StateTransform.set("boss_defeated", true),
            StateTransform.compute("gold") { state ->
                (state.getVariable<Int>("gold") ?: 0) + 1000
            }
        ), 5.0, setOf("准备充分"))
    
    .buildWithFinder()

// 玩家状态
val player = NodeState("village", mapOf(
    "level" to 1,
    "attack" to 10,
    "gold" to 100
))

// 寻找击败Boss的路径
val questResult = gameWorld.findPath(player, "boss_room",
    conditions = setOf("探索森林", "攻击力足够", "准备充分"))
```

### 2. 工作流管理

```kotlin
val workflow = StatefulGraphBuilder.create()
    .addNode("draft", "草稿")
    .addNode("review", "审核")
    .addNode("approved", "已批准")
    .addNode("published", "已发布")
    
    // 提交审核
    .addStatefulEdge("draft", "review", "提交审核",
        StateTransform.composite(
            StateTransform.set("submitted_at", System.currentTimeMillis()),
            StateTransform.set("status", "pending")
        ), 1.0, setOf("内容完整"))
    
    // 审核通过
    .addStatefulEdge("review", "approved", "审核通过",
        StateTransform.composite(
            StateTransform.set("approved_by", "管理员"),
            StateTransform.set("approved_at", System.currentTimeMillis())
        ), 1.0, setOf("审核权限"))
    
    // 发布
    .addStatefulEdge("approved", "published", "发布",
        StateTransform.set("published_at", System.currentTimeMillis()),
        1.0, setOf("发布权限"))
    
    .buildWithFinder()
```

### 3. 导航系统

```kotlin
val navigation = GraphBuilder.create()
    .addNode("home", "家")
    .addNode("office", "办公室")
    .addNode("gym", "健身房")
    .addNode("store", "商店")
    
    // 添加路径和时间
    .addBidirectionalEdge("home", "office", "开车", weight = 15.0)
    .addBidirectionalEdge("office", "store", "步行", weight = 5.0)
    .addBidirectionalEdge("home", "gym", "跑步", weight = 10.0)
    .addEdge("gym", "store", "购物", weight = 8.0)
    
    .buildWithFinder()

// 寻找最短路径
val route = navigation.findShortestPath("home", "store")
println("最短时间: ${route.path?.totalWeight}分钟")
```

## 🔧 高级功能

### 1. 自定义启发式搜索 (A*)

```kotlin
val pathFinder = PathFinder(graph)

// 提供启发式函数
val result = pathFinder.findPathWithHeuristic(
    "start", "goal",
    heuristic = { nodeId -> 
        // 估算到目标的距离
        when(nodeId) {
            "goal" -> 0.0
            "near_goal" -> 1.0
            else -> 5.0
        }
    }
)
```

### 2. 多路径搜索

```kotlin
val allPaths = pathFinder.findAllPaths(
    "start", "goal",
    maxDepth = 10,
    maxPaths = 5
)

allPaths.forEach { path ->
    println("路径: ${path.nodes.joinToString(" -> ")}")
    println("权重: ${path.totalWeight}")
}
```

### 3. 状态回退搜索

```kotlin
// 启用回退功能，处理状态冲突
val result = statefulFinder.findPath(
    startState, "target",
    enableBacktrack = true
)

if (result.success) {
    println("回退次数: ${result.backtrackCount}")
    println("搜索统计: ${result.searchStats}")
}
```

### 4. 便利构建方法

```kotlin
val graph = StatefulGraphBuilder.create()
    // 创建变量传递链
    .createVariableChain(
        listOf("A", "B", "C", "D"), 
        "传递", "token", "secret123"
    )
    
    // 创建累加器链
    .createAccumulatorChain(
        listOf("start", "step1", "step2", "end"),
        "累加", "counter", increment = 2
    )
    
    .buildWithFinder()
```

## 📊 性能特性

- **Dijkstra算法** - 标准最短路径搜索
- **A*算法** - 带启发式的高效搜索
- **回退搜索** - 处理状态冲突的深度优先搜索
- **状态去重** - 避免重复访问相同状态
- **距离限制** - 防止过度搜索
- **搜索统计** - 性能监控和调试

## 🧪 测试

模块包含完整的测试套件：

- `NodeStateTest` - 节点状态测试
- `StatefulEdgeTest` - 状态边测试
- `StatefulPathTest` - 状态路径测试
- `StatefulGraphBuilderTest` - 图构建器测试
- `StatefulPathFinderTest` - 路径搜索测试
- `StatefulGraphIntegrationTest` - 集成测试

运行测试：
```bash
./gradlew connectedAndroidTest --tests "*.map.*"
```

## 📈 扩展性

### 自定义状态转换

```kotlin
class CustomTransform : StateTransform() {
    override fun canApply(state: NodeState): Boolean {
        // 自定义条件检查
        return true
    }
    
    override fun apply(state: NodeState): NodeState? {
        // 自定义状态转换逻辑
        return state.withVariable("custom", "value")
    }
}
```

### 自定义搜索算法

```kotlin
class CustomPathFinder(graph: StatefulGraph) {
    fun customSearch(/* parameters */): StatefulPathResult {
        // 实现自定义搜索逻辑
        return StatefulPathResult.success(path)
    }
}
```

## 🎯 最佳实践

1. **状态设计** - 保持状态简单和不可变
2. **条件使用** - 合理使用边条件避免无效路径
3. **权重设置** - 根据实际成本设置边权重
4. **深度限制** - 设置合理的搜索深度避免无限循环
5. **状态键** - 确保状态键能正确区分不同状态
6. **回退策略** - 在复杂状态空间中启用回退搜索

## 🔍 调试技巧

```kotlin
// 启用详细日志
val result = finder.findPath(start, target)
if (result.searchStats != null) {
    println("访问节点: ${result.searchStats.visitedNodes}")
    println("探索边数: ${result.searchStats.exploredEdges}")
    println("搜索时间: ${result.searchStats.searchTimeMs}ms")
    println("算法: ${result.searchStats.algorithm}")
}

// 验证路径有效性
if (result.success && result.path != null) {
    println("路径有效: ${result.path.isValid()}")
    println("状态冲突: ${result.path.hasStateConflicts()}")
}
```

## 📚 相关资源

- 图论基础知识
- Dijkstra算法原理
- A*搜索算法
- 状态空间搜索
- 游戏AI路径规划 