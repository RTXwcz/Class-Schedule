package com.kebiao.app.ui.timetable

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import com.kebiao.app.ui.components.*
import com.kebiao.app.notifications.PeriodSchedule
import java.time.LocalTime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.EffectiveCourse
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
    var expandedWeek by remember { mutableStateOf(false) }
    var allCourses by remember { mutableStateOf(false) }
    var chooseDate by remember { mutableStateOf(false) }
    val selectedMonday = state.selectedDate.with(DayOfWeek.MONDAY)
    val formatter = remember { DateTimeFormatter.ofPattern("MM/dd") }
    val compactHeight = LocalConfiguration.current.screenHeightDp < 500
    val rowHeight = if (compactHeight) 48 else 64

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        floatingActionButton = {
            FloatingActionButton(onClick = { editorCourse = null; showEditor = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Icon(Icons.Default.Add, contentDescription = "添加课程")
            }
        },
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
                Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF214F40), Color(0xFF356E57))), RoundedCornerShape(26.dp))
                    .clickable { if (next == null && state.courses.isNotEmpty()) allCourses = true else { editorCourse = next?.course; showEditor = true } }
                    .clearAndSetSemantics {
                        contentDescription = "$heroLead，${next?.course?.name ?: "暂无课程，点按添加"}"
                        onClick("打开课程") { if (next == null && state.courses.isNotEmpty()) allCourses = true else { editorCourse = next?.course; showEditor = true }; true }
                    }
                    .padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(heroLead, style = MaterialTheme.typography.labelMedium, color = Color(0xFFC5D9CB))
                        Text(next?.course?.name ?: if (state.courses.isEmpty()) "从第一门课开始" else "课程已经收好", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(next?.course?.let { listOfNotNull(it.building, it.room).joinToString(" · ").ifBlank { "地点待补充" } } ?: if (state.courses.isEmpty()) "手动填写，或导入已有课表" else "在全部课程中查看周次与安排", color = Color(0xFFD6E5DA), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    if (next != null) Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(PeriodSchedule.start(next.course.startPeriod, state.settings.periods).toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(if (next.date == today) "今天" else next.date.format(formatter), style = MaterialTheme.typography.labelMedium, color = Color(0xFFD6E5DA))
                    }
                }
            }
            else Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("我的课表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.selectDate(state.selectedDate.minusWeeks(1)) }) { Icon(Icons.Outlined.ChevronLeft, "上一周") }
                Text("${selectedMonday.format(formatter)} — ${selectedMonday.plusDays(6).format(formatter)}", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { viewModel.selectDate(state.selectedDate.plusWeeks(1)) }) { Icon(Icons.Outlined.ChevronRight, "下一周") }
                TextButton(onClick = { viewModel.selectDate(LocalDate.now()) }) { Text("今天") }
                TextButton(onClick = { expandedWeek = !expandedWeek }) { Text(if (expandedWeek) "整周" else "展开") }
                TextButton(onClick = { allCourses = true }) { Text("全部课程 · ${state.courses.size}") }
                IconButton(onClick = { chooseDate = true }) { Icon(Icons.Outlined.CalendarMonth, "跳转日期") }
            }
            val weekCount = (0..6).sumOf { viewModel.effectiveCourses(selectedMonday.plusDays(it.toLong())).size }
            if (!compactHeight) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.selectDate(state.selectedDate.minusWeeks(1)) }) { Icon(Icons.Outlined.ChevronLeft, "上一周") }
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("${selectedMonday.format(formatter)} — ${selectedMonday.plusDays(6).format(formatter)}", style = MaterialTheme.typography.titleSmall)
                        Text("$weekCount 次课程", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.selectDate(state.selectedDate.plusWeeks(1)) }) { Icon(Icons.Outlined.ChevronRight, "下一周") }
                }
                Row {
                    TextButton(onClick = { viewModel.selectDate(LocalDate.now()) }) { Text("今天") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FilterChip(expandedWeek, { expandedWeek = !expandedWeek }, label = { Text(if (expandedWeek) "整周" else "展开") })
                TextButton(onClick = { allCourses = true }) { Text("全部课程 · ${state.courses.size}") }
            }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val gutterWidth = 42.dp
                val compactColumnWidth = (maxWidth - gutterWidth - 26.dp) / 7
                Row(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 6.dp, end = 6.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Column(Modifier.width(gutterWidth)) {
                        Spacer(Modifier.height(44.dp))
                        state.settings.periods.forEachIndexed { index, period ->
                            Column(Modifier.fillMaxWidth().height(rowHeight.dp).testTag("period-${index + 1}").padding(top = 4.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
                                Text(period.start, fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(period.end, fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) { (0..6).forEach { offset ->
                        val date = selectedMonday.plusDays(offset.toLong())
                        DayColumn(date, viewModel.effectiveCourses(date), formatter,
                            compactWidth = if (expandedWeek) null else compactColumnWidth,
                            parityEnabled = state.settings.parityEnabled,
                            periodCount = state.settings.periods.size,
                            rowHeight = rowHeight,
                        ) { course -> editorCourse = course; showEditor = true }
                    } }
                }
            }
        }
    }
    if (chooseDate) DateWheelDialog(state.selectedDate, "跳转到日期", { chooseDate = false }, { viewModel.selectDate(it); chooseDate = false })
    if (allCourses) AlertDialog(onDismissRequest = { allCourses = false }, title = { Text("全部课程") }, text = {
        LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.courses.isEmpty()) item { Text("还没有课程，点击下方添加。") }
            if (state.settings.semesterStartDate == null && state.courses.any { it.weeks.isNotEmpty() || (state.settings.parityEnabled && it.weekRule != WeekRule.ALL) }) item {
                Text("部分课程需要学期开始日期才会显示在周课表中。你可以在此管理它们，或到设置补充学期日期。", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            items(state.courses.sortedWith(compareBy<Course> { it.weekday }.thenBy { it.startPeriod }), key = { it.id }) { course ->
                Surface(onClick = { allCourses = false; editorCourse = course; showEditor = true }, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(course.name, fontWeight = FontWeight.SemiBold)
                        Text("周${"一二三四五六日"[course.weekday - 1]} · 第${course.startPeriod}–${course.endPeriod}节", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }, confirmButton = { Button(onClick = { allCourses = false; editorCourse = null; showEditor = true }) { Text("添加课程") } }, dismissButton = { TextButton(onClick = { allCourses = false }) { Text("关闭") } })

    if (showEditor) {
        CourseEditorDialog(
            initial = editorCourse,
            onDismiss = { showEditor = false },
            onSave = { course -> viewModel.addCourse(course); showEditor = false },
            onDelete = { course -> viewModel.deleteCourse(course.id); showEditor = false },
            parityEnabled = state.settings.parityEnabled,
            periodCount = state.settings.periods.size,
        )
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    courses: List<EffectiveCourse>,
    formatter: DateTimeFormatter,
    compactWidth: Dp?,
    parityEnabled: Boolean,
    periodCount: Int,
    rowHeight: Int = 64,
    onCourseClick: (Course) -> Unit,
) {
    val dayLabel = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
    val laneEnds = mutableListOf<Int>()
    val placed = courses.sortedWith(compareBy<EffectiveCourse> { it.course.startPeriod }.thenBy { it.course.endPeriod }.thenBy { it.course.id }).map { course ->
        val lane = laneEnds.indexOfFirst { end -> end < course.course.startPeriod }.let { if (it < 0) laneEnds.size else it }
        if (lane == laneEnds.size) laneEnds.add(course.course.endPeriod) else laneEnds[lane] = course.course.endPeriod
        course to lane
    }
    val isToday = date == LocalDate.now()
    val lanes = laneEnds.size.coerceAtLeast(1)
    val laneWidth = compactWidth?.div(lanes) ?: 112.dp
    Column(modifier = Modifier.width(compactWidth ?: (112 * lanes).dp)) {
        Text(if (compactWidth != null) "$dayLabel\n${date.dayOfMonth}" else "$dayLabel ${date.format(formatter)}",
            modifier = Modifier.fillMaxWidth().height(44.dp)
                .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(10.dp)).padding(horizontal = 2.dp, vertical = 2.dp), maxLines = 2,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Box(Modifier.fillMaxWidth().height((periodCount * rowHeight).dp)) {
            (0 until periodCount).forEach { period ->
                HorizontalDivider(Modifier.offset(y = (period * rowHeight).dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            placed.forEach { (course, lane) ->
                val palette = courseColors(course.course.name)
                val span = course.course.endPeriod - course.course.startPeriod + 1
                Card(
                    modifier = Modifier.offset(x = laneWidth * lane, y = ((course.course.startPeriod - 1) * rowHeight).dp)
                        .width(laneWidth).height((span * rowHeight).dp).testTag("course-block-${course.course.id}").padding(1.dp),
                    onClick = { onCourseClick(course.course) },
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = palette.first, contentColor = palette.second,
                    ),
                ) {
                    Column(modifier = Modifier.padding(if (compactWidth != null) 3.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(course.course.name, style = if (compactWidth != null) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge,
                            maxLines = if (compactWidth != null && span > 1) 4 else 2, overflow = TextOverflow.Ellipsis)
                        if (compactWidth == null) Text("第${course.course.startPeriod}-${course.course.endPeriod}节" +
                            if (parityEnabled) " · ${weekRuleLabel(course.course.weekRule)}" else "", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        val location = listOfNotNull(course.course.building, course.course.room).joinToString(" ")
                        if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
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
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var weekday by remember(initial) { mutableStateOf((initial?.weekday ?: 1).toString()) }
    var start by remember(initial) { mutableStateOf((initial?.startPeriod ?: 1).toString()) }
    var end by remember(initial) { mutableStateOf((initial?.endPeriod ?: 1).toString()) }
    var building by remember(initial) { mutableStateOf(initial?.building.orEmpty()) }
    var room by remember(initial) { mutableStateOf(initial?.room.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.locationNote.orEmpty()) }
    var teacher by remember(initial) { mutableStateOf(initial?.teacher.orEmpty()) }
    var weeks by remember(initial) { mutableStateOf(com.kebiao.app.domain.WeekSelection.format(initial?.weeks.orEmpty())) }
    var courseNote by remember(initial) { mutableStateOf(initial?.courseNote.orEmpty()) }
    var weekRule by remember(initial) { mutableStateOf(initial?.weekRule ?: WeekRule.ALL) }
    var menuExpanded by remember { mutableStateOf(false) }
    var choosePeriods by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val parsedWeekday = weekday.toIntOrNull()
    val parsedStart = start.toIntOrNull()
    val parsedEnd = end.toIntOrNull()
    val parsedWeeks = runCatching { com.kebiao.app.domain.WeekSelection.parse(weeks) }
    val valid = name.isNotBlank() && parsedWeekday in 1..7 && parsedStart in 1..periodCount && parsedEnd in 1..periodCount &&
        parsedStart != null && parsedEnd != null && parsedStart <= parsedEnd && parsedWeeks.isSuccess

    AlertDialog(
        onDismissRequest = onDismiss,
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
    if (choosePeriods) AlertDialog(onDismissRequest = { choosePeriods = false }, title = { Text("选择上课时段") }, text = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberWheel("星期", 1..7, parsedWeekday ?: 1, { weekday = it.toString() }, Modifier.weight(1f)) { "周${"一二三四五六日"[it - 1]}" }
            NumberWheel("起始节", 1..periodCount, (parsedStart ?: 1).coerceAtMost(periodCount), { value ->
                start = value.toString(); if ((end.toIntOrNull() ?: 1) < value) end = value.toString()
            }, Modifier.weight(1f))
            NumberWheel("结束节", (parsedStart ?: 1).coerceAtMost(periodCount)..periodCount, (parsedEnd ?: 1).coerceIn((parsedStart ?: 1).coerceAtMost(periodCount), periodCount), { end = it.toString() }, Modifier.weight(1f))
        }
    }, confirmButton = { Button(onClick = { choosePeriods = false }) { Text("确定时段") } })
    if (confirmDelete && initial != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除这门课程？") },
        text = { Text("${initial.name} 的每周安排将一并移除。") }, confirmButton = { TextButton(onClick = { onDelete(initial); confirmDelete = false }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}
