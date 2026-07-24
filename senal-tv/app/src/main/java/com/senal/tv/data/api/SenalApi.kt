package com.senal.tv.data.api

import com.senal.tv.data.model.CatalogResponse
import com.senal.tv.data.model.FavoriteRequest
import com.senal.tv.data.model.HealthResponse
import com.senal.tv.data.model.LoginRequest
import com.senal.tv.data.model.LoginResponse
import com.senal.tv.data.model.MeResponse
import com.senal.tv.data.model.PlaybackResponse
import com.senal.tv.data.model.SearchResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * APIs TV del panel SEÑAL (VPS):
 * - GET  /api/health
 * - POST /api/auth/login
 * - GET  /api/me
 * - GET  /api/catalog
 *
 * Headers: Authorization: Bearer <token> + X-Device-Id: <id>
 */
interface SenalApi {

    @GET("api/health")
    suspend fun health(): HealthResponse

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("api/me")
    suspend fun me(): MeResponse

    /** Catálogo autenticado. `type` es opcional (servidor 2.x puede devolver todo). */
    @GET("api/catalog")
    suspend fun catalog(
        @Query("type") type: String? = null,
        @Query("category") category: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("q") q: String? = null
    ): CatalogResponse

    /** Variante path-style por compatibilidad con paneles antiguos. */
    @GET("api/catalog/{type}")
    suspend fun catalogByPath(
        @Path("type") type: String,
        @Query("category") category: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null
    ): CatalogResponse

    @GET("api/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 40
    ): SearchResponse

    @POST("api/playback/{id}")
    suspend fun playback(
        @Path("id") id: String,
        @Body body: Map<String, String> = emptyMap()
    ): PlaybackResponse

    @GET("api/favorites")
    suspend fun favorites(): JsonElement

    @POST("api/favorites")
    suspend fun addFavorite(@Body body: FavoriteRequest): JsonElement

    @GET("api/history")
    suspend fun history(): JsonElement

    @GET("api/continue")
    suspend fun continueWatching(): JsonElement
}
