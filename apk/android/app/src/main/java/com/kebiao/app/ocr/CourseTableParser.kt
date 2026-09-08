package com.kebiao.app.ocr

import com.kebiao.app.domain.model.WeekRule
import kotlin.math.abs
import kotlin.math.max

class CourseTableParser {
    fun parse(text: String): List<CourseDraft> = parse(listOf(OcrTextBlock(text, 1f, OcrSourceBox(0f, 0f, 0f, 0f))))

    fun parse(blocks: List<OcrTextBlock>): List<CourseDraft> {
        val nonempty = blocks.filter { it.text.isNotBlank() }
        val headers = findHeaders(nonempty)
        if (headers.size < 2) return nonempty.filterNot { headerDay(it.text) != null || isAxisLabel(it.text) }
            .map { draft(listOf(it), null, null, null) }

        val headerBottom = headers.maxOf { it.block.box.bottom }
        val rows = nonempty.filter { it.box.right < headers.first().block.box.left && it.box.top > headerBottom && it.confidence >= 0.6f }
            .mapNotNull { block -> axisPeriods(block.text)?.let { Row(it.first, it.second, block) } }
            .sortedBy { it.block.centerY }
        val trustedRows = rows.takeIf { it.size >= 2 && it.zipWithNext().all { (a, b) -> a.end < b.start } }.orEmpty()
        val leftEdge = trustedRows.maxOfOrNull { it.block.box.right + 1f }
            ?: (headers.first().block.centerX - (headers[1].block.centerX - headers[0].block.centerX) / 2)
        val rowStep = trustedRows.zipWithNext().map { (a, b) -> b.block.centerY - a.block.centerY }
            .sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

        val cells = linkedMapOf<Header?, MutableList<OcrTextBlock>>()
        nonempty.filter { block ->
            block.box.top > headerBottom && block.box.right >= leftEdge &&
                headerDay(block.text) == null && block.text.trim() !in setOf("节次", "时间", "上午", "下午", "晚上", "日期")
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
            val groups = mutableListOf<MutableList<OcrTextBlock>>()
            content.sortedWith(compareBy<OcrTextBlock> { it.box.top }.thenBy { it.box.left }).forEach { block ->
                val previous = groups.lastOrNull()
                val gap = previous?.lastOrNull()?.let { block.box.top - it.box.bottom } ?: Float.MAX_VALUE
                val close = gap <= max(rowStep ?: 0f, block.height * 3f)
                val sameCell = previous?.lastOrNull()?.let { prior -> prior.cellBox == null || block.cellBox == null || prior.cellBox == block.cellBox } ?: false
                val continuesCell = previous != null && sameCell && close &&
                    (isDetail(block.text) || continuesTitle(previous.last(), block))
                if (continuesCell) previous!!.add(block) else groups.add(mutableListOf(block))
            }
            groups.filter { group -> group.any { it.text.lines().any { line -> line.isNotBlank() && !isDetail(line) } } }.map { group ->
                val cell = group.first().cellBox
                val enclosed = cell?.let { box -> trustedRows.filter { it.block.centerY > box.top && it.block.centerY < box.bottom } }.orEmpty()
                val firstRow = enclosed.firstOrNull() ?: rowAt(group.first(), trustedRows, rowStep)
                val lastRow = enclosed.lastOrNull() ?: rowAt(group.last(), trustedRows, rowStep)
                draft(group, column, firstRow, lastRow)
            }
        }
    }

