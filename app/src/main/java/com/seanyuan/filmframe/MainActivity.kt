package com.seanyuan.filmframe

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.seanyuan.filmframe.ui.batch.BatchScreen
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.home.HomeScreen
import com.seanyuan.filmframe.ui.settings.SettingsScreen
import com.seanyuan.filmframe.ui.theme.FilmFrameTheme

private sealed interface Route {
    data object Home : Route
    data class Batch(val uris: List<Uri>) : Route
    data object Settings : Route
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FilmFrameTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassColors.DeepBackground),
                    containerColor = GlassColors.DeepBackground,
                ) { innerPadding ->
                    AppRoot(modifier = Modifier
                        .fillMaxSize()
                        .background(GlassColors.DeepBackground))
                }
            }
        }
    }
}

@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf<Route>(Route.Home) }
    when (val r = route) {
        Route.Home -> HomeScreen(
            onBatch = { route = Route.Batch(it) },
            onSettings = { route = Route.Settings },
            modifier = modifier,
        )
        is Route.Batch -> BatchScreen(
            uris = r.uris,
            onBack = { route = Route.Home },
            modifier = modifier,
        )
        Route.Settings -> SettingsScreen(
            onBack = { route = Route.Home },
            modifier = modifier,
        )
    }
}
