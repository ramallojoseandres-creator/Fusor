package com.senal.tv.data.local

import android.content.Context
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.CatalogResponse
import com.senal.tv.data.model.Category
import com.senal.tv.data.model.PlaybackResponse
import com.senal.tv.util.CatalogRules
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Catálogo local en memoria desde:
 * 1) Caché en disco (`filesDir/catalog/playlist.m3u.gz`) — lista del servidor
 * 2) Asset embebido de respaldo (opcional)
 *
 * Respeta `group-title` exacto del M3U (orden de aparición, sin renormalizar).
 */
class LocalPlaylistStore(private val context: Context) {

    private val mutex = Mutex()
    @Volatile private var loaded = false

    private val all = ArrayList<PlaylistEntry>(10_000)
    private val byId = HashMap<String, PlaylistEntry>(10_000)
    private val groupIndex = LinkedHashMap<String, ArrayList<Int>>(256)
    private val categoriesByType = HashMap<String, List<Category>>(4)

    fun size(): Int = all.size

    /** Categorías live distintas tras cargar (para detectar caché plana "General"). */
    fun liveCategoryCount(): Int = categoriesByType["live"]?.size ?: groupIndex.size

    fun liveCategoryLabels(): List<String> =
        categoriesByType["live"]?.map { it.label() } ?: groupIndex.keys.toList()

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            val disk = File(context.filesDir, "catalog/playlist.m3u.gz")
            when {
                disk.exists() && disk.length() > 64L -> parseGzipFile(disk)
                else -> parseAssetOrEmpty()
            }
            loaded = true
        }
    }

    suspend fun loadFromGzipFile(file: File) {
        mutex.withLock {
            parseGzipFile(file)
            loaded = true
        }
    }

    suspend fun loadFromAssetFallback() {
        mutex.withLock {
            parseAssetOrEmpty()
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
        loaded = false
        all.clear()
        byId.clear()
        groupIndex.clear()
        categoriesByType.clear()
    }

    private fun findGroupIndexes(group: String): List<Int> {
        groupIndex[group]?.let { return it }
        val hit = groupIndex.entries.firstOrNull { it.key.equals(group, ignoreCase = true) }
        return hit?.value.orEmpty()
    }

    private fun parseGzipFile(file: File) {
        GZIPInputStream(file.inputStream()).use { gz ->
            parseReader(BufferedReader(InputStreamReader(gz, Charsets.UTF_8), 64 * 1024))
        }
    }

    private fun parseAssetOrEmpty() {
        val assetName = runCatching { resolveAssetName() }.getOrNull()
        if (assetName == null) {
            all.clear(); byId.clear(); groupIndex.clear(); categoriesByType.clear()
            categoriesByType["live"] = emptyList()
            categoriesByType["movie"] = emptyList()
            categoriesByType["series"] = emptyList()
            return
        }
        context.assets.open(assetName).use { input ->
            val stream = if (assetName.endsWith(".gz")) GZIPInputStream(input) else input
            parseReader(BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 64 * 1024))
        }
    }

    private fun parseReader(reader: BufferedReader) {
        all.clear()
        byId.clear()
        groupIndex.clear()
        categoriesByType.clear()

        reader.use { r ->
            var pending: ExtInf? = null
            var index = 0
            while (true) {
                val lineRaw = r.readLine() ?: break
                val line = lineRaw.trim()
                if (line.isEmpty()) continue
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> pending = parseExtInf(line)
                    line.startsWith("#") -> Unit
                    else -> {
                        val ext = pending
                        pending = null
                        if (ext == null) continue
                        val url = line
                        if (url.isEmpty()) continue
                        index += 1
                        val rawGroup = ext.group.ifBlank { "Variados" }
                        val group = CatalogRules.canonicalLabel(rawGroup)
                        // Solo categorías pedidas; el resto se elimina con sus canales.
                        if (!CatalogRules.isPreferredLabel(group)) continue
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
                            kind = "live"
                        )
                        val pos = all.size
                        all += entry
                        byId[id] = entry
                        groupIndex.getOrPut(group) { ArrayList(64) }.add(pos)
                    }
                }
            }
        }

        val liveCats = groupIndex.map { (name, idxs) ->
            Category(id = name, name = name, title = name, count = idxs.size)
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
    }

    private fun resolveAssetName(): String {
        val names = context.assets.list("catalog").orEmpty().toList()
        return when {
            names.any { it == "lista_fusionada.m3u.gz" } -> "catalog/lista_fusionada.m3u.gz"
            names.any { it == "lista_fusionada.m3u" } -> "catalog/lista_fusionada.m3u"
            else -> throw IllegalStateException("Sin lista en caché ni en assets")
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
