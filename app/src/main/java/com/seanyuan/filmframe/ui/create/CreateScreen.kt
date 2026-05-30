package com.seanyuan.filmframe.ui.create

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.seanyuan.filmframe.data.GalleryEntry
import com.seanyuan.filmframe.data.MediaGallery
import com.seanyuan.filmframe.frame.FrameRenderer
import com.seanyuan.filmframe.ui.TemplateLabels
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.rememberHaptics
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Create tab — the default landing. A horizontal snap carousel of the 5 frame
 * templates acts as a style picker; tapping 导入照片 carries the focused
 * template into the picker → editor flow as a preset.
 *
 * Card backgrounds use the user's most recent photos when read permission is
 * already granted (no prompt here — the picker owns the permission request);
 * otherwise each card falls back to a template-tinted gradient.
 */
@Composable
fun CreateScreen(
    onImport: (templateId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val templates = remember { FrameRenderer.all }

    val pagerState = rememberPagerState(pageCount = { templates.size })
    var activeIndex by remember { mutableStateOf(0) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { activeIndex = it }
    }

    // Recent photos for the card art — only if permission is already granted.
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var recent by remember { mutableStateOf<List<GalleryEntry>>(emptyList()) }
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            recent = withContext(Dispatchers.IO) {
                // Exclude our own exported works so the cards show raw photos,
                // not a frame-within-a-frame.
                val outputIds = MediaGallery.listFilmFrameOutputs(context, limit = 200)
                    .map { it.id }.toHashSet()
                MediaGallery.listImages(context, limit = 60)
                    .filter { it.id !in outputIds }
                    .take(8)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF1A1A1A),
                    1f to Color(0xFF050505),
                )
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text34("创作", Modifier.statusBarsPadding().padding(start = 24.dp, top = 16.dp, bottom = 8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 44.dp),
                pageSpacing = 20.dp,
                pageSize = PageSize.Fill,
                beyondViewportPageCount = 2,
            ) { page ->
                val offset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                    .absoluteValue.coerceIn(0f, 1f)
                val template = templates[page]
                val art = recent.getOrNull(page % recent.size.coerceAtLeast(1))
                TemplateCard(
                    templateId = template.id,
                    art = art?.uri,
                    pageOffset = offset,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 18.dp),
                )
            }

            Spacer(Modifier.height(150.dp)) // clears the import button + floating nav
        }

        ImportButton(
            onClick = { haptics.medium(); onImport(templates[activeIndex].id) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 116.dp),
        )
    }
}

@Composable
private fun Text34(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text,
        color = GlassColors.OnSurface,
        fontSize = 34.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
private fun TemplateCard(
    templateId: String,
    art: android.net.Uri?,
    pageOffset: Float,
    modifier: Modifier = Modifier,
) {
    val scale = lerp(0.9f, 1f, 1f - pageOffset)
    val alpha = lerp(0.4f, 1f, 1f - pageOffset)
    val meta = TemplateLabels.byId[templateId]

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            // 70% of the available height — keeps the card clear of the 导入照片
            // button below it (they used to overlap at full height).
            modifier = Modifier
                .fillMaxHeight(0.7f)
                .aspectRatio(2.5f / 4f, matchHeightConstraintsFirst = true),
        ) {
            // Stacked depth cards behind the main card.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 0.9f; scaleY = 0.9f; translationY = 16f }
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF222222))
                    .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp)),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = 0.95f; scaleY = 0.95f; translationY = 8f }
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF1A1A1A))
                    .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(32.dp)),
            )
            // Main glass-bordered card.
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(32.dp))
                    .padding(3.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(29.dp))
                        .background(Color(0xFF111111)),
                ) {
                    if (art != null) {
                        AsyncImage(
                            model = art,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Template-tinted fallback so the card still reads as that style.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color(meta?.swatch ?: 0xFF333333).copy(alpha = 0.18f),
                                        1f to Color(0xFF0C0C0C),
                                    )
                                ),
                        )
                    }
                    // Bottom scrim for label legibility.
                    Box(
                        Modifier.fillMaxSize().drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.30f),
                                    0.5f to Color.Black.copy(alpha = 0.10f),
                                    1f to Color.Black.copy(alpha = 0.90f),
                                )
                            )
                        },
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp),
                    ) {
                        androidx.compose.material3.Text(
                            meta?.zh ?: templateId,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 34.sp,
                        )
                        androidx.compose.material3.Text(
                            meta?.en ?: "",
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 34.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "import-press")

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(60.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = 0.80f),
                    1f to GlassColors.Accent.copy(alpha = 0.92f),
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(30.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            "导入照片",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
