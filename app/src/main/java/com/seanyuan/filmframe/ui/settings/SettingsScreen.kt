package com.seanyuan.filmframe.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.seanyuan.filmframe.data.ExportQuality
import com.seanyuan.filmframe.data.Settings
import com.seanyuan.filmframe.data.WatermarkPosition
import com.seanyuan.filmframe.data.WatermarkSettings
import com.seanyuan.filmframe.ui.glass.GlassButton
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.glass.GlassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val watermark by Settings.watermark(context).collectAsState(initial = WatermarkSettings.Default)
    val exportQuality by Settings.exportQuality(context).collectAsState(initial = ExportQuality.Original)
    val autoRemoveFrame by Settings.autoRemoveExistingFrame(context).collectAsState(initial = true)

    var draftWatermark by remember(watermark) { mutableStateOf(watermark) }

    BackHandler { onBack() }

    // Auto-save watermark draft with 400 ms debounce — covers typing in
    // the text field without a write storm. Prior behavior lost text if the
    // user pressed system back instead of the explicit save button.
    LaunchedEffect(draftWatermark) {
        if (draftWatermark == watermark) return@LaunchedEffect
        delay(400)
        Settings.updateWatermark(
            context,
            draftWatermark.copy(text = draftWatermark.text.trim()),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassColors.DeepBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 20.dp, vertical = 20.dp)),
        ) {
            TopBar(onBack = onBack)
            Spacer(Modifier.height(24.dp))

            var showOriginalInfo by remember { mutableStateOf(false) }

            SectionTitle("画质")
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    ExportQuality.values().forEach { quality ->
                        QualityRow(
                            quality = quality,
                            selected = quality == exportQuality,
                            onClick = { scope.launch { Settings.updateExportQuality(context, quality) } },
                            onInfoClick = if (quality == ExportQuality.Original) {
                                { showOriginalInfo = true }
                            } else null,
                        )
                    }
                }
            }

            if (showOriginalInfo) {
                AlertDialog(
                    onDismissRequest = { showOriginalInfo = false },
                    title = { Text("为什么文件比原图小？", color = GlassColors.OnSurface) },
                    text = {
                        Text(
                            "原画保留全部像素，但 JPEG 是有损格式，重新编码必定会比相机原始 JPEG 小 30-50%。\n\n" +
                                "Android 的 JPEG 编码器即使 quality=100 也会做色度子采样（4:2:0），" +
                                "OPPO/小米/Apple 相机直出的 JPEG 通常用更宽松的子采样（4:4:4），所以体积更大。\n\n" +
                                "像素数量和可见画质完全不变。要做到字节级无损，需要源图是 PNG 或 WEBP。",
                            color = GlassColors.OnSurfaceMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showOriginalInfo = false }) {
                            Text("知道了", color = GlassColors.Accent)
                        }
                    },
                    containerColor = Color(0xFF1A1A1A),
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("原图处理")
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "自动去除原边框",
                            color = GlassColors.OnSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "如 OPPO HASSELBLAD、小米 Leica 这类品牌水印边框",
                            color = GlassColors.OnSurfaceFaint,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = autoRemoveFrame,
                        onCheckedChange = { v ->
                            scope.launch { Settings.updateAutoRemoveExistingFrame(context, v) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GlassColors.OnSurface,
                            checkedTrackColor = GlassColors.Accent,
                            uncheckedThumbColor = GlassColors.OnSurfaceFaint,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                        ),
                    )
                }
            }

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
                            checked = draftWatermark.enabled,
                            onCheckedChange = { draftWatermark = draftWatermark.copy(enabled = it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GlassColors.OnSurface,
                                checkedTrackColor = GlassColors.Accent,
                                uncheckedThumbColor = GlassColors.OnSurfaceFaint,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                            ),
                        )
                    }
                    if (draftWatermark.enabled) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = draftWatermark.text,
                            onValueChange = { draftWatermark = draftWatermark.copy(text = it) },
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
                            selected = draftWatermark.position,
                            onSelect = { draftWatermark = draftWatermark.copy(position = it) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("关于")
            GlassSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    AboutRow("项目", "JustFrame · v0.2 dev")
                    AboutRow("仓库", "github.com/SeanyuanShuai/filmframe")
                    AboutRow("字体", "Cormorant Garamond · DM Serif Display · Inter (SIL OFL)")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "所有改动自动保存。",
                color = GlassColors.OnSurfaceFaint,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun QualityRow(
    quality: ExportQuality,
    selected: Boolean,
    onClick: () -> Unit,
    onInfoClick: (() -> Unit)? = null,
) {
    val bg = if (selected) GlassColors.AccentSoft else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    quality.displayName,
                    color = if (selected) GlassColors.Accent else GlassColors.OnSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
                onInfoClick?.let { info ->
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(20.dp)
                            .width(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(0.8.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .clickable(onClick = info),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "?",
                            color = GlassColors.OnSurfaceMuted,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Text(
                quality.subtitle,
                color = GlassColors.OnSurfaceFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(
            modifier = Modifier
                .height(18.dp)
                .width(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(
                    if (selected) 5.dp else 1.dp,
                    if (selected) GlassColors.Accent else Color.White.copy(alpha = 0.25f),
                    RoundedCornerShape(9.dp),
                )
                .background(if (selected) GlassColors.Accent else Color.Transparent),
        )
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
                PositionTile(left, selected == left, modifier = Modifier.weight(1f)) { onSelect(left) }
                PositionTile(right, selected == right, modifier = Modifier.weight(1f)) { onSelect(right) }
            }
        }
    }
}

@Composable
private fun PositionTile(
    position: WatermarkPosition,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val border = if (selected) GlassColors.Accent else Color.White.copy(alpha = 0.12f)
    val bg = if (selected) GlassColors.AccentSoft else Color.White.copy(alpha = 0.04f)
    Box(
        modifier = modifier
            .height(70.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(
                if (selected) 1.5.dp else 0.5.dp,
                border,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick),
    ) {
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
