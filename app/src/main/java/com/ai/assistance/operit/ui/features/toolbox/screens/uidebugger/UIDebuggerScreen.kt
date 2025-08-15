package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.services.UIDebuggerService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIDebuggerScreen(navController: NavController) {
    val context = LocalContext.current

    Scaffold(
        floatingActionButton = {
            // 大的悬浮窗启动按钮
            ExtendedFloatingActionButton(
                onClick = {
                    if (Settings.canDrawOverlays(context)) {
                        // 启动悬浮窗服务
                        val intent = Intent(context, UIDebuggerService::class.java)
                        context.startService(intent)
                    } else {
                        // 请求悬浮窗权限
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                },
                icon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                text = { Text("启动悬浮窗") },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 悬浮窗权限提示卡片
            if (!Settings.canDrawOverlays(context)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "需要悬浮窗权限",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "要使用悬浮窗UI调试功能，需要授予应用悬浮窗权限。点击右上角的启动按钮会自动跳转到权限设置页面。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            } else {
            }
        }
    }
} 