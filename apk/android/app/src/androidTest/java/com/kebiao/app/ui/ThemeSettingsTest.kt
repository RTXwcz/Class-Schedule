package com.kebiao.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kebiao.app.data.settings.AppSettings
import com.kebiao.app.data.settings.AppSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSettingsTest {
    @Test fun palettePersistsWithoutChangingDisplayModeOrOtherPreferences() = runBlocking<Unit> {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = AppSettingsStore(context)
        val original = store.settings.first()
        try {
            assertEquals("green", AppSettings().colorPalette)
            store.update { it.copy(colorPalette = "purple", theme = "dark") }
            assertEquals(original.copy(colorPalette = "purple", theme = "dark"), AppSettingsStore(context).settings.first())
            store.update { it.copy(theme = "light") }
            assertEquals(original.copy(colorPalette = "purple", theme = "light"), AppSettingsStore(context).settings.first())
            store.update { it.copy(colorPalette = "green") }
            assertEquals(original.copy(colorPalette = "green", theme = "light"), AppSettingsStore(context).settings.first())
            store.update { it.copy(colorPalette = "unsupported", theme = "system") }
            assertEquals(original.copy(colorPalette = "green", theme = "system"), AppSettingsStore(context).settings.first())
        } finally {
            store.update { original }
        }
    }
}

class ThemePaletteSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun paletteAndDisplayModeCanBeChangedIndependentlyFromSettings() {
        val vm = AppViewModel()
        compose.setContent { ScheduleApp(vm) }
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithText("松林绿").assertIsSelected()
        compose.onNodeWithText("跟随系统").assertIsSelected()
        compose.onNodeWithText("鸢尾紫").performClick().assertIsSelected()
        compose.onNodeWithText("深色", substring = false).performClick().assertIsSelected()
        compose.onNodeWithText("鸢尾紫").assertIsSelected()
        compose.runOnIdle {
            assertEquals("purple", vm.uiState.value.settings.colorPalette)
            assertEquals("dark", vm.uiState.value.settings.theme)
        }
        compose.onNodeWithText("课表", substring = false).performClick()
        compose.onNodeWithText("设置", substring = false).performClick()
        compose.onNodeWithText("鸢尾紫").assertIsSelected()
        compose.onNodeWithText("浅色", substring = false).performClick().assertIsSelected()
        compose.onNodeWithText("松林绿").performClick().assertIsSelected()
        compose.onNodeWithText("浅色", substring = false).assertIsSelected()
        compose.runOnIdle {
            assertEquals("green", vm.uiState.value.settings.colorPalette)
            assertEquals("light", vm.uiState.value.settings.theme)
        }
    }
}
