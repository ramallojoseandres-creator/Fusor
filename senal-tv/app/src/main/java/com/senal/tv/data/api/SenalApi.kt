package com.senal.tv.data.api

import com.senal.tv.data.model.LoginRequest
import com.senal.tv.data.model.LoginResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Auth JWT + banner. El catálogo se sincroniza por separado vía
 * GET /playlist.m3u (gzip + ETag) → caché en disco.
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

    /** Public (or auth) banner/news messages for the home screen. */
    @GET("api/banner")
    suspend fun banner(): BannerResponse
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

@kotlinx.serialization.Serializable
data class BannerResponse(
    val items: List<BannerItem> = emptyList(),
    val title: String? = null,
    val body: String? = null,
    val message: String? = null,
    val enabled: Boolean? = null
)

@kotlinx.serialization.Serializable
data class BannerItem(
    val id: String? = null,
    val title: String? = null,
    val body: String? = null,
    val message: String? = null,
    val imageUrl: String? = null,
    val image: String? = null,
    val active: Boolean? = true
) {
    fun headline(): String = title?.trim().orEmpty()
        .ifBlank { message?.trim().orEmpty() }
        .ifBlank { "SEÑAL" }

    fun text(): String = body?.trim().orEmpty()
        .ifBlank { message?.trim().orEmpty() }

    fun art(): String? = imageUrl?.takeIf { it.isNotBlank() } ?: image?.takeIf { it.isNotBlank() }
}
