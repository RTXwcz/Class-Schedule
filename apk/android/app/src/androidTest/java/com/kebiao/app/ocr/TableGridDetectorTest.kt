package com.kebiao.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.*
import org.junit.Test

class TableGridDetectorTest {
    @Test fun faintRulesPreserveUnequalColumnsAndIndependentMergedRows() {
        val bitmap = grid()
        try {
            val blocks = listOf(
                text(40f, 44f, 100f, 56f),
                text(40f, 66f, 80f, 78f),
                text(165f, 44f, 230f, 56f),
                text(165f, 82f, 230f, 94f),
                text(310f, 96f, 337f, 108f),
            )
            val cells = TableGridDetector.detect(bitmap, blocks)
            assertCell(cells[0], 20, 20, 140, 120)
            assertEquals("Wrapped lines occupy the same real cell", cells[0], cells[1])
            assertCell(cells[2], 140, 20, 290, 70)
            assertCell(cells[3], 140, 70, 290, 160)
            assertCell(cells[4], 290, 20, 360, 160)
        } finally { bitmap.recycle() }
    }

    @Test fun noGridAndCrossBorderTextHaveNoInferredCell() {
        val blank = Bitmap.createBitmap(400, 220, Bitmap.Config.ARGB_8888)
        blank.eraseColor(Color.WHITE)
        try {
            assertNull(TableGridDetector.detect(blank, listOf(text(40f, 40f, 120f, 60f))).single())
        } finally { blank.recycle() }
        val bitmap = grid()
        try {
            val crossing = listOf(text(120f, 40f, 180f, 60f), text(160f, 60f, 230f, 85f))
            assertTrue(TableGridDetector.detect(bitmap, crossing).all { it == null })
        } finally { bitmap.recycle() }
    }

    @Test fun incompleteCellSidesAreRejectedEvenWhenOtherRowsHaveRules() {
        val bitmap = grid()
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { color = Color.rgb(222, 222, 222); strokeWidth = 5f }
            canvas.drawLine(140f, 125f, 140f, 190f, paint)
            assertNull(TableGridDetector.detect(bitmap, listOf(text(40f, 150f, 110f, 170f))).single())
        } finally { bitmap.recycle() }
    }

    @Test fun lightRulesOnDarkBackgroundAreSupported() {
        val bitmap = grid(background = 30, rule = 70)
        try {
            assertCell(TableGridDetector.detect(bitmap, listOf(text(40f, 44f, 100f, 56f))).single(), 20, 20, 140, 120)
        } finally { bitmap.recycle() }
    }

    @Test fun alignedTextWithoutRulesDoesNotCreateCells() {
        val bitmap = Bitmap.createBitmap(400, 220, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint().apply { color = Color.BLACK; textSize = 13f }
            val blocks = mutableListOf<OcrTextBlock>()
            for (y in 35..185 step 30) {
                for (x in listOf(25, 145, 290)) {
                    canvas.drawText("Title III", x.toFloat(), y.toFloat(), paint)
                    blocks += text(x.toFloat(), y - 13f, x + 65f, y + 2f)
                }
            }
            assertTrue(TableGridDetector.detect(bitmap, blocks).all { it == null })
        } finally { bitmap.recycle() }
    }

    private fun grid(background: Int = 222, rule: Int = 217): Bitmap {
        val bitmap = Bitmap.createBitmap(400, 220, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(background, background, background))
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.rgb(rule, rule, rule)
            strokeWidth = 1f
            isAntiAlias = false
        }
        for (x in listOf(20f, 140f, 290f, 360f)) canvas.drawLine(x, 20f, x, 200f, paint)
        for (y in listOf(20f, 200f)) canvas.drawLine(20f, y, 360f, y, paint)
        canvas.drawLine(20f, 120f, 140f, 120f, paint)
        canvas.drawLine(140f, 70f, 290f, 70f, paint)
        canvas.drawLine(140f, 160f, 360f, 160f, paint)
        return bitmap
    }

    private fun text(left: Float, top: Float, right: Float, bottom: Float) =
        OcrTextBlock("Synthetic title", 1f, OcrSourceBox(left, top, right, bottom))

    private fun assertCell(actual: OcrSourceBox?, left: Int, top: Int, right: Int, bottom: Int) {
        assertNotNull(actual)
        assertEquals(left.toFloat(), actual!!.left, 1f)
        assertEquals(top.toFloat(), actual.top, 1f)
        assertEquals(right.toFloat(), actual.right, 1f)
        assertEquals(bottom.toFloat(), actual.bottom, 1f)
    }
}
