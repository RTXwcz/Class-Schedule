package com.kebiao.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.kebiao.app.notifications.LessonPeriod
import com.kebiao.app.notifications.PeriodSchedule

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val theme: String = "system",
    val semesterStartDate: String? = null,
    val reminderLeadMinutes: Int = 10,
    val notificationsEnabled: Boolean = true,
    val useLocalOcr: Boolean = false,
    val localOcrModel: String = "pp-ocrv6-tiny",
    val ocrChoiceMade: Boolean = false,
    val mcpEnabled: Boolean = false,
    val mcpPort: Int = 8765,
    val mcpWriteConfirmation: Boolean = true,
    val openAiEndpoint: String = "https://api.openai.com/v1/chat/completions",
    val openAiModel: String = "gpt-4o-mini",
    val parityEnabled: Boolean = false,
    val periods: List<LessonPeriod> = PeriodSchedule.defaults,
)

class AppSettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val semesterStartDate = stringPreferencesKey("semester_start_date")
        val reminderLeadMinutes = intPreferencesKey("reminder_lead_minutes")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val useLocalOcr = booleanPreferencesKey("use_local_ocr")
        val localOcrModel = stringPreferencesKey("local_ocr_model")
        val ocrChoiceMade = booleanPreferencesKey("ocr_choice_made")
        val mcpEnabled = booleanPreferencesKey("mcp_enabled")
        val mcpPort = intPreferencesKey("mcp_port")
        val mcpWriteConfirmation = booleanPreferencesKey("mcp_write_confirmation")
        val openAiEndpoint = stringPreferencesKey("openai_endpoint")
        val openAiModel = stringPreferencesKey("openai_model")
        val parityEnabled = booleanPreferencesKey("parity_enabled")
        val periods = stringPreferencesKey("lesson_periods")
    }

    val settings: Flow<AppSettings> = context.appSettingsDataStore.data.map { p ->
        AppSettings(
            theme = p[Keys.theme] ?: "system",
            semesterStartDate = p[Keys.semesterStartDate],
            reminderLeadMinutes = (p[Keys.reminderLeadMinutes] ?: 10).coerceIn(0, 120),
            notificationsEnabled = p[Keys.notificationsEnabled] ?: true,
            useLocalOcr = p[Keys.useLocalOcr] ?: false,
            localOcrModel = p[Keys.localOcrModel] ?: "pp-ocrv6-tiny",
            ocrChoiceMade = p[Keys.ocrChoiceMade] ?: false,
            mcpEnabled = p[Keys.mcpEnabled] ?: false,
            mcpPort = (p[Keys.mcpPort] ?: 8765).coerceIn(1024, 65535),
            mcpWriteConfirmation = p[Keys.mcpWriteConfirmation] ?: true,
            openAiEndpoint = p[Keys.openAiEndpoint] ?: "https://api.openai.com/v1/chat/completions",
            openAiModel = p[Keys.openAiModel] ?: "gpt-4o-mini",
            parityEnabled = p[Keys.parityEnabled] ?: p.asMap().isNotEmpty(),
            periods = p[Keys.periods]?.let(PeriodSchedule::decode) ?: PeriodSchedule.defaults,
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
                localOcrModel = p[Keys.localOcrModel] ?: "pp-ocrv6-tiny",
                ocrChoiceMade = p[Keys.ocrChoiceMade] ?: false,
                mcpEnabled = p[Keys.mcpEnabled] ?: false,
                mcpPort = p[Keys.mcpPort] ?: 8765,
                mcpWriteConfirmation = p[Keys.mcpWriteConfirmation] ?: true,
                openAiEndpoint = p[Keys.openAiEndpoint] ?: "https://api.openai.com/v1/chat/completions",
                openAiModel = p[Keys.openAiModel] ?: "gpt-4o-mini",
                parityEnabled = p[Keys.parityEnabled] ?: p.asMap().isNotEmpty(),
                periods = p[Keys.periods]?.let(PeriodSchedule::decode) ?: PeriodSchedule.defaults,
            )
            val next = transform(current)
            p[Keys.periods] = PeriodSchedule.encode(next.periods)
            p[Keys.parityEnabled] = next.parityEnabled
            p[Keys.theme] = next.theme
            if (next.semesterStartDate == null) p.remove(Keys.semesterStartDate) else p[Keys.semesterStartDate] = next.semesterStartDate
            p[Keys.reminderLeadMinutes] = next.reminderLeadMinutes.coerceIn(0, 120)
            p[Keys.notificationsEnabled] = next.notificationsEnabled
            p[Keys.useLocalOcr] = next.useLocalOcr
            p[Keys.localOcrModel] = next.localOcrModel
            p[Keys.ocrChoiceMade] = next.ocrChoiceMade
            p[Keys.mcpEnabled] = next.mcpEnabled
            p[Keys.mcpPort] = next.mcpPort.coerceIn(1024, 65535)
            p[Keys.mcpWriteConfirmation] = next.mcpWriteConfirmation
            p[Keys.openAiEndpoint] = next.openAiEndpoint
            p[Keys.openAiModel] = next.openAiModel
        }
    }
}
