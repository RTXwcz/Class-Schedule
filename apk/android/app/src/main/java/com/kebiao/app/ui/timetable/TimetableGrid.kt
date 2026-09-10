package com.kebiao.app.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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

/** Side padding between the grid and its viewport, counted when a whole week is fitted. */
internal val GRID_SIDE_PADDING = 6.dp

/** A course card keeps this much air inside its cell, and this much between its text lines. */
private val CARD_INSET = 1.dp
private val CARD_LINE_GAP = 1.dp

/**
 * Presets for the day width. [WEEK] is the overview: the real width is computed by
 * [overviewColumnWidth] so that all seven days land on screen, while the two wider presets are
 * meant to be dragged. A pinch can leave the width anywhere in between, so the resolved width is
 * always passed to [TimetableGrid] separately from the preset that owns the typography.
 */
internal enum class TimetableZoom(
    val label: String,
    val columnWidth: Dp,
    val gutterWidth: Dp,
    val dayGap: Dp,
    val headerHeight: Dp,
    val titleSize: TextUnit,
    val detailSize: TextUnit,
    val cardHorizontalPadding: Dp,
    val cardVerticalPadding: Dp,
) {
    WEEK("全览", 48.dp, 38.dp, 3.dp, 34.dp, 11.sp, 9.sp, 4.dp, 3.dp),
    STANDARD("标准", 92.dp, 44.dp, 4.dp, 36.dp, 12.sp, 10.sp, 6.dp, 4.dp),
    COMFORTABLE("宽松", 130.dp, 44.dp, 6.dp, 40.dp, 13.sp, 11.sp, 8.dp, 6.dp);

    val next: TimetableZoom get() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun nearest(width: Dp): TimetableZoom =
            entries.minBy { kotlin.math.abs(it.columnWidth.value - width.value) }

        /** Narrowest column the user can pinch down to before it snaps back to the overview. */
        val minManual: Dp get() = 40.dp
        val maxManual: Dp get() = 220.dp
    }
}

/**
 * Width of one day so that seven days, six gaps, the axis gutter and the grid padding fit into
 * [gridWidth] exactly. Floored to a whole pixel to keep the last column from being clipped.
 */
internal fun overviewColumnWidth(gridWidth: Dp, density: Density): Dp = with(density) {
    val week = TimetableZoom.WEEK
    val usable = (gridWidth - GRID_SIDE_PADDING * 2 - week.gutterWidth - week.dayGap * 6f).toPx()
    (usable.toInt() / 7).coerceAtLeast(24).toDp()
}

private data class PlacedCourse(val course: Course, val lane: Int)
private data class DayGrid(val date: LocalDate, val courses: List<PlacedCourse>, val laneCount: Int)

/**
 * Fixed reading canvas. Days and periods keep a stable size, the viewport scrolls in both axes,
 * and a two-finger pinch adjusts how wide a single day is.
 */
