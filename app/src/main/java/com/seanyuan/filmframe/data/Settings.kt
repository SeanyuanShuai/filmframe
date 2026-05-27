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

object Settings {
    private val WATERMARK_ENABLED = booleanPreferencesKey("watermark_enabled")
    private val WATERMARK_TEXT = stringPreferencesKey("watermark_text")
    private val WATERMARK_POSITION = stringPreferencesKey("watermark_position")
    private val LAST_TEMPLATE_ID = stringPreferencesKey("last_template_id")

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
}
