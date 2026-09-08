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
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.Box
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
import androidx.glance.semantics.semantics
import androidx.glance.semantics.contentDescription
import androidx.compose.runtime.Composable
import com.kebiao.app.R
import com.kebiao.app.MainActivity
import com.kebiao.app.notifications.ReminderCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ScheduleWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 220.dp), DpSize(300.dp, 270.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = ReminderCoordinator.snapshot(context)
        val courses = WidgetSnapshotProvider.upcoming(java.time.ZonedDateTime.now(), snapshot).map { it.course }
        updateAppWidgetState(context, id) { WidgetSnapshotProvider.write(it, courses, snapshot.settings.periods) }
        provideContent { ScheduleWidgetContent() }
    }
}

@Composable
internal fun ScheduleWidgetContent() {
    val state = currentState<Preferences>()
    val count = (state[WidgetKeys.count] ?: 0).coerceIn(0, 3)
    val compact = LocalSize.current.height < 220.dp
    val spacious = LocalSize.current.height >= 270.dp
    val foreground = ColorProvider(Color(0xFF244C3D), Color(0xFFE8ECEF))
    val secondary = ColorProvider(Color(0xFF68766E), Color(0xFFADB7BD))
    val accent = ColorProvider(Color(0xFF34745B), Color(0xFF9EDBC5))
    Column(modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
        .background(ImageProvider(R.drawable.widget_surface)).cornerRadius(28.dp)
        .clickable(actionStartActivity<MainActivity>()).padding(if (compact) 12.dp else 18.dp)) {
        if (count == 0) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("我的课表", style = TextStyle(color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Spacer(GlanceModifier.height(8.dp))
                Text("暂无课程安排", style = TextStyle(color = foreground, fontSize = if (compact) 17.sp else 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(6.dp))
                Text("轻点添加，按自己的节奏开始", style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 2)
            }
        } else if (compact) {
            repeat(count) { index ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight().semantics { contentDescription = description(state, index) }, verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Column(GlanceModifier.width(46.dp)) {
                        Text(state[WidgetKeys.clock(index)].orEmpty(), style = TextStyle(color = if (index == 0) accent else foreground,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(state[WidgetKeys.date(index)].orEmpty(), style = TextStyle(color = secondary, fontSize = 9.sp), maxLines = 1)
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(state[WidgetKeys.name(index)].orEmpty(), style = TextStyle(color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                        Text(state[WidgetKeys.location(index)].orEmpty(), style = TextStyle(color = secondary, fontSize = 10.sp), maxLines = 1)
                    }
                }
            }
        } else {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("接下来的课程", GlanceModifier.defaultWeight(), style = TextStyle(color = secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text("${state[WidgetKeys.date(0)].orEmpty()}  ${state[WidgetKeys.weekday(0)].orEmpty()}",
                    GlanceModifier.background(ImageProvider(R.drawable.widget_date_chip)).padding(horizontal = 8.dp, vertical = 4.dp),
                    style = TextStyle(color = accent, fontSize = 10.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(if (spacious) 14.dp else 9.dp))
            Column(GlanceModifier.fillMaxWidth().defaultWeight().semantics { contentDescription = description(state, 0) }) {
                Text(state[WidgetKeys.clock(0)].orEmpty(), style = TextStyle(color = accent, fontSize = if (spacious) 34.sp else 28.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(3.dp))
                Text(state[WidgetKeys.name(0)].orEmpty(), style = TextStyle(color = foreground, fontSize = if (spacious) 18.sp else 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(state[WidgetKeys.location(0)].orEmpty(), style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 1)
            }
            if (count > 1) {
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(Color(0xFFE3EAE4), Color(0xFF343C41)))) { }
                Spacer(GlanceModifier.height(5.dp))
                for (index in 1 until count) {
                    Row(GlanceModifier.fillMaxWidth().height(if (spacious) 37.dp else 30.dp).semantics { contentDescription = description(state, index) }, verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Column(GlanceModifier.width(45.dp)) {
                            Text(state[WidgetKeys.clock(index)].orEmpty(), style = TextStyle(color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(state[WidgetKeys.date(index)].orEmpty(), style = TextStyle(color = secondary, fontSize = 9.sp), maxLines = 1)
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(state[WidgetKeys.name(index)].orEmpty(), style = TextStyle(color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(state[WidgetKeys.location(index)].orEmpty(), style = TextStyle(color = secondary, fontSize = 10.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun description(state: Preferences, index: Int): String =
    "${state[WidgetKeys.time(index)].orEmpty()}，${state[WidgetKeys.name(index)].orEmpty()}，${state[WidgetKeys.location(index)].orEmpty()}。轻点打开课表"

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
