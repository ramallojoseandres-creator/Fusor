package com.streamvault.app.senal

import com.streamvault.app.BuildConfig

/**
 * SEÑAL playlist policy:
 * - Panel login = access control only
 * - Content always from VPS `downloads/lista_importada.m3u`
 *   (file on server: senal-server/data/lista_importada.m3u)
 * - Clients never paste arbitrary M3U URLs or manage sources in-app
 */
object SenalServerConfig {
    val baseUrl: String
        get() = BuildConfig.SENAL_BASE_URL.trimEnd('/')

    const val LISTA_FILENAME = "lista_importada.m3u"

    val listaUrl: String
        get() {
            val configured = BuildConfig.SENAL_LISTA_URL.trim()
            if (configured.isNotBlank()) return configured
            return "$baseUrl/downloads/$LISTA_FILENAME"
        }

    val listaName: String
        get() = BuildConfig.SENAL_LISTA_NAME.ifBlank { "SEÑAL" }

    val allowClientPlaylistAdd: Boolean
        get() = BuildConfig.SENAL_ALLOW_CLIENT_PLAYLISTS

    /** Locked SEÑAL experience: no Settings / content-edit chrome. */
    val hideSettings: Boolean
        get() = BuildConfig.SENAL_HIDE_SETTINGS && !allowClientPlaylistAdd

    val hidePlugins: Boolean
        get() = BuildConfig.SENAL_HIDE_PLUGINS && !allowClientPlaylistAdd
}
