package com.streamvault.app.senal

import android.net.Uri
import com.streamvault.app.BuildConfig

/**
 * Defaults for the SEÑAL panel at [BuildConfig.SENAL_BASE_URL].
 * Playlist options shown in provider setup / first-run seed.
 */
object SenalServerConfig {
    val baseUrl: String
        get() = BuildConfig.SENAL_BASE_URL.trimEnd('/')

    /** Public catalog M3U served by the panel (no login). */
    val principalListaUrl: String
        get() = BuildConfig.SENAL_LISTA_URL.ifBlank { "$baseUrl/downloads/lista.m3u" }

    val principalListaName: String
        get() = BuildConfig.SENAL_LISTA_NAME.ifBlank { "SEÑAL Principal" }

    val extraLista1Url: String get() = BuildConfig.SENAL_EXTRA_M3U_1_URL.trim()
    val extraLista1Name: String get() = BuildConfig.SENAL_EXTRA_M3U_1_NAME.ifBlank { "Lista extra 1" }

    val extraLista2Url: String get() = BuildConfig.SENAL_EXTRA_M3U_2_URL.trim()
    val extraLista2Name: String get() = BuildConfig.SENAL_EXTRA_M3U_2_NAME.ifBlank { "Lista extra 2" }

    /** Classic IPTV URL: /get.php?username=&password=&type=m3u_plus */
    fun accountPlaylistUrl(username: String, password: String): String {
        val u = username.trim()
        val p = password.trim()
        require(u.isNotEmpty() && p.isNotEmpty()) { "Usuario y contraseña requeridos" }
        return Uri.parse("$baseUrl/get.php").buildUpon()
            .appendQueryParameter("username", u)
            .appendQueryParameter("password", p)
            .appendQueryParameter("type", "m3u_plus")
            .build()
            .toString()
    }

    data class Preset(
        val id: String,
        val title: String,
        val subtitle: String,
        val url: String?,
        val needsCredentials: Boolean = false,
    )

    fun presets(): List<Preset> = buildList {
        add(
            Preset(
                id = "principal",
                title = principalListaName,
                subtitle = "Catálogo del servidor SEÑAL",
                url = principalListaUrl,
            ),
        )
        add(
            Preset(
                id = "cuenta",
                title = "SEÑAL (mi cuenta)",
                subtitle = "get.php con usuario y clave del panel",
                url = null,
                needsCredentials = true,
            ),
        )
        if (extraLista1Url.isNotBlank()) {
            add(
                Preset(
                    id = "extra1",
                    title = extraLista1Name,
                    subtitle = "Lista adicional 1",
                    url = extraLista1Url,
                ),
            )
        } else {
            add(
                Preset(
                    id = "extra1_custom",
                    title = "Lista extra 1",
                    subtitle = "Pega otra URL M3U",
                    url = "",
                ),
            )
        }
        if (extraLista2Url.isNotBlank()) {
            add(
                Preset(
                    id = "extra2",
                    title = extraLista2Name,
                    subtitle = "Lista adicional 2",
                    url = extraLista2Url,
                ),
            )
        } else {
            add(
                Preset(
                    id = "extra2_custom",
                    title = "Lista extra 2",
                    subtitle = "Pega otra URL M3U",
                    url = "",
                ),
            )
        }
    }
}
