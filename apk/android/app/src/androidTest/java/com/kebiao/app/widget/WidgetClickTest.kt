package com.kebiao.app.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kebiao.app.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class WidgetClickTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Before fun launchActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // A widget starts a singleTask Activity through a real PendingIntent. Own the
        // launch here: ActivityScenario cannot track an instance replaced by task flags.
        context.startActivity(WidgetNavigation.intent(context, WidgetNavigation.ENTRY))
        compose.waitUntil(5_000) { compose.onAllNodesWithText("添加你的安排").fetchSemanticsNodes().isNotEmpty() }
        // Begin away from manual entry so the first widget action must navigate too.
        compose.onNodeWithText("数据备份").performClick()
        compose.onNodeWithText("导出 JSON 文件").assertIsDisplayed()
    }

    @After fun closeActivities() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.values().filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }.distinct()
                .filterIsInstance<MainActivity>().forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }

    @OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
    @Test fun emptyWidgetTextHasDirectClickAndReopensEntryOnRepeatedTaps() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = GlanceRemoteViews().compose(context, DpSize(250.dp, 220.dp), state = mutablePreferencesOf()) { ScheduleWidgetContent() }
        lateinit var clickTarget: View
        compose.runOnIdle {
            val root = FrameLayout(context)
            root.addView(result.remoteViews.apply(context, root))
            fun texts(view: View): List<TextView> = when (view) {
                is TextView -> listOf(view)
                is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                else -> emptyList()
            }
            val label = texts(root).single { it.text.toString().contains("轻点") }
            clickTarget = generateSequence(label as View) { it.parent as? View }.first { it.hasOnClickListeners() }
            // Glance places a ripple wrapper around clickable Text; the action belongs to
            // this single label rather than only the outer container containing all three.
            assertTrue("Invitation needs its own click wrapper", texts(clickTarget).size == 1)
            assertTrue(clickTarget.performClick())
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("添加日程", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("手动填写").assertIsDisplayed()
        compose.onNodeWithText("添加日程", substring = false).assertIsDisplayed()
        compose.onNodeWithText("数据备份").performClick()
        compose.runOnIdle { assertTrue(clickTarget.performClick()) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("添加日程", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("添加日程", substring = false).performClick()
        compose.onNodeWithText("日程名称").assertIsDisplayed()
        compose.onNodeWithText("取消", substring = false).performClick()
    }
}
