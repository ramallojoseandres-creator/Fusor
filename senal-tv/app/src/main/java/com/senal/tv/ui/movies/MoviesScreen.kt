package com.senal.tv.ui.movies

import androidx.compose.runtime.Composable
import com.senal.tv.AppContainer
import com.senal.tv.data.local.DanielVodStore
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.vod.VodCategoryScreen

/** Películas Daniel65 — por categorías (sin mural cine). */
@Composable
fun MoviesScreen(
    container: AppContainer,
    onPlay: (CatalogItem) -> Unit
) {
    VodCategoryScreen(
        title = "PELÍCULAS",
        kind = DanielVodStore.Kind.MOVIES,
        container = container,
        onPlay = onPlay
    )
}
