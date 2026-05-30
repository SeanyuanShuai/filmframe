package com.seanyuan.filmframe.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seanyuan.filmframe.data.ExportQuality
import com.seanyuan.filmframe.data.Settings
import com.seanyuan.filmframe.data.WatermarkPosition
import com.seanyuan.filmframe.data.WatermarkSettings
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val QUALITY_LABELS = listOf("低清", "普清", "高清", "原画")
private val QUALITY_BY_INDEX = listOf(
    ExportQuality.Low, ExportQuality.Medium, ExportQuality.High, ExportQuality.Original,
)

private fun ExportQuality.sliderIndex(): Int = QUALITY_BY_INDEX.indexOf(this).coerceAtLeast(0)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val watermark by Settings.watermark(context).collectAsState(initial = WatermarkSettings.Default)
    val exportQuality by Settings.exportQuality(context).collectAsState(initial = ExportQuality.Original)
    val autoRemoveFrame by Settings.autoRemoveExistingFrame(context).collectAsState(initial = true)
    val rememberState by Settings.rememberState(context).collectAsState(initial = true)
    val preserveExif by Settings.preserveExif(context).collectAsState(initial = true)

    var draftWatermark by remember(watermark) { mutableStateOf(watermark) }
    LaunchedEffect(draftWatermark) {
        if (draftWatermark == watermark) return@LaunchedEffect
        delay(300)
        Settings.updateWatermark(context, draftWatermark.copy(text = draftWatermark.text.trim()))
    }

    var showTextDialog by remember { mutableStateOf(false) }
    var showPositionDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlassColors.DeepBackground)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        Text(
            "设置",
            color = GlassColors.OnSurface,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 12.dp),
        )

        Section("导出与画质") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Image, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("导出画质", color = GlassColors.OnSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(exportQuality.displayName, color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
            }
            QualitySlider(
                index = exportQuality.sliderIndex(),
                onIndex = { scope.launch { Settings.updateExportQuality(context, QUALITY_BY_INDEX[it]) } },
                modifier = Modifier.padding(start = 30.dp, end = 30.dp, top = 8.dp, bottom = 40.dp),
            )
        }

        Section("智能检测") {
            ToggleRow(
                icon = Icons.Rounded.Tune,
                label = "自动移除原图边框",
                checked = autoRemoveFrame,
                onCheckedChange = { scope.launch { Settings.updateAutoRemoveExistingFrame(context, it) } },
            )
        }

        Section("水印与 EXIF") {
            ToggleRow(
                icon = Icons.Rounded.TextFields,
                label = "自定义水印",
                checked = draftWatermark.enabled,
                onCheckedChange = { draftWatermark = draftWatermark.copy(enabled = it) },
            )
            if (draftWatermark.enabled) {
                NavRow(
                    icon = Icons.Rounded.ChevronRight,
                    label = "水印内容",
                    value = draftWatermark.text.ifBlank { "点击编辑" },
                    onClick = { showTextDialog = true },
                )
                NavRow(
                    icon = Icons.Rounded.ChevronRight,
                    label = "水印位置",
                    value = draftWatermark.position.displayName,
                    onClick = { showPositionDialog = true },
                )
            }
            ToggleRow(
                icon = Icons.Rounded.History,
                label = "完整保留 EXIF 数据",
                checked = preserveExif,
                onCheckedChange = { scope.launch { Settings.updatePreserveExif(context, it) } },
            )
        }

        Section("偏好") {
            ToggleRow(
                icon = Icons.Rounded.History,
                label = "状态记忆",
                checked = rememberState,
                onCheckedChange = { scope.launch { Settings.updateRememberState(context, it) } },
            )
        }

        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("JustFrame v0.2", color = Color.White.copy(alpha = 0.2f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(120.dp).navigationBarsPadding())
    }

    if (showTextDialog) {
        WatermarkTextDialog(
            initial = draftWatermark.text,
            onConfirm = { draftWatermark = draftWatermark.copy(text = it); showTextDialog = false },
            onDismiss = { showTextDialog = false },
        )
    }
    if (showPositionDialog) {
        PositionDialog(
            selected = draftWatermark.position,
            onSelect = { draftWatermark = draftWatermark.copy(position = it); showPositionDialog = false },
            onDismiss = { showPositionDialog = false },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            title,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.05f),
                        1f to Color.White.copy(alpha = 0.05f),
                    ),
                    shape = RoundedCornerShape(0.dp),
                )
                .padding(horizontal = 24.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { haptics.light(); onCheckedChange(!checked) }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = GlassColors.OnSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
        OrangeToggle(checked = checked)
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    label: String,
    value: String?,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { haptics.tick(); onClick() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = GlassColors.OnSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
        value?.let {
            Text(it, color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
    }
}

/** Orange gradient pill toggle, white knob — matches the v4.2 design. */
@Composable
private fun OrangeToggle(checked: Boolean) {
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = spring(),
        label = "knob",
    )
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (checked) {
                    Brush.linearGradient(listOf(GlassColors.Accent, GlassColors.AccentDeep))
                } else {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f)))
                }
            )
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White),
        )
    }
}

