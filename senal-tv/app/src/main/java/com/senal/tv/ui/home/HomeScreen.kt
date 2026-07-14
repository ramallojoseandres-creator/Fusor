package com.senal.tv.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.senal.tv.R
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.HomeSection
import com.senal.tv.ui.common.ContinueRecentsScreen
import com.senal.tv.ui.components.BrandMark
import com.senal.tv.ui.components.FocusColumnTile
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.PulseBorder
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.favorites.FavoritesScreen
import com.senal.tv.ui.live.LiveTvScreen
import com.senal.tv.ui.movies.MoviesScreen
import com.senal.tv.ui.search.SearchScreen
import com.senal.tv.ui.series.SeriesScreen
import com.senal.tv.ui.settings.SettingsScreen
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class FocusColumn(
    val section: HomeSection,
    val label: String,
    val normalRes: Int,
    val focusedRes: Int
)

private val focusColumns = listOf(
    FocusColumn(
        HomeSection.LIVE, "TV EN VIVO",
        R.mipmap.bg_main_live_category_item_n, R.mipmap.bg_main_live_category_item_f
    ),
    FocusColumn(
        HomeSection.MOVIES, "PELÍCULAS",
        R.mipmap.bg_main_vod_category_item_n, R.mipmap.bg_main_vod_category_item_f
    ),
    FocusColumn(
        HomeSection.SERIES, "SERIES",
        R.mipmap.bg_main_special_category_item_n, R.mipmap.bg_main_special_category_item_f
    ),
    FocusColumn(
        HomeSection.FAVORITES, "FAVORITOS",
        R.mipmap.bg_main_game_category_item_n, R.mipmap.bg_main_game_category_item_f
    ),
    FocusColumn(
        HomeSection.SEARCH, "BÚSQUEDA",
        R.mipmap.bg_main_vod_category_item_n, R.mipmap.bg_main_vod_category_item_f
    )
)

@Composable
fun HomeScreen(
    container: AppContainer,
    onPlay: (CatalogItem, Long, List<CatalogItem>) -> Unit,
    onLogout: () -> Unit
) {
    var section by remember { mutableStateOf<HomeSection?>(null) }
    var previewItems by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var previewIndex by remember { mutableIntStateOf(0) }
    var clock by remember { mutableStateOf(currentClock()) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = currentClock()
            delay(20_000)
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            container.catalogRepository.page(type = "live", page = 1, limit = 12)
        }.onSuccess { previewItems = it.resolveItems() }
    }

    LaunchedEffect(previewItems) {
        if (previewItems.size < 2) return@LaunchedEffect
        while (true) {
            delay(4_500)
            previewIndex = (previewIndex + 1) % previewItems.size
        }
    }

    SenalBackground(
        content = {
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { -it / 5 } + fadeOut(tween(180)))
                },
                label = "rootSection",
                content = { current ->
                    if (current == null) {
                        FlujoDashboard(
                            clock = clock,
                            preview = previewItems.getOrNull(previewIndex),
                            banners = previewItems.drop(1).take(4),
                            onOpen = { section = it },
                            onPlayPreview = { item -> onPlay(item, 0L, previewItems.ifEmpty { listOf(item) }) }
                        )
                    } else {
                        SectionHost(
                            section = current,
                            container = container,
                            onBackHome = { section = null },
                            onPlay = onPlay,
                            onLogout = onLogout
                        )
                    }
                }
            )
        }
    )
}

@Composable
private fun FlujoDashboard(
    clock: String,
    preview: CatalogItem?,
    banners: List<CatalogItem>,
    onOpen: (HomeSection) -> Unit,
    onPlayPreview: (CatalogItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandMark(compact = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusableButton(
                    label = "HISTORIAL",
                    onClick = { onOpen(HomeSection.RECENTS) },
                    primary = false
                )
                FocusableButton(
                    label = "AJUSTES",
                    onClick = { onOpen(HomeSection.SETTINGS) },
                    primary = false
                )
                Text(text = clock, style = LocalSenalTypography.current.subtitle, color = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Top row: live preview + rotating banners (FLUJO layout)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PulseBorder(
                active = preview != null,
                modifier = Modifier
                    .width(500.dp)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
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
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xDD000000))
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .width(6.dp)
                                    .height(6.dp)
                                    .background(BrandOrange, RoundedCornerShape(50))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("EN VIVO", style = LocalSenalTypography.current.caption, color = BrandOrange)
                                Text(
                                    preview.resolveTitle(),
                                    style = LocalSenalTypography.current.body,
                                    color = TextPrimary
                                )
                            }
                            FocusableButton(
                                label = "VER",
                                onClick = { onPlayPreview(preview) },
                                primary = true
                            )
                        }
                    } else {
                        Text(
                            "Cargando señal…",
                            color = TextMuted,
                            style = LocalSenalTypography.current.subtitle,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                banners.ifEmpty { List(2) { null } }.take(2).forEach { banner ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF15151C))
                    ) {
                        if (banner != null) {
                            AsyncImage(
                                model = banner.resolvePoster() ?: banner.resolveLogo(),
                                contentDescription = banner.resolveTitle(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f))
                            )
                            Text(
                                text = banner.resolveTitle(),
                                style = LocalSenalTypography.current.body,
                                color = TextPrimary,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Bottom FocusColumView row — the signature FLUJO interaction
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.spacedBy((-4).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            focusColumns.forEach { col ->
                FocusColumnTile(
                    label = col.label,
                    normalRes = col.normalRes,
                    focusedRes = col.focusedRes,
                    onClick = { onOpen(col.section) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            FocusableButton(
                label = "✚",
                onClick = { onOpen(HomeSection.CONTINUE) },
                primary = false,
                modifier = Modifier
                    .width(56.dp)
                    .height(56.dp)
                    .padding(start = 8.dp)
            )
        }
    }
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
            FocusableButton(label = "INICIO", onClick = onBackHome, primary = true)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (section) {
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
    }
}

private fun currentClock(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
