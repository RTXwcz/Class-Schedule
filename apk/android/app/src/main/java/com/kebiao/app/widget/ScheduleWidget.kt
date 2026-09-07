package com.kebiao.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.Alignment
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.color.ColorProvider
import com.kebiao.app.MainActivity
import com.kebiao.app.notifications.ReminderCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ScheduleWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val courses = WidgetSnapshotProvider.load(context)
        updateAppWidgetState(context, id) { WidgetSnapshotProvider.write(it, courses) }
        provideContent {
            val state = currentState<Preferences>()
            val count = (state[WidgetKeys.count] ?: 0).coerceIn(0, 3)
            val compact = LocalSize.current.height < 180.dp
            val foreground = ColorProvider(Color(0xFF202124), Color(0xFFF1F3F4))
            val secondary = ColorProvider(Color(0xFF596269), Color(0xFFBBC2C7))
            val background = ColorProvider(Color(0xFFF7F8FA), Color(0xFF202124))
            Column(modifier = GlanceModifier.fillMaxSize().background(background)
                .clickable(actionStartActivity<MainActivity>()).padding(if (compact) 8.dp else 12.dp)) {
                if (!compact || count == 0) {
                    Text("最近三节课", style = TextStyle(color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.height(6.dp))
                }
                if (count == 0) {
                    Column(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text("暂无安排", style = TextStyle(color = secondary, fontSize = 13.sp), maxLines = 1)
                    }
                } else repeat(count) { index ->
                    Column(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                            Text(state[WidgetKeys.time(index)].orEmpty(), modifier = GlanceModifier.width(if (compact) 70.dp else 82.dp),
                                style = TextStyle(color = secondary, fontSize = if (compact) 10.sp else 12.sp), maxLines = 1)
                            Text(state[WidgetKeys.name(index)].orEmpty(), modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(color = foreground, fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        }
                        Text(state[WidgetKeys.location(index)].orEmpty(), modifier = GlanceModifier.fillMaxWidth(),
                            style = TextStyle(color = secondary, fontSize = if (compact) 10.sp else 12.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget

    // Replaces Glance's asynchronous update with one job that first reloads Room and then
    // updates every real widget ID. Calling super here would claim goAsync() twice.
    @android.annotation.SuppressLint("MissingSuperCall")
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderCoordinator.refresh(context.applicationContext)
            } catch (error: Exception) {
                Log.e("ScheduleWidget", "Could not refresh widget", error)
            } finally {
                pending?.finish()
            }
        }
    }
}
