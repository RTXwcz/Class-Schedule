package com.kebiao.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import com.kebiao.app.ocr.OcrModelManager
import com.kebiao.app.ui.AppViewModel

@Composable
fun OcrModelSection(viewModel: AppViewModel, onboarding: Boolean = false, onDownload: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    var selected by remember(state.settings.localOcrModel) { mutableStateOf(AppViewModel.modelId(state.settings.localOcrModel)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("本地图片识别", style = MaterialTheme.typography.titleMedium)
        Text("下载源：hf-mirror.com · 下载并校验后可离线使用", style = MaterialTheme.typography.bodySmall)
        OcrModelManager.ModelId.entries.forEach { id ->
            Row(Modifier.fillMaxWidth().selectable(selected = selected == id,
                enabled = !state.modelDownloadBusy && !state.importBusy, role = Role.RadioButton, onClick = {
                    selected = id
                    if (!onboarding) viewModel.updateSettings { it.copy(localOcrModel = id.wireName) }
                })) {
                RadioButton(selected == id, onClick = null, enabled = !state.modelDownloadBusy && !state.importBusy)
                Column {
                    Text(id.displayName)
                    Text(if (id == OcrModelManager.ModelId.TINY) "轻量版 · 6.3 MB" else "标准版 · 31.2 MB")
                }
            }
        }
        if (state.modelDownloadBusy) {
            LinearProgressIndicator(progress = { state.modelProgress }, modifier = Modifier.fillMaxWidth())
            Text("已下载 ${(state.modelProgress * 100).toInt()}%")
            TextButton(onClick = viewModel::cancelModelDownload) { Text("取消下载") }
        } else {
            Button(onClick = { viewModel.downloadLocalModel(selected); onDownload() }, enabled = !state.importBusy) {
                Text(if (state.modelInstalled && selected.wireName == state.settings.localOcrModel) "校验本地模型" else "下载所选模型")
            }
            if (state.modelInstalled && !onboarding) TextButton(onClick = viewModel::deleteLocalModel, enabled = !state.importBusy) { Text("删除本地模型") }
        }
        state.modelStatus?.let { Text(it) }
    }
}

@Composable
fun OcrChoiceDialog(viewModel: AppViewModel, onManual: () -> Unit = {}, onConfigure: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    if (!state.settingsLoaded || state.settings.ocrChoiceMade) return
    AlertDialog(
        onDismissRequest = { }, title = { Text("从第一张课表开始") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("可以先手动填写，也可以下载本地识别模型。以后随时能在设置中切换。")
            OcrModelSection(viewModel, onboarding = true, onDownload = onConfigure)
            TextButton(onClick = { viewModel.updateSettings { it.copy(ocrChoiceMade = true, useLocalOcr = false) }; onConfigure() }) { Text("配置云端图片识别") }
        } },
        confirmButton = { TextButton(onClick = {
            viewModel.updateSettings { it.copy(ocrChoiceMade = true, useLocalOcr = false) }
            onManual()
        }) { Text("先手动填写") } },
        dismissButton = { TextButton(onClick = {
            viewModel.updateSettings { it.copy(ocrChoiceMade = true, useLocalOcr = false) }
        }) { Text("先浏览课表") } },
    )
}
