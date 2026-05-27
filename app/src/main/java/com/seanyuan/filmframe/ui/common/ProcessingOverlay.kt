package com.seanyuan.filmframe.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.glass.GlassSurface

/**
 * Full-screen processing modal. Pass `visible = true` while work is in flight;
 * the overlay handles its own enter/exit animation.
 */
@Composable
fun ProcessingOverlay(
    visible: Boolean,
    title: String,
    subtitle: String? = null,
    progress: Pair<Int, Int>? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.95f),
            ) {
                GlassSurface(
                    modifier = Modifier
                        .width(320.dp)
                        .padding(24.dp),
                    intensity = 1.7f,
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (progress == null) {
                            CircularProgressIndicator(
                                color = GlassColors.Accent,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(36.dp),
                            )
                        } else {
                            val (done, total) = progress
                            Text(
                                "$done / $total",
                                color = GlassColors.OnSurface,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { done.toFloat() / total },
                                color = GlassColors.Accent,
                                trackColor = Color.White.copy(alpha = 0.1f),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .height(3.dp),
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            title,
                            color = GlassColors.OnSurface,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        subtitle?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                it,
                                color = GlassColors.OnSurfaceMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
