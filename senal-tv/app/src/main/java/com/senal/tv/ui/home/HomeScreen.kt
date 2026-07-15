package com.senal.tv.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.senal.tv.AppContainer
import com.senal.tv.R
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.HomeSection
import com.senal.tv.ui.common.ContinueRecentsScreen
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.components.rememberFlujoFocusModifier
import com.senal.tv.ui.favorites.FavoritesScreen
import com.senal.tv.ui.live.LiveTvScreen
import com.senal.tv.ui.movies.MoviesScreen
import com.senal.tv.ui.search.SearchScreen
import com.senal.tv.ui.series.SeriesScreen
import com.senal.tv.ui.settings.SettingsScreen
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.util.CatalogRules
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** Home nav tiles — estilo gordo / brutalista (otra fuente). */
private data class NavTile(
    val section: HomeSection,
    val label: String,
    val accent: Color
)

private val navTiles = listOf(
    NavTile(HomeSection.LIVE, "EN VIVO", Color(0xFFFF6A00)),
    NavTile(HomeSection.MOVIES, "PELÍCULAS", Color(0xFFE8C547)),
    NavTile(HomeSection.SERIES, "SERIES", Color(0xFF3EC4E8)),
    NavTile(HomeSection.FAVORITES, "FAVORITOS", Color(0xFFE15B8D)),
    NavTile(HomeSection.SEARCH, "BUSCAR", Color(0xFF7CFF6B)),
)
@Composable
fun HomeScreen(
    container: AppContainer,
    onPlay: (CatalogItem, Long, List<CatalogItem>) -> Unit,
    onLogout: () -> Unit,
    autoPlayLastChannel: Boolean = true
) {
    var section by remember { mutableStateOf<HomeSection?>(null) }
    var live by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var livePreview by remember { mutableStateOf<CatalogItem?>(null) }
    var newsBanners by remember { mutableStateOf<List<com.senal.tv.data.api.BannerItem>>(emptyList()) }
    var clock by remember { mutableStateOf(nowParts()) }
    var didAutoPlay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = nowParts()
            delay(15_000)
        }
    }

    LaunchedEffect(Unit) {
        // Sync catálogo remoto (ETag) en paralelo al prefetch de home
        runCatching { container.playlistStore.syncFromServer(container.tokenStore.cachedToken) }

        // Prefetch catalog + last channel + admin banners in parallel paths
        val settings = runCatching { container.settingsStore.settings.first() }.getOrNull()
        val lastId = settings?.lastChannelId.orEmpty()

        if (lastId.isNotBlank()) {
            runCatching { container.catalogRepository.get(lastId) }.getOrNull()?.let {
                livePreview = it
            }
        }

        runCatching {
            container.catalogRepository.page(type = "live", category = null, page = 1, limit = 24)
        }.onSuccess { page ->
            live = CatalogRules.preferredLiveItems(page.resolveItems())
            if (livePreview == null) livePreview = live.firstOrNull()
        }

        if (livePreview == null) {
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
                livePreview = live.firstOrNull()
            }
        }

        // Admin news banner (server). Fail soft if endpoint missing.
        runCatching { container.api.banner() }
            .onSuccess { resp ->
                val items = resp.items.filter { it.active != false }
                newsBanners = when {
                    items.isNotEmpty() -> items
                    !resp.title.isNullOrBlank() || !resp.body.isNullOrBlank() || !resp.message.isNullOrBlank() ->
                        listOf(
                            com.senal.tv.data.api.BannerItem(
                                title = resp.title,
                                body = resp.body ?: resp.message,
                                active = resp.enabled != false
                            )
                        )
                    else -> emptyList()
                }
            }

        // Ya no saltamos a pantalla completa: EN VIVO abre la guía con categorías encima.
        didAutoPlay = true
    }

    SenalBackground(
        content = {
            if (section == null) {
                FlujoExactHome(
                    clock = clock,
                    livePreview = livePreview,
                    newsBanners = newsBanners,
                    onOpen = { section = it },
                    onPlayLive = { ch ->
                        onPlay(ch, 0L, live.ifEmpty { listOf(ch) })
                    }
                )
            } else if (section == HomeSection.LIVE) {
                // Guía a pantalla completa: categorías encima del reproductor al instante
                Box(Modifier.fillMaxSize()) {
                    LiveTvScreen(
                        container = container,
                        initialChannelId = livePreview?.resolveId(),
                        onBack = { section = null },
                        onPlay = { item, n -> onPlay(item, 0L, n) }
                    )
                }
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
    )
}

@Composable
private fun FlujoExactHome(
    clock: ClockParts,
    livePreview: CatalogItem?,
    newsBanners: List<com.senal.tv.data.api.BannerItem>,
    onOpen: (HomeSection) -> Unit,
    onPlayLive: (CatalogItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 56.dp, end = 56.dp, top = 28.dp, bottom = 28.dp)
    ) {
        HeaderBar(
            clock = clock,
            onSettings = { onOpen(HomeSection.SETTINGS) },
            onSearch = { onOpen(HomeSection.SEARCH) }
        )

        Spacer(Modifier.height(22.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LivePreviewCard(
                item = livePreview,
                onClick = { livePreview?.let(onPlayLive) },
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1.15f)
                    .zIndex(2f)
            )
            NewsBannerStack(
                items = newsBanners,
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight()
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navTiles.forEach { tile ->
                SenalNavTile(
                    label = tile.label,
                    accent = tile.accent,
                    onClick = { onOpen(tile.section) },
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
                IconFocusButton(
                    normal = R.mipmap.history_btn_n,
                    focused = R.mipmap.history_btn,
                    onClick = { onOpen(HomeSection.RECENTS) }
                )
                IconFocusButton(
                    normal = R.mipmap.fav_btn_n,
                    focused = R.mipmap.fav_btn,
                    onClick = { onOpen(HomeSection.FAVORITES) }
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    clock: ClockParts,
    onSettings: () -> Unit,
    onSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = "SEÑAL",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(36.dp)
        )
        Box(modifier = Modifier.weight(1f))
        Image(
            painter = painterResource(R.mipmap.ic_ethernet_connect),
            contentDescription = null,
            modifier = Modifier.height(22.dp)
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = clock.time,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(clock.week, color = Color.White, fontSize = 10.sp)
            Text(clock.date, color = Color.White, fontSize = 10.sp)
        }
        Spacer(Modifier.width(20.dp))
        IconFocusButton(
            normal = R.mipmap.ic_settings_n,
            focused = R.mipmap.ic_settings_h,
            onClick = onSettings,
            size = 30.dp
        )
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
            .then(rememberFlujoFocusModifier(focused, big = true))
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black,
            focusedContainerColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(Modifier.fillMaxSize()) {
                if (focused) {
                    Image(
                        painter = painterResource(R.mipmap.bg_shadow_large),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(if (focused) 7.dp else 0.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black)
                ) {
                    if (item != null) {
                        AsyncImage(
                            model = item.resolvePoster() ?: item.resolveLogo(),
                            contentDescription = item.resolveTitle(),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(39.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xCC000000), Color(0x99000000))
                                )
                            )
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(BrandOrange, RoundedCornerShape(50))
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = item?.resolveTitle() ?: "Señal en vivo",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun NewsBannerStack(
    items: List<com.senal.tv.data.api.BannerItem>,
    modifier: Modifier = Modifier
) {
    val display = if (items.isEmpty()) {
        listOf(
            com.senal.tv.data.api.BannerItem(
                title = "SEÑAL",
                body = "Noticias y avisos del administrador aparecerán aquí."
            ),
            com.senal.tv.data.api.BannerItem(
                title = "Panel admin",
                body = "Escribe mensajes desde el servidor SEÑAL."
            )
        )
    } else {
        items.take(2).let { list ->
            if (list.size == 1) list + list else list
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        display.take(2).forEach { item ->
            var focused by remember { mutableStateOf(false) }
            Surface(
                onClick = {},
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(rememberFlujoFocusModifier(focused, big = false))
                    .onFocusChanged { focused = it.isFocused },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(5.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xFF0E1520),
                    focusedContainerColor = Color(0xFF121C28)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                content = {
                    Box(Modifier.fillMaxSize()) {
                        item.art()?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = item.headline(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))
                        } ?: Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF0B3A4A), Color(0xFF071018))
                                    )
                                )
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "AVISO",
                                color = Color(0xFF3EC4E8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.headline(),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val body = item.text()
                            if (body.isNotBlank() && body != item.headline()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = body,
                                    color = Color.White.copy(0.82f),
                                    fontSize = 13.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SenalNavTile(
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    // Botones "gordos y feos": serif negro, bordes gruesos, tipografía otra familia
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(rememberFlujoFocusModifier(focused, big = true))
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = if (focused) 1f else 0.88f),
                                Color(0xFF0A0600)
                            )
                        )
                    )
                    .border(
                        width = if (focused) 5.dp else 4.dp,
                        color = if (focused) Color.White else Color.Black,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontWeight = FontWeight.Black,
                    fontSize = if (focused) 22.sp else 18.sp,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

@Composable
private fun IconFocusButton(
    normal: Int,
    focused: Int,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 30.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .then(rememberFlujoFocusModifier(isFocused, big = false))
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Image(
                painter = painterResource(if (isFocused) focused else normal),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
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
            Image(
                painter = painterResource(R.drawable.brand_logo),
                contentDescription = "SEÑAL",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(32.dp)
            )
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
                    HomeSection.LIVE -> LiveTvScreen(
                        container = container,
                        onBack = onBack,
                        onPlay = { item, n -> onPlay(item, 0L, n) }
                    )
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
    val cal = Calendar.getInstance()
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val week = SimpleDateFormat("EEE", Locale("es")).format(now).uppercase(Locale.getDefault())
    val date = SimpleDateFormat("dd/MM", Locale.getDefault()).format(now)
    return ClockParts(time, week, date)
}
