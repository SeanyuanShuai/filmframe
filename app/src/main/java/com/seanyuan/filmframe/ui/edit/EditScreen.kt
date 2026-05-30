package com.seanyuan.filmframe.ui.edit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seanyuan.filmframe.data.ExportQuality
import com.seanyuan.filmframe.data.ImageExporter
import com.seanyuan.filmframe.data.Settings
import com.seanyuan.filmframe.data.WatermarkSettings
import com.seanyuan.filmframe.frame.FrameProcessor
import com.seanyuan.filmframe.frame.FrameRenderer
import com.seanyuan.filmframe.frame.ProcessedSource
import com.seanyuan.filmframe.frame.TemplateAdjustments
import com.seanyuan.filmframe.ui.TemplateLabels
import com.seanyuan.filmframe.ui.glass.GlassColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class EditTab { Template, Adjust }

/**
 * Unified immersive editor. Replaces the old single-photo editor, batch screen
 * and both result screens. Always a horizontal carousel of the picked photos
 * (1..N) with a per-photo template and a global adjust set; export writes all
 * of them and shows the done overlay.
 */
@Composable
fun EditScreen(
    uris: List<Uri>,
    presetTemplateId: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val watermark by Settings.watermark(context).collectAsState(initial = WatermarkSettings.Default)
    val exportQuality by Settings.exportQuality(context).collectAsState(initial = ExportQuality.Original)
    val autoRemove by Settings.autoRemoveExistingFrame(context).collectAsState(initial = true)

    val count = uris.size
    val templateIds = remember { mutableStateListOf<String>().apply { repeat(count) { add(presetTemplateId) } } }
    val stripFlags = remember { mutableStateListOf<Boolean>().apply { repeat(count) { add(false) } } }
    val sources = remember { mutableStateMapOf<Int, ProcessedSource>() }
    val previews = remember { mutableStateMapOf<Int, Bitmap>() }

    var adjustments by remember { mutableStateOf(TemplateAdjustments.Default) }
    var applyToAll by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(EditTab.Template) }
    var renderEpoch by remember { mutableIntStateOf(0) }

    var exporting by remember { mutableStateOf(false) }
    var exportIndex by remember { mutableIntStateOf(0) }
    var exportDone by remember { mutableStateOf(false) }
    var savedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var toast by remember { mutableStateOf("") }

    val pagerState = rememberPagerState(pageCount = { count })
    var currentPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { currentPage = it }
    }

    fun invalidate(all: Boolean, index: Int = currentPage) {
        if (all) previews.clear() else previews.remove(index)
        renderEpoch++
    }

    fun selectTemplate(id: String) {
        if (applyToAll) {
            for (i in 0 until count) templateIds[i] = id
            invalidate(all = true)
        } else {
            templateIds[currentPage] = id
            invalidate(all = false)
        }
        scope.launch { Settings.updateLastTemplate(context, id) }
    }

    // Load source + EXIF + frame detection for the visible window.
    LaunchedEffect(currentPage, autoRemove) {
        for (i in (currentPage - 1)..(currentPage + 1)) {
            if (i in 0 until count && sources[i] == null) {
                val ps = withContext(Dispatchers.IO) {
                    FrameProcessor.loadAndAnalyze(context, uris[i], targetMaxDim = 1600)
                }
                if (ps != null) {
                    sources[i] = ps
                    stripFlags[i] = ps.detection.hasFrame && autoRemove
                    invalidate(all = false, index = i)
                }
            }
        }
    }

    // Debounced render for the visible window.
    LaunchedEffect(currentPage, renderEpoch) {
        delay(70)
        for (i in (currentPage - 1)..(currentPage + 1)) {
            if (i in 0 until count && previews[i] == null) {
                val ps = sources[i] ?: continue
                val template = FrameRenderer.byId(templateIds[i])
                val out = withContext(Dispatchers.Default) {
                    try {
                        FrameProcessor.render(context, ps, template, stripFlags[i], watermark, adjustments)
                    } catch (_: OutOfMemoryError) {
                        null
                    }
                }
                if (out != null) previews[i] = out
            }
        }
    }

    LaunchedEffect(toast) {
        if (toast.isNotBlank()) {
            delay(2000)
            toast = ""
        }
    }

    fun startExport() {
        if (exporting) return
        exporting = true
        exportIndex = 0
        scope.launch {
            val saved = mutableListOf<Uri>()
            uris.forEachIndexed { i, uri ->
                exportIndex = i
                val res = withContext(Dispatchers.IO) {
                    var cap = exportQuality.maxLongEdge
                    repeat(3) {
                        val full = try {
                            FrameProcessor.loadFullForExport(context, uri, cap)
                        } catch (_: OutOfMemoryError) {
                            null
                        } ?: run { cap = (cap / 2).coerceAtLeast(1024); return@repeat }
                        try {
                            val bmp = FrameProcessor.render(
                                context, full, FrameRenderer.byId(templateIds[i]),
                                stripFlags[i], watermark, adjustments,
                            )
                            return@withContext ImageExporter.saveToGallery(context, bmp, uri, full.loaded, exportQuality)
                        } catch (_: OutOfMemoryError) {
                            cap = (cap / 2).coerceAtLeast(1024)
                        }
                    }
                    null
                }
                if (res != null) saved += res.uri
            }
            savedUris = saved
            exporting = false
            exportDone = true
        }
    }

    BackHandler {
        if (exportDone) exportDone = false else onBack()
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        // Preview carousel
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = 100.dp, bottom = 280.dp),
            pageSpacing = 16.dp,
        ) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val bmp = previews[page]
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(color = GlassColors.Accent, strokeWidth = 2.dp)
                }
            }
        }

        // Toast
        AnimatedVisibility(
            visible = toast.isNotBlank(),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp),
        ) {
            Text(
                toast,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF111111).copy(alpha = 0.92f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        // Top controls
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ChevronLeft, "返回", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassColors.Accent)
                    .clickable { startExport() }
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("导出 ($count)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        // Bottom control sheet
        BottomSheet(
            activeTab = activeTab,
            onTab = { activeTab = it },
            templateIds = templateIds,
            currentPage = currentPage,
            applyToAll = applyToAll,
            onApplyToAll = { applyToAll = it },
            onSelectTemplate = ::selectTemplate,
            adjustments = adjustments,
            onAdjustments = { adjustments = it; invalidate(all = true) },
            stripOn = stripFlags.getOrElse(currentPage) { false },
            hasFrame = sources[currentPage]?.detection?.hasFrame == true,
            onToggleStrip = {
                if (sources[currentPage]?.detection?.hasFrame == true) {
                    stripFlags[currentPage] = !stripFlags[currentPage]
                    invalidate(all = false)
                } else {
                    toast = "未检索到原图边框"
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Overlays
        if (exporting) ExportingOverlay(index = exportIndex + 1, total = count)
        if (exportDone) {
            ExportDoneOverlay(
                count = savedUris.size,
                onShare = { shareImages(context, savedUris) },
                onRemix = { exportDone = false },
                onHome = onHome,
            )
        }
    }
}

@Composable
private fun BottomSheet(
    activeTab: EditTab,
    onTab: (EditTab) -> Unit,
    templateIds: List<String>,
    currentPage: Int,
    applyToAll: Boolean,
    onApplyToAll: (Boolean) -> Unit,
    onSelectTemplate: (String) -> Unit,
    adjustments: TemplateAdjustments,
    onAdjustments: (TemplateAdjustments) -> Unit,
    stripOn: Boolean,
    hasFrame: Boolean,
    onToggleStrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF111111).copy(alpha = 0.96f))
            .border(
                0.8.dp,
                Brush.verticalGradient(0f to Color.White.copy(alpha = 0.1f), 1f to Color.Transparent),
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            )
            .padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(140.dp)) {
            if (activeTab == EditTab.Template) {
                // 一键应用 checkbox
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 24.dp)
                        .clickable { onApplyToAll(!applyToAll) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckBox(checked = applyToAll, size = 14)
                    Spacer(Modifier.width(6.dp))
                    Text("一键应用", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, letterSpacing = 1.sp)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FrameRenderer.all.forEach { template ->
                        SwatchTile(
                            templateId = template.id,
                            selected = templateIds.getOrElse(currentPage) { "classic" } == template.id,
                            onClick = { onSelectTemplate(template.id) },
                        )
                    }
                }
            } else {
                AdjustPanel(
                    adjustments = adjustments,
                    onAdjustments = onAdjustments,
                    stripOn = stripOn,
                    hasFrame = hasFrame,
                    onToggleStrip = onToggleStrip,
                )
            }
        }

        // Bottom tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.1f)),
        ) {}
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            TabLabel("选择边框", activeTab == EditTab.Template) { onTab(EditTab.Template) }
            Spacer(Modifier.width(72.dp))
            TabLabel("调整参数", activeTab == EditTab.Adjust) { onTab(EditTab.Adjust) }
        }
    }
}

