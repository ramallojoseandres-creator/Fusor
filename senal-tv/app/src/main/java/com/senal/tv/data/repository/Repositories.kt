package com.senal.tv.data.repository

import com.senal.tv.data.api.NetworkModule
import com.senal.tv.data.api.SenalApi
import com.senal.tv.data.local.CacheDao
import com.senal.tv.data.local.CategoryCacheEntity
import com.senal.tv.data.local.ContinueDao
import com.senal.tv.data.local.ContinueEntity
import com.senal.tv.data.local.EpgCacheEntity
import com.senal.tv.data.local.FavoriteDao
import com.senal.tv.data.local.FavoriteEntity
import com.senal.tv.data.local.HistoryDao
import com.senal.tv.data.local.HistoryEntity
import com.senal.tv.data.local.TokenStore
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.CatalogResponse
import com.senal.tv.data.model.Category
import com.senal.tv.data.model.ContentType
import com.senal.tv.data.model.FavoriteRequest
import com.senal.tv.data.model.HealthResponse
import com.senal.tv.data.model.LoginRequest
import com.senal.tv.data.model.PlaybackResponse
import com.senal.tv.data.model.UserInfo
import com.senal.tv.util.CatalogRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import retrofit2.HttpException

class AuthRepository(
    private val api: SenalApi,
    private val tokenStore: TokenStore
) {
    val tokenFlow = tokenStore.tokenFlow

    suspend fun health(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        runCatching { api.health() }.recoverCatching { err ->
            throw friendlyHttp(err, fallback = "Servidor SEÑAL no disponible")
        }
    }

    suspend fun login(username: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val deviceId = tokenStore.deviceId()
            val response = api.login(
                LoginRequest(
                    username = username.trim(),
                    password = password,
                    deviceId = deviceId,
                    deviceName = "SEÑAL TV",
                    platform = "android-tv"
                )
            )
            val token = response.resolveToken()
                ?: throw IllegalStateException(
                    response.resolveError() ?: "El servidor no devolvió token JWT"
                )
            val display = response.user?.displayName()?.ifBlank { null } ?: username.trim()
            tokenStore.saveSession(token, display)
            // Confirma sesión con /api/me (no bloquea el login si falla por red).
            runCatching { api.me().resolveUser() }.getOrNull()?.let { user ->
                tokenStore.saveSession(token, user.displayName().ifBlank { display })
            }
        }.recoverCatching { err ->
            throw friendlyHttp(err, fallback = "No se pudo iniciar sesión")
        }
    }

    /**
     * Valida JWT + dispositivo con GET /api/me.
     * Si el token es inválido/expirado limpia la sesión local.
     */
    suspend fun validateSession(): Result<UserInfo> = withContext(Dispatchers.IO) {
        runCatching {
            if (tokenStore.cachedToken.isNullOrBlank()) {
                error("Sin sesión")
            }
            val me = api.me()
            val user = me.resolveUser()
                ?: error(me.resolveError() ?: "Sesión inválida")
            tokenStore.saveSession(
                token = tokenStore.cachedToken.orEmpty(),
                username = user.displayName()
            )
            user
        }.recoverCatching { err ->
            if (err is HttpException && err.code() in listOf(401, 403)) {
                tokenStore.clear()
            }
            throw friendlyHttp(err, fallback = "Sesión expirada")
        }
    }

    suspend fun logout() = tokenStore.clear()

    suspend fun hasSession(): Boolean = !tokenStore.cachedToken.isNullOrBlank()

    suspend fun username(): String? = tokenStore.username()

    private fun friendlyHttp(err: Throwable, fallback: String): Throwable {
        if (err is HttpException) {
            val body = err.response()?.errorBody()?.string().orEmpty()
            val msg = runCatching {
                NetworkModule.json.decodeFromString(
                    com.senal.tv.data.model.ApiError.serializer(),
                    body
                ).resolveMessage()
            }.getOrNull()
            return IllegalStateException(msg ?: "$fallback (${err.code()})")
        }
        return IllegalStateException(err.message ?: fallback)
    }
}

