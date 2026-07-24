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

    @Volatile
    private var cachedDeviceId: String? = null

    val tokenFlow: Flow<String?> = context.tokenDataStore.data.map { it[keyToken] }

    init {
        runBlocking {
            val prefs = context.tokenDataStore.data.first()
            cachedToken = prefs[keyToken]
            cachedDeviceId = prefs[keyDeviceId]
        }
    }

    suspend fun saveSession(token: String, username: String) {
        if (token.isBlank()) return
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

    fun peekDeviceId(): String? = cachedDeviceId

    suspend fun deviceId(): String {
        cachedDeviceId?.takeIf { it.isNotBlank() }?.let { return it }
        val existing = context.tokenDataStore.data.first()[keyDeviceId]
        if (!existing.isNullOrBlank()) {
            cachedDeviceId = existing
            return existing
        }
        val created = "senal-tv-" + java.util.UUID.randomUUID().toString()
        context.tokenDataStore.edit { it[keyDeviceId] = created }
        cachedDeviceId = created
        return created
    }
}
