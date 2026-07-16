package com.senal.tv.ui.live

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.senal.tv.AppContainer
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.Category
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.BrandOrangeHot
import com.senal.tv.ui.theme.Graphite
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.util.CatalogRules
import com.senal.tv.util.DeviceUi
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Guía EN VIVO sobre el reproductor.
 * Scroll/foco por categorías/canales **NO** cambia el stream.
 * SELECT en un canal → sintoniza y oculta la guía (el vídeo sigue).
 * SELECT / toque con guía oculta → vuelve a mostrar la lista (sin pausar).
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveTvScreen(
    container: AppContainer,
    onPlay: (CatalogItem, List<CatalogItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var page by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(true) }
    var loadingCats by remember { mutableStateOf(true) }
    var loadingChannels by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var guideReady by remember { mutableStateOf(false) }
    /** Lista categorías/canales visible. SELECT la oculta/muestra; el vídeo no se pausa. */
    var guideVisible by remember { mutableStateOf(true) }

    /** Cursor visual al navegar (no implica reproducción). */
    var focusedChannelId by remember { mutableStateOf<String?>(null) }
    /** Canal realmente en aire — solo cambia al confirmar un canal en la lista. */
    var playing by remember { mutableStateOf<CatalogItem?>(null) }
    var buffering by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf<String?>(null) }
    var allowPlayback by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val pageSize = 60
    val rootFocus = remember { FocusRequester() }
    val guideVisibleRef = remember { AtomicBoolean(true) }

    LaunchedEffect(guideVisible) {
        guideVisibleRef.set(guideVisible)
        if (!guideVisible) {
            delay(40)
            runCatching { rootFocus.requestFocus() }
        }
    }

    val player = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1_500, 30_000, 1_000, 1_500)
                    .build()
            )
            .build()
            .apply { playWhenReady = true }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) playError = null
            }

            override fun onPlayerError(error: PlaybackException) {
                playError = "Señal inestable"
                scope.launch {
                    delay(1_200)
                    player.prepare()
                    player.play()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    val appSettings by container.settingsStore.settings.collectAsState(initial = com.senal.tv.data.local.AppSettings())
    val adultsSession by container.adultsUnlockedSession.collectAsState()
    val hideAdults = appSettings.adultsLocked && !adultsSession

    fun tune(channel: CatalogItem) {
        playing = channel
        focusedChannelId = channel.resolveId()
    }

    /** Confirmar canal: sintoniza (si hace falta) y oculta la guía. No pausa el vídeo. */
    fun confirmChannel(channel: CatalogItem) {
        if (playing?.resolveId() != channel.resolveId()) {
            tune(channel)
        } else {
            focusedChannelId = channel.resolveId()
        }
        guideVisible = false
        scope.launch { container.libraryRepository.markHistory(channel) }
    }

    fun showGuide() {
        guideVisible = true
    }

    // 1) Categorías primero.
    LaunchedEffect(hideAdults) {
        loadingCats = true
        runCatching { container.catalogRepository.categories("live", hideAdults = hideAdults) }
            .onSuccess {
                categories = it
                guideReady = true
                if (selected == null || categories.none { c -> c.label() == selected }) {
                    selected = CatalogRules.defaultCategory(it)
                }
            }
            .onFailure { error = it.message }
        loadingCats = false
        delay(48)
        allowPlayback = true
    }

    // 2) Lista de canales al cambiar categoría — NO toca el stream en aire.
    LaunchedEffect(selected) {
        val category = selected ?: return@LaunchedEffect
        loadingChannels = true
        error = null
        page = 1
        hasMore = true
        channels = emptyList()
        runCatching {
            container.catalogRepository.page(
                type = "live",
                category = category,
                page = 1,
                limit = pageSize
            )
        }.onSuccess { response ->
            channels = response.resolveItems()
            hasMore = response.resolveHasMore(pageSize)
            // Primera carga: sintonizar un canal inicial si aún no hay ninguno.
            if (playing == null) {
                channels.firstOrNull()?.let { tune(it) }
            } else {
                // Mantener cursor en el canal en aire si está en esta categoría.
                val keep = channels.firstOrNull { it.resolveId() == playing!!.resolveId() }
                focusedChannelId = keep?.resolveId() ?: focusedChannelId
            }
        }.onFailure {
            error = it.message ?: "No se pudieron cargar los canales"
        }
        loadingChannels = false
    }

    // 3) Reproducir SOLO cuando cambia el canal aprobado (SELECT), nunca por foco.
    LaunchedEffect(playing?.resolveId(), allowPlayback) {
        if (!allowPlayback) return@LaunchedEffect
        val item = playing ?: return@LaunchedEffect
        playError = null
        buffering = true
        runCatching { container.catalogRepository.playback(item.resolveId()) }
            .onSuccess { playback ->
                val url = playback.resolveUrl() ?: item.resolveStreamUrl()
                if (url.isNullOrBlank()) {
                    playError = "Sin URL"
                    return@onSuccess
                }
                val headers = playback.headers.orEmpty().toMutableMap()
                item.userAgent?.takeIf { it.isNotBlank() }?.let {
                    headers.putIfAbsent("User-Agent", it)
                }
                val mediaItem = MediaItem.fromUri(url)
                if (headers.isNotEmpty()) {
                    val http = DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(10_000)
                        .setReadTimeoutMs(15_000)
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
                scope.launch { container.libraryRepository.markHistory(item) }
            }
            .onFailure {
                playError = it.message
            }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= channels.lastIndex - 8
        }
    }

    LaunchedEffect(listState, channels, hasMore, loadingMore, selected) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { need ->
                if (!need || !hasMore || loadingMore || loadingChannels || selected == null) return@collect
                loadingMore = true
                val next = page + 1
                runCatching {
                    container.catalogRepository.page(
                        type = "live",
                        category = selected,
                        page = next,
                        limit = pageSize
                    )
                }.onSuccess { response ->
                    val newItems = response.resolveItems()
                    channels = (channels + newItems).distinctBy { it.resolveId() }
                    page = next
                    hasMore = response.resolveHasMore(pageSize) && newItems.isNotEmpty()
                }
                loadingMore = false
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val guiding = guideVisibleRef.get()
                val code = event.key.keyCode.toInt()
                val isSelect =
                    event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter ||
                        code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == android.view.KeyEvent.KEYCODE_ENTER
                val isMenu =
                    code == android.view.KeyEvent.KEYCODE_MENU ||
                        code == android.view.KeyEvent.KEYCODE_TV_CONTENTS_MENU ||
                        event.key == Key.Menu
                when {
                    (isSelect || isMenu) && !guiding -> {
                        showGuide()
                        true
                    }
                    isMenu && guiding -> {
                        guideVisible = false
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(guideVisible) {
                if (!guideVisible) {
                    detectTapGestures { showGuide() }
                }
            }
    ) {
        // —— Vídeo de fondo (sigue reproduciendo con guía abierta o cerrada) ——
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

        if (buffering && !guideVisible) {
            CircularProgressIndicator(
                color = BrandOrange,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            )
        }

        AnimatedVisibility(
            visible = !guideVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .background(Color(0xAA050810), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = playing?.resolveTitle() ?: "SEÑAL EN VIVO",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = when {
                        playError != null -> playError!!
                        buffering -> "Sintonizando…"
                        else -> "SELECT / toque = categorías y canales"
                    },
                    color = if (playError != null) Color(0xFFFF8A80) else BrandOrange,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = guideVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140))
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.78f),
                                0.42f to Color.Black.copy(alpha = 0.45f),
                                0.72f to Color.Black.copy(alpha = 0.15f),
                                1f to Color.Transparent
                            )
                        )
                )

                if (buffering) {
                    CircularProgressIndicator(
                        color = BrandOrange,
                        strokeWidth = 3.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 48.dp)
                            .size(36.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, top = 18.dp, bottom = 18.dp, end = 18.dp)
                ) {
                    val catW = if (DeviceUi.isTabletBuild) 240.dp else 210.dp
                    val chW = if (DeviceUi.isTabletBuild) 400.dp else 360.dp
                    Column(
                        modifier = Modifier
                            .width(catW)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(10.dp)
                    ) {
                        Text(
                            "CATEGORÍAS",
                            color = BrandOrangeHot,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (loadingCats && categories.isEmpty()) {
                                item {
                                    Text(
                                        "Cargando categorías…",
                                        color = TextMuted,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                            items(categories, key = { it.label() }) { category ->
                                val active = category.label() == selected
                                Surface(
                                    onClick = { selected = category.label() },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (active) BrandOrange else Color.Transparent,
                                        focusedContainerColor = if (active) {
                                            BrandOrangeHot
                                        } else {
                                            Color.White.copy(alpha = 0.18f)
                                        }
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = category.label(),
                                        color = if (active) Color.White else TextPrimary,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .width(chW)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.52f))
                            .padding(10.dp)
                    ) {
                        Text(
                            selected?.uppercase() ?: "CANALES",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        when {
                            error != null -> Text(
                                error!!,
                                color = Color(0xFFFF8A80),
                                modifier = Modifier.padding(8.dp)
                            )
                            loadingChannels && channels.isEmpty() ->
                                Text("Cargando canales…", color = TextMuted, modifier = Modifier.padding(8.dp))
                            channels.isEmpty() ->
                                Text("Sin canales", color = TextMuted, modifier = Modifier.padding(8.dp))
                            else -> LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(channels, key = { it.resolveId() }) { channel ->
                                    GuideChannelRow(
                                        item = channel,
                                        selected = channel.resolveId() == playing?.resolveId(),
                                        onFocused = { focusedChannelId = channel.resolveId() },
                                        onClick = { confirmChannel(channel) },
                                        onLongClick = {
                                            scope.launch {
                                                container.libraryRepository.toggleFavorite(channel)
                                            }
                                        }
                                    )
                                }
                                if (loadingMore) {
                                    item {
                                        Text(
                                            "Más canales…",
                                            color = TextMuted,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 16.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { guideVisible = false }
                            },
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                    ),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Text(
                                text = playing?.resolveTitle() ?: "SEÑAL EN VIVO",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    !guideReady -> "Preparando guía…"
                                    playError != null -> playError!!
                                    !allowPlayback -> "Guía lista…"
                                    buffering -> "Sintonizando…"
                                    else -> "SELECT = ver · Mantener = favorito · SELECT otra vez = guía"
                                },
                                color = if (playError != null) Color(0xFFFF8A80) else BrandOrange,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideChannelRow(
    item: CatalogItem,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(140),
        label = "chScale"
    )
    val bg = when {
        focused -> BrandOrange
        selected -> BrandOrange.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    val fg = if (focused) Color.White else TextPrimary
    val epg = item.resolveNow().ifBlank { "En vivo" }

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = bg,
            focusedContainerColor = BrandOrange
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .then(
                if (selected && !focused) {
                    Modifier.border(1.dp, BrandOrange.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
    ) {
        val rowPadV = if (DeviceUi.isTabletBuild) 14.dp else 8.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = rowPadV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (item.resolveNumber() ?: "·").toString(),
                color = if (focused) Color.White else BrandOrange,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.width(40.dp)
            )
            AsyncImage(
                model = item.resolveLogo(),
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Graphite)
            )
            Spacer(modifier = Modifier.width(10.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.resolveTitle(),
                    color = fg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = epg,
                    color = if (focused) Color.White.copy(alpha = 0.85f) else TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
