package com.senal.tv.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.senal.tv.AppContainer
import com.senal.tv.R
import com.senal.tv.data.api.BannerItem
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.ContentType
import com.senal.tv.data.model.HomeSection
import com.senal.tv.ui.common.ContinueRecentsScreen
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.favorites.FavoritesScreen
import com.senal.tv.ui.live.LiveTvScreen
import com.senal.tv.ui.movies.MoviesScreen
import com.senal.tv.ui.search.SearchScreen
import com.senal.tv.ui.series.SeriesScreen
import com.senal.tv.ui.settings.SettingsScreen
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.BrandOrangeHot
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.util.CatalogRules
import com.senal.tv.util.DeviceUi
import com.senal.tv.util.rememberTabletLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private data class NavTile(
    val section: HomeSection,
    val label: String,
    val subtitle: String,
    val normalRes: Int,
    val focusedRes: Int,
    val accent: Color
)

private val navTiles = listOf(
    NavTile(
        HomeSection.LIVE, "EN VIVO", "TV en directo",
        R.mipmap.bg_main_live_category_item_n,
        R.mipmap.bg_main_live_category_item_f,
        Color(0xFF1A9BC4)
    ),
    NavTile(
        HomeSection.MOVIES, "PELÍCULAS", "Catálogo VOD",
        R.mipmap.bg_main_vod_category_item_n,
        R.mipmap.bg_main_vod_category_item_f,
        Color(0xFF2AA8C0)
    ),
    NavTile(
        HomeSection.SERIES, "SERIES", "Temporadas",
        R.mipmap.bg_main_special_category_item_n,
        R.mipmap.bg_main_special_category_item_f,
        Color(0xFF4DB8A0)
    ),
    NavTile(
        HomeSection.FAVORITES, "FAVORITOS", "Mis favoritos",
        R.mipmap.bg_main_game_category_item_n,
        R.mipmap.bg_main_game_category_item_f,
        Color(0xFF7EB8C8)
    ),
    NavTile(
        HomeSection.SEARCH, "BUSCAR", "Búsqueda universal",
        R.mipmap.bg_main_special_category_item_n,
        R.mipmap.bg_main_special_category_item_f,
        Color(0xFF3EC4E8)
    ),
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
    var spotlight by remember { mutableStateOf<CatalogItem?>(null) }
    var newReleases by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var newsBanners by remember { mutableStateOf<List<BannerItem>>(emptyList()) }
    var clock by remember { mutableStateOf(nowParts()) }
    var didAutoPlay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = nowParts()
            delay(15_000)
        }
    }

    LaunchedEffect(Unit) {
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

        val movies = runCatching {
            container.catalogRepository.page(type = "movie", category = null, page = 1, limit = 12)
                .resolveItems()
        }.getOrDefault(emptyList())
        val series = runCatching {
            container.catalogRepository.page(type = "series", category = null, page = 1, limit = 12)
                .resolveItems()
        }.getOrDefault(emptyList())

        spotlight = series.firstOrNull { !it.resolvePoster().isNullOrBlank() }
            ?: movies.firstOrNull { !it.resolvePoster().isNullOrBlank() }
            ?: series.firstOrNull()
            ?: movies.firstOrNull()
            ?: livePreview

        newReleases = (series + movies)
            .filter { !it.resolvePoster().isNullOrBlank() }
            .distinctBy { it.resolveId() }
            .take(4)

        runCatching { container.api.banner() }
            .onSuccess { resp ->
                val items = resp.items.filter { it.active != false }
                newsBanners = when {
                    items.isNotEmpty() -> items
                    !resp.title.isNullOrBlank() || !resp.body.isNullOrBlank() || !resp.message.isNullOrBlank() ->
                        listOf(
                            BannerItem(
                                title = resp.title,
                                body = resp.body ?: resp.message,
                                active = resp.enabled != false
                            )
                        )
                    else -> emptyList()
                }
            }

        if (autoPlayLastChannel && !didAutoPlay && livePreview != null && lastId.isNotBlank()) {
            didAutoPlay = true
            val neighbors = runCatching {
                container.playlistStore.neighborsFor(livePreview!!.resolveId())
            }.getOrDefault(live.ifEmpty { listOf(livePreview!!) })
            onPlay(livePreview!!, 0L, neighbors.ifEmpty { listOf(livePreview!!) })
        }
    }

    SenalBackground(
        content = {
            if (section == null) {
                SpotlightHome(
                    clock = clock,
                    livePreview = livePreview,
                    spotlight = spotlight,
                    newsBanners = newsBanners,
                    newReleases = newReleases,
                    onOpen = { section = it },
                    onPlaySpotlight = { item ->
                        when (item.contentType()) {
                            ContentType.LIVE -> onPlay(item, 0L, live.ifEmpty { listOf(item) })
                            else -> onPlay(item, 0L, listOf(item))
                        }
                    },
                    onPlayLive = { ch ->
                        onPlay(ch, 0L, live.ifEmpty { listOf(ch) })
                    }
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(if (section == HomeSection.LIVE) 0.dp else 20.dp)
                ) {
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
private fun SpotlightHome(
    clock: ClockParts,
    livePreview: CatalogItem?,
    spotlight: CatalogItem?,
    newsBanners: List<BannerItem>,
    newReleases: List<CatalogItem>,
    onOpen: (HomeSection) -> Unit,
    onPlaySpotlight: (CatalogItem) -> Unit,
    onPlayLive: (CatalogItem) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = rememberTabletLayout()
        val compact = maxWidth < 900.dp && !layout.isTablet
        val padH = when {
            layout.isTablet -> layout.padH
            compact -> 28.dp
            else -> 48.dp
        }
        val padV = when {
            layout.isTablet -> layout.padV
            compact -> 18.dp
            else -> 24.dp
        }
        val tileH = when {
            layout.isTablet -> layout.tileHeight
            compact -> 96.dp
            else -> 112.dp
        }
        val sideW = when {
            layout.isTablet -> layout.sideWeight
            compact -> 0.38f
            else -> 0.34f
        }
        val liveFocus = remember { FocusRequester() }

        // En TV pedimos foco D-pad; en tablet SM-X200 el usuario toca.
        LaunchedEffect(Unit) {
            if (!DeviceUi.isTabletBuild) {
                delay(120)
                runCatching { liveFocus.requestFocus() }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = padH, end = padH, top = padV, bottom = padV)
        ) {
            HeaderBar(
                clock = clock,
                onSettings = { onOpen(HomeSection.SETTINGS) }
            )

            Spacer(Modifier.height(if (compact) 14.dp else 18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f - sideW)
                        .fillMaxHeight()
                        .zIndex(2f)
                ) {
                    SpotlightHero(
                        item = spotlight,
                        onClick = { spotlight?.let(onPlaySpotlight) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    LiveNowChip(
                        item = livePreview,
                        onClick = { livePreview?.let(onPlayLive) }
                    )
                }

                RightRail(
                    newsBanners = newsBanners,
                    newReleases = newReleases,
                    onOpenCatalog = { onOpen(HomeSection.MOVIES) },
                    onPlayItem = onPlaySpotlight,
                    modifier = Modifier
                        .weight(sideW)
                        .fillMaxHeight()
                )
            }

            Spacer(Modifier.height(if (compact) 14.dp else 18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(tileH),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navTiles.forEachIndexed { index, tile ->
                    SpotlightNavTile(
                        tile = tile,
                        showVisualizer = tile.section == HomeSection.LIVE,
                        onClick = { onOpen(tile.section) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (index == 0) Modifier.focusRequester(liveFocus) else Modifier
                            )
                    )
                }
                Column(
                    modifier = Modifier.width(if (compact) 40.dp else 44.dp),
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
}

@Composable
private fun HeaderBar(
    clock: ClockParts,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = "SEÑAL",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(38.dp)
                .widthIn(max = 160.dp)
        )
        Box(modifier = Modifier.weight(1f))
        Image(
            painter = painterResource(R.mipmap.ic_ethernet_connect),
            contentDescription = null,
            modifier = Modifier.height(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = clock.time,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = clock.week,
                color = Color.White.copy(0.9f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = clock.date,
                color = Color.White.copy(0.75f),
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.width(18.dp))
        IconFocusButton(
            normal = R.mipmap.ic_settings_n,
            focused = R.mipmap.ic_settings_h,
            onClick = onSettings,
            size = 32.dp
        )
    }
}

@Composable
private fun SpotlightHero(
    item: CatalogItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val pulse by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(220),
        label = "heroPulse"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = 1f + 0.02f * pulse
                scaleY = 1f + 0.02f * pulse
            }
            .drawBehind {
                if (pulse > 0f) {
                    drawRoundRect(
                        color = BrandOrangeHot.copy(alpha = 0.28f * pulse),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                        size = Size(size.width + 10.dp.toPx(), size.height + 10.dp.toPx()),
                        topLeft = Offset(-5.dp.toPx(), -5.dp.toPx())
                    )
                }
            }
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF071018),
            focusedContainerColor = Color(0xFF071018)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (focused) 2.5.dp else 1.dp,
                        color = if (focused) BrandOrangeHot else Color.White.copy(0.12f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                val art = item?.resolvePoster() ?: item?.resolveLogo()
                if (!art.isNullOrBlank()) {
                    AsyncImage(
                        model = art,
                        contentDescription = item?.resolveTitle(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0A2A38), Color(0xFF050A10))
                                )
                            )
                    )
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(0.15f),
                                0.45f to Color.Black.copy(0.35f),
                                1f to Color.Black.copy(0.88f)
                            )
                        )
                )

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .fillMaxWidth(0.92f)
                ) {
                    Text(
                        text = "SEÑAL Spotlight",
                        color = BrandOrangeHot,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item?.resolveTitle()?.let { "“$it”" } ?: "Tu señal, al instante",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 30.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpotlightTag(tagForType(item))
                        item?.year?.let { SpotlightTag("$it") }
                        item?.resolveGenre()?.takeIf { it.isNotBlank() }?.split(",")?.firstOrNull()
                            ?.trim()?.take(12)?.let { SpotlightTag(it) }
                        if (item?.contentType() == ContentType.SERIES) SpotlightTag("Series")
                        if (item?.contentType() == ContentType.MOVIE) SpotlightTag("Film")
                    }
                }
            }
        }
    )
}

