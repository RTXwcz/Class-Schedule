package com.kebiao.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A finite, snapping wheel. Center item is selected; buttons support TalkBack and tests. */
@Composable
fun NumberWheel(label: String, values: IntRange, value: Int, onValue: (Int) -> Unit, modifier: Modifier = Modifier, onScrolling: (Boolean) -> Unit = {}, format: (Int) -> String = { it.toString() }) {
    val selected = value.coerceIn(values)
    val list = rememberLazyListState(selected - values.first)
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onValue)
    val range by rememberUpdatedState(values)
    val scrollCallback by rememberUpdatedState(onScrolling)
    val wheelHeight = if (LocalConfiguration.current.screenHeightDp < 500) 120.dp else 200.dp
    LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress }.distinctUntilChanged().collect { scrollCallback(it) } }
    LaunchedEffect(selected, values.first, values.last) {
        if (!list.isScrollInProgress && list.firstVisibleItemIndex != selected - values.first) list.scrollToItem(selected - values.first)
    }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.firstVisibleItemIndex }
            .filter { !it.first }.distinctUntilChanged().collect { (_, index) ->
                callback((range.first + index).coerceIn(range))
            }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().height(wheelHeight).testTag("wheel-$label").semantics {
            contentDescription = label
            stateDescription = format(selected)
            customActions = listOf(
                CustomAccessibilityAction("增加$label") { if (selected < values.last) onValue(selected + 1); true },
                CustomAccessibilityAction("减少$label") { if (selected > values.first) onValue(selected - 1); true },
            )
        }) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .09f), RoundedCornerShape(12.dp)))
            LazyColumn(state = list, flingBehavior = rememberSnapFlingBehavior(list), contentPadding = PaddingValues(vertical = (wheelHeight - 40.dp) / 2),
                modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                items(values.count(), key = { values.first + it }) { index ->
                    val number = values.first + index
                    Box(Modifier.fillMaxWidth().height(40.dp).clickable {
                        onValue(number); scope.launch { list.animateScrollToItem(index) }
                    }, contentAlignment = Alignment.Center) {
                        Text(format(number), fontSize = if (number == selected) 24.sp else 19.sp,
                            fontWeight = if (number == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (number == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f))
                    }
                }
            }
        }
    }
}

@Composable
fun DateWheelDialog(initial: LocalDate, title: String = "选择日期", onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    var day by remember { mutableIntStateOf(initial.dayOfMonth) }
    var moving by remember { mutableStateOf(emptySet<String>()) }
    fun setMoving(name: String, active: Boolean) { moving = if (active) moving + name else moving - name }
    val days = LocalDate.of(year, month, 1).lengthOfMonth()
    val date = LocalDate.of(year, month, day.coerceAtMost(days))
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA)),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberWheel("年", minOf(1900, initial.year)..maxOf(2100, initial.year), year, {
                        year = it; day = day.coerceAtMost(LocalDate.of(year, month, 1).lengthOfMonth())
                    }, Modifier.weight(1.25f), onScrolling = { setMoving("year", it) })
                    NumberWheel("月", 1..12, month, {
                        month = it; day = day.coerceAtMost(LocalDate.of(year, month, 1).lengthOfMonth())
                    }, Modifier.weight(1f), onScrolling = { setMoving("month", it) })
                    NumberWheel("日", 1..days, date.dayOfMonth, { day = it }, Modifier.weight(1f), onScrolling = { setMoving("day", it) })
                }
            }
        }, confirmButton = { Button(onClick = { onConfirm(date) }, enabled = moving.isEmpty()) { Text("确定日期") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun TimeWheelDialog(initial: LocalTime, title: String = "选择时间", onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit) {
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var hourMoving by remember { mutableStateOf(false) }
    var minuteMoving by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberWheel("时", 0..23, hour, { hour = it }, Modifier.weight(1f), onScrolling = { hourMoving = it }) { "%02d".format(it) }
            Text(":", style = MaterialTheme.typography.headlineMedium)
            NumberWheel("分", 0..59, minute, { minute = it }, Modifier.weight(1f), onScrolling = { minuteMoving = it }) { "%02d".format(it) }
        }
    }, confirmButton = { Button(onClick = { onConfirm(LocalTime.of(hour, minute)) }, enabled = !hourMoving && !minuteMoving) { Text("确定时间") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
fun DateWheelField(label: String, value: LocalDate?, onValue: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SelectionField(label, value?.format(DateTimeFormatter.ofPattern("yyyy年M月d日")) ?: "选择日期", Icons.Outlined.CalendarMonth, { open = true }, modifier)
    if (open) DateWheelDialog(value ?: LocalDate.now(), label, { open = false }, { onValue(it); open = false })
}

@Composable
fun TimeWheelField(label: String, value: LocalTime?, onValue: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SelectionField(label, value?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "选择时间", Icons.Outlined.Schedule, { open = true }, modifier)
    if (open) TimeWheelDialog(value ?: LocalTime.of(8, 0), label, { open = false }, { onValue(it); open = false })
}

@Composable
private fun SelectionField(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 72.dp).semantics { contentDescription = label },
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
