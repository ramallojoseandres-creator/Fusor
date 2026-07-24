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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.senal.tv.AppContainer
import com.senal.tv.BuildConfig
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.HomeSection
import com.senal.tv.ui.common.ContinueRecentsScreen
import com.senal.tv.ui.components.BrandMark
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.components.rememberSenalFocusModifier
import com.senal.tv.ui.favorites.FavoritesScreen
import com.senal.tv.ui.live.LiveTvScreen
import com.senal.tv.ui.movies.MoviesScreen
import com.senal.tv.ui.search.SearchScreen
import com.senal.tv.ui.series.SeriesScreen
import com.senal.tv.ui.settings.SettingsScreen
import com.senal.tv.ui.theme.BrandAccent
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.ui.theme.TileFavorites
import com.senal.tv.ui.theme.TileLive
import com.senal.tv.ui.theme.TileMovies
import com.senal.tv.ui.theme.TileSearch
import com.senal.tv.ui.theme.TileSeries
import com.senal.tv.util.CatalogRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class HomeColumn(
    val section: HomeSection,
    val label: String,
    val accent: Color
)

private val columns = listOf(
    HomeColumn(HomeSection.LIVE, "EN VIVO", TileLive),
    HomeColumn(HomeSection.MOVIES, "PELÍCULAS", TileMovies),
    HomeColumn(HomeSection.SERIES, "SERIES", TileSeries),
    HomeColumn(HomeSection.FAVORITES, "FAVORITOS", TileFavorites),
    HomeColumn(HomeSection.SEARCH, "BUSCAR", TileSearch)
)

