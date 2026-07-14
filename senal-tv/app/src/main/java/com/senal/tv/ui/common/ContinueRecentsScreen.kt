package com.senal.tv.ui.common

import androidx.compose.foundation.layout.Arrangement
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

object ContinueRecentsScreen {
    enum class Mode { CONTINUE, RECENTS }
}

@Composable
fun ContinueRecentsScreen(
    container: AppContainer,
    mode: ContinueRecentsScreen.Mode,
    onPlay: (CatalogItem, Long) -> Unit
) {
    if (mode == ContinueRecentsScreen.Mode.CONTINUE) {
        val rows by container.libraryRepository.continueWatching().collectAsState(initial = emptyList())
        Column(modifier = Modifier.fillMaxSize()) {
            SectionHeader("CONTINUAR VIENDO", "Retoma exactamente donde lo dejaste")
            if (rows.isEmpty()) EmptyState("Nada pendiente")
            else LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(rows, key = { it.contentId }) { row ->
                    val item = CatalogItem(
                        id = row.contentId,
                        title = row.title,
                        poster = row.poster,
                        type = row.type.lowercase()
                    )
                    PosterCard(item = item, onClick = { onPlay(item, row.positionMs) })
                }
            }
        }
    } else {
        val rows by container.libraryRepository.history().collectAsState(initial = emptyList())
        Column(modifier = Modifier.fillMaxSize()) {
            SectionHeader("RECIENTES", "Últimos canales, películas y episodios")
            if (rows.isEmpty()) EmptyState("Sin historial todavía")
            else LazyVerticalGrid(
                columns = GridCells.Adaptive(170.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(rows, key = { it.contentId }) { row ->
                    val item = CatalogItem(
                        id = row.contentId,
                        title = row.title,
                        poster = row.poster,
                        category = row.category,
                        type = row.type.lowercase()
                    )
                    PosterCard(item = item, onClick = { onPlay(item, 0L) })
                }
            }
        }
    }
}
