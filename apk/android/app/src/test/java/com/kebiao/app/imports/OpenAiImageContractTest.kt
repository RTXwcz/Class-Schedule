package com.kebiao.app.imports

import kotlinx.serialization.json.*
import kotlin.test.*

class OpenAiImageContractTest {
    @Test fun requestEscapesModelAndPreservesImageMime() {
        val root = Json.parseToJsonElement(OpenAiImageContract.request("model\"\\test", "AA==", "image/png")).jsonObject
        assertEquals("model\"\\test", root["model"]!!.jsonPrimitive.content)
        val content = root["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("data:image/png;base64,AA==", content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test fun missingValuesRemainUnconfirmedAndInvalid() {
        val response = response("""[{"name":null,"weekday":null,"startPeriod":1,"endPeriod":2,"weekRule":null}]""")
        val draft = OpenAiImageContract.parseResponse(response).single()
        assertEquals("", draft.name.value)
        assertNull(draft.weekday.value)
        assertFalse(ImportValidation.canPersist(listOf(draft)))
        assertFalse(ImportValidation.canPersist(listOf(draft.confirmAll())))
        assertEquals(0f, draft.name.confidence)
    }

    @Test fun refusesTruncatedAndEmptyResponses() {
        assertFailsWith<IllegalArgumentException> { OpenAiImageContract.parseResponse(response("[]", "length")) }
        assertFailsWith<IllegalArgumentException> { OpenAiImageContract.parseResponse(response("[]")) }
    }

    @Test fun validFencedJsonIsEligibleForBatchApplyWithoutFieldConfirmations() {
        val draft = OpenAiImageContract.parseResponse(response("```json\n[{\"name\":\"数学\",\"weekday\":1,\"startPeriod\":1,\"endPeriod\":2,\"weekRule\":\"ODD\"}]\n```")).single()
        assertFalse(draft.name.confirmed)
        assertTrue(ImportValidation.canPersist(listOf(draft)))
    }

    @Test fun validatesCustomHttpsEndpoint() {
        OpenAiImageContract.validateEndpoint("https://example.com/v1/chat/completions")
        listOf("http://example.com", "file:///tmp/key", "https://user:pass@example.com", "https://example.com/#fragment").forEach {
            assertFails { OpenAiImageContract.validateEndpoint(it) }
        }
    }

    @Test fun unknownWeekRuleCannotSilentlyBecomeWeekly() {
        val draft = OpenAiImageContract.parseResponse(response("""[{"name":"数学","weekday":1,"startPeriod":1,"endPeriod":2,"weekRule":"UNKNOWN"}]""")).single()
        assertNull(draft.weekRule.value)
        assertFalse(ImportValidation.canPersist(listOf(draft.confirmAll())))
    }

    private fun response(content: String, finish: String = "stop") = buildJsonObject {
        putJsonArray("choices") {
            addJsonObject {
                put("finish_reason", finish)
                putJsonObject("message") { put("content", content) }
            }
        }
    }.toString()
}
