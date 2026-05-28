package com.seanyuan.filmframe.ui.result

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seanyuan.filmframe.ui.glass.GlassButton
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.glass.GlassSurface

data class ResultSummary(
    val savedUri: Uri,
    val previewBitmap: Bitmap?,
    val outputFormat: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val downsampled: Boolean,
    val templateName: String,
    val quality: String,
)

@Composable
fun ResultScreen(
    summary: ResultSummary,
    onAnother: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var chromeVisible by remember { mutableStateOf(true) }
    val previewBitmap = summary.previewBitmap?.asImageBitmap()

    BackHandler { onHome() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassColors.DeepBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { chromeVisible = !chromeVisible },
    ) {
        // Blurred backdrop (gives "photo extends edge-to-edge" feel)
        previewBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp)
                    .alpha(0.45f),
            )
        }
        // Soft vignette darken
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
        )

        // Foreground sharp photo, slightly inset for "framed" feel
        previewBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 96.dp),
            )
        }

        // Top metadata pill
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassSurface(intensity = 1.3f) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF5BBF8F)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "已保存 · ${summary.templateName}",
                            color = GlassColors.OnSurface,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // Bottom floating action bar
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    intensity = 1.5f,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        MetaRow("格式", summary.outputFormat)
                        MetaRow("尺寸", "${summary.outputWidth} × ${summary.outputHeight}")
                        MetaRow("画质", summary.quality)
                        if (summary.downsampled && summary.originalWidth > 0) {
                            MetaRow(
                                "原图",
                                "${summary.originalWidth} × ${summary.originalHeight} · 内存所限略缩",
                                warn = true,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton(
                                text = "返回首页",
                                onClick = onHome,
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                text = "分享",
                                onClick = { shareImage(context, summary.savedUri) },
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(
                                text = "再来一张",
                                accent = true,
                                onClick = onAnother,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String, warn: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            label,
            color = GlassColors.OnSurfaceFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(56.dp),
        )
        Text(
            value,
            color = if (warn) GlassColors.Accent else GlassColors.OnSurfaceMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun shareImage(context: android.content.Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享到").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
