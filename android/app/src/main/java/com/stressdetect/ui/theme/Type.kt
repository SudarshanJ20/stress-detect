package com.stressdetect.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.stressdetect.R

/**
 * Bundled type. **Never downloadable fonts** — this app has no INTERNET permission, so a
 * runtime font request cannot succeed; it would silently fall back to the system face and
 * the whole design would quietly revert to looking like a prototype.
 *
 * Both faces ship as VARIABLE fonts (Google Fonts has no static instances for either), so
 * each weight is a `FontVariation` on the same file rather than a separate TTF. Variation
 * settings need API 26; minSdk is 29.
 *
 * Licences: SIL Open Font License 1.1 for both, in `android/app/licenses/`. The licence has
 * to travel with the fonts — do not remove those files.
 */

// The `variationSettings` overload of Font() is still marked experimental. Opting in
// knowingly: it is the only way to select a weight from a variable font, and Google Fonts
// ships no static instances of either face. The alternative — accepting each font's default
// instance — would render every weight identically.
@OptIn(ExperimentalTextApi::class)
private val Fraunces = FontFamily(
    Font(
        R.font.fraunces,
        weight = FontWeight.W500,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.fraunces,
        weight = FontWeight.W600,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

@OptIn(ExperimentalTextApi::class)
private val Inter = FontFamily(
    Font(
        R.font.inter,
        weight = FontWeight.W400,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.inter,
        weight = FontWeight.W600,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

/**
 * Styles that Material's scale has no slot for. Held here rather than inlined so a screen
 * cannot invent its own size.
 */
object CalmType {
    /** The one big number. Fraunces, tight tracking so 76sp does not feel sprawling. */
    val hero = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.W600,
        fontSize = 76.sp,
        letterSpacing = (-0.02).em,
        lineHeight = 84.sp,
    )

    /** Eyebrow / label: small, spaced, uppercased at the call site. */
    val eyebrow = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.W600,
        fontSize = 12.sp,
        letterSpacing = 0.08.em,
        lineHeight = 16.sp,
    )
}

val CalmTypography = Typography(
    // Screen titles.
    headlineSmall = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.W500,
        fontSize = 30.sp,
        lineHeight = 38.sp,
    ),
    // Section headings.
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.W600,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    // Body. 1.55 line height (16 × 1.55 ≈ 24.8) — unhurried to read.
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.8.sp,
    ),
    // Caption.
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelSmall = CalmType.eyebrow,
    // Buttons pick this up.
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        textAlign = TextAlign.Center,
    ),
)
