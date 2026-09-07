package com.kebiao.app.notifications

import android.content.Context
import java.time.LocalDate

internal object ReminderDeliveryStore {
    fun deliveredKeys(context: Context, date: LocalDate): Set<String> {
        val prefs = context.getSharedPreferences("delivered_reminders", Context.MODE_PRIVATE)
        val legacy = if (prefs.getString("date", null) == date.toString()) {
            prefs.getStringSet("keys", emptySet()).orEmpty().toSet()
        } else emptySet()
        return legacy + prefs.getStringSet("delivered:$date", emptySet()).orEmpty()
    }

    fun record(context: Context, date: LocalDate, key: String) = synchronized(this) {
        val delivered = deliveredKeys(context, date)
        val prefs = context.getSharedPreferences("delivered_reminders", Context.MODE_PRIVATE)
        val edit = prefs.edit().putStringSet("delivered:$date", delivered + key)
        prefs.all.keys.filter { it.startsWith("delivered:") }.forEach { stored ->
            val day = runCatching { LocalDate.parse(stored.removePrefix("delivered:")) }.getOrNull()
            if (day == null || day.isBefore(LocalDate.now().minusDays(2))) edit.remove(stored)
        }
        edit.apply()
    }
}
