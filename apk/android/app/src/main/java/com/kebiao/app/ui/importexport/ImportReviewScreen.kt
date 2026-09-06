package com.kebiao.app.ui.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kebiao.app.imports.ImportValidation
import com.kebiao.app.ocr.CourseDraft

@Composable
fun ImportReviewScreen(
    drafts: List<CourseDraft>,
    padding: PaddingValues = PaddingValues(),
    onConfirm: (List<CourseDraft>) -> Unit,
) {
    var reviewed by remember(drafts) { mutableStateOf(drafts) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("识别结果确认") }
        itemsIndexed(reviewed) { index, draft ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("第 ${index + 1} 门课程")
                    OutlinedTextField(draft.name.value, { reviewed = reviewed.updated(index) { copy(name = name.copy(value = it)) } }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth())
                    Text("星期：${draft.weekday.value ?: "未识别"}，节次：${draft.startPeriod.value ?: "?"}-${draft.endPeriod.value ?: "?"}")
                    Text("地点：${listOfNotNull(draft.building.value, draft.room.value).joinToString(" ").ifBlank { "未识别" }}")
                    Text("置信度：${"%.0f".format(draft.name.confidence * 100)}%")
                }
            }
        }
        item {
            Button(onClick = { onConfirm(reviewed.map { it.confirmAll() }) }, enabled = ImportValidation.canPersist(reviewed), modifier = Modifier.fillMaxWidth()) {
                Text("确认并写入课表")
            }
        }
    }
}

private fun List<CourseDraft>.updated(index: Int, transform: CourseDraft.() -> CourseDraft): List<CourseDraft> = mapIndexed { i, item -> if (i == index) item.transform() else item }
