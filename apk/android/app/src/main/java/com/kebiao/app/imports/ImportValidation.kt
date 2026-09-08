package com.kebiao.app.imports

import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.ValidationIssue

object ImportValidation {
    fun validate(draft: CourseDraft, periodCount: Int = 48): List<ValidationIssue> = buildList {
        addAll(draft.validationErrors())
        if (draft.startPeriod.value in 1..48 && draft.startPeriod.value !in 1..periodCount) {
            add(ValidationIssue("startPeriod", "开始节次超出当前作息（共 $periodCount 节），请修改节次或调整作息"))
        }
        if (draft.endPeriod.value in 1..48 && draft.endPeriod.value !in 1..periodCount) {
            add(ValidationIssue("endPeriod", "结束节次超出当前作息（共 $periodCount 节），请修改节次或调整作息"))
        }
    }

    /** Applying the review accepts the batch; recognition confidence is guidance, not a save gate. */
    fun canPersist(drafts: List<CourseDraft>, periodCount: Int = 48): Boolean =
        drafts.isNotEmpty() && drafts.all { validate(it, periodCount).isEmpty() }
}
