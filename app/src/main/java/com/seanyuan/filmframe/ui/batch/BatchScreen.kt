package com.seanyuan.filmframe.ui.batch

import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    val items = remember(uris) {
        mutableStateListOf<BatchItem>().apply {
            addAll(uris.map { BatchItem(it, selectedTemplateId = lastTemplateId) })
        }
    }
    var loadingDone by remember { mutableIntStateOf(0) }
    var exportProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var resultMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uris) {
        uris.forEachIndexed { idx, uri ->
            withContext(Dispatchers.IO) {
                val processed = FrameProcessor.loadAndAnalyze(context, uri, targetMaxDim = 900)
                    ?: return@withContext
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
                    stripFrame = processed.detection.hasFrame,
                )
                loadingDone++
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(GlassColors.DeepBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = "批处理 · ${uris.size} 张",
                subtitle = if (loadingDone < uris.size) "生成预览 $loadingDone / ${uris.size}" else null,
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(items, key = { _, it -> it.uri.toString() }) { index, item ->
                    BatchItemCard(
                        index = index + 1,
                        item = item,
                        onSelectTemplate = { templateId ->
                            items[index] = item.copy(selectedTemplateId = templateId)
                        },
                    )
                }
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
                                val full = FrameProcessor.loadFullForExport(context, item.uri)
                                    ?: return@withContext false
                                val template = FrameRenderer.all.first { it.id == item.selectedTemplateId }
                                val rendered = FrameProcessor.render(
                                    context, full, template, item.stripFrame,
                                    watermark = watermark,
                                )
                                ImageExporter.saveToGallery(context, rendered, item.uri, full.loaded) != null
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
            ProcessingOverlay(title = "批量导出中", subtitle = "正在写入 Pictures/FilmFrame", progress = it)
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
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
private fun BatchItemCard(
    index: Int,
    item: BatchItem,
    onSelectTemplate: (String) -> Unit,
) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#$index",
                    color = GlassColors.OnSurfaceFaint,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(10.dp))
                if (item.stripFrame) {
                    Text(
                        "已识别既有边框 · 先移除",
                        color = GlassColors.Accent,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (item.exported) {
                    Text("✓ 已保存", color = Color(0xFF5BBF8F), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(10.dp))

            if (item.templatePreviews.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = GlassColors.Accent, strokeWidth = 2.dp)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(FrameRenderer.all) { template ->
                        TemplatePreviewTile(
                            template = template,
                            preview = item.templatePreviews[template.id],
                            selected = template.id == item.selectedTemplateId,
                            onClick = { onSelectTemplate(template.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplatePreviewTile(
    template: FrameTemplate,
    preview: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) GlassColors.Accent else Color.White.copy(alpha = 0.12f)
    val borderWidth = if (selected) 1.5.dp else 0.5.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(120.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(borderWidth, border, RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0A0A))
                .clickable(onClick = onClick),
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
        }
        Spacer(Modifier.height(6.dp))
        Text(
            template.displayName,
            color = if (selected) GlassColors.Accent else GlassColors.OnSurfaceMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BottomActionBar(
    ready: Boolean,
    onApplyAll: (String) -> Unit,
    onExportAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FrameRenderer.all) { template ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .clickable(enabled = ready) { onApplyAll(template.id) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "全部 ${template.displayName}",
                        color = if (ready) GlassColors.OnSurface else GlassColors.OnSurfaceFaint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassButton(
            text = if (ready) "导出全部" else "加载中…",
            accent = true,
            onClick = onExportAll,
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