@Composable
private fun SpotlightTag(label: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(0.14f))
            .border(1.dp, Color.White.copy(0.22f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(0.92f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )
    }
}

private fun tagForType(item: CatalogItem?): String = when (item?.contentType()) {
    ContentType.LIVE -> "LIVE"
    ContentType.MOVIE -> "4K"
    ContentType.SERIES, ContentType.EPISODE -> "HD"
    null -> "SEÑAL"
}

@Composable
private fun LiveNowChip(
    item: CatalogItem?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(0.06f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (focused) BrandOrangeHot else BrandOrange)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "✓",
                    color = BrandOrangeHot,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item?.resolveTitle() ?: "Señal en vivo",
                    color = Color.White.copy(if (focused) 1f else 0.88f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}

@Composable
private fun RightRail(
    newsBanners: List<BannerItem>,
    newReleases: List<CatalogItem>,
    onOpenCatalog: () -> Unit,
    onPlayItem: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val news = newsBanners.firstOrNull() ?: BannerItem(
        title = "SEÑAL News",
        body = "Avisos del administrador aparecen aquí."
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NewsPickCard(
            item = news,
            modifier = Modifier
                .weight(0.42f)
                .fillMaxWidth()
        )
        SystemStatusCard(
            modifier = Modifier
                .heightIn(min = 56.dp, max = 72.dp)
                .fillMaxWidth()
        )
        NewReleasesCard(
            items = newReleases,
            onOpen = onOpenCatalog,
            onPlayItem = onPlayItem,
            modifier = Modifier
                .weight(0.58f)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun NewsPickCard(
    item: BannerItem,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = {},
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0B141C),
            focusedContainerColor = Color(0xFF12202C)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (focused) BrandOrangeHot.copy(0.7f) else Color.White.copy(0.1f),
                        RoundedCornerShape(10.dp)
                    )
            ) {
                item.art()?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = item.headline(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)))
                } ?: Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF123848), Color(0xFF071018))
                            )
                        )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "DESTACADOS",
                        color = BrandOrangeHot,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.headline(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val body = item.text()
                    if (body.isNotBlank() && body != item.headline()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = body,
                            color = Color.White.copy(0.8f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SystemStatusCard(modifier: Modifier = Modifier) {
    val shimmer = rememberInfiniteTransition(label = "status")
    val phase by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0B141C))
            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Estado",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "SEÑAL · lista en el dispositivo",
            color = TextMuted,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val colors = listOf(
                Color(0xFF1A9BC4),
                Color(0xFF3EC4E8),
                Color(0xFF4DB8A0),
                Color(0xFF7EB8C8),
                Color(0xFF2AA8C0)
            )
            val seg = size.width / colors.size
            colors.forEachIndexed { i, c ->
                val boost = 0.55f + 0.45f * ((sin((phase + i * 0.18f) * Math.PI * 2).toFloat() + 1f) / 2f)
                drawRoundRect(
                    color = c.copy(alpha = boost),
                    topLeft = Offset(i * seg + 1.dp.toPx(), 0f),
                    size = Size(seg - 2.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(3.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun NewReleasesCard(
    items: List<CatalogItem>,
    onOpen: () -> Unit,
    onPlayItem: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0B141C),
            focusedContainerColor = Color(0xFF12202C)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        if (focused) BrandOrangeHot.copy(0.7f) else Color.White.copy(0.1f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(10.dp)
            ) {
                Text(
                    text = "Novedades",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.take(3).forEach { item ->
                        ReleaseRow(item = item, onClick = { onPlayItem(item) })
                    }
                    if (items.isEmpty()) {
                        Text(
                            text = "El catálogo se llenará al sincronizar.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ReleaseRow(
    item: CatalogItem,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (focused) Color.White.copy(0.08f) else Color.Transparent,
            focusedContainerColor = Color.White.copy(0.1f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = item.resolvePoster() ?: item.resolveLogo(),
                    contentDescription = item.resolveTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 34.dp, height = 44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF152028))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.resolveTitle(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (item.contentType()) {
                            ContentType.SERIES -> "Serie"
                            ContentType.MOVIE -> "Película"
                            else -> "Contenido"
                        },
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    )
}

@Composable
private fun SpotlightNavTile(
    tile: NavTile,
    showVisualizer: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = tween(180),
        label = "tileScale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (focused) {
                    drawRoundRect(
                        color = tile.accent.copy(alpha = 0.4f),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                        topLeft = Offset(-4.dp.toPx(), -4.dp.toPx())
                    )
                }
            }
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        width = if (focused) 2.5.dp else 1.dp,
                        color = if (focused) BrandOrangeHot else Color.White.copy(0.14f),
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                Image(
                    painter = painterResource(if (focused) tile.focusedRes else tile.normalRes),
                    contentDescription = tile.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(if (focused) 0.15f else 0.35f),
                                    Color.Black.copy(if (focused) 0.45f else 0.62f)
                                )
                            )
                        )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = tile.label,
                        color = Color.White,
                        fontSize = if (focused) 16.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tile.subtitle,
                        color = Color.White.copy(0.72f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showVisualizer && focused) {
                        Spacer(Modifier.height(6.dp))
                        AudioBars(
                            color = BrandOrangeHot,
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(12.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun AudioBars(
    color: Color,
    modifier: Modifier = Modifier
) {
    val t = rememberInfiniteTransition(label = "bars")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "barPhase"
    )
    Canvas(modifier) {
        val bars = 7
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val h = size.height * (0.25f + 0.75f * ((sin(phase + i * 0.7f) + 1f) / 2f))
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

@Composable
private fun IconFocusButton(
    normal: Int,
    focused: Int,
    onClick: () -> Unit,
    size: Dp = 30.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val s = if (isFocused) 1.08f else 1f
                scaleX = s
                scaleY = s
            }
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
        if (section != HomeSection.LIVE) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.brand_logo),
                    contentDescription = "SEÑAL",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(32.dp)
                        .widthIn(max = 140.dp)
                )
                FocusableButton(label = "INICIO", onClick = onBack, primary = true)
            }
            Spacer(Modifier.height(12.dp))
        }
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(140))
            },
            modifier = Modifier.weight(1f),
            label = "section",
            content = { current ->
                Box(Modifier.fillMaxSize()) {
                    when (current) {
                        HomeSection.LIVE -> {
                            LiveTvScreen(container) { item, n -> onPlay(item, 0L, n) }
                            FocusableButton(
                                label = "INICIO",
                                onClick = onBack,
                                primary = true,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            )
                        }
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
            }
        )
    }
}

private data class ClockParts(val time: String, val week: String, val date: String)

private fun nowParts(): ClockParts {
    val now = Date()
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val week = SimpleDateFormat("EEE", Locale("es")).format(now).lowercase(Locale.getDefault())
    val date = SimpleDateFormat("dd/MM", Locale.getDefault()).format(now)
    return ClockParts(time, week, date)
}
