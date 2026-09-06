package com.kebiao.app.ui.timetable

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.ui.AppViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TimetableScreen(viewModel: AppViewModel, padding: PaddingValues = PaddingValues()) {
    val state = viewModel.uiState.value
    var editorCourse by remember { mutableStateOf<Course?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val selectedMonday = state.selectedDate.with(DayOfWeek.MONDAY)
    val formatter = remember { DateTimeFormatter.ofPattern("MM/dd") }

    Scaffold(
        modifier = Modifier.padding(padding),
        floatingActionButton = {
            FloatingActionButton(onClick = { editorCourse = null; showEditor = true }) { Text("+") }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { viewModel.selectDate(state.selectedDate.minusWeeks(1)) }) { Text("上一周") }
                Text("${selectedMonday.format(formatter)} 周课表")
                TextButton(onClick = { viewModel.selectDate(state.selectedDate.plusWeeks(1)) }) { Text("下一周") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (0..6).forEach { offset ->
                    val date = selectedMonday.plusDays(offset.toLong())
                    DayColumn(date, viewModel, formatter) { course -> editorCourse = course; showEditor = true }
                }
            }
        }
    }

    if (showEditor) {
        CourseEditorDialog(
            initial = editorCourse,
            onDismiss = { showEditor = false },
            onSave = { course -> viewModel.addCourse(course); showEditor = false },
            onDelete = { course -> viewModel.deleteCourse(course.id); showEditor = false },
        )
    }
}

@Composable
private fun DayColumn(
    date: LocalDate,
    viewModel: AppViewModel,
    formatter: DateTimeFormatter,
    onCourseClick: (Course) -> Unit,
) {
    val courses = viewModel.effectiveCourses(date)
    val dayLabel = when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
    Column(modifier = Modifier.width(148.dp)) {
        Text("$dayLabel ${date.format(formatter)}", modifier = Modifier.padding(6.dp))
        (1..12).forEach { period ->
            val course = courses.firstOrNull { it.course.startPeriod == period }
            if (course != null) {
                val span = course.course.endPeriod - course.course.startPeriod + 1
                Card(
                    modifier = Modifier.fillMaxWidth().height((span * 52).dp).padding(vertical = 2.dp),
                    onClick = { onCourseClick(course.course) },
                    colors = CardDefaults.cardColors(containerColor = if (course.hasConflict) CardDefaults.cardColors().containerColor else CardDefaults.cardColors().containerColor),
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(course.course.name)
                        Text("第${course.course.startPeriod}-${course.course.endPeriod}节")
                        val location = listOfNotNull(course.course.building, course.course.room).joinToString(" ")
                        if (location.isNotBlank()) Text(location)
                        course.course.locationNote?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Text(weekRuleLabel(course.course.weekRule))
                    }
                }
            } else {
                Spacer(modifier = Modifier.fillMaxWidth().height(52.dp).padding(vertical = 2.dp))
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
private fun CourseEditorDialog(
    initial: Course?,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit,
    onDelete: (Course) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var weekday by remember(initial) { mutableStateOf((initial?.weekday ?: 1).toString()) }
    var start by remember(initial) { mutableStateOf((initial?.startPeriod ?: 1).toString()) }
    var end by remember(initial) { mutableStateOf((initial?.endPeriod ?: 1).toString()) }
    var building by remember(initial) { mutableStateOf(initial?.building.orEmpty()) }
    var room by remember(initial) { mutableStateOf(initial?.room.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.locationNote.orEmpty()) }
    var weekRule by remember(initial) { mutableStateOf(initial?.weekRule ?: WeekRule.ALL) }
    var menuExpanded by remember { mutableStateOf(false) }
    val parsedWeekday = weekday.toIntOrNull()?.coerceIn(1, 7) ?: 1
    val parsedStart = start.toIntOrNull()?.coerceIn(1, 12) ?: 1
    val parsedEnd = end.toIntOrNull()?.coerceIn(parsedStart, 12) ?: parsedStart

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加课程" else "编辑课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(weekday, { weekday = it.filter(Char::isDigit) }, label = { Text("星期 1-7") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(start, { start = it.filter(Char::isDigit) }, label = { Text("起始节") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it.filter(Char::isDigit) }, label = { Text("结束节") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(building, { building = it }, label = { Text("教学楼") }, singleLine = true)
                OutlinedTextField(room, { room = it }, label = { Text("教室") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("地点备注") }, singleLine = true)
                ExposedDropdownMenuBox(expanded = menuExpanded, onExpandedChange = { menuExpanded = !menuExpanded }) {
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
            Button(onClick = {
                if (name.isNotBlank()) onSave(
                    Course(
                        id = initial?.id ?: AppViewModel.newCourseId(),
                        name = name.trim(),
                        weekday = parsedWeekday,
                        startPeriod = parsedStart,
                        endPeriod = parsedEnd,
                        weekRule = weekRule,
                        building = building.trim().ifBlank { null },
                        room = room.trim().ifBlank { null },
                        locationNote = note.trim().ifBlank { null },
                        source = initial?.source ?: "MANUAL",
                        createdAtEpochMillis = initial?.createdAtEpochMillis ?: System.currentTimeMillis(),
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    ),
                )
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { onDelete(initial) }) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
