package com.kebiao.app.mcp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Build
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kebiao.app.MainActivity
import com.kebiao.app.data.ScheduleRepository
import com.kebiao.app.data.local.AppDatabase
import com.kebiao.app.data.settings.AppSettingsStore
import com.kebiao.app.notifications.ReminderCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class McpServiceStatus(val running: Boolean = false, val endpoints: List<String> = emptyList(), val error: String? = null)

class McpService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var server: McpServer? = null
    private val networkHosts = MutableStateFlow(McpServer.localHosts().sorted())
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { refreshHosts() }
        override fun onLost(network: Network) { refreshHosts() }
        override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) { refreshHosts() }
        private fun refreshHosts() { networkHosts.value = McpServer.localHosts().sorted() }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Agent 连接", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION_ID, notification(0))
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
        val repository = ScheduleRepository(AppDatabase.getInstance(applicationContext))
        val settings = AppSettingsStore(applicationContext)
        val registry = McpToolRegistry(
            RepositoryScheduleStore(repository), approvals,
            writeConfirmation = { settings.settings.first().mcpWriteConfirmation },
            semesterStart = { settings.settings.first().semesterStartDate?.let(LocalDate::parse) },
            parityEnabled = { settings.settings.first().parityEnabled },
            periods = { settings.settings.first().periods },
        )
        ReminderCoordinator(applicationContext, repository, settings).start(scope)
        scope.launch {
            approvals.pending.collect { pending -> manager.notify(NOTIFICATION_ID, notification(pending.size)) }
        }
        scope.launch {
            try {
            repository.ensureRules(settings.settings.first())
            combine(settings.settings.map { it.mcpEnabled to it.mcpPort }.distinctUntilChanged(), networkHosts) { config, hosts ->
                Triple(config.first, config.second, hosts)
            }.distinctUntilChanged().collect { (enabled, port, _) ->
                if (!enabled) { stopSelf(); return@collect }
                try {
                    withContext(Dispatchers.IO) {
                        server?.stop()
                        approvals.cancelAll()
                        server = McpServer(registry, McpAuthStore(applicationContext), port).also { it.start() }
                    }
                    mutableStatus.value = McpServiceStatus(true, server!!.hosts.filter { it != "localhost" && !it.startsWith('[') }
                        .map { "http://$it:$port/mcp" })
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    mutableStatus.value = McpServiceStatus(error = "MCP 启动失败：${error.message}")
                    settings.update { it.copy(mcpEnabled = false) }
                    stopSelf()
                }
            }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { server?.stop(); server = null }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                AppSettingsStore(applicationContext).update { it.copy(mcpEnabled = false) }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        approvals.cancelAll()
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        scope.cancel()
        mutableStatus.value = mutableStatus.value.copy(running = false, endpoints = emptyList())
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(pending: Int): android.app.Notification {
        val open = PendingIntent.getActivity(this, 71, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 72, Intent(this, McpService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (pending == 0) "局域网 MCP 已启用" else "$pending 项 Agent 修改待确认")
            .setContentText(if (pending == 0) "课表 Agent 连接服务" else "打开应用查看修改内容")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "停止", stop).build()
    }

    companion object {
        val approvals = McpApprovalQueue()
        private val mutableStatus = MutableStateFlow(McpServiceStatus())
        val status = mutableStatus.asStateFlow()
        private const val CHANNEL = "mcp_service"
        private const val NOTIFICATION_ID = 7001
        private const val ACTION_STOP = "com.kebiao.app.mcp.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, McpService::class.java))
        }

        fun stop(context: Context) { context.stopService(Intent(context, McpService::class.java)) }
    }
}
