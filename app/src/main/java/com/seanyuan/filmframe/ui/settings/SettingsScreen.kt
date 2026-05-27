package com.seanyuan.filmframe.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seanyuan.filmframe.data.Settings
import com.seanyuan.filmframe.data.WatermarkPosition
import com.seanyuan.filmframe.data.WatermarkSettings
import com.seanyuan.filmframe.ui.glass.GlassButton
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.glass.GlassSurface
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watermark by Settings.watermark(context).collectAsState(initial = WatermarkSettings.Default)
    var draft by remember(watermark) { mutableStateOf(watermark) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassColors.DeepBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 20.dp, vertical = 20.dp)),
        ) {
            TopBar(onBack = onBack)
            Spacer(Modifier.height(24.dp))

            SectionTitle("水印")
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "启用水印",
                                color = GlassColors.OnSurface,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "渲染在输出图右下角等位置",
                                color = GlassColors.OnSurfaceFaint,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = draft.enabled,
                            onCheckedChange = { draft = draft.copy(enabled = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GlassColors.OnSurface,
                                checkedTrackColor = GlassColors.Accent,
                                uncheckedThumbColor = GlassColors.OnSurfaceFaint,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                            ),
                        )
                    }
                    if (draft.enabled) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = draft.text,
                            onValueChange = { draft = draft.copy(text = it) },
                            placeholder = { Text("例：© Sean Yuan", color = GlassColors.OnSurfaceFaint) },
                            label = { Text("水印文字", color = GlassColors.OnSurfaceMuted) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = GlassColors.OnSurface,
                                unfocusedTextColor = GlassColors.OnSurface,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = GlassColors.Accent,
                                focusedIndicatorColor = GlassColors.Accent,
                                unfocusedIndicatorColor = Color.White.copy(alpha = 0.2f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(20.dp))
                        Text(
                            "位置",
                            color = GlassColors.OnSurfaceMuted,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        PositionGrid(
                            selected = draft.position,
                            onSelect = { draft = draft.copy(position = it) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("关于")
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AboutRow("项目", "FilmFrame · v0.1 dev")
                    AboutRow("仓库", "github.com/SeanyuanShuai/filmframe")
                    AboutRow("字体", "Cormorant Garamond · DM Serif Display · Inter (SIL OFL)")
                }
            }

            Spacer(Modifier.height(32.dp))
            GlassButton(
                text = "保存",
                accent = true,
                onClick = {
                    scope.launch {
                        Settings.updateWatermark(context, draft.copy(text = draft.text.trim()))
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("← 返回", color = GlassColors.OnSurface, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(16.dp))
        Text(
            "设置",
            color = GlassColors.OnSurface,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = GlassColors.OnSurfaceMuted,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )
}

@Composable
private fun PositionGrid(
    selected: WatermarkPosition,
    onSelect: (WatermarkPosition) -> Unit,
) {
    val cells = listOf(
        WatermarkPosition.TopLeft to WatermarkPosition.TopRight,
        WatermarkPosition.BottomLeft to WatermarkPosition.BottomRight,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((left, right) in cells) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PositionTile(left, selected == left) { onSelect(left) }
                PositionTile(right, selected == right) { onSelect(right) }
            }
        }
    }
}

@Composable
private fun PositionTile(
    position: WatermarkPosition,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) GlassColors.Accent else Color.White.copy(alpha = 0.12f)
    val bg = if (selected) GlassColors.AccentSoft else Color.White.copy(alpha = 0.04f)
    Box(
        modifier = Modifier
            .height(70.dp)
            .fillMaxWidth(0.5f)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                if (selected) 1.5.dp else 0.5.dp,
                border,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
    ) {
        // Render a tiny dot in the corresponding corner of the tile
        val dotPos = when (position) {
            WatermarkPosition.TopLeft -> Alignment.TopStart
            WatermarkPosition.TopRight -> Alignment.TopEnd
            WatermarkPosition.BottomLeft -> Alignment.BottomStart
            WatermarkPosition.BottomRight -> Alignment.BottomEnd
        }
        Box(
            modifier = Modifier
                .align(dotPos)
                .padding(10.dp)
                .height(6.dp)
                .width(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (selected) GlassColors.Accent else GlassColors.OnSurfaceFaint),
        )
        Text(
            position.displayName,
            color = GlassColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = GlassColors.OnSurfaceFaint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
        Text(value, color = GlassColors.OnSurface, style = MaterialTheme.typography.bodySmall)
    }
}
