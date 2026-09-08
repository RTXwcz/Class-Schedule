package com.kebiao.app.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.kebiao.app.domain.model.Course
import com.kebiao.app.domain.model.EffectiveCourse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Render the real Glance content at its exact supported sizes, independent of launcher grid. */
class WidgetRenderTest {
    @OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
    @Test fun threeCoursesAndEmptyStateFitAllWidgetSizesInBothThemes() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val names = listOf("高等数学", "大学英语", "程序设计实验")
        for (dark in listOf(false, true)) for ((width, height) in listOf(180 to 110, 250 to 220, 300 to 270)) for (empty in listOf(false, true)) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = context.createConfigurationContext(config)
            val prefs = mutablePreferencesOf()
            WidgetSnapshotProvider.write(prefs, if (empty) emptyList() else names.mapIndexed { index, name ->
                EffectiveCourse(Course("widget-$index", name, 2, index + 1, index + 1, building = "教学楼", room = "A30${index+1}"),
                    LocalDate.of(2026, 9, 8 + index), 2, null)
            })
            val result = GlanceRemoteViews().compose(themed, DpSize(width.dp, height.dp), state = prefs) { ScheduleWidgetContent() }
            instrumentation.runOnMainSync {
                val pixels = themed.resources.displayMetrics.density
                val root = FrameLayout(themed)
                val view = result.remoteViews.apply(themed, root)
                root.addView(view)
                val w = (width * pixels).toInt()
                val h = (height * pixels).toInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, w, h)
                fun texts(v: View): List<TextView> = when (v) {
                    is TextView -> listOf(v)
                    is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
                    else -> emptyList()
                }
                val rendered = texts(root)
                for (expected in if (empty) listOf("暂无课程安排") else names) {
                    val text = rendered.single { it.text.toString() == expected }
                    val location = IntArray(2)
                    text.getLocationOnScreen(location)
                    assertTrue("$width x $height: $expected must fit vertically", location[1] >= 0 && location[1] + text.height <= h)
                    assertTrue("$width x $height: $expected must be visible", text.height > 0 && text.width > 0)
                }
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                assertEquals("Rounded corner stays transparent", 0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                val path = File(context.getExternalFilesDir(null), "widget-${width}x$height-${if (dark) "dark" else "light"}${if (empty) "-empty" else ""}.png")
                path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
