package com.senal.tv.data.repository

import com.senal.tv.data.api.ChangePasswordRequest
import com.senal.tv.data.api.NetworkModule
import com.senal.tv.data.api.PatchUserRequest
import com.senal.tv.data.api.SenalApi
import com.senal.tv.data.local.ContinueDao
import com.senal.tv.data.local.ContinueEntity
import com.senal.tv.data.local.FavoriteDao
import com.senal.tv.data.local.FavoriteEntity
import com.senal.tv.data.local.HistoryDao
import com.senal.tv.data.local.HistoryEntity
import com.senal.tv.data.local.LocalPlaylistStore
import com.senal.tv.data.local.SettingsStore
import com.senal.tv.data.local.TokenStore
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.CatalogResponse
import com.senal.tv.data.model.Category
import com.senal.tv.data.model.ContentType
import com.senal.tv.data.model.LoginRequest
import com.senal.tv.data.model.PlaybackResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Solo autentica contra el servidor SEÑAL (creación / acceso de usuarios).
 * El catálogo y las URLs de stream NO pasan por el servidor.
 */
class AuthRepository(
    private val api: SenalApi,
    private val tokenStore: TokenStore
) {
    val tokenFlow = tokenStore.tokenFlow

    suspend fun login(username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val deviceId = tokenStore.deviceId()
            val response = api.login(
                LoginRequest(
                    username = username.trim(),
                    password = password,
                    deviceId = deviceId,
                    deviceName = "SEÑAL Android TV"
                )
            )
            val token = response.resolveToken()
                ?: throw IllegalStateException(response.error ?: "No se recibió token JWT")
            tokenStore.saveSession(
                token = token,
                username = username.trim(),
                userId = response.user?.id,
                role = response.user?.role
            )
        }.recoverCatching { err ->
            throw friendlyHttp(err)
        }
    }

    suspend fun changePassword(current: String, newPassword: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (newPassword.length < 4) {
                    throw IllegalStateException("La nueva contraseña debe tener al menos 4 caracteres")
                }
                // 1) Preferred dedicated endpoint
                val viaAuth = runCatching {
                    api.changePassword(
                        ChangePasswordRequest(
                            currentPassword = current,
                            newPassword = newPassword,
                            oldPassword = current,
                            password = newPassword
                        )
                    )
                }
                if (viaAuth.isSuccess) {
                    val body = viaAuth.getOrThrow()
                    if (!body.error.isNullOrBlank()) {
                        throw IllegalStateException(body.error)
                    }
                    return@runCatching
                }
                // 2) Fallback: admin-style PATCH on own user id (if the API accepts password)
                val userId = tokenStore.cachedUserId
                    ?: throw viaAuth.exceptionOrNull()
                        ?: IllegalStateException("No hay id de usuario en la sesión")
                val patched = api.patchUser(userId, PatchUserRequest(password = newPassword))
                if (!patched.error.isNullOrBlank()) {
                    throw IllegalStateException(patched.error)
                }
            }.recoverCatching { err ->
                throw friendlyHttp(err)
            }
        }

    suspend fun logout() = tokenStore.clear()

    suspend fun hasSession(): Boolean = !tokenStore.cachedToken.isNullOrBlank()

    private fun friendlyHttp(err: Throwable): Throwable {
        if (err is HttpException) {
            val body = err.response()?.errorBody()?.string().orEmpty()
            val msg = runCatching {
                NetworkModule.json.decodeFromString(
                    com.senal.tv.data.model.ApiError.serializer(),
                    body
                ).error
            }.getOrNull()
            return IllegalStateException(msg ?: "Error de acceso (${err.code()})")
        }
        return IllegalStateException(err.message ?: "No se pudo conectar con SEÑAL")
    }
}

/**
 * Catálogo desde cache local alimentada por `/api/catalog/fast` (JSON.gz).
 * La APK ya no embute el M3U: descarga + ETag + exploración inmediata de categorías.
 */
class CatalogRepository(
    private val playlist: LocalPlaylistStore
) {
    suspend fun sync(authToken: String? = null): Boolean = playlist.syncFromServer(authToken)
    suspend fun categories(type: String): List<Category> = withContext(Dispatchers.IO) {
        playlist.categories(type)
    }

    /** Categories with optional adult filter for parental lock. */
    suspend fun categories(type: String, hideAdults: Boolean): List<Category> {
        val all = categories(type)
        return if (!hideAdults) {
            com.senal.tv.util.CatalogRules.sortCategories(all)
        } else {
            com.senal.tv.util.CatalogRules.sortCategories(all)
                .filterNot { com.senal.tv.util.CatalogRules.isAdultLabel(it.label()) }
        }
    }

    suspend fun get(id: String): CatalogItem? = withContext(Dispatchers.IO) {
        playlist.get(id)
    }

    suspend fun page(
        type: String,
        category: String? = null,
        page: Int = 1,
        limit: Int = 60
    ): CatalogResponse = withContext(Dispatchers.IO) {
        playlist.page(type = type, category = category, page = page, limit = limit)
    }

    suspend fun search(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        playlist.search(query)
    }

    suspend fun playback(id: String): PlaybackResponse = withContext(Dispatchers.IO) {
        playlist.playback(id)
    }

    fun clearMemory() {
        playlist.clearMemory()
    }
}

class LibraryRepository(
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val continueDao: ContinueDao,
    private val settingsStore: SettingsStore? = null
) {
    fun favorites(): Flow<List<FavoriteEntity>> = favoriteDao.observe()
    fun history(): Flow<List<HistoryEntity>> = historyDao.observe()
    fun continueWatching(): Flow<List<ContinueEntity>> = continueDao.observe()

    suspend fun toggleFavorite(item: CatalogItem): Boolean {
        val id = item.resolveId()
        return if (favoriteDao.exists(id)) {
            favoriteDao.delete(id)
            false
        } else {
            favoriteDao.upsert(
                FavoriteEntity(
                    contentId = id,
                    type = item.contentType().name,
                    title = item.resolveTitle(),
                    poster = item.resolvePoster(),
                    category = item.resolveCategory()
                )
            )
            true
        }
    }

    suspend fun isFavorite(id: String): Boolean = favoriteDao.exists(id)

    suspend fun markHistory(item: CatalogItem) {
        historyDao.upsert(
            HistoryEntity(
                contentId = item.resolveId(),
                type = item.contentType().name,
                title = item.resolveTitle(),
                poster = item.resolvePoster(),
                category = item.resolveCategory()
            )
        )
        historyDao.trim()
        // Persist last live channel for resume/autoplay
        if (item.contentType() == ContentType.LIVE ||
            item.type.equals("live", ignoreCase = true) ||
            item.type.isNullOrBlank()
        ) {
            settingsStore?.setLastChannel(item.resolveId(), item.resolveTitle())
        }
    }

    suspend fun latestHistory(): HistoryEntity? = historyDao.latest()

    suspend fun saveProgress(
        contentId: String,
        type: ContentType,
        title: String,
        poster: String?,
        positionMs: Long,
        durationMs: Long
    ) {
        if (durationMs <= 0L) return
        val ratio = positionMs.toDouble() / durationMs.toDouble()
        if (ratio < 0.03 || ratio > 0.95) {
            continueDao.delete(contentId)
            return
        }
        continueDao.upsert(
            ContinueEntity(
                contentId = contentId,
                type = type.name,
                title = title,
                poster = poster,
                positionMs = positionMs,
                durationMs = durationMs
            )
        )
    }
}
