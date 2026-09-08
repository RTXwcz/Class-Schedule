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
    fun validRecognizedBatchCanBeAppliedWithoutIndividualConfirmations() {
        val draft = validDraft()

        assertFalse(draft.name.confirmed)
        assertFalse(draft.teacher.confirmed)
        assertTrue(ImportValidation.canPersist(listOf(draft, draft.copy(name = DraftField("大学物理", .3f)))))
    }

    @Test
    fun optionalUnknownFieldsAndLowConfidenceDoNotPreventApplyingValidCourse() {
        val draft = validDraft().copy(
            name = DraftField("高等数学", .2f),
            building = DraftField(null, 0f),
            room = DraftField(null, 0f),
            teacher = DraftField(null, 0f),
            weeks = DraftField(null, 0f),
        )

        assertTrue(ImportValidation.canPersist(listOf(draft)))
    }

    @Test
    fun missingOrInvalidRequiredValuesBlockWholeBatchEvenWhenConfirmed() {
        val valid = validDraft()
        val invalidFields = listOf(
            "name" to valid.copy(name = DraftField("  ", 1f)),
            "weekday" to valid.copy(weekday = DraftField(null, 1f)),
            "weekday" to valid.copy(weekday = DraftField(8, 1f)),
            "startPeriod" to valid.copy(startPeriod = DraftField(null, 1f)),
            "startPeriod" to valid.copy(startPeriod = DraftField(0, 1f)),
            "endPeriod" to valid.copy(endPeriod = DraftField(null, 1f)),
            "endPeriod" to valid.copy(endPeriod = DraftField(49, 1f)),
            "endPeriod" to valid.copy(startPeriod = DraftField(3, 1f), endPeriod = DraftField(2, 1f)),
            "weekRule" to valid.copy(weekRule = DraftField(null, 1f)),
            "weeks" to valid.copy(weeks = DraftField("2-1", 1f)),
            "weeks" to valid.copy(weeks = DraftField("0,61", 1f)),
        )
        invalidFields.forEach { (field, invalid) ->
            assertFalse(ImportValidation.canPersist(listOf(valid, invalid.confirmAll())), "Must reject invalid $field")
            assertTrue(ImportValidation.validate(invalid).any { it.field == field }, "Must explain invalid $field")
        }
    }

    @Test
    fun actualDailyPeriodLimitIsValidatedAndExplained() {
        val draft = validDraft().copy(startPeriod = DraftField(11, .9f), endPeriod = DraftField(13, .9f))

        assertFalse(ImportValidation.canPersist(listOf(draft), periodCount = 12))
        assertEquals(listOf("endPeriod"), ImportValidation.validate(draft, 12).map { it.field })
        assertTrue(ImportValidation.validate(draft, 12).single().message.contains("12"))
        assertTrue(ImportValidation.canPersist(listOf(draft), periodCount = 13))
        assertFalse(ImportValidation.canPersist(listOf(validDraft()), periodCount = 0))
    }

    @Test
    fun emptyBatchCannotBeApplied() {
        assertFalse(ImportValidation.canPersist(emptyList()))
    }

    private fun validDraft() = CourseDraft(
        name = DraftField("高等数学", .99f),
        weekday = DraftField(1, .99f),
        startPeriod = DraftField(1, .99f),
        endPeriod = DraftField(2, .99f),
        weekRule = DraftField(WeekRule.ALL, .99f),
        building = DraftField("理科楼", .99f),
        room = DraftField("C203", .99f),
        locationNote = DraftField(null, 1f),
    )
}
