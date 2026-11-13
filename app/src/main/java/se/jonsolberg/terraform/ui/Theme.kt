package se.jonsolberg.terraform.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SyncGreen = Color(0xFF4CC26A)
val SyncAmber = Color(0xFFE8B23A)

private val scheme = darkColorScheme(
    primary = Color(0xFF9CC2E2),
    onPrimary = Color(0xFF10203A),
    primaryContainer = Color(0xFF233A63),
    onPrimaryContainer = Color(0xFFDCE9F7),
    secondary = Color(0xFF8FB2FF),
    secondaryContainer = Color(0xFF2B3A5C),
    onSecondaryContainer = Color(0xFFDCE7FF),
    background = Color(0xFF10131A),
    onBackground = Color(0xFFE3E6EC),
    surface = Color(0xFF151922),
    onSurface = Color(0xFFE3E6EC),
    surfaceVariant = Color(0xFF212736),
    onSurfaceVariant = Color(0xFFB2B9C8),
    surfaceContainer = Color(0xFF1A1F2A),
    surfaceContainerHigh = Color(0xFF212633),
    surfaceContainerHighest = Color(0xFF282E3C),
    outline = Color(0xFF515A6E),
    error = Color(0xFFFF6B5E),
)

@Composable
fun TerraformTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
