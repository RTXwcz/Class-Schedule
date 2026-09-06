package com.kebiao.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.currentState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kebiao.app.domain.model.EffectiveCourse

object ScheduleWidget : GlanceAppWidget() {
    override val stateDefinition: GlanceStateDefinition<Preferences> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<Preferences>()
            val lines = state[WidgetKeys.lines].orEmpty().split("\n").filter(String::isNotBlank)
            Column(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
                Text("最近三节课")
                if (lines.isEmpty()) Text("暂无安排") else lines.take(3).forEach { Text(it) }
            }
        }
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget
}

object WidgetSnapshotProvider {
    suspend fun update(context: Context, courses: List<EffectiveCourse>) {
        val lines = courses.take(3).joinToString("\n") { course ->
            val location = listOfNotNull(course.course.building, course.course.room).joinToString(" ")
            "${course.course.name} · 第${course.course.startPeriod}-${course.course.endPeriod}节${location.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}"
        }
        val manager = GlanceAppWidgetManager(context)
        manager.getGlanceIds(ScheduleWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs -> prefs[WidgetKeys.lines] = lines }
            ScheduleWidget.update(context, id)
        }
    }
}

private object WidgetKeys {
    val lines = stringPreferencesKey("schedule_lines")
}
