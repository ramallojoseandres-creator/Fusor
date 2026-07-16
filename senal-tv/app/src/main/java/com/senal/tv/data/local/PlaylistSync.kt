package com.senal.tv.data.local

import android.content.Context
import com.senal.tv.BuildConfig
import java.io.File
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Catálogo del servidor **una vez**, luego disco.
 *
 * - Primera vez (sin caché): descarga /playlist.m3u y guarda gzip en disco.
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

    private suspend fun downloadAndApply(token: String): SyncResult? {
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val url = "$base/playlist.m3u"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
            .get()
            .build()

        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299) return@use null
                val body = resp.body?.bytes() ?: return@use null
                val newEtag = resp.header("ETag")
                val tmp = File(cacheDir, "playlist.tmp.gz")
                GZIPOutputStream(tmp.outputStream()).use { it.write(body) }
                tmp.copyTo(cacheFile, overwrite = true)
                tmp.delete()
                playlistStore.loadFromGzipFile(cacheFile)
                settingsStore.setPlaylistMeta(
                    etag = newEtag.orEmpty(),
                    syncedAt = System.currentTimeMillis(),
                    channels = playlistStore.size()
                )
                SyncResult("network", playlistStore.size(), updated = true)
            }
        }.getOrNull()
    }

    data class SyncResult(
        val source: String,
        val channels: Int,
        val updated: Boolean,
        val error: String? = null
    )
}
