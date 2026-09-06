package com.kebiao.app.mcp

import android.content.Context
import java.security.SecureRandom
import java.util.Base64

interface McpTokenStore {
    fun currentToken(): String
    fun rotate(): String
    fun isValid(token: String): Boolean = token.isNotBlank() && token == currentToken()
}

class McpAuthStore(context: Context) : McpTokenStore {
    private val prefs = context.applicationContext.getSharedPreferences("mcp_auth", Context.MODE_PRIVATE)

    override fun currentToken(): String {
        val existing = prefs.getString(KEY_TOKEN, null)
        if (!existing.isNullOrBlank()) return existing
        return rotate()
    }

    override fun rotate(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).also {
            prefs.edit().putString(KEY_TOKEN, it).apply()
        }
    }

    companion object { private const val KEY_TOKEN = "token" }
}
