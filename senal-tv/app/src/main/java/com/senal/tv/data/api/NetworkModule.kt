package com.senal.tv.data.api

import android.os.Build
import com.senal.tv.BuildConfig
import com.senal.tv.data.local.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** OkHttp only allows header values in U+0020..U+007E. */
    private fun asciiHeader(value: String, fallback: String = "unknown"): String {
        val cleaned = buildString(value.length) {
            for (c in value) {
                append(if (c in '\u0020'..'\u007e') c else '?')
            }
        }.trim()
        return cleaned.ifBlank { fallback }
    }

    fun createApi(tokenStore: TokenStore): SenalApi {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            // Never block the calling thread; device id is warmed at app start.
            val deviceId = tokenStore.peekDeviceId() ?: "senal-tv-pending"
            val fingerprint = asciiHeader(
                "${Build.MANUFACTURER}-${Build.MODEL}"
            ).take(80)
            val builder = original.newBuilder()
                .header("Accept", "application/json")
                .header("X-Device-Id", asciiHeader(deviceId, "senal-tv-pending"))
                // ASCII only — "SEÑAL" (Ñ) crashes OkHttp header validation.
                .header("X-Device-Name", "SENAL TV")
                .header("X-Device-Platform", "android-tv")
                .header("X-Device-Fingerprint", fingerprint)

            val token = tokenStore.cachedToken
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }

            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SenalApi::class.java)
    }
}
