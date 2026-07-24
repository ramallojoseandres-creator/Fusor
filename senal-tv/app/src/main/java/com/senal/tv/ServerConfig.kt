package com.senal.tv

/**
 * Configuración global del servidor SEÑAL.
 *
 * Cambia [SERVER_IP] y [SERVER_PORT] aquí (o en `app/build.gradle.kts`
 * → `buildConfigField`) y recompila. Todas las peticiones Retrofit / M3U
 * usan [baseUrl].
 */
object ServerConfig {
    /** IP o host del panel (sin http://). */
    const val SERVER_IP: String = BuildConfig.SERVER_IP

    /** Puerto HTTP del panel. */
    const val SERVER_PORT: Int = BuildConfig.SERVER_PORT

    /** Base URL con barra final, p.ej. http://185.192.20.245:3000/ */
    fun baseUrl(): String {
        val fromBuild = BuildConfig.API_BASE_URL.trim()
        if (fromBuild.isNotBlank()) {
            return if (fromBuild.endsWith("/")) fromBuild else "$fromBuild/"
        }
        return "http://$SERVER_IP:$SERVER_PORT/"
    }

    fun healthUrl(): String = baseUrl() + "api/health"
    /** Ruta correcta del panel 2.x (ya no sirve /playlist.m3u). */
    fun catalogUrl(): String = baseUrl() + "api/catalog"
    fun bannerUrl(): String = baseUrl() + "api/banner"

    /**
     * Carpeta de APKs en el VPS.
     * El panel usa `/dowloads/` (así está en el servidor); también probamos `/downloads/`.
     */
    fun downloadsBases(): List<String> = listOf(
        baseUrl() + "dowloads/",
        baseUrl() + "downloads/",
    )

    fun updateManifestUrl(): String = downloadsBases().first() + "latest.json"
}
