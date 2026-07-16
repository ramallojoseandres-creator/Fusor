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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
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
import com.senal.tv.ui.theme.ChannelGold
import com.senal.tv.ui.theme.Graphite
import com.senal.tv.ui.theme.LiveRed
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.util.CatalogRules
import com.senal.tv.util.DeviceUi
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
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
    /** HUD inferior: se muestra al cerrar guía / cambiar canal y se oculta a los 5 s. */
    var hudVisible by remember { mutableStateOf(false) }

    /** Cursor visual al navegar (no implica reproducción). */
    var focusedChannelId by remember { mutableStateOf<String?>(null) }
    /** Canal realmente en aire — solo cambia al confirmar un canal en la lista. */
    var playing by remember { mutableStateOf<CatalogItem?>(null) }
    var buffering by remember { mutableStateOf(false) }
    var playError by remember { mutableStateOf<String?>(null) }
    var allowPlayback by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val catListState = rememberLazyListState()
    val pageSize = 60
    val rootFocus = remember { FocusRequester() }
    val playingFocus = remember { FocusRequester() }
    val guideVisibleRef = remember { AtomicBoolean(true) }
    var guideFocusTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(guideVisible) {
        guideVisibleRef.set(guideVisible)
        if (!guideVisible) {
            delay(40)
            runCatching { rootFocus.requestFocus() }
        }
    }

    // HUD: visible al salir de la guía o al cambiar de canal; desaparece a los 5 s de reproducción.
    LaunchedEffect(guideVisible, playing?.resolveId(), playError) {
        if (guideVisible) {
            hudVisible = false
            return@LaunchedEffect
        }
        hudVisible = true
        // Espera a que deje de bufferizar (reproducción iniciada) y luego 5 s.
        var waited = 0
        while (buffering && waited < 15_000) {
            delay(100)
            waited += 100
        }
        delay(5_000)
        if (playError == null) hudVisible = false
    }

    // Al abrir la guía: categoría del canal en aire + scroll + foco en ese canal (no en categorías).
    LaunchedEffect(guideVisible, guideFocusTick, channels, playing?.resolveId()) {
        if (!guideVisible) return@LaunchedEffect
        val id = playing?.resolveId() ?: return@LaunchedEffect
        val cat = playing?.resolveCategory().orEmpty()
        if (cat.isNotBlank()) {
            val catIdx = categories.indexOfFirst { it.label() == cat }
            if (catIdx >= 0) {
                runCatching { catListState.scrollToItem(catIdx) }
            }
        }
        val idx = channels.indexOfFirst { it.resolveId() == id }
        if (idx >= 0) {
            runCatching { listState.scrollToItem(idx) }
            delay(90)
            runCatching { playingFocus.requestFocus() }
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
        val ch = playing
        if (ch != null) {
            val cat = ch.resolveCategory()
            if (cat.isNotBlank() && categories.any { it.label() == cat }) {
                selected = cat
            }
            focusedChannelId = ch.resolveId()
        }
        guideVisible = true
        guideFocusTick++
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
            visible = (!guideVisible && hudVisible) || playError != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LivePlayerHud(
                channel = playing,
                error = playError,
                buffering = buffering,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            )
        }

        AnimatedVisibility(
            visible = guideVisible,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140))
        ) {
            Box(Modifier.fillMaxSize()) {
                // Soft veil — video stays visible on the right (not a full takeover).
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.55f),
                                0.38f to Color.Black.copy(alpha = 0.28f),
                                0.62f to Color.Black.copy(alpha = 0.08f),
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
                        .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 16.dp)
                ) {
                    val catW = if (DeviceUi.isTabletBuild) 220.dp else 190.dp
                    val chW = if (DeviceUi.isTabletBuild) 360.dp else 320.dp
                    Column(
                        modifier = Modifier
                            .width(catW)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xB008101C))
                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(14.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            "CATEGORÍAS",
                            color = BrandOrangeHot,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        LazyColumn(
                            state = catListState,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
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
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (active) {
                                            BrandOrange.copy(alpha = 0.28f)
                                        } else {
                                            Color.Transparent
                                        },
                                        focusedContainerColor = BrandOrange.copy(alpha = 0.4f)
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (active) {
                                                Modifier.border(
                                                    1.dp,
                                                    BrandOrange.copy(0.55f),
                                                    RoundedCornerShape(8.dp)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    Text(
                                        text = category.label(),
                                        color = if (active) BrandOrangeHot else TextPrimary,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(
                        modifier = Modifier
                            .width(chW)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xB008101C))
                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(14.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            selected?.uppercase() ?: "CANALES",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
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
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                contentPadding = PaddingValues(bottom = 12.dp)
                            ) {
                                items(channels, key = { it.resolveId() }) { channel ->
                                    val isOnAir = channel.resolveId() == playing?.resolveId()
                                    GuideChannelRow(
                                        item = channel,
                                        selected = isOnAir,
                                        focusRequester = if (isOnAir) playingFocus else null,
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

                    // Free video area — tap to dismiss; small status only.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 12.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { guideVisible = false }
                            },
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x99060A14))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = playing?.resolveTitle() ?: "SEÑAL EN VIVO",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    !guideReady -> "Preparando guía…"
                                    playError != null -> playError!!
                                    !allowPlayback -> "Guía lista…"
                                    buffering -> "Sintonizando…"
                                    else -> "SELECT = ver · Mantener = favorito"
                                },
                                color = if (playError != null) Color(0xFFFF8A80) else BrandOrangeHot,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePlayerHud(
    channel: CatalogItem?,
    error: String?,
    buffering: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = remember(channel?.resolveId()) { fakeProgress(channel) }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE6080C14))
            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelMark(channel, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val num = channel?.resolveNumber()
                if (num != null) {
                    Text(
                        text = "$num",
                        color = ChannelGold,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = channel?.resolveTitle() ?: "SEÑAL EN VIVO",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LiveRed)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("● EN VIVO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = when {
                    error != null -> error
                    buffering -> "Sintonizando…"
                    else -> channel?.resolveNow()?.ifBlank { "Programación en vivo" }
                        ?: "SELECT = guía · Mantener = favorito"
                },
                color = if (error != null) Color(0xFFFF8A80) else TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            ProgressBar(progress = progress, color = ChannelGold)
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "★ Favorito",
                color = Color.White.copy(0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Guía del canal",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun ChannelMark(channel: CatalogItem?, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val initials = channelInitials(channel)
    val color = logoColor(channel)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        val logo = channel?.resolveLogo()
        if (!logo.isNullOrBlank()) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(initials, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(0.15f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0.08f, 0.95f))
                .fillMaxHeight()
                .background(color)
        )
    }
}

@Composable
private fun GuideChannelRow(
    item: CatalogItem,
    selected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.015f else 1f,
        animationSpec = tween(140),
        label = "chScale"
    )
    val epg = item.resolveNow().ifBlank { "En vivo" }

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                focused -> BrandOrange.copy(alpha = 0.85f)
                selected -> BrandOrange.copy(alpha = 0.22f)
                else -> Color.White.copy(alpha = 0.04f)
            },
            focusedContainerColor = BrandOrange
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
                    Modifier.border(1.dp, BrandOrange.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
    ) {
        val rowPadV = if (DeviceUi.isTabletBuild) 11.dp else 7.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = rowPadV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (item.resolveNumber() ?: "·").toString(),
                color = if (focused) Color.White else BrandOrangeHot,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.width(34.dp)
            )
            AsyncImage(
                model = item.resolveLogo(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Graphite)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.resolveTitle(),
                    color = if (focused) Color.White else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = epg,
                    color = if (focused) Color.White.copy(0.85f) else TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun channelInitials(channel: CatalogItem?): String {
    val title = channel?.resolveTitle().orEmpty().trim()
    if (title.isBlank()) return "S"
    val parts = title.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        else -> title.take(2).uppercase()
    }
}

private fun logoColor(channel: CatalogItem?): Color {
    val seed = abs((channel?.resolveId() ?: channel?.resolveTitle().orEmpty()).hashCode())
    val palette = listOf(
        Color(0xFF39E56A),
        Color(0xFF4AA8FF),
        Color(0xFFE53935),
        Color(0xFF2AD4C8),
        Color(0xFFC6FF00),
        Color(0xFFFF2D95),
        Color(0xFFFF9800)
    )
    return palette[seed % palette.size]
}

private fun fakeProgress(channel: CatalogItem?): Float {
    val seed = abs((channel?.resolveId() ?: "senal").hashCode())
    return 0.28f + (seed % 55) / 100f
}
