package com.seanyuan.filmframe.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tokens for the Liquid Glass approximation.
 */
object GlassColors {
    val DeepBackground = Color(0xFF050507)
    val DeepSurfaceTint = Color(0xFF1A1E25)  // subtle bluish-cool tint behind glass
    val Accent = Color(0xFFE4B86E)
    val AccentDeep = Color(0xFFB47F2F)
    val AccentSoft = Color(0x33D4A24A)
    val OnSurface = Color(0xFFFAFAFA)
    val OnSurfaceMuted = Color(0xCCFAFAFA)
    val OnSurfaceFaint = Color(0x77FAFAFA)
}

/**
 * iOS 26 "Liquid Glass" approximation, layered.
 *
 *   1. base translucent fill (cool dark tint)
 *   2. vertical gradient — brighter top, near-zero bottom (light catch)
 *   3. specular highlight stripe at the very top edge (curved glass)
 *   4. outer border with edge gradient (top bright, bottom dim)
 *
 * intensity 1.0 = resting surface. Bump to 1.5–2.0 for focal modals.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    intensity: Float = 1f,
    content: @Composable () -> Unit,
) {
    val baseAlpha = 0.05f * intensity
    val gradTop = 0.14f * intensity
    val gradMid = 0.05f * intensity
    val gradBottom = 0.015f * intensity
    val borderTop = 0.45f * intensity.coerceAtMost(1.4f)
    val borderBottom = 0.06f * intensity

    Box(
        modifier = modifier
            .clip(shape)
            .background(GlassColors.DeepSurfaceTint.copy(alpha = baseAlpha * 6f))
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = gradTop),
                    0.32f to Color.White.copy(alpha = gradMid),
                    0.78f to Color.White.copy(alpha = gradBottom),
                    1f to Color.White.copy(alpha = 0f),
                )
            )
            .border(
                width = 0.6.dp,
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = borderTop),
                    0.45f to Color.White.copy(alpha = 0.06f),
                    1f to Color.White.copy(alpha = borderBottom),
                ),
                shape = shape,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.4.dp)
                .padding(horizontal = 16.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.55f * intensity.coerceAtMost(1.4f)),
                        1f to Color.Transparent,
                    )
                ),
        )
        content()
    }
}

/**
 * Glass-style pressable. Adds spring-based press scale and accent variant.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "press-scale",
    )

    val shape = RoundedCornerShape(14.dp)
    val intensity = if (accent) 1.9f else 1.05f

    val fillBrush = if (accent) {
        Brush.verticalGradient(
            0f to GlassColors.Accent,
            1f to GlassColors.AccentDeep,
        )
    } else {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.14f),
            0.4f to Color.White.copy(alpha = 0.06f),
            1f to Color.White.copy(alpha = 0.02f),
        )
    }
    val borderBrush = if (accent) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.55f),
            1f to Color.Black.copy(alpha = 0.15f),
        )
    } else {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.35f),
            1f to Color.White.copy(alpha = 0.06f),
        )
    }
    val textColor = if (accent) Color(0xFF1A1100) else GlassColors.OnSurface

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(fillBrush)
            .border(0.6.dp, borderBrush, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Top specular sheen on the button itself
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = 10.dp)
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = if (accent) 0.55f else 0.45f),
                        1f to Color.Transparent,
                    )
                ),
        )
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) textColor else textColor.copy(alpha = 0.4f)
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
