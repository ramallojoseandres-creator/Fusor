package com.senal.tv.ui.series

import androidx.compose.runtime.Composable
import com.senal.tv.AppContainer
import com.senal.tv.data.local.DanielVodStore
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.vod.VodCategoryScreen

/** Series Daniel65 — por categorías (NETFLIX, HBO, etc.). */
@Composable
fun SeriesScreen(
    container: AppContainer,
    onPlay: (CatalogItem) -> Unit
) {
    VodCategoryScreen(
        title = "SERIES",
        kind = DanielVodStore.Kind.SERIES,
        container = container,
        onPlay = onPlay
    )
}
