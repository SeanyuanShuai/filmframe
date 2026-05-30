package com.seanyuan.filmframe.ui.gallery

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.seanyuan.filmframe.data.ExifReader
import com.seanyuan.filmframe.data.GalleryEntry
import com.seanyuan.filmframe.data.MediaGallery
import com.seanyuan.filmframe.data.PhotoExif
import com.seanyuan.filmframe.ui.glass.GlassColors
import kotlin.math.absoluteValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Gallery tab — a "portfolio" of exported works. Auto-advancing horizontal
 * carousel that pauses while the user is dragging. Pure exhibit: cards are not
 * tappable by design (v4.2 decision).
 */
@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<GalleryEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) {
            MediaGallery.listFilmFrameOutputs(context, limit = 200)
        }
        entries = list
        loaded = true
    }

    Box(modifier = modifier.fillMaxSize().background(GlassColors.DeepBackground)) {
        SpotlightBackdrop(Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize()) {
            Header(count = entries.size)

            if (loaded && entries.isEmpty()) {
                EmptyState(Modifier.weight(1f))
            } else {
                Carousel(
                    entries = entries,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(120.dp)) // clears the floating nav
        }
    }
}

@Composable
private fun Header(count: Int) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(top = 20.dp, bottom = 8.dp),
    ) {
        Text("画廊", color = GlassColors.OnSurface, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("作品集", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            CounterPill(count)
        }
    }
}

@Composable
private fun CounterPill(count: Int) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "dot",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF2A2A2A).copy(alpha = 0.85f),
                    1f to Color(0xFF111111).copy(alpha = 0.85f),
                )
            )
            .border(0.8.dp, Color(0xFF444444), RoundedCornerShape(20.dp))
            .padding(horizontal = 13.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(GlassColors.Accent.copy(alpha = dotAlpha)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$count 张已导出",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
    }
}

@Composable
private fun Carousel(entries: List<GalleryEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) {
        Box(modifier) // placeholder while loading
        return
    }
    val pagerState = rememberPagerState(pageCount = { entries.size })

    // Auto-advance every 3s, paused while the user is dragging.
    LaunchedEffect(pagerState, entries.size) {
        while (true) {
            delay(3000)
            if (!pagerState.isScrollInProgress && entries.size > 1) {
                val next = (pagerState.currentPage + 1) % entries.size
                pagerState.animateScrollToPage(next, animationSpec = tween(900))
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 56.dp),
        pageSpacing = 24.dp,
        pageSize = PageSize.Fill,
        beyondViewportPageCount = 1,
    ) { page ->
        val offset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
            .absoluteValue.coerceIn(0f, 1f)
        WorkCard(entry = entries[page], pageOffset = offset)
    }
}

@Composable
private fun WorkCard(entry: GalleryEntry, pageOffset: Float) {
    val context = LocalContext.current
    val scale = lerp(0.88f, 1f, 1f - pageOffset)
    val alpha = lerp(0.5f, 1f, 1f - pageOffset)

    var exif by remember(entry.id) { mutableStateOf<PhotoExif?>(null) }
    LaunchedEffect(entry.id) {
        exif = withContext(Dispatchers.IO) { ExifReader.read(context, entry.uri) }
    }
    val camera = remember(exif) { cameraLabel(exif) }
    val dateLine = remember(exif) { (exif?.dateTaken?.take(10)?.replace(":", ".") ?: "") }

    // Exported works already carry their frame + caption baked in, so the card
    // shows the finished image as-is — no second mat. Aspect from MediaStore.
    val aspect = if (entry.width > 0 && entry.height > 0) {
        entry.width.toFloat() / entry.height
    } else {
        0.8f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect.coerceIn(0.45f, 2.2f))
                    .shadow(24.dp, clip = false),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = entry.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer { this.alpha = if (pageOffset < 0.3f) 1f else 0f },
            ) {
                MetaTag(camera)
                if (dateLine.isNotBlank()) MetaTag(dateLine)
            }
        }
    }
}

@Composable
private fun MetaTag(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
            Text("还没有作品", color = GlassColors.OnSurfaceMuted, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "你导出的作品会挂在这里，像画廊里的展品",
                color = GlassColors.OnSurfaceFaint,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SpotlightBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "spot")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                colors = listOf(GlassColors.Accent.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(w * 0.5f, h * 0.1f),
                radius = w * 0.9f,
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.04f), Color.Transparent),
                center = Offset(w * (0.1f + t * 0.2f), h * 0.2f),
                radius = w * 0.8f,
            ),
            radius = w * 0.8f,
            center = Offset(w * (0.1f + t * 0.2f), h * 0.2f),
        )
    }
}

private fun cameraLabel(exif: PhotoExif?): String {
    val make = exif?.cameraMake?.trim().orEmpty()
    val model = exif?.cameraModel?.trim().orEmpty()
    val label = when {
        model.isEmpty() && make.isEmpty() -> "JUSTFRAME"
        make.isEmpty() -> model
        model.isEmpty() -> make
        model.startsWith(make, ignoreCase = true) -> model
        else -> "$make $model"
    }
    return label.uppercase()
}
