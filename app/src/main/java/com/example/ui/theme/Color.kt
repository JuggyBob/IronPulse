package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The four complete looks the app can wear.
 *
 * The storage key is deliberately separate from the display name: a rename in Settings must never
 * make an existing athlete lose the theme they picked.
 */
enum class ThemeMode(
    val storageKey: String,
    val displayName: String,
    val description: String,
    val isDark: Boolean
) {
    IRONPULSE(
        storageKey = "ironpulse",
        displayName = "IronPulse",
        description = "The original dark blue IronPulse identity.",
        isDark = true
    ),
    DARK(
        storageKey = "dark",
        displayName = "Dark",
        description = "Neutral charcoal surfaces with the IronPulse accents.",
        isDark = true
    ),
    LIGHT(
        storageKey = "light",
        displayName = "Light",
        description = "A clean light interface with dark, readable text.",
        isDark = false
    ),
    AMOLED(
        storageKey = "amoled",
        displayName = "AMOLED Dark",
        description = "True black backgrounds made for OLED displays.",
        isDark = true
    );

    companion object {
        /** Unknown or missing values fall back to the original theme, never to a random one. */
        fun fromStorageKey(raw: String?): ThemeMode =
            values().firstOrNull { it.storageKey == raw } ?: IRONPULSE
    }
}

/**
 * One selectable accent swatch.
 *
 * The default entry is a sentinel for the original per-theme IronPulse blue. Selecting it clears
 * the override instead of storing a hex value, so the original dark and light blues are preserved
 * exactly for anyone who never customizes the accent.
 */
@Immutable
data class AccentPreset(
    val displayName: String,
    val hex: String,
    val isDefault: Boolean = false
)

/**
 * Accent parsing, contrast and preset metadata.
 *
 * Kept pure so the Settings UI, the picker and the persistence layer all agree on what counts as
 * a valid colour and so the contrast rules can be unit tested without a Compose host.
 */
object AccentColors {

    /** The minimum contrast the adjusted accent must keep against app surfaces. */
    const val MIN_CONTRAST = 4.5f

    private val hexPattern = Regex("^[0-9A-Fa-f]{6}$")

    /**
     * The current default accent is #58A6FF in the dark themes. The light theme keeps its own
     * #0969DA default through the sentinel above, so it is intentionally not listed as a hex here.
     */
    val presets = listOf(
        AccentPreset("IronPulse Blue", "#58A6FF", isDefault = true),
        AccentPreset("Purple", "#9465E8"),
        AccentPreset("Cyan", "#22B8CF"),
        AccentPreset("Green", "#35AD75"),
        AccentPreset("Orange", "#E99539"),
        AccentPreset("Red", "#E45D65"),
        AccentPreset("Pink", "#DF70A8"),
        AccentPreset("Yellow", "#D6B43D")
    )

    /** Normalizes to `#RRGGBB`, or null when the input is not a complete six-digit colour. */
    fun normalizeHex(raw: String?): String? {
        val candidate = raw?.trim()?.removePrefix("#") ?: return null
        if (!hexPattern.matches(candidate)) return null
        return "#${candidate.uppercase(Locale.US)}"
    }

    fun parseHex(raw: String?): Color? {
        val normalized = normalizeHex(raw) ?: return null
        val rgb = normalized.removePrefix("#").toInt(16)
        return Color(0xFF000000.toInt() or rgb)
    }

    fun toHex(color: Color): String = String.format(
        Locale.US,
        "#%02X%02X%02X",
        (color.red * 255f).roundToInt().coerceIn(0, 255),
        (color.green * 255f).roundToInt().coerceIn(0, 255),
        (color.blue * 255f).roundToInt().coerceIn(0, 255)
    )

    /** The preset matching [raw], or the default preset when no override is stored. */
    fun presetFor(raw: String?): AccentPreset? {
        val normalized = normalizeHex(raw) ?: return presets.firstOrNull { it.isDefault }
        return presets.firstOrNull { it.hex == normalized }
    }

    /** True when [raw] is a valid colour that is not one of the presets. */
    fun isCustom(raw: String?): Boolean {
        val normalized = normalizeHex(raw) ?: return false
        return presets.none { it.hex == normalized }
    }

    /** White or a dark page tone, whichever is more readable on [color]. */
    fun contrastTextColor(color: Color): Color {
        val dark = Color(0xFF0D1117)
        return if (contrastRatio(color, Color.White) >= contrastRatio(color, dark)) {
            Color.White
        } else {
            dark
        }
    }

