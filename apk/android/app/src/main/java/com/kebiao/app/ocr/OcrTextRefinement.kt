package com.kebiao.app.ocr

/** A second resolution may improve characters, but must never move a title to another cell. */
internal object OcrTextRefinement {
    fun merge(original: List<OcrTextBlock>, enlarged: List<OcrTextBlock>, scaleX: Float, scaleY: Float): List<OcrTextBlock> {
        val candidates = enlarged.map { block -> block.copy(box = OcrSourceBox(
            block.box.left / scaleX, block.box.top / scaleY, block.box.right / scaleX, block.box.bottom / scaleY)) }
        return original.mapIndexed { index, block ->
            if (block.confidence >= .98f) return@mapIndexed block
            val alternative = candidates.maxByOrNull { overlap(block.box, it.box) }
                ?.takeIf { candidate -> overlap(block.box, candidate.box) >= .5f && candidate.confidence >= block.confidence + .02f &&
                    original.indices.none { it != index && overlap(original[it].box, candidate.box) >= .25f } }
                ?: return@mapIndexed block
            block.copy(text = alternative.text, confidence = if (alternative.text == block.text) alternative.confidence
                else minOf(alternative.confidence, .79f))
        }
    }

    private fun overlap(a: OcrSourceBox, b: OcrSourceBox): Float {
        val intersection = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f) *
            (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        val union = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - intersection
        return if (union > 0) intersection / union else 0f
    }
}
