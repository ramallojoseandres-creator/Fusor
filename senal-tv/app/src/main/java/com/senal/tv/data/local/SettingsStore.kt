package com.senal.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("senal_settings")

data class AppSettings(
    val preferredAspect: String = "fit",
    val playbackSpeed: Float = 1f,
    val softSubtitles: Boolean = true,
    /** Parental lock: hide Adultos until PIN unlock (session or permanent off). */
    val adultsLocked: Boolean = false,
    val adultPinHash: String = "",
    /** Last live channel for this device/user — used for autoplay on launch. */
    val lastChannelId: String = "",
    val lastChannelTitle: String = ""
) {
    val hasAdultPin: Boolean get() = adultPinHash.isNotBlank()
}

class SettingsStore(private val context: Context) {
    private val aspect = stringPreferencesKey("aspect")
    private val speed = floatPreferencesKey("speed")
    private val subs = booleanPreferencesKey("subs")
    private val adultsLockedKey = booleanPreferencesKey("adults_locked")
    private val adultPinKey = stringPreferencesKey("adult_pin_hash")
    private val lastChannelIdKey = stringPreferencesKey("last_channel_id")
    private val lastChannelTitleKey = stringPreferencesKey("last_channel_title")

    val settings: Flow<AppSettings> = context.settingsStore.data.map {
        AppSettings(
            preferredAspect = it[aspect] ?: "fit",
            playbackSpeed = it[speed] ?: 1f,
            softSubtitles = it[subs] ?: true,
            adultsLocked = it[adultsLockedKey] ?: false,
            adultPinHash = it[adultPinKey].orEmpty(),
            lastChannelId = it[lastChannelIdKey].orEmpty(),
            lastChannelTitle = it[lastChannelTitleKey].orEmpty()
        )
    }

    suspend fun setLastChannel(id: String, title: String) {
        if (id.isBlank()) return
        context.settingsStore.edit {
            it[lastChannelIdKey] = id
            it[lastChannelTitleKey] = title
        }
    }

    suspend fun setAspect(value: String) {
        context.settingsStore.edit { it[aspect] = value }
    }

    suspend fun setSpeed(value: Float) {
        context.settingsStore.edit { it[speed] = value }
    }

    suspend fun enableAdultLock(pin: String) {
        require(pin.length in 4..8 && pin.all { it.isDigit() }) {
            "La clave debe tener 4–8 dígitos"
        }
        context.settingsStore.edit {
            it[adultsLockedKey] = true
            it[adultPinKey] = hashPin(pin)
        }
    }

    suspend fun disableAdultLock(pin: String): Boolean {
        val current = settings.first()
        if (!verifyPin(pin, current.adultPinHash)) return false
        context.settingsStore.edit {
            it[adultsLockedKey] = false
            it.remove(adultPinKey)
        }
        return true
    }

    suspend fun changeAdultPin(currentPin: String, newPin: String): Boolean {
        val current = settings.first()
        if (!verifyPin(currentPin, current.adultPinHash)) return false
        require(newPin.length in 4..8 && newPin.all { it.isDigit() }) {
            "La nueva clave debe tener 4–8 dígitos"
        }
        context.settingsStore.edit {
            it[adultPinKey] = hashPin(newPin)
            it[adultsLockedKey] = true
        }
        return true
    }

    fun verifyPin(pin: String, hash: String): Boolean {
        if (hash.isBlank()) return false
        return hashPin(pin) == hash
    }

    companion object {
        fun hashPin(pin: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(("senal-adult|" + pin).toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
