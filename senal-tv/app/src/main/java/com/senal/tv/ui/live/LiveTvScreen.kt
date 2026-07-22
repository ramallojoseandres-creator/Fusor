package com.senal.tv.ui.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.compose.material3.Text
import com.senal.tv.AppContainer
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.Category
import com.senal.tv.ui.components.ChannelCard
import com.senal.tv.ui.components.EmptyState
import com.senal.tv.ui.components.ErrorMessage
import com.senal.tv.ui.components.LoadingPulse
import com.senal.tv.ui.components.SectionHeader
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.Violet
import com.senal.tv.util.CatalogRules
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun LiveTvScreen(
    container: AppContainer,
    onPlay: (CatalogItem, List<CatalogItem>) -> Unit
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pageSize = 60

    LaunchedEffect(Unit) {
        loading = true
        runCatching { container.catalogRepository.categories("live") }
            .onSuccess {
                categories = it
                selected = CatalogRules.defaultCategory(it)
            }
            .onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(selected) {
        val category = selected ?: return@LaunchedEffect
        loading = true
        error = null
        page = 1
        hasMore = true
        channels = emptyList()
        runCatching {
            container.catalogRepository.page(
                type = "live",
                category = category,
                page = 1,
                limit = pageSize
            )
        }.onSuccess { response ->
            channels = response.resolveItems()
            hasMore = response.resolveHasMore(pageSize)
        }.onFailure {
            error = it.message ?: "No se pudieron cargar los canales"
        }
        loading = false
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= channels.lastIndex - 8
        }
    }

    LaunchedEffect(listState, channels, hasMore, loadingMore, selected) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { need ->
                if (!need || !hasMore || loadingMore || loading || selected == null) return@collect
                loadingMore = true
                val next = page + 1
                runCatching {
                    container.catalogRepository.page(
                        type = "live",
                        category = selected,
                        page = next,
                        limit = pageSize
                    )
                }.onSuccess { response ->
                    val newItems = response.resolveItems()
                    channels = (channels + newItems).distinctBy { it.resolveId() }
                    page = next
                    hasMore = response.resolveHasMore(pageSize) && newItems.isNotEmpty()
                }
                loadingMore = false
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader("TV EN VIVO", "Categorías a la izquierda · canales bajo demanda")
        if (error != null) {
            ErrorMessage(error!!)
        }
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight()
                    .padding(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories, key = { it.label() }) { category ->
                    val active = category.label() == selected
                    Surface(
                        onClick = { selected = category.label() },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (active) Teal.copy(alpha = 0.25f) else GraphiteCard,
                            focusedContainerColor = Violet.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = category.label(),
                            style = LocalSenalTypography.current.body,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when {
                    loading && channels.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { LoadingPulse("Cargando canales…") }

                    channels.isEmpty() -> EmptyState("No hay canales en esta categoría")

                    else -> LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        items(channels, key = { it.resolveId() }) { channel ->
                            ChannelCard(
                                item = channel,
                                onClick = {
                                    scope.launch {
                                        container.libraryRepository.markHistory(channel)
                                        onPlay(channel, channels)
                                    }
                                }
                            )
                        }
                        if (loadingMore) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoadingPulse("Más canales…")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
