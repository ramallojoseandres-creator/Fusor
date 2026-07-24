package com.senal.tv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.senal.tv.AppContainer
import com.senal.tv.data.local.DanielVodStore
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.Category
import com.senal.tv.ui.components.EmptyState
import com.senal.tv.ui.components.ErrorMessage
import com.senal.tv.ui.components.LoadingPulse
import com.senal.tv.ui.components.SenalClickable
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.SignalCyan
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.util.DeviceUi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Películas / series por categoría (sidebar + lista), sin mural/banner de cine.
 */
@Composable
fun VodCategoryScreen(
    title: String,
    kind: DanielVodStore.Kind,
    container: AppContainer,
    onPlay: (CatalogItem) -> Unit
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var total by remember { mutableIntStateOf(0) }
    var loadingCats by remember { mutableStateOf(true) }
    var loadingItems by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val pageSize = 60
    val catW = if (DeviceUi.isTouchBuild) 220.dp else 200.dp

    LaunchedEffect(kind) {
        loadingCats = true
        error = null
        runCatching {
            container.danielVodStore.ensureLoaded(kind)
            container.danielVodStore.categories(kind)
        }.onSuccess { cats ->
            categories = cats
            selected = cats.firstOrNull()?.label()
        }.onFailure {
            error = it.message ?: "No se pudo cargar el catálogo"
        }
        loadingCats = false
    }

    LaunchedEffect(kind, selected) {
        val cat = selected ?: return@LaunchedEffect
        loadingItems = true
        error = null
        runCatching {
            container.catalogRepository.page(
                type = if (kind == DanielVodStore.Kind.SERIES) "series" else "movie",
                category = cat,
                page = 1,
                limit = pageSize
            )
        }.onSuccess { res ->
            items = res.resolveItems()
            page = 1
            total = res.total ?: items.size
            hasMore = res.resolveHasMore(pageSize)
            listState.scrollToItem(0)
        }.onFailure {
            error = it.message
            items = emptyList()
        }
        loadingItems = false
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= items.lastIndex - 8
        }
    }

    LaunchedEffect(items, hasMore, selected, loadingMore, loadingItems) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { need ->
                if (!need || !hasMore || loadingMore || loadingItems) return@collect
                val cat = selected ?: return@collect
                loadingMore = true
                val next = page + 1
                runCatching {
                    container.catalogRepository.page(
                        type = if (kind == DanielVodStore.Kind.SERIES) "series" else "movie",
                        category = cat,
                        page = next,
                        limit = pageSize
                    )
                }.onSuccess { res ->
                    val chunk = res.resolveItems()
                    items = (items + chunk).distinctBy { it.resolveId() }
                    page = next
                    hasMore = res.resolveHasMore(pageSize) && chunk.isNotEmpty()
                }
                loadingMore = false
            }
    }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(catW)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xB3181C24))
        ) {
            Text(
                text = title,
                color = SignalCyan,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            )
            if (loadingCats) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingPulse("Cargando categorías…")
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    categories.forEach { cat ->
                        val label = cat.label()
                        val on = label == selected
                        var focused by remember(label) { mutableStateOf(false) }
                        SenalClickable(
                            onClick = { selected = label },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focused = it.isFocused },
                            shape = RoundedCornerShape(0.dp),
                            containerColor = when {
                                on || focused -> BrandOrange.copy(alpha = 0.92f)
                                else -> Color.Transparent
                            },
                            focusedContainerColor = BrandOrange,
                            pressedContainerColor = BrandOrange,
                            onFocusedChange = { focused = it }
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = label,
                                        color = TextPrimary,
                                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${cat.count ?: 0}",
                                        color = if (on || focused) Color.White.copy(0.85f) else TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xB3181C24))
                .padding(10.dp)
        ) {
            Text(
                text = selected?.let { "$it · $total títulos" } ?: "Elige una categoría",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            error?.let { ErrorMessage(it) }
            when {
                loadingItems && items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingPulse("Cargando…")
                    }
                }
                items.isEmpty() && !loadingItems -> EmptyState("Sin títulos en esta categoría")
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items, key = { it.resolveId() }) { item ->
                            VodRow(
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
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
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
}

@Composable
private fun VodRow(
    item: CatalogItem,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    SenalClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(8.dp),
        containerColor = when {
            focused -> BrandOrange.copy(0.92f)
            else -> Color.White.copy(0.04f)
        },
        focusedContainerColor = BrandOrange,
        pressedContainerColor = BrandOrange,
        onFocusedChange = { focused = it }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 48.dp, height = 68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF101016)),
                contentAlignment = Alignment.Center
            ) {
                val poster = item.resolvePoster()
                if (!poster.isNullOrBlank()) {
                    AsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = item.resolveTitle().take(1),
                        color = Color.White.copy(0.7f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.resolveTitle(),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.resolveCategory(),
                    color = if (focused) Color.White.copy(0.85f) else TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