@Composable
private fun TabLabel(text: String, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Text(
            text,
            color = if (active) GlassColors.Accent else Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (active) GlassColors.Accent else Color.Transparent),
        )
    }
}

@Composable
private fun SwatchTile(templateId: String, selected: Boolean, onClick: () -> Unit) {
    val meta = TemplateLabels.byId[templateId]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Box(
            Modifier
                .width(48.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(meta?.swatch ?: 0xFFFFFFFF))
                .then(
                    if (selected) Modifier.border(2.dp, GlassColors.Accent, RoundedCornerShape(6.dp))
                    else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            meta?.zh ?: templateId,
            color = if (selected) GlassColors.Accent else Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AdjustPanel(
    adjustments: TemplateAdjustments,
    onAdjustments: (TemplateAdjustments) -> Unit,
    stripOn: Boolean,
    hasFrame: Boolean,
    onToggleStrip: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Knob(
                label = "边框宽度",
                value = adjustments.borderWidthMultiplier,
                onTap = {
                    val next = if (adjustments.borderWidthMultiplier >= 1.5f) 0.7f else adjustments.borderWidthMultiplier + 0.1f
                    onAdjustments(adjustments.copy(borderWidthMultiplier = next))
                },
            )
            StripToggle(stripOn = stripOn && hasFrame, hasFrame = hasFrame, onClick = onToggleStrip)
            Knob(
                label = "字体大小",
                value = adjustments.titleSizeMultiplier,
                onTap = {
                    val next = if (adjustments.titleSizeMultiplier >= 1.5f) 0.7f else adjustments.titleSizeMultiplier + 0.1f
                    onAdjustments(adjustments.copy(titleSizeMultiplier = next))
                },
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onAdjustments(adjustments.copy(showCaption = !adjustments.showCaption)) },
            ) {
                CheckBox(checked = adjustments.showCaption, size = 16)
                Spacer(Modifier.width(8.dp))
                Text("EXIF 文本", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 2.sp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onAdjustments(TemplateAdjustments.Default) },
            ) {
                Icon(Icons.Rounded.Refresh, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("重置", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun Knob(label: String, value: Float, onTap: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF111111))))
                .border(0.8.dp, Color(0xFF444444), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTap,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .rotate(value * 120f)
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f))), CircleShape),
            )
            Text("${"%.1f".format(value)}x", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StripToggle(stripOn: Boolean, hasFrame: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("智能去除边框", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (stripOn) GlassColors.Accent else Color(0xFF111111))
                .border(
                    1.dp,
                    when {
                        stripOn -> GlassColors.Accent
                        hasFrame -> Color(0xFF444444)
                        else -> Color(0xFF333333)
                    },
                    CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                null,
                tint = when {
                    stripOn -> Color.White
                    hasFrame -> Color.White.copy(alpha = 0.4f)
                    else -> Color.White.copy(alpha = 0.2f)
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean, size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) GlassColors.Accent else Color.Transparent)
            .border(1.dp, if (checked) GlassColors.Accent else Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size((size - 4).dp))
    }
}

