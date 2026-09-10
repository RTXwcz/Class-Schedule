package com.kebiao.app.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kebiao.app.domain.WeekSelection
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.EffectiveCourse
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.notifications.LessonPeriod
import com.kebiao.app.ui.components.courseColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private data class PlacedCourse(val course: Course, val lane: Int)
private data class DayGrid(val date: LocalDate, val courses: List<PlacedCourse>, val laneCount: Int)

/**
 * Fixed reading canvas. The viewport scrolls over the grid, so seven days are never squeezed
 * into one screen and a long title never shrinks the type to a single character per line.
 */
@Composable
internal fun TimetableGrid(
    monday: LocalDate,
    selectedDate: LocalDate,
    days: List<List<EffectiveCourse>>,
    periods: List<LessonPeriod>,
    parityEnabled: Boolean,
    scrollRequest: Int,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
    onCourseClick: (Course) -> Unit,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val compact = scale < .95f
    val courseWidth = (196f * scale).dp
    val dayGap = if (compact) 4.dp else 6.dp
    val gutterWidth = if (compact) 44.dp else 50.dp
    val headerHeight = if (compact) 36.dp else 40.dp
    val titleStyle = MaterialTheme.typography.titleSmall.copy(
        fontSize = if (compact) 12.sp else 13.sp,
        lineHeight = if (compact) 16.sp else 17.sp,
        fontWeight = FontWeight.SemiBold,
    )
    val detailStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp)
    val axisTimeStyle = detailStyle.copy(fontSize = if (compact) 9.sp else 10.sp, lineHeight = 12.sp)
    val axisNumberStyle = axisTimeStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val textWidth = with(density) { (courseWidth - 16.dp).roundToPx() }
    // The gutter must always fit the period number and both clock times.
    val axisMinimum = measurer.measure("48", axisNumberStyle).size.height +
        2 * measurer.measure("23:59", axisTimeStyle).size.height + with(density) { 6.dp.roundToPx() }
    val minimumRowPixels = maxOf(axisMinimum, with(density) { (if (compact) 48.dp else 56.dp).roundToPx() })

    val grids = remember(monday, days) {
        days.mapIndexed { offset, effective ->
            val laneEnds = mutableListOf<Int>()
            val placed = effective
                .sortedWith(compareBy<EffectiveCourse> { it.course.startPeriod }.thenBy { it.course.endPeriod }.thenBy { it.course.id })
                .map { item ->
                    val lane = laneEnds.indexOfFirst { end -> end < item.course.startPeriod }
                        .let { if (it < 0) laneEnds.size else it }
                    if (lane == laneEnds.size) laneEnds += item.course.endPeriod else laneEnds[lane] = item.course.endPeriod
                    PlacedCourse(item.course, lane)
                }
            DayGrid(monday.plusDays(offset.toLong()), placed, laneEnds.size.coerceAtLeast(1))
        }
    }

    // Grow only the periods that actually hold the content. Empty rows stay at the minimum,
    // and every day shares the same boundaries so parallel courses remain aligned.
    val rowPixels = IntArray(periods.size) { minimumRowPixels }
    grids.flatMap { it.courses }
        .filter { it.course.endPeriod <= periods.size }
        .sortedBy { it.course.endPeriod - it.course.startPeriod }
        .forEach { placed ->
            val course = placed.course
            val details = courseDetails(course, parityEnabled)
            val titleHeight = measurer.measure(course.name, titleStyle, constraints = Constraints(maxWidth = textWidth)).size.height
            val detailsHeight = details.sumOf {
                measurer.measure(it, detailStyle, constraints = Constraints(maxWidth = textWidth)).size.height
            }
            val needed = titleHeight + detailsHeight + with(density) { (14 + details.size * 2).dp.roundToPx() }
            val rows = (course.startPeriod - 1) until course.endPeriod
            val deficit = needed - rows.sumOf { rowPixels[it] }
            if (deficit > 0) rows.forEach { rowPixels[it] += ceil(deficit.toDouble() / rows.count()).toInt() }
        }
    val offsets = rowPixels.runningFold(0) { total, value -> total + value }
    val rowHeights = rowPixels.map { with(density) { it.toDp() } }
    val rowOffsets = offsets.map { with(density) { it.toDp() } }
    val formatter = remember { DateTimeFormatter.ofPattern("MM/dd") }

    var positionedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var handledRequest by rememberSaveable { mutableIntStateOf(-1) }
    var previousScale by remember { mutableStateOf(scale) }
    LaunchedEffect(selectedDate, scrollRequest, scale) {
        if (positionedDate != selectedDate.toString() || handledRequest != scrollRequest) {
            withFrameNanos { }
            val target = grids.take(selectedDate.dayOfWeek.value - 1)
                .fold(0.dp) { total, grid -> total + courseWidth * grid.laneCount + dayGap }
            horizontal.scrollTo(with(density) { target.roundToPx() })
            vertical.scrollTo(0)
            positionedDate = selectedDate.toString()
            handledRequest = scrollRequest
        } else if (previousScale != scale) {
            horizontal.scrollTo((horizontal.value * scale / previousScale).toInt())
        }
        previousScale = scale
    }

    Column(modifier.padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(headerHeight)) {
            Box(Modifier.width(gutterWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("节次", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The heading shares the body's scroll state, so a column is always above its day.
            Row(Modifier.weight(1f).horizontalScroll(horizontal, enabled = false), horizontalArrangement = Arrangement.spacedBy(dayGap)) {
                grids.forEach { grid ->
                    Row(Modifier.width(courseWidth * grid.laneCount).testTag("day-header-${grid.date.dayOfWeek.value}")) {
                        repeat(grid.laneCount) {
                            Box(
                                Modifier.width(courseWidth).fillMaxHeight().padding(2.dp)
                                    .background(
                                        if (grid.date == LocalDate.now()) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                        RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "周${"一二三四五六日"[grid.date.dayOfWeek.value - 1]}  ${grid.date.format(formatter)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    textAlign = TextAlign.Center,
                                    color = if (grid.date == LocalDate.now()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vertical).testTag("timetable-vertical").padding(bottom = 8.dp)) {
            Column(Modifier.width(gutterWidth)) {
                periods.forEachIndexed { index, period ->
                    Column(
                        Modifier.fillMaxWidth().height(rowHeights[index]).testTag("period-${index + 1}").padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${index + 1}", style = axisNumberStyle)
                        Text(period.start, Modifier.testTag("period-start-${index + 1}"), style = axisTimeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(period.end, Modifier.testTag("period-end-${index + 1}"), style = axisTimeStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.weight(1f).horizontalScroll(horizontal).testTag("timetable-horizontal"), horizontalArrangement = Arrangement.spacedBy(dayGap)) {
                grids.forEach { grid ->
                    Box(Modifier.width(courseWidth * grid.laneCount).height(rowOffsets.last())) {
                        periods.indices.forEach { index ->
                            HorizontalDivider(Modifier.offset(y = rowOffsets[index]), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        grid.courses.filter { it.course.endPeriod <= periods.size }.forEach { placed ->
                            val course = placed.course
                            val colors = courseColors(course.name)
                            Card(
                                onClick = { onCourseClick(course) },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.first, contentColor = colors.second),
                                modifier = Modifier
                                    .offset(x = courseWidth * placed.lane, y = rowOffsets[course.startPeriod - 1])
                                    .width(courseWidth)
                                    .height(rowOffsets[course.endPeriod] - rowOffsets[course.startPeriod - 1])
                                    .testTag("course-block-${course.id}")
                                    .padding(2.dp),
                            ) {
                                Column(
                                    Modifier.padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 4.dp else 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(course.name, style = titleStyle, modifier = Modifier.testTag("course-title-${course.id}"))
                                    courseDetails(course, parityEnabled).forEach { Text(it, style = detailStyle) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Only real values become lines, so a course without a room does not reserve empty space. */
private fun courseDetails(course: Course, parityEnabled: Boolean): List<String> = buildList {
    val span = course.endPeriod - course.startPeriod + 1
    if (span > 1 || (parityEnabled && course.weekRule != WeekRule.ALL)) {
        add(
            "第${course.startPeriod}–${course.endPeriod}节" + if (parityEnabled) " · " + when (course.weekRule) {
                WeekRule.ALL -> "每周"
                WeekRule.ODD -> "单周"
                WeekRule.EVEN -> "双周"
            } else "",
        )
    }
    listOfNotNull(course.building, course.room, course.locationNote)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
        ?.let(::add)
    course.teacher?.takeIf { it.isNotBlank() }?.let { add("教师 $it") }
    if (course.weeks.isNotEmpty()) add(WeekSelection.format(course.weeks) + "周")
    course.courseNote?.takeIf { it.isNotBlank() }?.let(::add)
}
