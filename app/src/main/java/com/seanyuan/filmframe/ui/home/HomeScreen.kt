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
import androidx.compose.material3.MaterialTheme
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
import com.seanyuan.filmframe.frame.FrameInsets
import com.seanyuan.filmframe.frame.FrameProcessor
import com.seanyuan.filmframe.frame.FrameRenderer
import com.seanyuan.filmframe.frame.FrameTemplate
import com.seanyuan.filmframe.frame.ProcessedSource
import com.seanyuan.filmframe.frame.TemplateAdjustments
import com.seanyuan.filmframe.ui.common.ProcessingOverlay
import com.seanyuan.filmframe.ui.common.ResultDialog
import com.seanyuan.filmframe.ui.common.ResultSummary
import com.seanyuan.filmframe.ui.glass.GlassButton
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.glass.GlassSurface
import com.seanyuan.filmframe.ui.params.TemplateParamsPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onBatch: (List<Uri>) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val watermark by Settings.watermark(context).collectAsState(initial = WatermarkSettings.Default)
    val lastTemplateId by Settings.lastTemplateId(context).collectAsState(initial = "classic")

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exif by remember { mutableStateOf<PhotoExif?>(null) }
    var frameResult by remember { mutableStateOf<FrameDetectionResult?>(null) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var currentTemplate by remember { mutableStateOf<FrameTemplate?>(null) }
    var stripFrameChoice by remember { mutableStateOf(false) }
    var adjustments by remember { mutableStateOf(TemplateAdjustments.Default) }
    var pendingTemplate by remember { mutableStateOf<FrameTemplate?>(null) }
    var showParams by remember { mutableStateOf(false) }
    var processingMsg by remember { mutableStateOf<String?>(null) }
    var resultSummary by remember { mutableStateOf<ResultSummary?>(null) }

    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            exif = ExifReader.read(context, uri)
            frameResult = null
            sourceBitmap = null
            rendered = null
            currentTemplate = null
            stripFrameChoice = false
            adjustments = TemplateAdjustments.Default
            showParams = false
        }
    }

    val multiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris -> if (uris.isNotEmpty()) onBatch(uris) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            BitmapLoader.loadForAnalysis(context, uri, targetMaxDim = 1600)
        }
        sourceBitmap = bmp
        val detection = bmp?.let { withContext(Dispatchers.Default) { FrameDetector.detect(it) } }
        frameResult = detection

        val defaultTemplate = FrameRenderer.byId(lastTemplateId)
        if (detection?.hasFrame == true) {
            pendingTemplate = defaultTemplate
        } else {
            currentTemplate = defaultTemplate
            stripFrameChoice = false
        }
    }

    LaunchedEffect(watermark, currentTemplate, adjustments, stripFrameChoice, sourceBitmap) {
        val template = currentTemplate ?: return@LaunchedEffect
        val src = sourceBitmap ?: return@LaunchedEffect
        val out = withContext(Dispatchers.Default) {
            val processed = ProcessedSource(
                source = src,
                exif = exif ?: PhotoExif(),
                detection = frameResult ?: FrameDetectionResult(false, FrameInsets(0, 0, 0, 0), 0, 0f),
            )
            FrameProcessor.render(
                context = context,
                processed = processed,
                template = template,
                stripExistingFrame = stripFrameChoice,
                watermark = watermark,
                adjustments = adjustments,
            )
        }
        rendered = out
    }

    fun pickTemplate(template: FrameTemplate) {
        currentTemplate = template
        scope.launch { Settings.updateLastTemplate(context, template.id) }
    }

    fun exportFullRes() {
        val uri = selectedUri ?: return
        val template = currentTemplate ?: return
        val w = watermark
        val adj = adjustments
        val strip = stripFrameChoice
        processingMsg = "渲染并写入相册…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val full = FrameProcessor.loadFullForExport(context, uri) ?: return@withContext null
                val out = FrameProcessor.render(
                    context = context,
                    processed = full,
                    template = template,
                    stripExistingFrame = strip,
                    watermark = w,
                    adjustments = adj,
                )
                ImageExporter.saveToGallery(context, out, uri, full.loaded)
            }
            processingMsg = null
            if (result != null) {
                resultSummary = ResultSummary(
                    savedUri = result.uri,
                    previewBitmap = rendered?.asImageBitmap(),
                    outputFormat = result.outputFormat,
                    outputWidth = result.outputWidth,
                    outputHeight = result.outputHeight,
                    originalWidth = result.originalWidth,
                    originalHeight = result.originalHeight,
                    downsampled = result.downsampled,
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(GlassColors.DeepBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(
                watermarkActive = watermark.active,
                onSettings = onSettings,
            )

            if (selectedUri == null) {
                LandingPanel(
                    modifier = Modifier.weight(1f),
                    onPickSingle = {
                        singleLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickMulti = {
                        multiLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                )
            } else {
                EditorPanel(
                    modifier = Modifier.weight(1f),
                    rendered = rendered,
                    selectedUri = selectedUri,
                    frameResult = frameResult,
                    currentTemplate = currentTemplate,
                    onSelectTemplate = ::pickTemplate,
                    onPickAnother = {
                        singleLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onToggleParams = { showParams = !showParams },
                    onExport = ::exportFullRes,
                    paramsOpen = showParams,
                )
            }
        }

        if (showParams && currentTemplate != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                TemplateParamsPanel(
                    template = currentTemplate!!,
                    adjustments = adjustments,
                    onChange = { adjustments = it },
                    onClose = { showParams = false },
                )
            }
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
                        currentTemplate = template
                        stripFrameChoice = true
                        scope.launch { Settings.updateLastTemplate(context, template.id) }
                    }) { Text("移除并重新加") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingTemplate = null
                        currentTemplate = template
                        stripFrameChoice = false
                        scope.launch { Settings.updateLastTemplate(context, template.id) }
                    }) { Text("保留直接套") }
                },
                containerColor = Color(0xFF1A1A1A),
                titleContentColor = GlassColors.OnSurface,
                textContentColor = GlassColors.OnSurfaceMuted,
            )
        }

        processingMsg?.let {
            ProcessingOverlay(title = "导出中", subtitle = it)
        }

        resultSummary?.let { summary ->
            ResultDialog(
                summary = summary,
                onAnother = {
                    resultSummary = null
                    selectedUri = null
                    rendered = null
                    currentTemplate = null
                    singleLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onDone = { resultSummary = null },
            )
        }
    }
}

