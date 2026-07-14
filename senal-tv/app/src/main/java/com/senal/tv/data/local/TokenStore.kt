package com.senal.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.tokenDataStore by preferencesDataStore("senal_secure_prefs")

class TokenStore(private val context: Context) {
    private val keyToken = stringPreferencesKey("jwt")
    private val keyUsername = stringPreferencesKey("username_display")
    private val keyDeviceId = stringPreferencesKey("device_id")

    @Volatile
    var cachedToken: String? = null
        private set

    val tokenFlow: Flow<String?> = context.tokenDataStore.data.map { it[keyToken] }

    init {
        cachedToken = runBlocking {
            context.tokenDataStore.data.first()[keyToken]
        }
    }

    suspend fun saveSession(token: String, username: String) {
        context.tokenDataStore.edit {
            it[keyToken] = token
            it[keyUsername] = username
        }
        cachedToken = token
    }

    suspend fun clear() {
        context.tokenDataStore.edit {
            it.remove(keyToken)
            it.remove(keyUsername)
        }
        cachedToken = null
    }

    suspend fun username(): String? =
        context.tokenDataStore.data.first()[keyUsername]

    suspend fun deviceId(): String {
        val existing = context.tokenDataStore.data.first()[keyDeviceId]
        if (!existing.isNullOrBlank()) return existing
        val created = "senal-tv-" + java.util.UUID.randomUUID().toString()
        context.tokenDataStore.edit { it[keyDeviceId] = created }
        return created
    }
}