    /**
     * Keeps a custom colour visible on the active theme.
     *
     * A colour is adjusted only when it would be too close to the page, surface or on-accent tone.
     * The adjustment moves toward white on dark themes and toward black on light ones, preserving
     * the chosen hue while making text and small controls readable.
     */
    fun adjustForTheme(accent: Color, palette: AppPalette): Color {
        val targets = listOf(
            palette.onAccent,
            palette.background,
            palette.surface,
            palette.surfaceVariant
        )
        var candidate = accent
        var attempts = 0
        while (attempts < 24 && targets.any { contrastRatio(candidate, it) < MIN_CONTRAST }) {
            candidate = lerp(candidate, if (palette.isLight) Color.Black else Color.White, 0.10f)
            attempts++
        }
        return candidate
    }
}

/** WCAG relative luminance of an sRGB colour. */
fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

/** WCAG contrast ratio between two colours, from 1 to 21. */
fun contrastRatio(first: Color, second: Color): Float {
    val firstLuminance = first.relativeLuminance()
    val secondLuminance = second.relativeLuminance()
    val lighter = max(firstLuminance, secondLuminance)
    val darker = min(firstLuminance, secondLuminance)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Returns a palette with the athlete's accent applied.
 *
 * Null deliberately returns the base palette untouched, which is what keeps existing installations
 * exactly on their original blue until they choose an accent.
 */
fun AppPalette.withAccent(accent: Color?): AppPalette {
    if (accent == null) return this
    return copy(accentPrimary = AccentColors.adjustForTheme(accent, this))
}

/** Chart-specific colours, kept together so a new chart cannot invent a one-off palette. */
@Immutable
data class ChartPalette(
    val grid: Color,
    val label: Color,
    val selectedPoint: Color,
    val positive: Color,
    val negative: Color
)

/** Five steps from a rest day to the most intense day. */
@Immutable
data class HeatmapPalette(
    val empty: Color,
    val low: Color,
    val medium: Color,
    val high: Color,
    val max: Color
)

/** The custom-drawn body illustration. */
@Immutable
data class MusclePalette(
    val skin: Color,
    val skinLine: Color,
    val rest: Color,
    val dim: Color,
    val line: Color,
    val inactiveFill: Color,
    val absFill: Color,
    val activeOutline: Color
)

/**
 * Liquid Glass is layered from these tokens rather than from the raw accent.
 *
 * The panel body, specular sheen and lens body all need different values on a light page than on
 * a black one; keeping them here is what lets the same glass implementation work on all four
 * themes without a single theme conditional inside the drawing code.
 */
@Immutable
data class GlassPalette(
    val panelTop: Color,
    val panelMid: Color,
    val panelBottom: Color,
    val highlight: Color,
    val shade: Color,
    val innerRim: Color,
    val borderStrong: Color,
    val borderSoft: Color,
    val lensHighlight: Color,
    val lensWash: Color,
    val lensMid: Color,
    val lensDeep: Color,
    val press: Color
)

/**
 * The single authoritative palette for one theme.
 *
 * Every screen reads these roles, either through the legacy names below (which are now dynamic
 * getters) or through [LocalAppPalette]. No component decides on its own which theme is active.
 */
@Immutable
data class AppPalette(
    val mode: ThemeMode,
    val isLight: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val surfaceSunken: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val accentPurple: Color,
    val accentAmber: Color,
    val danger: Color,
    val aiAccent: Color,
    val onAccent: Color,
    val onDanger: Color,
    val successFlash: Color,
    val chart: ChartPalette,
    val heatmap: HeatmapPalette,
    val muscle: MusclePalette,
    val glass: GlassPalette,
    val scrim: Color
)

object AppPalettes {

    /** The pre-theme IronPulse palette, preserved value for value. */
    val ironPulse = AppPalette(
        mode = ThemeMode.IRONPULSE,
        isLight = false,
        background = Color(0xFF0D1117),
        surface = Color(0xFF161B22),
        surfaceVariant = Color(0xFF21262D),
        surfaceElevated = Color(0xFF1C2128),
        surfaceSunken = Color(0xFF0A0E13),
        border = Color(0xFF30363D),
        borderSubtle = Color(0xFF21262D),
        textPrimary = Color(0xFFF0F6FC),
        textSecondary = Color(0xFF8B949E),
        textDisabled = Color(0xFF6E7681),
        accentPrimary = Color(0xFF58A6FF),
        accentSecondary = Color(0xFF3FB950),
        accentTertiary = Color(0xFFF78166),
        accentPurple = Color(0xFFBC8CFF),
        accentAmber = Color(0xFFD29922),
        danger = Color(0xFFF85149),
        aiAccent = Color(0xFF7CC4F5),
        onAccent = Color(0xFF0D1117),
        onDanger = Color(0xFFFFFFFF),
        successFlash = Color(0xFFB4FFCB),
        chart = ChartPalette(
            grid = Color(0xFF30363D),
            label = Color(0xFF8B949E),
            selectedPoint = Color(0xFFF0F6FC),
            positive = Color(0xFF3FB950),
            negative = Color(0xFFF85149)
        ),
        heatmap = HeatmapPalette(
            empty = Color(0xFF1F252D),
            low = Color(0xFF1B5E33),
            medium = Color(0xFF23893D),
            high = Color(0xFF2FBE4C),
            max = Color(0xFF3CE063)
        ),
        muscle = MusclePalette(
            skin = Color(0xFF39424E),
            skinLine = Color(0xFF4C5764),
            rest = Color(0xFF3E4B5D),
            dim = Color(0xFF2A323C),
            line = Color(0xFF566374),
            inactiveFill = Color(0xFF323A45),
            absFill = Color(0xFF45536A),
            activeOutline = Color(0xFF8FF6FF)
        ),
        glass = GlassPalette(
            panelTop = Color(0xFF2A3441),
            panelMid = Color(0xFF161E27),
            panelBottom = Color(0xFF0B1016),
            highlight = Color(0xFFFFFFFF),
            shade = Color(0xFF000000),
            innerRim = Color(0xFFFFFFFF),
            borderStrong = Color(0xFFFFFFFF),
            borderSoft = Color(0xFFFFFFFF),
            lensHighlight = Color(0xFFFFFFFF),
            lensWash = Color(0xFFB8D9FF),
            lensMid = Color(0xFF243447),
            lensDeep = Color(0xFF070B10),
            press = Color(0xFFFFFFFF)
        ),
        scrim = Color(0xFF000000)
    )

    /** Neutral dark: charcoal instead of IronPulse blue, same accent language. */
    val dark = AppPalette(
        mode = ThemeMode.DARK,
        isLight = false,
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        surfaceVariant = Color(0xFF2A2A2A),
        surfaceElevated = Color(0xFF242424),
        surfaceSunken = Color(0xFF0C0C0C),
        border = Color(0xFF3A3A3A),
        borderSubtle = Color(0xFF2A2A2A),
        textPrimary = Color(0xFFF2F2F2),
        textSecondary = Color(0xFFA6A6A6),
        textDisabled = Color(0xFF7A7A7A),
        accentPrimary = Color(0xFF58A6FF),
        accentSecondary = Color(0xFF3FB950),
        accentTertiary = Color(0xFFF78166),
        accentPurple = Color(0xFFBC8CFF),
        accentAmber = Color(0xFFD29922),
        danger = Color(0xFFF85149),
        aiAccent = Color(0xFF8CC8F5),
        onAccent = Color(0xFF121212),
        onDanger = Color(0xFFFFFFFF),
        successFlash = Color(0xFFB4FFCB),
        chart = ChartPalette(
            grid = Color(0xFF3A3A3A),
            label = Color(0xFFA6A6A6),
            selectedPoint = Color(0xFFF2F2F2),
            positive = Color(0xFF3FB950),
            negative = Color(0xFFF85149)
        ),
        heatmap = HeatmapPalette(
            // The card surface is #1E1E1E. The empty cell must be visibly separate from it; the
            // old #1F1F1F was a one-level difference and disappeared on most displays.
            empty = Color(0xFF2A2A2A),
            low = Color(0xFF1B5E33),
            medium = Color(0xFF23893D),
            high = Color(0xFF2FBE4C),
            max = Color(0xFF3CE063)
        ),
        muscle = MusclePalette(
            skin = Color(0xFF3A3A3A),
            skinLine = Color(0xFF505050),
            rest = Color(0xFF444444),
            dim = Color(0xFF2E2E2E),
            line = Color(0xFF5A5A5A),
            inactiveFill = Color(0xFF333333),
            absFill = Color(0xFF4A4A4A),
            activeOutline = Color(0xFF8FF6FF)
        ),
        glass = GlassPalette(
            panelTop = Color(0xFF333333),
            panelMid = Color(0xFF1E1E1E),
            panelBottom = Color(0xFF0F0F0F),
            highlight = Color(0xFFFFFFFF),
            shade = Color(0xFF000000),
            innerRim = Color(0xFFFFFFFF),
            borderStrong = Color(0xFFFFFFFF),
            borderSoft = Color(0xFFFFFFFF),
            lensHighlight = Color(0xFFFFFFFF),
            lensWash = Color(0xFFB8D9FF),
            lensMid = Color(0xFF2E2E2E),
            lensDeep = Color(0xFF080808),
            press = Color(0xFFFFFFFF)
        ),
        scrim = Color(0xFF000000)
    )

    /** A complete light theme, not a washed-out version of the dark one. */
    val light = AppPalette(
        mode = ThemeMode.LIGHT,
        isLight = true,
        background = Color(0xFFF6F8FA),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEAEEF2),
        surfaceElevated = Color(0xFFFFFFFF),
        surfaceSunken = Color(0xFFF0F3F6),
        border = Color(0xFFD0D7DE),
        borderSubtle = Color(0xFFE4E8EC),
        textPrimary = Color(0xFF1F2328),
        textSecondary = Color(0xFF57606A),
        textDisabled = Color(0xFF8C959F),
        accentPrimary = Color(0xFF0969DA),
        accentSecondary = Color(0xFF1A7F37),
        accentTertiary = Color(0xFFBC4C00),
        accentPurple = Color(0xFF8250DF),
        accentAmber = Color(0xFF9A6700),
        danger = Color(0xFFCF222E),
        aiAccent = Color(0xFF0B5CAD),
        onAccent = Color(0xFFFFFFFF),
        onDanger = Color(0xFFFFFFFF),
        successFlash = Color(0xFF2DA44E),
        chart = ChartPalette(
            grid = Color(0xFFD0D7DE),
            label = Color(0xFF57606A),
            selectedPoint = Color(0xFF1F2328),
            positive = Color(0xFF1A7F37),
            negative = Color(0xFFCF222E)
        ),
        heatmap = HeatmapPalette(
            empty = Color(0xFFEBEDF0),
            low = Color(0xFF9BE9A8),
            medium = Color(0xFF40C463),
            high = Color(0xFF30A14E),
            max = Color(0xFF216E39)
        ),
        muscle = MusclePalette(
            skin = Color(0xFFD8DEE6),
            skinLine = Color(0xFFB6C0CC),
            rest = Color(0xFFC9D3DF),
            dim = Color(0xFFE4E9EF),
            line = Color(0xFFA8B3C1),
            inactiveFill = Color(0xFFDDE5EE),
            absFill = Color(0xFFB9C6D6),
            activeOutline = Color(0xFF0550AE)
        ),
        glass = GlassPalette(
            panelTop = Color(0xFFFFFFFF),
            panelMid = Color(0xFFF2F6FA),
            panelBottom = Color(0xFFE6EDF3),
            highlight = Color(0xFFFFFFFF),
            shade = Color(0xFF0D1117),
            innerRim = Color(0xFFFFFFFF),
            borderStrong = Color(0xFFFFFFFF),
            borderSoft = Color(0xFFFFFFFF),
            lensHighlight = Color(0xFFFFFFFF),
            lensWash = Color(0xFFB8D9FF),
            lensMid = Color(0xFFDCEBFF),
            lensDeep = Color(0xFFB7D3F2),
            press = Color(0xFFFFFFFF)
        ),
        scrim = Color(0xFF000000)
    )

    /** OLED: the page is genuinely #000000; cards and controls step up from it. */
    val amoled = AppPalette(
        mode = ThemeMode.AMOLED,
        isLight = false,
        background = Color(0xFF000000),
        surface = Color(0xFF101010),
        surfaceVariant = Color(0xFF1A1A1A),
        surfaceElevated = Color(0xFF141414),
        surfaceSunken = Color(0xFF000000),
        border = Color(0xFF2C2C2C),
        borderSubtle = Color(0xFF1F1F1F),
        textPrimary = Color(0xFFF2F2F2),
        textSecondary = Color(0xFF9BA1A6),
        textDisabled = Color(0xFF6E7681),
        accentPrimary = Color(0xFF58A6FF),
        accentSecondary = Color(0xFF3FB950),
        accentTertiary = Color(0xFFF78166),
        accentPurple = Color(0xFFBC8CFF),
        accentAmber = Color(0xFFD29922),
        danger = Color(0xFFF85149),
        aiAccent = Color(0xFF8CC8F5),
        onAccent = Color(0xFF000000),
        onDanger = Color(0xFFFFFFFF),
        successFlash = Color(0xFFB4FFCB),
        chart = ChartPalette(
            grid = Color(0xFF2C2C2C),
            label = Color(0xFF9BA1A6),
            selectedPoint = Color(0xFFF2F2F2),
            positive = Color(0xFF3FB950),
            negative = Color(0xFFF85149)
        ),
        heatmap = HeatmapPalette(
            // Match the distinguishable AMOLED surface step rather than a shade that disappears
            // into the #101010 card.
            empty = Color(0xFF1A1A1A),
            low = Color(0xFF123D22),
            medium = Color(0xFF1B5E33),
            high = Color(0xFF23893D),
            max = Color(0xFF2FBE4C)
        ),
        muscle = MusclePalette(
            skin = Color(0xFF1E1E1E),
            skinLine = Color(0xFF333333),
            rest = Color(0xFF262626),
            dim = Color(0xFF161616),
            line = Color(0xFF3A3A3A),
            inactiveFill = Color(0xFF1B1B1B),
            absFill = Color(0xFF2A2A2A),
            activeOutline = Color(0xFF8FF6FF)
        ),
        glass = GlassPalette(
            panelTop = Color(0xFF1A1A1A),
            panelMid = Color(0xFF0D0D0D),
            panelBottom = Color(0xFF000000),
            highlight = Color(0xFFFFFFFF),
            shade = Color(0xFF000000),
            innerRim = Color(0xFFFFFFFF),
            borderStrong = Color(0xFFFFFFFF),
            borderSoft = Color(0xFFFFFFFF),
            lensHighlight = Color(0xFFFFFFFF),
            lensWash = Color(0xFFB8D9FF),
            lensMid = Color(0xFF1A1A1A),
            lensDeep = Color(0xFF000000),
            press = Color(0xFFFFFFFF)
        ),
        scrim = Color(0xFF000000)
    )

    /**
     * The complete palette for a theme, optionally with the athlete's accent override applied.
     *
     * The optional parameter keeps every existing caller on the original palette and makes the
     * accent a single centralized override rather than a second theme system.
     */
    fun forMode(mode: ThemeMode, accent: Color? = null): AppPalette {
        val base = when (mode) {
            ThemeMode.IRONPULSE -> ironPulse
            ThemeMode.DARK -> dark
            ThemeMode.LIGHT -> light
            ThemeMode.AMOLED -> amoled
        }
        return base.withAccent(accent)
    }
}