@Composable
private fun HomeTopBar(watermarkActive: Boolean, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "FilmFrame",
            color = GlassColors.OnSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (watermarkActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GlassColors.AccentSoft)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "水印 · 开",
                    color = GlassColors.Accent,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onSettings)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("设置", color = GlassColors.OnSurface, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LandingPanel(
    modifier: Modifier = Modifier,
    onPickSingle: () -> Unit,
    onPickMulti: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "为你的照片加上",
                color = GlassColors.OnSurfaceMuted,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Gallery 级的边框",
                color = GlassColors.OnSurface,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "本地处理 · EXIF 自动识别 · 5 个内置模板",
                color = GlassColors.OnSurfaceFaint,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(40.dp))
            GlassButton(
                text = "选一张照片",
                accent = true,
                onClick = onPickSingle,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            GlassButton(
                text = "批处理多张",
                onClick = onPickMulti,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EditorPanel(
    modifier: Modifier = Modifier,
    rendered: Bitmap?,
    selectedUri: Uri?,
    frameResult: FrameDetectionResult?,
    currentTemplate: FrameTemplate?,
    onSelectTemplate: (FrameTemplate) -> Unit,
    onPickAnother: () -> Unit,
    onToggleParams: () -> Unit,
    onExport: () -> Unit,
    paramsOpen: Boolean,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Image preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxSize(),
                tonalIntensity = 0.6f,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        rendered != null -> Image(
                            bitmap = rendered.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        selectedUri != null -> AsyncImage(
                            model = selectedUri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        frameResult?.takeIf { it.hasFrame }?.let { fr ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(GlassColors.Accent),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "原图已有边框 · 自动移除 · 置信度 ${(fr.confidence * 100).toInt()}%",
                    color = GlassColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Template chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(FrameRenderer.all) { template ->
                TemplateChip(
                    template = template,
                    selected = currentTemplate?.id == template.id,
                    onClick = { onSelectTemplate(template) },
                )
            }
        }

        // Bottom action row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassButton(
                text = "换一张",
                onClick = onPickAnother,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                text = if (paramsOpen) "收起调整" else "调整",
                onClick = onToggleParams,
                modifier = Modifier.weight(1f),
                enabled = currentTemplate != null,
            )
            GlassButton(
                text = "保存",
                onClick = onExport,
                modifier = Modifier.weight(1f),
                accent = true,
                enabled = currentTemplate != null,
            )
        }
    }
}

@Composable
private fun TemplateChip(
    template: FrameTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) GlassColors.Accent else Color.White.copy(alpha = 0.12f)
    val bg = if (selected) GlassColors.AccentSoft else Color.White.copy(alpha = 0.04f)
    val textColor = if (selected) GlassColors.Accent else GlassColors.OnSurface
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                if (selected) 1.2.dp else 0.5.dp,
                border,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            template.displayName,
            color = textColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
