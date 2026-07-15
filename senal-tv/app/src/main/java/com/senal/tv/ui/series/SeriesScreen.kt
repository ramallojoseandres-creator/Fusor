package com.senal.tv.ui.series

import androidx.compose.runtime.Composable
import com.senal.tv.AppContainer
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.movies.CatalogGridScreen

@Composable
fun SeriesScreen(
    container: AppContainer,
    onPlay: (CatalogItem) -> Unit
) {
    CatalogGridScreen(
        title = "SERIES",
        subtitle = "Temporadas y capítulos se cargan al entrar",
        type = "series",
        container = container,
        onPlay = onPlay
    )
}