/**
 * The active palette.
 *
 * It is snapshot state so a plain top-level getter can be observed by Compose. That is what lets
 * the 2,400 existing call sites keep their familiar names (`DarkBg`, `TextWhite`, ...) while every
 * one of them resolves to the selected theme. New code should prefer [LocalAppPalette] or the
 * semantic names, but both paths read the same palette object.
 */
object ThemeRuntime {
    var palette by mutableStateOf(AppPalettes.ironPulse)
        internal set
}

/** The palette for the current composition, for new code that wants explicit semantic names. */
val LocalAppPalette = staticCompositionLocalOf { AppPalettes.ironPulse }

// ---------------------------------------------------------------------------
// Legacy semantic names
//
// These are getters, not constants. Existing screens were written against these names, and keeping
// them as a dynamic bridge is what guarantees that every existing surface changes at once. They
// are intentionally non-composable: some are read inside Canvas draw scopes, default parameters
// and small helpers, where a composable-only getter would not compile.
// ---------------------------------------------------------------------------

val DarkBg: Color get() = ThemeRuntime.palette.background
val DarkSurface: Color get() = ThemeRuntime.palette.surface
val DarkSurfaceVariant: Color get() = ThemeRuntime.palette.surfaceVariant
val DarkBorder: Color get() = ThemeRuntime.palette.border
val DarkBorderSubtle: Color get() = ThemeRuntime.palette.borderSubtle

