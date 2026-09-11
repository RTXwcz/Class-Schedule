package com.kebiao.app.ocr

import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.notifications.LessonPeriod
import kotlin.math.abs
import kotlin.math.max

class CourseTableParser {
    fun parse(text: String): List<CourseDraft> = parse(listOf(OcrTextBlock(text, 1f, OcrSourceBox(0f, 0f, 0f, 0f))))

    /**
     * The period schedule the image itself prints: how many periods a day has and what clock times
     * each one runs between. Returns an empty list when the axis is incomplete, because a partially
     * read schedule would renumber the user's periods.
     */
    fun parsePeriods(blocks: List<OcrTextBlock>): List<LessonPeriod> {
        val nonempty = blocks.filter { it.text.isNotBlank() }
        val headers = findHeaders(nonempty)

        if (headers.isEmpty()) return emptyList()
        val headerBottom = headers.maxOf { it.block.box.bottom }
        val axisLeft = headers.first().block.box.left
        val axisBlocks = nonempty.filter { it.box.right < axisLeft && it.box.top > headerBottom && it.confidence >= 0.6f }
        val numbers = axisBlocks.mapNotNull { block -> axisPeriods(block.text)?.let { it.first to block } }
            .filter { (period, _) -> period in 1..48 }
            .sortedBy { it.second.centerY }
        if (numbers.size < MIN_PERIODS) return emptyList()
        val clocks = axisBlocks.filter { CLOCK.matches(it.text.trim()) }.sortedBy { it.centerY }

        if (clocks.size < MIN_PERIODS) return emptyList()
        val schedule = numbers.mapIndexed { index, (period, block) ->
            val top = if (index == 0) Float.NEGATIVE_INFINITY else (numbers[index - 1].second.centerY + block.centerY) / 2
            val bottom = if (index == numbers.lastIndex) Float.POSITIVE_INFINITY else (numbers[index + 1].second.centerY + block.centerY) / 2
            val inside = clocks.filter { it.centerY in top..bottom }
            period to inside
        }
        // Every period needs its own start and end; a half-read row must not shift the rest.
        if (schedule.any { it.second.size < 2 }) return emptyList()

        val periods = schedule.map { (period, inside) ->
            val start = inside.first().text.trim()
            val end = (inside.last().text.trim()).takeIf { inside.size > 1 } ?: start
            period to LessonPeriod(start, end)
        }
        val ordered = periods.sortedBy { it.first }
        if (ordered.map { it.first } != (1..ordered.size).toList()) return emptyList()
        if (ordered.any { (_, period) -> !isClockOrder(period.start, period.end) }) return emptyList()
        if (ordered.zipWithNext().any { (a, b) -> !isClockOrder(a.second.end, b.second.start) }) return emptyList()
        return ordered.map { it.second }
    }

