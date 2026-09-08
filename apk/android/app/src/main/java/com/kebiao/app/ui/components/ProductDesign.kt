package com.kebiao.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ProductLightColors = lightColorScheme(
    primary = Color(0xFF235649), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCECE3), onPrimaryContainer = Color(0xFF193E32),
    secondary = Color(0xFF7B6139), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E5CF), onSecondaryContainer = Color(0xFF55401E),
    tertiary = Color(0xFFA5644E), tertiaryContainer = Color(0xFFF5E1D8), onTertiaryContainer = Color(0xFF643D30),
    background = Color(0xFFF5F6F2), onBackground = Color(0xFF20352E),
    surface = Color(0xFFFCFDF9), onSurface = Color(0xFF20352E),
    surfaceVariant = Color(0xFFE6EBE4), onSurfaceVariant = Color(0xFF68766D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F3EC),
    surfaceContainer = Color(0xFFECF0E8), surfaceContainerHigh = Color(0xFFE5EBE1),
    surfaceContainerHighest = Color(0xFFDDE5D8), outline = Color(0xFF87988B), outlineVariant = Color(0xFFDCE3D9),
)
val ProductDarkColors = darkColorScheme(
    primary = Color(0xFF9EDBC5), onPrimary = Color(0xFF123D30),
    primaryContainer = Color(0xFF283E36), onPrimaryContainer = Color(0xFFCEF0E2),
    secondary = Color(0xFFD7C5A4), onSecondary = Color(0xFF392F1E),
    secondaryContainer = Color(0xFF3A3530), onSecondaryContainer = Color(0xFFF0E1C7),
    tertiary = Color(0xFFD4B9D9), onTertiary = Color(0xFF39263F),
    tertiaryContainer = Color(0xFF37313E), onTertiaryContainer = Color(0xFFEADAF0),
    background = Color(0xFF111315), onBackground = Color(0xFFE8ECEF),
    surface = Color(0xFF1C1F22), onSurface = Color(0xFFE8ECEF),
    surfaceVariant = Color(0xFF303539), onSurfaceVariant = Color(0xFFAAB3BA),
    surfaceContainerLowest = Color(0xFF0D0F11), surfaceContainerLow = Color(0xFF22262A),
    surfaceContainer = Color(0xFF272C30), surfaceContainerHigh = Color(0xFF2D3338),
    surfaceContainerHighest = Color(0xFF353C42), outline = Color(0xFF7C8991), outlineVariant = Color(0xFF343C42),
    surfaceTint = Color(0xFF9EDBC5), inverseSurface = Color(0xFFE8ECEF), inverseOnSurface = Color(0xFF20262A), inversePrimary = Color(0xFF235649),
)

@Composable
fun ProductHeader(title: String, subtitle: String, eyebrow: String? = null, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            eyebrow?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp) }
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

@Composable
fun SettingsGroup(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

@Composable
fun EntryAction(icon: ImageVector, title: String, description: String, onClick: () -> Unit, tone: Color = MaterialTheme.colorScheme.primaryContainer, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).background(tone, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun courseColors(name: String): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.red < .3f
    val light = listOf(0xFFDCECE3 to 0xFF254E3C, 0xFFE6E5F0 to 0xFF514B73, 0xFFF2E6D3 to 0xFF755831,
        0xFFDDEBF0 to 0xFF345868, 0xFFF3E1D9 to 0xFF7A4D3D)
    val night = listOf(0xFF273D35 to 0xFFC4E5D5, 0xFF343347 to 0xFFD8D3F2, 0xFF40382D to 0xFFEDDCBD,
        0xFF283A46 to 0xFFC3DEEF, 0xFF433330 to 0xFFF0D0C7)
    val colors = (if (dark) night else light)[Math.floorMod(name.hashCode(), light.size)]
    return Color(colors.first) to Color(colors.second)
}

/** Keep the next-course panel quiet at night while retaining its hierarchy. */
@Composable
fun nextCoursePalette(): List<Color> = if (MaterialTheme.colorScheme.background.red < .3f)
    listOf(Color(0xFF253830), Color(0xFF202C29), Color(0xFFC7F0DD), Color(0xFFB2C7BD))
else listOf(Color(0xFF214F40), Color(0xFF356E57), Color.White, Color(0xFFD6E5DA))
