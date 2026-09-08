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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.LocalContext
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
import com.kebiao.app.notifications.ReminderCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ScheduleWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    internal val supportedSizes = setOf(
        DpSize(180.dp, 110.dp), DpSize(180.dp, 160.dp), DpSize(220.dp, 180.dp),
        DpSize(250.dp, 220.dp), DpSize(300.dp, 270.dp),
    )
    override val sizeMode = SizeMode.Responsive(supportedSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = ReminderCoordinator.snapshot(context)
        val courses = WidgetSnapshotProvider.upcoming(java.time.ZonedDateTime.now(), snapshot).map { it.course }
        updateAppWidgetState(context, id) { WidgetSnapshotProvider.write(it, courses, snapshot.settings.periods) }
        provideContent { ScheduleWidgetContent() }
    }
}

@Composable
@OptIn(ExperimentalGlanceApi::class)
internal fun ScheduleWidgetContent() {
    val state = currentState<Preferences>()
    val count = (state[WidgetKeys.count] ?: 0).coerceIn(0, 3)
    val dense = LocalSize.current.height < 160.dp
    val compact = LocalSize.current.height < 220.dp
    val spacious = LocalSize.current.height >= 270.dp
    val foreground = ColorProvider(Color(0xFF244C3D), Color(0xFFE8ECEF))
    val secondary = ColorProvider(Color(0xFF68766E), Color(0xFFADB7BD))
    val accent = ColorProvider(Color(0xFF34745B), Color(0xFF9EDBC5))
    val open = actionStartActivity(WidgetNavigation.intent(LocalContext.current,
        if (count == 0) WidgetNavigation.ENTRY else WidgetNavigation.TIMETABLE), activityOptions = WidgetNavigation.activityOptions())
    Column(modifier = GlanceModifier.fillMaxSize().appWidgetBackground()
        .background(ImageProvider(R.drawable.widget_surface)).cornerRadius(28.dp)
        // At the minimum height, preserve safe horizontal space inside the 28dp corners.
        // Keep vertical space for CJK font padding instead of clipping location/date baselines.
        .clickable(open).padding(horizontal = if (dense) 16.dp else if (compact) 14.dp else 18.dp,
            vertical = if (dense) 2.dp else if (compact) 6.dp else if (spacious) 18.dp else 14.dp)) {
        if (count == 0) {
            Column(GlanceModifier.fillMaxSize().clickable(open), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("我的课表", modifier = GlanceModifier.clickable(open), style = TextStyle(color = accent, fontSize = (if (dense) 9 else if (compact) 11 else 12).sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Spacer(GlanceModifier.height(8.dp))
                Text("暂无课程安排", modifier = GlanceModifier.clickable(open), style = TextStyle(color = foreground, fontSize = if (compact) 17.sp else 22.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(6.dp))
                Text("轻点录入课程、考试或日程", modifier = GlanceModifier.clickable(open), style = TextStyle(color = secondary, fontSize = 11.sp), maxLines = 2)
            }
        } else {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Text("接下来的课程", GlanceModifier.defaultWeight(), style = TextStyle(color = secondary, fontSize = (if (dense) 7 else 11).sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Text("${state[WidgetKeys.date(0)].orEmpty()}  ${state[WidgetKeys.weekday(0)].orEmpty()}",
                    GlanceModifier.background(ImageProvider(R.drawable.widget_date_chip)).padding(horizontal = if (dense) 4.dp else 8.dp, vertical = if (dense) 0.dp else if (compact) 1.dp else if (spacious) 4.dp else 3.dp),
                    style = TextStyle(color = accent, fontSize = (if (dense) 7 else if (compact) 9 else 10).sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(if (dense) 1.dp else if (compact) 3.dp else if (spacious) 14.dp else 7.dp))
            Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(open).semantics { contentDescription = description(state, 0) }) {
                Text(state[WidgetKeys.clock(0)].orEmpty(), style = TextStyle(color = accent, fontSize = (if (dense) 16 else if (compact) 22 else if (spacious) 34 else 28).sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.height(if (dense) 0.dp else if (compact) 2.dp else 3.dp))
                Text(state[WidgetKeys.name(0)].orEmpty(), style = TextStyle(color = foreground, fontSize = (if (dense) 9 else if (compact) 13 else if (spacious) 18 else 16).sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(state[WidgetKeys.location(0)].orEmpty(), style = TextStyle(color = secondary, fontSize = (if (dense) 7 else if (compact) 9 else 11).sp), maxLines = 1)
            }
            if (count > 1) {
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(Color(0xFFE3EAE4), Color(0xFF343C41)))) { }
                Spacer(GlanceModifier.height(if (dense) 1.dp else if (compact) 2.dp else 5.dp))
                for (index in 1 until count) {
                    Row(GlanceModifier.fillMaxWidth().height(if (dense) 23.dp else if (compact) 28.dp else if (spacious) 37.dp else 34.dp).clickable(open).semantics { contentDescription = description(state, index) }, verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Column(GlanceModifier.width(if (dense) 36.dp else 45.dp)) {
                            Text(state[WidgetKeys.clock(index)].orEmpty(), style = TextStyle(color = foreground, fontSize = (if (dense) 8 else if (compact) 10 else 12).sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(state[WidgetKeys.date(index)].orEmpty(), style = TextStyle(color = secondary, fontSize = (if (dense) 7 else if (compact) 8 else 9).sp), maxLines = 1)
                        }
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(state[WidgetKeys.name(index)].orEmpty(), style = TextStyle(color = foreground, fontSize = (if (dense) 8 else if (compact) 10 else 12).sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(state[WidgetKeys.location(index)].orEmpty(), style = TextStyle(color = secondary, fontSize = (if (dense) 7 else if (compact) 8 else 10).sp), maxLines = 1)
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
