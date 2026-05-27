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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.seanyuan.filmframe.frame.FrameProcessor
import com.seanyuan.filmframe.frame.FrameRenderer
import com.seanyuan.filmframe.frame.FrameTemplate
import com.seanyuan.filmframe.frame.ProcessedSource
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
    val snackbar = remember { SnackbarHostState() }

    val items = remember(uris) {
        mutableStateListOf<BatchItem>().apply { addAll(uris.map { BatchItem(it) }) }
    }
    var loadingDone by remember { mutableIntStateOf(0) }
    var exportProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(uris) {
        uris.forEachIndexed { idx, uri ->
            withContext(Dispatchers.IO) {
                val processed = FrameProcessor.loadAndAnalyze(context, uri, targetMaxDim = 900)
                    ?: return@withContext
                val previews = FrameRenderer.all.associate { template ->
                    template.id to FrameProcessor.render(
                        context, processed, template,
                        stripExistingFrame = processed.detection.hasFrame,
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

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = "批处理 · ${uris.size} 张",
                subtitle = if (loadingDone < uris.size) "正在生成预览 $loadingDone / ${uris.size}" else null,
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                exportProgress = exportProgress,
                onApplyAll = { templateId ->
                    for (i in items.indices) {
                        items[i] = items[i].copy(selectedTemplateId = templateId)
                    }
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
                                val rendered = FrameProcessor.render(context, full, template, item.stripFrame)
                                ImageExporter.saveToGallery(context, rendered) != null
                            }
                            if (ok) successCount++
                            exportProgress = (i + 1) to items.size
                            items[i] = items[i].copy(exported = ok)
                        }
                        exportProgress = null
                        snackbar.showSnackbar(
                            "批处理完成 · $successCount / ${items.size} 张已保存到 Pictures/FilmFrame/"
                        )
                    }
                },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        ) { Snackbar(snackbarData = it) }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E0E0E))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("← 返回") }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, color = Color(0xFFAAAAAA), style = MaterialTheme.typography.bodySmall)
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#$index",
                color = Color(0xFF888888),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(10.dp))
            if (item.stripFrame) {
                Text(
                    "识别到既有边框 · 将先移除",
                    color = Color(0xFFD4A24A),
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
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
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

@Composable
private fun TemplatePreviewTile(
    template: FrameTemplate,
    preview: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) Color(0xFFD4A24A) else Color(0xFF333333)
    val borderWidth = if (selected) 2.dp else 1.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(120.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0A0A))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = template.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            template.displayName,
            color = if (selected) Color(0xFFD4A24A) else Color(0xFFBBBBBB),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BottomActionBar(
    ready: Boolean,
    exportProgress: Pair<Int, Int>?,
    onApplyAll: (String) -> Unit,
    onExportAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E0E0E))
            .padding(16.dp),
    ) {
        if (exportProgress != null) {
            val (done, total) = exportProgress
            Text(
                "导出中 · $done / $total",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { done.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FrameRenderer.all) { template ->
                OutlinedButton(
                    onClick = { onApplyAll(template.id) },
                    enabled = ready,
                ) {
                    Text("全部 ${template.displayName}")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onExportAll, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text(if (ready) "导出全部" else "加载中…")
        }
    }
}
