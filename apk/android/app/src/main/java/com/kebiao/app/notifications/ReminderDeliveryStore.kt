package com.kebiao.app.notifications

import android.content.Context
import java.time.LocalDate

internal object ReminderDeliveryStore {
    fun deliveredKeys(context: Context, date: LocalDate): Set<String> {
        val prefs = context.getSharedPreferences("delivered_reminders", Context.MODE_PRIVATE)
        return if (prefs.getString("date", null) == date.toString()) {
            prefs.getStringSet("keys", emptySet()).orEmpty().toSet()
        } else emptySet()
    }

    fun record(context: Context, date: LocalDate, key: String) {
        val delivered = deliveredKeys(context, date)
        context.getSharedPreferences("delivered_reminders", Context.MODE_PRIVATE).edit()
            .putString("date", date.toString()).putStringSet("keys", delivered + key).apply()
    }
}
