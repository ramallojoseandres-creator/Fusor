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
 * Descarga /playlist.m3u del servidor (JWT), guarda gzip en disco y
 * recarga [LocalPlaylistStore]. Usa ETag para no bajar de nuevo si no cambió.
 *
 * Orden de velocidad: caché disco → sync delta (304) → asset embebido de respaldo.
 */
class PlaylistSync(
    private val context: Context,
    private val tokenStore: TokenStore,
    private val settingsStore: SettingsStore,
    private val playlistStore: LocalPlaylistStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val cacheDir: File
        get() = File(context.filesDir, "catalog").also { it.mkdirs() }

    val cacheFile: File
        get() = File(cacheDir, "playlist.m3u.gz")

    suspend fun ensureCatalogReady(forceNetwork: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        // 1) Disco inmediato
        if (cacheFile.exists() && cacheFile.length() > 64) {
            runCatching {
                playlistStore.loadFromGzipFile(cacheFile)
            }.onSuccess {
                if (!forceNetwork) {
                    // Sync en cold path solo si no forzamos; caller can background refresh.
                    return@withContext SyncResult(source = "cache", channels = playlistStore.size(), updated = false)
                }
            }
        }

        // 2) Red (si hay sesión)
        val token = tokenStore.cachedToken
        if (!token.isNullOrBlank()) {
            val net = downloadAndApply(token, forceNetwork)
            if (net != null) return@withContext net
        }

        // 3) Asset embebido (solo si aún no hay nada en memoria)
        if (playlistStore.size() == 0) {
            runCatching { playlistStore.loadFromAssetFallback() }
                .onSuccess {
                    return@withContext SyncResult(source = "asset", channels = playlistStore.size(), updated = false)
                }
                .onFailure {
                    return@withContext SyncResult(source = "none", channels = 0, updated = false, error = it.message)
                }
        }
        SyncResult(source = "memory", channels = playlistStore.size(), updated = false)
    }

    /** Refresh en background tras login / botón Ajustes. */
    suspend fun refreshFromServer(): SyncResult = withContext(Dispatchers.IO) {
        val token = tokenStore.cachedToken
            ?: return@withContext SyncResult("none", 0, false, "Sin sesión")
        downloadAndApply(token, force = true)
            ?: SyncResult("none", playlistStore.size(), false, "No se pudo descargar playlist")
    }

    private suspend fun downloadAndApply(token: String, force: Boolean): SyncResult? {
        val etag = if (force) null else settingsStore.playlistEtag()
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val url = "$base/playlist.m3u"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
            .apply {
                if (!etag.isNullOrBlank()) header("If-None-Match", etag)
            }
            .get()
            .build()

        return runCatching {
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    304 -> {
                        if (cacheFile.exists()) {
                            playlistStore.loadFromGzipFile(cacheFile)
                            SyncResult("cache-304", playlistStore.size(), updated = false)
                        } else null
                    }
                    in 200..299 -> {
                        // OkHttp descomprime gzip transparente → body en texto plano.
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
                    else -> null
                }
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
