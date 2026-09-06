package com.kebiao.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val theme: String = "system",
    val semesterStartDate: String? = null,
    val reminderLeadMinutes: Int = 10,
    val notificationsEnabled: Boolean = true,
    val useLocalOcr: Boolean = false,
    val mcpEnabled: Boolean = false,
    val mcpPort: Int = 8765,
    val mcpWriteConfirmation: Boolean = true,
)

class AppSettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val semesterStartDate = stringPreferencesKey("semester_start_date")
        val reminderLeadMinutes = intPreferencesKey("reminder_lead_minutes")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val useLocalOcr = booleanPreferencesKey("use_local_ocr")
        val mcpEnabled = booleanPreferencesKey("mcp_enabled")
        val mcpPort = intPreferencesKey("mcp_port")
        val mcpWriteConfirmation = booleanPreferencesKey("mcp_write_confirmation")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { p ->
        AppSettings(
            theme = p[Keys.theme] ?: "system",
            semesterStartDate = p[Keys.semesterStartDate],
            reminderLeadMinutes = (p[Keys.reminderLeadMinutes] ?: 10).coerceIn(0, 120),
            notificationsEnabled = p[Keys.notificationsEnabled] ?: true,
            useLocalOcr = p[Keys.useLocalOcr] ?: false,
            mcpEnabled = p[Keys.mcpEnabled] ?: false,
            mcpPort = (p[Keys.mcpPort] ?: 8765).coerceIn(1024, 65535),
            mcpWriteConfirmation = p[Keys.mcpWriteConfirmation] ?: true,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.appSettingsDataStore.edit { p ->
            val current = AppSettings(
                theme = p[Keys.theme] ?: "system",
                semesterStartDate = p[Keys.semesterStartDate],
                reminderLeadMinutes = p[Keys.reminderLeadMinutes] ?: 10,
                notificationsEnabled = p[Keys.notificationsEnabled] ?: true,
                useLocalOcr = p[Keys.useLocalOcr] ?: false,
                mcpEnabled = p[Keys.mcpEnabled] ?: false,
                mcpPort = p[Keys.mcpPort] ?: 8765,
                mcpWriteConfirmation = p[Keys.mcpWriteConfirmation] ?: true,
            )
            val next = transform(current)
            p[Keys.theme] = next.theme
            if (next.semesterStartDate == null) p.remove(Keys.semesterStartDate) else p[Keys.semesterStartDate] = next.semesterStartDate
            p[Keys.reminderLeadMinutes] = next.reminderLeadMinutes.coerceIn(0, 120)
            p[Keys.notificationsEnabled] = next.notificationsEnabled
            p[Keys.useLocalOcr] = next.useLocalOcr
            p[Keys.mcpEnabled] = next.mcpEnabled
            p[Keys.mcpPort] = next.mcpPort.coerceIn(1024, 65535)
            p[Keys.mcpWriteConfirmation] = next.mcpWriteConfirmation
        }
    }
}
