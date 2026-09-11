package com.kebiao.app.ocr

import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertEquals
import java.io.File
import java.text.Normalizer

/** Optional local-only reproduction. Images and OCR text are never bundled or sent to a server. */
class LocalImageDiagnosticTest {
    @Test fun recognizeSuppliedImageWithBothModels() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.getExternalFilesDir(null), "ocr-repro.jpg")
        assumeTrue("Pass a local reproduction image explicitly", input.exists())
        val original = BitmapFactory.decodeFile(input.absolutePath)
        val scale = InstrumentationRegistry.getArguments().getString("ocrScale")?.toIntOrNull()?.coerceIn(1, 3) ?: 1
        val bitmap = if (scale > 1) Bitmap.createScaledBitmap(original, original.width * scale, original.height * scale, true) else original
        try {
            val manager = OcrModelManager(context)
            for (id in OcrModelManager.ModelId.entries) {
                manager.download(id)
                val blocks = PaddleOcrEngine(context, id, manager).recognize(bitmap)
                val data = buildJsonObject {
                    put("model", id.wireName)
                    put("width", bitmap.width); put("height", bitmap.height)
                    put("blocks", JsonArray(blocks.map { block -> buildJsonObject {
                        put("text", block.text); put("confidence", block.confidence)
                        put("box", JsonArray(listOf(block.box.left, block.box.top, block.box.right, block.box.bottom).map(::JsonPrimitive)))
                        block.cellBox?.let { cell -> put("cell", JsonArray(listOf(cell.left, cell.top, cell.right, cell.bottom).map(::JsonPrimitive))) }
                    } }))
                    put("drafts", JsonArray(CourseTableParser().parse(blocks).map { draft -> buildJsonObject {
                        put("name", draft.name.value); put("day", draft.weekday.value); put("start", draft.startPeriod.value); put("end", draft.endPeriod.value)
                        put("rule", draft.weekRule.value?.name); put("weeks", draft.weeks.value)
                        put("teacher", draft.teacher.value); put("building", draft.building.value)
                        put("room", draft.room.value); put("note", draft.courseNote.value)
                        put("place", draft.locationNote.value)
                    } }))
                    put("periods", JsonArray(CourseTableParser().parsePeriods(blocks).map { period -> buildJsonObject {
                        put("start", period.start); put("end", period.end)
                    } }))
                }
                File(context.getExternalFilesDir(null), "ocr-${id.wireName}.json").writeText(data.toString())
                val expectedFile = File(context.getExternalFilesDir(null), "ocr-expected.json")
                if (expectedFile.exists()) {
                    fun rows(array: JsonArray) = array.map { row ->
                        val fields = row.jsonObject
                        listOf("name", "day", "start", "end").joinToString("|") { key ->
                            Normalizer.normalize(fields.getValue(key).jsonPrimitive.content, Normalizer.Form.NFKC).replace(" ", "")
                        }
                    }.sorted()
                    val expected = Json.parseToJsonElement(expectedFile.readText()).jsonArray
                    assertEquals("${id.wireName}: reviewed image titles, weekdays and complete period spans", rows(expected), rows(data.getValue("drafts").jsonArray))
                }
            }
        } finally { bitmap.recycle(); if (original !== bitmap) original.recycle() }
    }
}
