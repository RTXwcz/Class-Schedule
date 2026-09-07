package com.kebiao.app.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Network download is only performed when the test explicitly invokes the user download action. */
class PaddleOcrDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun explicitTinyDownloadThenChineseRecognition() = runBlocking {
        val manager = OcrModelManager(context)
        val progress = mutableListOf<Long>()
        manager.download(OcrModelManager.ModelId.TINY) { progress.add(it.downloadedBytes) }
        assertTrue(manager.isInstalled(OcrModelManager.ModelId.TINY))
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(progress.last() == manager.spec(OcrModelManager.ModelId.TINY).sizeBytes)
        verifyChineseRecognition(manager)
    }

    @Test
    fun installedTinyRecognizesWithoutDownload() = runBlocking {
        val manager = OcrModelManager(context)
        assumeTrue("Run the explicit download test once before the offline test", manager.isInstalled(OcrModelManager.ModelId.TINY))
        verifyChineseRecognition(manager)
    }

    @Test
    fun cancelledSmallDownloadNeverActivates() = runBlocking {
        val manager = OcrModelManager(context)
        assumeFalse("Preserve a previously installed small model", manager.isInstalled(OcrModelManager.ModelId.SMALL))
        try {
            manager.download(OcrModelManager.ModelId.SMALL) { throw CancellationException("Test cancellation") }
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertFalse(manager.isInstalled(OcrModelManager.ModelId.SMALL))
        }
    }

    @Test
    fun explicitSmallDownloadThenChineseRecognition() = runBlocking {
        val manager = OcrModelManager(context)
        manager.download(OcrModelManager.ModelId.SMALL) { }
        assertTrue(manager.isInstalled(OcrModelManager.ModelId.SMALL))
        verifyChineseRecognition(manager, OcrModelManager.ModelId.SMALL)
    }

    private suspend fun verifyChineseRecognition(manager: OcrModelManager, id: OcrModelManager.ModelId = OcrModelManager.ModelId.TINY) {
        val image = Bitmap.createBitmap(1200, 460, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(image)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 56f }
            canvas.drawText("星期一  高等数学", 60f, 100f, paint)
            canvas.drawText("第1-2节  1-16周", 60f, 220f, paint)
            canvas.drawText("张老师  教学楼A  301", 60f, 340f, paint)
            val blocks = PaddleOcrEngine(context, id, manager).recognize(image)
            val text = blocks.joinToString("\n") { it.text }
            assertTrue("Missing Chinese title in: $text", text.contains("高等数学"))
            assertTrue("Missing classroom in: $text", text.contains("301"))
            assertTrue("Missing weekday in: $text", text.contains("星期一"))
            assertTrue(blocks.all { it.confidence in 0f..1f && it.box.right > it.box.left && it.box.bottom > it.box.top })
            assertTrue(blocks.all { it.box.left >= 0 && it.box.top >= 0 && it.box.right <= image.width && it.box.bottom <= image.height })
        } finally {
            image.recycle()
        }
    }
}
