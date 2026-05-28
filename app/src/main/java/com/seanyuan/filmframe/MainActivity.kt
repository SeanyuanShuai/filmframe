package com.seanyuan.filmframe

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.seanyuan.filmframe.ui.batch.BatchScreen
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.home.HomeScreen
import com.seanyuan.filmframe.ui.picker.PhotoPickerScreen
import com.seanyuan.filmframe.ui.result.BatchResultScreen
import com.seanyuan.filmframe.ui.result.BatchResultSummary
import com.seanyuan.filmframe.ui.result.ResultScreen
import com.seanyuan.filmframe.ui.result.ResultSummary
import com.seanyuan.filmframe.ui.settings.SettingsScreen
import com.seanyuan.filmframe.ui.theme.FilmFrameTheme

private sealed interface Route {
    val depth: Int
    data object Home : Route { override val depth = 0 }
    data object Picker : Route { override val depth = 1 }
    data class Batch(val uris: List<Uri>) : Route { override val depth = 2 }
    data object Settings : Route { override val depth = 2 }
    data class Result(val summary: ResultSummary) : Route { override val depth = 3 }
    data class BatchResult(val summary: BatchResultSummary) : Route { override val depth = 3 }
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
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { _ ->
                    AppRoot(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun AppRoot(modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf<Route>(Route.Home) }
    var pendingPickedUri by remember { mutableStateOf<Uri?>(null) }

    AnimatedContent(
        targetState = route,
        modifier = modifier,
        transitionSpec = {
            val forward = targetState.depth > initialState.depth
            val w = 220
            val durF = tween<Float>(durationMillis = 320)
            val durO = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 320)
            if (forward) {
                slideInHorizontally(durO) { w } + fadeIn(durF) togetherWith
                    slideOutHorizontally(durO) { -w / 4 } + fadeOut(durF)
            } else {
                slideInHorizontally(durO) { -w / 4 } + fadeIn(durF) togetherWith
                    slideOutHorizontally(durO) { w } + fadeOut(durF)
            }
        },
        label = "route",
    ) { r ->
        when (r) {
            Route.Home -> HomeScreen(
                initialUri = pendingPickedUri,
                onConsumeInitialUri = { pendingPickedUri = null },
                onRequestPick = { route = Route.Picker },
                onSettings = { route = Route.Settings },
                onResult = { route = Route.Result(it) },
                onOpenSavedExhibit = { exhibitSummary -> route = Route.Result(exhibitSummary) },
                modifier = Modifier.fillMaxSize(),
            )
            Route.Picker -> PhotoPickerScreen(
                onBack = { route = Route.Home },
                onConfirm = { uris ->
                    when {
                        uris.isEmpty() -> route = Route.Home
                        uris.size == 1 -> {
                            pendingPickedUri = uris.first()
                            route = Route.Home
                        }
                        else -> route = Route.Batch(uris)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            is Route.Batch -> BatchScreen(
                uris = r.uris,
                onBack = { route = Route.Home },
                onResult = { route = Route.BatchResult(it) },
                modifier = Modifier.fillMaxSize(),
            )
            Route.Settings -> SettingsScreen(
                onBack = { route = Route.Home },
                modifier = Modifier.fillMaxSize(),
            )
            is Route.Result -> ResultScreen(
                summary = r.summary,
                onHome = { route = Route.Home },
                onAnother = { route = Route.Picker },
                onRetemplate = r.summary.sourceUri?.let {
                    {
                        pendingPickedUri = it
                        route = Route.Home
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            is Route.BatchResult -> BatchResultScreen(
                summary = r.summary,
                onHome = { route = Route.Home },
                onAnother = { route = Route.Picker },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
