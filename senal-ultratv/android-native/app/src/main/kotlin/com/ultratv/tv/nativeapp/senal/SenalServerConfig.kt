package com.ultratv.tv.nativeapp.senal

import com.ultratv.tv.nativeapp.BuildConfig

/**
 * SEÑAL VPS policy for this Ultra TV fork:
 * - Panel login = access control
 * - Content always from downloads/lista.m3u on the VPS
 * - Clients cannot add arbitrary playlists in-app
 */
object SenalServerConfig {
    val lockToVps: Boolean
        get() = BuildConfig.SENAL_LOCK_VPS

    val baseUrl: String
        get() = BuildConfig.SENAL_BASE_URL.trimEnd('/')

    val listaUrl: String
        get() = BuildConfig.SENAL_LISTA_URL.trim().ifBlank { "$baseUrl/downloads/lista.m3u" }

    val listaName: String
        get() = BuildConfig.SENAL_LISTA_NAME.ifBlank { "SEÑAL" }
}
