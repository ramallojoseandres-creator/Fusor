package com.senal.tv.data.local

import android.content.Context
import com.senal.tv.BuildConfig
import com.senal.tv.ServerConfig
import com.senal.tv.data.api.NetworkModule
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.CatalogResponse
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Catálogo del servidor **una vez**, luego disco.
 *
 * - Primera vez (sin caché): descarga GET /api/catalog (JWT + X-Device-Id),
 *   lo convierte a M3U local y guarda gzip en disco.
 * - Arranques siguientes: SOLO lee disco — no toca la red.
 * - Actualización manual: Ajustes → "Actualizar lista".
 */
class PlaylistSync(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
    private val playlistStore: LocalPlaylistStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val cacheDir: File
        get() = File(context.filesDir, "catalog").also { it.mkdirs() }

    val cacheFile: File
        get() = File(cacheDir, "playlist.m3u.gz")

    fun hasLocalCache(): Boolean = cacheFile.exists() && cacheFile.length() > 64L

    /**
     * Carga rápida desde disco (o asset de respaldo). **Nunca** descarga de red.
     */
    suspend fun loadLocalOnly(): SyncResult = withContext(Dispatchers.IO) {
        if (hasLocalCache()) {
            runCatching { playlistStore.loadFromGzipFile(cacheFile) }
                .onSuccess {
                    return@withContext SyncResult("cache", playlistStore.size(), updated = false)
                }
        }
        if (playlistStore.size() == 0) {
            runCatching { playlistStore.loadFromAssetFallback() }
                .onSuccess {
                    return@withContext SyncResult("asset", playlistStore.size(), updated = false)
                }
                .onFailure {
                    return@withContext SyncResult("none", 0, false, it.message)
                }
        }
        SyncResult("memory", playlistStore.size(), updated = false)
    }

    /**
     * Si ya hay caché → disco. Si no → descarga del servidor (primera vez).
     * [forceNetwork] solo para el botón manual de Ajustes.
     */
    suspend fun ensureCatalogReady(forceNetwork: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (!forceNetwork && hasLocalCache()) {
            return@withContext loadLocalOnly()
        }

        val token = tokenStore.cachedToken
        if (!token.isNullOrBlank() && (forceNetwork || !hasLocalCache())) {
            val net = downloadAndApply(token)
            if (net != null) return@withContext net
            // Fallo de red: intentar lo local que haya
            if (hasLocalCache() || playlistStore.size() > 0) {
                return@withContext loadLocalOnly()
            }
            return@withContext SyncResult("none", 0, false, "No se pudo descargar la lista de canales")
        }

        loadLocalOnly()
    }

    /** Primera vez: descarga obligatoria si no hay caché. */
    suspend fun downloadFirstTimeIfNeeded(): SyncResult = withContext(Dispatchers.IO) {
        if (hasLocalCache()) return@withContext loadLocalOnly()
        val token = tokenStore.cachedToken
            ?: return@withContext SyncResult("none", 0, false, "Sin sesión")
        downloadAndApply(token)
            ?: SyncResult("none", 0, false, "No se pudo descargar la lista de canales")
    }

    /** Solo Ajustes → Actualizar lista (sí usa red). */
    suspend fun refreshFromServer(): SyncResult = withContext(Dispatchers.IO) {
        val token = tokenStore.cachedToken
            ?: return@withContext SyncResult("none", 0, false, "Sin sesión")
        downloadAndApply(token)
            ?: SyncResult("none", playlistStore.size(), false, "No se pudo descargar playlist")
    }

    /**
     * Descarga una M3U pública (p. ej. lista de prueba) y la aplica como catálogo EN VIVO local.
     * Sustituye la caché en disco hasta que se vuelva a “Actualizar lista desde servidor”.
     */
    suspend fun loadFromRemoteM3u(url: String = TEST_PLAYLIST_URL): SyncResult = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "SENAL-TV/${BuildConfig.VERSION_NAME} (Android; playlist-test)")
            .header("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
            .get()
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299) {
                    return@withContext SyncResult("none", playlistStore.size(), false, "HTTP ${resp.code}")
                }
                var body = resp.body?.bytes() ?: return@withContext SyncResult(
                    "none", playlistStore.size(), false, "Lista vacía"
                )
                // Algunas listas públicas omiten #EXTM3U
                val text = body.toString(Charsets.UTF_8)
                if (!text.contains("#EXTINF", ignoreCase = true)) {
                    return@withContext SyncResult("none", playlistStore.size(), false, "No parece un M3U válido")
                }
                if (!text.trimStart().startsWith("#EXTM3U", ignoreCase = true)) {
                    body = ("#EXTM3U\n$text").toByteArray(Charsets.UTF_8)
                }
                val tmp = File(cacheDir, "playlist.tmp.gz")
                GZIPOutputStream(tmp.outputStream()).use { it.write(body) }
                tmp.copyTo(cacheFile, overwrite = true)
                tmp.delete()
                playlistStore.loadFromGzipFile(cacheFile)
                settingsStore.setPlaylistMeta(
                    etag = "test:$url",
                    syncedAt = System.currentTimeMillis(),
                    channels = playlistStore.size()
                )
                SyncResult("test-m3u", playlistStore.size(), updated = true)
            }
        }.getOrElse {
            SyncResult("none", playlistStore.size(), false, it.message ?: "No se pudo descargar la lista")
        }
    }

    companion object {
        /** Lista pública de prueba (Duartegame/listas). */
        const val TEST_PLAYLIST_URL =
            "https://raw.githubusercontent.com/Duartegame/listas/main/canalesgratistvpro"
    }

    private suspend fun downloadAndApply(token: String): SyncResult? {
        val deviceId = tokenStore.peekDeviceId() ?: tokenStore.deviceId()
        val items = fetchAllCatalogItems(token, deviceId)
        if (items.isEmpty()) return null

        val m3u = catalogItemsToM3u(items).toByteArray(Charsets.UTF_8)
        val tmp = File(cacheDir, "playlist.tmp.gz")
        GZIPOutputStream(tmp.outputStream()).use { it.write(m3u) }
        tmp.copyTo(cacheFile, overwrite = true)
        tmp.delete()
        playlistStore.loadFromGzipFile(cacheFile)
        settingsStore.setPlaylistMeta(
            etag = "api-catalog:${items.size}",
            syncedAt = System.currentTimeMillis(),
            channels = playlistStore.size()
        )
        return SyncResult("network", playlistStore.size(), updated = true)
    }

    /** Descarga /api/catalog (bulk + paginación de respaldo). */
    private fun fetchAllCatalogItems(token: String, deviceId: String): List<CatalogItem> {
        val bulk = fetchCatalogPage(token, deviceId, page = null, limit = 20_000)
        val bulkItems = bulk?.resolveItems().orEmpty()
        if (bulkItems.size >= 50 || bulk?.resolveHasMore(20_000) != true) {
            if (bulkItems.isNotEmpty()) return bulkItems.distinctBy { it.resolveId() }
        }

        val all = LinkedHashMap<String, CatalogItem>()
        bulkItems.forEach { all[it.resolveId()] = it }
        var page = 1
        while (page <= 40) {
            val resp = fetchCatalogPage(token, deviceId, page = page, limit = 500) ?: break
            val chunk = resp.resolveItems()
            if (chunk.isEmpty()) break
            chunk.forEach { all[it.resolveId()] = it }
            if (!resp.resolveHasMore(500)) break
            page += 1
        }
        return all.values.toList()
    }

    private fun fetchCatalogPage(
        token: String,
        deviceId: String,
        page: Int?,
        limit: Int?
    ): CatalogResponse? {
        val urlBuilder = ServerConfig.catalogUrl().toHttpUrl().newBuilder()
        if (page != null) urlBuilder.addQueryParameter("page", page.toString())
        if (limit != null) urlBuilder.addQueryParameter("limit", limit.toString())
        if (page != null && limit != null) {
            urlBuilder.addQueryParameter("offset", ((page - 1) * limit).toString())
        }
        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer $token")
            .header("X-Device-Id", deviceId)
            .header("X-Device-Name", "SENAL TV")
            .header("X-Device-Platform", "android-tv")
            .header("Accept", "application/json")
            .get()
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                NetworkModule.json.decodeFromString(CatalogResponse.serializer(), body)
            }
        }.getOrNull()
    }

    private fun catalogItemsToM3u(items: List<CatalogItem>): String {
        val sb = StringBuilder(items.size * 160)
        sb.append("#EXTM3U\n")
        for (item in items) {
            val url = item.resolveStreamUrl()?.trim().orEmpty()
            if (url.isEmpty()) continue
            val name = item.resolveTitle().replace('\n', ' ').trim().ifBlank { "Canal" }
            val group = item.resolveCategory().replace(',', ' ').trim().ifBlank { "General" }
            val logo = item.resolveLogo().orEmpty()
            val id = item.resolveId().replace(',', ' ')
            val num = item.resolveNumber()
            sb.append("#EXTINF:-1")
            if (num != null) sb.append(" tvg-chno=\"").append(num).append('"')
            sb.append(" tvg-id=\"").append(escapeAttr(id)).append('"')
            if (logo.isNotBlank()) sb.append(" tvg-logo=\"").append(escapeAttr(logo)).append('"')
            sb.append(" group-title=\"").append(escapeAttr(group)).append('"')
            sb.append(',').append(name).append('\n')
            item.userAgent?.takeIf { it.isNotBlank() }?.let {
                sb.append("#EXTVLCOPT:http-user-agent=").append(it.trim()).append('\n')
            }
            sb.append(url).append('\n')
        }
        return sb.toString()
    }

    private fun escapeAttr(value: String): String =
        value.replace('"', '\'').replace('\n', ' ').replace('\r', ' ')

    data class SyncResult(
        val source: String,
        val channels: Int,
        val updated: Boolean,
        val error: String? = null
    )
}
