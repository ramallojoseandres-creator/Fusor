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
    private val keyUserId = stringPreferencesKey("user_id")
    private val keyRole = stringPreferencesKey("user_role")
    private val keyDeviceId = stringPreferencesKey("device_id")

    @Volatile
    var cachedToken: String? = null
        private set

    @Volatile
    var cachedRole: String? = null
        private set

    @Volatile
    var cachedUserId: String? = null
        private set

    @Volatile
    var cachedUsername: String? = null
        private set

    val tokenFlow: Flow<String?> = context.tokenDataStore.data.map { it[keyToken] }

    init {
        runBlocking {
            val prefs = context.tokenDataStore.data.first()
            cachedToken = prefs[keyToken]
            cachedRole = prefs[keyRole]
            cachedUserId = prefs[keyUserId]
            cachedUsername = prefs[keyUsername]
        }
    }

    val isAdmin: Boolean
        get() {
            val role = cachedRole?.trim().orEmpty()
            return role.equals("MASTER", ignoreCase = true) ||
                role.equals("ADMIN", ignoreCase = true) ||
                role.equals("admin", ignoreCase = true)
        }

    suspend fun saveSession(
        token: String,
        username: String,
        userId: String? = null,
        role: String? = null
    ) {
        context.tokenDataStore.edit {
            it[keyToken] = token
            it[keyUsername] = username
            if (!userId.isNullOrBlank()) it[keyUserId] = userId else it.remove(keyUserId)
            if (!role.isNullOrBlank()) it[keyRole] = role else it.remove(keyRole)
        }
        cachedToken = token
        cachedUsername = username
        cachedUserId = userId
        cachedRole = role
    }

    suspend fun clear() {
        context.tokenDataStore.edit {
            it.remove(keyToken)
            it.remove(keyUsername)
            it.remove(keyUserId)
            it.remove(keyRole)
        }
        cachedToken = null
        cachedUsername = null
        cachedUserId = null
        cachedRole = null
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
