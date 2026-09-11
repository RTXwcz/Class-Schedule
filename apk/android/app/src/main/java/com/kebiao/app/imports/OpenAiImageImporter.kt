package com.kebiao.app.imports

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kebiao.app.ocr.CourseDraft
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class OpenAiImageImporter(private val context: Context) {
    suspend fun importUri(uri: android.net.Uri, endpoint: String, model: String): List<CourseDraft> = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: error("无法确定图片格式")
        val limit = 10 * 1024 * 1024
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= limit) { "请选择 10 MB 以内的图片" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("无法读取图片")
        importImage(bytes, endpoint, model, mime)
    }

    fun saveApiKey(key: String) {
        val cipher = cipher(Cipher.ENCRYPT_MODE)
        val encrypted = cipher.doFinal(key.toByteArray(StandardCharsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_IV, android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP))
            .putString(KEY_VALUE, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            .apply()
    }

    fun hasApiKey(): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_VALUE)

    /** Proves address + key + model without spending an image request. */
    suspend fun testConnection(endpoint: String, model: String): String = withContext(Dispatchers.IO) {
        require(model.isNotBlank()) { "请填写模型名称" }
        val url = OpenAiImageContract.normalizeEndpoint(endpoint)
        val apiKey = readApiKey() ?: error("请先保存 API Key")
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(connectionCheckBody(model).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(600) }.orEmpty()
            connectionCheckResult(status, body)
        } finally { connection.disconnect() }
    }

    suspend fun importImage(
        imageBytes: ByteArray,
        endpoint: String = "https://api.openai.com/v1/chat/completions",
        model: String = "gpt-4o-mini",
        mime: String = "image/jpeg",
    ): List<CourseDraft> = withContext(Dispatchers.IO) {
        OpenAiImageContract.validateEndpoint(endpoint)
        val url = OpenAiImageContract.normalizeEndpoint(endpoint)
        require(imageBytes.isNotEmpty() && imageBytes.size <= 10 * 1024 * 1024) { "图片大小须在 10 MB 以内" }
        val apiKey = readApiKey() ?: error("OpenAI API Key 未配置")
        val image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        val body = OpenAiImageContract.request(model, image, mime)
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) {
                // The provider explains itself in the body ("unknown model", "no image support", …);
                // showing it is the difference between a usable error and a bare status code.
                val detail = runCatching {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(500) }.orEmpty()
                }.getOrNull().orEmpty()
                error("识别服务返回 ${connection.responseCode}" + describeFailure(detail) + failureHint(connection.responseCode, detail))
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    require(result.length + count <= 2 * 1024 * 1024) { "识别响应过大" }
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
            OpenAiImageContract.parseResponse(response)
        } finally { connection.disconnect() }
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

/** What the status code usually means for this feature, so the user can fix it without guessing. */
internal fun failureHint(status: Int, body: String): String = when {
    body.isNotBlank() -> ""
    status == 401 -> "。请检查 API Key 是否正确。"
    status == 403 -> "。Key 无权限，或模型名不存在；可在设置里用「测试连接」确认。"
    status == 404 -> "。地址可能不对：填服务根地址（如 https://服务商 或 https://服务商/v1）即可，应用会补全 /chat/completions。"
    status == 400 -> "。模型可能不支持图片输入，或参数不被接受。"
    status == 429 -> "。请求过于频繁或额度不足，请稍后重试。"
    else -> ""
}

/** A one-shot text request that proves the address, the key and the model name line up. */
internal fun connectionCheckBody(model: String): String = buildJsonObject {
    put("model", model)
    put("stream", false)
    put("max_tokens", 1)
    putJsonArray("messages") {
        addJsonObject {
            put("role", "user")
            put("content", "ping")
        }
    }
}.toString()

internal fun connectionCheckResult(status: Int, body: String): String = when {
    status in 200..299 -> "连接正常，模型 ${
        runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["model"]?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty().ifBlank { "已响应" }
    }"
    else -> "连接失败（$status）" + describeFailure(body) + failureHint(status, body)
}

/** Pulls the provider's own message out of an OpenAI-style error body. */
internal fun describeFailure(body: String): String {
    if (body.isBlank()) return ""
    val message = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["error"]
            ?.let { element ->
                element.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                    ?: element.jsonPrimitive.contentOrNull
            }
    }.getOrNull()
    return "：" + (message ?: body).replace(Regex("\\s+"), " ").trim().take(300)
}
