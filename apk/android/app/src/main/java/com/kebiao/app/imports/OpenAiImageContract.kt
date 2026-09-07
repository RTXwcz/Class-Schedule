package com.kebiao.app.imports

import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.DraftField
import kotlinx.serialization.json.*
import java.net.URI

object OpenAiImageContract {
    fun validateEndpoint(endpoint: String) {
        val uri = URI(endpoint)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "API 地址必须为完整 HTTPS 地址"
        }
    }

    fun request(model: String, base64: String, mime: String): String {
        require(model.isNotBlank()) { "请填写模型名称" }
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "请选择 JPG、PNG 或 WebP 图片" }
        return buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "text")
                            put("text", "识别课表，逐门返回 JSON 数组。字段 name, weekday(1-7), startPeriod, endPeriod, weekRule(ALL/ODD/EVEN), building, room, locationNote。每个上课时段分别列出。教学楼与教室分开，教师及其他信息保留到 locationNote。看不清的字段填 null，不要猜测，不要输出 Markdown。")
                        }
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:$mime;base64,$base64") }
                        }
                    }
                }
            }
        }.toString()
    }

    fun parseResponse(response: String): List<CourseDraft> {
        val choice = Json.parseToJsonElement(response).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("响应缺少课程内容")
        require(choice["finish_reason"]?.jsonPrimitive?.contentOrNull != "length") { "识别结果被截断，请拆分图片重试" }
        val content = choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: error("响应缺少课程内容")
        val json = content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val items = Json.parseToJsonElement(json).jsonArray
        require(items.size in 1..200) { "未识别到课程，或课程数量超过 200" }
        return items.map { item ->
            val obj = item.jsonObject
            fun value(key: String) = obj[key]?.jsonPrimitive?.contentOrNull
            // Vision responses do not expose calibrated field confidence.
            CourseDraft(
                name = DraftField(value("name").orEmpty(), 0f),
                weekday = DraftField(value("weekday")?.toIntOrNull(), 0f),
                startPeriod = DraftField(value("startPeriod")?.toIntOrNull(), 0f),
                endPeriod = DraftField(value("endPeriod")?.toIntOrNull(), 0f),
                weekRule = DraftField(runCatching { WeekRule.valueOf(value("weekRule").orEmpty()) }.getOrNull(), 0f),
                building = DraftField(value("building"), 0f),
                room = DraftField(value("room"), 0f),
                locationNote = DraftField(value("locationNote"), 0f),
            )
        }
    }
}
