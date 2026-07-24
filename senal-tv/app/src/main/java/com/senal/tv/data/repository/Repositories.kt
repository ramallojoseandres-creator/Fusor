package com.senal.tv.data.repository

import com.senal.tv.data.api.NetworkModule
import com.senal.tv.data.api.SenalApi
import com.senal.tv.data.local.CacheDao
import com.senal.tv.data.local.CatalogSnapshotEntity
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
            Unit
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
    private val loadMutex = Mutex()
    private val refreshMutex = Mutex()

    @Volatile private var snapshotItems: List<CatalogItem> = emptyList()
    @Volatile private var snapshotLoadedAt: Long = 0L
    private val memoryCategories = mutableMapOf<String, List<Category>>()
    private val memoryPages = mutableMapOf<String, CatalogResponse>()

    private val snapshotKey = "catalog-v1"
    private val snapshotTtlMs = 12L * 60L * 60L * 1000L

    fun isLoaded(): Boolean = snapshotItems.isNotEmpty()

    /**
     * Loads the full catalog once (memory → disk → network) so live/movies
     * screens can page without waiting on the network.
     */
    suspend fun ensureCatalogLoaded(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            if (!force && snapshotItems.isNotEmpty()) return@withContext true
            if (!force) {
                cacheDao.getSnapshot(snapshotKey)?.let { cached ->
                    if (System.currentTimeMillis() - cached.updatedAt < snapshotTtlMs) {
                        runCatching {
                            NetworkModule.json.decodeFromString<List<CatalogItem>>(cached.json)
                        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { items ->
                            applySnapshot(items, cached.updatedAt)
                            return@withContext true
                        }
                    }
                }
            }
            val remote = downloadFullCatalog()
            if (remote.isNotEmpty()) {
                applySnapshot(remote, System.currentTimeMillis())
                cacheDao.putSnapshot(
                    CatalogSnapshotEntity(
                        key = snapshotKey,
                        json = NetworkModule.json.encodeToString(remote),
                        updatedAt = snapshotLoadedAt
                    )
                )
                true
            } else {
                snapshotItems.isNotEmpty()
            }
        }
    }

    fun refreshInBackground() {
        // Prefer calling refreshInBackgroundSuspend() from a coroutine.
    }

    suspend fun refreshInBackgroundSuspend() = withContext(Dispatchers.IO) {
        if (!refreshMutex.tryLock()) return@withContext
        try {
            ensureCatalogLoaded(force = true)
        } finally {
            refreshMutex.unlock()
        }
    }

    suspend fun categories(type: String): List<Category> = withContext(Dispatchers.IO) {
        ensureCatalogLoaded()
        memoryCategories[type]?.let { return@withContext it }
        val derived = CatalogRules.sortCategories(categoriesFromSnapshot(type))
        memoryCategories[type] = derived
        cacheDao.putCategory(
            CategoryCacheEntity(
                key = "cat-v7-$type",
                json = NetworkModule.json.encodeToString(derived),
                updatedAt = System.currentTimeMillis()
            )
        )
        derived
    }

    suspend fun page(
        type: String,
        category: String? = null,
        page: Int = 1,
        limit: Int = 60
    ): CatalogResponse = withContext(Dispatchers.IO) {
        ensureCatalogLoaded()
        val key = "$type|${category.orEmpty()}|$page|$limit"
        memoryPages[key]?.let { return@withContext it }

        val filtered = filterSnapshot(type, category)
        val from = ((page - 1) * limit).coerceAtLeast(0)
        val slice = if (from >= filtered.size) emptyList() else filtered.drop(from).take(limit)
        val hasMore = from + slice.size < filtered.size
        val response = CatalogResponse(
            items = slice,
            channels = if (type.equals("live", true)) slice else null,
            movies = if (type.equals("movie", true)) slice else null,
            series = if (type.equals("series", true)) slice else null,
            page = page,
            limit = limit,
            total = filtered.size,
            hasMore = hasMore
        )
        memoryPages[key] = response
        response
    }

    suspend fun search(query: String): List<CatalogItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        ensureCatalogLoaded()
        val q = query.trim().lowercase()
        val local = snapshotItems.filter {
            it.resolveTitle().lowercase().contains(q) ||
                it.resolveCategory().lowercase().contains(q)
        }
        if (local.isNotEmpty()) return@withContext local.distinctBy { it.resolveId() }
        runCatching { api.search(query.trim()).resolveAll() }
            .getOrDefault(emptyList())
            .distinctBy { it.resolveId() }
    }

    /** Prefer stream URL already in catalog — no wait on /api/playback for zapping. */
    suspend fun playback(id: String, fallbackItem: CatalogItem? = null): PlaybackResponse =
        withContext(Dispatchers.IO) {
            val direct = fallbackItem?.resolveStreamUrl()
                ?: snapshotItems.firstOrNull { it.resolveId() == id }?.resolveStreamUrl()
                ?: memoryPages.values
                    .asSequence()
                    .flatMap { it.resolveItems().asSequence() }
                    .firstOrNull { it.resolveId() == id }
                    ?.resolveStreamUrl()

            if (!direct.isNullOrBlank()) {
                return@withContext PlaybackResponse(
                    url = direct,
                    title = fallbackItem?.resolveTitle(),
                    logo = fallbackItem?.resolveLogo()
                )
            }

            val remote = runCatching { api.playback(id) }.getOrNull()
            val remoteUrl = remote?.resolveUrl()
            if (!remoteUrl.isNullOrBlank()) return@withContext remote!!
            remote ?: error("Sin URL de reproducción")
        }

    fun clearMemory() {
        memoryCategories.clear()
        memoryPages.clear()
        snapshotItems = emptyList()
        snapshotLoadedAt = 0L
    }

    suspend fun clearAllCaches() = withContext(Dispatchers.IO) {
        clearMemory()
        cacheDao.clearSnapshots()
        cacheDao.clearCategories()
    }

    private fun applySnapshot(items: List<CatalogItem>, loadedAt: Long) {
        snapshotItems = items
        snapshotLoadedAt = loadedAt
        memoryCategories.clear()
        memoryPages.clear()
    }

    private fun categoriesFromSnapshot(type: String): List<Category> {
        val labels = LinkedHashSet<String>()
        filterSnapshot(type, category = null).forEach { item ->
            val label = item.resolveCategory().trim()
            if (label.isNotBlank()) {
                if (!type.equals("live", true) || CatalogRules.isPreferredLabel(label)) {
                    labels += CatalogRules.canonicalLabel(label)
                }
            }
        }
        return labels.map { Category(id = it, name = it) }.ifEmpty {
            if (type.equals("live", true)) {
                CatalogRules.preferredLiveOrder.map { Category(id = it, name = it) }
            } else emptyList()
        }
    }

    private fun filterSnapshot(type: String, category: String?): List<CatalogItem> {
        val typed = snapshotItems.filter { item ->
            when (type.lowercase()) {
                "live", "tv", "channel" ->
                    item.contentType() == ContentType.LIVE || item.type.isNullOrBlank()
                "movie", "vod", "film" ->
                    item.contentType() == ContentType.MOVIE ||
                        item.type.equals("movie", true) || item.type.equals("vod", true)
                "series", "show" ->
                    item.contentType() == ContentType.SERIES || item.type.equals("series", true)
                else -> true
            }
        }.let { list ->
            if (list.isEmpty() && snapshotItems.isNotEmpty() &&
                snapshotItems.none { !it.type.isNullOrBlank() }
            ) snapshotItems else list.ifEmpty { snapshotItems }
        }

        val byCategory = if (category.isNullOrBlank()) {
            typed
        } else {
            val wanted = CatalogRules.canonicalLabel(category)
            typed.filter {
                CatalogRules.canonicalLabel(it.resolveCategory()).equals(wanted, ignoreCase = true)
            }
        }

        return if (type.equals("live", true)) {
            byCategory.filter { CatalogRules.isPreferredLabel(it.resolveCategory()) }
        } else {
            byCategory
        }
    }

    private suspend fun downloadFullCatalog(): List<CatalogItem> {
        // Prefer a single dump from the panel.
        val bulk = runCatching { api.catalog(limit = 20_000) }.getOrNull()
        val bulkItems = bulk?.resolveItems().orEmpty()
        if (bulkItems.size >= 50) {
            return bulkItems.distinctBy { it.resolveId() }
        }

        val collected = LinkedHashMap<String, CatalogItem>()
        fun absorb(response: CatalogResponse?) {
            response?.resolveItems()?.forEach { collected[it.resolveId()] = it }
        }
        absorb(bulk)
        listOf("live", "movie", "series").forEach { type ->
            var page = 1
            var hasMore = true
            while (hasMore && page <= 30) {
                val response = runCatching {
                    fetchCatalog(type = type, page = page, limit = 200)
                }.getOrNull() ?: break
                val items = response.resolveItems()
                if (items.isEmpty()) break
                absorb(response)
                hasMore = response.resolveHasMore(200)
                page += 1
            }
        }
        return collected.values.toList()
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
