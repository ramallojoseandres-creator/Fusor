package com.senal.tv.data.local

import android.content.Context
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.CatalogResponse
import com.senal.tv.data.model.Category
import com.senal.tv.data.model.PlaybackResponse
import com.senal.tv.util.CatalogRules
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Peliculas / series desde las listas Daniel65 embebidas (assets/vod, M3U gzip).
 * Sustituye el catalogo iptv-org y el mural estilo cine.
 */
class DanielVodStore(private val context: Context) {

    companion object {
        private const val MOVIES_ASSET = "vod/daniel65_peliculas.m3u.gz"
        private const val SERIES_ASSET = "vod/daniel65_series.m3u.gz"
    }

    private val mutex = Mutex()

    private var movies: List<CatalogItem> = emptyList()
    private var series: List<CatalogItem> = emptyList()
    private val moviesByCat = LinkedHashMap<String, ArrayList<CatalogItem>>()
    private val seriesByCat = LinkedHashMap<String, ArrayList<CatalogItem>>()
    private val movieCats = ArrayList<Category>()
    private val seriesCats = ArrayList<Category>()
    private val byId = LinkedHashMap<String, CatalogItem>()

    suspend fun ensureLoaded(kind: Kind = Kind.ALL): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            when (kind) {
                Kind.MOVIES -> if (movies.isEmpty()) loadMoviesLocked()
                Kind.SERIES -> if (series.isEmpty()) loadSeriesLocked()
                Kind.ALL -> {
                    if (movies.isEmpty()) loadMoviesLocked()
                    if (series.isEmpty()) loadSeriesLocked()
                }
            }
        }
    }

    suspend fun categories(kind: Kind): List<Category> = withContext(Dispatchers.IO) {
        ensureLoaded(kind)
        when (kind) {
            Kind.MOVIES -> movieCats.toList()
            Kind.SERIES -> seriesCats.toList()
            Kind.ALL -> movieCats + seriesCats
        }
    }

    suspend fun page(
        kind: Kind,
        category: String?,
        page: Int = 1,
        limit: Int = 60
    ): CatalogResponse = withContext(Dispatchers.IO) {
        ensureLoaded(kind)
        val source = itemsFor(kind, category)
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, 120)
        val from = (safePage - 1) * safeLimit
        val total = source.size
        val end = minOf(from + safeLimit, total)
        val slice = if (from >= total) emptyList() else source.subList(from, end)
        CatalogResponse(
            items = slice,
            page = safePage,
            limit = safeLimit,
            total = total,
            hasMore = end < total
        )
    }

    suspend fun get(id: String): CatalogItem? = withContext(Dispatchers.IO) {
        ensureLoaded(Kind.ALL)
        byId[id]
    }

    suspend fun playback(id: String): PlaybackResponse = withContext(Dispatchers.IO) {
        ensureLoaded(Kind.ALL)
        val item = byId[id] ?: throw IllegalStateException("Título no encontrado")
        val url = item.resolveStreamUrl() ?: throw IllegalStateException("Sin URL")
        val headers = buildMap {
            item.userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
        }.ifEmpty { null }
        PlaybackResponse(
            url = url,
            headers = headers,
            type = item.type ?: "movie",
            title = item.resolveTitle(),
            logo = item.resolvePoster()
        )
    }

    suspend fun search(query: String, kind: Kind): List<CatalogItem> = withContext(Dispatchers.IO) {
        ensureLoaded(kind)
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()
        val pool = when (kind) {
            Kind.MOVIES -> movies
            Kind.SERIES -> series
            Kind.ALL -> movies + series
        }
        pool.asSequence()
            .filter {
                it.resolveTitle().lowercase().contains(q) ||
                    it.resolveCategory().lowercase().contains(q)
            }
            .take(80)
            .toList()
    }

    private fun itemsFor(kind: Kind, category: String?): List<CatalogItem> {
        val map = when (kind) {
            Kind.MOVIES -> moviesByCat
            Kind.SERIES -> seriesByCat
            Kind.ALL -> LinkedHashMap<String, ArrayList<CatalogItem>>().apply {
                putAll(moviesByCat)
                seriesByCat.forEach { (k, v) -> getOrPut(k) { ArrayList() }.addAll(v) }
            }
        }
        val all = when (kind) {
            Kind.MOVIES -> movies
            Kind.SERIES -> series
            Kind.ALL -> movies + series
        }
        if (category.isNullOrBlank()) return all
        val key = category.trim()
        map[key]?.let { return it }
        return map.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: emptyList()
    }

    private fun loadMoviesLocked() {
        val parsed = parseAsset(MOVIES_ASSET, type = "movie", idPrefix = "dmovie")
        movies = parsed
        rebuildIndex(parsed, moviesByCat, movieCats)
        parsed.forEach { byId[it.resolveId()] = it }
    }

    private fun loadSeriesLocked() {
        val parsed = parseAsset(SERIES_ASSET, type = "series", idPrefix = "dseries")
        series = parsed
        rebuildIndex(parsed, seriesByCat, seriesCats)
        parsed.forEach { byId[it.resolveId()] = it }
    }

    private fun rebuildIndex(
        items: List<CatalogItem>,
        into: LinkedHashMap<String, ArrayList<CatalogItem>>,
        catsOut: ArrayList<Category>
    ) {
        into.clear()
        catsOut.clear()
        for (item in items) {
            val g = item.resolveCategory().ifBlank { "General" }
            into.getOrPut(g) { ArrayList() }.add(item)
        }
        val ordered = into.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, ArrayList<CatalogItem>>> { it.value.size }
                    .thenBy { it.key.lowercase() }
            )
        ordered.forEach { (name, list) ->
            catsOut += Category(id = name, name = name, title = name, count = list.size)
        }
    }

    private fun parseAsset(assetPath: String, type: String, idPrefix: String): List<CatalogItem> {
        val out = ArrayList<CatalogItem>(if (type == "series") 70_000 else 18_000)
        context.assets.open(assetPath).use { raw ->
            GZIPInputStream(raw).use { gz ->
                BufferedReader(InputStreamReader(gz, Charsets.UTF_8), 64 * 1024).use { reader ->
                    var pending: Ext? = null
                    var pendingUa: String? = null
                    var index = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#EXTM3U")) continue
                        when {
                            trimmed.startsWith("#EXTINF", ignoreCase = true) -> {
                                pending = parseExtInf(trimmed)
                                pendingUa = pending.userAgent
                            }
                            trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                                val opt = trimmed.substringAfter(':')
                                if (opt.startsWith("http-user-agent=", ignoreCase = true)) {
                                    pendingUa = opt.substringAfter('=').trim().trim('"')
                                }
                            }
                            trimmed.startsWith("#") -> Unit
                            else -> {
                                val ext = pending ?: continue
                                if (!trimmed.startsWith("http", ignoreCase = true)) {
                                    pending = null
                                    pendingUa = null
                                    continue
                                }
                                index++
                                val title = CatalogRules.cleanChannelTitle(ext.name).ifBlank { ext.name }
                                val group = ext.group.ifBlank { if (type == "series") "Series" else "Películas" }
                                val id = "$idPrefix:$index"
                                out += CatalogItem(
                                    id = id,
                                    streamId = id,
                                    name = title,
                                    title = title,
                                    number = index,
                                    channelNumber = index,
                                    logo = ext.logo,
                                    poster = ext.logo,
                                    category = group,
                                    group = group,
                                    groupTitle = group,
                                    type = type,
                                    url = trimmed,
                                    streamUrl = trimmed,
                                    userAgent = pendingUa ?: ext.userAgent
                                )
                                pending = null
                                pendingUa = null
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    private data class Ext(
        val name: String,
        val group: String,
        val logo: String?,
        val tvgId: String,
        val userAgent: String?
    )

    private fun parseExtInf(line: String): Ext {
        val comma = line.lastIndexOf(',')
        val name = if (comma >= 0) line.substring(comma + 1).trim() else ""
        val attrs = if (comma >= 0) line.substring(0, comma) else line
        return Ext(
            name = name.ifBlank { "Título" },
            group = attr(attrs, "group-title") ?: "",
            logo = attr(attrs, "tvg-logo"),
            tvgId = attr(attrs, "tvg-id").orEmpty(),
            userAgent = attr(attrs, "http-user-agent") ?: attr(attrs, "user-agent")
        )
    }

    private fun attr(source: String, key: String): String? {
        val needle = "$key=\""
        val start = source.indexOf(needle, ignoreCase = true)
        if (start < 0) return null
        val valueStart = start + needle.length
        val end = source.indexOf('"', valueStart)
        if (end < 0) return null
        return source.substring(valueStart, end).trim().ifBlank { null }
    }

    enum class Kind { MOVIES, SERIES, ALL }
}