class CatalogRepository(
    private val api: SenalApi,
    private val cacheDao: CacheDao
) {
    private val categoryMutex = Mutex()
    private val memoryCategories = mutableMapOf<String, List<Category>>()
    private val memoryPages = mutableMapOf<String, CatalogResponse>()

    suspend fun categories(type: String): List<Category> = withContext(Dispatchers.IO) {
        categoryMutex.withLock {
            memoryCategories[type]?.let { return@withContext it }
            // v6: hard-delete non-preferred categories (only user screenshot list + Adultos)
            val cacheKey = "cat-v6-$type"
            cacheDao.getCategory(cacheKey)?.let { cached ->
                runCatching {
                    NetworkModule.json.decodeFromString<List<Category>>(cached.json)
                }.getOrNull()?.let {
                    val ordered = CatalogRules.sortCategories(it)
                    memoryCategories[type] = ordered
                    return@withContext ordered
                }
            }
            val derived = CatalogRules.sortCategories(discoverCategories(type))
            memoryCategories[type] = derived
            cacheDao.putCategory(
                CategoryCacheEntity(
                    key = cacheKey,
                    json = NetworkModule.json.encodeToString(derived),
                    updatedAt = System.currentTimeMillis()
                )
            )
            derived
        }
    }

    /**
     * Prefer server `categories` when present; otherwise page through the catalog
     * until exhaustion so the sidebar is complete (not only the first 100 rows).
     * Non-preferred groups are discarded later by [CatalogRules.sortCategories].
     */
    private suspend fun discoverCategories(type: String): List<Category> {
        val first = fetchCatalog(type = type, page = 1, limit = 200)
        val fromApi = first.categories?.mapNotNull { cat ->
            val label = cat.label().trim()
            if (label.isBlank()) null else Category(id = cat.id ?: label, name = label, title = cat.title)
        }.orEmpty()
        if (fromApi.isNotEmpty()) return fromApi

        val labels = LinkedHashSet<String>()
        fun absorb(response: CatalogResponse) {
            response.resolveItems().forEach { item ->
                val label = item.resolveCategory().trim()
                if (label.isNotBlank() && CatalogRules.isPreferredLabel(label)) {
                    labels += CatalogRules.canonicalLabel(label)
                }
            }
        }
        absorb(first)
        var page = 1
        var hasMore = first.resolveHasMore(200)
        while (hasMore && page < 40) {
            page += 1
            val next = runCatching {
                fetchCatalog(type = type, page = page, limit = 200)
            }.getOrNull() ?: break
            val items = next.resolveItems()
            if (items.isEmpty()) break
            absorb(next)
            hasMore = next.resolveHasMore(200)
        }

        return labels
            .map { Category(id = it, name = it) }
            .ifEmpty {
                CatalogRules.preferredLiveOrder.map { Category(id = it, name = it) }
            }
    }

    suspend fun page(
        type: String,
        category: String? = null,
        page: Int = 1,
        limit: Int = 60
    ): CatalogResponse = withContext(Dispatchers.IO) {
        val key = "$type|${category.orEmpty()}|$page|$limit"
        memoryPages[key]?.let { return@withContext it }
        val response = fetchCatalog(type, category, page, limit)
        val typed = response.resolveItems().filter { item ->
            when (type.lowercase()) {
                "live", "tv", "channel" -> item.contentType() == ContentType.LIVE ||
                    item.type.isNullOrBlank()
                "movie", "vod", "film" -> item.contentType() == ContentType.MOVIE ||
                    item.type.equals("movie", true) || item.type.equals("vod", true)
                "series", "show" -> item.contentType() == ContentType.SERIES ||
                    item.type.equals("series", true)
                else -> true
            }
        }.let { list ->
            // Si el servidor ya filtró por type, no descartar por heurística vacía.
            if (list.isEmpty() && response.resolveItems().isNotEmpty() &&
                response.resolveItems().none { !it.type.isNullOrBlank() }
            ) {
                response.resolveItems()
            } else {
                list.ifEmpty { response.resolveItems() }
            }
        }
        val filtered = if (type.equals("live", ignoreCase = true)) {
            val preferredOnly = typed.filter { item ->
                val label = category?.takeIf { it.isNotBlank() } ?: item.resolveCategory()
                CatalogRules.isPreferredLabel(label)
            }
            response.copy(
                items = preferredOnly,
                channels = preferredOnly,
                data = preferredOnly,
                results = preferredOnly
            )
        } else {
            response.copy(
                items = typed,
                movies = if (type.equals("movie", true)) typed else response.movies,
                series = if (type.equals("series", true)) typed else response.series,
                data = typed,
                results = typed
            )
        }
        filtered.resolveItems().forEach { item ->
            cacheDao.putEpg(
                EpgCacheEntity(
                    channelId = item.resolveId(),
                    nowTitle = item.resolveNow().ifBlank { null },
                    nextTitle = item.resolveNext().ifBlank { null },
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        memoryPages[key] = filtered
        if (memoryPages.size > 80) {
            memoryPages.keys.take(20).forEach { memoryPages.remove(it) }
        }
        filtered
    }

    suspend fun search(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        runCatching { api.search(query.trim()).resolveAll() }
            .recoverCatching {
                fetchCatalog(type = "live", q = query, limit = 30).resolveItems() +
                    fetchCatalog(type = "movie", q = query, limit = 30).resolveItems() +
                    fetchCatalog(type = "series", q = query, limit = 30).resolveItems()
            }
            .getOrDefault(emptyList())
            .distinctBy { it.resolveId() }
    }

    suspend fun playback(id: String, fallbackItem: CatalogItem? = null): PlaybackResponse =
        withContext(Dispatchers.IO) {
            val remote = runCatching { api.playback(id) }.getOrNull()
            val remoteUrl = remote?.resolveUrl()
            if (!remoteUrl.isNullOrBlank()) return@withContext remote!!

            val direct = fallbackItem?.resolveStreamUrl()
                ?: memoryPages.values
                    .asSequence()
                    .flatMap { it.resolveItems().asSequence() }
                    .firstOrNull { it.resolveId() == id }
                    ?.resolveStreamUrl()

            if (!direct.isNullOrBlank()) {
                PlaybackResponse(
                    url = direct,
                    title = fallbackItem?.resolveTitle() ?: remote?.title,
                    logo = fallbackItem?.resolveLogo() ?: remote?.logo,
                    error = remote?.error
                )
            } else {
                remote ?: error("Sin URL de reproducción")
            }
        }

    fun clearMemory() {
        memoryCategories.clear()
        memoryPages.clear()
    }

    private suspend fun fetchCatalog(
        type: String,
        category: String? = null,
        page: Int? = null,
        limit: Int? = null,
        q: String? = null
    ): CatalogResponse {
        return try {
            api.catalog(
                type = type,
                category = category,
                page = page,
                limit = limit,
                offset = if (page != null && limit != null) (page - 1) * limit else null,
                q = q
            )
        } catch (pathMissing: HttpException) {
            when (pathMissing.code()) {
                404 -> api.catalogByPath(type = type, category = category, page = page, limit = limit)
                // Algunos paneles 2.x exponen solo GET /api/catalog sin query type.
                400, 422 -> api.catalog(category = category, page = page, limit = limit, q = q)
                else -> throw pathMissing
            }
        } catch (_: Exception) {
            runCatching {
                api.catalogByPath(type = type, category = category, page = page, limit = limit)
            }.getOrElse {
                api.catalog(category = category, page = page, limit = limit, q = q)
            }
        }
    }
}

class LibraryRepository(
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val continueDao: ContinueDao,
    private val api: SenalApi
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
            runCatching {
                api.addFavorite(FavoriteRequest(id, item.contentType().name.lowercase()))
            }
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
    }

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
