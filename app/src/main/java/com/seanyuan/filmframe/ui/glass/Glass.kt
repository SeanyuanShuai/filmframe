package com.seanyuan.filmframe.ui.glass

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * iOS 26 "Liquid Glass" approximation for Android Compose without backdrop
 * blur (Min SDK 26).
 *
 * Composition:
 *   - vertical gradient of subtle white-alpha tones on a dark base
 *   - 0.5dp white-alpha border at the top edge to read as "glass highlight"
 *   - rounded corners (24dp default — large enough to feel ambient)
 */
object GlassColors {
    val DeepBackground = Color(0xFF050505)
    val Accent = Color(0xFFD4A24A)
    val AccentSoft = Color(0x33D4A24A)
    val OnSurface = Color(0xFFFAFAFA)
    val OnSurfaceMuted = Color(0xB3FAFAFA)
    val OnSurfaceFaint = Color(0x66FAFAFA)
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    tonalIntensity: Float = 1f,
    content: @Composable () -> Unit,
) {
    val topAlpha = 0.09f * tonalIntensity
    val bottomAlpha = 0.03f * tonalIntensity
    val highlightAlpha = 0.16f * tonalIntensity

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = topAlpha),
                    0.5f to Color.White.copy(alpha = (topAlpha + bottomAlpha) / 2f),
                    1f to Color.White.copy(alpha = bottomAlpha),
                )
            )
            .border(
                BorderStroke(0.6.dp, Color.White.copy(alpha = highlightAlpha)),
                shape,
            ),
    ) { content() }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val intensity = if (accent) 1.8f else 1.0f
    val bgGradient = if (accent) {
        Brush.verticalGradient(
            0f to Color(0xFFE5B765),
            1f to Color(0xFFB47F2F),
        )
    } else {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.10f * intensity),
            1f to Color.White.copy(alpha = 0.03f * intensity),
        )
    }
    val borderAlpha = if (accent) 0.35f else 0.16f
    val textColor = if (accent) Color(0xFF1A1100) else GlassColors.OnSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgGradient)
            .border(
                BorderStroke(0.6.dp, Color.White.copy(alpha = borderAlpha)),
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides
                if (enabled) textColor else textColor.copy(alpha = 0.4f)
        ) {
            content()
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    GlassButton(onClick = onClick, modifier = modifier, enabled = enabled, accent = accent) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
