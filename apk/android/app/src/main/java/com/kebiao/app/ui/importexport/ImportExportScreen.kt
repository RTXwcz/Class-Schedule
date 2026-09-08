package com.kebiao.app.ui.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import com.kebiao.app.ui.timetable.CourseEditorDialog
import com.kebiao.app.ui.calendar.ExamEditorDialog
import com.kebiao.app.ui.components.ProductHeader
import com.kebiao.app.ui.components.EntryAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Event
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.kebiao.app.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportExportScreen(viewModel: AppViewModel, padding: PaddingValues = PaddingValues(), onConfigure: () -> Unit = {}, entryRequestKey: String? = null) {
    val state by viewModel.uiState.collectAsState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var json by remember { mutableStateOf(viewModel.exportJson()) }
    var status by remember { mutableStateOf<String?>(null) }
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var localRecognition by remember { mutableStateOf(false) }
    var confirmJson by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(if (state.importDrafts != null) "图片识别" else "手动填写") }
    var reviewDismissed by rememberSaveable { mutableStateOf(false) }
    var handledEntryKey by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var manualType by remember { mutableStateOf<String?>(null) }
    var showJson by remember { mutableStateOf(false) }
    LaunchedEffect(entryRequestKey) {
        if (entryRequestKey != null && handledEntryKey != entryRequestKey) {
            handledEntryKey = entryRequestKey
            mode = "手动填写"
            manualType = null
            reviewDismissed = true
            selectedImage = null
            confirmJson = false
        }
    }
    val showReview = state.importDrafts != null && !reviewDismissed && mode == "图片识别"
    fun leaveReview() { focus.clearFocus(); keyboard?.hide(); reviewDismissed = true }
    BackHandler(enabled = showReview) { leaveReview() }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false }, title = { Text("放弃此次识别结果？") },
        text = { Text("未保存的识别内容和校对修改将被清除，已有课表不会改变。") },
        confirmButton = { TextButton(onClick = { viewModel.cancelImport(); confirmDiscard = false; reviewDismissed = true }, enabled = !state.importBusy) { Text("确认放弃") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续保留") } },
    )
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
    state.importDrafts?.takeIf { showReview }?.let { drafts ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = ::leaveReview) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回图片导入") }
                Column(Modifier.weight(1f)) {
                    Text("校对识别结果", style = MaterialTheme.typography.titleMedium)
                    Text("返回后保留未保存内容", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { confirmDiscard = true }, enabled = !state.importBusy) { Text("放弃", color = MaterialTheme.colorScheme.error) }
            }
            state.errorMessage?.let { Text(it, Modifier.padding(16.dp)) }
            ImportReviewScreen(drafts, saving = state.importBusy, imageUri = state.importImageUri, onChange = viewModel::editImportDrafts,
                parityEnabled = state.settings.parityEnabled,
                periodCount = state.settings.periods.size,
                removed = state.removedImportDraft, onRemove = viewModel::removeImportDraft, onUndoRemove = viewModel::undoImportRemoval,
                onConfirm = viewModel::saveImportDrafts)
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
            confirmButton = { TextButton(onClick = { selectedImage = null; reviewDismissed = false; viewModel.recognizeImage(uri, localRecognition) }) { Text("开始识别") } },
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
        ProductHeader("添加你的安排", "填一门课，或把整张课表交给识别", "录入与备份")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("手动填写", "图片识别", "数据备份").forEach { option ->
                FilterChip(selected = mode == option, onClick = { mode = option }, label = { Text(option) })
            }
        }
        if (mode == "手动填写") {
            listOf(Triple("COURSE", "添加课程", "选择星期与节次，填写教师和上课地点"),
                Triple("EXAM", "添加考试", "记录考试日期、时间与考场"),
                Triple("EVENT", "添加日程", "讲座、社团、待办或其他日期安排")).forEach { (type, title, description) ->
                EntryAction(if (type == "COURSE") Icons.Outlined.MenuBook else if (type == "EXAM") Icons.Outlined.School else Icons.Outlined.Event,
                    title, description, { manualType = type },
                    tone = if (type == "COURSE") MaterialTheme.colorScheme.primaryContainer else if (type == "EXAM") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer)
            }
            Text("手动填写无需下载模型，也无需配置 API。", style = MaterialTheme.typography.bodySmall)
        }
        if (mode == "图片识别") {
        if (state.importDrafts != null) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${state.importDrafts!!.size} 门课程待校对", style = MaterialTheme.typography.titleMedium)
                    Text("刚才的识别与修改已暂存，尚未写入课表。", style = MaterialTheme.typography.bodySmall)
                    Text("保存或放弃这次结果后，可选择新的图片。", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { reviewDismissed = false }) { Text("继续校对") }
                        TextButton(onClick = { confirmDiscard = true }, enabled = !state.importBusy) { Text("放弃识别结果") }
                    }
                }
            }
        }
        Button(onClick = { localRecognition = true; picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
            enabled = state.importDrafts == null && !state.importBusy && state.modelInstalled && !state.modelDownloadBusy && state.settings.useLocalOcr) {
            Text("选择课表图片 · 本地 OCR")
        }
        if (state.settings.useLocalOcr && !state.modelInstalled) Text(state.modelStatus ?: "本地模型尚未下载")
        if (state.modelDownloadBusy) LinearProgressIndicator(progress = { state.modelProgress }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { localRecognition = false; picker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) }, enabled = state.importDrafts == null && !state.importBusy && viewModel.hasOpenAiKey()) {
            Text("选择课表图片 · OpenAI")
        }
        if (!viewModel.hasOpenAiKey()) Text("请先在设置中配置 API Key")
        OutlinedButton(onClick = onConfigure) { Text("设置识别方式") }
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
