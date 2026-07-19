package com.streamvault.app.senal

import com.streamvault.app.BuildConfig

/**
 * SEÑAL playlist policy for this fork:
 * - Clients authenticate against the panel.
 * - Content always comes from a single file on the VPS: `downloads/lista.m3u`
 * - Admin renames/replaces files on the VPS; the app never lets clients paste arbitrary M3U URLs.
 */
object SenalServerConfig {
    val baseUrl: String
        get() = BuildConfig.SENAL_BASE_URL.trimEnd('/')

    /** Only this filename is used by the app. */
    const val LISTA_FILENAME = "lista.m3u"

    val listaUrl: String
        get() {
            val configured = BuildConfig.SENAL_LISTA_URL.trim()
            if (configured.isNotBlank()) return configured
            return "$baseUrl/downloads/$LISTA_FILENAME"
        }

    val listaName: String
        get() = BuildConfig.SENAL_LISTA_NAME.ifBlank { "SEÑAL" }

    /** Clients cannot add custom playlists in-app. */
    val allowClientPlaylistAdd: Boolean
        get() = BuildConfig.SENAL_ALLOW_CLIENT_PLAYLISTS
}