@Composable
private fun ExportingOverlay(index: Int, total: Int) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = GlassColors.Accent,
                trackColor = Color.White.copy(alpha = 0.1f),
                strokeWidth = 4.dp,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(32.dp))
            Text("处理中", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            Text("正在渲染无损画质…", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text("正在写入 EXIF 数据 ($index/$total)", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun ExportDoneOverlay(count: Int, onShare: () -> Unit, onRemix: () -> Unit, onHome: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF050505)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFF4CAF50).copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(48.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text("导出完成", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("$count 张照片已无损保存至系统相册", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
            Spacer(Modifier.height(48.dp))

            ActionButton(
                text = "分享作品",
                icon = Icons.Rounded.IosShare,
                bg = Color.White,
                fg = Color.Black,
                onClick = onShare,
            )
            Spacer(Modifier.height(16.dp))
            ActionButton(
                text = "换个模板重做",
                icon = Icons.Rounded.Refresh,
                bg = Color(0xFF1A1A1A),
                fg = Color.White,
                border = true,
                onClick = onRemix,
            )
            Spacer(Modifier.height(16.dp))
            ActionButton(
                text = "返回主页",
                icon = Icons.Rounded.Home,
                bg = Color.Transparent,
                fg = Color.White.copy(alpha = 0.6f),
                onClick = onHome,
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bg: Color,
    fg: Color,
    border: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .then(if (border) Modifier.border(0.8.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

private fun shareImages(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uris.first())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    context.startActivity(Intent.createChooser(intent, "分享到").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
