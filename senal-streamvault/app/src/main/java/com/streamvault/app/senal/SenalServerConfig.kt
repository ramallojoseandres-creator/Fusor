package com.streamvault.app.senal

import com.streamvault.app.BuildConfig
import android.net.Uri

/**
 * Defaults for the SEÑAL panel — same model as the previous SEÑAL TV app:
 * login → user bouquet M3U (/get.php or /playlist.m3u), not the public mega-lista.
 */
object SenalServerConfig {
    val baseUrl: String
        get() = BuildConfig.SENAL_BASE_URL.trimEnd('/')

    /** Public full catalog — slow; only as optional manual preset. */
    val principalListaUrl: String
        get() = BuildConfig.SENAL_LISTA_URL.ifBlank { "$baseUrl/downloads/lista.m3u" }

    val principalListaName: String
        get() = BuildConfig.SENAL_LISTA_NAME.ifBlank { "Lista pública completa (lenta)" }

    val extraLista1Url: String get() = BuildConfig.SENAL_EXTRA_M3U_1_URL.trim()
    val extraLista1Name: String get() = BuildConfig.SENAL_EXTRA_M3U_1_NAME.ifBlank { "Lista extra 1" }

    val extraLista2Url: String get() = BuildConfig.SENAL_EXTRA_M3U_2_URL.trim()
    val extraLista2Name: String get() = BuildConfig.SENAL_EXTRA_M3U_2_NAME.ifBlank { "Lista extra 2" }

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
        // Same path as old SEÑAL app — account bouquet first
        add(
            Preset(
                id = "cuenta",
                title = "SEÑAL (mi cuenta)",
                subtitle = "Login panel → bouquet (como la app anterior)",
                url = null,
                needsCredentials = true,
            ),
        )
        if (extraLista1Url.isNotBlank()) {
            add(Preset("extra1", extraLista1Name, "Lista adicional 1", extraLista1Url))
        } else {
            add(Preset("extra1_custom", "Lista extra 1", "Pega otra URL M3U", ""))
        }
        if (extraLista2Url.isNotBlank()) {
            add(Preset("extra2", extraLista2Name, "Lista adicional 2", extraLista2Url))
        } else {
            add(Preset("extra2_custom", "Lista extra 2", "Pega otra URL M3U", ""))
        }
        add(
            Preset(
                id = "principal",
                title = principalListaName,
                subtitle = "Todo el catálogo público — puede tardar mucho",
                url = principalListaUrl,
            ),
        )
    }
}
