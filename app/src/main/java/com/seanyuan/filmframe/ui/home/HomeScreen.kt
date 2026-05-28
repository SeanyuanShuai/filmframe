package com.seanyuan.filmframe.ui.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.seanyuan.filmframe.data.ExportQuality
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
import com.seanyuan.filmframe.ui.glass.GlassButton
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.glass.GlassSurface
import com.seanyuan.filmframe.ui.params.TemplateParamsPanel
import com.seanyuan.filmframe.ui.result.ResultSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    initialUri: Uri?,
    onConsumeInitialUri: () -> Unit,
    onRequestPick: () -> Unit,
    onSettings: () -> Unit,
    onResult: (ResultSummary) -> Unit,
    onOpenSavedExhibit: (ResultSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val watermark by Settings.watermark(context).collectAsState(initial = WatermarkSettings.Default)
    val lastTemplateId by Settings.lastTemplateId(context).collectAsState(initial = "classic")
    val exportQuality by Settings.exportQuality(context).collectAsState(initial = ExportQuality.Original)
    val autoRemoveExistingFrame by Settings.autoRemoveExistingFrame(context).collectAsState(initial = true)

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exif by remember { mutableStateOf<PhotoExif?>(null) }
    var frameResult by remember { mutableStateOf<FrameDetectionResult?>(null) }
    var rendered by remember { mutableStateOf<Bitmap?>(null) }
    var currentTemplate by remember { mutableStateOf<FrameTemplate?>(null) }
    var stripFrameChoice by remember { mutableStateOf(false) }
    var adjustments by remember { mutableStateOf(TemplateAdjustments.Default) }
    var showParams by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }

    // System back: close params first → clear editor state → fall through to
    // system (App exits). Without this, back from editor exits the app
    // unexpectedly because there's no nav-back stack for in-screen state.
    BackHandler(enabled = showParams || selectedUri != null) {
        when {
            showParams -> showParams = false
            selectedUri != null -> {
                selectedUri = null
                sourceBitmap = null
                rendered = null
                currentTemplate = null
                stripFrameChoice = false
                adjustments = TemplateAdjustments.Default
                frameResult = null
                exif = null
            }
        }
    }

    LaunchedEffect(initialUri) {
        val u = initialUri ?: return@LaunchedEffect
        selectedUri = u
        exif = ExifReader.read(context, u)
        frameResult = null
        sourceBitmap = null
        rendered = null
        currentTemplate = null
        stripFrameChoice = false
        adjustments = TemplateAdjustments.Default
        showParams = false
        onConsumeInitialUri()
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            BitmapLoader.loadForAnalysis(context, uri, targetMaxDim = 3200)
        }
        sourceBitmap = bmp
        val detection = bmp?.let { withContext(Dispatchers.Default) { FrameDetector.detect(it) } }
        frameResult = detection
        currentTemplate = FrameRenderer.byId(lastTemplateId)
        stripFrameChoice = detection?.hasFrame == true && autoRemoveExistingFrame
    }

    LaunchedEffect(watermark, currentTemplate, adjustments, stripFrameChoice, sourceBitmap) {
        val template = currentTemplate ?: return@LaunchedEffect
        val src = sourceBitmap ?: return@LaunchedEffect
        // Debounce — when user drags a slider, keys mutate at 60Hz and each
        // change cancels the previous LaunchedEffect coroutine. The delay
        // here gives the gesture time to settle so we don't kick off a
        // 50-150 ms bitmap render per drag tick. Cancellation flips to a
        // suspension point at delay(), so in-flight renders abort cheaply.
        delay(80)
        val out = try {
            withContext(Dispatchers.Default) {
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
        } catch (oom: OutOfMemoryError) {
            null
        }
        if (out != null) rendered = out
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
        val q = exportQuality
        processing = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                // BitmapLoader.loadForExport already steps the source cap down
                // on OOM. The output bitmap allocation (~1.15x source area) is
                // a separate OOM surface — wrap the render+save in a retry
                // that halves the cap on failure.
                var cap = q.maxLongEdge
                repeat(3) { attempt ->
                    val full = try {
                        FrameProcessor.loadFullForExport(context, uri, cap)
                    } catch (_: OutOfMemoryError) {
                        null
                    } ?: run {
                        cap = (cap / 2).coerceAtLeast(1024)
                        return@repeat
                    }
                    try {
                        val out = FrameProcessor.render(
                            context = context,
                            processed = full,
                            template = template,
                            stripExistingFrame = strip,
                            watermark = w,
                            adjustments = adj,
                        )
                        return@withContext ImageExporter.saveToGallery(
                            context, out, uri, full.loaded, q,
                        )
                    } catch (_: OutOfMemoryError) {
                        cap = (cap / 2).coerceAtLeast(1024)
                    }
                }
                null
            }
            processing = false
            if (result != null) {
                onResult(
                    ResultSummary(
                        savedUri = result.uri,
                        previewBitmap = rendered,
                        outputFormat = result.outputFormat,
                        outputWidth = result.outputWidth,
                        outputHeight = result.outputHeight,
                        originalWidth = result.originalWidth,
                        originalHeight = result.originalHeight,
                        downsampled = result.downsampled,
                        templateName = template.displayName,
                        quality = q.displayName,
                        sourceUri = uri,
                    )
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(GlassColors.DeepBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(
                watermarkActive = watermark.active,
                onSettings = onSettings,
                editorMode = selectedUri != null,
                canSave = currentTemplate != null,
                onSave = ::exportFullRes,
            )

            Crossfade(
                targetState = selectedUri != null,
                animationSpec = tween(durationMillis = 280),
                modifier = Modifier.weight(1f),
                label = "landing-vs-editor",
            ) { hasImage ->
                if (!hasImage) {
                    LandingPanel(
                        onPick = onRequestPick,
                        onOpenSavedExhibit = onOpenSavedExhibit,
                    )
                } else {
                    EditorPanel(
                        rendered = rendered,
                        selectedUri = selectedUri,
                        frameResult = frameResult,
                        currentTemplate = currentTemplate,
                        stripFrameChoice = stripFrameChoice,
                        onToggleStripFrame = { stripFrameChoice = !stripFrameChoice },
                        onSelectTemplate = ::pickTemplate,
                        onPickAnother = onRequestPick,
                        onToggleParams = { showParams = !showParams },
                        paramsOpen = showParams,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showParams && currentTemplate != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            currentTemplate?.let { template ->
                TemplateParamsPanel(
                    template = template,
                    adjustments = adjustments,
                    onChange = { adjustments = it },
                    onClose = { showParams = false },
                )
            }
        }

        ProcessingOverlay(
            visible = processing,
            title = "导出中",
            subtitle = "渲染并写入 Pictures/JustFrame · ${exportQuality.displayName}",
        )
    }
}

@Composable
private fun HomeTopBar(
    watermarkActive: Boolean,
    onSettings: () -> Unit,
    editorMode: Boolean = false,
    canSave: Boolean = false,
    onSave: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "JustFrame",
            color = GlassColors.OnSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        AnimatedVisibility(
            visible = watermarkActive && !editorMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlassColors.AccentSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "水印·开",
                        color = GlassColors.Accent,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
        }
        if (editorMode) {
            // Save lives in the top-right while editing — keeps the bottom row
            // available for purely editing-context actions (换一张 / 调整).
            GlassButton(
                text = "保存",
                onClick = onSave,
                enabled = canSave,
                accent = true,
                compact = true,
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("设置", color = GlassColors.OnSurface, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LandingPanel(
    onPick: () -> Unit,
    onOpenSavedExhibit: (ResultSummary) -> Unit,
) {
    val context = LocalContext.current
    val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    // Re-check permission on resume so granting via Picker propagates back here.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = context.checkSelfPermission(permission) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val entries = remember { mutableStateOf<List<com.seanyuan.filmframe.data.GalleryEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(granted) {
        if (!granted) {
            loaded = true
            entries.value = emptyList()
            return@LaunchedEffect
        }
        val list = withContext(Dispatchers.IO) {
            com.seanyuan.filmframe.data.MediaGallery.listFilmFrameOutputs(context, limit = 12)
        }
        entries.value = list
        loaded = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackdrop(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Gallery 级的胶卷边框",
                color = GlassColors.OnSurfaceMuted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "本地处理 · EXIF 自动识别",
                color = GlassColors.OnSurfaceFaint,
                style = MaterialTheme.typography.bodySmall,
            )

            Spacer(Modifier.height(20.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !loaded -> Unit
                    !granted -> EmptyExhibitHint(
                        title = "还没有作品",
                        body = "选一张照片，给它一个胶卷感的边框",
                    )
                    entries.value.isEmpty() -> EmptyExhibitHint(
                        title = "还没有作品",
                        body = "你导出的作品会展示在这里，像挂在画廊里",
                    )
                    else -> RecentWorksExhibit(
                        entries = entries.value,
                        onOpen = onOpenSavedExhibit,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            GlassButton(
                text = "选照片",
                accent = true,
                onClick = onPick,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Slow ambient gradient sweep that lives behind the Landing content. Two
 * low-saturation radial blobs drift across the screen at very different
 * periods so the pattern never visibly repeats. Color tokens kept dim enough
 * (~6% alpha) that it reads as "the room has light in it" rather than
 * decoration.
 */
@Composable
private fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ambient")
    val tA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 22_000,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "ambientA",
    )
    val tB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 31_000,
                easing = androidx.compose.animation.core.LinearEasing,
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "ambientB",
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Warm low-sat highlight, drifts diagonally
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF3A2A1A).copy(alpha = 0.32f),
                    Color.Transparent,
                ),
                center = androidx.compose.ui.geometry.Offset(
                    x = w * (0.15f + tA * 0.7f),
                    y = h * (0.2f + tA * 0.3f),
                ),
                radius = w * 0.95f,
            ),
            radius = w * 0.95f,
            center = androidx.compose.ui.geometry.Offset(
                x = w * (0.15f + tA * 0.7f),
                y = h * (0.2f + tA * 0.3f),
            ),
        )
        // Cool deep blue, drifts opposite
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF15202E).copy(alpha = 0.40f),
                    Color.Transparent,
                ),
                center = androidx.compose.ui.geometry.Offset(
                    x = w * (0.85f - tB * 0.6f),
                    y = h * (0.85f - tB * 0.5f),
                ),
                radius = w * 1.05f,
            ),
            radius = w * 1.05f,
            center = androidx.compose.ui.geometry.Offset(
                x = w * (0.85f - tB * 0.6f),
                y = h * (0.85f - tB * 0.5f),
            ),
        )
    }
}

@Composable
private fun EmptyExhibitHint(title: String, body: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                color = GlassColors.OnSurfaceMuted,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                color = GlassColors.OnSurfaceFaint,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RecentWorksExhibit(
    entries: List<com.seanyuan.filmframe.data.GalleryEntry>,
    onOpen: (ResultSummary) -> Unit,
) {
    // Marquee carousel — slow continuous left-to-right drift. We multiply the
    // entries list so the LazyRow has enough content to scroll without the
    // edges ever showing. When the scroll position approaches end-1 list-length
    // we silently snap back to position 0 of the next-cycle to keep it infinite.
    val baseCount = entries.size
    val loopCount = if (baseCount == 0) 0 else 100  // 100 cycles ≈ effectively infinite
    val virtualCount = baseCount * loopCount
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = baseCount * (loopCount / 2),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "最近作品 · $baseCount",
            color = GlassColors.OnSurfaceFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
        )

        // Auto-scroll loop: ~24 px/sec leftward via scrollBy. 60fps frame ≈ 0.4 px.
        LaunchedEffect(listState, baseCount) {
            if (baseCount == 0) return@LaunchedEffect
            val frameMs = 16L
            val pxPerFrame = 0.6f  // tune for taste — gallery slow drift
            while (true) {
                kotlinx.coroutines.delay(frameMs)
                if (!listState.isScrollInProgress) {
                    listState.scrollBy(pxPerFrame)
                }
            }
        }

        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            // userScrollEnabled = true lets users still take over and swipe by hand
        ) {
            items(virtualCount) { vi ->
                val entry = entries[vi % baseCount]
                val aspect = if (entry.width > 0 && entry.height > 0) {
                    entry.width.toFloat() / entry.height
                } else 0.7f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(aspect.coerceIn(0.4f, 2.5f))
                        // No clip — galleries hang paintings with sharp edges, not
                        // rounded poker-cards. Matches Magnum / Aperture aesthetic.
                        .background(Color(0xFF0B0B0D))
                        .clickable {
                            onOpen(
                                ResultSummary(
                                    savedUri = entry.uri,
                                    previewBitmap = null,
                                    outputFormat = "—",
                                    outputWidth = entry.width,
                                    outputHeight = entry.height,
                                    originalWidth = 0,
                                    originalHeight = 0,
                                    downsampled = false,
                                    templateName = "已保存作品",
                                    quality = "—",
                                    sourceUri = null,
                                )
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = entry.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorPanel(
    rendered: Bitmap?,
    selectedUri: Uri?,
    frameResult: FrameDetectionResult?,
    currentTemplate: FrameTemplate?,
    stripFrameChoice: Boolean,
    onToggleStripFrame: () -> Unit,
    onSelectTemplate: (FrameTemplate) -> Unit,
    onPickAnother: () -> Unit,
    onToggleParams: () -> Unit,
    paramsOpen: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Preview area with crossfade between AsyncImage (original) and rendered Bitmap
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxSize(),
                intensity = 0.7f,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = rendered,
                        animationSpec = tween(durationMillis = 260),
                        label = "preview",
                    ) { bmp ->
                        when {
                            bmp != null -> Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                // High filter: trades a few % perf for visibly sharper
                                // downscaling. Default Low (bilinear) leaves the editor
                                // preview looking soft on 3x+ density screens.
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
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
        }

        AnimatedVisibility(
            visible = frameResult?.hasFrame == true,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        ) {
            FrameDetectionBanner(
                stripFrameChoice = stripFrameChoice,
                onToggle = onToggleStripFrame,
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 18.dp),
        ) {
            items(FrameRenderer.all) { template ->
                TemplateChip(
                    template = template,
                    selected = currentTemplate?.id == template.id,
                    onClick = { onSelectTemplate(template) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
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
        }
    }
}

@Composable
private fun FrameDetectionBanner(
    stripFrameChoice: Boolean,
    onToggle: () -> Unit,
) {
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
            text = if (stripFrameChoice) "原图已有边框 · 自动移除" else "原图已有边框 · 保留原状",
            color = GlassColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                text = if (stripFrameChoice) "保留" else "移除",
                color = GlassColors.Accent,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
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
    val borderColor by animateColorAsState(
        targetValue = if (selected) GlassColors.Accent else Color.White.copy(alpha = 0.14f),
        animationSpec = tween(220),
        label = "border",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) GlassColors.AccentSoft else Color.White.copy(alpha = 0.04f),
        animationSpec = tween(220),
        label = "bg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) GlassColors.Accent else GlassColors.OnSurface,
        animationSpec = tween(220),
        label = "text",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 1.4.dp else 0.5.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "borderW",
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
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
