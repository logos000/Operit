package com.ai.assistance.operit.core.tools.automatic

import com.ai.assistance.operit.util.map.NodeState
import com.ai.assistance.operit.util.map.StateTransform

/**
 * 定义所有可能的原子UI操作。
 * 每个操作都继承自StateTransform，以便在图的边上使用。
 */
sealed interface UIOperation : StateTransform {
    val description: String
        get() = this.javaClass.simpleName

    override fun apply(state: NodeState, context: Map<String, Any>): NodeState? {
        // By default, UI operations don't change the NodeState directly.
        // The change is handled by the UIOperationExecutor.
        return state
    }

    // 基础UI操作
    data class Click(
        val selector: UISelector,
        override val description: String,
        /** Position relative to the element's width (0.0 to 1.0 from left to right) */
        val relativeX: Float? = null,
        /** Position relative to the element's height (0.0 to 1.0 from top to bottom) */
        val relativeY: Float? = null
    ) : UIOperation

    data class Input(
        val selector: UISelector,
        val textVariableKey: String, // 从状态(State)中获取文本的key
        override val description: String = "输入文本"
    ) : UIOperation

    data class Swipe(
        val direction: SwipeDirection,
        val distance: Int = 500,
        override val description: String = "滑动页面"
    ) : UIOperation

    // 应用级操作
    data class LaunchApp(
        val packageName: String,
        override val description: String = "启动应用"
    ) : UIOperation

    data class KillApp(
        val packageName: String,
        override val description: String = "关闭应用"
    ) : UIOperation

    data class PressKey(
        val keyCode: String,
        override val description: String = "Press a key"
    ) : UIOperation

    data class Wait(
        val durationMs: Long,
        override val description: String = "Wait for ${durationMs}ms"
    ) : UIOperation

    // 流程控制与验证
    data class WaitForPage(
        val timeoutMs: Long = 5000,
        override val description: String = "等待页面加载"
    ) : UIOperation

    data class ValidateState(
        val validator: (UIState) -> Boolean,
        override val description: String = "验证当前状态"
    ) : UIOperation {
        override fun canApply(state: NodeState): Boolean {
            val uiState = state.getVariable<UIState>("current_ui_state")
            return uiState != null && validator(uiState)
        }
    }

    // 复合操作
    data class Sequential(
        val operations: List<UIOperation>,
        override val description: String = "执行一系列操作"
    ) : UIOperation

    // 这是一个No-Op，用于表示状态转换但不执行任何UI操作
    object NoOp : UIOperation {
        override val description: String = "无操作"
    }

    data class ValidateElement(
        val selector: UISelector,
        val expectedValueKey: String,
        val validationType: ValidationType = ValidationType.TEXT_EQUALS,
        override val description: String = "验证元素"
    ) : UIOperation
}

/**
 * 定义验证操作的类型。
 */
enum class ValidationType {
    TEXT_EQUALS,
    TEXT_CONTAINS,
    EXISTS
}

/**
 * 定义UI元素的选择器，用于定位要操作的元素。
 */
sealed class UISelector {
    data class ByResourceId(val id: String) : UISelector()
    data class ByText(val text: String) : UISelector()
    data class ByContentDesc(val desc: String) : UISelector()
    data class ByClassName(val name: String) : UISelector()
    data class ByBounds(val bounds: String) : UISelector()
    data class ByXPath(val xpath: String) : UISelector()
    data class Compound(val selectors: List<UISelector>, val operator: String = "AND") : UISelector()
    
    companion object {
        fun byResourceId(id: String) = ByResourceId(id)
        fun byText(text: String) = ByText(text)
        fun byContentDesc(desc: String) = ByContentDesc(desc)
        fun byClassName(name: String) = ByClassName(name)
        fun byBounds(bounds: String) = ByBounds(bounds)
        fun byXPath(xpath: String) = ByXPath(xpath)
        fun compound(selectors: List<UISelector>, operator: String = "AND") = Compound(selectors, operator)
    }
}

/**
 * 定义滑动的方向。
 */
enum class SwipeDirection {
    UP, DOWN, LEFT, RIGHT
} 