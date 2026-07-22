package com.senal.tv.ui.home

import android.app.Activity

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.senal.tv.AppContainer
import com.senal.tv.BuildConfig
import com.senal.tv.R
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.ContentType
import com.senal.tv.data.model.HomeSection
import com.senal.tv.player.ExoPlayerManager
import com.senal.tv.ui.common.ContinueRecentsScreen
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.favorites.FavoritesScreen
import com.senal.tv.ui.focus.FocusTurquoise
import com.senal.tv.ui.focus.senalFocusable
import com.senal.tv.ui.live.LiveTvScreen
import com.senal.tv.ui.movies.MoviesScreen
import com.senal.tv.ui.search.SearchScreen
import com.senal.tv.ui.series.SeriesScreen
import com.senal.tv.ui.settings.SettingsScreen
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.BrandOrangeHot
import com.senal.tv.ui.theme.LiveGreen
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.util.CatalogRules
import com.senal.tv.util.DeviceUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Flujo-style bottom tiles — estética unificada azul/negro; foco turquesa. */
private data class NavTile(
    val section: HomeSection,
    val label: String,
    val normalRes: Int,
    val focusedRes: Int,
    val accent: Color,
    val iconKind: TileIcon
)

private enum class TileIcon { LIVE, SERIES, MOVIE, ANIME, SPECIAL }

private val flujoTiles = listOf(
    NavTile(
        HomeSection.LIVE, "VIVO",
        R.mipmap.bg_main_live_category_item_n,
        R.mipmap.bg_main_live_category_item_f,
        Color(0xFF1E6FFF),
        TileIcon.LIVE
    ),
    NavTile(
        HomeSection.SERIES, "SERIE",
        R.mipmap.bg_main_special_category_item_n,
        R.mipmap.bg_main_special_category_item_f,
        Color(0xFF0A2A6B),
        TileIcon.SERIES
    ),
    NavTile(
        HomeSection.MOVIES, "PELÍCULA",
        R.mipmap.bg_main_vod_category_item_n,
        R.mipmap.bg_main_vod_category_item_f,
        Color(0xFF3D9EFF),
        TileIcon.MOVIE
    ),
    NavTile(
        HomeSection.SERIES, "ANIME",
        R.mipmap.bg_main_game_category_item_n,
        R.mipmap.bg_main_game_category_item_f,
        Color(0xFF1A3A7A),
        TileIcon.ANIME
    ),
    NavTile(
        HomeSection.FAVORITES, "ESPECIAL",
        R.mipmap.bg_main_special_category_item_n,
        R.mipmap.bg_main_special_category_item_f,
        BrandOrange,
        TileIcon.SPECIAL
    ),
)

