package com.senal.tv.data.local

import android.content.Context
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.CatalogResponse
import com.senal.tv.data.model.PlaybackResponse
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Catálogo PELÍCULAS desde iptv-org (lista pública demo/fuente externa).
 * URL: https://iptv-org.github.io/iptv/categories/movies.m3u
 *
 * Se descarga una vez, se cachea en disco y se pagina en memoria.
 * No sustituye la lista oficial EN VIVO de SEÑAL.
 */
class RemoteMoviesStore(private val context: Context) {

    companion object {
        const val SOURCE_URL = "https://iptv-org.github.io/iptv/categories/movies.m3u"
        private const val CACHE_NAME = "iptv_org_movies.m3u"
        private const val MAX_AGE_MS = 12L * 60L * 60L * 1000L // 12h
    }

    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var items: List<CatalogItem> = emptyList()
    private val byId = LinkedHashMap<String, CatalogItem>()

    private fun cacheFile(): File = File(context.filesDir, "catalog/$CACHE_NAME")

    suspend fun ensureLoaded(forceRefresh: Boolean = false): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!forceRefresh && items.isNotEmpty()) return@withLock items.size
            val file = cacheFile()
            file.parentFile?.mkdirs()
            val freshEnough = file.exists() &&
                (System.currentTimeMillis() - file.lastModified()) < MAX_AGE_MS &&
                file.length() > 100
            if (!forceRefresh && freshEnough) {
                parseAndIndex(file.readText(Charsets.UTF_8))
                if (items.isNotEmpty()) return@withLock items.size
            }
            // Download
            val body = runCatching {
                val req = Request.Builder()
                    .url(SOURCE_URL)
                    .header("User-Agent", "SEÑAL-TV/1.2 (Android; movies)")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    resp.body?.string().orEmpty()
                }
            }.getOrElse { err ->
                // Fallback to stale cache
                if (file.exists() && file.length() > 100) {
                    parseAndIndex(file.readText(Charsets.UTF_8))
                    if (items.isNotEmpty()) return@withLock items.size
                }
                throw IllegalStateException("No se pudo cargar películas: ${err.message}")
            }
            if (body.length < 50 || !body.contains("#EXTINF", ignoreCase = true)) {
                throw IllegalStateException("Lista de películas vacía o inválida")
            }
            file.writeText(body, Charsets.UTF_8)
            parseAndIndex(body)
            items.size
        }
    }

    suspend fun page(page: Int = 1, limit: Int = 48): CatalogResponse = withContext(Dispatchers.IO) {
        ensureLoaded()
        val safePage = page.coerceAtLeast(1)
        val safeLimit = limit.coerceIn(1, 120)
        val from = (safePage - 1) * safeLimit
        val total = items.size
        val end = minOf(from + safeLimit, total)
        val slice = if (from >= total) emptyList() else items.subList(from, end)
        CatalogResponse(
            items = slice,
            page = safePage,
            limit = safeLimit,
            total = total,
            hasMore = end < total
        )
    }

    suspend fun get(id: String): CatalogItem? = withContext(Dispatchers.IO) {
        ensureLoaded()
        byId[id]
    }

    suspend fun playback(id: String): PlaybackResponse = withContext(Dispatchers.IO) {
        ensureLoaded()
        val item = byId[id] ?: throw IllegalStateException("Película no encontrada")
        val url = item.resolveStreamUrl() ?: throw IllegalStateException("Sin URL")
        val headers = buildMap {
            item.userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
        }.ifEmpty { null }
        PlaybackResponse(
            url = url,
            headers = headers,
            type = "movie",
            title = item.resolveTitle(),
            logo = item.resolvePoster()
        )
    }

    fun size(): Int = items.size

    private fun parseAndIndex(text: String) {
        val parsed = ArrayList<CatalogItem>(800)
        var pending: Ext? = null
        var pendingUa: String? = null
        var index = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#EXTM3U")) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = parseExtInf(line)
                    pendingUa = pending.userAgent
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    if (opt.startsWith("http-user-agent=", ignoreCase = true)) {
                        pendingUa = opt.substringAfter('=').trim().trim('"')
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val ext = pending ?: continue
                    val url = line
                    if (!url.startsWith("http", ignoreCase = true)) {
                        pending = null
                        pendingUa = null
                        continue
                    }
                    index++
                    val id = stableId(ext.tvgId, ext.name, url, index)
                    val item = CatalogItem(
                        id = id,
                        streamId = id,
                        name = ext.name,
                        title = ext.name,
                        number = index,
                        channelNumber = index,
                        logo = ext.logo,
                        poster = ext.logo,
                        category = ext.group.ifBlank { "Movies" },
                        group = ext.group.ifBlank { "Movies" },
                        type = "movie",
                        url = url,
                        streamUrl = url,
                        userAgent = pendingUa ?: ext.userAgent
                    )
                    parsed.add(item)
                    pending = null
                    pendingUa = null
                }
            }
        }
        items = parsed
        byId.clear()
        parsed.forEach { byId[it.resolveId()] = it }
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
            name = name.ifBlank { "Película" },
            group = attr(attrs, "group-title") ?: "Movies",
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

    private fun stableId(tvgId: String, name: String, url: String, index: Int): String = when {
        tvgId.isNotBlank() -> "movies:$tvgId"
        name.isNotBlank() -> "movies:${name.lowercase().hashCode()}:$index"
        else -> "movies:${url.hashCode()}:$index"
    }
}
