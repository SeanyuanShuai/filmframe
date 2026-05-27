package com.seanyuan.filmframe.frame

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.seanyuan.filmframe.R

/**
 * Type stack — all SIL Open Font License (commercial use OK):
 *   - Cormorant Garamond  (display serif, classical book feel, refined italic)
 *   - DM Serif Display    (high-contrast display serif by Colophon Foundry)
 *   - Inter               (geometric sans, modern, near-Helvetica neutrality)
 *
 * No CJK glyphs bundled by design; Latin-only with system fallback if needed.
 */
object Fonts {

    @Volatile private var cormorantItalic: Typeface? = null
    @Volatile private var cormorantRegular: Typeface? = null
    @Volatile private var dmSerifDisplay: Typeface? = null
    @Volatile private var dmSerifDisplayItalic: Typeface? = null
    @Volatile private var inter: Typeface? = null

    fun cormorantItalic(context: Context): Typeface = cormorantItalic ?: synchronized(this) {
        cormorantItalic ?: (
            ResourcesCompat.getFont(context.applicationContext, R.font.cormorant_garamond_italic)
                ?: Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            ).also { cormorantItalic = it }
    }

    fun cormorant(context: Context): Typeface = cormorantRegular ?: synchronized(this) {
        cormorantRegular ?: (
            ResourcesCompat.getFont(context.applicationContext, R.font.cormorant_garamond_regular)
                ?: Typeface.SERIF
            ).also { cormorantRegular = it }
    }

    fun dmSerif(context: Context): Typeface = dmSerifDisplay ?: synchronized(this) {
        dmSerifDisplay ?: (
            ResourcesCompat.getFont(context.applicationContext, R.font.dm_serif_display_regular)
                ?: Typeface.SERIF
            ).also { dmSerifDisplay = it }
    }

    fun dmSerifItalic(context: Context): Typeface = dmSerifDisplayItalic ?: synchronized(this) {
        dmSerifDisplayItalic ?: (
            ResourcesCompat.getFont(context.applicationContext, R.font.dm_serif_display_italic)
                ?: Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            ).also { dmSerifDisplayItalic = it }
    }

    fun inter(context: Context): Typeface = inter ?: synchronized(this) {
        inter ?: (
            ResourcesCompat.getFont(context.applicationContext, R.font.inter)
                ?: Typeface.SANS_SERIF
            ).also { inter = it }
    }
}
