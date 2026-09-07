package com.kebiao.app.imports

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kebiao.app.ocr.CourseDraft
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

    suspend fun importImage(
        imageBytes: ByteArray,
        endpoint: String = "https://api.openai.com/v1/chat/completions",
        model: String = "gpt-4o-mini",
        mime: String = "image/jpeg",
    ): List<CourseDraft> = withContext(Dispatchers.IO) {
        OpenAiImageContract.validateEndpoint(endpoint)
        require(imageBytes.isNotEmpty() && imageBytes.size <= 10 * 1024 * 1024) { "图片大小须在 10 MB 以内" }
        val apiKey = readApiKey() ?: error("OpenAI API Key 未配置")
        val image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        val body = OpenAiImageContract.request(model, image, mime)
        val connection = java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) error("OpenAI 请求失败：${connection.responseCode}")
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
