package com.senal.tv.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.senal.tv.AppContainer
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.HomeSection
import com.senal.tv.ui.common.ContinueRecentsScreen
import com.senal.tv.ui.components.BrandMark
import com.senal.tv.ui.components.ColorTile
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.favorites.FavoritesScreen
import com.senal.tv.ui.live.LiveTvScreen
import com.senal.tv.ui.movies.MoviesScreen
import com.senal.tv.ui.search.SearchScreen
import com.senal.tv.ui.series.SeriesScreen
import com.senal.tv.ui.settings.SettingsScreen
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.ui.theme.TileCoral
import com.senal.tv.ui.theme.TileCyan
import com.senal.tv.ui.theme.TileGreen
import com.senal.tv.ui.theme.TilePurple
import com.senal.tv.ui.theme.Violet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class DashTile(
    val section: HomeSection,
    val label: String,
    val color: Color
)

private val tiles = listOf(
    DashTile(HomeSection.LIVE, "TV EN VIVO", BrandOrange),
    DashTile(HomeSection.MOVIES, "PELÍCULAS", TilePurple),
    DashTile(HomeSection.SERIES, "SERIES", TileCyan),
    DashTile(HomeSection.FAVORITES, "FAVORITOS", TileGreen),
    DashTile(HomeSection.CONTINUE, "CONTINUAR", TileCoral),
    DashTile(HomeSection.RECENTS, "RECIENTES", Violet),
    DashTile(HomeSection.SEARCH, "BÚSQUEDA", Teal),
    DashTile(HomeSection.SETTINGS, "AJUSTES", GraphiteCard)
)

@Composable
fun HomeScreen(
    container: AppContainer,
    onPlay: (CatalogItem, Long, List<CatalogItem>) -> Unit,
    onLogout: () -> Unit
) {
    var section by remember { mutableStateOf<HomeSection?>(null) }
    var preview by remember { mutableStateOf<CatalogItem?>(null) }
    var clock by remember { mutableStateOf(currentClock()) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = currentClock()
            delay(30_000)
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            container.catalogRepository.page(type = "live", page = 1, limit = 8)
        }.onSuccess { response ->
            preview = response.resolveItems().firstOrNull()
        }
    }

    SenalBackground(
        content = {
            if (section == null) {
                Dashboard(
                    clock = clock,
                    preview = preview,
                    onOpen = { section = it },
                    onPlayPreview = { item -> onPlay(item, 0L, listOf(item)) }
                )
            } else {
                SectionHost(
                    section = section!!,
                    container = container,
                    onBackHome = { section = null },
                    onPlay = onPlay,
                    onLogout = onLogout
                )
            }
        }
    )
}

@Composable
private fun SectionHost(
    section: HomeSection,
    container: AppContainer,
    onBackHome: () -> Unit,
    onPlay: (CatalogItem, Long, List<CatalogItem>) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandMark(compact = true)
            ColorTile(
                label = "INICIO",
                color = BrandOrange.copy(alpha = 0.85f),
                onClick = onBackHome,
                modifier = Modifier.width(160.dp),
                height = 52.dp
            )
        }
        Box(modifier = Modifier.height(14.dp))
        AnimatedContent(
            targetState = section,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            modifier = Modifier.weight(1f),
            label = "section",
            content = { current ->
                when (current) {
                    HomeSection.LIVE -> LiveTvScreen(
                        container = container,
                        onPlay = { item, neighbors -> onPlay(item, 0L, neighbors) }
                    )
                    HomeSection.MOVIES -> MoviesScreen(
                        container = container,
                        onPlay = { onPlay(it, 0L, listOf(it)) }
                    )
                    HomeSection.SERIES -> SeriesScreen(
                        container = container,
                        onPlay = { onPlay(it, 0L, listOf(it)) }
                    )
                    HomeSection.FAVORITES -> FavoritesScreen(
                        container = container,
                        onPlay = { onPlay(it, 0L, listOf(it)) }
                    )
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
                    HomeSection.SEARCH -> SearchScreen(
                        container = container,
                        onPlay = { onPlay(it, 0L, listOf(it)) }
                    )
                    HomeSection.SETTINGS -> SettingsScreen(
                        container = container,
                        onLogout = onLogout
                    )
                }
            }
        )
    }
}

@Composable
private fun Dashboard(
    clock: String,
    preview: CatalogItem?,
    onOpen: (HomeSection) -> Unit,
    onPlayPreview: (CatalogItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandMark(compact = true)
                Text(text = clock, style = LocalSenalTypography.current.subtitle, color = TextMuted)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2A1A08), Color(0xFF121218), Color(0xFF0B1C1A))
                        )
                    )
                    .padding(22.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(text = "SEÑAL", style = LocalSenalTypography.current.title, color = BrandOrange)
                    Text(
                        text = "TV en vivo · Películas · Series",
                        style = LocalSenalTypography.current.subtitle
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Navega con el control remoto. Enfoque grande, cambios rápidos.",
                        style = LocalSenalTypography.current.body,
                        color = TextPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.Black)
            ) {
                if (preview != null) {
                    AsyncImage(
                        model = preview.resolvePoster() ?: preview.resolveLogo(),
                        contentDescription = preview.resolveTitle(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC000000))
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "EN VIVO",
                            style = LocalSenalTypography.current.caption,
                            color = BrandOrange
                        )
                        Text(
                            text = preview.resolveTitle(),
                            style = LocalSenalTypography.current.body,
                            color = TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        content = {
                            ColorTile(
                                label = "VER",
                                color = BrandOrange,
                                onClick = { onPlayPreview(preview) },
                                modifier = Modifier.width(110.dp),
                                height = 44.dp
                            )
                        }
                    )
                } else {
                    Text(
                        text = "Preview en vivo",
                        style = LocalSenalTypography.current.subtitle,
                        color = TextMuted,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            tiles.forEach { tile ->
                ColorTile(
                    label = tile.label,
                    color = tile.color,
                    onClick = { onOpen(tile.section) },
                    modifier = Modifier.weight(1f),
                    height = 64.dp
                )
            }
        }
    }
}

private fun currentClock(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
