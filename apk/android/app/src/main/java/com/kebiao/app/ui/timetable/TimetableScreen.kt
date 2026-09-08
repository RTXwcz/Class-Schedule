package com.kebiao.app.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import com.kebiao.app.ui.components.*
import com.kebiao.app.notifications.PeriodSchedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.ui.AppViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TimetableScreen(viewModel: AppViewModel, padding: PaddingValues = PaddingValues()) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentMoment by produceState(java.time.ZonedDateTime.now(), lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { value = java.time.ZonedDateTime.now(); delay(30_000) }
        }
    }
    var editorCourse by remember { mutableStateOf<Course?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var gridScrollRequest by rememberSaveable { mutableIntStateOf(0) }
    var allCourses by remember { mutableStateOf(false) }
    var editorFromAllCourses by remember { mutableStateOf(false) }
    val allCoursesListState = rememberLazyListState()
    var chooseDate by remember { mutableStateOf(false) }
    fun closeCourseEditor() {
        showEditor = false
        if (editorFromAllCourses) allCourses = true
        editorFromAllCourses = false
    }
    fun jumpToDate(date: LocalDate) { viewModel.selectDate(date); gridScrollRequest++ }
    val selectedMonday = state.selectedDate.with(DayOfWeek.MONDAY)
    val formatter = remember { DateTimeFormatter.ofPattern("MM/dd") }
    val compactHeight = LocalConfiguration.current.screenHeightDp < 500
    val heroPalette = nextCoursePalette()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.padding(padding).consumeWindowInsets(padding),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (!compactHeight) Column(Modifier.padding(horizontal = 20.dp)) {
                ProductHeader("我的课表", currentMoment.toLocalDate().format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA)), action = {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        IconButton(onClick = { chooseDate = true }) { Icon(Icons.Outlined.CalendarMonth, "跳转日期", tint = MaterialTheme.colorScheme.primary) }
                    }
                })
                val today = currentMoment.toLocalDate()
                val next = (0L..7L).asSequence().flatMap { offset -> viewModel.effectiveCourses(today.plusDays(offset)).asSequence() }
                    .filter { it.course.endPeriod <= state.settings.periods.size }
                    .firstOrNull { it.date.isAfter(today) || PeriodSchedule.end(it.course.endPeriod, state.settings.periods) > currentMoment.toLocalTime() }
                val heroLead = if (next == null) "暂无即将开始的课程" else if (next.date == today && PeriodSchedule.start(next.course.startPeriod, state.settings.periods) <= currentMoment.toLocalTime()) "正在上课" else "下一节课"
                Row(Modifier.fillMaxWidth().background(Brush.linearGradient(heroPalette.take(2)), RoundedCornerShape(26.dp))
                    .clickable { if (next == null && state.courses.isNotEmpty()) allCourses = true else { editorCourse = next?.course; showEditor = true } }
                    .clearAndSetSemantics {
                        contentDescription = "$heroLead，${next?.course?.name ?: "暂无课程，点按添加"}"
                        onClick("打开课程") { if (next == null && state.courses.isNotEmpty()) allCourses = true else { editorCourse = next?.course; showEditor = true }; true }
                    }
                    .padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(heroLead, style = MaterialTheme.typography.labelMedium, color = heroPalette[3])
                        Text(next?.course?.name ?: if (state.courses.isEmpty()) "从第一门课开始" else "课程已经收好", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = heroPalette[2], maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(next?.course?.let { listOfNotNull(it.building, it.room).joinToString(" · ").ifBlank { "地点待补充" } } ?: if (state.courses.isEmpty()) "手动填写，或导入已有课表" else "在全部课程中查看周次与安排", color = heroPalette[3], style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    if (next != null) Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(PeriodSchedule.start(next.course.startPeriod, state.settings.periods).toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = heroPalette[2])
                        Text(if (next.date == today) "今天" else next.date.format(formatter), style = MaterialTheme.typography.labelMedium, color = heroPalette[3])
                    }
                }
            }
            else Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("我的课表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { jumpToDate(state.selectedDate.minusWeeks(1)) }) { Icon(Icons.Outlined.ChevronLeft, "上一周") }
                Text("${selectedMonday.format(formatter)} — ${selectedMonday.plusDays(6).format(formatter)}", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { jumpToDate(state.selectedDate.plusWeeks(1)) }) { Icon(Icons.Outlined.ChevronRight, "下一周") }
                TextButton(onClick = { jumpToDate(LocalDate.now()) }) { Text("今天") }
                TextButton(onClick = { allCourses = true }) { Text("全部课程 · ${state.courses.size}") }
                IconButton(onClick = { editorCourse = null; showEditor = true }) { Icon(Icons.Default.Add, "添加课程", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = { chooseDate = true }) { Icon(Icons.Outlined.CalendarMonth, "跳转日期") }
            }
            val weekCount = (0..6).sumOf { viewModel.effectiveCourses(selectedMonday.plusDays(it.toLong())).size }
            if (!compactHeight) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = { jumpToDate(state.selectedDate.minusWeeks(1)) }) { Icon(Icons.Outlined.ChevronLeft, "上一周") }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("${selectedMonday.format(formatter)} — ${selectedMonday.plusDays(6).format(formatter)}", style = MaterialTheme.typography.titleSmall)
                        Text("$weekCount 次课程", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { jumpToDate(state.selectedDate.plusWeeks(1)) }) { Icon(Icons.Outlined.ChevronRight, "下一周") }
                }
                Row {
                    TextButton(onClick = { jumpToDate(LocalDate.now()) }) { Text("今天") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("左右看日期 · 上下看节次", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { allCourses = true }) { Text("全部课程 · ${state.courses.size}") }
                IconButton(onClick = { editorCourse = null; showEditor = true }) { Icon(Icons.Default.Add, "添加课程", tint = MaterialTheme.colorScheme.primary) }
            }
            }
            TimetableGrid(
                monday = selectedMonday, selectedDate = state.selectedDate,
                days = (0..6).map { viewModel.effectiveCourses(selectedMonday.plusDays(it.toLong())) },
                periods = state.settings.periods, parityEnabled = state.settings.parityEnabled,
                scrollRequest = gridScrollRequest, modifier = Modifier.fillMaxWidth().weight(1f),
            ) { course -> editorCourse = course; showEditor = true }

        }
    }
    if (chooseDate) DateWheelDialog(state.selectedDate, "跳转到日期", { chooseDate = false }, { jumpToDate(it); chooseDate = false })
    if (allCourses) AlertDialog(onDismissRequest = { allCourses = false }, title = { Text("全部课程") }, text = {
        LazyColumn(Modifier.heightIn(max = 480.dp).testTag("all-courses-list"), state = allCoursesListState,
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.courses.isEmpty()) item { Text("还没有课程，点击下方添加。") }
            if (state.settings.semesterStartDate == null && state.courses.any { it.weeks.isNotEmpty() || (state.settings.parityEnabled && it.weekRule != WeekRule.ALL) }) item {
                Text("部分课程需要学期开始日期才会显示在周课表中。你可以在此管理它们，或到设置补充学期日期。", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            items(state.courses.sortedWith(compareBy<Course> { it.weekday }.thenBy { it.startPeriod }), key = { it.id }) { course ->
                Surface(onClick = { allCourses = false; editorFromAllCourses = true; editorCourse = course; showEditor = true }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(course.name, fontWeight = FontWeight.SemiBold)
                        Text("周${"一二三四五六日"[course.weekday - 1]} · 第${course.startPeriod}–${course.endPeriod}节", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }, confirmButton = { Button(onClick = { allCourses = false; editorFromAllCourses = true; editorCourse = null; showEditor = true }) { Text("添加课程") } }, dismissButton = { TextButton(onClick = { allCourses = false }) { Text("关闭") } })

    if (showEditor) {
        CourseEditorDialog(
            initial = editorCourse,
            onDismiss = ::closeCourseEditor,
            onSave = { course -> viewModel.addCourse(course); closeCourseEditor() },
            onDelete = { course -> viewModel.deleteCourse(course.id); closeCourseEditor() },
            parityEnabled = state.settings.parityEnabled,
            periodCount = state.settings.periods.size,
        )
    }
}

private fun weekRuleLabel(rule: WeekRule): String = when (rule) {
    WeekRule.ALL -> "每周"
    WeekRule.ODD -> "单周"
    WeekRule.EVEN -> "双周"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorDialog(
    initial: Course?,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (Course) -> Unit,
    parityEnabled: Boolean = true,
    periodCount: Int = 12,
) {
    var name by rememberSaveable(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var weekday by rememberSaveable(initial) { mutableStateOf((initial?.weekday ?: 1).toString()) }
    var start by rememberSaveable(initial) { mutableStateOf((initial?.startPeriod ?: 1).toString()) }
    var end by rememberSaveable(initial) { mutableStateOf((initial?.endPeriod ?: 1).toString()) }
    var building by rememberSaveable(initial) { mutableStateOf(initial?.building.orEmpty()) }
    var room by rememberSaveable(initial) { mutableStateOf(initial?.room.orEmpty()) }
    var note by rememberSaveable(initial) { mutableStateOf(initial?.locationNote.orEmpty()) }
    var teacher by rememberSaveable(initial) { mutableStateOf(initial?.teacher.orEmpty()) }
    var weeks by rememberSaveable(initial) { mutableStateOf(com.kebiao.app.domain.WeekSelection.format(initial?.weeks.orEmpty())) }
    var courseNote by rememberSaveable(initial) { mutableStateOf(initial?.courseNote.orEmpty()) }
    var weekRule by rememberSaveable(initial) { mutableStateOf(initial?.weekRule ?: WeekRule.ALL) }
    var menuExpanded by remember { mutableStateOf(false) }
    var choosePeriods by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val parsedWeekday = weekday.toIntOrNull()
    val parsedStart = start.toIntOrNull()
    val parsedEnd = end.toIntOrNull()
    val parsedWeeks = runCatching { com.kebiao.app.domain.WeekSelection.parse(weeks) }
    val valid = name.isNotBlank() && parsedWeekday in 1..7 && parsedStart in 1..periodCount && parsedEnd in 1..periodCount &&
        parsedStart != null && parsedEnd != null && parsedStart <= parsedEnd && parsedWeeks.isSuccess

    val hasChanges = name != initial?.name.orEmpty() || weekday != (initial?.weekday ?: 1).toString() ||
        start != (initial?.startPeriod ?: 1).toString() || end != (initial?.endPeriod ?: 1).toString() ||
        building != initial?.building.orEmpty() || room != initial?.room.orEmpty() || note != initial?.locationNote.orEmpty() ||
        teacher != initial?.teacher.orEmpty() || weeks != com.kebiao.app.domain.WeekSelection.format(initial?.weeks.orEmpty()) ||
        courseNote != initial?.courseNote.orEmpty() || weekRule != (initial?.weekRule ?: WeekRule.ALL)

    AlertDialog(
        onDismissRequest = {
            if (!choosePeriods && !confirmDelete && !confirmDiscard) {
                if (hasChanges) confirmDiscard = true else onDismiss()
            }
        },
        title = { Text(if (initial == null) "添加课程" else "编辑课程") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, singleLine = true)
                Surface(onClick = { choosePeriods = true }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("上课时段", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("周${"一二三四五六日"[(parsedWeekday ?: 1) - 1]} · 第 $start–$end 节", style = MaterialTheme.typography.titleMedium)
                    }
                }
                OutlinedTextField(building, { building = it }, label = { Text("教学楼") }, singleLine = true)
                OutlinedTextField(room, { room = it }, label = { Text("教室") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("地点备注") }, singleLine = true)
                OutlinedTextField(teacher, { teacher = it }, label = { Text("教师") }, singleLine = true)
                OutlinedTextField(weeks, { weeks = it }, label = { Text("周次（空为全部，如 1-16）") },
                    isError = parsedWeeks.isFailure, supportingText = { parsedWeeks.exceptionOrNull()?.message?.let { Text(it) } })
                OutlinedTextField(courseNote, { courseNote = it }, label = { Text("课程备注") })
                if (parityEnabled) ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = !menuExpanded }) {
                    OutlinedTextField(
                        value = weekRuleLabel(weekRule),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("单双周") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        WeekRule.values().forEach { option ->
                            DropdownMenuItem(text = { Text(weekRuleLabel(option)) }, onClick = { weekRule = option; menuExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                if (valid) onSave(
                    Course(
                        id = initial?.id ?: AppViewModel.newCourseId(),
                        name = name.trim(),
                        weekday = requireNotNull(parsedWeekday),
                        startPeriod = requireNotNull(parsedStart),
                        endPeriod = requireNotNull(parsedEnd),
                        weekRule = if (initial == null && !parityEnabled) WeekRule.ALL else weekRule,
                        building = building.trim().ifBlank { null },
                        room = room.trim().ifBlank { null },
                        locationNote = note.trim().ifBlank { null },
                        source = initial?.source ?: "MANUAL",
                        createdAtEpochMillis = initial?.createdAtEpochMillis ?: System.currentTimeMillis(),
                        updatedAtEpochMillis = System.currentTimeMillis(),
                        teacher = teacher.trim().ifBlank { null }, weeks = parsedWeeks.getOrThrow(),
                        courseNote = courseNote.trim().ifBlank { null },
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { confirmDelete = true }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
    if (choosePeriods) CourseTimeDialog(parsedWeekday, parsedStart, parsedEnd, periodCount,
        onDismiss = { choosePeriods = false },
        onConfirm = { selectedDay, selectedStart, selectedEnd ->
            weekday = selectedDay.toString()
            start = selectedStart.toString()
            end = selectedEnd.toString()
            choosePeriods = false
        })
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false }, title = { Text("放弃修改？") },
        text = { Text("这门课程的修改尚未保存，可以继续编辑。") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text("放弃修改", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } },
    )
    if (confirmDelete && initial != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除这门课程？") },
        text = { Text("${initial.name} 的每周安排将一并移除。") }, confirmButton = { TextButton(onClick = { onDelete(initial); confirmDelete = false }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}