    fun parse(blocks: List<OcrTextBlock>): List<CourseDraft> {
        val nonempty = blocks.filter { it.text.isNotBlank() }
        val headers = findHeaders(nonempty)
        val parityColumns = parityColumns(nonempty)
        if (headers.size < 2) return nonempty.filterNot { headerDay(it.text) != null || isAxisLabel(it.text) || parityOf(it.text) != null }
            .map { draft(listOf(it), null, null, null) }

        val headerBottom = headers.maxOf { it.block.box.bottom }
        val rows = nonempty.filter { it.box.right < headers.first().block.box.left && it.box.top > headerBottom && it.confidence >= 0.6f }
            .mapNotNull { block -> axisPeriods(block.text)?.let { Row(it.first, it.second, block) } }
            .sortedBy { it.block.centerY }
        val trustedRows = rows.takeIf { it.size >= 2 && it.zipWithNext().all { (a, b) -> a.end < b.start } }.orEmpty()
        // The axis column ends at the first rule the cells expose; the clock labels sit inside it
        // and must never be read as courses.
        val leftEdge = trustedRows.maxOfOrNull { row -> (row.block.cellBox?.right ?: row.block.box.right) + 1f }
            ?: (headers.first().block.centerX - (headers[1].block.centerX - headers[0].block.centerX) / 2)
        val rowStep = trustedRows.zipWithNext().map { (a, b) -> b.block.centerY - a.block.centerY }
            .sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

        val cells = linkedMapOf<Header?, MutableList<OcrTextBlock>>()
        nonempty.filter { block ->
            block.box.top > headerBottom && block.box.right >= leftEdge &&
                headerDay(block.text) == null && !isAxisCaption(block.text) && parityOf(block.text) == null
        }.forEach { block ->
            val nearest = headers.minByOrNull { abs(it.block.centerX - block.centerX) }
            val rightEdge = headers.last().block.centerX + (headers.last().block.centerX - headers[headers.lastIndex - 1].block.centerX) / 2
            val column = headers.firstOrNull { header -> header.block.cellBox?.let { block.centerX in it.left..it.right } == true }
                ?: nearest?.takeIf { block.centerX in leftEdge..rightEdge &&
                    // A detected cell in an unrecognized header column must stay unknown.
                    (block.cellBox == null || it.block.cellBox == null || block.cellBox.left == it.block.cellBox.left) &&
                    belongsToKnownDay(block.centerX, it, headers) }
            cells.getOrPut(column) { mutableListOf() }.add(block)
        }

        return cells.flatMap { (column, content) ->
            val groups = groupCellBlocks(content, rowStep)
            groups.filter { group -> group.any { it.text.lines().any { line -> line.isNotBlank() && !isDetail(line) } } }.map { group ->
                val cell = group.first().cellBox
                val enclosed = cell?.let { box -> trustedRows.filter { it.block.centerY > box.top && it.block.centerY < box.bottom } }.orEmpty()
                val blockFirst = rowAt(group.first(), trustedRows, rowStep)
                val bodyLast = group.lastOrNull { !sessionStamp(it.text) }?.let { rowAt(it, trustedRows, rowStep) }
                // One session repeated per period: the last repeated title is where the session ends.
                val repeatLast = group.lastOrNull { it !== group.first() && sameCourseTitle(group, it) }
                    ?.let { rowAt(it, trustedRows, rowStep) }
                val firstRow = listOfNotNull(blockFirst, enclosed.firstOrNull()).minByOrNull { it.start }
                val lastRow = repeatLast ?: listOfNotNull(bodyLast, enclosed.lastOrNull()).maxByOrNull { it.end }
                draft(group, column, firstRow, lastRow, parityFor(group.first().box, parityColumns))
            }
        }
    }

    /**
     * Blocks of one detected cell belong to one course: the cell is the table's own structure, and
     * a place or teacher line must not become a course of its own. Blocks without cells keep the
     * text heuristics, and a second title inside one cell still starts a new course while the cell
     * has no detail lines yet.
     */
    private fun groupCellBlocks(content: List<OcrTextBlock>, rowStep: Float?): List<MutableList<OcrTextBlock>> {
        val groups = mutableListOf<MutableList<OcrTextBlock>>()
        val ordered = content.sortedWith(compareBy<OcrTextBlock> { it.box.top }.thenBy { it.box.left })
        ordered.forEachIndexed { index, block ->
            val next = ordered.getOrNull(index + 1)
            val previous = groups.lastOrNull()
            val last = previous?.lastOrNull()
            val groupCell = previous?.firstNotNullOfOrNull { it.cellBox }
            val gap = last?.let { block.box.top - it.box.bottom } ?: Float.MAX_VALUE
            val close = gap <= max(rowStep ?: 0f, block.height * 3f)
            val sameCell = last?.cellBox == null || block.cellBox == null || last.cellBox == block.cellBox
            val continues = when {
                previous == null || last == null -> false
                isCourseDetail(block, previous, next) -> close && (sameCell || groupCell != null)
                continuesTitle(last, block) -> sameCell
                // The export repeats one session once per period, so the identical title in the next
                // row is the same course; a different title starts the next one.
                groupHasDetails(previous) && close && sameCourseTitle(previous, block) -> true
                else -> false
            }
            if (continues && previous != null) previous.add(block) else groups.add(mutableListOf(block))
        }
        return groups
    }

    private fun groupHasDetails(group: List<OcrTextBlock>): Boolean =
        group.any { block -> block.text.lines().any { it.isNotBlank() && isDetail(it) } }

    private fun groupHasSchedule(group: List<OcrTextBlock>): Boolean =
        group.any { block ->
            block.text.lines().any { line ->
                isDetail(line) || sessionStamp(line) || Regex("\\d\\s*节\\s*/\\s*周").containsMatchIn(line)
            }
        }

