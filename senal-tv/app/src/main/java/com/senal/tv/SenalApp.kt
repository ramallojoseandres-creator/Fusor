package com.senal.tv

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.senal.tv.data.api.NetworkModule
import com.senal.tv.data.local.SenalDatabase
import com.senal.tv.data.local.SettingsStore
import com.senal.tv.data.local.TokenStore
import com.senal.tv.data.repository.AuthRepository
import com.senal.tv.data.repository.CatalogRepository
import com.senal.tv.data.repository.LibraryRepository
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SenalApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .crossfade(180)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.28)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024L * 1024L)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false)
            .build()
    }
}

class AppContainer(app: Application) {
    val tokenStore = TokenStore(app)
    val settingsStore = SettingsStore(app)
    val db: SenalDatabase = Room.databaseBuilder(app, SenalDatabase::class.java, "senal.db")
        .fallbackToDestructiveMigration()
        .build()
    val api = NetworkModule.createApi(tokenStore)
    val authRepository = AuthRepository(api, tokenStore)
    val catalogRepository = CatalogRepository(api, db.cache())
    val libraryRepository = LibraryRepository(
        favoriteDao = db.favorites(),
        historyDao = db.history(),
        continueDao = db.continueWatching(),
        api = api
    )
}
