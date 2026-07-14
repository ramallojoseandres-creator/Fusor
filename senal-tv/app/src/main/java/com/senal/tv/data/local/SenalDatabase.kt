package com.senal.tv.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val contentId: String,
    val type: String,
    val title: String,
    val poster: String?,
    val category: String?,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val contentId: String,
    val type: String,
    val title: String,
    val poster: String?,
    val category: String?,
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "continue_watching")
data class ContinueEntity(
    @PrimaryKey val contentId: String,
    val type: String,
    val title: String,
    val poster: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "category_cache")
data class CategoryCacheEntity(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long
)

@Entity(tableName = "epg_cache")
data class EpgCacheEntity(
    @PrimaryKey val channelId: String,
    val nowTitle: String?,
    val nextTitle: String?,
    val updatedAt: Long
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun observe(): Flow<List<FavoriteEntity>>

    @Query("SELECT contentId FROM favorites")
    suspend fun ids(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE contentId = :id")
    suspend fun delete(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE contentId = :id)")
    suspend fun exists(id: String): Boolean
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT :limit")
    fun observe(limit: Int = 40): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HistoryEntity)

    @Query("DELETE FROM history WHERE contentId NOT IN (SELECT contentId FROM history ORDER BY watchedAt DESC LIMIT 200)")
    suspend fun trim()
}

@Dao
interface ContinueDao {
    @Query("SELECT * FROM continue_watching ORDER BY updatedAt DESC LIMIT :limit")
    fun observe(limit: Int = 30): Flow<List<ContinueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ContinueEntity)

    @Query("DELETE FROM continue_watching WHERE contentId = :id")
    suspend fun delete(id: String)
}

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCategory(item: CategoryCacheEntity)

    @Query("SELECT * FROM category_cache WHERE key = :key")
    suspend fun getCategory(key: String): CategoryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEpg(item: EpgCacheEntity)

    @Query("SELECT * FROM epg_cache WHERE channelId = :id")
    suspend fun getEpg(id: String): EpgCacheEntity?
}

@Database(
    entities = [
        FavoriteEntity::class,
        HistoryEntity::class,
        ContinueEntity::class,
        CategoryCacheEntity::class,
        EpgCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SenalDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun history(): HistoryDao
    abstract fun continueWatching(): ContinueDao
    abstract fun cache(): CacheDao
}
