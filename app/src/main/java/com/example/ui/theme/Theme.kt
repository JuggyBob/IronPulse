package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Turns one semantic palette into a complete Material scheme.
 *
 * The app still draws most of its own surfaces, but every Material component that is not given
 * explicit colours (dialogs, menus, sheets, text fields, switches, sliders, snackbars) inherits
 * from this scheme. Building it from the same tokens is what keeps those components from falling
 * back to a stock dark palette in the light and AMOLED themes.
 */
private fun AppPalette.toColorScheme(): ColorScheme {
    val containerMix = if (isLight) 0.86f else 0.78f
    val primaryContainer = lerp(accentPrimary, surface, containerMix)
    val secondaryContainer = lerp(accentSecondary, surface, containerMix)
    val tertiaryContainer = lerp(accentTertiary, surface, containerMix)
    val errorContainer = lerp(danger, surface, containerMix)

    val inversePrimary = if (isLight) {
        lerp(accentPrimary, Color.White, 0.45f)
    } else {
        lerp(accentPrimary, background, 0.45f)
    }

    return darkColorScheme(
        primary = accentPrimary,
        onPrimary = onAccent,
        primaryContainer = primaryContainer,
        onPrimaryContainer = textPrimary,
        inversePrimary = inversePrimary,
        secondary = accentSecondary,
        onSecondary = onAccent,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = textPrimary,
        tertiary = accentTertiary,
        onTertiary = onAccent,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = textPrimary,
        background = background,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = textSecondary,
        surfaceTint = accentPrimary,
        inverseSurface = textPrimary,
        inverseOnSurface = background,
        error = danger,
        onError = onDanger,
        errorContainer = errorContainer,
        onErrorContainer = textPrimary,
        outline = border,
        outlineVariant = borderSubtle,
        scrim = scrim,
        surfaceBright = if (isLight) surface else surfaceVariant,
        surfaceDim = if (isLight) surfaceVariant else background,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background
    )
}

/**
 * Applies the selected theme to the whole application.
 *
 * The legacy colour names are dynamic getters backed by [ThemeRuntime], and the palette is written
 * before the content composes. That makes every existing `DarkBg`/`TextWhite` call site switch in
 * the same frame as the theme selection, without rewriting two thousand references by hand. The
 * [LocalAppPalette] provider is the explicit path for new code.
 */
@Composable
fun MyApplicationTheme(
    themeMode: ThemeMode = ThemeMode.IRONPULSE,
    accentColorHex: String? = null,
    content: @Composable () -> Unit
) {
    val accentColor = remember(accentColorHex) { AccentColors.parseHex(accentColorHex) }
    val palette = AppPalettes.forMode(themeMode, accentColor)

    // Deliberately before the content: the legacy getters below read this value while the tree
    // composes, so the new palette is already in effect on the first frame after a switch.
    ThemeRuntime.palette = palette

    val colorScheme = remember(themeMode, accentColor) { palette.toColorScheme() }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = palette.isLight
                isAppearanceLightNavigationBars = palette.isLight
            }
        }
    }

    CompositionLocalProvider(LocalAppPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
