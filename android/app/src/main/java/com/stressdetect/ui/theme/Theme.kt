package com.stressdetect.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The visual system.
 *
 * This app tells someone they may be stressed, so the design must not add to it. Three
 * rules hold the whole thing together:
 *
 *  1. **No semantic colour.** Magnitude is encoded by bar LENGTH only — never by hue. A
 *     high score never turns anything red. There is no warning colour in this file.
 *  2. **Two accents, and they mean "source", not "severity".** Sage = your own answers,
 *     slate = the model. They exist to keep the validated number visually distinct from
 *     the unvalidated one.
 *  3. **Weight instead of emphasis.** Numerals are large but Regular; headings are Medium,
 *     not Bold. Nothing shouts.
 */

// ── palette ─────────────────────────────────────────────────────────────────────────────
private val PaperLight = Color(0xFFFBFAF8)
private val InkLight = Color(0xFF1F2421)
private val MutedInkLight = Color(0xFF5C625F)
private val AccentLight = Color(0xFF6E8B7E)       // sage — the questionnaire
private val AccentAltLight = Color(0xFF8A8FA3)    // slate — the model estimate
private val TrackLight = Color(0xFFE6E3DE)
private val CardLight = Color(0xFFFFFFFF)

private val PaperDark = Color(0xFF16181A)
private val InkDark = Color(0xFFE8E6E1)
private val MutedInkDark = Color(0xFF9AA09C)
private val AccentDark = Color(0xFF8FAE9F)
private val AccentAltDark = Color(0xFFA3A8BA)
private val TrackDark = Color(0xFF2A2D30)
private val CardDark = Color(0xFF1D2023)

/**
 * Roles Material's scheme has no slot for. Held in a CompositionLocal so screens never
 * hardcode a colour — every value on screen resolves through here and therefore has a
 * verified light AND dark counterpart.
 */
data class CalmColors(
    val paper: Color,
    val ink: Color,
    val mutedInk: Color,
    val accent: Color,
    val accentAlt: Color,
    val track: Color,
    val card: Color,
    val isDark: Boolean,
)

val LocalCalmColors = staticCompositionLocalOf {
    CalmColors(PaperLight, InkLight, MutedInkLight, AccentLight, AccentAltLight, TrackLight, CardLight, false)
}

/** Spacing scale. Generous whitespace is doing the calming work, so these are large. */
object Space {
    val screen = 24.dp
    val block = 20.dp
    val section = 40.dp
    val tight = 8.dp
    val item = 12.dp
}

private val CalmTypography = Typography(
    // Score numerals: large, but REGULAR weight. A bold 3-digit number reads as an alarm.
    displaySmall = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Normal),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    // Body is 17sp with 1.5 line-height: comfortable, unhurried reading.
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Normal),
    // Section eyebrows: small, letter-spaced, never bold.
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

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

    val calm = if (dark) {
        CalmColors(PaperDark, InkDark, MutedInkDark, AccentDark, AccentAltDark, TrackDark, CardDark, true)
    } else {
        CalmColors(PaperLight, InkLight, MutedInkLight, AccentLight, AccentAltLight, TrackLight, CardLight, false)
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = calm.accent, onPrimary = PaperDark,
            background = calm.paper, onBackground = calm.ink,
            surface = calm.card, onSurface = calm.ink,
            surfaceVariant = calm.track, onSurfaceVariant = calm.mutedInk,
            outline = calm.track,
            // Material insists on an error role; give it the ink colour so a stray usage
            // cannot introduce the red this design deliberately excludes.
            error = calm.ink, onError = calm.paper,
        )
    } else {
        lightColorScheme(
            primary = calm.accent, onPrimary = PaperLight,
            background = calm.paper, onBackground = calm.ink,
            surface = calm.card, onSurface = calm.ink,
            surfaceVariant = calm.track, onSurfaceVariant = calm.mutedInk,
            outline = calm.track,
            error = calm.ink, onError = calm.paper,
        )
    }

    CompositionLocalProvider(LocalCalmColors provides calm) {
        MaterialTheme(colorScheme = scheme, typography = CalmTypography, content = content)
    }
}
