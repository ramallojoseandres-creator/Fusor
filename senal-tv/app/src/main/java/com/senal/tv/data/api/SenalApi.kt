package com.senal.tv.data.api

import com.senal.tv.data.model.LoginRequest
import com.senal.tv.data.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * El servidor SEÑAL solo gestiona usuarios (login / sesión JWT).
 * Catálogo y streams van embebidos en la APK — no hay endpoints de contenido aquí.
 */
interface SenalApi {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): ChangePasswordResponse

    @PATCH("api/admin/users/{id}")
    suspend fun patchUser(
        @Path("id") id: String,
        @Body body: PatchUserRequest
    ): ChangePasswordResponse
}

@kotlinx.serialization.Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
    val oldPassword: String? = null,
    val password: String? = null
)

@kotlinx.serialization.Serializable
data class PatchUserRequest(
    val password: String? = null,
    val active: Boolean? = null
)

@kotlinx.serialization.Serializable
data class ChangePasswordResponse(
    val ok: Boolean? = null,
    val success: Boolean? = null,
    val error: String? = null,
    val message: String? = null
)