@Composable
private fun QualitySlider(index: Int, onIndex: (Int) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val haptics = rememberHaptics()
    var widthPx by remember { mutableStateOf(1) }
    val count = QUALITY_LABELS.size

    val trackY = 9.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(count) {
                // Local to the gesture loop so it survives across drag events
                // without depending on the recomposing `index` prop.
                var lastIndex = -1
                fun pick(x: Float) {
                    val pct = (x / widthPx).coerceIn(0f, 1f)
                    val next = Math.round(pct * (count - 1))
                    if (next != lastIndex) {
                        lastIndex = next
                        haptics.tick()
                        onIndex(next)
                    }
                }
                // onDragStart fires on touch-down, so a plain tap on a node sets
                // it too — no separate tap detector needed.
                detectHorizontalDragGestures(
                    onDragStart = { pick(it.x) },
                ) { change, _ -> pick(change.position.x) }
            },
    ) {
        // Track
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(y = trackY)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color.White.copy(alpha = 0.1f)),
        )
        // Active fill
        val fillFraction = if (count > 1) index.toFloat() / (count - 1) else 0f
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(y = trackY)
                .fillMaxWidth(fillFraction)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(GlassColors.Accent),
        )
        // Nodes + labels — each a fixed-width column centered on its node x, so
        // the circle stays round (requiredSize ignores the thin track height)
        // and the label sits centered beneath it.
        QUALITY_LABELS.forEachIndexed { i, label ->
            val selected = i == index
            val nodeFraction = if (count > 1) i.toFloat() / (count - 1) else 0f
            val xDp = with(density) { (widthPx * nodeFraction).toDp() }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(56.dp)
                    .offset(x = xDp - 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .requiredSize(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else Color(0xFF1A1A1A))
                        .border(
                            width = if (selected) 2.dp else 3.dp,
                            color = if (selected) GlassColors.Accent else Color(0xFF333333),
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    label,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun WatermarkTextDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("水印内容", color = GlassColors.OnSurface) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("例：© Sean Yuan", color = GlassColors.OnSurfaceFaint) },
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
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("确定", color = GlassColors.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = GlassColors.OnSurfaceMuted) } },
        containerColor = Color(0xFF1A1A1A),
    )
}

@Composable
private fun PositionDialog(
    selected: WatermarkPosition,
    onSelect: (WatermarkPosition) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("水印位置", color = GlassColors.OnSurface) },
        text = {
            Column {
                WatermarkPosition.entries.forEach { pos ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(pos) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(pos.displayName, color = GlassColors.OnSurface, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (pos == selected) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(GlassColors.Accent))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭", color = GlassColors.Accent) } },
        containerColor = Color(0xFF1A1A1A),
    )
}
