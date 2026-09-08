package com.kebiao.app.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrTextRefinementTest {
    @Test fun improvesUncertainCharactersWithoutLosingReliableHeadersOrMovingGeometry() {
        val header = OcrTextBlock("星期一", .999f, OcrSourceBox(10f, 10f, 60f, 30f))
        val title = OcrTextBlock("软件设什", .7f, OcrSourceBox(10f, 50f, 90f, 70f))
        val result = OcrTextRefinement.merge(listOf(header, title), listOf(
            OcrTextBlock("星期", .9999f, OcrSourceBox(20f, 20f, 120f, 60f)),
            OcrTextBlock("软件设计", .98f, OcrSourceBox(22f, 98f, 178f, 140f)),
        ), 2f, 2f)
        assertEquals(header, result[0])
        assertEquals("软件设计", result[1].text)
        assertEquals(title.box, result[1].box)
        assertTrue(result[1].confidence < .8f, "Changed characters still require review")
    }

    @Test fun ignoresHigherConfidenceTextFromAnotherCell() {
        val original = OcrTextBlock("待校对", .6f, OcrSourceBox(10f, 50f, 80f, 70f))
        val unrelated = OcrTextBlock("其他课程", .999f, OcrSourceBox(210f, 100f, 360f, 140f))
        assertEquals(listOf(original), OcrTextRefinement.merge(listOf(original), listOf(unrelated), 2f, 2f))
    }

    @Test fun oneEnlargedRegionCannotOverwriteTwoSeparateOriginalLines() {
        val original = listOf(OcrTextBlock("原一", .5f, OcrSourceBox(10f, 10f, 90f, 30f)),
            OcrTextBlock("原二", .5f, OcrSourceBox(10f, 30f, 90f, 50f)))
        val enlarged = listOf(OcrTextBlock("两行被并在一起", .999f, OcrSourceBox(20f, 20f, 180f, 100f)))
        assertEquals(original, OcrTextRefinement.merge(original, enlarged, 2f, 2f))
    }
}
