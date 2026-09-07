package com.kebiao.app.ui.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import com.kebiao.app.ui.timetable.CourseEditorDialog
import com.kebiao.app.ui.calendar.ExamEditorDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportExportScreen(viewModel: AppViewModel, padding: PaddingValues = PaddingValues()) {
    val state by viewModel.uiState.collectAsState()
    var json by remember { mutableStateOf(viewModel.exportJson()) }
    var status by remember { mutableStateOf<String?>(null) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var localRecognition by remember { mutableStateOf(false) }
    var confirmJson by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("手动填写") }
    var manualType by remember { mutableStateOf<String?>(null) }
    var showJson by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val readJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 4 * 1024 * 1024) { "JSON 文件超过 4 MB" }
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    } ?: error("无法打开文件")
                }
                com.kebiao.app.data.JsonScheduleCodec.decode(json)
                confirmJson = true
            } catch (error: Exception) { status = error.message ?: "读取失败" }
        }
    }
    val writeJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val export = viewModel.exportCurrentJson()
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(export.toByteArray(Charsets.UTF_8)) }
                }
                json = export
                status = "文件已导出"
            } catch (error: Exception) { status = error.message ?: "导出失败" }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> selectedImage = uri }
    state.importDrafts?.let { drafts ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.errorMessage?.let { Text(it, Modifier.padding(16.dp)) }
            ImportReviewScreen(drafts, saving = state.importBusy, imageUri = state.importImageUri, onChange = viewModel::editImportDrafts,
                parityEnabled = state.settings.parityEnabled,
                periodCount = state.settings.periods.size,
                onCancel = viewModel::cancelImport, onConfirm = viewModel::saveImportDrafts)
        }
        return
    }
    selectedImage?.let { uri ->
        AlertDialog(
            onDismissRequest = { selectedImage = null },
            title = { Text(if (localRecognition) "本地识别图片" else "发送图片进行识别") },
            text = {
                Column {
                    ImportImagePreview(uri)
                    Text(if (localRecognition) "使用本机 ${AppViewModel.modelId(state.settings.localOcrModel).displayName}"
                        else "图片将发送至 ${state.settings.openAiEndpoint}，使用模型 ${state.settings.openAiModel}。")
                }
            },
            confirmButton = { TextButton(onClick = { selectedImage = null; viewModel.recognizeImage(uri, localRecognition) }) { Text("开始识别") } },
            dismissButton = { TextButton(onClick = { selectedImage = null }) { Text("取消") } },
        )
    }
    if (confirmJson) {
        AlertDialog(
            onDismissRequest = { confirmJson = false },
            title = { Text("替换当前课表") },
            text = { Text("将替换现有的 ${state.courses.size} 门课程、${state.exams.size} 条考试/日程和 ${state.overrides.size} 条调休。") },
            confirmButton = { TextButton(onClick = { confirmJson = false; status = null; viewModel.importJson(json) }) { Text("替换并导入") } },
            dismissButton = { TextButton(onClick = { confirmJson = false }) { Text("取消") } },
        )
    }
    if (manualType == "COURSE") CourseEditorDialog(
        initial = null, onDismiss = { manualType = null },
        onSave = { viewModel.addCourse(it); manualType = null }, onDelete = { },
        parityEnabled = state.settings.parityEnabled,
        periodCount = state.settings.periods.size,
    )
    if (manualType == "EXAM" || manualType == "EVENT") ExamEditorDialog(
        initial = null, onDismiss = { manualType = null },
        onSave = { viewModel.addExam(it); manualType = null }, onDelete = { },
        initialType = manualType!!,
    )
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("录入你的安排", style = MaterialTheme.typography.headlineMedium)
        Text("从一门课开始，也可以一次导入整张课表。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("手动填写", "图片识别", "数据备份").forEach { option ->
                FilterChip(selected = mode == option, onClick = { mode = option }, label = { Text(option) })
            }
        }
        if (mode == "手动填写") {
            listOf(Triple("COURSE", "添加课程", "选择星期与节次，填写教师和上课地点"),
                Triple("EXAM", "添加考试", "记录考试日期、时间与考场"),
                Triple("EVENT", "添加日程", "讲座、社团、待办或其他日期安排")).forEach { (type, title, description) ->
                OutlinedCard(onClick = { manualType = type }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("手动填写无需下载模型，也无需配置 API。", style = MaterialTheme.typography.bodySmall)
        }
        if (mode == "图片识别") {
        Button(onClick = { localRecognition = true; picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
            enabled = !state.importBusy && state.modelInstalled && !state.modelDownloadBusy && state.settings.useLocalOcr) {
            Text("选择课表图片 · 本地 OCR")
        }
        if (state.settings.useLocalOcr && !state.modelInstalled) Text(state.modelStatus ?: "本地模型尚未下载")
        if (state.modelDownloadBusy) LinearProgressIndicator(progress = { state.modelProgress }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { localRecognition = false; picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, enabled = !state.importBusy && viewModel.hasOpenAiKey()) {
            Text("选择课表图片 · OpenAI")
        }
        if (!viewModel.hasOpenAiKey()) Text("请先在设置中配置 API Key")
        Text("识别完成后可以逐项校对；没有图片时可切换到手动填写。", style = MaterialTheme.typography.bodySmall)
        }
        if (state.importBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.importStatus?.let { Text(it) }
        if (mode == "数据备份") {
        Text("备份与迁移", style = MaterialTheme.typography.titleLarge)
        Text("JSON 保留课程、考试、日程与调休。恢复备份会替换当前数据。")
        Button(onClick = { writeJson.launch("class-schedule.json") }, modifier = Modifier.fillMaxWidth()) { Text("导出 JSON 文件") }
        OutlinedButton(onClick = { readJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !state.importBusy, modifier = Modifier.fillMaxWidth()) { Text("选择 JSON 文件") }
        TextButton(onClick = { showJson = !showJson }) { Text(if (showJson) "收起 JSON 编辑" else "粘贴或编辑 JSON") }
        if (showJson) {
        OutlinedTextField(
            value = json,
            onValueChange = { json = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("课表 JSON") },
            minLines = 5, maxLines = 10,
        )
        TextButton(onClick = { scope.launch { json = viewModel.exportCurrentJson(); status = "已生成当前数据" } }) { Text("生成导出 JSON") }
        Button(onClick = { confirmJson = true }, enabled = !state.importBusy && json.isNotBlank()) { Text("导入 JSON") }
        TextButton(onClick = { json = ""; status = null }) { Text("清空编辑框") }
        }
        }
        status?.let { Text(it) }
        state.errorMessage?.let {
            Text(it)
            TextButton(onClick = viewModel::clearError) { Text("关闭错误") }
        }
    }
}
