package com.kebiao.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.kebiao.app.mcp.McpAuthStore
import com.kebiao.app.mcp.McpServer
import com.kebiao.app.mcp.McpToolRegistry
import com.kebiao.app.mcp.RepositoryScheduleStore
import com.kebiao.app.imports.OpenAiImageImporter
import com.kebiao.app.ui.AppViewModel
import com.kebiao.app.ui.ScheduleApp

class MainActivity : ComponentActivity() {
    private var mcpServer: McpServer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        val database = AppDatabase.getInstance(applicationContext)
        val repository = ScheduleRepository(database)
        val settingsStore = AppSettingsStore(applicationContext)
        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(repository, settingsStore, openAiImporter = OpenAiImageImporter(applicationContext)) as T
        })[AppViewModel::class.java]
        ReminderCoordinator(applicationContext, repository, settingsStore).start(lifecycleScope)
        val mcp = McpServer(applicationContext, McpToolRegistry(RepositoryScheduleStore(repository), McpAuthStore(applicationContext), writeConfirmation = true))
        mcpServer = mcp
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                settingsStore.settings.collectLatest { settings ->
                    if (settings.mcpEnabled) mcp.start(lifecycleScope) else mcp.stop()
                }
            }
        }
        setContent {
            ScheduleApp(viewModel)
        }
    }

    override fun onDestroy() {
        mcpServer?.stop()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 1001
    }
}
