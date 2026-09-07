package com.kebiao.app.ocr

import android.graphics.Bitmap

interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): List<OcrTextBlock>
}
