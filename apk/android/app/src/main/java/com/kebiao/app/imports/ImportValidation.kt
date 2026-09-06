package com.kebiao.app.imports

import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.ValidationIssue

object ImportValidation {
    fun validate(draft: CourseDraft): List<ValidationIssue> = draft.validationErrors()
    fun canPersist(drafts: List<CourseDraft>): Boolean = drafts.isNotEmpty() && drafts.all { it.validationErrors().isEmpty() && listOf(it.name, it.weekday, it.startPeriod, it.endPeriod, it.weekRule, it.building, it.room, it.locationNote).all { field -> field.confirmed } }
}
