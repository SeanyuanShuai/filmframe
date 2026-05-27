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
import com.seanyuan.filmframe.ui.result.ResultScreen
import com.seanyuan.filmframe.ui.result.ResultSummary
import com.seanyuan.filmframe.ui.settings.SettingsScreen
import com.seanyuan.filmframe.ui.theme.FilmFrameTheme

private sealed interface Route {
    val depth: Int
    data object Home : Route { override val depth = 0 }
    data class Batch(val uris: List<Uri>) : Route { override val depth = 1 }
    data object Settings : Route { override val depth = 2 }
    data class Result(val summary: ResultSummary) : Route { override val depth = 3 }
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
    AnimatedContent(
        targetState = route,
        modifier = modifier,
        transitionSpec = {
            val forward = targetState.depth > initialState.depth
            val w = 220
            val tween1 = tween<Float>(durationMillis = 320)
            val tween2 = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = 320)
            if (forward) {
                slideInHorizontally(tween2) { w } + fadeIn(tween1) togetherWith
                    slideOutHorizontally(tween2) { -w / 4 } + fadeOut(tween1)
            } else {
                slideInHorizontally(tween2) { -w / 4 } + fadeIn(tween1) togetherWith
                    slideOutHorizontally(tween2) { w } + fadeOut(tween1)
            }
        },
        label = "route",
    ) { r ->
        when (r) {
            Route.Home -> HomeScreen(
                onBatch = { route = Route.Batch(it) },
                onSettings = { route = Route.Settings },
                onResult = { route = Route.Result(it) },
                modifier = Modifier.fillMaxSize(),
            )
            is Route.Batch -> BatchScreen(
                uris = r.uris,
                onBack = { route = Route.Home },
                modifier = Modifier.fillMaxSize(),
            )
            Route.Settings -> SettingsScreen(
                onBack = { route = Route.Home },
                modifier = Modifier.fillMaxSize(),
            )
            is Route.Result -> ResultScreen(
                summary = r.summary,
                onHome = { route = Route.Home },
                onAnother = { route = Route.Home },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
