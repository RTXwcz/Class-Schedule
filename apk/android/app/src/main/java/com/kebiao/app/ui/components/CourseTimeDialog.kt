package com.kebiao.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Keeps tentative wheel values local until the user confirms the complete time slot. */
@Composable
fun CourseTimeDialog(
    weekday: Int?,
    startPeriod: Int?,
    endPeriod: Int?,
    periodCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit,
    title: String = "选择上课时段",
) {
    require(periodCount > 0)
    var selectedWeekday by rememberSaveable(weekday) { mutableIntStateOf((weekday ?: 1).coerceIn(1..7)) }
    var selectedStart by rememberSaveable(startPeriod, periodCount) { mutableIntStateOf((startPeriod ?: 1).coerceIn(1..periodCount)) }
    var selectedEnd by rememberSaveable(endPeriod, periodCount) {
        mutableIntStateOf((endPeriod ?: selectedStart).coerceIn(selectedStart..periodCount))
    }
    var moving by remember { mutableStateOf(emptySet<String>()) }
    fun updateMoving(wheel: String, active: Boolean) {
        moving = if (active) moving + wheel else moving - wheel
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("周${"一二三四五六日"[selectedWeekday - 1]} · 第 $selectedStart–$selectedEnd 节",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (weekday == null || startPeriod == null || endPeriod == null) {
                    Text("时段信息尚未完整，请核对后确定。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberWheel("星期", 1..7, selectedWeekday, { selectedWeekday = it }, Modifier.weight(1f),
                        onScrolling = { updateMoving("weekday", it) }) { "周${"一二三四五六日"[it - 1]}" }
                    NumberWheel("起始节", 1..periodCount, selectedStart, { value ->
                        selectedStart = value
                        selectedEnd = selectedEnd.coerceAtLeast(value)
                    }, Modifier.weight(1f), onScrolling = { updateMoving("start", it) })
                    NumberWheel("结束节", 1..periodCount, selectedEnd, { value ->
                        selectedEnd = value
                        selectedStart = selectedStart.coerceAtMost(value)
                    }, Modifier.weight(1f),
                        onScrolling = { updateMoving("end", it) })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedWeekday, selectedStart, selectedEnd) },
                enabled = moving.isEmpty() && selectedStart <= selectedEnd) { Text("确定时段") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
