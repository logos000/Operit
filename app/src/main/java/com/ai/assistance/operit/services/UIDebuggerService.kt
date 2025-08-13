package com.ai.assistance.operit.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.services.floating.UIDebuggerWindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.ComponentName
import android.content.ServiceConnection
import androidx.lifecycle.ViewModelProvider
import com.ai.assistance.operit.ui.features.toolbox.screens.uidebugger.UIDebuggerViewModel

/**
 * A service for managing the UI Debugger floating window.
 *
 * This service allows the UI debugger to be displayed as a floating window over other apps,
 * providing tools to inspect and debug the UI hierarchy on the screen.
 */
class UIDebuggerService : Service(), ViewModelStoreOwner {
    private val TAG = "UIDebuggerService"
    private lateinit var windowManager: UIDebuggerWindowManager
    private lateinit var lifecycleOwner: ServiceLifecycleOwner
    override val viewModelStore = ViewModelStore()
    private lateinit var viewModel: UIDebuggerViewModel

    private var floatingChatService: FloatingChatService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as FloatingChatService.LocalBinder
            val chatService = binder.getService()
            floatingChatService = chatService
            isBound = true
            viewModel.setWindowInteractionController(chatService::setWindowInteraction)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            floatingChatService = null
            viewModel.setWindowInteractionController(null)
        }
    }

    private val NOTIFICATION_ID = 1337
    private val CHANNEL_ID = "UIDebuggerChannel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner = ServiceLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        
        // Initialize ViewModel
        val factory = ViewModelProvider.NewInstanceFactory()
        viewModel = ViewModelProvider(this, factory).get(UIDebuggerViewModel::class.java)

        windowManager = UIDebuggerWindowManager(this, this, lifecycleOwner)
        createNotificationChannel()

        // Bind to FloatingChatService
        Intent(this, FloatingChatService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "UI Debugger service started")
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        lifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        isServiceRunning.value = true
        windowManager.show()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "UI Debugger service stopped")
        lifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_DESTROY)
        isServiceRunning.value = false
        windowManager.remove()
        viewModelStore.clear()
        stopForeground(true)

        // Unbind from FloatingChatService
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "UI Debugger Service"
            val descriptionText = "Displays a floating overlay for UI debugging"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("UI Debugger Active")
            .setContentText("Tap to manage the UI debugger overlay.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with a real icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        val isServiceRunning = MutableStateFlow(false)
    }
} 