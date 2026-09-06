package com.kebiao.app.ui.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.AppViewModel

@Composable
fun ImportExportScreen(viewModel: AppViewModel, padding: PaddingValues = PaddingValues()) {
    val state by viewModel.uiState.collectAsState()
    var json by remember { mutableStateOf(viewModel.exportJson()) }
    var status by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("导入与导出")
        Text("原生应用与 Web/PWA 使用相同 JSON 格式。导入会替换当前课程、考试和调休数据。")
        OutlinedTextField(
            value = json,
            onValueChange = { json = it },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            label = { Text("课表 JSON") },
            minLines = 12,
        )
        Button(onClick = { json = viewModel.exportJson(); status = "已生成当前数据" }) { Text("生成导出 JSON") }
        Button(onClick = { status = if (viewModel.importJson(json)) "导入成功" else "导入失败" }) { Text("导入 JSON") }
        TextButton(onClick = { json = ""; status = null }) { Text("清空编辑框") }
        status?.let { Text(it) }
        state.errorMessage?.let {
            Text(it)
            TextButton(onClick = viewModel::clearError) { Text("关闭错误") }
        }
    }
}
