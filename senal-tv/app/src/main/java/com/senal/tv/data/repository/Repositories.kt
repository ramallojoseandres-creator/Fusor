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
import com.senal.tv.data.model.LoginRequest
import com.senal.tv.data.model.PlaybackResponse
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
            tokenStore.saveSession(token, username.trim())
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
            // v5: preferred order + Adultos last
            val cacheKey = "cat-v5-$type"
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
                if (label.isNotBlank()) labels += label
            }
        }
        absorb(first)
        var page = 1
        var hasMore = first.resolveHasMore(200)
        // Safety cap: enough pages for large IPTV catalogs without hanging cold start.
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
                listOf(
                    "Deportes", "Noticias", "Infantil", "USA", "España",
                    "Latinos", "Música", "4K", "Documentales", "General"
                ).map { Category(id = it, name = it) }
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
        response.resolveItems().forEach { item ->
            cacheDao.putEpg(
                EpgCacheEntity(
                    channelId = item.resolveId(),
                    nowTitle = item.resolveNow().ifBlank { null },
                    nextTitle = item.resolveNext().ifBlank { null },
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        memoryPages[key] = response
        // keep memory bounded
        if (memoryPages.size > 80) {
            memoryPages.keys.take(20).forEach { memoryPages.remove(it) }
        }
        response
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

    suspend fun playback(id: String): PlaybackResponse = withContext(Dispatchers.IO) {
        api.playback(id)
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
            if (pathMissing.code() == 404) {
                api.catalogByPath(type = type, category = category, page = page, limit = limit)
            } else {
                throw pathMissing
            }
        } catch (_: Exception) {
            // try documented path style as secondary
            api.catalogByPath(type = type, category = category, page = page, limit = limit)
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
