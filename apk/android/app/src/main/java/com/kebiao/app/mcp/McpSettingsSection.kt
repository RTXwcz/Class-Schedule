package com.kebiao.app.mcp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kebiao.app.data.settings.AppSettings
import kotlinx.serialization.json.Json

@Composable
fun McpSettingsSection(settings: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit) {
    val context = LocalContext.current
    val status by McpService.status.collectAsState()
    var port by remember(settings.mcpPort) { mutableStateOf(settings.mcpPort.toString()) }
    var message by remember { mutableStateOf<String?>(null) }
    var rotate by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Agent 连接", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("启用局域网 MCP", Modifier.weight(1f))
            Switch(settings.mcpEnabled, { enabled -> update { it.copy(mcpEnabled = enabled) } })
        }
        Text(if (status.running) "服务运行中" else status.error ?: "服务已停止")
        status.endpoints.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("端口") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        TextButton(onClick = {
            val value = port.toIntOrNull()
            if (value != null && value in 1024..65535) {
                update { it.copy(mcpPort = value) }; message = "端口已保存"
            } else message = "端口须为 1024 到 65535"
        }) { Text("保存端口") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("每次写入在应用内确认", Modifier.weight(1f))
            Switch(settings.mcpWriteConfirmation, { required -> update { it.copy(mcpWriteConfirmation = required) } })
        }
        Row {
            Text("配对 Token", Modifier.weight(1f))
            IconButton(onClick = {
                message = runCatching {
                    val clip = ClipData.newPlainText("MCP Token", McpAuthStore(context).currentToken())
                    clip.description.extras = android.os.PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                    "Token 已复制"
                }.getOrElse { "Token 读取失败：${it.message}" }
            }) { Icon(Icons.Default.ContentCopy, "复制配对 Token") }
            IconButton(onClick = { rotate = true }) { Icon(Icons.Default.Refresh, "轮换配对 Token") }
        }
        message?.let { Text(it) }
    }
    if (rotate) AlertDialog(
        onDismissRequest = { rotate = false }, title = { Text("轮换配对 Token") },
        text = { Text("已连接的 Agent 需要更新 Token，待确认请求将被撤销。") },
        confirmButton = { Button(onClick = {
            message = runCatching {
                McpAuthStore(context).rotate()
                McpService.approvals.cancelAll()
                "Token 已轮换"
            }.getOrElse { "轮换失败：${it.message}" }
            rotate = false
        }) { Text("轮换") } },
        dismissButton = { TextButton(onClick = { rotate = false }) { Text("取消") } },
    )
}

@Composable
fun McpApprovalDialog() {
    val pending by McpService.approvals.pending.collectAsState()
    val request = pending.firstOrNull() ?: return
    val formatted = remember(request.id) {
        Json { prettyPrint = true }.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), request.arguments)
    }
    AlertDialog(
        onDismissRequest = { McpService.approvals.resolve(request.id, false) },
        title = { Text("Agent 请求修改课表") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(request.tool)
                Text(formatted, style = MaterialTheme.typography.bodySmall)
                Text("待处理 ${pending.size} 项")
            }
        },
        confirmButton = { Button(onClick = { McpService.approvals.resolve(request.id, true) }) { Text("允许此次修改") } },
        dismissButton = { TextButton(onClick = { McpService.approvals.resolve(request.id, false) }) { Text("拒绝") } },
    )
}