@Composable
fun HomeScreen(
    container: AppContainer,
    onPlay: (CatalogItem, Long, List<CatalogItem>) -> Unit,
    onLogout: () -> Unit,
    autoPlayLastChannel: Boolean = false
) {
    // Hub Flujo primero (no auto-entrar a LIVE).
    var section by remember { mutableStateOf<HomeSection?>(null) }
    var live by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var livePreview by remember { mutableStateOf<CatalogItem?>(null) }
    var spotlight by remember { mutableStateOf<CatalogItem?>(null) }
    var sidePoster by remember { mutableStateOf<CatalogItem?>(null) }
    var clock by remember { mutableStateOf(nowParts()) }
    var didAutoPlay by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity
    var lastBackPressAt by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = section != null) {
        section = null
    }
    BackHandler(enabled = section == null) {
        val now = System.currentTimeMillis()
        if (now - lastBackPressAt < 2_000L) {
            activity?.finish()
        } else {
            lastBackPressAt = now
            Toast.makeText(context, "Pulsa atrás otra vez para salir", Toast.LENGTH_SHORT).show()
        }
    }

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

        spotlight = livePreview
            ?: series.firstOrNull { !it.resolvePoster().isNullOrBlank() }
            ?: movies.firstOrNull { !it.resolvePoster().isNullOrBlank() }
            ?: series.firstOrNull()
            ?: movies.firstOrNull()

        fun isRealPoster(item: CatalogItem): Boolean {
            val p = item.resolvePoster().orEmpty()
            if (p.isBlank()) return false
            // Evitar logos de canal (suelen ser pequeños / mismos que logo).
            if (p == item.resolveLogo().orEmpty() && item.contentType() == ContentType.LIVE) return false
            return p.startsWith("http")
        }
        sidePoster = movies.firstOrNull(::isRealPoster)
            ?: series.firstOrNull(::isRealPoster)
            ?: movies.firstOrNull { !it.resolvePoster().isNullOrBlank() }
            ?: series.firstOrNull { !it.resolvePoster().isNullOrBlank() }

        if (autoPlayLastChannel && !didAutoPlay && livePreview != null && lastId.isNotBlank()) {
            didAutoPlay = true
            val neighbors = runCatching {
                container.playlistStore.neighborsFor(livePreview!!.resolveId())
            }.getOrDefault(live.ifEmpty { listOf(livePreview!!) })
            onPlay(livePreview!!, 0L, neighbors.ifEmpty { listOf(livePreview!!) })
        }
    }

    if (section == null) {
        // Fondo plano estilo Flujo (sin grilla ATV).
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF000000))
        ) {
            FlujoHomeHub(
                container = container,
                clock = clock,
                livePreview = livePreview,
                sidePoster = sidePoster,
                onOpen = { section = it },
                onPlayItem = { item ->
                    when (item.contentType()) {
                        ContentType.LIVE -> onPlay(item, 0L, live.ifEmpty { listOf(item) })
                        else -> onPlay(item, 0L, listOf(item))
                    }
                }
            )
        }
    } else {
        SenalBackground {
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
}

/** Home hub exacto Flujo: header · featured vídeo · póster · 5 tiles. */
@Composable
private fun FlujoHomeHub(
    container: AppContainer,
    clock: ClockParts,
    livePreview: CatalogItem?,
    sidePoster: CatalogItem?,
    onOpen: (HomeSection) -> Unit,
    onPlayItem: (CatalogItem) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp
        val padH = if (compact) 36.dp else 48.dp
        val padV = if (compact) 18.dp else 24.dp
        val tileH = if (DeviceUi.isTabletBuild) 112.dp else if (compact) 104.dp else 120.dp
        val featureFocus = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            if (!DeviceUi.isTabletBuild) {
                delay(200)
                runCatching { featureFocus.requestFocus() }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = padH, end = padH, top = padV, bottom = padV)
        ) {
            FlujoTopBar(
                clock = clock,
                onSettings = { onOpen(HomeSection.SETTINGS) }
            )

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FlujoFeatureCard(
                    container = container,
                    item = livePreview,
                    modifier = Modifier
                        .weight(0.72f)
                        .fillMaxHeight()
                        .focusRequester(featureFocus),
                    onClick = { onOpen(HomeSection.LIVE) }
                )
                FlujoPosterCard(
                    item = sidePoster,
                    modifier = Modifier
                        .weight(0.28f)
                        .fillMaxHeight(),
                    onClick = {
                        if (sidePoster != null) onPlayItem(sidePoster) else onOpen(HomeSection.MOVIES)
                    }
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(tileH),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                flujoTiles.forEach { tile ->
                    FlujoNavTile(
                        tile = tile,
                        onClick = { onOpen(tile.section) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Column(
                    Modifier
                        .width(52.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RoundIconBtn(
                        normal = R.mipmap.history_btn_n,
                        focused = R.mipmap.history_btn,
                        onClick = { onOpen(HomeSection.SEARCH) },
                        size = 44.dp
                    )
                    RoundIconBtn(
                        normal = R.mipmap.fav_btn_n,
                        focused = R.mipmap.fav_btn,
                        onClick = { onOpen(HomeSection.FAVORITES) },
                        size = 44.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun FlujoTopBar(
    clock: ClockParts,
    onSettings: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.brand_logo_pill),
                contentDescription = "SEÑAL",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(42.dp)
                    .widthIn(max = 170.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = BuildConfig.VERSION_NAME,
                color = Color.White.copy(0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            WifiGlyph(Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(
                text = clock.time,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = clock.week.replaceFirstChar { it.uppercase() } + ".",
                    color = Color.White.copy(0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = clock.date,
                    color = Color.White.copy(0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(16.dp))
            RoundIconBtn(
                normal = R.mipmap.ic_settings_n,
                focused = R.mipmap.ic_settings_h,
                onClick = onSettings,
                size = 38.dp
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun FlujoFeatureCard(
    container: AppContainer,
    item: CatalogItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var bitrate by remember { mutableStateOf("—") }
    val context = LocalContext.current

    val player = remember {
        ExoPlayerManager.create(context, ExoPlayerManager.Profile.PREVIEW)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    ready = true
                    val br = player.videoFormat?.bitrate ?: player.audioFormat?.bitrate ?: 0
                    bitrate = ExoPlayerManager.formatBitrate(if (br > 0) br else null)
                        .let { if (it == "—") "1 Mb/s" else it }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(item?.resolveId()) {
        ready = false
        val channel = item ?: return@LaunchedEffect
        runCatching { container.catalogRepository.playback(channel.resolveId()) }
            .onSuccess { playback ->
                val url = playback.resolveUrl() ?: channel.resolveStreamUrl()
                if (url.isNullOrBlank()) return@onSuccess
                val headers = playback.headers.orEmpty().toMutableMap()
                channel.userAgent?.takeIf { it.isNotBlank() }?.let {
                    headers.putIfAbsent("User-Agent", it)
                }
                ExoPlayerManager.playUrl(player, url, headers)
            }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .senalFocusable(focused = focused, scaleFocused = 1.06f, cornerRadius = 10.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black,
            focusedContainerColor = Color.Black
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(10.dp)
            ),
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.08f)),
                shape = RoundedCornerShape(10.dp)
            )
        )
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        this.player = player
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )

            if (!ready) {
                val art = item?.resolvePoster() ?: item?.resolveLogo()
                if (!art.isNullOrBlank()) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Gradiente suave solo abajo (como Flujo).
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.62f to Color.Transparent,
                            1f to Color.Black.copy(0.72f)
                        )
                    )
            )

            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(LiveGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item?.resolveTitle() ?: "SEÑAL · En vivo",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = bitrate,
                    color = Color.White.copy(0.85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun FlujoPosterCard(
    item: CatalogItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .senalFocusable(focused = focused, scaleFocused = 1.06f, cornerRadius = 10.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1A1A1E),
            focusedContainerColor = Color(0xFF1A1A1E)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent),
                shape = RoundedCornerShape(10.dp)
            ),
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.08f)),
                shape = RoundedCornerShape(10.dp)
            )
        )
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))) {
            val art = item?.resolvePoster()
            if (!art.isNullOrBlank()) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0xFF2A2038), Color(0xFF121018))))
                )
                Text(
                    "PELÍCULA",
                    color = Color.White.copy(0.7f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            // Dots carrusel estilo Flujo
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(if (i == 0) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (i == 0) BrandOrange else Color.White.copy(0.35f))
                    )
                }
            }
        }
    }
}

