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
    primary = Color(0xFFA3CDB7), onPrimary = Color(0xFF153B2C),
    primaryContainer = Color(0xFF2A5140), onPrimaryContainer = Color(0xFFDCECE3),
    secondary = Color(0xFFD8BE90), secondaryContainer = Color(0xFF51472F), onSecondaryContainer = Color(0xFFF0E5CF),
    tertiary = Color(0xFFE2AE98), tertiaryContainer = Color(0xFF583E33), onTertiaryContainer = Color(0xFFF5E1D8),
    background = Color(0xFF141D18), onBackground = Color(0xFFE4EBE1),
    surface = Color(0xFF1B251E), onSurface = Color(0xFFE4EBE1),
    surfaceVariant = Color(0xFF354237), onSurfaceVariant = Color(0xFFAFBEB0),
    surfaceContainerLowest = Color(0xFF111A14), surfaceContainerLow = Color(0xFF222F25),
    surfaceContainer = Color(0xFF27362B), surfaceContainerHigh = Color(0xFF2E3D31),
    surfaceContainerHighest = Color(0xFF38493B), outline = Color(0xFF879D8A), outlineVariant = Color(0xFF3E5141),
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
    val night = listOf(0xFF294637 to 0xFFC3E5D2, 0xFF3C3854 to 0xFFD9D1F3, 0xFF4F432F to 0xFFF0DEB4,
        0xFF29434F to 0xFFC3E0EC, 0xFF503A30 to 0xFFF3D0C1)
    val colors = (if (dark) night else light)[Math.floorMod(name.hashCode(), light.size)]
    return Color(colors.first) to Color(colors.second)
}
