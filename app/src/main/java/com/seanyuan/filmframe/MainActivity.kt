package com.seanyuan.filmframe

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.seanyuan.filmframe.ui.create.CreateScreen
import com.seanyuan.filmframe.ui.edit.EditScreen
import com.seanyuan.filmframe.ui.gallery.GalleryScreen
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.nav.BottomNav
import com.seanyuan.filmframe.ui.nav.Tab
import com.seanyuan.filmframe.ui.picker.PhotoPickerScreen
import com.seanyuan.filmframe.ui.settings.SettingsScreen
import com.seanyuan.filmframe.ui.theme.FilmFrameTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private sealed interface Screen {
    val depth: Int
    data object Main : Screen { override val depth = 0 }
    data object Picker : Screen { override val depth = 1 }
    data class Edit(val uris: List<Uri>, val presetTemplateId: String) : Screen { override val depth = 2 }
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
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var tab by remember { mutableStateOf(Tab.Create) }
    var pendingTemplateId by remember { mutableStateOf("classic") }

    AnimatedContent(
        targetState = screen,
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
        label = "screen",
    ) { s ->
        when (s) {
            Screen.Main -> MainTabs(
                tab = tab,
                onTab = { tab = it },
                onImport = { templateId ->
                    pendingTemplateId = templateId
                    screen = Screen.Picker
                },
                modifier = Modifier.fillMaxSize(),
            )
            Screen.Picker -> PhotoPickerScreen(
                onBack = { screen = Screen.Main },
                onConfirm = { uris ->
                    screen = if (uris.isEmpty()) Screen.Main
                    else Screen.Edit(uris, pendingTemplateId)
                },
                modifier = Modifier.fillMaxSize(),
            )
            is Screen.Edit -> EditScreen(
                uris = s.uris,
                presetTemplateId = s.presetTemplateId,
                onBack = { screen = Screen.Main },
                onHome = {
                    tab = Tab.Create
                    screen = Screen.Main
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MainTabs(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onImport: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberHazeState()
    Box(modifier = modifier.background(GlassColors.DeepBackground)) {
        Crossfade(
            targetState = tab,
            animationSpec = tween(260),
            label = "tab",
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        ) { t ->
            when (t) {
                Tab.Gallery -> GalleryScreen(modifier = Modifier.fillMaxSize())
                Tab.Create -> CreateScreen(onImport = onImport, modifier = Modifier.fillMaxSize())
                Tab.Settings -> SettingsScreen(modifier = Modifier.fillMaxSize())
            }
        }
        BottomNav(
            current = tab,
            onSelect = onTab,
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
