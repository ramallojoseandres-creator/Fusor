package com.senal.tv.ui.movies

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.senal.tv.AppContainer
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.components.EmptyState
import com.senal.tv.ui.components.ErrorMessage
import com.senal.tv.ui.components.LoadingPulse
import com.senal.tv.ui.components.PosterCard
import com.senal.tv.ui.components.SectionHeader
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * PELICULAS = catalogo iptv-org movies.m3u
 * https://iptv-org.github.io/iptv/categories/movies.m3u
 */
@Composable
fun MoviesScreen(
    container: AppContainer,
    onPlay: (CatalogItem) -> Unit
) {
    CatalogGridScreen(
        title = "PELÍCULAS",
        subtitle = "iptv-org · Movies (caché local tras 1ª carga)",
        type = "movie",
        container = container,
        onPlay = onPlay
    )
}

@Composable
fun CatalogGridScreen(
    title: String,
    subtitle: String,
    type: String,
    container: AppContainer,
    onPlay: (CatalogItem) -> Unit
) {
    var movieItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var totalHint by remember { mutableStateOf<Int?>(null) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val pageSize = 48
    val isMovies = type.equals("movie", ignoreCase = true) ||
        type.equals("movies", ignoreCase = true)

    LaunchedEffect(type) {
        loading = true
        error = null
        runCatching {
            if (isMovies) {
                container.remoteMoviesStore.ensureLoaded()
            }
            container.catalogRepository.page(type = type, page = 1, limit = pageSize)
        }.onSuccess { response ->
            movieItems = response.resolveItems()
            hasMore = response.resolveHasMore(pageSize)
            totalHint = response.total
            page = 1
        }.onFailure { err ->
            error = err.message ?: "No se pudo cargar el catálogo"
        }
        loading = false
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= movieItems.lastIndex - 6
        }
    }

    LaunchedEffect(movieItems, hasMore, loadingMore, loading) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { need ->
                if (!need || !hasMore || loadingMore || loading) return@collect
                loadingMore = true
                val next = page + 1
                runCatching {
                    container.catalogRepository.page(type = type, page = next, limit = pageSize)
                }.onSuccess { response ->
                    val chunk = response.resolveItems()
                    movieItems = (movieItems + chunk).distinctBy { it.resolveId() }
                    page = next
                    hasMore = response.resolveHasMore(pageSize) && chunk.isNotEmpty()
                }
                loadingMore = false
            }
    }

    val headerSub = if (totalHint != null && totalHint!! > 0) {
        "$subtitle · ${totalHint} títulos"
    } else {
        subtitle
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(title, headerSub)
        if (error != null) {
            ErrorMessage(error!!)
        }
        when {
            loading && movieItems.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingPulse(
                        if (isMovies) "Descargando películas iptv-org…" else "Cargando…"
                    )
                }
            }
            error != null && movieItems.isEmpty() -> {
                EmptyState(error ?: "Error")
            }
            movieItems.isEmpty() -> {
                EmptyState("Sin contenido disponible")
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    state = gridState,
                    contentPadding = PaddingValues(bottom = 28.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(
                        items = movieItems,
                        key = { it.resolveId() }
                    ) { item ->
                        PosterCard(
                            item = item,
                            onClick = {
                                scope.launch {
                                    container.libraryRepository.markHistory(item)
                                    onPlay(item)
                                }
                            },
                            onLongClick = {
                                scope.launch {
                                    container.libraryRepository.toggleFavorite(item)
                                }
                            }
                        )
                    }
                    if (loadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingPulse("Cargando más…")
                            }
                        }
                    }
                }
            }
        }
    }
}
