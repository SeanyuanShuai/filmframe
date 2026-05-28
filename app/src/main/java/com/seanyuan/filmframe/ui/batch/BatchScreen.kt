package com.seanyuan.filmframe.ui.batch

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.seanyuan.filmframe.data.ExportQuality
import com.seanyuan.filmframe.data.ImageExporter
import com.seanyuan.filmframe.data.Settings
import com.seanyuan.filmframe.data.WatermarkSettings
import com.seanyuan.filmframe.frame.FrameProcessor
import com.seanyuan.filmframe.frame.FrameRenderer
import com.seanyuan.filmframe.frame.FrameTemplate
import com.seanyuan.filmframe.frame.ProcessedSource
import com.seanyuan.filmframe.ui.common.ProcessingOverlay
import com.seanyuan.filmframe.ui.glass.GlassButton
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.glass.GlassSurface
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class BatchItem(
    val uri: Uri,
    val processed: ProcessedSource? = null,
    val templatePreviews: Map<String, Bitmap> = emptyMap(),
    val selectedTemplateId: String = "classic",
    val stripFrame: Boolean = false,
    val exported: Boolean = false,
)

@Composable
fun BatchScreen(uris: List<Uri>, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val watermark by Settings.watermark(context).collectAsState(initial = WatermarkSettings.Default)
    val lastTemplateId by Settings.lastTemplateId(context).collectAsState(initial = "classic")
    val exportQuality by Settings.exportQuality(context).collectAsState(initial = ExportQuality.Original)
    val autoRemoveExistingFrame by Settings.autoRemoveExistingFrame(context).collectAsState(initial = true)

    val items = remember(uris) {
        mutableStateListOf<BatchItem>().apply {
            addAll(uris.map { BatchItem(it, selectedTemplateId = lastTemplateId) })
        }
    }
    var loadingDone by remember { mutableIntStateOf(0) }
    var exportProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var resultMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uris) {
        // 360px source → ~410px output → ~0.65MB per ARGB bitmap.
        // 5 templates × 30 images = ~100 MB peak.
        val previewSourceDim = 360
        uris.forEachIndexed { idx, uri ->
            try {
                withContext(Dispatchers.IO) {
                    val processed = FrameProcessor.loadAndAnalyze(
                        context, uri, targetMaxDim = previewSourceDim,
                    ) ?: return@withContext
                    val previews = FrameRenderer.all.associate { template ->
                        template.id to FrameProcessor.render(
                            context, processed, template,
                            stripExistingFrame = processed.detection.hasFrame,
                            watermark = watermark,
                        )
                    }
                    items[idx] = items[idx].copy(
                        processed = processed,
                        templatePreviews = previews,
                        stripFrame = processed.detection.hasFrame && autoRemoveExistingFrame,
                    )
                    loadingDone++
                }
            } catch (oom: OutOfMemoryError) {
                loadingDone++
            }
        }
    }

    val pagerState = rememberPagerState(pageCount = { items.size })

    BackHandler { onBack() }
    val currentIndex by remember {
        derivedStateOf { pagerState.currentPage.coerceAtMost(items.lastIndex.coerceAtLeast(0)) }
    }

    Box(modifier = modifier.fillMaxSize().background(GlassColors.DeepBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = "批处理 · ${uris.size} 张",
                subtitle = if (loadingDone < uris.size) {
                    "生成预览 $loadingDone / ${uris.size}"
                } else {
                    val item = items.getOrNull(currentIndex)
                    val templateName = FrameRenderer.byId(item?.selectedTemplateId ?: "classic").displayName
                    "${currentIndex + 1} / ${uris.size} · $templateName"
                },
                onBack = onBack,
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 36.dp),
                pageSpacing = 12.dp,
                pageSize = PageSize.Fill,
            ) { page ->
                val item = items.getOrNull(page) ?: return@HorizontalPager
                PhotoCard(
                    item = item,
                    pageOffset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                        .absoluteValue
                        .coerceIn(0f, 1f),
                )
            }

            PageIndicator(
                count = items.size,
                current = currentIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            )

            items.getOrNull(currentIndex)?.let { item ->
                TemplateStrip(
                    item = item,
                    onSelect = { templateId ->
                        items[currentIndex] = item.copy(selectedTemplateId = templateId)
                    },
                )
            }

            BottomActionBar(
                ready = loadingDone == uris.size,
                onApplyAll = { templateId ->
                    for (i in items.indices) items[i] = items[i].copy(selectedTemplateId = templateId)
                },
                onExportAll = {
                    scope.launch {
                        exportProgress = 0 to items.size
                        var successCount = 0
                        items.forEachIndexed { i, item ->
                            val ok = withContext(Dispatchers.IO) {
                                val full = FrameProcessor.loadFullForExport(
                                    context, item.uri, exportQuality.maxLongEdge,
                                ) ?: return@withContext false
                                val template = FrameRenderer.byId(item.selectedTemplateId)
                                val rendered = FrameProcessor.render(
                                    context, full, template, item.stripFrame,
                                    watermark = watermark,
                                )
                                ImageExporter.saveToGallery(context, rendered, item.uri, full.loaded, exportQuality) != null
                            }
                            if (ok) successCount++
                            exportProgress = (i + 1) to items.size
                            items[i] = items[i].copy(exported = ok)
                        }
                        exportProgress = null
                        resultMsg = "✓ $successCount / ${items.size} 张已保存到 Pictures/FilmFrame"
                    }
                },
            )
        }

        exportProgress?.let {
            ProcessingOverlay(
                visible = true,
                title = "批量导出中",
                subtitle = "正在写入 Pictures/FilmFrame",
                progress = it,
            )
        }

        resultMsg?.let { msg ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { resultMsg = null },
                title = { Text("批处理完成", color = GlassColors.OnSurface) },
                text = { Text(msg, color = GlassColors.OnSurfaceMuted) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        resultMsg = null
                        onBack()
                    }) { Text("返回首页") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { resultMsg = null }) {
                        Text("留下继续看")
                    }
                },
                containerColor = Color(0xFF1A1A1A),
            )
        }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("← 返回", color = GlassColors.OnSurface, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = GlassColors.OnSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(it, color = GlassColors.OnSurfaceFaint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PhotoCard(item: BatchItem, pageOffset: Float) {
    val scale = lerp(0.92f, 1f, 1f - pageOffset)
    val alpha = lerp(0.45f, 1f, 1f - pageOffset)
    val elevation = (4f + 18f * (1f - pageOffset)).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .shadow(elevation, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0E0E0E)),
        contentAlignment = Alignment.Center,
    ) {
        val preview = item.templatePreviews[item.selectedTemplateId]
        if (preview == null) {
            CircularProgressIndicator(color = GlassColors.Accent, strokeWidth = 2.dp)
        } else {
            Crossfade(
                targetState = preview,
                animationSpec = tween(durationMillis = 240),
                label = "preview-${item.uri}",
            ) { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            }
        }
        if (item.stripFrame) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassColors.AccentSoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "原图边框 · 已移除",
                    color = GlassColors.Accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (item.exported) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x335BBF8F))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "✓ 已保存",
                    color = Color(0xFF5BBF8F),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count <= 1) {
        Spacer(modifier.height(8.dp))
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count.coerceAtMost(12)) {
            val isCurrent = i == current
            val dotW by animateDpAsState(
                targetValue = if (isCurrent) 20.dp else 6.dp,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dotW",
            )
            val dotColor by animateColorAsState(
                targetValue = if (isCurrent) GlassColors.Accent else Color.White.copy(alpha = 0.2f),
                animationSpec = tween(220),
                label = "dotC",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(dotW)
                    .clip(RoundedCornerShape(3.dp))
                    .background(dotColor),
            )
        }
        if (count > 12) {
            Spacer(Modifier.width(6.dp))
            Text(
                "+${count - 12}",
                color = GlassColors.OnSurfaceFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TemplateStrip(
    item: BatchItem,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        items(FrameRenderer.all) { template ->
            PolaroidTile(
                template = template,
                preview = item.templatePreviews[template.id],
                selected = template.id == item.selectedTemplateId,
                onClick = { onSelect(template.id) },
            )
        }
    }
}

/**
 * Skeuomorphic polaroid-style mini print — paper-white border, soft drop
 * shadow, the rendered photo inside, a thin caption below. Tilts slightly
 * out of selection like a print resting on a table; springs flat + glows
 * when chosen.
 */
@Composable
private fun PolaroidTile(
    template: FrameTemplate,
    preview: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )
    val rotation by animateFloatAsState(
        targetValue = if (selected) 0f else -1.2f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rot",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) GlassColors.Accent else Color(0xFFE9E5DC),
        animationSpec = tween(220),
        label = "border",
    )
    val elevation by animateDpAsState(
        targetValue = if (selected) 12.dp else 4.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "elev",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(110.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            },
    ) {
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 130.dp)
                .shadow(elevation, RoundedCornerShape(6.dp), clip = false)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFAF8F2)) // paper white
                .border(if (selected) 1.5.dp else 0.5.dp, borderColor, RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(top = 6.dp, start = 6.dp, end = 6.dp, bottom = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = template.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Polaroid caption (sits in the paper margin)
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    template.displayName,
                    color = Color(0xFF3A3530),
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 0.dp),
                )
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    ready: Boolean,
    onApplyAll: (String) -> Unit,
    onExportAll: () -> Unit,
) {
    var showApplyMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        if (showApplyMenu) {
            GlassSurface(modifier = Modifier.fillMaxWidth(), intensity = 1.3f) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "对所有图应用一个模板",
                        color = GlassColors.OnSurfaceMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                    FrameRenderer.all.forEach { template ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onApplyAll(template.id)
                                    showApplyMenu = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                template.displayName,
                                color = GlassColors.OnSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "全部应用",
                                color = GlassColors.Accent,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = if (showApplyMenu) "收起" else "全部应用…",
                onClick = { showApplyMenu = !showApplyMenu },
                enabled = ready,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = if (ready) "导出全部" else "加载中…",
                accent = true,
                onClick = onExportAll,
                enabled = ready,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}