    /** Lines that belong to the course already open: weeks, teacher, place, session stamp. */
    private fun isCourseDetail(block: OcrTextBlock, group: List<OcrTextBlock>, next: OcrTextBlock?): Boolean {
        val trimmed = block.text.trim()
        if (isDetail(trimmed) || placeLine(trimmed) != null || sessionStamp(trimmed)) return true
        // A bare name is only a teacher when the table itself prints it inside the course's cell;
        // a standalone line is far more likely to be the next course title.
        val groupCell = group.firstNotNullOfOrNull { it.cellBox }
        if (!groupHasSchedule(group) || !looksLikeTeacher(trimmed)) return false
        // One course has one teacher line. A second bare name is the next course title, unless the
        // week range just above it starts the next repeated period of the same session.
        val previousIsWeekRange = group.lastOrNull()?.text?.lines()
            ?.any { it.isNotBlank() && it.contains("周") && !sessionStamp(it) } == true
        if (groupHasTeacher(group) && !previousIsWeekRange) return false
        val followedByDetail = next == null || isDetail(next.text) || placeLine(next.text) != null || sessionStamp(next.text)
        val cellLinked = groupCell != null && (block.cellBox == null || overlapsCell(block.cellBox, groupCell))
        // Without detected cells the teacher line still sits above the place line.
        return followedByDetail && (cellLinked || previousIsWeekRange || groupCell == null)
    }

    private fun overlapsCell(a: OcrSourceBox, b: OcrSourceBox): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    private fun groupHasTeacher(group: List<OcrTextBlock>): Boolean =
        group.withIndex().drop(1).any { (index, block) ->
            // A wrapped title is not a teacher line, however much it looks like a name.
            val previous = group[index - 1]
            !continuesTitle(previous, block) &&
                block.text.lines().any { line ->
                    Regex("(?:任课教师|授课教师|教师|老师)").containsMatchIn(line) || looksLikeTeacher(line)
                }
        }

    private fun sameCourseTitle(group: List<OcrTextBlock>, block: OcrTextBlock): Boolean {
        val title = group.firstOrNull()?.text?.trim().orEmpty()
        val candidate = block.text.trim()
        if (title.isEmpty() || candidate.isEmpty()) return false
        if (title == candidate) return true
        val shorter = if (title.length <= candidate.length) title else candidate
        val longer = if (title.length <= candidate.length) candidate else title
        return shorter.length >= 4 && longer.startsWith(shorter)
    }

    private fun sessionStamp(text: String): Boolean {
        val trimmed = text.trim()
        return Regex("^\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日.*$").matches(trimmed) ||
            Regex("^\\d{1,2}\\s*节\\s*/\\s*周.*$").matches(trimmed) ||
            Regex("^\\d{1,2}:\\d{2}\\s*[-—–~至]\\s*\\d{1,2}:\\d{2}.*$").matches(trimmed)
    }

