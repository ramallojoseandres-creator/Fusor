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
import com.senal.tv.ui.theme.LiveGreen
import com.senal.tv.ui.theme.LiveRed
import com.senal.tv.ui.theme.NeonBlue
import com.senal.tv.ui.theme.NeonPurple
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.util.CatalogRules
import com.senal.tv.util.DeviceUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Flujo-style bottom tiles — labels exactos; acentos SEÑAL (teal donde Flujo usaba naranja). */
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
        LiveRed,
        TileIcon.LIVE
    ),
    NavTile(
        HomeSection.SERIES, "SERIE",
        R.mipmap.bg_main_special_category_item_n,
        R.mipmap.bg_main_special_category_item_f,
        NeonBlue,
        TileIcon.SERIES
    ),
    NavTile(
        HomeSection.MOVIES, "PELÍCULA",
        R.mipmap.bg_main_vod_category_item_n,
        R.mipmap.bg_main_vod_category_item_f,
        LiveGreen,
        TileIcon.MOVIE
    ),
    NavTile(
        HomeSection.SERIES, "ANIME",
        R.mipmap.bg_main_game_category_item_n,
        R.mipmap.bg_main_game_category_item_f,
        NeonPurple,
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
    var featured by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
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

        sidePoster = movies.firstOrNull { !it.resolvePoster().isNullOrBlank() && it.resolveId() != spotlight?.resolveId() }
            ?: series.firstOrNull { !it.resolvePoster().isNullOrBlank() && it.resolveId() != spotlight?.resolveId() }

        featured = (listOfNotNull(spotlight) + live.take(3) + series.take(2) + movies.take(2))
            .distinctBy { it.resolveId() }
            .take(5)

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
                FlujoHomeHub(
                    container = container,
                    clock = clock,
                    livePreview = livePreview,
                    sidePoster = sidePoster,
                    featured = featured,
                    onOpen = { section = it },
                    onPlayItem = { item ->
                        when (item.contentType()) {
                            ContentType.LIVE -> onPlay(item, 0L, live.ifEmpty { listOf(item) })
                            else -> onPlay(item, 0L, listOf(item))
                        }
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

/** Home hub exacto Flujo: header · featured con vídeo vivo · 5 tiles. */
@Composable
private fun FlujoHomeHub(
    container: AppContainer,
    clock: ClockParts,
    livePreview: CatalogItem?,
    sidePoster: CatalogItem?,
    featured: List<CatalogItem>,
    onOpen: (HomeSection) -> Unit,
    onPlayItem: (CatalogItem) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp
        val padH = if (compact) 28.dp else 40.dp
        val padV = if (compact) 16.dp else 22.dp
        val tileH = if (DeviceUi.isTabletBuild) 108.dp else if (compact) 100.dp else 118.dp
        val featureFocus = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            if (!DeviceUi.isTabletBuild) {
                delay(160)
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

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FlujoFeatureCard(
                    container = container,
                    item = livePreview ?: featured.firstOrNull(),
                    modifier = Modifier
                        .weight(0.68f)
                        .fillMaxHeight()
                        .focusRequester(featureFocus),
                    onClick = { onOpen(HomeSection.LIVE) }
                )
                FlujoPosterCard(
                    item = sidePoster ?: featured.getOrNull(1),
                    modifier = Modifier
                        .weight(0.32f)
                        .fillMaxHeight(),
                    onClick = {
                        val item = sidePoster ?: featured.getOrNull(1)
                        if (item != null) onPlayItem(item) else onOpen(HomeSection.MOVIES)
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(tileH),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        .width(48.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RoundIconBtn(
                        normal = R.mipmap.history_btn_n,
                        focused = R.mipmap.history_btn,
                        onClick = { onOpen(HomeSection.SEARCH) }
                    )
                    RoundIconBtn(
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
                painter = painterResource(R.drawable.brand_logo),
                contentDescription = "SEÑAL",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(max = 150.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = BuildConfig.VERSION_NAME,
                color = TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            WifiGlyph(Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = clock.time,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${clock.week.replaceFirstChar { it.uppercase() }}. ${clock.date}",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(14.dp))
            RoundIconBtn(
                normal = R.mipmap.ic_settings_n,
                focused = R.mipmap.ic_settings_h,
                onClick = onSettings,
                size = 36.dp
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
    val scale by animateFloatAsState(if (focused) 1.015f else 1f, tween(160), label = "feat")
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1_200, 20_000, 800, 1_200)
                    .build()
            )
            .build()
            .apply {
                volume = 0f
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) ready = true
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
                val mediaItem = MediaItem.fromUri(url)
                if (headers.isNotEmpty()) {
                    val http = DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(8_000)
                        .setReadTimeoutMs(12_000)
                        .setDefaultRequestProperties(headers)
                    val source = if (url.contains(".m3u8", ignoreCase = true)) {
                        HlsMediaSource.Factory(http).createMediaSource(mediaItem)
                    } else {
                        DefaultMediaSourceFactory(http).createMediaSource(mediaItem)
                    }
                    player.setMediaSource(source)
                } else {
                    player.setMediaItem(mediaItem)
                }
                player.prepare()
                player.play()
            }
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0A101C),
            focusedContainerColor = Color(0xFF0A101C)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) BrandOrange else Color.White.copy(0.12f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
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
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0C2238), Color(0xFF061018), Color(0xFF102830))
                                )
                            )
                    )
                }
            }

            Image(
                painter = painterResource(R.drawable.brand_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .height(22.dp)
                    .widthIn(max = 100.dp)
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(0.85f)
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Tu ventana al mundo",
                    color = BrandOrangeHot,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
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
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, tween(160), label = "poster")

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF0A101C),
            focusedContainerColor = Color(0xFF0A101C)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) BrandOrange else Color.White.copy(0.12f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
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
                        .background(Brush.verticalGradient(listOf(Color(0xFF1A1030), Color(0xFF060A14))))
                )
                Text(
                    "PELÍCULAS",
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
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
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(150), label = "tile")

    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (focused) 2.5.dp else 0.dp,
                    color = if (focused) BrandOrangeHot else Color.Transparent,
                    shape = RoundedCornerShape(14.dp)
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
                                tile.accent.copy(if (focused) 0.55f else 0.42f),
                                tile.accent.copy(if (focused) 0.78f else 0.65f),
                                Color.Black.copy(0.35f)
                            )
                        )
                    )
            )
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TileGlyph(kind = tile.iconKind, focused = focused)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tile.label,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (focused) 15.sp else 13.sp,
                    letterSpacing = 1.2.sp
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
            .graphicsLayer {
                val s = if (isFocused) 1.1f else 1f
                scaleX = s
                scaleY = s
            }
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                if (isFocused) Modifier.border(2.dp, BrandOrange, CircleShape) else Modifier
            ),
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
