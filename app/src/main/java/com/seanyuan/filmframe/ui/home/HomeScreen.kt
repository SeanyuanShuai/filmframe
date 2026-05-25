package com.seanyuan.filmframe.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.seanyuan.filmframe.data.ExifReader
import com.seanyuan.filmframe.data.PhotoExif

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var exif by remember { mutableStateOf<PhotoExif?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            exif = ExifReader.read(context, uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val uri = selectedUri
            if (uri == null) {
                Text(
                    text = "FilmFrame",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                )
            } else {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        exif?.let { ExifDebugPanel(it) }

        Button(
            onClick = {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(if (selectedUri == null) "导入照片" else "换一张")
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
