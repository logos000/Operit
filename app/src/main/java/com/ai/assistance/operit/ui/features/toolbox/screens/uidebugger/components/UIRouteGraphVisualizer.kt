package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import android.util.Log

@OptIn(ExperimentalTextApi::class)
@Composable
fun UIRouteGraphVisualizer(
    graph: Graph,
    modifier: Modifier = Modifier,
    selectedNodeId: String? = null,
    boxSelectedNodeIds: Set<String> = emptySet(),
    isBoxSelectionMode: Boolean = false,
    linkingNodeIds: List<String> = emptyList(),
    selectedEdgeId: Long? = null,
    onNodeClick: (Node) -> Unit,
    onEdgeClick: (Edge) -> Unit,
    onNodesSelected: (Set<String>) -> Unit
) {
    Log.d("UIRouteGraphVisualizer", "Recomposing. isBoxSelectionMode: $isBoxSelectionMode")
    val textMeasurer = rememberTextMeasurer()
    var nodePositions by remember { mutableStateOf(mapOf<String, Offset>()) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }

    // 当退出框选模式时，确保清除选框
    LaunchedEffect(isBoxSelectionMode) {
        if (!isBoxSelectionMode) {
            selectionRect = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // A more structured, deterministic layout for UI routes without physics simulation
        LaunchedEffect(graph.nodes, width, height) {
            if (graph.nodes.isNotEmpty()) {
                val newPositions = withContext(Dispatchers.Default) {
                    val positions = mutableMapOf<String, Offset>()
                    val nodesByColumn = mutableMapOf<Int, MutableList<Node>>()
                    val roots = graph.nodes.filter { n -> graph.edges.none { e -> e.targetId == n.id } }
                        .ifEmpty { graph.nodes.take(1) }
                    
                    val visited = mutableSetOf<String>()
                    val queue = ArrayDeque<Pair<Node, Int>>() // Node and column index

                    roots.forEach { 
                        queue.add(it to 0) 
                        visited.add(it.id)
                    }

                    while(queue.isNotEmpty()){
                        val (node, col) = queue.removeFirst()
                        nodesByColumn.getOrPut(col) { mutableListOf() }.add(node)

                        graph.edges.filter { it.sourceId == node.id }.forEach { edge ->
                            if (edge.targetId !in visited) {
                                graph.nodes.find { it.id == edge.targetId }?.let { targetNode ->
                                    queue.add(targetNode to col + 1)
                                    visited.add(targetNode.id)
                                }
                            }
                        }
                    }

                    // Position unvisited nodes (cycles, etc.) in separate columns
                    var extraCol = (nodesByColumn.keys.maxOfOrNull { it } ?: -1) + 1
                    val remainingNodes = graph.nodes.filter { it.id !in visited }
                    if (remainingNodes.isNotEmpty()) {
                         nodesByColumn.getOrPut(extraCol) { mutableListOf() }.addAll(remainingNodes)
                    }

                    val columnCount = nodesByColumn.keys.size
                    val columnWidth = if (columnCount > 0) width / columnCount else width
                    
                    nodesByColumn.forEach { (col, nodes) ->
                        val x = (columnWidth / 2) + (col * columnWidth)
                        val rowHeight = height / (nodes.size + 1)
                        nodes.forEachIndexed { index, node ->
                            val y = rowHeight * (index + 1)
                            positions[node.id] = Offset(x, y.toFloat())
                        }
                    }
                    positions
                }
                
                withContext(Dispatchers.Main) {
                    nodePositions = newPositions
                }
            } else {
                withContext(Dispatchers.Main) {
                    nodePositions = emptyMap()
                }
            }
        }

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(isBoxSelectionMode) {
                Log.d("UIRouteGraphVisualizer", "pointerInput recomposed. Mode: ${if (isBoxSelectionMode) "BoxSelect" else "Normal"}")
                coroutineScope {
                    if (isBoxSelectionMode) {
                        // Box selection mode gestures
                        launch {
                            var dragStart: Offset? = null
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    dragStart = startOffset
                                    selectionRect = createNormalizedRect(startOffset, startOffset)
                                },
                                onDrag = { change, _ ->
                                    dragStart?.let { start ->
                                        selectionRect = createNormalizedRect(start, change.position)
                                    }
                                },
                                onDragEnd = {
                                    selectionRect?.let { rect ->
                                        val selectedIds = nodePositions.filter { (_, pos) ->
                                            val viewPos = pos * scale + offset
                                            rect.contains(viewPos)
                                        }.keys
                                        onNodesSelected(selectedIds)
                                    }
                                    selectionRect = null
                                    dragStart = null
                                }
                            )
                        }
                        launch {
                            detectTapGestures(onTap = { tapOffset ->
                                val clickedNode = graph.nodes.findLast { node ->
                                    nodePositions[node.id]?.let { pos ->
                                        val viewPos = pos * scale + offset
                                        (tapOffset - viewPos).getDistance() <= 60f * scale
                                    } ?: false
                                }
                                if (clickedNode != null) {
                                    onNodeClick(clickedNode)
                                }
                            })
                        }
                    } else {
                        // Normal mode gestures
                        launch {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val oldScale = scale
                                val newScale = (scale * zoom).coerceIn(0.2f, 5f)
                                offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                                scale = newScale
                            }
                        }
                        launch {
                            detectTapGestures(onTap = { tapOffset ->
                                val clickedNode = graph.nodes.findLast { node ->
                                    nodePositions[node.id]?.let { pos ->
                                        val viewPos = pos * scale + offset
                                        (tapOffset - viewPos).getDistance() <= 60f * scale
                                    } ?: false
                                }
                                val clickedEdge = if (clickedNode == null) {
                                    graph.edges.find { edge ->
                                        val sourcePos = nodePositions[edge.sourceId]
                                        val targetPos = nodePositions[edge.targetId]
                                        if (sourcePos != null && targetPos != null) {
                                            val start = sourcePos * scale + offset
                                            val end = targetPos * scale + offset
                                            distanceToSegment(tapOffset, start, end) < 20f
                                        } else false
                                    }
                                } else null

                                if (clickedNode != null) {
                                    onNodeClick(clickedNode)
                                } else if (clickedEdge != null) {
                                    onEdgeClick(clickedEdge)
                                }
                            })
                        }
                    }
                }
            }
        ) {
            // Draw selection rectangle
            selectionRect?.let { rect ->
                drawRect(
                    color = Color.Blue.copy(alpha = 0.3f),
                    topLeft = rect.topLeft,
                    size = rect.size
                )
                drawRect(
                    color = Color.Blue,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 6f)
                )
            }

            // Draw edges with arrow heads for better direction indication
            graph.edges.forEach { edge ->
                val sourcePos = nodePositions[edge.sourceId]
                val targetPos = nodePositions[edge.targetId]
                if (sourcePos != null && targetPos != null) {
                    val start = sourcePos * scale + offset
                    val end = targetPos * scale + offset
                    
                    // Different colors for different edge types
                    val edgeColor = when (edge.metadata["type"]) {
                        "function_target" -> Color(0xFFFF9800) // Orange for function edges
                        else -> if (edge.id == selectedEdgeId) Color.Red else Color.Gray
                    }
                    
                    drawLine(
                        color = edgeColor,
                        start = start,
                        end = end,
                        strokeWidth = (edge.weight * 3f).coerceIn(2f, 8f)
                    )
                    
                    // Draw arrow head
                    drawArrowHead(start, end, edgeColor, 20f * scale)
                    
                    // Draw edge label
                    edge.label?.let { label ->
                        val center = (start + end) / 2f
                        val textLayoutResult = textMeasurer.measure(
                            text = AnnotatedString(label),
                            style = TextStyle(fontSize = (10 * scale).sp, color = Color.DarkGray)
                        )
                        
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x = center.x - textLayoutResult.size.width / 2,
                                y = center.y - textLayoutResult.size.height / 2
                            )
                        )
                    }
                }
            }

            // Draw nodes with different shapes for different types
            graph.nodes.forEach { node ->
                val position = nodePositions[node.id]
                if (position != null) {
                    val isSelected = node.id == selectedNodeId
                    val isLinkingCandidate = node.id in linkingNodeIds
                    val isBoxSelected = node.id in boxSelectedNodeIds
                    
                    drawUIRouteNode(
                        node = node,
                        position = position * scale + offset,
                        radius = 60f * scale,
                        textMeasurer = textMeasurer,
                        isSelected = isSelected,
                        isLinkingCandidate = isLinkingCandidate,
                        isBoxSelected = isBoxSelected
                    )
                }
            }
        }
    }
}

