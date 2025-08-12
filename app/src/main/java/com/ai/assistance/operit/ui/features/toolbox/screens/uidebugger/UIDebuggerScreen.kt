package com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.ai.assistance.operit.services.UIDebuggerService

@Composable
fun UIDebuggerScreen(navController: NavController) {
    val context = LocalContext.current
    var hasOverlayPermission by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
    }

    val isServiceRunning by UIDebuggerService.isServiceRunning.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("UI Debugger", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Start the UI debugger service to display an overlay with UI element information.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (!hasOverlayPermission) {
            Button(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
                // You might want to refresh the permission state after returning to the app
            }) {
                Text("Request Overlay Permission")
                }
            } else {
            Button(
                            onClick = {
                    if (isServiceRunning) {
                        context.stopService(Intent(context, UIDebuggerService::class.java))
                    } else {
                        val intent = Intent(context, UIDebuggerService::class.java)
                        ContextCompat.startForegroundService(context, intent)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServiceRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isServiceRunning) "Stop Service" else "Start Service")
}
        }
    }
} 