package com.senal.tv.data.local

import android.content.Context
import com.senal.tv.BuildConfig
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.CatalogResponse
import com.senal.tv.data.model.Category
import com.senal.tv.data.model.PlaybackResponse
import com.senal.tv.util.CatalogRules
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Catálogo **fuera de la APK**: descarga JSON.gz preindexado del servidor SEÑAL
 * (`/api/catalog/fast`) con ETag → cache en disco → UI inmediata.
 *
 * Las categorías están disponibles en cuanto se lee la cache (sin esperar al player).
 */
class LocalPlaylistStore(private val context: Context) {

    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Volatile private var loaded = false

    private val all = ArrayList<PlaylistEntry>(10_000)
    private val byId = HashMap<String, PlaylistEntry>(10_000)
    private val groupIndex = LinkedHashMap<String, ArrayList<Int>>(256)
    private val categoriesByType = HashMap<String, List<Category>>(4)

    private val cacheFile = File(context.filesDir, "catalog.fast.json")
    private val etagFile = File(context.filesDir, "catalog.fast.etag")

    private val _categoriesReady = MutableStateFlow(false)
    val categoriesReady: StateFlow<Boolean> = _categoriesReady.asStateFlow()

    private val _catalogVersion = MutableStateFlow(0)
    val catalogVersion: StateFlow<Int> = _catalogVersion.asStateFlow()

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            // 1) Cache local (instantáneo si ya se descargó antes)
            if (cacheFile.exists()) {
                runCatching { applyPayload(json.decodeFromString(FastCatalog.serializer(), cacheFile.readText())) }
                    .onSuccess {
                        loaded = true
                        _categoriesReady.value = true
                    }
            }
            // 2) Red: refresco con ETag (304 = 0 bytes)
            val remote = runCatching { fetchRemote() }.getOrNull()
            if (remote != null) {
                applyPayload(remote)
                loaded = true
                _categoriesReady.value = true
            }
            // 3) Seed mínimo opcional en assets (solo si no hay cache ni red)
            if (!loaded) {
                runCatching { parseAssetFallback() }
                    .onSuccess {
                        loaded = true
                        _categoriesReady.value = true
                    }
            }
            if (!loaded) {
                throw IllegalStateException(
                    "Sin catálogo: conecta el servidor SEÑAL (${BuildConfig.API_BASE_URL}) " +
                        "o importa un M3U en el panel."
                )
            }
        }
    }

    /** Fuerza sync con el servidor (tras login o desde ajustes). */
    suspend fun syncFromServer(authToken: String? = null): Boolean = mutex.withLock {
        val remote = runCatching { fetchRemote(authToken) }.getOrNull() ?: return false
        applyPayload(remote)
        loaded = true
        _categoriesReady.value = true
        true
    }

    suspend fun categories(type: String): List<Category> {
        ensureLoaded()
        return categoriesByType[normalizeType(type)].orEmpty()
    }

    suspend fun page(
        type: String,
        category: String? = null,
        page: Int = 1,
        limit: Int = 60
    ): CatalogResponse {
        ensureLoaded()
        val kind = normalizeType(type)
        val cat = category?.trim().orEmpty()
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, 200)
        val from = (safePage - 1) * safeLimit

        val (total, slice) = when {
            kind == "live" && cat.isNotEmpty() -> {
                val idxs = findGroupIndexes(cat)
                val totalCount = idxs.size
                val end = minOf(from + safeLimit, totalCount)
                val items = if (from >= totalCount) emptyList() else {
                    idxs.subList(from, end).map { all[it].toCatalogItem(displayType = kind) }
                }
                totalCount to items
            }
            kind == "live" && cat.isEmpty() -> {
                val totalCount = all.size
                val end = minOf(from + safeLimit, totalCount)
                val items = if (from >= totalCount) emptyList() else {
                    all.subList(from, end).map { it.toCatalogItem(displayType = kind) }
                }
                totalCount to items
            }
            else -> {
                val filtered = all.asSequence()
                    .filter { matchesType(it, kind) }
                    .filter { cat.isEmpty() || it.group.equals(cat, ignoreCase = true) }
                    .toList()
                val end = minOf(from + safeLimit, filtered.size)
                val items = if (from >= filtered.size) emptyList() else {
                    filtered.subList(from, end).map { it.toCatalogItem(displayType = kind) }
                }
                filtered.size to items
            }
        }

        val cats = categoriesByType[kind].orEmpty()
        return CatalogResponse(
            items = slice,
            categories = if (safePage == 1) cats else null,
            page = safePage,
            limit = safeLimit,
            total = total,
            hasMore = from + slice.size < total
        )
    }

    suspend fun search(query: String, limit: Int = 80): List<CatalogItem> {
        ensureLoaded()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return all.asSequence()
            .filter {
                it.name.lowercase().contains(q) ||
                    it.group.lowercase().contains(q) ||
                    it.tvgId.lowercase().contains(q)
            }
            .take(limit)
            .map { it.toCatalogItem() }
            .toList()
    }

    suspend fun playback(id: String): PlaybackResponse {
        ensureLoaded()
        val entry = byId[id]
            ?: all.firstOrNull { it.id == id }
            ?: throw IllegalStateException("Canal no encontrado en el catálogo")
        val headers = buildMap {
            entry.userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
            entry.referrer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
        }.ifEmpty { null }
        return PlaybackResponse(
            url = entry.url,
            headers = headers,
            type = entry.kind,
            title = entry.name,
            logo = entry.logo
        )
    }

    suspend fun get(id: String): CatalogItem? {
        ensureLoaded()
        return byId[id]?.toCatalogItem()
    }

    suspend fun neighborsFor(id: String, limit: Int = 200): List<CatalogItem> {
        ensureLoaded()
        val entry = byId[id] ?: return emptyList()
        val idxs = findGroupIndexes(entry.group)
        if (idxs.isEmpty()) {
            return all.take(limit).map { it.toCatalogItem() }
        }
        return idxs.asSequence().take(limit).map { all[it].toCatalogItem() }.toList()
    }

    fun clearMemory() {
        // keep in memory
    }

    private fun findGroupIndexes(group: String): List<Int> {
        groupIndex[group]?.let { return it }
        val hit = groupIndex.entries.firstOrNull { it.key.equals(group, ignoreCase = true) }
        return hit?.value.orEmpty()
    }

    private fun fetchRemote(authToken: String? = null): FastCatalog? {
        val base = BuildConfig.API_BASE_URL.trimEnd('/') + "/"
        val url = base + "api/catalog/fast"
        val etag = if (etagFile.exists()) etagFile.readText().trim() else ""
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "identity") // we handle gzip body ourselves if server sends Content-Encoding
        if (etag.isNotBlank()) reqBuilder.header("If-None-Match", etag)
        if (!authToken.isNullOrBlank()) reqBuilder.header("Authorization", "Bearer $authToken")

        http.newCall(reqBuilder.build()).execute().use { resp ->
            if (resp.code == 304) return null // cache válida
            if (!resp.isSuccessful) {
                throw IllegalStateException("Catálogo HTTP ${resp.code}")
            }
            val raw = resp.body?.bytes() ?: return null
            val text = decodePossiblyGzip(raw, resp.header("Content-Encoding"))
            val payload = json.decodeFromString(FastCatalog.serializer(), text)
            cacheFile.writeText(text)
            val newEtag = resp.header("ETag")?.trim()?.trim('"').orEmpty()
            if (newEtag.isNotBlank()) etagFile.writeText(newEtag)
            return payload
        }
    }

    private fun decodePossiblyGzip(bytes: ByteArray, encoding: String?): String {
        val isGzip = encoding?.contains("gzip", ignoreCase = true) == true ||
            (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
        val stream = if (isGzip) GZIPInputStream(ByteArrayInputStream(bytes)) else ByteArrayInputStream(bytes)
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun applyPayload(payload: FastCatalog) {
        all.clear()
        byId.clear()
        groupIndex.clear()
        categoriesByType.clear()

        // Prefer categoryOrder from server; fall back to categories list / appearance.
        val order = payload.categoryOrder
            ?.takeIf { it.isNotEmpty() }
            ?: payload.categories?.map { it.name ?: it.title ?: it.id.orEmpty() }?.filter { it.isNotBlank() }
            ?: emptyList()

        val channels = payload.channels.orEmpty()
        channels.forEachIndexed { index, ch ->
            val group = (ch.group ?: ch.category ?: "Variados").ifBlank { "Variados" }
            val name = (ch.title ?: ch.name ?: "Canal ${index + 1}").ifBlank { "Canal ${index + 1}" }
            val id = ch.id?.takeIf { it.isNotBlank() }
                ?: stableId(ch.tvgId.orEmpty(), name, ch.url ?: ch.streamUrl.orEmpty(), index + 1)
            val url = (ch.url ?: ch.streamUrl).orEmpty()
            if (url.isBlank()) return@forEachIndexed
            val entry = PlaylistEntry(
                id = id,
                number = ch.number ?: (index + 1),
                name = name,
                logo = ch.logo ?: ch.poster,
                group = group,
                tvgId = ch.tvgId.orEmpty(),
                url = url,
                userAgent = ch.userAgent,
                referrer = ch.referrer,
                kind = normalizeType(ch.type ?: "live")
            )
            val pos = all.size
            all += entry
            byId[id] = entry
            groupIndex.getOrPut(group) { ArrayList(64) }.add(pos)
        }

        // Ensure empty categories from server order still appear
        for (g in order) {
            groupIndex.putIfAbsent(g, ArrayList(0))
        }

        val liveCats = if (order.isNotEmpty()) {
            order.map { name ->
                Category(id = name, name = name, title = name, count = groupIndex[name]?.size ?: 0)
            }.filter { (it.count ?: 0) > 0 || groupIndex.containsKey(it.label()) }
        } else {
            groupIndex.map { (name, idxs) ->
                Category(id = name, name = name, title = name, count = idxs.size)
            }
        }
        categoriesByType["live"] = CatalogRules.sortCategories(liveCats)

        fun typedCats(predicate: (PlaylistEntry) -> Boolean): List<Category> {
            val counts = LinkedHashMap<String, Int>()
            all.forEach { e ->
                if (predicate(e)) counts[e.group] = (counts[e.group] ?: 0) + 1
            }
            return CatalogRules.sortCategories(
                counts.map { (name, count) ->
                    Category(id = name, name = name, title = name, count = count)
                }
            )
        }
        categoriesByType["movie"] = typedCats { isMovieGroup(it.group, it.name) }
        categoriesByType["series"] = typedCats { isSeriesGroup(it.group, it.name) }

        _catalogVersion.value = _catalogVersion.value + 1
        _categoriesReady.value = true
    }

    /** Fallback opcional: assets/catalog/* solo si el servidor no responde y no hay cache. */
    private fun parseAssetFallback() {
        val names = runCatching { context.assets.list("catalog").orEmpty().toList() }.getOrDefault(emptyList())
        val assetName = when {
            names.any { it == "lista_fusionada.m3u.gz" } -> "catalog/lista_fusionada.m3u.gz"
            names.any { it == "lista_fusionada.m3u" } -> "catalog/lista_fusionada.m3u"
            names.any { it == "catalog.fast.json.gz" } -> "catalog/catalog.fast.json.gz"
            names.any { it == "catalog.fast.json" } -> "catalog/catalog.fast.json"
            else -> return
        }
        if (assetName.endsWith(".json") || assetName.endsWith(".json.gz")) {
            context.assets.open(assetName).use { input ->
                val bytes = input.readBytes()
                val text = decodePossiblyGzip(bytes, if (assetName.endsWith(".gz")) "gzip" else null)
                applyPayload(json.decodeFromString(FastCatalog.serializer(), text))
            }
            return
        }
        // Legacy M3U seed (no debe ir en builds de producción)
        all.clear(); byId.clear(); groupIndex.clear(); categoriesByType.clear()
        context.assets.open(assetName).use { input ->
            val stream = if (assetName.endsWith(".gz")) GZIPInputStream(input) else input
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 64 * 1024).use { reader ->
                var pending: ExtInf? = null
                var index = 0
                while (true) {
                    val lineRaw = reader.readLine() ?: break
                    val line = lineRaw.trim()
                    if (line.isEmpty()) continue
                    when {
                        line.startsWith("#EXTINF", ignoreCase = true) -> pending = parseExtInf(line)
                        line.startsWith("#") -> Unit
                        else -> {
                            val ext = pending
                            pending = null
                            if (ext == null || line.isEmpty()) continue
                            index += 1
                            val group = ext.group.ifBlank { "Variados" }
                            val id = stableId(ext.tvgId, ext.name, line, index)
                            val entry = PlaylistEntry(
                                id = id, number = index,
                                name = ext.name.ifBlank { "Canal $index" },
                                logo = ext.logo, group = group, tvgId = ext.tvgId,
                                url = line, userAgent = ext.userAgent, referrer = ext.referrer, kind = "live"
                            )
                            val pos = all.size
                            all += entry
                            byId[id] = entry
                            groupIndex.getOrPut(group) { ArrayList(64) }.add(pos)
                        }
                    }
                }
            }
        }
        val liveCats = groupIndex.map { (name, idxs) ->
            Category(id = name, name = name, title = name, count = idxs.size)
        }
        categoriesByType["live"] = CatalogRules.sortCategories(liveCats)
        categoriesByType["movie"] = emptyList()
        categoriesByType["series"] = emptyList()
        _catalogVersion.value = _catalogVersion.value + 1
    }

    private data class ExtInf(
        val name: String,
        val group: String,
        val logo: String?,
        val tvgId: String,
        val userAgent: String?,
        val referrer: String?
    )

    private fun parseExtInf(line: String): ExtInf {
        val comma = line.lastIndexOf(',')
        val name = if (comma >= 0) line.substring(comma + 1).trim() else ""
        val attrsPart = if (comma >= 0) line.substring(0, comma) else line
        return ExtInf(
            name = name,
            group = attrFast(attrsPart, "group-title") ?: "Variados",
            logo = attrFast(attrsPart, "tvg-logo"),
            tvgId = attrFast(attrsPart, "tvg-id").orEmpty(),
            userAgent = attrFast(attrsPart, "http-user-agent") ?: attrFast(attrsPart, "user-agent"),
            referrer = attrFast(attrsPart, "http-referrer") ?: attrFast(attrsPart, "referrer")
        )
    }

    private fun attrFast(source: String, key: String): String? {
        val needle = "$key=\""
        val start = source.indexOf(needle, ignoreCase = true)
        if (start < 0) return null
        val valueStart = start + needle.length
        val end = source.indexOf('"', valueStart)
        if (end < 0) return null
        return source.substring(valueStart, end).trim().ifBlank { null }
    }

    private fun matchesType(entry: PlaylistEntry, kind: String): Boolean = when (kind) {
        "movie" -> isMovieGroup(entry.group, entry.name)
        "series" -> isSeriesGroup(entry.group, entry.name)
        else -> true
    }

    private fun isSeriesGroup(group: String, name: String): Boolean {
        val hay = "$group $name".lowercase()
        return hay.contains("serie") || hay.contains("series")
    }

    private fun isMovieGroup(group: String, name: String): Boolean {
        val hay = "$group $name".lowercase()
        if (isSeriesGroup(group, name)) return false
        return hay.contains("película") || hay.contains("pelicula") ||
            hay.contains("cine") || hay.contains("movie") || hay.contains("vod")
    }

    private fun stableId(tvgId: String, name: String, url: String, index: Int): String {
        return when {
            tvgId.isNotBlank() -> "tvg:$tvgId"
            name.isNotBlank() -> "m3u:${name.lowercase().hashCode()}:$index"
            else -> "url:${url.hashCode()}:$index"
        }
    }

    private fun normalizeType(type: String): String = when (type.lowercase()) {
        "movie", "movies", "vod", "film" -> "movie"
        "series", "show", "shows" -> "series"
        else -> "live"
    }

    @Serializable
    data class FastCatalog(
        val version: Int? = null,
        val generatedAt: String? = null,
        val playlistName: String? = null,
        val categories: List<Category>? = null,
        val categoryOrder: List<String>? = null,
        val channels: List<FastChannel>? = null,
        val total: Int? = null
    )

    @Serializable
    data class FastChannel(
        val id: String? = null,
        val number: Int? = null,
        val title: String? = null,
        val name: String? = null,
        val group: String? = null,
        val category: String? = null,
        val type: String? = null,
        val logo: String? = null,
        val poster: String? = null,
        val url: String? = null,
        val streamUrl: String? = null,
        val tvgId: String? = null,
        val userAgent: String? = null,
        val referrer: String? = null,
        val sort: Int? = null
    )

    data class PlaylistEntry(
        val id: String,
        val number: Int,
        val name: String,
        val logo: String?,
        val group: String,
        val tvgId: String,
        val url: String,
        val userAgent: String?,
        val referrer: String?,
        val kind: String
    ) {
        fun toCatalogItem(displayType: String = kind): CatalogItem = CatalogItem(
            id = id,
            streamId = id,
            name = name,
            title = name,
            number = number,
            channelNumber = number,
            logo = logo,
            poster = logo,
            category = group,
            group = group,
            type = displayType,
            url = url,
            streamUrl = url,
            userAgent = userAgent
        )
    }
}
