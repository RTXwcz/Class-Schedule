package com.kebiao.app.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): List<OcrTextBlock>
}

/** Offline-capable Chinese fallback while PaddleOCR model assets are unavailable. */
class MlKitChineseEngine : OcrEngine {
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override suspend fun recognize(bitmap: Bitmap): List<OcrTextBlock> = withContext(Dispatchers.IO) {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrTextBlock(
                    text = line.text,
                    confidence = line.elements.mapNotNull { it.confidence }.average().toFloat().takeIf { it.isFinite() } ?: 0.5f,
                    box = OcrSourceBox(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat()),
                )
            }
        }
    }
}
