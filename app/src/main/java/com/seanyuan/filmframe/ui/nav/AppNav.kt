package com.seanyuan.filmframe.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.seanyuan.filmframe.ui.rememberHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Three primary destinations. Create is the default landing. */
enum class Tab(val label: String, val icon: ImageVector) {
    Gallery("画廊", Icons.Rounded.PhotoLibrary),
    Create("创作", Icons.Rounded.AddBox),
    Settings("设置", Icons.Rounded.Settings),
}

/**
 * Floating glassmorphic tab pill. Order: 画廊 · 创作 · 设置 (Create centered as
 * the primary entry). Matches the v4.2 design — translucent dark fill,
 * specular top highlight, active item white with a soft glow.
 */
@Composable
fun BottomNav(
    current: Tab,
    onSelect: (Tab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(32.dp))
                // Real frosted glass — blurs whatever screen content sits behind
                // the pill, tinted dark so labels stay legible over any photo.
                .hazeEffect(state = hazeState) {
                    backgroundColor = Color(0xFF0A0A0A)
                    tints = listOf(HazeTint(Color(0xFF141414).copy(alpha = 0.55f)))
                    blurRadius = 28.dp
                    noiseFactor = 0.04f
                }
                .border(
                    0.8.dp,
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.18f),
                        1f to Color.White.copy(alpha = 0.04f),
                    ),
                    RoundedCornerShape(32.dp),
                ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == current,
                    onClick = { if (tab != current) haptics.tick(); onSelect(tab) },
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
        animationSpec = tween(280),
        label = "tab-tint",
    )
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            tab.label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
