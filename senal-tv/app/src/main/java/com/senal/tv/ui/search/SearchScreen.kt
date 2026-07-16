package com.senal.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.senal.tv.AppContainer
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.components.EmptyState
import com.senal.tv.ui.components.LoadingPulse
import com.senal.tv.ui.components.PosterCard
import com.senal.tv.ui.components.SectionHeader
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    container: AppContainer,
    onPlay: (CatalogItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun enqueueSearch(value: String) {
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(220)
            if (value.isBlank()) {
                results = emptyList()
                loading = false
                return@launch
            }
            loading = true
            results = runCatching { container.catalogRepository.search(value) }.getOrDefault(emptyList())
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader("BÚSQUEDA", "Canales, películas y series en tiempo real")
        BasicTextField(
            value = query,
            onValueChange = {
                query = it
                enqueueSearch(it)
            },
            singleLine = true,
            cursorBrush = SolidColor(Teal),
            textStyle = LocalSenalTypography.current.body.copy(color = TextPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .background(Color(0xFF12121A), RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Escribe para buscar…", color = TextMuted, style = LocalSenalTypography.current.body)
                    }
                    inner()
                }
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 18.dp)
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingPulse("Buscando…")
                }
                query.isNotBlank() && results.isEmpty() -> EmptyState("Sin resultados")
                results.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(170.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(results, key = { it.resolveId() }) { item ->
                        PosterCard(
                            item = item,
                            onClick = {
                                scope.launch {
                                    container.libraryRepository.markHistory(item)
                                    onPlay(item)
                                }
                            },
                            onLongClick = {
                                scope.launch { container.libraryRepository.toggleFavorite(item) }
                            }
                        )
                    }
                }
                else -> EmptyState("La búsqueda es instantánea")
            }
        }
    }
}