@Composable
fun HomeScreen(
    container: AppContainer,
    onPlay: (CatalogItem, Long, List<CatalogItem>) -> Unit,
    onLogout: () -> Unit
) {
    var section by remember { mutableStateOf<HomeSection?>(null) }
    var live by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var movies by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var bannerIndex by remember { mutableIntStateOf(0) }
    var clock by remember { mutableStateOf(nowParts()) }
    var catalogReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = nowParts()
            delay(15_000)
        }
    }

    // Load catalog once into memory/disk, then serve instantly.
    LaunchedEffect(Unit) {
        runCatching { container.catalogRepository.ensureCatalogLoaded() }
        catalogReady = true
        val defaultLiveCategory = runCatching {
            CatalogRules.defaultCategory(container.catalogRepository.categories("live"))
        }.getOrNull()
        runCatching {
            container.catalogRepository.page(
                type = "live",
                category = defaultLiveCategory,
                page = 1,
                limit = 20
            )
        }.onSuccess {
            live = CatalogRules.preferredLiveItems(it.resolveItems())
        }
        runCatching { container.catalogRepository.page("movie", page = 1, limit = 12) }
            .onSuccess { movies = it.resolveItems() }
        // Background refresh — does not block UI.
        runCatching { container.catalogRepository.refreshInBackgroundSuspend() }
    }

    LaunchedEffect(movies) {
        if (movies.size < 2) return@LaunchedEffect
        while (true) {
            delay(3500)
            bannerIndex = (bannerIndex + 1) % movies.size
        }
    }

    SenalBackground {
        if (section == null) {
            SenalHome(
                clock = clock,
                livePreview = live.firstOrNull(),
                banners = movies.ifEmpty { live }.let { list ->
                    if (list.isEmpty()) emptyList()
                    else listOf(
                        list[bannerIndex % list.size],
                        list[(bannerIndex + 1) % list.size]
                    )
                },
                catalogReady = catalogReady,
                onOpen = { section = it },
                onPlayLive = { ch ->
                    onPlay(ch, 0L, live.ifEmpty { listOf(ch) })
                }
            )
        } else {
            Box(Modifier.fillMaxSize().padding(20.dp)) {
                SectionBody(
                    section = section!!,
                    container = container,
                    onBack = { section = null },
                    onPlay = onPlay,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun SenalHome(
    clock: ClockParts,
    livePreview: CatalogItem?,
    banners: List<CatalogItem>,
    catalogReady: Boolean,
    onOpen: (HomeSection) -> Unit,
    onPlayLive: (CatalogItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 24.dp)
    ) {
        HeaderBar(
            clock = clock,
            catalogReady = catalogReady,
            onSettings = { onOpen(HomeSection.SETTINGS) }
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LivePreviewCard(
                item = livePreview,
                onClick = { livePreview?.let(onPlayLive) },
                modifier = Modifier
                    .weight(1.35f)
                    .fillMaxHeight()
            )
            BannerStack(
                items = banners,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            columns.forEach { col ->
                HomeTile(
                    label = col.label,
                    accent = col.accent,
                    onClick = { onOpen(col.section) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            Column(
                modifier = Modifier.width(44.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextChipButton("⏳", onClick = { onOpen(HomeSection.RECENTS) })
                TextChipButton("★", onClick = { onOpen(HomeSection.FAVORITES) })
            }
        }
    }
}

@Composable
private fun HeaderBar(
    clock: ClockParts,
    catalogReady: Boolean,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(compact = true)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            color = TextMuted,
            fontSize = 12.sp
        )
        if (catalogReady) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Lista lista",
                color = BrandAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(modifier = Modifier.weight(1f))
        Text(
            text = clock.time,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(clock.week, color = TextMuted, fontSize = 10.sp)
            Text(clock.date, color = TextMuted, fontSize = 10.sp)
        }
        Spacer(Modifier.width(16.dp))
        TextChipButton("⚙", onClick = onSettings, size = 34.dp)
    }
}

@Composable
private fun LivePreviewCard(
    item: CatalogItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(rememberSenalFocusModifier(focused, big = true))
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = GraphiteCard,
            focusedContainerColor = GraphiteCard
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                if (item != null) {
                    AsyncImage(
                        model = item.resolvePoster() ?: item.resolveLogo(),
                        contentDescription = item.resolveTitle(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0C1A22), Color(0xFF10151C))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SEÑAL EN VIVO", color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC000000))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(BrandAccent, RoundedCornerShape(50))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = item?.resolveTitle() ?: "Señal en vivo",
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun BannerStack(items: List<CatalogItem>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(2) { i ->
            val item = items.getOrNull(i)
            var focused by remember { mutableStateOf(false) }
            Surface(
                onClick = {},
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(rememberSenalFocusModifier(focused, big = false))
                    .onFocusChanged { focused = it.isFocused },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = GraphiteCard,
                    focusedContainerColor = GraphiteCard
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                content = {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) {
                        if (item != null) {
                            AsyncImage(
                                model = item.resolvePoster() ?: item.resolveLogo(),
                                contentDescription = item.resolveTitle(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.28f)))
                            Text(
                                text = item.resolveTitle(),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun HomeTile(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(rememberSenalFocusModifier(focused, big = true))
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = if (focused) 0.95f else 0.72f),
                                accent.copy(alpha = if (focused) 0.55f else 0.35f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    )
}

@Composable
private fun TextChipButton(
    label: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 30.dp
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .then(rememberSenalFocusModifier(focused, big = false))
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (focused) BrandAccent else GraphiteCard,
            focusedContainerColor = BrandAccent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    color = if (focused) Color.Black else TextPrimary,
                    fontSize = 14.sp
                )
            }
        }
    )
}

@Composable
private fun SectionBody(
    section: HomeSection,
    container: AppContainer,
    onBack: () -> Unit,
    onPlay: (CatalogItem, Long, List<CatalogItem>) -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandMark(compact = true)
            FocusableButton(label = "INICIO", onClick = onBack, primary = true)
        }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
            },
            modifier = Modifier.weight(1f),
            label = "section",
            content = { current ->
                when (current) {
                    HomeSection.LIVE -> LiveTvScreen(container) { item, n -> onPlay(item, 0L, n) }
                    HomeSection.MOVIES -> MoviesScreen(container) { onPlay(it, 0L, listOf(it)) }
                    HomeSection.SERIES -> SeriesScreen(container) { onPlay(it, 0L, listOf(it)) }
                    HomeSection.FAVORITES -> FavoritesScreen(container) { onPlay(it, 0L, listOf(it)) }
                    HomeSection.CONTINUE -> ContinueRecentsScreen(
                        container, ContinueRecentsScreen.Mode.CONTINUE
                    ) { item, pos -> onPlay(item, pos, listOf(item)) }
                    HomeSection.RECENTS -> ContinueRecentsScreen(
                        container, ContinueRecentsScreen.Mode.RECENTS
                    ) { item, _ -> onPlay(item, 0L, listOf(item)) }
                    HomeSection.SEARCH -> SearchScreen(container) { onPlay(it, 0L, listOf(it)) }
                    HomeSection.SETTINGS -> SettingsScreen(container, onLogout)
                }
            }
        )
    }
}

private data class ClockParts(val time: String, val week: String, val date: String)

private fun nowParts(): ClockParts {
    val now = Date()
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val week = SimpleDateFormat("EEE", Locale("es")).format(now).uppercase(Locale.getDefault())
    val date = SimpleDateFormat("dd/MM", Locale.getDefault()).format(now)
    return ClockParts(time, week, date)
}