private fun createNormalizedRect(start: Offset, end: Offset): Rect {
    return Rect(
        left = min(start.x, end.x),
        top = min(start.y, end.y),
        right = max(start.x, end.x),
        bottom = max(start.y, end.y)
    )
}

private fun distanceToSegment(p: Offset, start: Offset, end: Offset): Float {
    val l2 = (start - end).getDistanceSquared()
    if (l2 == 0f) return (p - start).getDistance()
    val t = ((p.x - start.x) * (end.x - start.x) + (p.y - start.y) * (end.y - start.y)) / l2
    val tClamped = t.coerceIn(0f, 1f)
    val projection = start + (end - start) * tClamped
    return (p - projection).getDistance()
}

private fun DrawScope.drawArrowHead(start: Offset, end: Offset, color: Color, size: Float) {
    val angle = atan2(end.y - start.y, end.x - start.x)
    val arrowLength = size
    val arrowAngle = Math.PI / 6 // 30 degrees
    
    val arrowHead1 = Offset(
        end.x - (arrowLength * cos(angle - arrowAngle)).toFloat(),
        end.y - (arrowLength * sin(angle - arrowAngle)).toFloat()
    )
    val arrowHead2 = Offset(
        end.x - (arrowLength * cos(angle + arrowAngle)).toFloat(),
        end.y - (arrowLength * sin(angle + arrowAngle)).toFloat()
    )
    
    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(arrowHead1.x, arrowHead1.y)
        lineTo(arrowHead2.x, arrowHead2.y)
        close()
    }
    
    drawPath(path, color)
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawUIRouteNode(
    node: Node,
    position: Offset,
    radius: Float,
    textMeasurer: TextMeasurer,
    isSelected: Boolean,
    isLinkingCandidate: Boolean,
    isBoxSelected: Boolean
) {
    val nodeType = node.metadata["nodeType"] ?: "UNKNOWN"
    
    // Different shapes for different node types
    when (nodeType) {
        "APP_HOME" -> {
            // Draw house shape
            drawRect(
                color = node.color,
                topLeft = Offset(position.x - radius/2, position.y - radius/2),
                size = androidx.compose.ui.geometry.Size(radius, radius)
            )
        }
        "DIALOG" -> {
            // Draw diamond shape
            val path = Path().apply {
                moveTo(position.x, position.y - radius)
                lineTo(position.x + radius, position.y)
                lineTo(position.x, position.y + radius)
                lineTo(position.x - radius, position.y)
                close()
            }
            drawPath(path, node.color)
        }
        "function" -> {
            // Draw hexagon for functions
            val path = Path().apply {
                val hexRadius = radius * 0.8f
                for (i in 0..5) {
                    val angle = Math.PI / 3 * i
                    val x = position.x + (hexRadius * cos(angle)).toFloat()
                    val y = position.y + (hexRadius * sin(angle)).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path, node.color)
        }
        else -> {
            // Default circle
            drawCircle(
                color = node.color,
                radius = radius,
                center = position
            )
        }
    }

    // Selection and linking indicators
    if (isSelected) {
        drawCircle(
            color = Color.Yellow,
            radius = radius + 8f,
            center = position,
            style = Stroke(width = 6f)
        )
    }

    if (isBoxSelected) {
        drawCircle(
            color = Color(0xFF4FC3F7),
            radius = radius + 10f,
            center = position,
            style = Stroke(width = 8f)
        )
    }

    if (isLinkingCandidate) {
        drawCircle(
            color = Color.Red,
            radius = radius + 5f,
            center = position,
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )
    }
    
    // Draw node label
    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(node.label),
        style = TextStyle(fontSize = (12f * (radius / 60f)).sp, color = Color.Black)
    )
    
    val textPosition = Offset(
        x = position.x - textLayoutResult.size.width / 2,
        y = position.y - textLayoutResult.size.height / 2
    )
    
    drawText(textLayoutResult, topLeft = textPosition)
} 