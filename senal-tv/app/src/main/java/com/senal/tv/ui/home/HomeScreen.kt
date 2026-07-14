package com.senal.tv.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class FocusColumn(
    val section: HomeSection,
    val label: String,
    val normalRes: Int,
    val focusedRes: Int
)

/** Exact FLUJO column set (5 FocusColumView). */
private val columns = listOf(
    FocusColumn(HomeSection.LIVE, "EN VIVO", R.mipmap.bg_main_live_category_item_n, R.mipmap.bg_main_live_category_item_f),
    FocusColumn(HomeSection.MOVIES, "PELÍCULAS", R.mipmap.bg_main_vod_category_item_n, R.mipmap.bg_main_vod_category_item_f),
    FocusColumn(HomeSection.SERIES, "SERIES", R.mipmap.bg_main_special_category_item_n, R.mipmap.bg_main_special_category_item_f),
    FocusColumn(HomeSection.FAVORITES, "FAVORITOS", R.mipmap.bg_main_game_category_item_n, R.mipmap.bg_main_game_category_item_f),
    FocusColumn(HomeSection.SEARCH, "BUSCAR", R.mipmap.bg_main_vod_category_item_n, R.mipmap.bg_main_vod_category_item_f)
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

    LaunchedEffect(Unit) {
        while (true) {
            clock = nowParts()
            delay(15_000)
        }
    }

    LaunchedEffect(Unit) {
        runCatching { container.catalogRepository.page("live", page = 1, limit = 20) }
            .onSuccess { live = it.resolveItems() }
        runCatching { container.catalogRepository.page("movie", page = 1, limit = 12) }
            .onSuccess { movies = it.resolveItems() }
    }

    LaunchedEffect(movies) {
        if (movies.size < 2) return@LaunchedEffect
        while (true) {
            delay(3500)
            bannerIndex = (bannerIndex + 1) % movies.size
        }
    }

    SenalBackground(
        content = {
            if (section == null) {
                FlujoExactHome(
                    clock = clock,
                    livePreview = live.firstOrNull(),
                    banners = movies.ifEmpty { live }.let { list ->
                        if (list.isEmpty()) emptyList()
                        else listOf(
                            list[bannerIndex % list.size],
                            list[(bannerIndex + 1) % list.size]
                        )
                    },
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
    )
}

@Composable
private fun FlujoExactHome(
    clock: ClockParts,
    livePreview: CatalogItem?,
    banners: List<CatalogItem>,
    onOpen: (HomeSection) -> Unit,
    onPlayLive: (CatalogItem) -> Unit
) {
    // Margins from FLUJO dimens: horizontal 89-95, top 35
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 89.dp, end = 95.dp, top = 35.dp, bottom = 24.dp)
    ) {
        HeaderBar(
            clock = clock,
            onSettings = { onOpen(HomeSection.SETTINGS) },
            onSearch = { onOpen(HomeSection.SEARCH) }
        )

        Spacer(Modifier.height(20.dp))

        // Top content row height 303dp (FLUJO)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(303.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live player window 542dp + focus zoom shadow
            LivePreviewCard(
                item = livePreview,
                onClick = { livePreview?.let(onPlayLive) },
                modifier = Modifier
                    .width(542.dp)
                    .fillMaxHeight()
                    .zIndex(2f)
            )

            // Banner column fills remaining
            BannerStack(
                items = banners,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        Spacer(Modifier.height(16.dp))

        // FocusColumView row height 100dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            columns.forEachIndexed { index, col ->
                FocusColumnExact(
                    label = col.label,
                    normalRes = col.normalRes,
                    focusedRes = col.focusedRes,
                    onClick = { onOpen(col.section) },
                    modifier = Modifier
                        .width(150.dp)
                        .fillMaxHeight()
                        .then(if (index > 0) Modifier.offset(x = (-3).dp) else Modifier)
                        .zIndex((index + 1).toFloat())
                )
            }
            Box(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.width(30.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
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
            painter = painterResource(R.mipmap.logo_new),
            contentDescription = "SEÑAL",
            modifier = Modifier.height(40.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = "  v1.0",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
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
private fun BannerStack(items: List<CatalogItem>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(2) { i ->
            val item = items.getOrNull(i)
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
                    containerColor = Color(0xFF121218),
                    focusedContainerColor = Color(0xFF121218)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                content = {
                    Box(Modifier.fillMaxSize()) {
                        if (item != null) {
                            AsyncImage(
                                model = item.resolvePoster() ?: item.resolveLogo(),
                                contentDescription = item.resolveTitle(),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.25f)))
                            Text(
                                text = item.resolveTitle(),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp),
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
private fun FocusColumnExact(
    label: String,
    normalRes: Int,
    focusedRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(rememberFlujoFocusModifier(focused, big = true))
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(if (focused) focusedRes else normalRes),
                    contentDescription = label,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
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
                painter = painterResource(R.mipmap.logo_new),
                contentDescription = null,
                modifier = Modifier.height(36.dp)
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
    val cal = Calendar.getInstance()
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val week = SimpleDateFormat("EEE", Locale("es")).format(now).uppercase(Locale.getDefault())
    val date = SimpleDateFormat("dd/MM", Locale.getDefault()).format(now)
    return ClockParts(time, week, date)
}
