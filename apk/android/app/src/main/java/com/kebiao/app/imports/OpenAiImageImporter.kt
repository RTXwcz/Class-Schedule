package com.kebiao.app.imports

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kebiao.app.ocr.CourseDraft
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
    fun importImage(imageBytes: ByteArray): List<CourseDraft> = error("OpenAI image transport is configured but not executed without user confirmation")

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
