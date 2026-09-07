package com.kebiao.app.ui.settings

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LicenseSection() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var files by remember { mutableStateOf(emptyList<String>()) }
    var body by remember { mutableStateOf("") }
    TextButton(onClick = { open = true }) { Text("开源许可 · GPL-3.0") }
    if (!open) return
    LaunchedEffect(selected) {
        val content = withContext(Dispatchers.IO) {
            val available = context.assets.list("licenses").orEmpty().sorted()
            val text = selected?.let { file ->
                context.assets.open("licenses/$file").bufferedReader().use { it.readText() }
            }.orEmpty()
            available to text
        }
        files = content.first
        body = content.second
    }
    val lines = remember(body) { body.lines() }
    AlertDialog(
        onDismissRequest = { open = false; selected = null },
        title = { Text(selected ?: "开源许可") },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                if (selected == null) {
                    item { Text("我的课表 © 2026 RTXwcz 和贡献者。按 GPL-3.0-only 分发，可依该协议修改和再分发，不提供担保。第三方组件保留各自许可。") }
                    item { TextButton(onClick = { uriHandler.openUri("https://github.com/RTXwcz/Class-Schedule") }) { Text("获取源代码") } }
                    items(files) { name -> TextButton(onClick = { selected = name }) { Text(name) } }
                } else items(lines) { line -> Text(line, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = {
            if (selected != null) selected = null else open = false
        }) { Text(if (selected != null) "返回" else "关闭") } },
    )
}
