package com.senal.tv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String,
    val deviceName: String = "SENAL TV",
    val platform: String = "android-tv"
)

@Serializable
data class LoginResponse(
    val token: String? = null,
    val accessToken: String? = null,
    val jwt: String? = null,
    val error: String? = null,
    val user: UserInfo? = null
) {
    fun resolveToken(): String? = token ?: accessToken ?: jwt
}

@Serializable
data class UserInfo(
    val id: String? = null,
    val username: String? = null,
    val role: String? = null,
    val expiresAt: String? = null
)

@Serializable
data class ApiError(
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class CatalogResponse(
    val items: List<CatalogItem>? = null,
    val channels: List<CatalogItem>? = null,
    val movies: List<CatalogItem>? = null,
    val series: List<CatalogItem>? = null,
    val data: List<CatalogItem>? = null,
    val results: List<CatalogItem>? = null,
    val categories: List<Category>? = null,
    val page: Int? = null,
    val limit: Int? = null,
    val total: Int? = null,
    val hasMore: Boolean? = null,
    val nextPage: Int? = null
) {
    fun resolveItems(): List<CatalogItem> =
        items ?: channels ?: movies ?: series ?: data ?: results ?: emptyList()

    fun resolveHasMore(requestedLimit: Int): Boolean {
        hasMore?.let { return it }
        nextPage?.let { return true }
        val list = resolveItems()
        if (list.size >= requestedLimit) return true
        val p = page ?: 1
        val t = total ?: return false
        return p * (limit ?: requestedLimit) < t
    }
}

@Serializable
data class Category(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val count: Int? = null
) {
    fun label(): String = name ?: title ?: id.orEmpty()
}

@Serializable
data class CatalogItem(
    val id: String? = null,
    @SerialName("_id") val mongoId: String? = null,
    val streamId: String? = null,
    val name: String? = null,
    val title: String? = null,
    val number: Int? = null,
    val channelNumber: Int? = null,
    val logo: String? = null,
    val poster: String? = null,
    val cover: String? = null,
    val image: String? = null,
    val icon: String? = null,
    val category: String? = null,
    val categoryId: String? = null,
    val group: String? = null,
    val type: String? = null,
    val year: Int? = null,
    val duration: Int? = null,
    val durationSeconds: Int? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val description: String? = null,
    val synopsis: String? = null,
    val genre: String? = null,
    val genres: List<String>? = null,
    val seasons: List<Season>? = null,
    val epg: EpgInfo? = null,
    val epgNow: String? = null,
    val epgNext: String? = null,
    val currentProgram: String? = null,
    val nextProgram: String? = null,
    val nowPlaying: ProgramInfo? = null,
    val nextPlaying: ProgramInfo? = null,
    /** Direct stream from local M3U (no server playback hop). */
    val url: String? = null,
    val streamUrl: String? = null,
    val userAgent: String? = null
) {
    fun resolveId(): String = id ?: mongoId ?: streamId ?: name.hashCode().toString()
    fun resolveTitle(): String = title ?: name ?: "Sin título"
    fun resolveLogo(): String? = logo ?: poster ?: cover ?: image ?: icon
    fun resolvePoster(): String? = poster ?: cover ?: image ?: logo ?: icon
    fun resolveNumber(): Int? = number ?: channelNumber
    fun resolveCategory(): String = category ?: group ?: "General"
    fun resolveSynopsis(): String = synopsis ?: plot ?: description.orEmpty()
    fun resolveGenre(): String = genre ?: genres?.joinToString(", ").orEmpty()
    fun resolveStreamUrl(): String? = streamUrl ?: url
    fun resolveDurationMinutes(): Int? {
        duration?.let { return if (it > 300) it / 60 else it }
        durationSeconds?.let { return it / 60 }
        return null
    }
    fun resolveNow(): String =
        nowPlaying?.title ?: currentProgram ?: epgNow ?: epg?.now ?: epg?.currentTitle.orEmpty()
    fun resolveNext(): String =
        nextPlaying?.title ?: nextProgram ?: epgNext ?: epg?.next ?: epg?.nextTitle.orEmpty()
    fun contentType(): ContentType = when (type?.lowercase()) {
        "live", "tv", "channel" -> ContentType.LIVE
        "movie", "vod", "film" -> ContentType.MOVIE
        "series", "show" -> ContentType.SERIES
        else -> when {
            seasons != null -> ContentType.SERIES
            year != null || rating != null -> ContentType.MOVIE
            else -> ContentType.LIVE
        }
    }
}

@Serializable
data class ProgramInfo(
    val title: String? = null,
    val start: String? = null,
    val end: String? = null
)

@Serializable
data class EpgInfo(
    val now: String? = null,
    val next: String? = null,
    val currentTitle: String? = null,
    val nextTitle: String? = null
)

@Serializable
data class Season(
    val id: String? = null,
    val number: Int? = null,
    val name: String? = null,
    val episodes: List<Episode>? = null
)

@Serializable
data class Episode(
    val id: String? = null,
    val number: Int? = null,
    val title: String? = null,
    val name: String? = null,
    val duration: Int? = null,
    val description: String? = null,
    val poster: String? = null
) {
    fun resolveTitle(): String = title ?: name ?: "Episodio ${number ?: "?"}"
    fun resolveId(): String = id ?: "${title.hashCode()}"
}

@Serializable
data class PlaybackResponse(
    val url: String? = null,
    val streamUrl: String? = null,
    val playbackUrl: String? = null,
    val src: String? = null,
    val headers: Map<String, String>? = null,
    val type: String? = null,
    val title: String? = null,
    val logo: String? = null,
    val error: String? = null,
    val extras: JsonElement? = null
) {
    fun resolveUrl(): String? = url ?: streamUrl ?: playbackUrl ?: src
}

@Serializable
data class SearchResponse(
    val items: List<CatalogItem>? = null,
    val results: List<CatalogItem>? = null,
    val channels: List<CatalogItem>? = null,
    val movies: List<CatalogItem>? = null,
    val series: List<CatalogItem>? = null,
    val data: List<CatalogItem>? = null
) {
    fun resolveAll(): List<CatalogItem> {
        val merged = mutableListOf<CatalogItem>()
        (items ?: results ?: data)?.let { merged += it }
        channels?.let { merged += it }
        movies?.let { merged += it }
        series?.let { merged += it }
        return merged.distinctBy { it.resolveId() }
    }
}

@Serializable
data class FavoriteRequest(
    val contentId: String,
    val type: String
)

enum class ContentType { LIVE, MOVIE, SERIES, EPISODE }

enum class HomeSection {
    LIVE, MOVIES, SERIES, FAVORITES, CONTINUE, RECENTS, SEARCH, SETTINGS
}
