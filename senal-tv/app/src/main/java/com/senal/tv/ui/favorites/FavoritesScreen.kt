package com.senal.tv.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senal.tv.AppContainer
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.components.EmptyState
import com.senal.tv.ui.components.PosterCard
import com.senal.tv.ui.components.SectionHeader

@Composable
fun FavoritesScreen(
    container: AppContainer,
    onPlay: (CatalogItem) -> Unit
) {
    val favorites by container.libraryRepository.favorites().collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader("FAVORITOS", "Canales, películas y series sin duplicados")
        if (favorites.isEmpty()) {
            EmptyState("Aún no hay favoritos")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(favorites, key = { it.contentId }) { fav ->
                    val item = CatalogItem(
                        id = fav.contentId,
                        title = fav.title,
                        poster = fav.poster,
                        category = fav.category,
                        type = fav.type.lowercase()
                    )
                    PosterCard(item = item, onClick = { onPlay(item) })
                }
            }
        }
    }
}
