package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun UIDebuggerOverlay(
    viewModelStoreOwner: ViewModelStoreOwner,
    onClose: () -> Unit,
    onMinimize: (() -> Unit)? = null
) {
    val viewModel: UIDebuggerViewModel = viewModel(viewModelStoreOwner = viewModelStoreOwner)
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var isOverlayVisible by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf<UIElement?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isOverlayVisible) {
            ElementHighlightOverlay(
                elements = uiState.elements,
                onElementClick = { element ->
                    selectedElement = element
                }
            )
        }

        // Element info panel (floating)
        selectedElement?.let { element ->
            ElementInfoPanel(
                element = element,
                onDismiss = { selectedElement = null },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .zIndex(10f)
            )
        }

        // Control buttons
        Box(modifier = Modifier.fillMaxSize()) {
            // Minimize button (if callback provided) - 放在右下角，更大更显眼
            onMinimize?.let { minimizeCallback ->
                FloatingActionButton(
                    onClick = minimizeCallback,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "最小化",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // Main action button - 放在右上角
            SmallFloatingActionButton(
                onClick = {
                    if (isOverlayVisible) {
                        onClose()
                    } else {
                        viewModel.refreshUI()
                        isOverlayVisible = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (isOverlayVisible) Icons.Default.Close else Icons.Default.Build,
                    contentDescription = if (isOverlayVisible) "关闭" else "开始分析",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Action feedback message
        if (uiState.showActionFeedback) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .zIndex(20f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.actionFeedbackMessage,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Error message
        uiState.errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .zIndex(20f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun ElementHighlightOverlay(
    elements: List<UIElement>,
    onElementClick: (UIElement) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(elements) {
                detectTapGestures { offset ->
                    // Find the smallest element that contains the tap point
                    // This ensures we get the most specific/innermost element
                    val tappedElement = elements
                        .filter { element ->
                            element.bounds?.let { bounds ->
                                offset.x >= bounds.left && 
                                offset.x <= bounds.right && 
                                offset.y >= bounds.top && 
                                offset.y <= bounds.bottom
                            } ?: false
                        }
                        .minByOrNull { element ->
                            // Find the element with smallest area
                            element.bounds?.let { bounds ->
                                bounds.width() * bounds.height()
                            } ?: Int.MAX_VALUE
                        }
                    
                    tappedElement?.let(onElementClick)
                }
            }
    ) {
        // Draw elements sorted by area (largest first, smallest last)
        // This ensures smaller elements are drawn on top of larger ones
        elements
            .sortedByDescending { element ->
                element.bounds?.let { bounds ->
                    bounds.width() * bounds.height()
                } ?: 0
            }
            .forEach { element ->
                element.bounds?.let { bounds ->
                    drawElementHighlight(bounds)
                }
            }
    }
}

private fun DrawScope.drawElementHighlight(
    bounds: android.graphics.Rect
) {
    // Always use red color like the original
    val color = Color.Red
    
    // Draw highlight border
    drawRect(
        color = color,
        topLeft = Offset(bounds.left.toFloat(), bounds.top.toFloat()),
        size = Size(bounds.width().toFloat(), bounds.height().toFloat()),
        style = Stroke(width = 2.dp.toPx())
    )
}

@Composable
fun ElementInfoPanel(
    element: UIElement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 300.dp)
            .heightIn(max = 400.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "控件信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Element type badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = element.typeDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Scrollable content
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false)
            ) {
                // Basic info with smaller text
                Text(
                    text = element.getFullDetails(),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Additional size info if available
                if (element.bounds != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "尺寸: ${element.bounds.width()}×${element.bounds.height()}px",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
} 