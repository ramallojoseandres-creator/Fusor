package com.senal.tv.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.compose.material3.Text
import com.senal.tv.AppContainer
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.HomeSection
import com.senal.tv.ui.components.BrandMark
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.favorites.FavoritesScreen
import com.senal.tv.ui.live.LiveTvScreen
import com.senal.tv.ui.movies.MoviesScreen
import com.senal.tv.ui.search.SearchScreen
import com.senal.tv.ui.series.SeriesScreen
import com.senal.tv.ui.settings.SettingsScreen
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.Violet
import com.senal.tv.ui.common.ContinueRecentsScreen

private data class MenuEntry(val section: HomeSection, val label: String)

private val menu = listOf(
    MenuEntry(HomeSection.LIVE, "TV EN VIVO"),
    MenuEntry(HomeSection.MOVIES, "PELÍCULAS"),
    MenuEntry(HomeSection.SERIES, "SERIES"),
    MenuEntry(HomeSection.FAVORITES, "FAVORITOS"),
    MenuEntry(HomeSection.CONTINUE, "CONTINUAR VIENDO"),
    MenuEntry(HomeSection.RECENTS, "RECIENTES"),
    MenuEntry(HomeSection.SEARCH, "BÚSQUEDA"),
    MenuEntry(HomeSection.SETTINGS, "AJUSTES")
)

@Composable
fun HomeScreen(
    container: AppContainer,
    onPlay: (CatalogItem, Long, List<CatalogItem>) -> Unit,
    onLogout: () -> Unit
) {
    var section by remember { mutableStateOf(HomeSection.LIVE) }

    SenalBackground {
        Row(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .padding(end = 18.dp)
            ) {
                BrandMark(compact = true)
                Spacer(Modifier.height(24.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(menu, key = { it.section }) { entry ->
                        val selected = section == entry.section
                        Surface(
                            onClick = { section = entry.section },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (selected) Violet else GraphiteCard,
                                focusedContainerColor = if (selected) Violet else Teal.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = entry.label,
                                style = LocalSenalTypography.current.button,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                label = "section"
            ) { current ->
                when (current) {
                    HomeSection.LIVE -> LiveTvScreen(
                        container,
                        onPlay = { item, neighbors -> onPlay(item, 0L, neighbors) }
                    )
                    HomeSection.MOVIES -> MoviesScreen(container, onPlay = { onPlay(it, 0L, listOf(it)) })
                    HomeSection.SERIES -> SeriesScreen(container, onPlay = { onPlay(it, 0L, listOf(it)) })
                    HomeSection.FAVORITES -> FavoritesScreen(container, onPlay = { onPlay(it, 0L, listOf(it)) })
                    HomeSection.CONTINUE -> ContinueRecentsScreen(
                        container = container,
                        mode = ContinueRecentsScreen.Mode.CONTINUE,
                        onPlay = { item, pos -> onPlay(item, pos, listOf(item)) }
                    )
                    HomeSection.RECENTS -> ContinueRecentsScreen(
                        container = container,
                        mode = ContinueRecentsScreen.Mode.RECENTS,
                        onPlay = { item, _ -> onPlay(item, 0L, listOf(item)) }
                    )
                    HomeSection.SEARCH -> SearchScreen(container, onPlay = { onPlay(it, 0L, listOf(it)) })
                    HomeSection.SETTINGS -> SettingsScreen(container, onLogout = onLogout)
                }
            }
        }
    }
}
