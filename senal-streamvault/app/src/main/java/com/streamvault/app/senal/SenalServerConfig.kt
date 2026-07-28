package com.streamvault.app.senal

import com.streamvault.app.BuildConfig

/**
 * SEÑAL: login → GET /playlist.m3u con Bearer JWT (catálogo del usuario).
 * Sin carga manual de listas ni plugins/downloads en chrome.
 */
object SenalServerConfig {
    val baseUrl: String
        get() = BuildConfig.SENAL_BASE_URL.trimEnd('/')

    /** Playlist autenticada del panel (no la pública de /downloads). */
    val playlistUrl: String
        get() {
            val configured = BuildConfig.SENAL_PLAYLIST_URL.trim()
            if (configured.isNotBlank()) return configured
            return "$baseUrl/playlist.m3u"
        }

    val playlistName: String
        get() = BuildConfig.SENAL_PLAYLIST_NAME.ifBlank { "SEÑAL" }

    val hidePlugins: Boolean
        get() = BuildConfig.SENAL_HIDE_PLUGINS

    val hideDownloads: Boolean
        get() = BuildConfig.SENAL_HIDE_DOWNLOADS
}
