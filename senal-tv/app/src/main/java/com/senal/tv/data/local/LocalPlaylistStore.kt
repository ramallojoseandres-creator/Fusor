package com.senal.tv.data.local

import android.content.Context
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.CatalogResponse
import com.senal.tv.data.model.Category
import com.senal.tv.data.model.PlaybackResponse
import com.senal.tv.util.CatalogRules
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * Catálogo embebido desde `assets/catalog/lista_fusionada.m3u(.gz)`.
 * El servidor SEÑAL solo autentica usuarios; el contenido no viaja por la API.
 */
class LocalPlaylistStore(private val context: Context) {

    private val mutex = Mutex()
    @Volatile private var loaded = false

    private val all = mutableListOf<PlaylistEntry>()
    private val byId = LinkedHashMap<String, PlaylistEntry>()
    private val categoriesByType = mutableMapOf<String, List<Category>>()

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            parseAsset()
            loaded = true
        }
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
        val filtered = all.asSequence()
            .filter { matchesType(it, kind) }
            .filter { cat.isEmpty() || it.group.equals(cat, ignoreCase = true) }
            .toList()
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, 200)
        val from = (safePage - 1) * safeLimit
        val slice = if (from >= filtered.size) emptyList() else {
            filtered.subList(from, minOf(from + safeLimit, filtered.size))
        }
        val cats = categoriesByType[kind].orEmpty()
        return CatalogResponse(
            items = slice.map { it.toCatalogItem(displayType = kind) },
            categories = if (safePage == 1) cats else null,
            page = safePage,
            limit = safeLimit,
            total = filtered.size,
            hasMore = from + slice.size < filtered.size
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
            ?: throw IllegalStateException("Canal no encontrado en la lista local")
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

    fun clearMemory() {
        // Keep parse in memory; only wipe category memo if needed in future.
    }

    private fun parseAsset() {
        all.clear()
        byId.clear()
        categoriesByType.clear()

        val assetName = resolveAssetName()
        val raw = context.assets.open(assetName).use { input ->
            val stream = if (assetName.endsWith(".gz")) GZIPInputStream(input) else input
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                reader.readLines()
            }
        }

        var pending: ExtInf? = null
        var index = 0
        for (lineRaw in raw) {
            val line = lineRaw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = parseExtInf(line)
                }
                line.startsWith("#") -> {
                    // ignore other tags
                }
                else -> {
                    val ext = pending
                    pending = null
                    if (ext == null) continue
                    val url = line.trim()
                    if (url.isEmpty()) continue
                    index += 1
                    val group = ext.group.ifBlank { "Variados" }
                    val id = stableId(ext.tvgId, ext.name, url, index)
                    val entry = PlaylistEntry(
                        id = id,
                        number = index,
                        name = ext.name.ifBlank { "Canal $index" },
                        logo = ext.logo,
                        group = group,
                        tvgId = ext.tvgId,
                        url = url,
                        userAgent = ext.userAgent,
                        referrer = ext.referrer,
                        kind = "live" // M3U = streams en vivo; movie/series filtran por grupo
                    )
                    all += entry
                    byId[id] = entry
                }
            }
        }

        fun buildCats(filter: (PlaylistEntry) -> Boolean): List<Category> {
            val counts = LinkedHashMap<String, Int>()
            all.filter(filter).forEach { e ->
                counts[e.group] = (counts[e.group] ?: 0) + 1
            }
            val cats = counts.map { (name, count) ->
                Category(id = name, name = name, title = name, count = count)
            }
            return CatalogRules.sortCategories(cats)
        }

        categoriesByType["live"] = buildCats { true }
        categoriesByType["movie"] = buildCats { isMovieGroup(it.group, it.name) }
        categoriesByType["series"] = buildCats { isSeriesGroup(it.group, it.name) }
    }

    private fun resolveAssetName(): String {
        val names = context.assets.list("catalog").orEmpty().toList()
        return when {
            names.any { it == "lista_fusionada.m3u.gz" } -> "catalog/lista_fusionada.m3u.gz"
            names.any { it == "lista_fusionada.m3u" } -> "catalog/lista_fusionada.m3u"
            else -> throw IllegalStateException(
                "Falta assets/catalog/lista_fusionada.m3u(.gz) — lista embebida"
            )
        }
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
        fun attr(key: String): String? {
            val regex = Regex("""(?i)${Regex.escape(key)}="([^"]*)"""")
            return regex.find(attrsPart)?.groupValues?.getOrNull(1)?.trim()?.ifBlank { null }
        }
        return ExtInf(
            name = name,
            group = attr("group-title") ?: "Variados",
            logo = attr("tvg-logo"),
            tvgId = attr("tvg-id").orEmpty(),
            userAgent = attr("http-user-agent") ?: attr("user-agent"),
            referrer = attr("http-referrer") ?: attr("referrer")
        )
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
        val base = when {
            tvgId.isNotBlank() -> "tvg:${tvgId}"
            name.isNotBlank() -> "m3u:${name.lowercase().hashCode()}:$index"
            else -> "url:${url.hashCode()}:$index"
        }
        return base
    }

    private fun normalizeType(type: String): String = when (type.lowercase()) {
        "movie", "movies", "vod", "film" -> "movie"
        "series", "show", "shows" -> "series"
        else -> "live"
    }

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
