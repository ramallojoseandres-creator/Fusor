package com.senal.tv.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.senal.tv.BuildConfig
import com.senal.tv.ServerConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Actualizaciones OTA desde el panel:
 *   http://IP:3000/dowloads/latest.json
 *   http://IP:3000/dowloads/SenalTV.apk
 *
 * (También acepta /downloads/ por si se corrige el typo en el servidor.)
 */
class AppUpdater(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun check(): UpdateStatus = withContext(Dispatchers.IO) {
        val manifest = fetchManifest()
            ?: return@withContext UpdateStatus.Unavailable("No hay latest.json en /dowloads")
        val remoteCode = manifest.versionCode
        val localCode = BuildConfig.VERSION_CODE
        if (remoteCode <= localCode) {
            return@withContext UpdateStatus.UpToDate(
                localName = BuildConfig.VERSION_NAME,
                remoteName = manifest.versionName ?: BuildConfig.VERSION_NAME
            )
        }
        val apkName = manifest.apk?.trim().orEmpty().ifBlank { "SenalTV.apk" }
        val apkUrl = resolveApkUrl(apkName)
        UpdateStatus.Available(
            remoteCode = remoteCode,
            remoteName = manifest.versionName ?: remoteCode.toString(),
            apkUrl = apkUrl,
            changelog = manifest.changelog.orEmpty()
        )
    }

    suspend fun downloadAndInstall(
        available: UpdateStatus.Available,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
            val out = File(dir, "SenalTV-update.apk")
            if (out.exists()) out.delete()

            val req = Request.Builder()
                .url(available.apkUrl)
                .header("User-Agent", "SENAL-TV/${BuildConfig.VERSION_NAME}")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    error("HTTP ${resp.code} al descargar APK")
                }
                val body = resp.body ?: error("APK vacío")
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            readTotal += n
                            if (total > 0) {
                                onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                        output.flush()
                    }
                }
            }

            if (out.length() < 64_000L) error("APK demasiado pequeño (${out.length()} bytes)")
            onProgress(1f)

            withContext(Dispatchers.Main) {
                promptInstall(out)
            }
        }
    }

    fun promptInstall(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                // El usuario debe volver a pulsar Actualizar tras permitir instalaciones.
                return
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    private fun fetchManifest(): UpdateManifest? {
        for (base in ServerConfig.downloadsBases()) {
            val url = base + "latest.json"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "SENAL-TV/${BuildConfig.VERSION_NAME}")
                .get()
                .build()
            val text = runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                }
            }.getOrNull() ?: continue
            if (text.isNullOrBlank()) continue
            return runCatching { json.decodeFromString(UpdateManifest.serializer(), text) }.getOrNull()
        }
        return null
    }

    private fun resolveApkUrl(apkName: String): String {
        val file = apkName.removePrefix("/")
        if (file.startsWith("http://") || file.startsWith("https://")) return file
        return ServerConfig.downloadsBases().first() + file
    }
}

@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String? = null,
    val apk: String? = null,
    val changelog: String? = null
)

sealed class UpdateStatus {
    data class Available(
        val remoteCode: Int,
        val remoteName: String,
        val apkUrl: String,
        val changelog: String
    ) : UpdateStatus()

    data class UpToDate(val localName: String, val remoteName: String) : UpdateStatus()
    data class Unavailable(val reason: String) : UpdateStatus()
}
