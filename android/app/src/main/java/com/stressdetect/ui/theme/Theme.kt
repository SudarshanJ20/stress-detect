package com.stressdetect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The visual system: warm terracotta against a cool deep teal, on blush paper.
 *
 * The two-temperature contrast is the point. A single muted colour reads as dead however
 * carefully it is spaced, and this app is about someone's week, not a control panel.
 *
 * Three rules survive from the earlier system, because they are about safety rather than
 * taste:
 *
 *  1. **No traffic lights.** Score bands never map to red/amber/green. Every band uses the
 *     same terracotta; only the depth of the muted fill behind the figure changes. Someone
 *     at the high band must not open a red screen.
 *  2. **Terracotta carries no text on paper.** Measured 3.02:1 against the paper — below AA.
 *     It is a FILL colour: buttons, the figure, dots. Text that needs colour uses the teal
 *     (4.95:1) or ink.
 *  3. **Buttons use the `buttonFill`/`onButton` pair, not the accent.** White on `#E2704A`
 *     is 3.16:1 — below AA for a 16sp label — and in dark mode white fails on every
 *     terracotta here, so the two themes resolve it differently: light is white on deep
 *     terracotta (4.62:1), dark inverts to dark text on the bright accent (7.61:1).
 *     `ContrastTest` enforces both, so a future edit cannot quietly reintroduce a failure.
 */

// ── light: warm blush paper ──────────────────────────────────────────────────────────────
private val PaperLight = Color(0xFFFFF9F4)
private val SurfaceLight = Color(0xFFFFFFFF)
private val BorderLight = Color(0xFFF0E4DA)
private val InkLight = Color(0xFF1A1614)
private val InkMutedLight = Color(0xFF6E625C)
private val AccentLight = Color(0xFFE2704A)
private val AccentDeepLight = Color(0xFFC1532F)
private val AccentMutedLight = Color(0xFFFBE4D9)
private val SecondaryLight = Color(0xFF1E7A6E)
private val SecondaryMutedLight = Color(0xFFD6ECE7)
private val DividerLight = Color(0xFFF3E7DD)

// ── dark ─────────────────────────────────────────────────────────────────────────────────
private val PaperDark = Color(0xFF16131A)
private val SurfaceDark = Color(0xFF211C24)
private val BorderDark = Color(0xFF322A36)
private val InkDark = Color(0xFFF7F1EC)
private val InkMutedDark = Color(0xFFA79C99)
private val AccentDark = Color(0xFFF58A62)
private val AccentDeepDark = Color(0xFFE2704A)
private val AccentMutedDark = Color(0xFF3A2A26)
private val SecondaryDark = Color(0xFF58BFAE)
private val SecondaryMutedDark = Color(0xFF23383A)
private val DividerDark = Color(0xFF2B2430)

/**
 * Every colour on screen resolves through here, so each one has a verified light AND dark
 * counterpart and no screen can hardcode a hex.
 */
data class CalmColors(
    val paper: Color,
    val card: Color,
    val cardBorder: Color,
    val ink: Color,
    val mutedInk: Color,
    /** Fills, the figure, dots. NEVER text on paper — see the class doc. */
    val accent: Color,
    /** Pressed states and the figure's stroke. */
    val accentDeep: Color,
    /**
     * Filled-button background, and the label colour that is AA-safe on it.
     *
     * These differ by theme because no single pairing works for both: white fails on every
     * terracotta in the dark palette (2.4–3.2:1), so dark mode inverts to a bright fill with
     * dark text (7.61:1), the usual dark-theme convention. Light mode keeps white on the
     * deep terracotta (4.62:1). `ContrastTest` checks both.
     */
    val buttonFill: Color,
    val onButton: Color,
    /** The wash behind the figure; deepens slightly with the band. */
    val accentMuted: Color,
    /** Links and the chart line. Safe as text (4.95:1 light, 8.30:1 dark). */
    val secondary: Color,
    val secondaryMuted: Color,
    val divider: Color,
    val isDark: Boolean,
) {
    /** Legacy alias — the old slate `accentAlt` role is now the teal secondary. */
    val accentAlt: Color get() = secondary

    /** The old `track` role: hairlines and unfilled bar backgrounds. */
    val track: Color get() = divider
}

private val LightColors = CalmColors(
    paper = PaperLight, card = SurfaceLight, cardBorder = BorderLight,
    ink = InkLight, mutedInk = InkMutedLight,
    accent = AccentLight, accentDeep = AccentDeepLight, accentMuted = AccentMutedLight,
    buttonFill = AccentDeepLight, onButton = Color.White,
    secondary = SecondaryLight, secondaryMuted = SecondaryMutedLight,
    divider = DividerLight, isDark = false,
)

private val DarkColors = CalmColors(
    paper = PaperDark, card = SurfaceDark, cardBorder = BorderDark,
    ink = InkDark, mutedInk = InkMutedDark,
    accent = AccentDark, accentDeep = AccentDeepDark, accentMuted = AccentMutedDark,
    buttonFill = AccentDark, onButton = PaperDark,
    secondary = SecondaryDark, secondaryMuted = SecondaryMutedDark,
    divider = DividerDark, isDark = true,
)

val LocalCalmColors = staticCompositionLocalOf { LightColors }

/** Exposed for `ContrastTest`, which asserts the AA ratios of the pairs actually used. */
object CalmPalette {
    val light: CalmColors = LightColors
    val dark: CalmColors = DarkColors
}

/** 8dp grid. */
object Space {
    val screen = 24.dp
    val section = 40.dp
    val block = 20.dp
    val card = 20.dp
    val item = 12.dp
    val tight = 8.dp

    val cardRadius = 20.dp
    val buttonRadius = 16.dp
    val buttonHeight = 56.dp
    /** Beyond this, a single column of text stops being readable — centre it instead. */
    val maxContentWidth = 440.dp
}

/** User's theme preference. Default follows the system. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

@Composable
fun StressDetectTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val calm = if (dark) DarkColors else LightColors

    val scheme = if (dark) {
        darkColorScheme(
            primary = calm.buttonFill, onPrimary = calm.onButton,
            secondary = calm.secondary,
            background = calm.paper, onBackground = calm.ink,
            surface = calm.card, onSurface = calm.ink,
            surfaceVariant = calm.divider, onSurfaceVariant = calm.mutedInk,
            outline = calm.cardBorder,
            // Material insists on an error role. Bound to ink so a stray usage cannot
            // introduce the red this design deliberately excludes.
            error = calm.ink, onError = calm.paper,
        )
    } else {
        lightColorScheme(
            primary = calm.buttonFill, onPrimary = calm.onButton,
            secondary = calm.secondary,
            background = calm.paper, onBackground = calm.ink,
            surface = calm.card, onSurface = calm.ink,
            surfaceVariant = calm.divider, onSurfaceVariant = calm.mutedInk,
            outline = calm.cardBorder,
            error = calm.ink, onError = calm.paper,
        )
    }

    CompositionLocalProvider(LocalCalmColors provides calm) {
        MaterialTheme(colorScheme = scheme, typography = CalmTypography, content = content)
    }
}
