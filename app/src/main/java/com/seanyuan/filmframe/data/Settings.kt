package com.seanyuan.filmframe.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "filmframe_settings")

enum class WatermarkPosition(val displayName: String) {
    TopLeft("左上"),
    TopRight("右上"),
    BottomLeft("左下"),
    BottomRight("右下"),
}

data class WatermarkSettings(
    val enabled: Boolean,
    val text: String,
    val position: WatermarkPosition = WatermarkPosition.BottomRight,
) {
    val active: Boolean get() = enabled && text.isNotBlank()
    companion object {
        val Default = WatermarkSettings(enabled = false, text = "", position = WatermarkPosition.BottomRight)
    }
}

/**
 * 4 export quality tiers. Each yields an ExportSpec that controls:
 *   - maxLongEdge of the source bitmap (= ≈ output size minus margins)
 *   - whether to force JPEG (true for Medium/Low, predictable file size)
 *   - JPEG quality (or ignored for PNG / WEBP_LOSSLESS)
 *
 * Even Low keeps decent quality — JPEG 85 @ 2000px produces ~500KB-1.5MB
 * for typical photos, NOT the few-hundred-KB nuclear-compression user
 * pushed back on.
 */
enum class ExportQuality(
    val displayName: String,
    val subtitle: String,
    val maxLongEdge: Int,
    val forceJpeg: Boolean,
    val jpegQuality: Int,
) {
    Original("原画", "保留全部像素 · 跟随源格式", Int.MAX_VALUE, false, 100),
    High("高", "长边 4096 · 保留原图格式", 4096, false, 95),
    Medium("中", "长边 2800 · JPEG 92 · 适合社交", 2800, true, 92),
    Low("低", "长边 2000 · JPEG 85 · 体积更小", 2000, true, 85);

    val isOriginal: Boolean get() = this == Original
}

data class AppSettings(
    val watermark: WatermarkSettings,
    val exportQuality: ExportQuality,
    val autoRemoveExistingFrame: Boolean,
)

object Settings {
    private val WATERMARK_ENABLED = booleanPreferencesKey("watermark_enabled")
    private val WATERMARK_TEXT = stringPreferencesKey("watermark_text")
    private val WATERMARK_POSITION = stringPreferencesKey("watermark_position")
    private val LAST_TEMPLATE_ID = stringPreferencesKey("last_template_id")
    private val EXPORT_QUALITY = stringPreferencesKey("export_quality")
    private val AUTO_REMOVE_FRAME = booleanPreferencesKey("auto_remove_frame")
    private val REMEMBER_STATE = booleanPreferencesKey("remember_state")
    private val PRESERVE_EXIF = booleanPreferencesKey("preserve_exif")

    fun watermark(context: Context): Flow<WatermarkSettings> =
        context.applicationContext.settingsDataStore.data.map { prefs ->
            WatermarkSettings(
                enabled = prefs[WATERMARK_ENABLED] ?: false,
                text = prefs[WATERMARK_TEXT].orEmpty(),
                position = prefs[WATERMARK_POSITION]
                    ?.let { runCatching { WatermarkPosition.valueOf(it) }.getOrNull() }
                    ?: WatermarkPosition.BottomRight,
            )
        }

    suspend fun updateWatermark(context: Context, value: WatermarkSettings) {
        context.applicationContext.settingsDataStore.edit { prefs ->
            prefs[WATERMARK_ENABLED] = value.enabled
            prefs[WATERMARK_TEXT] = value.text
            prefs[WATERMARK_POSITION] = value.position.name
        }
    }

    fun lastTemplateId(context: Context): Flow<String> =
        context.applicationContext.settingsDataStore.data.map { prefs ->
            prefs[LAST_TEMPLATE_ID] ?: "classic"
        }

    suspend fun updateLastTemplate(context: Context, id: String) {
        context.applicationContext.settingsDataStore.edit { prefs ->
            prefs[LAST_TEMPLATE_ID] = id
        }
    }

    fun exportQuality(context: Context): Flow<ExportQuality> =
        context.applicationContext.settingsDataStore.data.map { prefs ->
            prefs[EXPORT_QUALITY]
                ?.let { runCatching { ExportQuality.valueOf(it) }.getOrNull() }
                ?: ExportQuality.Original
        }

    suspend fun updateExportQuality(context: Context, value: ExportQuality) {
        context.applicationContext.settingsDataStore.edit { prefs ->
            prefs[EXPORT_QUALITY] = value.name
        }
    }

    fun autoRemoveExistingFrame(context: Context): Flow<Boolean> =
        context.applicationContext.settingsDataStore.data.map { prefs ->
            prefs[AUTO_REMOVE_FRAME] ?: true
        }

    suspend fun updateAutoRemoveExistingFrame(context: Context, value: Boolean) {
        context.applicationContext.settingsDataStore.edit { prefs ->
            prefs[AUTO_REMOVE_FRAME] = value
        }
    }

    /** 状态记忆 — when on, Create opens on the last-used template. */
    fun rememberState(context: Context): Flow<Boolean> =
        context.applicationContext.settingsDataStore.data.map { prefs ->
            prefs[REMEMBER_STATE] ?: true
        }

    suspend fun updateRememberState(context: Context, value: Boolean) {
        context.applicationContext.settingsDataStore.edit { prefs ->
            prefs[REMEMBER_STATE] = value
        }
    }

    /** 完整保留 EXIF — when on, source camera metadata is written into JPEG exports. */
    fun preserveExif(context: Context): Flow<Boolean> =
        context.applicationContext.settingsDataStore.data.map { prefs ->
            prefs[PRESERVE_EXIF] ?: true
        }

    suspend fun updatePreserveExif(context: Context, value: Boolean) {
        context.applicationContext.settingsDataStore.edit { prefs ->
            prefs[PRESERVE_EXIF] = value
        }
    }
}
