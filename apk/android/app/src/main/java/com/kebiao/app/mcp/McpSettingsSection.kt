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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import com.kebiao.app.ui.components.SettingsGroup
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
    var advanced by remember { mutableStateOf(false) }
    var troubleshooting by remember { mutableStateOf(false) }
    val addresses = status.endpoints.filterNot { it.contains("127.0.0.1") }
    var selectedAddress by remember(addresses) { mutableStateOf(addresses.firstOrNull()) }
    fun copy(label: String, value: String, sensitive: Boolean = false) {
        val clip = ClipData.newPlainText(label, value)
        if (sensitive) clip.description.extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        message = "$label 已复制"
    }
    fun copyPairing(fullConfig: Boolean) {
        runCatching {
            val token = McpAuthStore(context).currentToken()
            if (fullConfig) selectedAddress?.let { copy("MCP JSON 配置", McpConnectionConfig.json(it, token), true) }
            else copy("配对 Token", token, true)
        }.onFailure { message = "无法读取配对信息：${it.message}" }
    }
    SettingsGroup("连接 AI 助手", "让支持 MCP 的 Agent 查询课表，或在你允许时修改安排。") {
        Text("1  连接同一个可信 Wi-Fi", style = MaterialTheme.typography.titleSmall)
        Text("手机和电脑需处于同一局域网。当前连接使用 HTTP，内容没有传输加密。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("2  开启手机上的服务", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(if (status.running) "服务正在监听" else if (settings.mcpEnabled) "正在启动…" else "尚未开启")
                Text("开启期间显示持续通知，可随时停止。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(settings.mcpEnabled, { enabled -> update { it.copy(mcpEnabled = enabled) } })
        }
        status.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (status.running && addresses.isEmpty()) Text("没有可供电脑访问的局域网地址，请检查 Wi-Fi。")
        if (status.running && addresses.isNotEmpty()) {
            addresses.forEach { address ->
                TextButton(onClick = { selectedAddress = address }) { Text((if (selectedAddress == address) "✓ " else "") + address) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selectedAddress?.let { copy("连接地址", it) } }) { Text("复制地址") }
                OutlinedButton(onClick = { copyPairing(false) }) { Text("复制 Token") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("3  在 Agent 客户端添加 MCP", style = MaterialTheme.typography.titleSmall)
        Text("选择 HTTP / Streamable HTTP 连接，粘贴上面的地址。在请求头中填写 Authorization，值为 Bearer 加一个空格和 Token。", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { copyPairing(true) },
            enabled = status.running && selectedAddress != null, modifier = Modifier.fillMaxWidth()) { Text("复制完整 JSON 配置") }
        Text("适用于支持 mcpServers 的配置文件；其他客户端按地址和请求头分别填写。然后让 Agent 试着查询今天的课程。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("修改前由我确认", style = MaterialTheme.typography.titleSmall)
                Text(if (settings.mcpWriteConfirmation) "每次写入会在应用中请求确认" else "已配对 Agent 可以直接编辑；清空仍需确认", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(settings.mcpWriteConfirmation, { required -> update { it.copy(mcpWriteConfirmation = required) } })
        }
        TextButton(onClick = { troubleshooting = !troubleshooting }) { Text(if (troubleshooting) "收起连接帮助" else "连接不上？查看排查方法") }
        if (troubleshooting) Text("• 确认服务显示“正在监听”，且复制的是 Wi-Fi 地址。\n• 访客 Wi-Fi、AP 隔离或 VPN 可能阻止设备互访。\n• 401 通常表示 Token 不匹配，请重新复制。\n• 手机换网络后需要更新客户端地址。\n• 强行停止应用会断开服务，重新打开并启用即可。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "收起高级设置" else "高级设置") }
        if (advanced) {
        OutlinedTextField(port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("端口") }, singleLine = true,
            modifier = Modifier.fillMaxWidth())
        TextButton(onClick = {
            val value = port.toIntOrNull()
            if (value != null && value in 1024..65535) {
                update { it.copy(mcpPort = value) }; message = "端口已保存"
            } else message = "端口须为 1024 到 65535"
        }) { Text("保存端口") }
        TextButton(onClick = { rotate = true }) { Text("轮换 Token，撤销现有配对") }
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
