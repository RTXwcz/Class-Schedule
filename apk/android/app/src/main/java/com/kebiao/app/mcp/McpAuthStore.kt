package com.kebiao.app.mcp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface McpTokenStore {
    fun currentToken(): String
    fun rotate(): String
    fun isValid(token: String): Boolean = token.isNotBlank() && MessageDigest.isEqual(
        token.toByteArray(Charsets.UTF_8), currentToken().toByteArray(Charsets.UTF_8),
    )
}

class McpAuthStore(context: Context) : McpTokenStore {
    private val prefs = context.applicationContext.getSharedPreferences("mcp_auth", Context.MODE_PRIVATE)

    override fun currentToken(): String = synchronized(lock) {
        val encoded = prefs.getString("encrypted_token", null)
        if (encoded == null) {
            // Preserve existing clients when migrating the former plaintext token.
            val legacy = prefs.getString("token", null)
            if (!legacy.isNullOrBlank()) { save(legacy); legacy } else rotate()
        } else {
            runCatching {
                val bytes = Base64.getDecoder().decode(encoded)
                check(bytes.size > 12) { "token ciphertext is truncated" }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
            }.getOrElse {
                // A restored backup, a truncated blob or a lost keystore key all mean the same thing:
                // issue a new token instead of failing inside the authorization path.
                rotate()
            }
        }
    }

    override fun rotate(): String = synchronized(lock) {
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
            .also(::save)
    }

    private fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("encrypted_token", Base64.getEncoder().encodeToString(cipher.iv + encrypted))
            .remove("token").commit()) { "Token 保存失败" }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
                generateKey()
            }
        }
        return (store.getEntry(ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    companion object {
        private val lock = Any()
        private const val ALIAS = "class_schedule_mcp_token"
    }
}