@Composable
internal fun TimetableGrid(
    monday: LocalDate,
    selectedDate: LocalDate,
    days: List<List<EffectiveCourse>>,
    periods: List<LessonPeriod>,
    parityEnabled: Boolean,
    scrollRequest: Int,
    zoom: TimetableZoom = TimetableZoom.WEEK,
    columnWidth: Dp = zoom.columnWidth,
    onPinchZoom: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
    onCourseClick: (Course) -> Unit,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val courseWidth = columnWidth
    val dayGap = zoom.dayGap
    val gutterWidth = zoom.gutterWidth
    val headerHeight = zoom.headerHeight
    val titleStyle = MaterialTheme.typography.titleSmall.copy(
        fontSize = zoom.titleSize,
        lineHeight = zoom.titleSize * 1.35f,
        fontWeight = FontWeight.SemiBold,
    )
    val detailStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = zoom.detailSize,
        lineHeight = zoom.detailSize * 1.3f,
    )
    val axisTimeStyle = detailStyle.copy(fontSize = 9.sp, lineHeight = 12.sp)
    val axisNumberStyle = axisTimeStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val cardHorizontalPadding = zoom.cardHorizontalPadding
    val cardVerticalPadding = zoom.cardVerticalPadding
    val textWidth = with(density) { (courseWidth - cardHorizontalPadding * 2 - 4.dp).roundToPx() }
    // The gutter must always fit the period number and both clock times.
    val axisMinimum = measurer.measure("48", axisNumberStyle).size.height +
        2 * measurer.measure("23:59", axisTimeStyle).size.height + with(density) { 6.dp.roundToPx() }
    val minimumRowPixels = maxOf(axisMinimum, with(density) { 44.dp.roundToPx() })

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
            val details = courseDetails(course, parityEnabled, compact = zoom == TimetableZoom.WEEK)
            val titleHeight = measurer.measure(course.name, titleStyle, constraints = Constraints(maxWidth = textWidth)).size.height
            val detailsHeight = details.sumOf {
                measurer.measure(it, detailStyle, constraints = Constraints(maxWidth = textWidth)).size.height
            }
            // The card also owns a 1dp inset on every side and a 1dp gap before each detail
            // line; leaving them out makes the tallest title clip by a couple of pixels.
            val needed = titleHeight + detailsHeight + with(density) {
                ((CARD_INSET + cardVerticalPadding) * 2 + CARD_LINE_GAP * details.size).roundToPx() + 1
            }
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
    var previousColumns by remember { mutableStateOf(courseWidth + dayGap) }
    LaunchedEffect(selectedDate, scrollRequest, courseWidth) {
        if (positionedDate != selectedDate.toString() || handledRequest != scrollRequest) {
            withFrameNanos { }
            val target = grids.take(selectedDate.dayOfWeek.value - 1)
                .fold(0.dp) { total, grid -> total + courseWidth * grid.laneCount + dayGap }
            horizontal.scrollTo(with(density) { target.roundToPx() })
            vertical.scrollTo(0)
            positionedDate = selectedDate.toString()
            handledRequest = scrollRequest
        } else if (previousColumns.value > 0f && previousColumns != courseWidth + dayGap) {
            // Keep roughly the same day in view when the column width changes.
            horizontal.scrollTo((horizontal.value * (courseWidth + dayGap).value / previousColumns.value).toInt())
        }
        previousColumns = courseWidth + dayGap
    }

    val zoomCallback = rememberUpdatedState(onPinchZoom)
    val widthSource = rememberUpdatedState(courseWidth)

    Column(
        modifier
            .padding(horizontal = GRID_SIDE_PADDING)
            .testTag("timetable-grid")
            // Initial pass: the parent sees the gesture before the scrollable children, so a
            // two-finger pinch wins while a one-finger drag still pans the grid.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // The pinch is accumulated inside the gesture: pointer events arrive faster
                    // than recomposition, so scaling the reported width per event would lose most
                    // of the movement.
                    var pinchStart: Dp? = null
                    var accumulated = 1f
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) {
                            val start = pinchStart ?: widthSource.value.also { pinchStart = it }
                            val factor = event.calculateZoom()
                            if (factor.isFinite() && factor > 0f && factor != 1f) {
                                accumulated *= factor
                                val target = (start.value * accumulated)
                                    .coerceIn(TimetableZoom.minManual.value, TimetableZoom.maxManual.value)
                                zoomCallback.value(target.dp)
                                event.changes.forEach { it.consume() }
                            }
                        } else {
                            pinchStart = null
                            accumulated = 1f
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Row(Modifier.fillMaxWidth().height(headerHeight)) {
            Box(Modifier.width(gutterWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text("节次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The heading shares the body's scroll state, so a column is always above its day.
            Row(Modifier.weight(1f).horizontalScroll(horizontal, enabled = false), horizontalArrangement = Arrangement.spacedBy(dayGap)) {
                grids.forEach { grid ->
                    Row(Modifier.width(courseWidth * grid.laneCount).testTag("day-header-${grid.date.dayOfWeek.value}")) {
                        repeat(grid.laneCount) {
                            Box(
                                Modifier.width(courseWidth).fillMaxHeight().padding(1.dp)
                                    .background(
                                        if (grid.date == LocalDate.now()) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                        RoundedCornerShape(8.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "周${"一二三四五六日"[grid.date.dayOfWeek.value - 1]}\n${grid.date.format(formatter)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontSize = if (courseWidth < 70.dp) 10.sp else 12.sp,
                                    lineHeight = if (courseWidth < 70.dp) 12.sp else 15.sp,
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
                        Modifier.fillMaxWidth().height(rowHeights[index]).testTag("period-${index + 1}").padding(top = 3.dp),
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
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.first, contentColor = colors.second),
                                modifier = Modifier
                                    .offset(x = courseWidth * placed.lane, y = rowOffsets[course.startPeriod - 1])
                                    .width(courseWidth)
                                    .height(rowOffsets[course.endPeriod] - rowOffsets[course.startPeriod - 1])
                                    .testTag("course-block-${course.id}")
                                    .padding(CARD_INSET),
                            ) {
                                Column(
                                    Modifier.padding(horizontal = cardHorizontalPadding, vertical = cardVerticalPadding),
                                    verticalArrangement = Arrangement.spacedBy(CARD_LINE_GAP),
                                ) {
                                    Text(course.name, style = titleStyle, modifier = Modifier.testTag("course-title-${course.id}"))
                                    courseDetails(course, parityEnabled, compact = zoom == TimetableZoom.WEEK).forEach { Text(it, style = detailStyle) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Only real values become lines, so a course without a room does not reserve empty space.
 * [compact] is the overview width, where the card height already shows how many periods it
 * covers and only the parity flag still needs a line of its own.
 */
private fun courseDetails(course: Course, parityEnabled: Boolean, compact: Boolean = false): List<String> = buildList {
    val span = course.endPeriod - course.startPeriod + 1
    val parity = if (parityEnabled) when (course.weekRule) {
        WeekRule.ALL -> null
        WeekRule.ODD -> "单周"
        WeekRule.EVEN -> "双周"
    } else null
    if (compact) {
        parity?.let(::add)
    } else if (span > 1 || parity != null) {
        add("第${course.startPeriod}–${course.endPeriod}节" + if (parity != null) " · $parity" else "")
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
