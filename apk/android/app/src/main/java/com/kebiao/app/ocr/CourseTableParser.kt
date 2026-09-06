package com.kebiao.app.ocr

import com.kebiao.app.domain.model.WeekRule

class CourseTableParser {
    fun parse(text: String): List<CourseDraft> = parse(listOf(OcrTextBlock(text, 1f, OcrSourceBox(0f, 0f, 0f, 0f))))

    fun parse(blocks: List<OcrTextBlock>): List<CourseDraft> = blocks.map { block ->
        val lines = block.text.lines().map(String::trim).filter(String::isNotBlank)
        val name = lines.firstOrNull().orEmpty()
        val details = lines.drop(1).joinToString(" ")
        val weekday = weekday(details)
        val periods = period(details)
        val rule = when {
            details.contains("单") || details.contains("odd", true) -> WeekRule.ODD
            details.contains("双") || details.contains("even", true) -> WeekRule.EVEN
            else -> WeekRule.ALL
        }
        val location = lines.lastOrNull { it != name && !it.contains("星期") && !it.contains("周") } ?: ""
        val tokens = location.split(Regex("\\s+"), limit = 2)
        CourseDraft(
            DraftField(name, block.confidence, block.box),
            DraftField(weekday, block.confidence, block.box),
            DraftField(periods?.first, block.confidence, block.box),
            DraftField(periods?.second, block.confidence, block.box),
            DraftField(rule, block.confidence, block.box),
            DraftField(tokens.getOrNull(0)?.takeIf { it.isNotBlank() }, block.confidence, block.box),
            DraftField(tokens.getOrNull(1)?.takeIf { it.isNotBlank() }, block.confidence, block.box),
            DraftField(null, block.confidence, block.box),
        )
    }

    private fun weekday(text: String): Int? {
        val value = Regex("(?:星期|周)([一二三四五六日天1-7])").find(text)?.groupValues?.get(1) ?: return null
        return mapOf("一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5, "六" to 6, "日" to 7, "天" to 7)[value] ?: value.toIntOrNull()
    }

    private fun period(text: String): Pair<Int, Int>? {
        val match = Regex("(?:第)?([0-9]{1,2})(?:\\s*(?:-|至|到)\\s*([0-9]{1,2}))?\\s*节").find(text) ?: return null
        val start = match.groupValues[1].toIntOrNull() ?: return null
        val end = match.groupValues[2].toIntOrNull() ?: start
        return start to end
    }
}
