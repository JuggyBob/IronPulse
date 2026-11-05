/*
 * Licensed under the PolyForm Noncommercial License 1.0.0.
 * See LICENSES/POLYFORM-NONCOMMERCIAL-1.0.0.txt.
 *
 * New standalone code authored after the repository's 3.0.0 licensing boundary.
 */
package com.example.widget

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import com.example.R
import com.example.ui.theme.AppPalettes
import com.example.ui.theme.ThemeMode

data class WidgetPalette(
    val background: Int,
    val surface: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val onAccent: Int,
    val success: Int,
    val danger: Int,
    val border: Int,
    val dayOn: Int,
    val dayOff: Int,
    val flame: Int,
    @DrawableRes val backgroundRes: Int,
    @DrawableRes val buttonRes: Int,
    @DrawableRes val cardRes: Int,
    @DrawableRes val dayOnRes: Int,
    @DrawableRes val dayOffRes: Int
)

/** Maps the four app palettes onto the RemoteViews colour surface. */
object WidgetTheme {

    fun palette(mode: ThemeMode): WidgetPalette {
        val source = AppPalettes.forMode(mode)
        return WidgetPalette(
            background = source.background.toArgb(),
            surface = source.surface.toArgb(),
            textPrimary = source.textPrimary.toArgb(),
            textSecondary = source.textSecondary.toArgb(),
            accent = source.accentPrimary.toArgb(),
            onAccent = source.onAccent.toArgb(),
            success = source.accentSecondary.toArgb(),
            danger = source.danger.toArgb(),
            border = source.border.toArgb(),
            dayOn = source.accentSecondary.toArgb(),
            dayOff = source.surfaceVariant.toArgb(),
            flame = source.danger.toArgb(),
            backgroundRes = when (mode) {
                ThemeMode.LIGHT -> R.drawable.widget_bg_light
                ThemeMode.DARK -> R.drawable.widget_bg_dark
                ThemeMode.AMOLED -> R.drawable.widget_bg_amoled
                ThemeMode.IRONPULSE -> R.drawable.widget_bg_ironpulse
            },
            buttonRes = when (mode) {
                ThemeMode.LIGHT -> R.drawable.widget_button_light
                ThemeMode.DARK -> R.drawable.widget_button_dark
                ThemeMode.AMOLED -> R.drawable.widget_button_amoled
                ThemeMode.IRONPULSE -> R.drawable.widget_button_ironpulse
            },
            cardRes = when (mode) {
                ThemeMode.LIGHT -> R.drawable.widget_card_light
                ThemeMode.DARK -> R.drawable.widget_card_dark
                ThemeMode.AMOLED -> R.drawable.widget_card_amoled
                ThemeMode.IRONPULSE -> R.drawable.widget_card_ironpulse
            },
            dayOnRes = when (mode) {
                ThemeMode.LIGHT -> R.drawable.widget_day_on_light
                ThemeMode.DARK -> R.drawable.widget_day_on_dark
                ThemeMode.AMOLED -> R.drawable.widget_day_on_amoled
                ThemeMode.IRONPULSE -> R.drawable.widget_day_on_ironpulse
            },
            dayOffRes = when (mode) {
                ThemeMode.LIGHT -> R.drawable.widget_day_off_light
                ThemeMode.DARK -> R.drawable.widget_day_off_dark
                ThemeMode.AMOLED -> R.drawable.widget_day_off_amoled
                ThemeMode.IRONPULSE -> R.drawable.widget_day_off_ironpulse
            }
        )
    }
}
