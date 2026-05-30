package com.seanyuan.filmframe.ui.picker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil3.compose.AsyncImage
import com.seanyuan.filmframe.data.GalleryEntry
import com.seanyuan.filmframe.data.MediaGallery
import com.seanyuan.filmframe.ui.glass.GlassButton
import com.seanyuan.filmframe.ui.glass.GlassColors
import com.seanyuan.filmframe.ui.glass.GlassSurface
import com.seanyuan.filmframe.ui.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PhotoPickerScreen(
    onBack: () -> Unit,
    onConfirm: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var granted by remember {
        mutableStateOf(
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    var requestedOnce by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        requestedOnce = true
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(permission)
    }

    // Re-check permission when activity resumes (user may grant from system settings)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, permission) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val entries = remember { mutableStateListOf<GalleryEntry>() }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(granted) {
        if (granted) {
            val list = withContext(Dispatchers.IO) { MediaGallery.listImages(context, limit = 800) }
            entries.clear()
            entries.addAll(list)
            loaded = true
        }
    }

    val selectedIds = remember { mutableStateListOf<Long>() }

    BackHandler { onBack() }

    Box(modifier = modifier.fillMaxSize().background(GlassColors.DeepBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title = "选择照片",
                subtitle = when {
                    !granted -> "需要相册权限"
                    !loaded -> "加载中…"
                    selectedIds.isEmpty() -> "${entries.size} 张照片 · 新到旧"
                    else -> "已选 ${selectedIds.size} 张 · 进入编辑"
                },
                onBack = onBack,
            )

            if (!granted) {
                PermissionPrompt(
                    showSettingsFallback = requestedOnce,
                    onRequest = { permissionLauncher.launch(permission) },
                    onOpenSettings = {
                        val intent = Intent(
                            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        val isSelected = selectedIds.contains(entry.id)
                        PhotoCell(
                            entry = entry,
                            selected = isSelected,
                            onTap = {
                                if (isSelected) selectedIds.remove(entry.id)
                                else if (selectedIds.size < 30) selectedIds.add(entry.id)
                            },
                        )
                    }
                }
            }

            if (granted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassButton(
                        text = "清空",
                        onClick = { selectedIds.clear() },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                    val confirmLabel = when (selectedIds.size) {
                        0 -> "选照片"
                        1 -> "编辑这张"
                        else -> "编辑 · ${selectedIds.size}"
                    }
                    GlassButton(
                        text = confirmLabel,
                        accent = true,
                        enabled = selectedIds.isNotEmpty(),
                        onClick = {
                            haptics.medium()
                            val uris = entries
                                .filter { it.id in selectedIds }
                                .map { it.uri }
                            onConfirm(uris)
                        },
                        modifier = Modifier.weight(1.8f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("← 返回", color = GlassColors.OnSurface, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = GlassColors.OnSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(it, color = GlassColors.OnSurfaceFaint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PhotoCell(
    entry: GalleryEntry,
    selected: Boolean,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onTap),
    ) {
        AsyncImage(
            model = entry.uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = Color(0xFF1A1100), fontWeight = FontWeight.Bold)
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, GlassColors.Accent, RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun PermissionPrompt(
    showSettingsFallback: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(modifier = Modifier.fillMaxWidth(), intensity = 1.3f) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "需要相册权限",
                    color = GlassColors.OnSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "JustFrame 需要读取你的相册来显示照片缩略图。所有处理都在本地完成，照片不会上传任何服务器。",
                    color = GlassColors.OnSurfaceMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(20.dp))
                GlassButton(
                    text = "授权访问相册",
                    accent = true,
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showSettingsFallback) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "如果系统不再弹出授权请求，请去系统设置手动开启。",
                        color = GlassColors.OnSurfaceFaint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassButton(
                        text = "去系统设置",
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
