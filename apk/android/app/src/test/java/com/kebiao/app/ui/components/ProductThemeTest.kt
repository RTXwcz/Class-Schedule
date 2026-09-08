package com.kebiao.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductThemeTest {
    @Test fun existingAndUnknownPaletteKeepTheGreenTheme() {
        assertSame(ProductLightColors, productColorScheme("green", false))
        assertSame(ProductDarkColors, productColorScheme("green", true))
        assertSame(ProductLightColors, productColorScheme("unknown", false))
        assertSame(ProductPurpleLightColors, productColorScheme("purple", false))
        assertSame(ProductPurpleDarkColors, productColorScheme("purple", true))
    }

    @Test fun purpleBodyAndButtonTextMeetNormalTextContrastInBothModes() {
        listOf(false, true).forEach { dark ->
            val colors = productColorScheme("purple", dark)
            val pairs = listOf(
                "primary button" to (colors.onPrimary to colors.primary),
                "primary container" to (colors.onPrimaryContainer to colors.primaryContainer),
                "secondary button" to (colors.onSecondary to colors.secondary),
                "secondary container" to (colors.onSecondaryContainer to colors.secondaryContainer),
                "tertiary button" to (colors.onTertiary to colors.tertiary),
                "tertiary container" to (colors.onTertiaryContainer to colors.tertiaryContainer),
                "page" to (colors.onBackground to colors.background),
                "surface" to (colors.onSurface to colors.surface),
                "surface low" to (colors.onSurface to colors.surfaceContainerLow),
                "surface highest" to (colors.onSurface to colors.surfaceContainerHighest),
                "secondary text" to (colors.onSurfaceVariant to colors.surface),
                "variant text" to (colors.onSurfaceVariant to colors.surfaceVariant),
            )
            pairs.forEach { (label, pair) -> assertContrast("$label dark=$dark", pair.first, pair.second) }
            val hero = nextCourseColors("purple", dark)
            hero.take(2).forEachIndexed { index, background ->
                assertContrast("next course title dark=$dark endpoint=$index", hero[2], background)
                assertContrast("next course detail dark=$dark endpoint=$index", hero[3], background)
            }
        }
    }

    private fun assertContrast(label: String, foreground: Color, background: Color) {
        val bright = maxOf(foreground.luminance(), background.luminance())
        val dim = minOf(foreground.luminance(), background.luminance())
        val ratio = (bright + .05f) / (dim + .05f)
        assertTrue("$label contrast was $ratio: expected at least 4.5:1", ratio >= 4.5f)
    }
}
