package com.seanyuan.filmframe.frame

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.seanyuan.filmframe.R

/**
 * Lazy + cached Typeface accessors. Variable TTFs include all weight axes;
 * the system picks the matching instance based on Paint weight setting.
 * Falls back to platform fonts if the resource fails to load (defensive).
 */
object Fonts {

    @Volatile private var garamondItalic: Typeface? = null
    @Volatile private var garamond: Typeface? = null
    @Volatile private var inter: Typeface? = null

    fun garamondItalic(context: Context): Typeface = garamondItalic ?: synchronized(this) {
        garamondItalic ?: (
            ResourcesCompat.getFont(context.applicationContext, R.font.eb_garamond_italic)
                ?: Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            ).also { garamondItalic = it }
    }

    fun garamond(context: Context): Typeface = garamond ?: synchronized(this) {
        garamond ?: (
            ResourcesCompat.getFont(context.applicationContext, R.font.eb_garamond_regular)
                ?: Typeface.SERIF
            ).also { garamond = it }
    }

    fun inter(context: Context): Typeface = inter ?: synchronized(this) {
        inter ?: (
            ResourcesCompat.getFont(context.applicationContext, R.font.inter)
                ?: Typeface.SANS_SERIF
            ).also { inter = it }
    }
}
