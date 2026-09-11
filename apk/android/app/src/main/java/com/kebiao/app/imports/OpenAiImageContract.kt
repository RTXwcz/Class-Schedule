package com.kebiao.app.imports

import com.kebiao.app.domain.model.WeekRule
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.DraftField
import com.kebiao.app.notifications.LessonPeriod
import kotlinx.serialization.json.*
import java.net.URI

object OpenAiImageContract {
    fun validateEndpoint(endpoint: String) {
        val uri = URI(endpoint)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) {
            "API 地址必须为完整 HTTPS 地址"
        }
    }

    /**
     * Accepts what people actually paste: `https://api.deepseek.com`, `.../v1`, or the full
     * `.../v1/chat/completions`. The chat path is appended when it is missing, and OpenAI keeps its
     * `/v1` prefix.
     */
    fun normalizeEndpoint(endpoint: String): String {
        val trimmed = endpoint.trim()
        validateEndpoint(trimmed)
        val uri = URI(trimmed)
        val path = uri.path.orEmpty().trimEnd('/')
        val chatPath = when {
            path.endsWith("/chat/completions") -> path
            path.endsWith("/v1") -> "$path/chat/completions"
            // OpenAI and most gateways (DeepSeek, New API relays, DashScope compatible mode) serve the
            // API under /v1; a bare host therefore gets /v1/chat/completions.
            path.isEmpty() -> "/v1/chat/completions"
            else -> "$path/v1/chat/completions"
        }
        return URI(uri.scheme, null, uri.host, uri.port, chatPath, null, null).toString()
    }

    fun request(model: String, base64: String, mime: String): String {
        require(model.isNotBlank()) { "请填写模型名称" }
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "请选择 JPG、PNG 或 WebP 图片" }
        return buildJsonObject {
            put("model", model)
            // Some gateways stream by default; the app reads a single JSON answer.
            put("stream", false)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                        put("type", "text")
                        put(
                            "text",
                            "识别课表，返回 JSON 对象 {\"courses\":[...],\"periods\":[...]}。" +
                                "courses 逐门一个时段：字段 name, weekday(1-7), startPeriod, endPeriod, " +
                                "weekRule(ALL/ODD/EVEN), building, room, locationNote, teacher, " +
                                "weeks(周次字符串，如1-16或1,3,5-8), courseNote；教学楼、教室、教师分开。" +
                                "periods 是图中左侧作息时间轴，每节课一项 {start,end}（形如 08:00），没有就返回空数组。" +
                                "看不清的字段填 null，不要猜测，不要输出 Markdown。",
                        )
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

    /** Courses plus the schedule the image prints on its time axis. */
    data class ParsedImport(val drafts: List<CourseDraft>, val periods: List<LessonPeriod>)

    fun parse(response: String): ParsedImport {
        val head = response.trimStart()
        require(head.startsWith("{") || head.startsWith("[")) {
            "接口返回的不是 JSON，而是在返回网页或纯文本。请确认地址是 API 端点（如 https://服务商/v1），而不是控制台首页。"
        }
        val choice = Json.parseToJsonElement(response).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("响应缺少课程内容")
        require(choice["finish_reason"]?.jsonPrimitive?.contentOrNull != "length") { "识别结果被截断，请拆分图片重试" }
        val content = choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            ?: error("响应缺少课程内容")
        val payload = Json.parseToJsonElement(content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val items = when (payload) {
            is JsonArray -> payload
            is JsonObject -> payload["courses"]?.jsonArray ?: error("响应缺少课程内容")
            else -> error("响应缺少课程内容")
        }
        require(items.size in 1..200) { "未识别到课程，或课程数量超过 200" }
        val periods = (payload as? JsonObject)?.get("periods")?.let { element ->
            runCatching {
                element.jsonArray.mapNotNull { item ->
                    val fields = item.jsonObject
                    val start = fields["start"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    val end = fields["end"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    LessonPeriod(start, end).takeIf { CLOCK.matches(start) && CLOCK.matches(end) }
                }
            }.getOrNull().orEmpty()
        }.orEmpty()
        val drafts = items.map { item ->
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
                teacher = DraftField(value("teacher"), 0f),
                weeks = DraftField(value("weeks"), 0f),
                courseNote = DraftField(value("courseNote"), 0f),
            )
        }
        return ParsedImport(drafts, periods)
    }

    /** Kept for callers that only need the courses. */
    fun parseResponse(response: String): List<CourseDraft> = parse(response).drafts

    private val CLOCK = Regex("^(?:[01]?\\d|2[0-3]):[0-5]\\d$")
}
