package com.kebiao.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.kebiao.app.widget.WidgetNavigation
import com.kebiao.app.widget.WidgetLaunchRequest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.notifications.ReminderCoordinator
import com.kebiao.app.mcp.McpService
import com.kebiao.app.imports.OpenAiImageImporter
import com.kebiao.app.ocr.OcrModelManager
import com.kebiao.app.ocr.PaddleOcrEngine
import com.kebiao.app.ui.AppViewModel
import com.kebiao.app.ui.ScheduleApp

class MainActivity : ComponentActivity() {
    private var widgetRequest by mutableStateOf<WidgetLaunchRequest?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptWidgetIntent(intent)
        val permissions = getSharedPreferences("permission_requests", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !permissions.getBoolean("notification_requested", false)
        ) {
            permissions.edit().putBoolean("notification_requested", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        val database = AppDatabase.getInstance(applicationContext)
        val repository = ScheduleRepository(database)
        val settingsStore = AppSettingsStore(applicationContext)
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(repository, settingsStore, openAiImporter = OpenAiImageImporter(applicationContext),
                    localOcrManager = OcrModelManager(applicationContext),
                    recognizeLocal = { uri, id -> PaddleOcrEngine(applicationContext, id).recognize(uri) }) as T
        })[AppViewModel::class.java]
        ReminderCoordinator(applicationContext, repository, settingsStore).start(lifecycleScope)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsStore.settings.collectLatest { settings ->
                    if (settings.mcpEnabled) McpService.start(applicationContext) else McpService.stop(applicationContext)
                }
            }
        }
        setContent {
            ScheduleApp(viewModel, widgetRequest, onWidgetLaunchHandled = { widgetRequest = null })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptWidgetIntent(intent)
    }

    private fun acceptWidgetIntent(incoming: Intent) {
        WidgetNavigation.destination(incoming)?.let { destination ->
            widgetRequest = WidgetLaunchRequest(destination)
            // Navigation is one-shot; a later configuration recreation must not reset the user's tab.
            incoming.data = null
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { ReminderCoordinator.refresh(applicationContext) }
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
}