    private fun draft(
        blocks: List<OcrTextBlock>,
        header: Header?,
        firstRow: Row?,
        lastRow: Row?,
        parity: WeekRule? = null,
    ): CourseDraft {
        val lines = blocks.flatMap { it.text.lines().map(String::trim).filter(String::isNotBlank) }
        val nameLines = lines.takeWhile { line -> !isDetail(line) && placeLine(line) == null && !sessionStamp(line) }
        val name = normalizeTitle(nameLines.joinToString("").ifBlank { lines.firstOrNull().orEmpty() })
        val details = lines.drop(nameLines.size.coerceAtLeast(1)).joinToString("\n")
        val weeks = parseWeeks(details)
        val teacher = lines.firstNotNullOfOrNull { line ->
            Regex("(?:任课教师|授课教师|教师|老师)\\s*[:：]\\s*([^\\s,，;；]+)").find(line)?.groupValues?.get(1)
                ?: Regex("[\\p{IsHan}]{1,5}老师").find(line)?.value
        } ?: run {
            // A teacher is printed after the week range, never before it.
            val scheduleIndex = lines.indexOfFirst { isDetail(it) }
            lines.drop((scheduleIndex + 1).coerceAtLeast(nameLines.size.coerceAtLeast(1)))
                .firstOrNull { line -> looksLikeTeacher(line) }
        }
        val confidence = blocks.minOf { it.confidence }.coerceIn(0f, 1f)
        val explicitDay = weekday(details)
        // "2节/周" is the weekly meeting count and "第1-8周" the term range; neither is a period.
        val periodText = details
            .replace(Regex("\\d{1,2}\\s*节\\s*/\\s*周"), " ")
            .replace(Regex("第?\\s*\\d{1,2}\\s*[-~～至到—–]\\s*\\d{1,2}\\s*周"), " ")
        val explicitPeriods = period(periodText)
        val start = explicitPeriods?.first ?: firstRow?.start
        // Text crossing a numbered row is only a tentative span; a range axis is stronger evidence.
        val end = explicitPeriods?.second ?: when {
            firstRow == null -> null
            firstRow.end > firstRow.start -> firstRow.end
            lastRow != null && (lastRow.end > firstRow.start || blocks.first().cellBox != null) -> lastRow.end
            else -> null
        }
        val geometryConfidence = if (blocks.first().cellBox != null || (firstRow != null && firstRow.end > firstRow.start)) 0.85f else 0.55f
        val textRule = when {
            Regex("单周|周\\s*[（(]?单|\\bodd\\b", RegexOption.IGNORE_CASE).containsMatchIn(details) -> WeekRule.ODD
            Regex("双周|周\\s*[（(]?双|\\beven\\b", RegexOption.IGNORE_CASE).containsMatchIn(details) -> WeekRule.EVEN
            else -> null
        }
        // A 单/双 column heading is table structure rather than cell text, and it is stronger than
        // the generic week range the cell prints.
        val rule = textRule ?: parity ?: if (weeks != null || Regex("每周|全周").containsMatchIn(details)) WeekRule.ALL else null
        val place = lines.firstNotNullOfOrNull { line -> placeLine(line) }
        // The split place wins: it keeps a note such as "（东）" that the generic building regex drops.
        val building = place?.first?.takeIf { it.isNotBlank() } ?: lines.firstNotNullOfOrNull { line ->
            Regex("([\\p{IsHan}A-Za-z0-9]+(?:教学楼|实验楼|楼|馆|校区))[A-Za-z]?").find(line)?.value
        }
        // "东2-204" is one room number: the split place wins over the bare digit group.
        val room = place?.second?.takeIf { it.isNotBlank() } ?: lines.firstNotNullOfOrNull { line ->
            Regex("(?:教室\\s*[:：]?\\s*)?\\b([A-Za-z]?[0-9]{3,4}[A-Za-z]?)\\b").find(line)?.groupValues?.get(1)
        }
        val locationNote = if (building == null && room == null) lines.firstOrNull { placeLine(it) != null } else null
        val box = OcrSourceBox(blocks.minOf { it.box.left }, blocks.minOf { it.box.top }, blocks.maxOf { it.box.right }, blocks.maxOf { it.box.bottom })
        return CourseDraft(
            name = DraftField(name, confidence, box),
            weekday = DraftField(explicitDay ?: header?.day, if (explicitDay != null) confidence else if (header != null) minOf(confidence, header.block.confidence, 0.9f) else 0f, header?.block?.box ?: box),
            startPeriod = DraftField(start, if (start == null) 0f else if (explicitPeriods != null) confidence else minOf(confidence, geometryConfidence), firstRow?.block?.box ?: box),
            endPeriod = DraftField(end, if (end == null) 0f else if (explicitPeriods != null) confidence else minOf(confidence, geometryConfidence), lastRow?.block?.box ?: firstRow?.block?.box ?: box),
            weekRule = DraftField(rule, if (rule == null) 0f else confidence, box),
            building = DraftField(building, if (building == null) 0f else confidence, box),
            room = DraftField(room, if (room == null) 0f else confidence, box),
            locationNote = DraftField(locationNote, if (locationNote == null) 0f else confidence, box),
            teacher = DraftField(teacher, if (teacher == null) 0f else confidence, box),
            weeks = DraftField(weeks, if (weeks == null) 0f else confidence, box),
            courseNote = DraftField(meaningfulNote(details), confidence, box),
        )
    }

