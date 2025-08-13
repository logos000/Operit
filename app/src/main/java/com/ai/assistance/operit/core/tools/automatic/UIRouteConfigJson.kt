package com.ai.assistance.operit.core.tools.automatic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable data classes for parsing UI route configuration from JSON.
 * These classes mirror the structure of the JSON file and are used for deserialization.
 */

@Serializable
data class JsonUIRouteConfig(
    val appName: String,
    val packageName: String,
    val nodes: List<JsonUINode>,
    val edges: List<JsonUIEdge>,
    val functions: List<JsonUIFunction>
)

@Serializable
data class JsonUINode(
    val name: String,
    val description: String,
    val activityName: String? = null,
    val nodeType: String // e.g., "APP_HOME", "LIST_PAGE"
)

@Serializable
data class JsonUIEdge(
    val from: String,
    val to: String,
    val operation: JsonUIOperation,
    val validation: JsonUIOperation? = null,
    val conditions: Set<String> = emptySet(),
    val weight: Double = 1.0
)

@Serializable
data class JsonUIFunction(
    val name: String,
    val description: String,
    val targetNodeName: String,
    val operation: JsonUIOperation? = null // Functions might not have a direct operation
)

@Serializable
data class JsonUISelector(
    val type: String, // "ByText", "ByResourceId", "ByClassName"
    val value: String,
    val partialMatch: Boolean = false
)

/**
 * Defines a polymorphic base class for UI operations.
 * The @SerialName annotation maps the "type" field in JSON to the corresponding subclass.
 */
@Serializable
sealed class JsonUIOperation {
    abstract val description: String?

    @Serializable
    @SerialName("Click")
    data class Click(
        val selector: JsonUISelector,
        override val description: String? = null,
        val relativeX: Float? = null,
        val relativeY: Float? = null
    ) : JsonUIOperation()

    @Serializable
    @SerialName("Input")
    data class Input(
        val selector: JsonUISelector,
        val textVariableKey: String,
        override val description: String? = null
    ) : JsonUIOperation()

    @Serializable
    @SerialName("LaunchApp")
    data class LaunchApp(
        val packageName: String,
        override val description: String? = null
    ) : JsonUIOperation()

    @Serializable
    @SerialName("Sequential")
    data class Sequential(
        val operations: List<JsonUIOperation>,
        override val description: String? = null
    ) : JsonUIOperation()

    @Serializable
    @SerialName("ValidateElement")
    data class ValidateElement(
        val selector: JsonUISelector,
        val expectedValueKey: String,
        val validationType: String, // "TEXT_EQUALS", "CONTAINS", etc.
        override val description: String? = null
    ) : JsonUIOperation()
} 