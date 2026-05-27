package com.seanyuan.filmframe

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.seanyuan.filmframe.ui.batch.BatchScreen
import com.seanyuan.filmframe.ui.home.HomeScreen
import com.seanyuan.filmframe.ui.theme.FilmFrameTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FilmFrameTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppRoot(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    var batchUris by remember { mutableStateOf<List<Uri>?>(null) }
    val uris = batchUris
    if (uris == null) {
        HomeScreen(
            onBatch = { batchUris = it },
            modifier = modifier,
        )
    } else {
        BatchScreen(
            uris = uris,
            onBack = { batchUris = null },
            modifier = modifier,
        )
    }
}
