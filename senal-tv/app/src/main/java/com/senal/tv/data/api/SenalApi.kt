package com.senal.tv.data.api

import com.senal.tv.data.model.LoginRequest
import com.senal.tv.data.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * El servidor SEÑAL solo gestiona usuarios (login / sesión JWT).
 * Catálogo y streams van embebidos en la APK — no hay endpoints de contenido aquí.
 */
interface SenalApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse
}
