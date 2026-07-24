package com.senal.tv.ui.home

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.senal.tv.AppContainer
import com.senal.tv.R
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.HomeSection
import com.senal.tv.player.ExoPlayerManager
import com.senal.tv.ui.common.ContinueRecentsScreen
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.components.SenalBrandText
import com.senal.tv.ui.favorites.FavoritesScreen
import com.senal.tv.ui.live.LiveTvScreen
import com.senal.tv.ui.movies.MoviesScreen
import com.senal.tv.ui.search.SearchScreen
import com.senal.tv.ui.series.SeriesScreen
import com.senal.tv.ui.settings.SettingsScreen
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.BrandOrangeHot
import com.senal.tv.ui.theme.SignalCyan
import com.senal.tv.ui.theme.SignalCyanHot
import com.senal.tv.util.CatalogRules
import com.senal.tv.util.DeviceUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/** Hub SEÑAL — 4 entradas, vídeo a sangre. */
private data class NavTile(
    val section: HomeSection,
    val label: String,
    val accent: Color,
    val iconKind: TileIcon
)

private enum class TileIcon { LIVE, SERIES, MOVIE, SETTINGS }

private val hubTiles = listOf(
    NavTile(HomeSection.LIVE, "VIVO", Color(0xFF1E6FFF), TileIcon.LIVE),
    NavTile(HomeSection.MOVIES, "PELÍCULAS", Color(0xFF3D9EFF), TileIcon.MOVIE),
    NavTile(HomeSection.SERIES, "SERIES", Color(0xFF0A2A6B), TileIcon.SERIES),
    NavTile(HomeSection.SETTINGS, "AJUSTES", BrandOrange, TileIcon.SETTINGS),
)

