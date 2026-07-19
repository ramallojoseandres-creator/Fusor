package com.senal.tv.data.local

import android.content.Context
import com.senal.tv.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Catálogo SEÑAL — multi-fuente para que SIEMPRE haya canales.
 *
 * Orden de descarga:
 * 1) `/playlist.m3u` con JWT (lista filtrada del panel)
 * 2) `/downloads/lista.m3u` (público en el VPS — funciona hoy)
 * 3) `/downloads/lista_importada.m3u` (cuando el admin despliega el server nuevo)
 *
 * Rechaza respuestas HTML (SPA) que no son M3U.
 */
class PlaylistSync(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
    private val playlistStore: LocalPlaylistStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val cacheDir: File
        get() = File(context.filesDir, "catalog").also { it.mkdirs() }

    val cacheFile: File
        get() = File(cacheDir, "playlist.m3u.gz")

    fun hasLocalCache(): Boolean = cacheFile.exists() && cacheFile.length() > 64L

    suspend fun loadLocalOnly(): SyncResult = withContext(Dispatchers.IO) {
        if (hasLocalCache()) {
            runCatching { playlistStore.loadFromGzipFile(cacheFile) }
                .onSuccess {
                    if (playlistStore.size() > 0) {
                        return@withContext SyncResult("cache", playlistStore.size(), updated = false)
                    }
                }
        }
        if (playlistStore.size() == 0) {
            runCatching { playlistStore.loadFromAssetFallback() }
                .onSuccess {
                    if (playlistStore.size() > 0) {
                        return@withContext SyncResult("asset", playlistStore.size(), updated = false)
                    }
                }
                .onFailure {
                    return@withContext SyncResult("none", 0, false, it.message)
                }
        }
        SyncResult(
            source = "memory",
            channels = playlistStore.size(),
            updated = false,
            error = if (playlistStore.size() <= 0) "Sin canales en caché" else null,
        )
    }

    suspend fun ensureCatalogReady(forceNetwork: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (!forceNetwork && hasLocalCache()) {
            val local = loadLocalOnly()
            if (local.channels > 0) return@withContext local
        }
        downloadBestAvailable()
            ?: loadLocalOnly().let { local ->
                if (local.channels > 0) local
                else SyncResult("none", 0, false, "No se pudo descargar la lista de canales")
            }
    }

    /** Tras login: descarga fresca (con fallback a caché). */
    suspend fun downloadFirstTimeIfNeeded(): SyncResult = withContext(Dispatchers.IO) {
        downloadBestAvailable()
            ?: if (hasLocalCache()) loadLocalOnly()
            else SyncResult("none", 0, false, "No se pudo descargar la lista de canales")
    }

    /** Forzar sync (Ajustes → Actualizar lista). */
    suspend fun refreshFromServer(): SyncResult = withContext(Dispatchers.IO) {
        downloadBestAvailable()
            ?: SyncResult("none", playlistStore.size(), false, "No se pudo descargar playlist")
    }

    companion object {
        private val PLAYLIST_PATHS = listOf(
            "/playlist.m3u",
            "/downloads/lista.m3u",
            "/downloads/lista_importada.m3u",
        )
    }

    /**
     * Prueba varias URLs hasta obtener un M3U con #EXTINF.
     * `/playlist.m3u` usa Bearer; las de /downloads/ también lo envían si hay token.
     */
    private suspend fun downloadBestAvailable(): SyncResult? {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val token = tokenStore.cachedToken
        var lastError: String? = null

        for (path in PLAYLIST_PATHS) {
            val url = "$base$path"
            val result = fetchM3u(url, token)
            when {
                result != null && result.channels > 0 -> return result
                result?.error != null -> lastError = result.error
            }
        }
        return if (lastError != null) {
            SyncResult("none", playlistStore.size(), false, lastError)
        } else {
            null
        }
    }

    private suspend fun fetchM3u(url: String, token: String?): SyncResult? {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "SEÑAL-TV/${BuildConfig.VERSION_NAME} (Android; mundial)")
            .header("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
            .get()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        val req = builder.build()

        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299) {
                    return@use SyncResult("none", playlistStore.size(), false, "HTTP ${resp.code} · $url")
                }
                val body = resp.body?.bytes() ?: return@use SyncResult(
                    "none", playlistStore.size(), false, "Lista vacía"
                )
                val text = body.toString(Charsets.UTF_8)
                // VPS a veces sirve el SPA HTML en rutas no desplegadas — rechazar.
                if (looksLikeHtml(text) || !text.contains("#EXTINF", ignoreCase = true)) {
                    return@use SyncResult(
                        "none",
                        playlistStore.size(),
                        false,
                        "Respuesta no es M3U · $url",
                    )
                }
                val payload = if (text.trimStart().startsWith("#EXTM3U", ignoreCase = true)) {
                    body
                } else {
                    ("#EXTM3U\n$text").toByteArray(Charsets.UTF_8)
                }
                val tmp = File(cacheDir, "playlist.tmp.gz")
                GZIPOutputStream(tmp.outputStream()).use { it.write(payload) }
                tmp.copyTo(cacheFile, overwrite = true)
                tmp.delete()
                playlistStore.loadFromGzipFile(cacheFile)
                val count = playlistStore.size()
                if (count <= 0) {
                    return@use SyncResult("none", 0, false, "M3U sin canales · $url")
                }
                settingsStore.setPlaylistMeta(
                    etag = resp.header("ETag").orEmpty().ifBlank { "url:$url" },
                    syncedAt = System.currentTimeMillis(),
                    channels = count,
                )
                SyncResult("network", count, updated = true)
            }
        }.getOrElse {
            SyncResult("none", playlistStore.size(), false, it.message ?: "Error de red")
        }
    }

    private fun looksLikeHtml(text: String): Boolean {
        val head = text.take(200).lowercase()
        return head.contains("<!doctype html") ||
            head.contains("<html") ||
            head.contains("<head>")
    }

    data class SyncResult(
        val source: String,
        val channels: Int,
        val updated: Boolean,
        val error: String? = null
    )
}