val ElectricCyan: Color get() = ThemeRuntime.palette.accentPrimary
val MintGreen: Color get() = ThemeRuntime.palette.accentSecondary
val CoralOrange: Color get() = ThemeRuntime.palette.accentTertiary
val VioletPurple: Color get() = ThemeRuntime.palette.accentPurple
val GoldenAmber: Color get() = ThemeRuntime.palette.accentAmber
val FireRed: Color get() = ThemeRuntime.palette.danger
val AiBlue: Color get() = ThemeRuntime.palette.aiAccent

val TextWhite: Color get() = ThemeRuntime.palette.textPrimary
val TextMuted: Color get() = ThemeRuntime.palette.textSecondary
val TextDim: Color get() = ThemeRuntime.palette.textDisabled

/**
 * Content placed on an accent-filled surface.
 *
 * Dark themes use the page colour (the existing look), while Light uses white. Anywhere the old
 * code used [DarkBg] as text or an icon on an accent button belongs here instead.
 */
val OnAccent: Color get() = ThemeRuntime.palette.onAccent

/** Content placed on a destructive (FireRed) surface. White in every theme. */
val OnDanger: Color get() = ThemeRuntime.palette.onDanger

/** The green flash of a fresh auto save. */
val SuccessFlash: Color get() = ThemeRuntime.palette.successFlash
