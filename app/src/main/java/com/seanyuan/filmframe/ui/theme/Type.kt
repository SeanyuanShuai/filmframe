package com.seanyuan.filmframe.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.seanyuan.filmframe.R

// Brand families. Kept separate from system Typography so screens can opt
// into the editorial serif explicitly (brand mark, captions) while body UI
// stays on the system sans for legibility.
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

/** Editorial italic wordmark — used for the JustFrame brand label. */
val BrandMark = TextStyle(
    fontFamily = DmSerifDisplay,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 30.sp,
    letterSpacing = (-0.5).sp,
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