    private fun draft(blocks: List<OcrTextBlock>, header: Header?, firstRow: Row?, lastRow: Row?): CourseDraft {
        val lines = blocks.flatMap { it.text.lines().map(String::trim).filter(String::isNotBlank) }
        val nameLines = lines.takeWhile { !isDetail(it) }
        val name = normalizeTitle(nameLines.joinToString("").ifBlank { lines.firstOrNull().orEmpty() })
        val details = lines.drop(nameLines.size.coerceAtLeast(1)).joinToString("\n")
        val weeks = parseWeeks(details)
        val teacher = lines.firstNotNullOfOrNull { line ->
            Regex("(?:任课教师|授课教师|教师|老师)\\s*[:：]\\s*([^\\s,，;；]+)").find(line)?.groupValues?.get(1)
                ?: Regex("[\\p{IsHan}]{1,5}老师").find(line)?.value
        }
        val confidence = blocks.minOf { it.confidence }.coerceIn(0f, 1f)
        val explicitDay = weekday(details)
        val explicitPeriods = period(details)
        val start = explicitPeriods?.first ?: firstRow?.start
        // Text crossing a numbered row is only a tentative span; a range axis is stronger evidence.
        val end = explicitPeriods?.second ?: when {
            firstRow == null -> null
            firstRow.end > firstRow.start -> firstRow.end
            lastRow != null && (lastRow.end > firstRow.start || blocks.first().cellBox != null) -> lastRow.end
            else -> null
        }
        val geometryConfidence = if (blocks.first().cellBox != null || (firstRow != null && firstRow.end > firstRow.start)) 0.85f else 0.55f
        val rule = when {
            Regex("单周|周\\s*[（(]?单|\\bodd\\b", RegexOption.IGNORE_CASE).containsMatchIn(details) -> WeekRule.ODD
            Regex("双周|周\\s*[（(]?双|\\beven\\b", RegexOption.IGNORE_CASE).containsMatchIn(details) -> WeekRule.EVEN
            weeks != null || Regex("每周|全周").containsMatchIn(details) -> WeekRule.ALL
            else -> null
        }
        val building = lines.firstNotNullOfOrNull { line ->
            Regex("([\\p{IsHan}A-Za-z0-9]+(?:教学楼|实验楼|楼|馆|校区))[A-Za-z]?").find(line)?.value
        }
        val room = lines.firstNotNullOfOrNull { line ->
            Regex("(?:教室\\s*[:：]?\\s*)?\\b([A-Za-z]?[0-9]{3,4}[A-Za-z]?)\\b").find(line)?.groupValues?.get(1)
        }
        val box = OcrSourceBox(blocks.minOf { it.box.left }, blocks.minOf { it.box.top }, blocks.maxOf { it.box.right }, blocks.maxOf { it.box.bottom })
        return CourseDraft(
            name = DraftField(name, confidence, box),
            weekday = DraftField(explicitDay ?: header?.day, if (explicitDay != null) confidence else if (header != null) minOf(confidence, header.block.confidence, 0.9f) else 0f, header?.block?.box ?: box),
            startPeriod = DraftField(start, if (start == null) 0f else if (explicitPeriods != null) confidence else minOf(confidence, geometryConfidence), firstRow?.block?.box ?: box),
            endPeriod = DraftField(end, if (end == null) 0f else if (explicitPeriods != null) confidence else minOf(confidence, geometryConfidence), lastRow?.block?.box ?: firstRow?.block?.box ?: box),
            weekRule = DraftField(rule, if (rule == null) 0f else confidence, box),
            building = DraftField(building, if (building == null) 0f else confidence, box),
            room = DraftField(room, if (room == null) 0f else confidence, box),
            locationNote = DraftField(null, 0f, box),
            teacher = DraftField(teacher, if (teacher == null) 0f else confidence, box),
            weeks = DraftField(weeks, if (weeks == null) 0f else confidence, box),
            courseNote = DraftField(details.takeIf { it.isNotBlank() }, confidence, box),
        )
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
        return normalized.takeIf { runCatching { com.kebiao.app.domain.WeekSelection.parse(it) }.isSuccess }
    }

    private data class Header(val day: Int, val block: OcrTextBlock)
    private data class Row(val start: Int, val end: Int, val block: OcrTextBlock)
    private val OcrTextBlock.centerX get() = (box.left + box.right) / 2
    private val OcrTextBlock.centerY get() = (box.top + box.bottom) / 2
    private val OcrTextBlock.height get() = (box.bottom - box.top).coerceAtLeast(1f)

    private fun findHeaders(blocks: List<OcrTextBlock>): List<Header> {
        val candidates = blocks.filter { it.confidence >= 0.65f }.mapNotNull { block -> headerDay(block.text)?.let { Header(it, block) } }
        return candidates.map { anchor ->
            candidates.filter { abs(it.block.centerY - anchor.block.centerY) <= max(it.block.height, anchor.block.height) }
                .distinctBy { it.day }.sortedBy { it.block.centerX }
        }.filter { group ->
            group.size >= 3 && group.zipWithNext().all { (a, b) -> b.day > a.day && b.block.centerX > a.block.centerX }
        }
            .maxByOrNull { it.size }.orEmpty()
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

    private fun isAxisLabel(text: String): Boolean = text.trim() in setOf("节次", "时间", "上午", "下午", "晚上", "日期") || axisPeriods(text) != null

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
        val value = Regex("(?:星期|周|礼拜)([一二三四五六日天1-7])").find(text)?.groupValues?.get(1) ?: return null
        return mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)[value] ?: value.toIntOrNull()
    }

    private fun period(text: String): Pair<Int, Int>? {
        val match = Regex("(?:第)?([0-9]{1,2})(?:\\s*(?:-|~|～|—|–|至|到)\\s*([0-9]{1,2}))?\\s*节").find(text) ?: return null
        val start = match.groupValues[1].toIntOrNull() ?: return null
        val end = match.groupValues[2].toIntOrNull() ?: start
        return (start to end).takeIf { start in 1..48 && end in start..48 }
    }
}
