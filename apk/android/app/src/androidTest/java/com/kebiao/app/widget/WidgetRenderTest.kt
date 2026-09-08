package com.kebiao.app.widget

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
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

/** Render actual RemoteViews: Compose semantics alone cannot detect launcher text clipping. */
class WidgetRenderTest {
    @OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
    @Test fun threeCoursesAndEmptyStateFitAllWidgetSizesInBothThemes() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val samples = mapOf(
            "" to listOf("高等数学", "大学英语", "程序设计实验"),
            "-long" to listOf("C语言程序设计基础及实验", "习近平新时代中国特色社会主义思想概论", "大学生心理健康教育与实践"),
            "-empty" to emptyList(),
        )
        // Iterate production sizes so a newly supported size cannot silently escape this check.
        for (palette in listOf("green", "purple")) for (dark in listOf(false, true)) for (size in ScheduleWidget.supportedSizes) for ((sample, names) in samples) {
            val width = size.width.value.toInt()
            val height = size.height.value.toInt()
            val label = "$palette/${width}x$height/${if (dark) "dark" else "light"}$sample"
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                fontScale = 1f
            }
            val themed = context.createConfigurationContext(config)
            val prefs = mutablePreferencesOf()
            WidgetSnapshotProvider.write(prefs, names.mapIndexed { index, name ->
                EffectiveCourse(Course("widget-$index", name, 2, index + 1, index + 1,
                    building = if (sample == "-long") "综合实验教学楼" else "教学楼", room = "A30${index+1}"),
                    LocalDate.of(2026, 9, 8 + index), 2, null)
            }, colorPalette = palette)
            val result = GlanceRemoteViews().compose(themed, size, state = prefs) { ScheduleWidgetContent() }
            instrumentation.runOnMainSync {
                val pixels = themed.resources.displayMetrics.density
                val root = FrameLayout(themed)
                val view = result.remoteViews.apply(themed, root)
                root.addView(view)
                val w = (width * pixels).toInt()
                val h = (height * pixels).toInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, w, h)
                // Save even a failing layout so the assertion can be checked against real pixels.
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val path = File(context.getExternalFilesDir(null), "widget-$palette-${width}x$height-${if (dark) "dark" else "light"}$sample.png")
                path.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                // Pre-Android 12 Glance templates contain empty placeholder TextViews.
                // Required content is asserted separately below; only actual labels have glyphs.
                val rendered = descendants(root).filterIsInstance<TextView>().filter { it.text.isNotEmpty() && it.visibility == View.VISIBLE }
                fun text(expected: String): TextView = rendered.singleOrNull { it.text.toString() == expected }
                    ?: error("$label: expected one visible text for '$expected'; found ${rendered.map { it.text }}")
                fun bounds(v: View) = Rect().also {
                    v.getDrawingRect(it)
                    root.offsetDescendantRectToMyCoords(v, it)
                }
                File(context.getExternalFilesDir(null), "widget-${width}x$height-${if (dark) "dark" else "light"}$sample-layout.txt")
                    .writeText(rendered.joinToString("\n") { textView ->
                        val layout = textView.layout
                        "'${textView.text}' bounds=${bounds(textView)} height=${textView.height} " +
                            "lineBottom=${layout?.let { it.getLineBottom(it.lineCount - 1) }} " +
                            "padding=${textView.paddingTop},${textView.paddingBottom} " +
                            "totalPadding=${textView.totalPaddingTop},${textView.totalPaddingBottom} " +
                            "includeFontPadding=${textView.includeFontPadding}"
                    })
                // Check all dates/locations, actual text layout and clipping ancestors. Checking
                // only the name View misses a clipped second line or overflow into another row.
                for (textView in rendered) {
                    val rect = bounds(textView)
                    val diagnostic = "$label: '${textView.text}' bounds=$rect"
                    assertTrue("$diagnostic must have visible area", textView.width > 0 && textView.height > 0)
                    assertTrue("$diagnostic must fit widget", rect.left >= 0 && rect.right <= w && rect.top >= 0 && rect.bottom <= h)
                    val layout = checkNotNull(textView.layout) { "$diagnostic has no text layout" }
                    assertTrue("$diagnostic must not clip its text vertically: layout=${layout.getLineBottom(layout.lineCount - 1)}, " +
                        "available=${textView.height - textView.totalPaddingTop - textView.totalPaddingBottom}",
                        layout.getLineBottom(layout.lineCount - 1) <= textView.height - textView.totalPaddingTop - textView.totalPaddingBottom)
                    var parent = textView.parent
                    while (parent is ViewGroup && parent !== root) {
                        if (parent.clipChildren) {
                            assertTrue("$diagnostic clipped by ${parent.javaClass.simpleName} ${bounds(parent)}",
                                bounds(parent).contains(rect))
                        }
                        parent = parent.parent
                    }
                }
                if (names.isEmpty()) {
                    text("我的课表")
                    text("暂无课程安排")
                    text("轻点录入课程、考试或日程")
                } else {
                    val header = text("接下来的课程")
                    text("9/8  周二")
                    val firstClock = text("08:00")
                    val laterClocks = listOf(text("09:00"), text("10:10"))
                    val courseNames = names.map(::text)
                    val locations = names.indices.map { text(prefs[WidgetKeys.location(it)].orEmpty()) }
                    text("9/9")
                    text("9/10")
                    assertTrue("$label retains the large primary time", laterClocks.all { firstClock.textSize >= it.textSize * 1.5f })
                    assertTrue("$label retains primary course hierarchy", courseNames.drop(1).all { courseNames[0].textSize > it.textSize })
                    assertTrue("$label header precedes primary time", bounds(header).bottom <= bounds(firstClock).top)
                    assertTrue("$label primary name follows time", bounds(firstClock).bottom <= bounds(courseNames[0]).top)
                    assertTrue("$label primary location follows name", bounds(courseNames[0]).bottom <= bounds(locations[0]).top)
                    assertTrue("$label later courses follow primary course", bounds(locations[0]).bottom <= bounds(laterClocks[0]).top)
                    assertTrue("$label third course follows second location", bounds(locations[1]).bottom <= bounds(courseNames[2]).top)
                    // Long titles may ellipsize horizontally; preserve the complete accessible title.
                    courseNames.forEach { assertEquals("$label: title stays on one line", 1, it.layout.lineCount) }
                    val descriptions = descendants(root).mapNotNull { it.contentDescription?.toString() }
                    names.forEach { name -> assertTrue("$label exposes complete title to accessibility: $name", descriptions.any { name in it }) }
                }
                assertEquals("$label: rounded corner stays transparent", 0, android.graphics.Color.alpha(bitmap.getPixel(0, 0)))
                if (names.isNotEmpty()) {
                    val separatorColor = if (palette == "purple") { if (dark) 0xFF403746.toInt() else 0xFFE8E0F0.toInt() } else if (dark) 0xFF343C41.toInt() else 0xFFE3EAE4.toInt()
                    val firstLocation = text(prefs[WidgetKeys.location(0)].orEmpty())
                    val between = bounds(firstLocation).bottom.coerceIn(0, h - 1)..bounds(text("09:00")).top.coerceIn(0, h - 1)
                    assertTrue("$label: divider separates featured course and next two rows", between.any { y ->
                        (0 until w).count { x -> bitmap.getPixel(x, y) == separatorColor } >= w / 2
                    })
                }
                bitmap.recycle()
            }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()
}
