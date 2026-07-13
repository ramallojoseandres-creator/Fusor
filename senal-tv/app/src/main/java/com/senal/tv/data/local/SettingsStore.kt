package com.senal.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("senal_settings")

data class AppSettings(
    val preferredAspect: String = "fit",
    val playbackSpeed: Float = 1f,
    val softSubtitles: Boolean = true
)

class SettingsStore(private val context: Context) {
    private val aspect = stringPreferencesKey("aspect")
    private val speed = floatPreferencesKey("speed")
    private val subs = booleanPreferencesKey("subs")

    val settings: Flow<AppSettings> = context.settingsStore.data.map {
        AppSettings(
            preferredAspect = it[aspect] ?: "fit",
            playbackSpeed = it[speed] ?: 1f,
            softSubtitles = it[subs] ?: true
        )
    }

    suspend fun setAspect(value: String) {
        context.settingsStore.edit { it[aspect] = value }
    }

    suspend fun setSpeed(value: Float) {
        context.settingsStore.edit { it[speed] = value }
    }
}