@Composable
fun HomeScreen(
    container: AppContainer,
    onPlay: (CatalogItem, Long, List<CatalogItem>) -> Unit,
    onLogout: () -> Unit,
    autoPlayLastChannel: Boolean = false
) {
    // Hub SEÑAL primero (no auto-entrar a LIVE).
    var section by remember { mutableStateOf<HomeSection?>(null) }
    var live by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var livePreview by remember { mutableStateOf<CatalogItem?>(null) }
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

        if (autoPlayLastChannel && !didAutoPlay && livePreview != null && lastId.isNotBlank()) {
            didAutoPlay = true
            val neighbors = runCatching {
                container.playlistStore.neighborsFor(livePreview!!.resolveId())
            }.getOrDefault(live.ifEmpty { listOf(livePreview!!) })
            onPlay(livePreview!!, 0L, neighbors.ifEmpty { listOf(livePreview!!) })
        }
    }

    if (section == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            SenalHomeHub(
                container = container,
                clock = clock,
                livePreview = livePreview,
                onOpen = { section = it },
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

/** Home: vídeo a sangre + marca + Continuar viendo + 4 tiles. */
@Composable
private fun SenalHomeHub(
    container: AppContainer,
    clock: ClockParts,
    livePreview: CatalogItem?,
    onOpen: (HomeSection) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp
        val vivoFocus = remember { FocusRequester() }
        val scope = rememberCoroutineScope()
        var updateAvailable by remember {
            mutableStateOf<com.senal.tv.update.UpdateStatus.Available?>(null)
        }
        var updateBusy by remember { mutableStateOf(false) }
        var updateMsg by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            delay(160)
            runCatching { vivoFocus.requestFocus() }
            when (val st = container.appUpdater.check()) {
                is com.senal.tv.update.UpdateStatus.Available -> updateAvailable = st
                else -> Unit
            }
        }

        // —— Vídeo a sangre ——
        HomeBleedPreview(
            container = container,
            item = livePreview,
            modifier = Modifier.fillMaxSize()
        )

        // Velo legible (oscuro a la izquierda / abajo).
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(0.72f),
                        0.42f to Color.Black.copy(0.35f),
                        0.75f to Color.Black.copy(0.12f),
                        1f to Color.Transparent
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(0.35f),
                        0.45f to Color.Transparent,
                        0.78f to Color.Black.copy(0.45f),
                        1f to Color.Black.copy(0.82f)
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = if (compact) 28.dp else 44.dp,
                    end = if (compact) 28.dp else 44.dp,
                    top = if (compact) 22.dp else 28.dp,
                    bottom = if (compact) 18.dp else 24.dp
                )
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                SenalBrandText(
                    size = if (compact) 30.sp else 36.sp,
                    letterSpacing = 3.sp,
                    subtitle = "IPTV",
                    subtitleSize = 13.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val available = updateAvailable
                    if (available != null) {
                        var updFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = {
                                if (updateBusy) return@Surface
                                scope.launch {
                                    updateBusy = true
                                    updateMsg = "Descargando v${available.remoteName}…"
                                    container.appUpdater.downloadAndInstall(available) { p ->
                                        updateMsg = "Descargando… ${(p * 100).toInt()}%"
                                    }.onSuccess {
                                        updateMsg = "Confirma la instalación"
                                    }.onFailure {
                                        updateMsg = it.message ?: "Error al actualizar"
                                    }
                                    updateBusy = false
                                }
                            },
                            modifier = Modifier.onFocusChanged { updFocused = it.isFocused },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (updFocused) SignalCyanHot else SignalCyan,
                                focusedContainerColor = SignalCyanHot
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                        ) {
                            Text(
                                text = if (updateBusy) "…" else "Actualizar v${available.remoteName}",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(
                        text = clock.time,
                        color = Color.White.copy(0.9f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            updateMsg?.let { msg ->
                Text(
                    text = msg,
                    color = SignalCyanHot,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Column(Modifier.fillMaxWidth(0.65f)) {
                Text(
                    text = livePreview?.resolveTitle() ?: "ESPN Deportes",
                    color = Color.White,
                    fontSize = if (compact) 28.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                var continueFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { onOpen(HomeSection.LIVE) },
                    modifier = Modifier.onFocusChanged { continueFocused = it.isFocused },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    color = Color.White.copy(if (continueFocused) 1f else 0.88f),
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append("Continuar viendo • ")
                            }
                            withStyle(
                                SpanStyle(
                                    color = SignalCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append("OK")
                            }
                        },
                        fontSize = 16.sp,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(Modifier.height(if (compact) 30.dp else 40.dp))

            val tileH = if (DeviceUi.isTabletBuild) 108.dp else if (compact) 96.dp else 110.dp
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(tileH),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                hubTiles.forEachIndexed { index, tile ->
                    HubNavTile(
                        tile = tile,
                        onClick = { onOpen(tile.section) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (index == 0) Modifier.focusRequester(vivoFocus) else Modifier
                            )
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun HomeBleedPreview(
    container: AppContainer,
    item: CatalogItem?,
    modifier: Modifier = Modifier
) {
    var ready by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val player = remember {
        ExoPlayerManager.create(context, ExoPlayerManager.Profile.PREVIEW)
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
                ExoPlayerManager.playUrl(player, url, headers)
            }
    }

    Box(modifier.background(Color.Black)) {
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
    }
}

@Composable
private fun HubNavTile(
    tile: NavTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (focused) SignalCyan else Color.White.copy(0.10f),
            focusedContainerColor = SignalCyan
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TileGlyph(kind = tile.iconKind, focused = focused)
            Spacer(Modifier.height(8.dp))
            Text(
                text = tile.label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = if (focused) 15.sp else 14.sp,
                letterSpacing = 1.2.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TileGlyph(kind: TileIcon, focused: Boolean) {
    val glyph = if (focused) 34.dp else 30.dp
    Canvas(Modifier.size(glyph)) {
        val c = Color.White
        val stroke = Stroke(width = 2.4.dp.toPx())
        val w = this.size.width
        val h = this.size.height
        val minD = this.size.minDimension
        when (kind) {
            TileIcon.LIVE -> {
                // TV body
                drawRoundRect(
                    color = c,
                    topLeft = Offset(w * 0.12f, h * 0.22f),
                    size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.48f),
                    style = stroke,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
                // Stand
                drawLine(c, Offset(w * 0.35f, h * 0.72f), Offset(w * 0.65f, h * 0.72f), 2.4.dp.toPx())
                drawLine(c, Offset(w * 0.5f, h * 0.70f), Offset(w * 0.5f, h * 0.78f), 2.4.dp.toPx())
                // Signal waves
                drawArc(
                    color = c,
                    startAngle = -50f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(w * 0.72f, h * 0.08f),
                    size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.28f),
                    style = stroke
                )
            }
            TileIcon.MOVIE -> {
                // Clapper
                drawRoundRect(
                    color = c,
                    topLeft = Offset(w * 0.18f, h * 0.32f),
                    size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.48f),
                    style = stroke,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
                drawLine(c, Offset(w * 0.22f, h * 0.32f), Offset(w * 0.78f, h * 0.18f), 2.4.dp.toPx())
                // Play triangle
                val path = Path().apply {
                    moveTo(w * 0.42f, h * 0.42f)
                    lineTo(w * 0.68f, h * 0.55f)
                    lineTo(w * 0.42f, h * 0.68f)
                    close()
                }
                drawPath(path, c)
            }
            TileIcon.SERIES -> {
                drawRoundRect(
                    color = c,
                    topLeft = Offset(w * 0.16f, h * 0.2f),
                    size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.52f),
                    style = stroke,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                )
                val path = Path().apply {
                    moveTo(w * 0.42f, h * 0.34f)
                    lineTo(w * 0.66f, h * 0.46f)
                    lineTo(w * 0.42f, h * 0.58f)
                    close()
                }
                drawPath(path, c)
                drawLine(c, Offset(w * 0.28f, h * 0.82f), Offset(w * 0.72f, h * 0.82f), 2.4.dp.toPx())
            }
            TileIcon.SETTINGS -> {
                drawCircle(c, radius = minD * 0.34f, style = stroke)
                drawCircle(c, radius = minD * 0.12f)
            }
        }
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
