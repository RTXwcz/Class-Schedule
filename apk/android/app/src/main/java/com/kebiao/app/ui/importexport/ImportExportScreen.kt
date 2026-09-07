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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
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
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var confirmJson by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> selectedImage = uri }
    state.importDrafts?.let { drafts ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.errorMessage?.let { Text(it, Modifier.padding(16.dp)) }
            ImportReviewScreen(drafts, saving = state.importBusy, imageUri = state.importImageUri, onChange = viewModel::editImportDrafts,
                onCancel = viewModel::cancelImport, onConfirm = viewModel::saveImportDrafts)
        }
        return
    }
    selectedImage?.let { uri ->
        AlertDialog(
            onDismissRequest = { selectedImage = null },
            title = { Text("发送图片进行识别") },
            text = {
                Column {
                    ImportImagePreview(uri)
                    Text("图片将发送至 ${state.settings.openAiEndpoint}，使用模型 ${state.settings.openAiModel}。")
                }
            },
            confirmButton = { TextButton(onClick = { selectedImage = null; viewModel.recognizeImage(uri) }) { Text("开始识别") } },
            dismissButton = { TextButton(onClick = { selectedImage = null }) { Text("取消") } },
        )
    }
    if (confirmJson) {
        AlertDialog(
            onDismissRequest = { confirmJson = false },
            title = { Text("替换当前课表") },
            text = { Text("将替换现有的 ${state.courses.size} 门课程、${state.exams.size} 场考试和 ${state.overrides.size} 条调休。") },
            confirmButton = { TextButton(onClick = { confirmJson = false; status = null; viewModel.importJson(json) }) { Text("替换并导入") } },
            dismissButton = { TextButton(onClick = { confirmJson = false }) { Text("取消") } },
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("导入与导出")
        Button(onClick = { picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, enabled = !state.importBusy && viewModel.hasOpenAiKey()) {
            Text("选择课表图片 · OpenAI")
        }
        if (!viewModel.hasOpenAiKey()) Text("请先在设置中配置 API Key")
        if (state.importBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.importStatus?.let { Text(it) }
        Text("原生应用与 Web/PWA 使用相同 JSON 格式。导入会替换当前课程、考试和调休数据。")
        OutlinedTextField(
            value = json,
            onValueChange = { json = it },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            label = { Text("课表 JSON") },
            minLines = 12,
        )
        Button(onClick = { json = viewModel.exportJson(); status = "已生成当前数据" }) { Text("生成导出 JSON") }
        Button(onClick = { confirmJson = true }, enabled = !state.importBusy && json.isNotBlank()) { Text("导入 JSON") }
        TextButton(onClick = { json = ""; status = null }) { Text("清空编辑框") }
        status?.let { Text(it) }
        state.errorMessage?.let {
            Text(it)
            TextButton(onClick = viewModel::clearError) { Text("关闭错误") }
        }
    }
}
