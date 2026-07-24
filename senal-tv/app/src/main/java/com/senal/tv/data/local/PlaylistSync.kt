package com.senal.tv.data.local

import android.content.Context
import com.senal.tv.BuildConfig
import com.senal.tv.ServerConfig
import com.senal.tv.data.api.NetworkModule
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.CatalogResponse
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Catálogo **una vez**, luego disco.
 *
 * Prioridad de categorías (como en 1.8.4):
 * 1) Caché en disco con grupos reales
 * 2) Asset `lista_fusionada` (grupos Deportes, México, …)
 * 3) GET /api/catalog — si el panel no manda group/category, se rellenan
 *    cruzando nombres con el asset embebido.
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
     * Si la caché está plana (solo General), la descarta y usa el asset con categorías.
     */
    suspend fun loadLocalOnly(): SyncResult = withContext(Dispatchers.IO) {
        if (hasLocalCache()) {
            val ok = runCatching { playlistStore.loadFromGzipFile(cacheFile) }
            if (ok.isSuccess && playlistStore.size() > 0) {
                if (hasUsefulCategories()) {
                    return@withContext SyncResult("cache", playlistStore.size(), updated = false)
                }
                // Caché mala de /api/catalog sin grupos → borrar y usar asset.
                cacheFile.delete()
            }
        }
        applyAssetAsCache()
    }

    /**
     * Si ya hay caché útil → disco. Si no → asset / red.
     * [forceNetwork] solo para el botón manual de Ajustes.
     */
    suspend fun ensureCatalogReady(forceNetwork: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (!forceNetwork && hasLocalCache()) {
            val local = loadLocalOnly()
            if (local.channels > 0 && hasUsefulCategories()) return@withContext local
        }

        val token = tokenStore.cachedToken
        if (!token.isNullOrBlank() && (forceNetwork || !hasUsefulDiskOrMemory())) {
            val net = downloadAndApply(token)
            if (net != null && hasUsefulCategories()) return@withContext net
            // API sin categorías: quedarse con asset categorado.
            val asset = applyAssetAsCache()
            if (asset.channels > 0) return@withContext asset
            if (net != null) return@withContext net
            return@withContext SyncResult("none", 0, false, "No se pudo descargar la lista de canales")
        }

        loadLocalOnly()
    }

    /** Primera vez: asset con categorías; si hay token, intenta enriquecer desde API. */
    suspend fun downloadFirstTimeIfNeeded(): SyncResult = withContext(Dispatchers.IO) {
        if (hasLocalCache()) {
            val local = loadLocalOnly()
            if (local.channels > 0 && hasUsefulCategories()) return@withContext local
        }

        // Base: lista embebida (grupos correctos, como 1.8.4).
        val asset = applyAssetAsCache()
        val token = tokenStore.cachedToken
        if (!token.isNullOrBlank()) {
            val net = downloadAndApply(token)
            if (net != null && hasUsefulCategories()) return@withContext net
            // Si la API llegó plana, restaurar asset.
            if (!hasUsefulCategories()) {
                return@withContext applyAssetAsCache()
            }
        }
        if (asset.channels > 0) asset
        else SyncResult("none", 0, false, "No se pudo cargar la lista de canales")
    }

    /** Solo Ajustes → Actualizar lista (sí usa red). */
    suspend fun refreshFromServer(): SyncResult = withContext(Dispatchers.IO) {
        val token = tokenStore.cachedToken
            ?: return@withContext SyncResult("none", 0, false, "Sin sesión")
        val net = downloadAndApply(token)
        if (net != null && hasUsefulCategories()) return@withContext net
        // No pisar categorías buenas con una lista plana.
        val asset = applyAssetAsCache()
        if (asset.channels > 0) {
            return@withContext asset.copy(error = "El panel no envió categorías; se usó la lista local")
        }
        net ?: SyncResult("none", playlistStore.size(), false, "No se pudo descargar playlist")
    }

    /**
     * Descarga una M3U pública (p. ej. lista de prueba) y la aplica como catálogo EN VIVO local.
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
                val text = body.toString(Charsets.UTF_8)
                if (!text.contains("#EXTINF", ignoreCase = true)) {
                    return@withContext SyncResult("none", playlistStore.size(), false, "No parece un M3U válido")
                }
                if (!text.trimStart().startsWith("#EXTM3U", ignoreCase = true)) {
                    body = ("#EXTM3U\n$text").toByteArray(Charsets.UTF_8)
                }
                writeGzipCache(body)
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
        const val TEST_PLAYLIST_URL =
            "https://raw.githubusercontent.com/Duartegame/listas/main/canalesgratistvpro"

        private val genericGroups = setOf("general", "variados", "otros", "other", "uncategorized")
    }

    private fun hasUsefulDiskOrMemory(): Boolean =
        (hasLocalCache() || playlistStore.size() > 0) && hasUsefulCategories()

    private fun hasUsefulCategories(): Boolean {
        if (playlistStore.size() <= 0) return false
        val labels = playlistStore.liveCategoryLabels()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (labels.size >= 3) return true
        val real = labels.filterNot { genericGroups.contains(it.lowercase()) }
        return real.size >= 2
    }

    private suspend fun applyAssetAsCache(): SyncResult {
        return runCatching {
            playlistStore.loadFromAssetFallback()
            if (playlistStore.size() <= 0) {
                return SyncResult("none", 0, false, "Asset de lista vacío")
            }
            // Persistir asset en disco para arranques siguientes.
            val assetName = resolveAssetNameOrNull() ?: return SyncResult(
                "asset", playlistStore.size(), updated = false
            )
            context.assets.open(assetName).use { input ->
                if (assetName.endsWith(".gz")) {
                    input.copyTo(cacheFile.outputStream())
                } else {
                    val bytes = input.readBytes()
                    writeGzipCache(bytes)
                }
            }
            settingsStore.setPlaylistMeta(
                etag = "asset:$assetName",
                syncedAt = System.currentTimeMillis(),
                channels = playlistStore.size()
            )
            SyncResult("asset", playlistStore.size(), updated = true)
        }.getOrElse {
            SyncResult("none", 0, false, it.message)
        }
    }

    private fun resolveAssetNameOrNull(): String? {
        val names = context.assets.list("catalog").orEmpty().toList()
        return when {
            names.any { it == "lista_fusionada.m3u.gz" } -> "catalog/lista_fusionada.m3u.gz"
            names.any { it == "lista_fusionada.m3u" } -> "catalog/lista_fusionada.m3u"
            else -> null
        }
    }

    private fun writeGzipCache(body: ByteArray) {
        val tmp = File(cacheDir, "playlist.tmp.gz")
        GZIPOutputStream(tmp.outputStream()).use { it.write(body) }
        tmp.copyTo(cacheFile, overwrite = true)
        tmp.delete()
    }

    private suspend fun downloadAndApply(token: String): SyncResult? {
        val deviceId = tokenStore.peekDeviceId() ?: tokenStore.deviceId()
        val pages = fetchAllCatalogPages(token, deviceId)
        val items = pages.flatMap { it.resolveItems() }.distinctBy { it.resolveId() }
        if (items.isEmpty()) return null

        val categoriesById = LinkedHashMap<String, String>()
        pages.forEach { page ->
            page.categories.orEmpty().forEach { cat ->
                val id = cat.id?.trim().orEmpty()
                val label = cat.label().trim()
                if (id.isNotEmpty() && label.isNotEmpty()) categoriesById[id] = label
                if (label.isNotEmpty()) categoriesById.putIfAbsent(label.lowercase(), label)
            }
        }

        val nameToGroup = loadBundledNameToGroup()
        val m3u = catalogItemsToM3u(items, categoriesById, nameToGroup).toByteArray(Charsets.UTF_8)
        writeGzipCache(m3u)
        playlistStore.loadFromGzipFile(cacheFile)

        // Si tras mapear sigue plano, no sirve.
        if (!hasUsefulCategories()) return null

        settingsStore.setPlaylistMeta(
            etag = "api-catalog:${items.size}",
            syncedAt = System.currentTimeMillis(),
            channels = playlistStore.size()
        )
        return SyncResult("network", playlistStore.size(), updated = true)
    }

    private fun fetchAllCatalogPages(token: String, deviceId: String): List<CatalogResponse> {
        val out = ArrayList<CatalogResponse>()
        val bulk = fetchCatalogPage(token, deviceId, page = null, limit = 20_000)
        if (bulk != null) {
            out += bulk
            val bulkItems = bulk.resolveItems()
            if (bulkItems.size >= 50 || !bulk.resolveHasMore(20_000)) {
                return out
            }
        }
        var page = 1
        while (page <= 40) {
            val resp = fetchCatalogPage(token, deviceId, page = page, limit = 500) ?: break
            out += resp
            if (resp.resolveItems().isEmpty() || !resp.resolveHasMore(500)) break
            page += 1
        }
        return out
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

    private fun catalogItemsToM3u(
        items: List<CatalogItem>,
        categoriesById: Map<String, String>,
        nameToGroup: Map<String, String>
    ): String {
        val sb = StringBuilder(items.size * 160)
        sb.append("#EXTM3U\n")
        for (item in items) {
            val url = item.resolveStreamUrl()?.trim().orEmpty()
            if (url.isEmpty()) continue
            val name = item.resolveTitle().replace('\n', ' ').trim().ifBlank { "Canal" }
            val group = resolveItemGroup(item, name, categoriesById, nameToGroup)
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

    private fun resolveItemGroup(
        item: CatalogItem,
        name: String,
        categoriesById: Map<String, String>,
        nameToGroup: Map<String, String>
    ): String {
        val fromApi = item.resolveCategory(categoriesById).replace(',', ' ').trim()
        if (fromApi.isNotEmpty() && !genericGroups.contains(fromApi.lowercase())) {
            return fromApi
        }
        val key = normalizeNameKey(name)
        nameToGroup[key]?.let { return it }
        // Match sin resolución/país entre paréntesis finales
        val stripped = key.replace(Regex("""\s*\([^)]*\)\s*$"""), "").trim()
        nameToGroup[stripped]?.let { return it }
        nameToGroup.entries.firstOrNull { (k, _) ->
            k.startsWith(stripped) || stripped.startsWith(k)
        }?.value?.let { return it }
        inferGroupFromTitle(name)?.let { return it }
        return fromApi.ifBlank { "Variados" }
    }

    /** Mapa nombre→group-title desde el asset embebido (lista_fusionada). */
    private fun loadBundledNameToGroup(): Map<String, String> {
        val asset = resolveAssetNameOrNull() ?: return emptyMap()
        return runCatching {
            context.assets.open(asset).use { input ->
                val stream = if (asset.endsWith(".gz")) GZIPInputStream(input) else input
                val map = HashMap<String, String>(8_192)
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8), 64 * 1024).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (!line.startsWith("#EXTINF", ignoreCase = true)) continue
                        val group = attrFast(line, "group-title")?.trim().orEmpty()
                        if (group.isEmpty()) continue
                        val name = line.substringAfterLast(',').trim()
                        if (name.isEmpty()) continue
                        val key = normalizeNameKey(name)
                        map.putIfAbsent(key, group)
                        val stripped = key.replace(Regex("""\s*\([^)]*\)\s*$"""), "").trim()
                        if (stripped.isNotEmpty()) map.putIfAbsent(stripped, group)
                    }
                }
                map
            }
        }.getOrDefault(emptyMap())
    }

    private fun inferGroupFromTitle(name: String): String? {
        val lower = name.lowercase()
        val rules = listOf(
            "méxico" to "México", "mexico" to "México",
            "españa" to "España", "espana" to "España", "spain" to "España",
            "argentina" to "Argentina",
            "colombia" to "Colombia",
            "chile" to "Chile",
            "perú" to "Perú", "peru" to "Perú",
            "brasil" to "Brasil", "brazil" to "Brasil",
            "venezuela" to "Venezuela",
            "bolivia" to "Bolivia",
            "ecuador" to "Ecuador",
            "uruguay" to "Uruguay",
            "paraguay" to "Paraguay",
            "honduras" to "Honduras",
            "guatemala" to "Guatemala",
            "nicaragua" to "Nicaragua",
            "panamá" to "Panamá", "panama" to "Panamá",
            "costa rica" to "Costa Rica",
            "república dominicana" to "República Dominicana",
            "republica dominicana" to "República Dominicana",
            "puerto rico" to "Puerto Rico",
            "el salvador" to "El Salvador",
            "italia" to "Italia", "italy" to "Italia",
            "canadá" to "Canadá", "canada" to "Canadá",
            "estados unidos" to "US Channels", "ee.uu" to "US Channels",
        )
        // Only use parenthetical country: "Foo (Mexico)"
        val paren = Regex("""\(([^)]+)\)""").findAll(name).map { it.groupValues[1].lowercase() }.toList()
        for (p in paren) {
            for ((needle, group) in rules) {
                if (p.contains(needle)) return group
            }
        }
        for ((needle, group) in rules) {
            if (lower.contains(needle)) return group
        }
        return null
    }

    private fun normalizeNameKey(name: String): String =
        name.trim().lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')
            .replace(Regex("\\s+"), " ")

    private fun attrFast(line: String, key: String): String? {
        val needle = "$key=\""
        val start = line.indexOf(needle, ignoreCase = true)
        if (start < 0) return null
        val from = start + needle.length
        val end = line.indexOf('"', from)
        if (end <= from) return null
        return line.substring(from, end)
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
