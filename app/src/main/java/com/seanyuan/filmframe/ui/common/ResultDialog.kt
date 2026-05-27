package com.seanyuan.filmframe.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.seanyuan.filmframe.ui.glass.GlassButton
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.glass.GlassSurface

data class ResultSummary(
    val savedUri: Uri,
    val previewBitmap: ImageBitmap?,
    val outputFormat: String,
    val outputWidth: Int,
    val outputHeight: Int,
    val originalWidth: Int,
    val originalHeight: Int,
    val downsampled: Boolean,
)

@Composable
fun ResultDialog(
    summary: ResultSummary,
    onAnother: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier
                .width(340.dp)
                .padding(24.dp),
            tonalIntensity = 1.4f,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "✓ 已保存到相册",
                    color = GlassColors.OnSurface,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pictures / FilmFrame",
                    color = GlassColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))

                summary.previewBitmap?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = it,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                MetadataRow("格式", summary.outputFormat)
                MetadataRow(
                    "尺寸",
                    "${summary.outputWidth} × ${summary.outputHeight}",
                )
                if (summary.downsampled && summary.originalWidth > 0) {
                    MetadataRow(
                        "原图",
                        "${summary.originalWidth} × ${summary.originalHeight} · 内存所限略缩",
                        warning = true,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    GlassButton(
                        text = "分享",
                        onClick = { shareImage(context, summary.savedUri) },
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = "再来一张",
                        onClick = onAnother,
                        modifier = Modifier.weight(1f),
                        accent = true,
                    )
                }
                Spacer(Modifier.height(8.dp))
                GlassButton(
                    text = "完成",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String, warning: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            label,
            color = GlassColors.OnSurfaceFaint,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(56.dp),
        )
        Text(
            value,
            color = if (warning) GlassColors.Accent else GlassColors.OnSurfaceMuted,
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
