package com.seanyuan.filmframe.ui.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.seanyuan.filmframe.frame.ClassicTemplate
import com.seanyuan.filmframe.frame.FrameDetectionResult
import com.seanyuan.filmframe.frame.FrameDetector
import com.seanyuan.filmframe.frame.FrameRenderer
import com.seanyuan.filmframe.frame.FrameTemplate
import com.seanyuan.filmframe.frame.SolidTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

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

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            BitmapLoader.loadForAnalysis(context, uri, targetMaxDim = 1600)
        }
        sourceBitmap = bmp
        frameResult = bmp?.let { withContext(Dispatchers.Default) { FrameDetector.detect(it) } }
    }

    fun renderPreview(template: FrameTemplate, stripFrame: Boolean) {
        val src = sourceBitmap ?: return
        val detected = frameResult
        rendering = true
        currentTemplate = template
        stripFrameChoice = stripFrame
        scope.launch {
            val out = withContext(Dispatchers.Default) {
                val base = if (stripFrame && detected?.hasFrame == true) {
                    FrameRenderer.deframe(src, detected.insets)
                } else {
                    src
                }
                template.render(base, exif)
            }
            rendered = out
            rendering = false
        }
    }

    fun onTemplateTap(template: FrameTemplate) {
        if (frameResult?.hasFrame == true) {
            pendingTemplate = template
        } else {
            renderPreview(template, stripFrame = false)
        }
    }

    fun exportFullRes() {
        val uri = selectedUri ?: return
        val template = currentTemplate ?: return
        val stripFrame = stripFrameChoice
        exporting = true
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                val fullSrc = BitmapLoader.loadForExport(context, uri) ?: return@withContext null
                val baseSrc = if (stripFrame) {
                    val fullScale = FrameDetector.detect(fullSrc)
                    if (fullScale.hasFrame) {
                        FrameRenderer.deframe(fullSrc, fullScale.insets)
                    } else {
                        fullSrc
                    }
                } else {
                    fullSrc
                }
                val out = template.render(baseSrc, exif)
                ImageExporter.saveToGallery(context, out)
            }
            exporting = false
            snackbar.showSnackbar(
                if (saved != null) "已保存到 相册 / Pictures / FilmFrame" else "导出失败，看 logcat"
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

            if (rendered == null) {
                frameResult?.let { FrameDetectionBanner(it) }
                exif?.let { ExifDebugPanel(it) }
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
                onBackToOriginal = { rendered = null },
                onApply = { template -> onTemplateTap(template) },
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
private fun BottomActions(
    hasImage: Boolean,
    hasRendered: Boolean,
    rendering: Boolean,
    exporting: Boolean,
    onPick: () -> Unit,
    onBackToOriginal: () -> Unit,
    onApply: (FrameTemplate) -> Unit,
    onExport: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (!hasImage) {
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text("导入照片")
            }
            return@Column
        }

        if (hasRendered) {
            Row {
                OutlinedButton(onClick = onBackToOriginal, modifier = Modifier.weight(1f)) {
                    Text("返回原图")
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onExport,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (exporting) "导出中…" else "保存到相册")
                }
            }
            return@Column
        }

        // Has image, not rendered yet — show template options
        Row {
            OutlinedButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                Text("换一张")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                onClick = { onApply(ClassicTemplate()) },
                enabled = !rendering,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (rendering) "渲染中…" else "Classic")
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { onApply(SolidTemplate()) },
                enabled = !rendering,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (rendering) "渲染中…" else "纯色")
            }
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(accent),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (result.hasFrame) "已识别到既有边框" else "未检测到既有边框",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (result.hasFrame) {
                val ins = result.insets
                Text(
                    text = "边距 ${ins.top}·${ins.bottom}·${ins.left}·${ins.right}px · " +
                        if (result.isBottomHeavy) "底部加高（含 EXIF 文字概率高）" else "等宽边框",
                    color = Color(0xFFAAAAAA),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "置信度 ${(result.confidence * 100).toInt()}%",
                    color = Color(0xFF888888),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (result.hasFrame) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(1.dp, Color(0xFF555555), RoundedCornerShape(4.dp))
                    .background(Color(result.frameColor)),
            )
        }
    }
}

@Composable
private fun ExifDebugPanel(exif: PhotoExif) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(12.dp),
    ) {
        ExifRow("机身", formatBody(exif.cameraMake, exif.cameraModel))
        ExifRow("镜头", exif.lensModel)
        ExifRow("焦距", exif.focalLength)
        ExifRow("光圈", exif.aperture)
        ExifRow("快门", exif.shutterSpeed)
        ExifRow("ISO", exif.iso)
    }
}

private fun formatBody(make: String?, model: String?): String? {
    if (make.isNullOrBlank()) return model
    if (model.isNullOrBlank()) return make
    return if (model.startsWith(make, ignoreCase = true)) model else "$make $model"
}

@Composable
private fun ExifRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = Color(0xFF888888),
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "N/A",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