    /** A bare name between the week range and the place: this table prints teachers without a label. */
    private fun looksLikeTeacher(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length !in 2..12) return false
        if (Regex("[0-9A-Za-z:：()（）.。\\-—–]").containsMatchIn(trimmed)) return false
        if (Regex("周|节|课|楼|馆|场|室|区|中心|学院|大学|考试|实验").containsMatchIn(trimmed)) return false
        // Several teachers are printed as "楼俊超/李梦宇" or "张三、李四".
        return Regex("^[\\p{IsHan}·]+([/、,，][\\p{IsHan}·]+)*$").matches(trimmed)
    }

    /** "紫金港东2-204" -> building + room; "紫金港风雨操场（羽毛球场）" -> building only. */
    private fun placeLine(line: String): Pair<String, String>? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (Regex("^\\d{1,2}:\\d{2}").containsMatchIn(trimmed)) return null
        // Notes such as "（东）" are part of the building and are re-attached after the room split.
        val notes = Regex("[（(][^）)]*[）)]").findAll(trimmed).joinToString("") { it.value }
        val stripped = trimmed.replace(Regex("[（(][^）)]*[）)]"), " ").trim()
        val tail = Regex("^([\\p{IsHan}]+?)\\s*([A-Za-z]?[0-9][0-9A-Za-z\\-]*)$").find(stripped)
        if (tail != null && tail.groupValues[1].isNotEmpty()) {
            return (tail.groupValues[1].trim() + notes) to tail.groupValues[2]
        }
        if (trimmed.length <= 24 && Regex("楼|馆|场|校区|教室|实验室|中心").containsMatchIn(trimmed)) return trimmed to ""
        return null
    }

    /** Session stamps and per-week counts are table metadata; they never become course notes. */
    private fun meaningfulNote(details: String): String? {
        val kept = details.lines().filter { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() && !sessionStamp(trimmed)
        }
        return kept.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun normalizeTitle(text: String): String {
        val result = StringBuilder()
        var bookDepth = 0
        var parenthesisDepth = 0
        text.forEach { char ->
            if (char == '《') bookDepth++
            if (char == '(' || char == '（') parenthesisDepth++
            val isParenthesisClose = char == ')' || char == '）'
            val normalized = if (bookDepth > 0 && (char == '>' || (isParenthesisClose && parenthesisDepth == 0))) '》' else char
            if (isParenthesisClose) parenthesisDepth = (parenthesisDepth - 1).coerceAtLeast(0)
            if (normalized == '》') bookDepth = (bookDepth - 1).coerceAtLeast(0)
            result.append(normalized)
        }
        return result.toString()
    }

    private fun parseWeeks(text: String): String? {
        val numberExpression = "[0-9]{1,2}(?:\\s*[-~～至到—–]\\s*[0-9]{1,2})?(?:\\s*[,，、]\\s*[0-9]{1,2}(?:\\s*[-~～至到—–]\\s*[0-9]{1,2})?)*"
        val expressions = Regex("(?<![0-9])($numberExpression)\\s*周").findAll(text).map { it.groupValues[1] }.toList()
            .ifEmpty { Regex("周次\\s*[:：]\\s*($numberExpression)").findAll(text).map { it.groupValues[1] }.toList() }
        if (expressions.isEmpty()) return null
        val normalized = expressions.joinToString(",").replace(Regex("\\s+"), "")
            .replace(Regex("[~～至到—–]"), "-").replace(Regex("[，、]"), ",")
            // A session repeated once per period prints its range again; keep it once.
            .split(",").filter { it.isNotBlank() }.distinct().joinToString(",")
        return normalized.takeIf { runCatching { com.kebiao.app.domain.WeekSelection.parse(it) }.isSuccess }
    }

    private data class Header(val day: Int, val block: OcrTextBlock)
    private data class Row(val start: Int, val end: Int, val block: OcrTextBlock)

    private companion object {
        val CLOCK = Regex("^(?:[01]?\\d|2[0-3]):[0-5]\\d$")
        const val MIN_PERIODS = 4
    }

    /** True when both strings are clock times and the second is not before the first. */
    private fun isClockOrder(first: String, second: String): Boolean {
        fun minutes(value: String): Int? = CLOCK.matchEntire(value.trim())?.let {
            val parts = value.trim().split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        }
        val a = minutes(first) ?: return false
        val b = minutes(second) ?: return false
        return b >= a
    }

    private val OcrTextBlock.centerX get() = (box.left + box.right) / 2
    private val OcrTextBlock.centerY get() = (box.top + box.bottom) / 2
    private val OcrTextBlock.height get() = (box.bottom - box.top).coerceAtLeast(1f)
    private val OcrTextBlock.width get() = (box.right - box.left).coerceAtLeast(1f)

    private fun findHeaders(blocks: List<OcrTextBlock>): List<Header> {
        val candidates = blocks.filter { it.confidence >= 0.65f }.mapNotNull { block -> headerDay(block.text)?.let { Header(it, block) } }
        val best = candidates.map { anchor ->
            candidates.filter { abs(it.block.centerY - anchor.block.centerY) <= max(it.block.height, anchor.block.height) }
                .distinctBy { it.day }.sortedBy { it.block.centerX }
        }.filter { group ->
            group.size >= 3 && group.zipWithNext().all { (a, b) -> b.day > a.day && b.block.centerX > a.block.centerX }
        }
            .maxByOrNull { it.size }.orEmpty()
        if (best.isEmpty()) return best
        // "星期一" is sometimes read as a bare "星期". The block still holds its column, so it takes
        // the missing day between its neighbours instead of letting that day's courses fall onto the
        // neighbouring weekday.
        val truncated = blocks.filter {
            it.confidence >= 0.65f && Regex("^(星期|周|礼拜)$").matches(it.text.trim()) &&
                abs(it.centerY - best.first().block.centerY) <= maxOf(it.height, best.first().block.height)
        }
        val filled = best.toMutableList()
        truncated.forEach { block ->
            if (filled.any { abs(it.block.centerX - block.centerX) <= maxOf(it.block.width, block.width) }) return@forEach
            val left = filled.filter { it.block.centerX < block.centerX }.maxByOrNull { it.block.centerX }
            val right = filled.filter { it.block.centerX > block.centerX }.minByOrNull { it.block.centerX }
            val day = when {
                left != null && right != null && right.day - left.day == 2 -> left.day + 1
                left == null && right != null && right.day > 1 -> right.day - 1
                right == null && left != null && left.day < 7 -> left.day + 1
                else -> null
            }
            if (day != null) filled += Header(day, block)
        }
        return filled.sortedBy { it.block.centerX }
    }

    /** Join a wrapped title, while leaving complete neighboring titles as separate drafts. */
    private fun continuesTitle(previous: OcrTextBlock, next: OcrTextBlock): Boolean {
        if (isDetail(previous.text) || isDetail(next.text)) return false
        val gap = next.box.top - previous.box.bottom
        val lineHeight = max(previous.height, next.height)
        if (gap !in (-lineHeight * 0.25f)..(lineHeight * 0.6f)) return false
        val centered = abs(previous.centerX - next.centerX) <= lineHeight
        val leftAligned = abs(previous.box.left - next.box.left) <= lineHeight * 0.6f
        if (!centered && !leftAligned) return false
        val prior = previous.text.trim()
        val following = next.text.trim()
        val unclosed = listOf('（' to '）', '(' to ')', '《' to '》', '[' to ']').any { (open, close) -> prior.count { it == open } > prior.count { it == close } }
        val continuation = prior.endsWith("及") || prior.endsWith("与") || prior.endsWith("和") || unclosed
        val shortTail = following.length == 1 && prior.length >= 3 && !prior.endsWith("）") && !prior.endsWith(")")
        val cellWidth = previous.cellBox?.let { it.right - it.left }
        val fullLine = cellWidth != null && prior.length >= 7 && (previous.box.right - previous.box.left) >= cellWidth * .76f && following.length < prior.length
        return continuation || shortTail || fullLine
    }

    private fun belongsToKnownDay(x: Float, header: Header, headers: List<Header>): Boolean {
        // With a missing weekday and no detected cell, variable column widths make even
        // interpolated boundaries ambiguous. Keep the day unset for review.
        if (headers.zipWithNext().any { (a, b) -> b.day - a.day != 1 }) return false
        val index = headers.indexOf(header)
        val before = headers.getOrNull(index - 1)
        val after = headers.getOrNull(index + 1)
        val leftStep = before?.let { (header.block.centerX - it.block.centerX) / (header.day - it.day) }
        val rightStep = after?.let { (it.block.centerX - header.block.centerX) / (it.day - header.day) }
        val left = header.block.centerX - (leftStep ?: rightStep ?: return false) / 2
        val right = header.block.centerX + (rightStep ?: leftStep)!! / 2
        return x in left..right
    }

    private fun rowAt(block: OcrTextBlock, rows: List<Row>, step: Float?): Row? {
        if (step == null || rows.isEmpty()) return null
        return rows.minByOrNull { abs(it.block.centerY - block.centerY) }
            ?.takeIf { abs(it.block.centerY - block.centerY) <= step / 2 }
    }

    private fun headerDay(text: String): Int? =
        if (Regex("(?:星期|周|礼拜)[一二三四五六日天1-7]").matches(text.trim())) weekday(text.trim()) else null

    /**
     * Labels that belong to the time axis rather than to a course: the period numbers, the day-part
     * captions, the clock stamps and the sub-column parity headings.
     */
    private fun isAxisLabel(text: String): Boolean = isAxisCaption(text) || axisPeriods(text) != null

    /**
     * Captions that belong to the time axis, the clock stamps and the 单/双 headings. A period line
     * inside a course cell ("第5-6节") is content, so it is deliberately not part of this set.
     */
    private fun isAxisCaption(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed in setOf(
                "节次", "时间", "日期", "上午", "下午", "晚上", "早晨", "早上", "清晨", "中午", "傍晚", "夜间", "凌晨",
                "上", "午", "下", "晚",
            )
        ) return true
        return Regex("^\\d{1,2}:\\d{2}$").matches(trimmed) ||
            Regex("^\\d{1,2}:\\d{2}\\s*[-—–~至]\\s*\\d{1,2}:\\d{2}$").matches(trimmed)
    }

    /** The 单 / 双 headings above a day's two sub-columns. */
    private fun parityOf(text: String): WeekRule? = when (text.trim()) {
        "单", "单周", "单周课", "单周课程" -> WeekRule.ODD
        "双", "双周", "双周课", "双周课程" -> WeekRule.EVEN
        else -> null
    }

    private data class ParityColumn(val box: OcrSourceBox, val rule: WeekRule)

    private fun parityColumns(blocks: List<OcrTextBlock>): List<ParityColumn> =
        blocks.mapNotNull { block -> parityOf(block.text)?.let { ParityColumn(block.box, it) } }
            .sortedBy { it.box.left }

    /**
     * Which half of a day a block sits in. A lost rule merges the two sub-columns into one detected
     * cell, so the boundary is taken from the headings themselves.
     */
    private fun parityFor(anchor: OcrSourceBox, columns: List<ParityColumn>): WeekRule? {
        val center = (anchor.left + anchor.right) / 2
        columns.zipWithNext().forEach { (left, right) ->
            if (left.rule != WeekRule.ODD || right.rule != WeekRule.EVEN) return@forEach
            val boundary = (left.box.right + right.box.left) / 2f
            val slack = maxOf((right.box.left - left.box.right), right.box.width)
            if (center >= left.box.left - slack && center < boundary) return WeekRule.ODD
            if (center >= boundary && center <= right.box.right + slack) return WeekRule.EVEN
        }
        return null
    }

    private val OcrSourceBox.width: Float get() = right - left

    private fun axisPeriods(text: String): Pair<Int, Int>? {
        val trimmed = text.trim()
        if (!Regex("(?:第)?\\d{1,2}(?:\\s*[-~～至到—–]\\s*\\d{1,2})?\\s*节?").matches(trimmed)) return null
        return period(if (trimmed.endsWith("节")) trimmed else "${trimmed}节")
    }

    private fun isDetail(text: String): Boolean {
        val trimmed = text.trim()
        return period(trimmed) != null || weekday(trimmed) != null ||
            Regex("周次|\\d\\s*周|单周|双周|每周|教师|老师|教室|教学楼|实验楼|\\S+楼|\\S+馆|校区").containsMatchIn(trimmed) ||
            Regex("[A-Za-z]?[0-9]{3,4}[A-Za-z]?").matches(trimmed)
    }

    private fun weekday(text: String): Int? {
        // "第1-8周2节/周" must not be read as "周2": the weekly range and the meeting count are not
        // weekdays, so a 周 whose day digit follows a number is ignored.
        val value = Regex("(?:星期|礼拜)\\s*([一二三四五六日天1-7])").find(text)?.groupValues?.get(1)
            ?: Regex("(?<![0-9第])\\s*周\\s*([一二三四五六日天])(?![节周])").find(text)?.groupValues?.get(1)
            ?: Regex("(?<![0-9第])\\s*周\\s*([1-7])(?![0-9节周/])").find(text)?.groupValues?.get(1)
            ?: return null
        return mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)[value] ?: value.toIntOrNull()
    }

    private fun period(text: String): Pair<Int, Int>? {
        val match = Regex("(?:第)?([0-9]{1,2})(?:\\s*(?:-|~|～|—|–|至|到)\\s*([0-9]{1,2}))?\\s*节").find(text) ?: return null
        val start = match.groupValues[1].toIntOrNull() ?: return null
        val end = match.groupValues[2].toIntOrNull() ?: start
        return (start to end).takeIf { start in 1..48 && end in start..48 }
    }
}

