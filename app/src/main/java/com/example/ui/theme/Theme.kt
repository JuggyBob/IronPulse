package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IronPulseColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF0A192F),
    primaryContainer = Color(0xFF1F3A5A),
    onPrimaryContainer = Color(0xFFCBE5FE),

    secondary = MintGreen,
    onSecondary = Color(0xFF04270F),
    secondaryContainer = Color(0xFF133E1D),
    onSecondaryContainer = Color(0xFFA6F3B8),

    tertiary = CoralOrange,
    onTertiary = Color(0xFF3B1207),
    tertiaryContainer = Color(0xFF4F2013),
    onTertiaryContainer = Color(0xFFFFD7D0),

    background = DarkBg,
    onBackground = TextWhite,

    surface = DarkSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextMuted,

    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkBorder,

    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,

    error = FireRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // App is dark-themed by design as requested
    dynamicColor: Boolean = false, // Keep high-contrast gym dark identity
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IronPulseColorScheme,
        typography = Typography,
        content = content
    )
}
