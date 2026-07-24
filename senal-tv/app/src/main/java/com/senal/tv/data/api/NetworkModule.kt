package com.senal.tv.data.api

import android.os.Build
import com.senal.tv.BuildConfig
import com.senal.tv.ServerConfig
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

    fun createApi(tokenStore: TokenStore): SenalApi {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Accept", "application/json")
            // Header values must be ASCII (OkHttp). Device id is UUID-safe.
            tokenStore.peekDeviceId()?.takeIf { it.isNotBlank() }?.let {
                builder.header("X-Device-Id", it)
            }
            builder.header("X-Device-Name", "SENAL TV")
            builder.header("X-Device-Platform", "android-tv")
            val fingerprint = "${Build.MANUFACTURER}-${Build.MODEL}"
                .replace(Regex("[^\\x20-\\x7E]"), "?")
                .take(80)
            if (fingerprint.isNotBlank()) {
                builder.header("X-Device-Fingerprint", fingerprint)
            }

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
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(ServerConfig.baseUrl())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SenalApi::class.java)
    }
}
