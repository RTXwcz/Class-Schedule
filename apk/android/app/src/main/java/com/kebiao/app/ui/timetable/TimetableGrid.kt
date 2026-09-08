package com.kebiao.app.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.EffectiveCourse
import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.notifications.LessonPeriod
import com.kebiao.app.ui.components.courseColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private val COURSE_WIDTH = 196.dp
private val DAY_GAP = 8.dp
private val GUTTER_WIDTH = 58.dp
private val HEADER_HEIGHT = 52.dp
private val MIN_PERIOD_HEIGHT = 56.dp

private data class PlacedCourse(val course: Course, val lane: Int)
private data class DayGrid(val date: LocalDate, val courses: List<PlacedCourse>, val laneCount: Int)

/** Fixed reading scale: the viewport scrolls over the grid; it never squeezes seven days to fit. */
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
    val courseWidth = (196f * scale).dp
    val dayGap = (8f * scale).dp
    val gutterWidth = (58f * scale).dp
    val headerHeight = (52f * scale).dp
    val minimumPeriodHeight = (56f * scale).dp
    val titleStyle = MaterialTheme.typography.titleSmall.copy(fontSize = (13f * scale).sp, lineHeight = (17f * scale).sp, fontWeight = FontWeight.SemiBold)
    val detailStyle = MaterialTheme.typography.bodySmall.copy(fontSize = (10f * scale).sp, lineHeight = (13f * scale).sp)
    val grids = remember(monday, days) { days.mapIndexed { offset, courses ->
        val ends = mutableListOf<Int>()
        val placed = courses.sortedWith(compareBy<EffectiveCourse> { it.course.startPeriod }.thenBy { it.course.endPeriod }.thenBy { it.course.id }).map {
            val course = it.course
            val lane = ends.indexOfFirst { end -> end < course.startPeriod }.let { index -> if (index < 0) ends.size else index }
            if (lane == ends.size) ends.add(course.endPeriod) else ends[lane] = course.endPeriod
            PlacedCourse(course, lane)
        }
        DayGrid(monday.plusDays(offset.toLong()), placed, ends.size.coerceAtLeast(1))
    } }
    val textWidth = with(density) { (courseWidth - (28f * scale).dp).roundToPx() }
    val minimumHeight = with(density) { minimumPeriodHeight.roundToPx() }
    // All periods share one height. Long names/locations and larger system fonts add room
    // without changing the font size or breaking cross-day and multi-period alignment.
    val rowPixels = grids.flatMap { it.courses }.maxOfOrNull { placed ->
        val course = placed.course
        val details = courseDetails(course, parityEnabled, course.endPeriod - course.startPeriod + 1)
        val titleHeight = measurer.measure(course.name, titleStyle, constraints = Constraints(maxWidth = textWidth)).size.height
        val detailsHeight = details.sumOf { measurer.measure(it, detailStyle, constraints = Constraints(maxWidth = textWidth)).size.height }
        val extra = with(density) { ((18f * scale).dp + (3f * scale * details.size).dp).roundToPx() }
        ceil((titleHeight + detailsHeight + extra).toDouble() / (course.endPeriod - course.startPeriod + 1)).toInt()
    }?.coerceAtLeast(minimumHeight) ?: minimumHeight
    val rowHeight = with(density) { rowPixels.toDp() }
    val formatter = remember { DateTimeFormatter.ofPattern("MM/dd") }
    var positionedDate by rememberSaveable { mutableStateOf<String?>(null) }
    var handledRequest by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(selectedDate, scrollRequest) {
        if (positionedDate != selectedDate.toString() || handledRequest != scrollRequest) {
            withFrameNanos { }
            val day = selectedDate.dayOfWeek.value - 1
            val target = grids.take(day).fold(0.dp) { width, item -> width + courseWidth * item.laneCount + dayGap }
            horizontal.scrollTo(with(density) { target.roundToPx() })
            vertical.scrollTo(0)
            positionedDate = selectedDate.toString()
            handledRequest = scrollRequest
        }
    }

    Column(modifier.padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth().height(headerHeight)) {
            Box(Modifier.width(gutterWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("节次", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Both horizontal layouts have identical content and viewport widths. Sharing the
            // scroll state keeps headings exactly above their columns, including after resize.
            Row(Modifier.weight(1f).horizontalScroll(horizontal, enabled = false), horizontalArrangement = Arrangement.spacedBy(dayGap)) {
                grids.forEach { grid ->
                    Row(Modifier.width(courseWidth * grid.laneCount).testTag("day-header-${grid.date.dayOfWeek.value}")) {
                        repeat(grid.laneCount) {
                            Box(Modifier.width(courseWidth).fillMaxHeight().padding((2f * scale).dp)
                                .background(if (grid.date == LocalDate.now()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Text("周${"一二三四五六日"[grid.date.dayOfWeek.value - 1]}  ${grid.date.format(formatter)}",
                                    style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                                    color = if (grid.date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vertical).testTag("timetable-vertical").padding(bottom = 16.dp)) {
            Column(Modifier.width(gutterWidth)) {
                periods.forEachIndexed { index, period ->
                    Column(Modifier.fillMaxWidth().height(rowHeight).testTag("period-${index + 1}").padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                        Text(period.start, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(period.end, fontSize = 11.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.weight(1f).horizontalScroll(horizontal).testTag("timetable-horizontal"), horizontalArrangement = Arrangement.spacedBy(dayGap)) {
                grids.forEach { grid ->
                    Box(Modifier.width(courseWidth * grid.laneCount).height(rowHeight * periods.size)) {
                        periods.indices.forEach { index -> HorizontalDivider(Modifier.offset(y = rowHeight * index), color = MaterialTheme.colorScheme.outlineVariant) }
                        grid.courses.forEach { placed ->
                            val course = placed.course
                            val colors = courseColors(course.name)
                            Card(onClick = { onCourseClick(course) }, shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.first, contentColor = colors.second),
                                modifier = Modifier.offset(x = courseWidth * placed.lane, y = rowHeight * (course.startPeriod - 1))
                                    .width(courseWidth).height(rowHeight * (course.endPeriod - course.startPeriod + 1))
                                    .testTag("course-block-${course.id}").padding((2f * scale).dp)) {
                                Column(Modifier.padding(horizontal = (10f * scale).dp, vertical = (6f * scale).dp), verticalArrangement = Arrangement.spacedBy((2f * scale).dp)) {
                                    Text(course.name, style = titleStyle, modifier = Modifier.testTag("course-title-${course.id}"))
                                    courseDetails(course, parityEnabled, course.endPeriod - course.startPeriod + 1).forEach { Text(it, style = detailStyle) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun courseDetails(course: Course, parityEnabled: Boolean, span: Int = course.endPeriod - course.startPeriod + 1): List<String> = buildList {
    val place = listOfNotNull(course.building, course.room).filter { it.isNotBlank() }.joinToString(" ")
    if (span > 1) add("第${course.startPeriod}–${course.endPeriod}节" + if (parityEnabled) " · " + when (course.weekRule) { WeekRule.ALL -> "每周"; WeekRule.ODD -> "单周"; WeekRule.EVEN -> "双周" } else "")
    if (place.isNotBlank()) add(place)
    course.teacher?.takeIf { it.isNotBlank() }?.let { add("教师 $it") }
}
