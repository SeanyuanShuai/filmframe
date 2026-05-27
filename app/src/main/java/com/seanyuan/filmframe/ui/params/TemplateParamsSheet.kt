package com.seanyuan.filmframe.ui.params

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seanyuan.filmframe.frame.FrameTemplate
import com.seanyuan.filmframe.frame.TemplateAdjustments
import com.seanyuan.filmframe.ui.glass.GlassButton
import com.seanyuan.filmframe.ui.glass.GlassColors

/**
 * In-line params editor. Mounted as a panel above the bottom action row
 * when the user taps "调整" on a selected template. Avoids ModalBottomSheet
 * complexity for v0.1.
 */
@Composable
fun TemplateParamsPanel(
    template: FrameTemplate,
    adjustments: TemplateAdjustments,
    onChange: (TemplateAdjustments) -> Unit,
    onClose: () -> Unit,
) {
    val supportsCaption = template.id in listOf("classic", "bold", "polaroid")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF0E0E0E))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                "调整 · ${template.displayName}",
                color = GlassColors.OnSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            GlassButton(text = "重置", onClick = { onChange(TemplateAdjustments.Default) })
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            GlassButton(text = "完成", accent = true, onClick = onClose)
        }
        Spacer(Modifier.height(20.dp))

        Slider(
            label = "边框宽度",
            value = adjustments.borderWidthMultiplier,
            range = 0.5f..1.5f,
            display = "${(adjustments.borderWidthMultiplier * 100).toInt()}%",
            onChange = { onChange(adjustments.copy(borderWidthMultiplier = it)) },
        )

        if (supportsCaption) {
            Spacer(Modifier.height(8.dp))
            Slider(
                label = "字号",
                value = adjustments.titleSizeMultiplier,
                range = 0.6f..1.6f,
                display = "${(adjustments.titleSizeMultiplier * 100).toInt()}%",
                onChange = { onChange(adjustments.copy(titleSizeMultiplier = it)) },
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "显示 EXIF 文字",
                    color = GlassColors.OnSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = adjustments.showCaption,
                    onCheckedChange = { onChange(adjustments.copy(showCaption = it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GlassColors.OnSurface,
                        checkedTrackColor = GlassColors.Accent,
                        uncheckedThumbColor = GlassColors.OnSurfaceFaint,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun Slider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(
                label,
                color = GlassColors.OnSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                display,
                color = GlassColors.Accent,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = GlassColors.Accent,
                activeTrackColor = GlassColors.Accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.1f),
            ),
        )
    }
}
