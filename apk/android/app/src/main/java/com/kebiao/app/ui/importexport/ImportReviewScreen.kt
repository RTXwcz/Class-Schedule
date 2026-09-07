package com.kebiao.app.ui.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.kebiao.app.imports.ImportValidation
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.DraftField
import com.kebiao.app.domain.model.WeekRule

@Composable
fun ImportReviewScreen(
    drafts: List<CourseDraft>,
    padding: PaddingValues = PaddingValues(),
    saving: Boolean = false,
    imageUri: android.net.Uri? = null,
    parityEnabled: Boolean = true,
    periodCount: Int = 12,
    onChange: (List<CourseDraft>) -> Unit,
    onCancel: () -> Unit,
    onConfirm: (List<CourseDraft>) -> Unit,
) {
    fun update(index: Int, transform: CourseDraft.() -> CourseDraft) {
        onChange(drafts.updated(index, transform))
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text("识别结果确认") }
        imageUri?.let { uri -> item { ImportImagePreview(uri) } }
        itemsIndexed(drafts) { index, draft ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("第 ${index + 1} 门课程")
                    ReviewField("课程名称", draft.name, saving,
                        { update(index) { copy(name = name.copy(value = it, confirmed = false)) } },
                        { update(index) { copy(name = name.copy(confirmed = it)) } })
                    ReviewField("星期 1-7", draft.weekday, saving,
                        { update(index) { copy(weekday = weekday.copy(value = it.toIntOrNull(), confirmed = false)) } },
                        { update(index) { copy(weekday = weekday.copy(confirmed = it)) } })
                    ReviewField("开始节次 1-$periodCount", draft.startPeriod, saving,
                        { update(index) { copy(startPeriod = startPeriod.copy(value = it.toIntOrNull(), confirmed = false)) } },
                        { update(index) { copy(startPeriod = startPeriod.copy(confirmed = it)) } })
                    ReviewField("结束节次 1-$periodCount", draft.endPeriod, saving,
                        { update(index) { copy(endPeriod = endPeriod.copy(value = it.toIntOrNull(), confirmed = false)) } },
                        { update(index) { copy(endPeriod = endPeriod.copy(confirmed = it)) } })
                    if (parityEnabled) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        var expanded by remember { mutableStateOf(false) }
                        Column(Modifier.weight(1f)) {
                            OutlinedButton(onClick = { expanded = true }, enabled = !saving) { Text(draft.weekRule.value?.label() ?: "周次未识别") }
                            DropdownMenu(expanded, { expanded = false }) {
                                WeekRule.entries.forEach { rule ->
                                    DropdownMenuItem(text = { Text(rule.label()) }, onClick = {
                                        update(index) { copy(weekRule = weekRule.copy(value = rule, confirmed = false)) }
                                        expanded = false
                                    })
                                }
                            }
                        }
                        Checkbox(draft.weekRule.confirmed, { update(index) { copy(weekRule = weekRule.copy(confirmed = it)) } }, enabled = !saving)
                        Text("确认")
                    }
                    ReviewField("教学楼", draft.building, saving,
                        { update(index) { copy(building = building.copy(value = it, confirmed = false)) } },
                        { update(index) { copy(building = building.copy(confirmed = it)) } })
                    ReviewField("教室", draft.room, saving,
                        { update(index) { copy(room = room.copy(value = it, confirmed = false)) } },
                        { update(index) { copy(room = room.copy(confirmed = it)) } })
                    ReviewField("地点备注", draft.locationNote, saving,
                        { update(index) { copy(locationNote = locationNote.copy(value = it, confirmed = false)) } },
                        { update(index) { copy(locationNote = locationNote.copy(confirmed = it)) } })
                    ReviewField("教师", draft.teacher, saving,
                        { update(index) { copy(teacher = teacher.copy(value = it, confirmed = false)) } },
                        { update(index) { copy(teacher = teacher.copy(confirmed = it)) } })
                    ReviewField("周次（空为全部，如 1-16）", draft.weeks, saving,
                        { update(index) { copy(weeks = weeks.copy(value = it, confirmed = false)) } },
                        { update(index) { copy(weeks = weeks.copy(confirmed = it)) } })
                    ReviewField("课程备注", draft.courseNote, saving,
                        { update(index) { copy(courseNote = courseNote.copy(value = it, confirmed = false)) } },
                        { update(index) { copy(courseNote = courseNote.copy(confirmed = it)) } })
                    draft.validationErrors().forEach { Text(it.message) }
                    if ((draft.endPeriod.value ?: 0) > periodCount) Text("课程超出当前每天 $periodCount 节，请先调整作息或修改节次")
                    TextButton(onClick = { onChange(drafts.filterIndexed { i, _ -> i != index }) }, enabled = !saving) { Text("移除此课程") }
                }
            }
        }
        item {
            Button(onClick = { onConfirm(drafts) }, enabled = !saving && ImportValidation.canPersist(drafts, periodCount), modifier = Modifier.fillMaxWidth()) {
                Text(if (saving) "正在保存" else "追加 ${drafts.size} 门课程")
            }
            TextButton(onClick = onCancel, enabled = !saving) { Text("放弃此次导入") }
        }
    }
}

private fun List<CourseDraft>.updated(index: Int, transform: CourseDraft.() -> CourseDraft): List<CourseDraft> = mapIndexed { i, item -> if (i == index) item.transform() else item }

@Composable
private fun <T> ReviewField(label: String, field: DraftField<T>, saving: Boolean, onEdit: (String) -> Unit, onConfirm: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(field.value?.toString().orEmpty(), onEdit, label = { Text(label) }, enabled = !saving, modifier = Modifier.weight(1f))
        Checkbox(field.confirmed, onConfirm, enabled = !saving)
        Text("确认")
    }
}

private fun WeekRule.label() = when (this) {
    WeekRule.ALL -> "每周"
    WeekRule.ODD -> "单周"
    WeekRule.EVEN -> "双周"
}
