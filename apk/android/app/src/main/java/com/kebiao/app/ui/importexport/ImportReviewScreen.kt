package com.kebiao.app.ui.importexport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kebiao.app.domain.WeekSelection
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.imports.ImportValidation
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.DraftField
import com.kebiao.app.ui.components.CourseTimeDialog
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ImportReviewScreen(
    drafts: List<CourseDraft>,
    padding: PaddingValues = PaddingValues(),
    saving: Boolean = false,
    imageUri: android.net.Uri? = null,
    parityEnabled: Boolean = true,
    periodCount: Int = 12,
    detectedPeriods: List<com.kebiao.app.notifications.LessonPeriod>? = null,
    onApplyPeriods: () -> Unit = {},
    removed: Pair<Int, CourseDraft>? = null,
    onRemove: (String) -> Unit,
    onUndoRemove: () -> Unit,
    onChange: (List<CourseDraft>) -> Unit,
    onConfirm: (List<CourseDraft>) -> Unit,
) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val compact = LocalConfiguration.current.screenHeightDp < 500
    var expandedIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var onlyIncomplete by rememberSaveable { mutableStateOf(false) }
    var showImage by remember { mutableStateOf(false) }
    var showWeeks by remember { mutableStateOf(false) }
    val invalid = drafts.filter { ImportValidation.validate(it, periodCount).isNotEmpty() }
    val shown = if (onlyIncomplete) drafts.filter { it in invalid || it.reviewId in expandedIds } else drafts
    fun update(id: String, transform: CourseDraft.() -> CourseDraft) {
        onChange(drafts.map { if (it.reviewId == id) it.transform() else it })
    }
    fun applyResults() {
        focus.clearFocus(); keyboard?.hide()
        if (invalid.isEmpty()) onConfirm(drafts)
        else {
            onlyIncomplete = true
            expandedIds = arrayListOf(invalid.first().reviewId)
            scope.launch { list.animateScrollToItem(0) }
        }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("review-list"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "overview") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${drafts.size} 门课程，一次应用", style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (!compact) Text("先看摘要，有误再展开修改。应用后追加到现有课表。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!onlyIncomplete, { onlyIncomplete = false }, label = { Text("全部 ${drafts.size}") })
                        FilterChip(onlyIncomplete, { onlyIncomplete = true; expandedIds = ArrayList(expandedIds.filter { id -> invalid.any { it.reviewId == id } }) }, label = { Text("待补充 ${invalid.size}") })
                        if (imageUri != null) TextButton(onClick = { showImage = true }) { Text("查看原图") }
                        TextButton(onClick = { showWeeks = true }, enabled = drafts.isNotEmpty() && !saving) { Text("统一周次") }
                    }
                }
            }
            if (shown.isEmpty()) item(key = "empty") {
                Text(if (drafts.isEmpty()) { if (removed != null) "已移除全部课程，可撤销刚才的移除。" else "没有待应用课程。" } else "没有待补充课程，可以直接应用。", Modifier.padding(vertical = 20.dp))
            }
            detectedPeriods?.takeIf { it.size >= 2 }?.let { periods ->
                item(key = "periods") {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("识别到 ${periods.size} 节作息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "第 1 节 ${periods.first().start}–${periods.first().end}，第 ${periods.size} 节 ${periods.last().start}–${periods.last().end}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text("图片里的作息时间可以覆盖「设置 → 每日作息」，课程卡片的提醒与小组件都会跟着更新。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onApplyPeriods, enabled = !saving, modifier = Modifier.testTag("apply-periods")) { Text("应用为每日作息") }
                        }
                    }
                }
            }
            itemsIndexed(shown, key = { _, draft -> draft.reviewId }) { _, draft ->
                val index = drafts.indexOfFirst { it.reviewId == draft.reviewId }
                val expanded = draft.reviewId in expandedIds
                val issues = ImportValidation.validate(draft, periodCount)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Surface(onClick = {
                        focus.clearFocus(); keyboard?.hide()
                        expandedIds = ArrayList(if (expanded) expandedIds - draft.reviewId else expandedIds + draft.reviewId)
                    }, enabled = !saving, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().testTag("review-card-$index").semantics { stateDescription = if (expanded) "已展开" else "已折叠" }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${index + 1}".padStart(2, '0'), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(draft.name.value.ifBlank { "课程名称待补充" }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "收起课程" else "展开课程", tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(timeSummary(draft), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            val place = listOfNotNull(draft.building.value, draft.room.value, draft.locationNote.value).filter { it.isNotBlank() }.joinToString(" · ")
                            Text(place.ifBlank { "地点未提供" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(listOfNotNull(draft.teacher.value?.takeIf { it.isNotBlank() }?.let { "教师 $it" },
                                "${draft.weekRule.value?.label() ?: "周规则待补充"} · ${draft.weeks.value?.takeIf { it.isNotBlank() }?.let { "$it 周" } ?: "全部周次"}").joinToString("  |  "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            draft.courseNote.value?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (issues.isNotEmpty()) Text("需补充：" + issues.map { issueLabel(it.field) }.distinct().joinToString("、"),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                            else if (hasUncertainValues(draft)) Text("部分文字建议核对", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (expanded) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            var editTime by remember { mutableStateOf(false) }
                            ReviewField("课程名称", draft.name, saving, issues.any { it.field == "name" }) { value -> update(draft.reviewId) { copy(name = name.edited(value)) } }
                            OutlinedButton(onClick = { editTime = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("编辑时段 · ${timeSummary(draft)}")
                            }
                            if (editTime) CourseTimeDialog(draft.weekday.value, draft.startPeriod.value, draft.endPeriod.value, periodCount,
                                onDismiss = { editTime = false }, onConfirm = { day, start, end ->
                                    update(draft.reviewId) { copy(weekday = weekday.edited(day), startPeriod = startPeriod.edited(start), endPeriod = endPeriod.edited(end)) }; editTime = false
                                })
                            WeekRuleChoice(draft.weekRule.value, enabled = !saving) { value -> update(draft.reviewId) { copy(weekRule = weekRule.edited(value)) } }
                            if (!parityEnabled) Text("单双周功能已关闭，保存的规则会保留，目前按每周显示。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ReviewField("教学楼", draft.building, saving, modifier = Modifier.weight(1f)) { value -> update(draft.reviewId) { copy(building = building.edited(value)) } }
                                ReviewField("教室", draft.room, saving, modifier = Modifier.weight(1f)) { value -> update(draft.reviewId) { copy(room = room.edited(value)) } }
                            }
                            ReviewField("地点备注", draft.locationNote, saving) { value -> update(draft.reviewId) { copy(locationNote = locationNote.edited(value)) } }
                            ReviewField("教师", draft.teacher, saving) { value -> update(draft.reviewId) { copy(teacher = teacher.edited(value)) } }
                            ReviewField("周次（空为全部，如 1-16）", draft.weeks, saving, issues.any { it.field == "weeks" }) { value -> update(draft.reviewId) { copy(weeks = weeks.edited(value)) } }
                            ReviewField("课程备注", draft.courseNote, saving, multiline = true) { value -> update(draft.reviewId) { copy(courseNote = courseNote.edited(value)) } }
                            issues.forEach { Text(it.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = {
                                    focus.clearFocus(); keyboard?.hide(); onRemove(draft.reviewId)
                                }, enabled = !saving) { Text("移除此课程", color = MaterialTheme.colorScheme.error) }
                                TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); expandedIds = ArrayList(expandedIds - draft.reviewId) }) { Text("收起") }
                            }
                        }
                    }
                }
            }
        }
        Surface(shadowElevation = 6.dp, tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                removed?.let { (_, draft) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("已移除 ${draft.name.value}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onUndoRemove, enabled = !saving) { Text("撤销移除") }
                    }
                }
                Text(if (invalid.isEmpty()) "将追加 ${drafts.size} 门课程" else "${invalid.size} 门课程仍需补充，点击下方按钮定位", style = MaterialTheme.typography.bodySmall,
                    color = if (invalid.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                Button(onClick = ::applyResults, enabled = !saving && drafts.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (saving) "正在应用…" else "应用识别结果")
                }
            }
        }
    }
    if (showImage && imageUri != null) AlertDialog(onDismissRequest = { showImage = false }, title = { Text("课表原图") },
        text = { ImportImagePreview(imageUri) }, confirmButton = { TextButton(onClick = { showImage = false }) { Text("关闭") } })
    if (showWeeks) {
        var rule by remember { mutableStateOf<WeekRule?>(null) }
        var weeks by remember { mutableStateOf(drafts.map { it.weeks.value.orEmpty() }.distinct().singleOrNull().orEmpty()) }
        var changeWeeks by remember { mutableStateOf(false) }
        val valid = runCatching { WeekSelection.parse(weeks) }.isSuccess
        AlertDialog(onDismissRequest = { showWeeks = false }, title = { Text("统一周次") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("bulk-weeks-dialog"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("只统一你选择修改的内容，应用到本次 ${drafts.size} 门课程。")
                FilterChip(rule == null, { rule = null }, label = { Text("保留各自周规则") })
                WeekRuleChoice(rule, true) { rule = it }
                OutlinedTextField(weeks, { weeks = it; changeWeeks = true }, label = { Text("周次（空为全部，如 1-16）") }, isError = !valid,
                    supportingText = { Text(if (!valid) "请输入有效周次，如 1-8,10-16" else if (changeWeeks) "将统一更新周次范围" else "未修改时保留各自周次范围") })
                TextButton(onClick = { weeks = ""; changeWeeks = true }) { Text("设为全部周次") }
            }
        }, confirmButton = { Button(onClick = {
            onChange(drafts.map { it.copy(weekRule = rule?.let { value -> it.weekRule.edited(value) } ?: it.weekRule,
                weeks = if (changeWeeks) it.weeks.edited(weeks) else it.weeks) }); showWeeks = false
        }, enabled = valid && !saving) { Text("应用到 ${drafts.size} 门课程") } }, dismissButton = { TextButton(onClick = { showWeeks = false }) { Text("取消") } })
    }
}

