package com.kebiao.app.widget

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.kebiao.app.MainActivity

/** Immutable, explicit widget actions; destination URIs keep entry and timetable PendingIntents distinct. */
object WidgetNavigation {
    const val ENTRY = "entry"
    const val TIMETABLE = "timetable"
    private const val ACTION_OPEN = "com.kebiao.app.action.OPEN_FROM_WIDGET"

    fun intent(context: Context, destination: String): Intent {
        require(destination == ENTRY || destination == TIMETABLE)
        return Intent(context, MainActivity::class.java).setAction(ACTION_OPEN)
            .setData(Uri.Builder().scheme("kebiao").authority("widget").appendPath(destination).build())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun destination(intent: Intent): String? = intent.data?.takeIf {
        intent.action == ACTION_OPEN && it.scheme == "kebiao" && it.authority == "widget" && it.query == null && it.fragment == null
    }?.path?.removePrefix("/")?.takeIf { it == ENTRY || it == TIMETABLE }

    fun activityOptions(): Bundle = ActivityOptions.makeBasic().apply {
        // Android 15+ requires creator opt-in. This permission belongs only to an explicit
        // MainActivity PendingIntent handed to the widget host, never a service trampoline.
        if (Build.VERSION.SDK_INT >= 36) {
            setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        } else if (Build.VERSION.SDK_INT >= 35) {
            @Suppress("DEPRECATION")
            setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
    }.toBundle()
}

data class WidgetLaunchRequest(val destination: String, val id: String = java.util.UUID.randomUUID().toString())