@Composable
private fun FlujoNavTile(
    tile: NavTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .senalFocusable(focused = focused, scaleFocused = 1.06f, cornerRadius = 16.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            tile.accent.copy(alpha = if (focused) 0.95f else 0.88f),
                            tile.accent.copy(alpha = if (focused) 0.75f else 0.62f),
                            Color(0xFF101018).copy(alpha = 0.55f)
                        )
                    )
                )
                .border(
                    width = if (focused) 2.5.dp else 0.dp,
                    color = if (focused) Color.White.copy(0.85f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            // Burbujas sutiles estilo Flujo
            Canvas(Modifier.fillMaxSize()) {
                val c = Color.White.copy(alpha = 0.08f)
                drawCircle(c, radius = size.minDimension * 0.22f, center = Offset(size.width * 0.2f, size.height * 0.3f))
                drawCircle(c, radius = size.minDimension * 0.14f, center = Offset(size.width * 0.75f, size.height * 0.25f))
                drawCircle(c, radius = size.minDimension * 0.18f, center = Offset(size.width * 0.55f, size.height * 0.7f))
            }
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TileGlyph(kind = tile.iconKind, focused = focused)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = tile.label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (focused) 14.sp else 13.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun TileGlyph(kind: TileIcon, focused: Boolean) {
    val glyph = if (focused) 36.dp else 32.dp
    Box(
        Modifier
            .size(glyph + 10.dp)
            .clip(CircleShape)
            .background(Color.White.copy(0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(glyph)) {
            val c = Color.White
            val stroke = Stroke(width = 2.5.dp.toPx())
            val w = this.size.width
            val h = this.size.height
            val minD = this.size.minDimension
            when (kind) {
                TileIcon.LIVE -> {
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(w * 0.12f, h * 0.28f),
                        size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.44f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                    val path = Path().apply {
                        moveTo(w * 0.68f, h * 0.35f)
                        lineTo(w * 0.92f, h * 0.22f)
                        lineTo(w * 0.92f, h * 0.78f)
                        lineTo(w * 0.68f, h * 0.65f)
                        close()
                    }
                    drawPath(path, c)
                }
                TileIcon.SERIES -> {
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(w * 0.18f, h * 0.22f),
                        size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.48f),
                        style = stroke,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                    )
                    drawLine(
                        c,
                        Offset(w * 0.3f, h * 0.78f),
                        Offset(w * 0.7f, h * 0.78f),
                        strokeWidth = 2.5.dp.toPx()
                    )
                }
                TileIcon.MOVIE -> {
                    drawCircle(c, radius = minD * 0.32f, style = stroke)
                    drawCircle(c, radius = minD * 0.12f)
                }
                TileIcon.ANIME -> {
                    drawCircle(c, radius = minD * 0.28f, center = Offset(w * 0.5f, h * 0.55f), style = stroke)
                    drawCircle(c, radius = minD * 0.08f, center = Offset(w * 0.38f, h * 0.5f))
                    drawCircle(c, radius = minD * 0.08f, center = Offset(w * 0.62f, h * 0.5f))
                    drawCircle(c, radius = minD * 0.14f, center = Offset(w * 0.28f, h * 0.28f), style = stroke)
                    drawCircle(c, radius = minD * 0.14f, center = Offset(w * 0.72f, h * 0.28f), style = stroke)
                }
                TileIcon.SPECIAL -> {
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(w * 0.22f, h * 0.2f),
                        size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.62f),
                        style = stroke,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                    )
                    val cx = w * 0.5f
                    val cy = h * 0.48f
                    val r = minD * 0.14f
                    val star = Path()
                    for (i in 0 until 5) {
                        val a = Math.toRadians((-90 + i * 144).toDouble())
                        val x = cx + (r * kotlin.math.cos(a)).toFloat()
                        val y = cy + (r * kotlin.math.sin(a)).toFloat()
                        if (i == 0) star.moveTo(x, y) else star.lineTo(x, y)
                    }
                    star.close()
                    drawPath(star, c)
                }
            }
        }
    }
}

@Composable
private fun WifiGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White
        val cx = size.width / 2f
        val cy = size.height * 0.78f
        drawCircle(c, radius = size.minDimension * 0.08f, center = Offset(cx, cy))
        for (i in 1..3) {
            val r = size.minDimension * (0.18f + i * 0.18f)
            drawArc(
                color = c,
                startAngle = 220f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }
    }
}

@Composable
private fun RoundIconBtn(
    normal: Int,
    focused: Int,
    onClick: () -> Unit,
    size: Dp = 40.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .senalFocusable(
                focused = isFocused,
                scaleFocused = 1.1f,
                cornerRadius = size / 2,
                drawGlow = true,
            )
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Image(
            painter = painterResource(if (isFocused) focused else normal),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
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
                    painter = painterResource(R.drawable.brand_logo_pill),
                    contentDescription = "SEÑAL",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(34.dp)
                        .widthIn(max = 150.dp)
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
                        HomeSection.LIVE -> LiveTvScreen(
                            container = container,
                            onBack = onBack
                        ) { item, n -> onPlay(item, 0L, n) }
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
    val week = SimpleDateFormat("EEE", Locale("es")).format(now)
    val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now)
    return ClockParts(time, week, date)
}
