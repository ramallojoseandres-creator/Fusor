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
import com.senal.tv.data.local.PlaylistSync
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
 * Autentica contra el servidor SEÑAL y dispara sync de playlist (gzip + ETag).
 * Streams siguen siendo URLs directas del M3U (no proxy).
 */
class AuthRepository(
    private val api: SenalApi,
    private val tokenStore: TokenStore,
    private val playlistSync: PlaylistSync? = null
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
                    deviceName = "SENAL TV",
                    platform = "android-tv"
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
            // NO descargar aquí: CatalogLoadingScreen lo hace UNA vez si no hay caché.
            // Así el login es rápido y el mensaje «Cargando todos los canales…» es claro.
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
 * Catálogo local EN VIVO (M3U SEÑAL) + VOD Daniel65 (películas / series).
 */
class CatalogRepository(
    private val playlist: LocalPlaylistStore,
    private val danielVod: com.senal.tv.data.local.DanielVodStore? = null
) {
    suspend fun categories(type: String): List<Category> = withContext(Dispatchers.IO) {
        when (type.lowercase()) {
            "movie", "movies", "vod" ->
                danielVod?.categories(com.senal.tv.data.local.DanielVodStore.Kind.MOVIES)
                    ?: playlist.categories(type)
            "series", "show", "shows" ->
                danielVod?.categories(com.senal.tv.data.local.DanielVodStore.Kind.SERIES)
                    ?: playlist.categories(type)
            else -> playlist.categories(type)
        }
    }

    /** Categories with optional adult filter for parental lock. */
    suspend fun categories(type: String, hideAdults: Boolean): List<Category> {
        val all = categories(type)
        val sorted = when (type.lowercase()) {
            "movie", "movies", "vod", "series", "show", "shows" -> all
            else -> com.senal.tv.util.CatalogRules.sortCategories(all)
        }
        return if (!hideAdults) {
            sorted
        } else {
            sorted.filterNot { com.senal.tv.util.CatalogRules.isAdultLabel(it.label()) }
        }
    }

    suspend fun get(id: String): CatalogItem? = withContext(Dispatchers.IO) {
        playlist.get(id) ?: danielVod?.get(id)
    }

    suspend fun page(
        type: String,
        category: String? = null,
        page: Int = 1,
        limit: Int = 60
    ): CatalogResponse = withContext(Dispatchers.IO) {
        val kind = type.lowercase()
        when {
            (kind == "movie" || kind == "movies" || kind == "vod") && danielVod != null ->
                danielVod.page(
                    kind = com.senal.tv.data.local.DanielVodStore.Kind.MOVIES,
                    category = category,
                    page = page,
                    limit = limit
                )
            (kind == "series" || kind == "show" || kind == "shows") && danielVod != null ->
                danielVod.page(
                    kind = com.senal.tv.data.local.DanielVodStore.Kind.SERIES,
                    category = category,
                    page = page,
                    limit = limit
                )
            else -> playlist.page(type = type, category = category, page = page, limit = limit)
        }
    }

    suspend fun search(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        val live = playlist.search(query)
        val vod = danielVod?.search(query, com.senal.tv.data.local.DanielVodStore.Kind.ALL).orEmpty()
        (live + vod).distinctBy { it.resolveId() }.take(100)
    }

    suspend fun playback(id: String): PlaybackResponse = withContext(Dispatchers.IO) {
        runCatching { playlist.playback(id) }.getOrElse {
            danielVod?.playback(id) ?: throw it
        }
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
