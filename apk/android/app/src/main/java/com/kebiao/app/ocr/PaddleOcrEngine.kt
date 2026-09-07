package com.kebiao.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import com.kebiao.app.ocr.paddle.EngineConfig
import com.kebiao.app.ocr.paddle.PaddleOCR
import com.kebiao.app.ocr.paddle.PaddleOCRConfig
import com.kebiao.app.ocr.paddle.util.OpenCVUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Official PP-OCRv6 Android pipeline. Construction and recognition never download a model. */
class PaddleOcrEngine(
    context: Context,
    private val modelId: OcrModelManager.ModelId = OcrModelManager.ModelId.TINY,
    private val modelManager: OcrModelManager = OcrModelManager(context),
) : OcrEngine {
    private val appContext = context.applicationContext

    override suspend fun recognize(bitmap: Bitmap): List<OcrTextBlock> =
        modelManager.withInstalledModel(modelId) { files ->
            require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "Invalid OCR image" }
            require(bitmap.width.toLong() * bitmap.height <= MAX_BITMAP_PIXELS) { "OCR image is too large" }
            currentCoroutineContext().ensureActive()
            check(OpenCVUtils.init(appContext)) { "Unable to load OpenCV" }
            val engine = PaddleOCR.create(
                context = appContext,
                config = PaddleOCRConfig(
                    detLimitSideLen = 2048,
                    detLimitType = "max",
                    detMaxSideLimit = 2048,
                    recBatchSize = 1,
                    recScoreThresh = 0.0f,
                ),
                engineConfig = EngineConfig(numThreads = 2),
                detModelAssetPath = files.detection.absolutePath,
                recModelAssetPath = files.recognition.absolutePath,
                recConfigAssetPath = files.dictionary.absolutePath,
            )
            try {
                val result = engine.recognize(bitmap)
                currentCoroutineContext().ensureActive()
                result.results.filter { it.text.isNotBlank() }.map { item ->
                    val points = item.box.points
                    OcrTextBlock(
                        text = item.text,
                        confidence = item.confidence.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f,
                        box = OcrSourceBox(
                            points.minOf { it.x }, points.minOf { it.y },
                            points.maxOf { it.x }, points.maxOf { it.y },
                        ),
                    )
                }
            } finally {
                withContext(NonCancellable) { engine.release() }
            }
        }

    suspend fun recognize(uri: Uri): List<OcrTextBlock> = withContext(Dispatchers.IO) {
        require(uri.scheme == "content" || uri.scheme == "file") { "Select a local image" }
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unable to decode OCR image" }
        var sample = 1
        while (bounds.outWidth.toLong() * bounds.outHeight / sample / sample > MAX_BITMAP_PIXELS ||
            maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Unable to decode OCR image")
        var oriented = decoded
        try {
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            if (!matrix.isIdentity) oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val swapsAxes = orientation in setOf(
                ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270,
            )
            val fullWidth = if (swapsAxes) bounds.outHeight else bounds.outWidth
            val fullHeight = if (swapsAxes) bounds.outWidth else bounds.outHeight
            val scaleX = fullWidth.toFloat() / oriented.width
            val scaleY = fullHeight.toFloat() / oriented.height
            recognize(oriented).map { block ->
                // URI results use the full EXIF-oriented image coordinates shown by image loaders.
                block.copy(box = OcrSourceBox(
                    block.box.left * scaleX, block.box.top * scaleY,
                    block.box.right * scaleX, block.box.bottom * scaleY,
                ))
            }
        } finally {
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
    }

    private companion object { const val MAX_BITMAP_PIXELS = 12_000_000L }
}
