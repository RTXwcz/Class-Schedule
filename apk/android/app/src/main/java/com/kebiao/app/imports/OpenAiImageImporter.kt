package com.kebiao.app.imports

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kebiao.app.ocr.CourseDraft
import com.kebiao.app.ocr.DraftField
import com.kebiao.app.domain.model.WeekRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class OpenAiImageImporter(private val context: Context) {
    fun saveApiKey(key: String) {
        val cipher = cipher(Cipher.ENCRYPT_MODE)
        val encrypted = cipher.doFinal(key.toByteArray(StandardCharsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_IV, android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP))
            .putString(KEY_VALUE, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            .apply()
    }

    fun hasApiKey(): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_VALUE)

    suspend fun importImage(
        imageBytes: ByteArray,
        endpoint: String = "https://api.openai.com/v1/chat/completions",
        model: String = "gpt-4o-mini",
    ): List<CourseDraft> = withContext(Dispatchers.IO) {
        val apiKey = readApiKey() ?: error("OpenAI API Key 未配置")
        val image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        val prompt = "识别这张课表图片，只返回 JSON 数组。每项字段为 name, weekday(1-7), startPeriod, endPeriod, weekRule(ALL/ODD/EVEN), building, room, locationNote。无法确认的字段填 null，不要输出 Markdown。"
        val body = "{\"model\":\"$model\",\"temperature\":0,\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"$prompt\"},{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:image/jpeg;base64,$image\"}}]}]}"
        val connection = java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 20_000
        connection.readTimeout = 90_000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            .bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) error("OpenAI 请求失败：${connection.responseCode}")
        parseResponse(response)
    }

    private fun parseResponse(response: String): List<CourseDraft> {
        val root = Json.parseToJsonElement(response).jsonObject
        val content = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: error("OpenAI 响应缺少课程内容")
        val json = content.trim().removePrefix("```").removePrefix("json").removeSuffix("```").trim()
        return Json.parseToJsonElement(json).jsonArray.map { item ->
            val obj = item.jsonObject
            CourseDraft(
                DraftField(obj["name"]?.jsonPrimitive?.content.orEmpty(), 1f),
                DraftField(obj["weekday"]?.jsonPrimitive?.intOrNull, 1f),
                DraftField(obj["startPeriod"]?.jsonPrimitive?.intOrNull, 1f),
                DraftField(obj["endPeriod"]?.jsonPrimitive?.intOrNull, 1f),
                DraftField(runCatching { WeekRule.valueOf(obj["weekRule"]?.jsonPrimitive?.content ?: "ALL") }.getOrDefault(WeekRule.ALL), 1f),
                DraftField(obj["building"]?.jsonPrimitive?.contentOrNull, 1f),
                DraftField(obj["room"]?.jsonPrimitive?.contentOrNull, 1f),
                DraftField(obj["locationNote"]?.jsonPrimitive?.contentOrNull, 1f),
            )
        }
    }

    private fun readApiKey(): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_VALUE, null) ?: return null
        val cipher = cipher(Cipher.DECRYPT_MODE)
        return cipher.doFinal(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP)).toString(StandardCharsets.UTF_8)
    }

    private fun cipher(mode: Int): Cipher {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        }
        val key = (store.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (mode == Cipher.DECRYPT_MODE) {
            val iv = android.util.Base64.decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_IV, ""), android.util.Base64.NO_WRAP)
            cipher.init(mode, key, GCMParameterSpec(128, iv))
        } else cipher.init(mode, key)
        return cipher
    }

    companion object { private const val ALIAS = "course_schedule_openai_key"; private const val PREFS = "openai_import"; private const val KEY_IV = "iv"; private const val KEY_VALUE = "value" }
}
