package com.kebiao.app.domain

object WeekSelection {
    fun parse(text: String): List<Int> {
        if (text.isBlank()) return emptyList()
        val values = text.replace("，", ",").split(',').flatMap { raw ->
            val part = raw.trim()
            val match = Regex("(\\d{1,2})(?:\\s*[-至]\\s*(\\d{1,2}))?").matchEntire(part)
                ?: throw IllegalArgumentException("周次格式示例：1-16 或 1,3,5-8")
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toIntOrNull() ?: start
            require(start in 1..60 && end in start..60) { "周次须为 1 到 60" }
            (start..end).toList()
        }
        return values.distinct().sorted()
    }

    fun format(weeks: List<Int>): String = weeks.sorted().joinToString(",")
}
