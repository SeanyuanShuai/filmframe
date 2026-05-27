package com.seanyuan.filmframe.ui.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.seanyuan.filmframe.data.BitmapLoader
import com.seanyuan.filmframe.data.ExifReader
import com.seanyuan.filmframe.data.ImageExporter
import com.seanyuan.filmframe.data.PhotoExif
import com.seanyuan.filmframe.data.Settings
import com.seanyuan.filmframe.data.WatermarkSettings
import com.seanyuan.filmframe.frame.FrameDetectionResult
import com.seanyuan.filmframe.frame.FrameDetector
import com.seanyuan.filmframe.frame.FrameRenderer
import com.seanyuan.filmframe.frame.FrameTemplate
import com.seanyuan.filmframe.frame.ProcessedSource
import com.seanyuan.filmframe.frame.FrameProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onBatch: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val watermark by Settings.watermark(context)
        .collectAsState(initial = WatermarkSettings.Default)
    val lastTemplateId by Settings.lastTemplateId(context)
        .collectAsState(initial = "classic")

    var showSettings by remember { mutableStateOf(false) }

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exif by remember { mutableStateOf<PhotoExif?>(null) }
    var frameResult by remember { mutableStateOf<FrameDetectionResult?>(null) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var pendingTemplate by remember { mutableStateOf<FrameTemplate?>(null) }
    var currentTemplate by remember { mutableStateOf<FrameTemplate?>(null) }
    var stripFrameChoice by remember { mutableStateOf(false) }
    var rendering by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            exif = ExifReader.read(context, uri)
            frameResult = null
            sourceBitmap = null
            rendered = null
            currentTemplate = null
        }
    }

    val multiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) onBatch(uris)
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            BitmapLoader.loadForAnalysis(context, uri, targetMaxDim = 1600)
        }
        sourceBitmap = bmp
        frameResult = bmp?.let { withContext(Dispatchers.Default) { FrameDetector.detect(it) } }
    }

    // Re-render preview when watermark changes
    LaunchedEffect(watermark, currentTemplate) {
        val template = currentTemplate ?: return@LaunchedEffect
        val src = sourceBitmap ?: return@LaunchedEffect
        val detected = frameResult
        rendering = true
        val out = withContext(Dispatchers.Default) {
            val processed = ProcessedSource(
                source = src,
                exif = exif ?: PhotoExif(),
                detection = detected ?: FrameDetectionResult(false, com.seanyuan.filmframe.frame.FrameInsets(0,0,0,0), 0, 0f),
            )
            FrameProcessor.render(
                context = context,
                processed = processed,
                template = template,
                stripExistingFrame = stripFrameChoice,
                watermark = watermark,
            )
        }
        rendered = out
        rendering = false
    }

    fun renderPreview(template: FrameTemplate, stripFrame: Boolean) {
        val src = sourceBitmap ?: return
        val detected = frameResult
        rendering = true
        currentTemplate = template
        stripFrameChoice = stripFrame
        scope.launch { Settings.updateLastTemplate(context, template.id) }
        scope.launch {
            val out = withContext(Dispatchers.Default) {
                val processed = ProcessedSource(
                    source = src,
                    exif = exif ?: PhotoExif(),
                    detection = detected ?: FrameDetectionResult(false, com.seanyuan.filmframe.frame.FrameInsets(0,0,0,0), 0, 0f),
                )
                FrameProcessor.render(
                    context = context,
                    processed = processed,
                    template = template,
                    stripExistingFrame = stripFrame,
                    watermark = watermark,
                )
            }
            rendered = out
            rendering = false
        }
    }

    fun onTemplateTap(template: FrameTemplate) {
        if (frameResult?.hasFrame == true && currentTemplate == null) {
            pendingTemplate = template
        } else {
            renderPreview(template, stripFrame = stripFrameChoice && frameResult?.hasFrame == true)
        }
    }

    fun exportFullRes() {
        val uri = selectedUri ?: return
        val template = currentTemplate ?: return
        val stripFrame = stripFrameChoice
        val currentWatermark = watermark
        exporting = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val full = FrameProcessor.loadFullForExport(context, uri) ?: return@withContext null
                val out = FrameProcessor.render(
                    context = context,
                    processed = full,
                    template = template,
                    stripExistingFrame = stripFrame,
                    watermark = currentWatermark,
                )
                ImageExporter.saveToGallery(context, out, uri, full.loaded)
            }
            exporting = false
            val msg = when {
                saved == null -> "导出失败，看 logcat"
                saved.downsampled -> "已保存（实际 ${saved.outputWidth}px · 原图 ${saved.originalWidth}px，内存所限略缩）"
                else -> "已保存到 Pictures/FilmFrame · ${saved.outputFormat} · ${saved.outputWidth}×${saved.outputHeight}"
            }
            snackbar.showSnackbar(msg)
        }
    }

    if (showSettings) {
        SettingsDialog(
            initial = watermark,
            onDismiss = { showSettings = false },
            onSave = { newValue ->
                showSettings = false
                scope.launch { Settings.updateWatermark(context, newValue) }
            },
        )
    }

    pendingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingTemplate = null },
            title = { Text("照片已带边框") },
            text = {
                Text("识别到这张照片已经有一圈现成边框（比如 OPPO HASSELBLAD、小米 Leica）。是否先移除再加 FilmFrame 自己的边框？")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingTemplate = null
                    renderPreview(template, stripFrame = true)
                }) { Text("移除并重新加") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingTemplate = null
                    renderPreview(template, stripFrame = false)
                }) { Text("保留直接套") }
            },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            TopBar(
                watermarkActive = watermark.active,
                onSettings = { showSettings = true },
            )

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val renderedBmp = rendered
                val uri = selectedUri
                when {
                    renderedBmp != null -> Image(
                        bitmap = renderedBmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    uri != null -> AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Text(
                        text = "FilmFrame",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }

            if (selectedUri != null && rendered == null) {
                frameResult?.let { FrameDetectionBanner(it) }
            }

            if (selectedUri != null) {
                TemplateChipRow(
                    templates = FrameRenderer.all,
                    selected = currentTemplate?.id,
                    enabled = !rendering && sourceBitmap != null,
                    onSelect = ::onTemplateTap,
                )
            }

            BottomActions(
                hasImage = selectedUri != null,
                hasRendered = rendered != null,
                rendering = rendering,
                exporting = exporting,
                onPick = {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickMulti = {
                    multiLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onBackToOriginal = { rendered = null; currentTemplate = null },
                onExport = { exportFullRes() },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        ) { data -> Snackbar(snackbarData = data) }
    }
}

@Composable
private fun TopBar(watermarkActive: Boolean, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E0E0E))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "FilmFrame",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (watermarkActive) {
            Text(
                "水印·开",
                color = Color(0xFFD4A24A),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        TextButton(onClick = onSettings) { Text("设置") }
    }
}

@Composable
private fun SettingsDialog(
    initial: WatermarkSettings,
    onDismiss: () -> Unit,
    onSave: (WatermarkSettings) -> Unit,
) {
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var text by remember(initial) { mutableStateOf(initial.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("水印", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("水印文字") },
                        placeholder = { Text("例：© Sean Yuan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "渲染在每张图右下角，字体 Cormorant Italic。",
                        color = Color(0xFF888888),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(WatermarkSettings(enabled, text.trim())) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun TemplateChipRow(
    templates: List<FrameTemplate>,
    selected: String?,
    enabled: Boolean,
    onSelect: (FrameTemplate) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E0E0E))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(templates) { template ->
            TemplateChip(
                template = template,
                selected = template.id == selected,
                enabled = enabled,
                onClick = { onSelect(template) },
            )
        }
    }
}

@Composable
private fun TemplateChip(
    template: FrameTemplate,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) Color(0xFFD4A24A) else Color(0xFF333333)
    val bg = if (selected) Color(0xFF2A1F0F) else Color(0xFF1A1A1A)
    val textColor = if (enabled) Color.White else Color(0xFF666666)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = template.displayName,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun BottomActions(
    hasImage: Boolean,
    hasRendered: Boolean,
    rendering: Boolean,
    exporting: Boolean,
    onPick: () -> Unit,
    onPickMulti: () -> Unit,
    onBackToOriginal: () -> Unit,
    onExport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (!hasImage) {
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text("导入照片")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onPickMulti, modifier = Modifier.fillMaxWidth()) {
                Text("批处理多张")
            }
            return@Column
        }

        Row {
            OutlinedButton(
                onClick = if (hasRendered) onBackToOriginal else onPick,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (hasRendered) "返回原图" else "换一张")
            }
            if (hasRendered) {
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onExport,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (exporting) "导出中…" else "保存到相册")
                }
            }
        }
        if (rendering) {
            Spacer(Modifier.height(8.dp))
            Text(
                "渲染中…",
                color = Color(0xFFAAAAAA),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FrameDetectionBanner(result: FrameDetectionResult) {
    val bg = if (result.hasFrame) Color(0xFF2A1F0F) else Color(0xFF0F1F1A)
    val accent = if (result.hasFrame) Color(0xFFD4A24A) else Color(0xFF5BBF8F)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (result.hasFrame) {
                "已识别到既有边框 · ${if (result.isBottomHeavy) "底部加高" else "等宽"} · ${(result.confidence * 100).toInt()}%"
            } else {
                "未检测到既有边框"
            },
            color = Color.White,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (result.hasFrame) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.dp, Color(0xFF555555), RoundedCornerShape(4.dp))
                    .background(Color(result.frameColor)),
            )
        }
    }
}
