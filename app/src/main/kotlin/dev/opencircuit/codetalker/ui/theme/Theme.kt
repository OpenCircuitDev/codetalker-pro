package dev.opencircuit.codetalker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * CCT-31 — explicit dark color scheme for the Compose tree.
 *
 * Without this, MaterialTheme falls back to dynamic color (Android 12+)
 * which derives `onBackground` from the system wallpaper. On Beam Pro's
 * stock theme, that produces nearly-black text on our forge-dark
 * surface — text disappears.
 *
 * Palette ports the React Phase 27 surface tokens 1:1 so the AR
 * composition feels coherent with the desktop dashboard.
 */

private val ForgeDarkColors = darkColorScheme(
    // Phase 27 surface tokens
    background = Color(0xFF0A0B10),
    surface = Color(0xFF11141C),
    surfaceVariant = Color(0xFF171B26),

    // Text
    onBackground = Color(0xFFE6E8EE),
    onSurface = Color(0xFFE6E8EE),
    onSurfaceVariant = Color(0xFFAAB1C0),

    // Accents
    primary = Color(0xFF22D3EE),       // accent-brand cyan
    onPrimary = Color(0xFF0A0B10),
    primaryContainer = Color(0xFF0E7490),
    onPrimaryContainer = Color(0xFFCFFAFE),

    secondary = Color(0xFFA78BFA),     // accent-activity violet
    onSecondary = Color(0xFF0A0B10),
    secondaryContainer = Color(0xFF581C87),
    onSecondaryContainer = Color(0xFFFAE8FF),

    tertiary = Color(0xFFFBBF24),      // accent-warn amber
    onTertiary = Color(0xFF0A0B10),

    error = Color(0xFFFB7185),
    onError = Color(0xFF0A0B10),

    // Outlines
    outline = Color(0xFF2A2A3A),
    outlineVariant = Color(0xFF1E1E2E),
)

@Composable
fun CodetalkerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ForgeDarkColors,
        content = content,
    )
}
