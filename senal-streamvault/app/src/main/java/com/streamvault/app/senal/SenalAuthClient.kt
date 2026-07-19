package com.streamvault.app.senal

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class SenalLoginResult(
    val username: String,
    val token: String?,
)

/**
 * Panel login only (access control). Playlist content always comes from
 * [SenalServerConfig.listaUrl] (`downloads/lista.m3u` on the VPS).
 */
@Singleton
class SenalAuthClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun login(username: String, password: String): Result<SenalLoginResult> =
        withContext(Dispatchers.IO) {
            val user = username.trim()
            val pass = password.trim()
            if (user.isEmpty() || pass.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Usuario y contraseña requeridos"))
            }
            val deviceId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID,
            ) ?: "senal-android"
            val bodyJson = JSONObject()
                .put("username", user)
                .put("password", pass)
                .put("deviceId", deviceId)
                .put("deviceName", "SEÑAL StreamVault")
                .toString()
            val request = Request.Builder()
                .url("${SenalServerConfig.baseUrl}/api/auth/login")
                .post(bodyJson.toRequestBody(jsonMedia))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val err = runCatching { JSONObject(raw).optString("error") }.getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?: "Login falló (${response.code})"
                        error(err)
                    }
                    val json = JSONObject(raw)
                    val token = sequenceOf("token", "accessToken", "jwt")
                        .map { json.optString(it) }
                        .firstOrNull { it.isNotBlank() }
                    SenalLoginResult(username = user, token = token)
                }
            }
        }
}
