package com.kebiao.app.ocr

import android.graphics.Bitmap
import kotlin.math.roundToInt

/**
 * Finds closed, axis-aligned table cells from visible rules. Coordinates refer to [bitmap], just
 * like the input OCR boxes. Unequal column widths and independently merged rows are supported.
 * A missing/broken border or text spanning a border produces null, never a guessed cell.
 *
 * Local contrast also detects faint rules on a tinted background. No OCR vocabulary, model,
 * network call or OpenCV allocation is needed; working storage is one grayscale byte per pixel.
 */
object TableGridDetector {
    fun detect(bitmap: Bitmap, blocks: List<OcrTextBlock>): List<OcrSourceBox?> {
        if (blocks.isEmpty()) return emptyList()
        val unknown = List<OcrSourceBox?>(blocks.size) { null }
        if (bitmap.isRecycled || bitmap.width < 32 || bitmap.height < 32 ||
            bitmap.width.toLong() * bitmap.height > MAX_PIXELS) return unknown
        // Hardware bitmaps do not expose their pixels. The OCR decoder supplies software bitmaps;
        // other callers get an explicit absence of structural evidence instead of a crash.
        val pixels = runCatching { GrayImage.read(bitmap) }.getOrNull() ?: return unknown
        val radius = (minOf(pixels.width, pixels.height) / 160f).roundToInt().coerceIn(3, 12)
        val verticalScores = IntArray(pixels.width)
        for (x in radius until pixels.width - radius) {
            var count = 0
            var run = 0
            var longest = 0
            var gap = 0
            for (y in 0 until pixels.height) {
                if (pixels.verticalRule(x, y, radius)) {
                    count++
                    run += gap + 1
                    gap = 0
                    longest = maxOf(longest, run)
                } else {
                    gap++
                    // Horizontal intersections briefly hide local vertical contrast.
                    if (gap > radius * 2) { run = 0; gap = 0 }
                }
            }
            // Aligned letter stems must not masquerade as a full column border.
            if (longest >= maxOf(16, (pixels.height * 0.18f).roundToInt())) {
                verticalScores[x] = count
            }
        }
        val columns = peaks(verticalScores, (pixels.height * 0.28f).roundToInt())
        if (columns.size < 3) return unknown
        val horizontalByColumn = mutableMapOf<Int, IntArray>()
        return blocks.map { block ->
            val box = block.box
            if (!box.isValid()) return@map null
            val centerX = (box.left + box.right) / 2f
            val centerY = (box.top + box.bottom) / 2f
            val column = (0 until columns.lastIndex).firstOrNull {
                centerX > columns[it] && centerX < columns[it + 1]
            } ?: return@map null
            val left = columns[column]
            val right = columns[column + 1]
            if (right - left < maxOf(12, radius * 4) ||
                box.left < left - radius || box.right > right + radius) return@map null
            val rows = horizontalByColumn.getOrPut(column) {
                val scores = IntArray(pixels.height)
                val startX = left + radius
                val endX = right - radius
                for (y in radius until pixels.height - radius) {
                    var count = 0
                    for (x in startX..endX) {
                        if (pixels.horizontalRule(x, y, radius)) count++
                    }
                    scores[y] = count
                }
                peaks(scores, ((endX - startX + 1) * 0.8f).roundToInt())
            }
            val row = (0 until rows.lastIndex).firstOrNull {
                centerY > rows[it] && centerY < rows[it + 1]
            } ?: return@map null
            val top = rows[row]
            val bottom = rows[row + 1]
            if (bottom - top < radius * 3 ||
                box.top < top - radius || box.bottom > bottom + radius) return@map null
            // A long vertical segment elsewhere in the image is insufficient: both side borders
            // must continue across this particular row, including merged cells.
            val startY = top + radius
            val endY = bottom - radius
            val required = ((endY - startY + 1) * 0.8f).roundToInt()
            if ((startY..endY).count { pixels.verticalRule(left, it, radius) } < required ||
                (startY..endY).count { pixels.verticalRule(right, it, radius) } < required) {
                return@map null
            }
            OcrSourceBox(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        }
    }

    /** Collapse a stroke's adjacent pixels to its strongest center; never assume uniform spacing. */
    private fun peaks(scores: IntArray, threshold: Int): IntArray {
        val result = mutableListOf<Int>()
        var position = 0
        while (position < scores.size) {
            if (scores[position] < threshold) {
                position++
                continue
            }
            var best = position
            while (position < scores.size && scores[position] >= threshold) {
                if (scores[position] > scores[best]) best = position
                position++
            }
            result += best
        }
        return result.toIntArray()
    }

    private fun OcrSourceBox.isValid() =
        left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
            left >= 0f && top >= 0f && right > left && bottom > top

    private class GrayImage(val width: Int, val height: Int, private val data: ByteArray) {
        private fun value(x: Int, y: Int) = data[y * width + x].toInt() and 0xff

        fun verticalRule(x: Int, y: Int, radius: Int): Boolean =
            localRule(value(x - radius, y), value(x, y), value(x + radius, y))

        fun horizontalRule(x: Int, y: Int, radius: Int): Boolean =
            localRule(value(x, y - radius), value(x, y), value(x, y + radius))

        companion object {
            fun read(bitmap: Bitmap): GrayImage {
                val data = ByteArray(bitmap.width * bitmap.height)
                val row = IntArray(bitmap.width)
                for (y in 0 until bitmap.height) {
                    bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                    for (x in row.indices) {
                        val pixel = row[x]
                        val red = (pixel ushr 16) and 0xff
                        val green = (pixel ushr 8) and 0xff
                        val blue = pixel and 0xff
                        val alpha = (pixel ushr 24) and 0xff
                        val gray = (red * 77 + green * 150 + blue * 29) ushr 8
                        // Match the white background used when displaying transparent imports.
                        data[y * bitmap.width + x] = ((gray * alpha + 255 * (255 - alpha)) / 255).toByte()
                    }
                }
                return GrayImage(bitmap.width, bitmap.height, data)
            }

            private fun localRule(before: Int, center: Int, after: Int): Boolean =
                (before - center >= MIN_CONTRAST && after - center >= MIN_CONTRAST) ||
                    (center - before >= MIN_CONTRAST && center - after >= MIN_CONTRAST)
        }
    }

    private const val MAX_PIXELS = 12_000_000L
    private const val MIN_CONTRAST = 3
}
