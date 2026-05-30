package com.seanyuan.filmframe.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.seanyuan.filmframe.R

// Brand families. Kept separate from the body stack so screens can opt into a
// specific display serif (brand mark, captions) explicitly.
val DmSerifDisplay = FontFamily(
    Font(R.font.dm_serif_display_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.dm_serif_display_italic, FontWeight.Normal, FontStyle.Italic),
)

val Cormorant = FontFamily(
    Font(R.font.cormorant_garamond_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.cormorant_garamond_italic, FontWeight.Normal, FontStyle.Italic),
)

val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal, FontStyle.Normal),
)

/**
 * Editorial serif — Source Han Serif / 思源宋体 (SIL OFL 1.1), subset to the
 * app's glyph set (~90KB/weight, two weights). Its Latin is a proper book
 * serif, so the whole UI reads in one editorial register — the Magnum/Aperture
 * catalogue feel — Chinese and Latin sharing a voice. The subset also carries
 * Basic Latin + digits, so mixed strings like "导出 (3)" stay coherent.
 *
 * License text bundled at licenses/SourceHanSerif-OFL.txt.
 */
val SerifZh = FontFamily(
    Font(R.font.source_han_serif_regular, FontWeight.Normal),
    Font(R.font.source_han_serif_regular, FontWeight.Medium),
    Font(R.font.source_han_serif_semibold, FontWeight.SemiBold),
    Font(R.font.source_han_serif_semibold, FontWeight.Bold),
)

/** Editorial italic wordmark — used for the JustFrame brand label. */
val BrandMark = TextStyle(
    fontFamily = DmSerifDisplay,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 30.sp,
    letterSpacing = (-0.5).sp,
)

private fun Typography.withFamily(f: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

/** App-wide type — every Material text style set on the editorial serif. */
val Typography: Typography = Typography().withFamily(SerifZh)
