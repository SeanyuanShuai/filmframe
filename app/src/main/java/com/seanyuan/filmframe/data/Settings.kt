package com.seanyuan.filmframe.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "filmframe_settings")

data class WatermarkSettings(val enabled: Boolean, val text: String) {
    val active: Boolean get() = enabled && text.isNotBlank()
    companion object {
        val Default = WatermarkSettings(enabled = false, text = "")
    }
}

object Settings {
    private val WATERMARK_ENABLED = booleanPreferencesKey("watermark_enabled")
    private val WATERMARK_TEXT = stringPreferencesKey("watermark_text")

    fun watermark(context: Context): Flow<WatermarkSettings> =
        context.applicationContext.settingsDataStore.data.map { prefs ->
            WatermarkSettings(
                enabled = prefs[WATERMARK_ENABLED] ?: false,
                text = prefs[WATERMARK_TEXT].orEmpty(),
            )
        }

    suspend fun updateWatermark(context: Context, value: WatermarkSettings) {
        context.applicationContext.settingsDataStore.edit { prefs ->
            prefs[WATERMARK_ENABLED] = value.enabled
            prefs[WATERMARK_TEXT] = value.text
        }
    }
}