private fun <T> DraftField<T>.edited(value: T): DraftField<T> = copy(value = value, confidence = 1f, confirmed = true)

@Composable
private fun <T> ReviewField(label: String, field: DraftField<T>, saving: Boolean, error: Boolean = false,
    modifier: Modifier = Modifier, multiline: Boolean = false, onEdit: (String) -> Unit) {
    OutlinedTextField(field.value?.toString().orEmpty(), onEdit, label = { Text(label) }, enabled = !saving,
        modifier = modifier.fillMaxWidth(), isError = error, singleLine = !multiline,
        keyboardOptions = KeyboardOptions(imeAction = if (multiline) ImeAction.Default else ImeAction.Next))
}

@Composable
private fun WeekRuleChoice(value: WeekRule?, enabled: Boolean, onValue: (WeekRule) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeekRule.entries.forEach { rule -> FilterChip(value == rule, { onValue(rule) }, enabled = enabled, label = { Text(rule.label()) }) }
    }
}

private fun timeSummary(draft: CourseDraft): String {
    val day = draft.weekday.value?.takeIf { it in 1..7 }?.let { "周${"一二三四五六日"[it - 1]}" } ?: "星期待补充"
    val start = draft.startPeriod.value; val end = draft.endPeriod.value
    return "$day · " + if (start != null && end != null) "第 $start–$end 节" else "节次待补充"
}

private fun hasUncertainValues(draft: CourseDraft) = listOf(draft.name, draft.weekday, draft.startPeriod, draft.endPeriod,
    draft.weekRule, draft.building, draft.room, draft.teacher, draft.locationNote, draft.weeks, draft.courseNote)
    .any { it.value != null && it.value.toString().isNotBlank() && it.confidence < .8f }

private fun issueLabel(field: String) = when (field) { "name" -> "课程名称"; "weekday" -> "星期"; "startPeriod", "endPeriod" -> "时段"; "weekRule", "weeks" -> "周次"; else -> field }
private fun WeekRule.label() = when (this) { WeekRule.ALL -> "每周"; WeekRule.ODD -> "单周"; WeekRule.EVEN -> "双周" }
