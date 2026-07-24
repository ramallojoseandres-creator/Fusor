package com.senal.tv.data.api

import android.os.Build
import com.senal.tv.BuildConfig
import com.senal.tv.data.local.TokenStore
import kotlinx.coroutines.runBlocking
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

    fun createApi(tokenStore: TokenStore): SenalApi {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val deviceId = tokenStore.peekDeviceId()
                ?: runBlocking { tokenStore.deviceId() }
            val builder = original.newBuilder()
                .header("Accept", "application/json")
                .header("X-Device-Id", deviceId)
                .header("X-Device-Name", "SEÑAL TV")
                .header("X-Device-Platform", "android-tv")
                .header(
                    "X-Device-Fingerprint",
                    "${Build.MANUFACTURER}-${Build.MODEL}".take(80)
                )

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
