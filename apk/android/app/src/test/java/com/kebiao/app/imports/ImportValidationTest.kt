package com.kebiao.app.imports

import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.DraftField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportValidationTest {
    @Test
    fun validDraftRequiresEveryFieldToBeConfirmedBeforePersistence() {
        val draft = CourseDraft(
            name = DraftField("高等数学", 0.99f),
            weekday = DraftField(1, 0.99f),
            startPeriod = DraftField(1, 0.99f),
            endPeriod = DraftField(2, 0.99f),
            weekRule = DraftField(WeekRule.ALL, 0.99f),
            building = DraftField("理科楼", 0.99f),
            room = DraftField("C203", 0.99f),
            locationNote = DraftField(null, 1f),
        )

        assertFalse(ImportValidation.canPersist(listOf(draft)))
        val confirmed = draft.confirmAll()
        assertTrue(ImportValidation.canPersist(listOf(confirmed)))
    }

    @Test
    fun invalidDraftIsRejectedEvenWhenFieldsAreConfirmed() {
        val draft = CourseDraft(
            name = DraftField("", 1f, confirmed = true),
            weekday = DraftField(8, 1f, confirmed = true),
            startPeriod = DraftField(1, 1f, confirmed = true),
            endPeriod = DraftField(2, 1f, confirmed = true),
            weekRule = DraftField(WeekRule.ALL, 1f, confirmed = true),
            building = DraftField(null, 1f, confirmed = true),
            room = DraftField(null, 1f, confirmed = true),
            locationNote = DraftField(null, 1f, confirmed = true),
        )

        assertEquals(false, ImportValidation.canPersist(listOf(draft)))
        assertTrue(ImportValidation.validate(draft).any { it.field == "name" })
        assertTrue(ImportValidation.validate(draft).any { it.field == "weekday" })
    }
}
