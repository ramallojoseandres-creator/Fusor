package com.senal.tv.ui.live

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.senal.tv.ui.components.SenalClickable
import coil.compose.AsyncImage
import com.senal.tv.AppContainer
import com.senal.tv.R
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.Category
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.BrandOrangeHot
import com.senal.tv.ui.theme.LiveGreen
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.util.CatalogRules
import com.senal.tv.util.DeviceUi
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Guía EN VIVO estilo Flujo (SEÑAL):
 * vídeo a pantalla completa + categorías | canales a la izquierda.
 * Navegación D-pad: solo mueve el foco; OK confirma y oculta la guía.
 * (Sin preview-on-focus: retunear ExoPlayer en cada flecha era el mayor lag.)
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveTvScreen(
    container: AppContainer,
    onBack: () -> Unit = {},
    onPlay: (CatalogItem, List<CatalogItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    /** Categoría con foco D-pad (instantánea — no dispara I/O). */
    var focusedCategory by remember { mutableStateOf<String?>(null) }
    /** Categoría cuya lista de canales está cargada / en curso (debounce). */
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
    val catScroll = rememberScrollState()
    val pageSize = 120
    val rootFocus = remember { FocusRequester() }
    val playingFocus = remember { FocusRequester() }
    val categoryFocusRequesters = remember(categories) {
        categories.associate { it.label() to FocusRequester() }
    }
    val guideVisibleRef = remember { AtomicBoolean(true) }
    var guideFocusTick by remember { mutableIntStateOf(0) }
    /** true = foco en lista de canales; false = foco en categorías. */
    var focusInChannels by remember { mutableStateOf(false) }
    /**
     * Solo para restaurar foco al abrir la guía.
     * NO sigue al canal en aire durante el preview (eso laggea y mueve el foco).
     */
    var restoreChannelId by remember { mutableStateOf<String?>(null) }
    /** Caché página-1 por categoría → cambio de categoría casi instantáneo. */
    val channelCache = remember { LinkedHashMap<String, CachedCatPage>(48) }

    fun focusSelectedCategory() {
        val cat = focusedCategory ?: selected ?: return
        val requester = categoryFocusRequesters[cat] ?: return
        focusInChannels = false
        runCatching { requester.requestFocus() }
    }

    LaunchedEffect(guideVisible) {
        guideVisibleRef.set(guideVisible)
        if (!guideVisible) {
            delay(40)
            if (!DeviceUi.isTouchBuild) {
                runCatching { rootFocus.requestFocus() }
            }
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

    // BACK: canales → categoría actual; categorías → cierra guía; sin guía → home.
    BackHandler {
        when {
            guideVisible && focusInChannels -> focusSelectedCategory()
            guideVisible -> guideVisible = false
            else -> onBack()
        }
    }

    // Al abrir la guía: categoría del canal en aire + scroll + foco en ese canal (no en categorías).
    LaunchedEffect(guideVisible, guideFocusTick, channels, restoreChannelId) {
        if (!guideVisible) return@LaunchedEffect
        val id = restoreChannelId ?: return@LaunchedEffect
        val idx = channels.indexOfFirst { it.resolveId() == id }
        if (idx >= 0) {
            runCatching { listState.scrollToItem(idx) }
            delay(40)
            focusInChannels = true
            runCatching { playingFocus.requestFocus() }
        }
        // Liberar el requester fijado para que el D-pad no pelee con el preview.
        delay(50)
        if (restoreChannelId == id) restoreChannelId = null
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
                focusedCategory = cat
                selected = cat
            }
            focusedChannelId = ch.resolveId()
            restoreChannelId = ch.resolveId()
        }
        guideVisible = true
        guideFocusTick++
    }

    fun toggleFavForCurrent() {
        val channel = channels.firstOrNull { it.resolveId() == focusedChannelId }
            ?: playing
            ?: return
        scope.launch {
            val added = runCatching {
                container.libraryRepository.toggleFavorite(channel)
            }.getOrNull()
            val msg = when (added) {
                true -> "FAV · ${channel.resolveTitle()}"
                false -> "Quitado de FAV · ${channel.resolveTitle()}"
                null -> "No se pudo actualizar FAV"
            }
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** Zapping instantáneo ▲/▼ a pantalla completa (guía cerrada). */
    fun zapChannel(delta: Int) {
        if (channels.isEmpty()) return
        val currentId = playing?.resolveId()
        val idx = channels.indexOfFirst { it.resolveId() == currentId }.let { if (it < 0) 0 else it }
        val nextIdx = (idx + delta + channels.size) % channels.size
        val next = channels[nextIdx]
        if (next.resolveId() == currentId && channels.size == 1) return
        tune(next)
        scope.launch { container.libraryRepository.markHistory(next) }
    }

    // 1) Categorías primero.
    LaunchedEffect(hideAdults) {
        loadingCats = true
        runCatching { container.catalogRepository.categories("live", hideAdults = hideAdults) }
            .onSuccess {
                categories = it
                guideReady = true
                if (selected == null || categories.none { c -> c.label() == selected }) {
                    val def = CatalogRules.defaultCategory(it)
                    selected = def
                    focusedCategory = def
                }
            }
            .onFailure { error = it.message }
        loadingCats = false
        delay(16)
        allowPlayback = true
    }

    // Foco en categorías: debounce mínimo (solo agrupa ticks del D-pad).
    LaunchedEffect(focusedCategory) {
        val cat = focusedCategory ?: return@LaunchedEffect
        delay(28)
        if (focusedCategory != cat) return@LaunchedEffect
        if (selected != cat) selected = cat
    }

    // 2) Lista de canales al cambiar categoría — caché + prefetch vecinos.
    LaunchedEffect(selected) {
        val category = selected ?: return@LaunchedEffect
        error = null
        page = 1
        val cached = channelCache[category]
        if (cached != null) {
            channels = cached.items
            hasMore = cached.hasMore
            loadingChannels = false
            if (playing == null) {
                channels.firstOrNull()?.let { tune(it) }
            }
        } else {
            loadingChannels = true
            hasMore = true
            runCatching {
                container.catalogRepository.page(
                    type = "live",
                    category = category,
                    page = 1,
                    limit = pageSize
                )
            }.onSuccess { response ->
                if (selected != category) return@onSuccess
                val items = response.resolveItems()
                val more = response.resolveHasMore(pageSize)
                channelCache[category] = CachedCatPage(items, more)
                trimCache(channelCache)
                channels = items
                hasMore = more
                if (playing == null) {
                    channels.firstOrNull()?.let { tune(it) }
                } else {
                    val keep = channels.firstOrNull { it.resolveId() == playing!!.resolveId() }
                    focusedChannelId = keep?.resolveId() ?: focusedChannelId
                }
            }.onFailure {
                if (selected == category) {
                    error = it.message ?: "No se pudieron cargar los canales"
                }
            }
            if (selected == category) loadingChannels = false
        }

        // Prefetch categorías vecinas (arriba/abajo) en idle — cambio instantáneo al volver.
        val labels = categories.map { it.label() }
        val idx = labels.indexOf(category)
        if (idx >= 0) {
            val neighbors = listOfNotNull(
                labels.getOrNull(idx - 1),
                labels.getOrNull(idx + 1),
                labels.getOrNull(idx + 2)
            )
            for (n in neighbors) {
                if (channelCache.containsKey(n)) continue
                runCatching {
                    container.catalogRepository.page(
                        type = "live",
                        category = n,
                        page = 1,
                        limit = pageSize
                    )
                }.onSuccess { response ->
                    channelCache[n] = CachedCatPage(
                        items = response.resolveItems(),
                        hasMore = response.resolveHasMore(pageSize)
                    )
                    trimCache(channelCache)
                }
            }
        }
    }

    // Navegación rápida: NO retunear ExoPlayer al pasar el foco por canales.
    // Solo se sintoniza al confirmar (OK) o zapping con guía cerrada.
    // (El preview-on-focus era el mayor lag del D-pad.)

    // 3) Reproducir cuando cambia el canal en aire.
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
                    val merged = (channels + newItems).distinctBy { it.resolveId() }
                    channels = merged
                    page = next
                    hasMore = response.resolveHasMore(pageSize) && newItems.isNotEmpty()
                    val cat = selected
                    if (cat != null) {
                        channelCache[cat] = CachedCatPage(merged, hasMore)
                    }
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
                val isBack =
                    event.key == Key.Back ||
                        code == android.view.KeyEvent.KEYCODE_BACK

                // Con guía abierta: NO consumir OK aquí.
                // Las categorías / canales deben recibir el click del Surface (si no, no se selecciona).
                // Solo manejamos MENU=FAV y BACK; OK largo FAV lo hace onLongClick del canal.
                if (guiding && isSelect) {
                    return@onPreviewKeyEvent false
                }

                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isUp =
                    event.key == Key.DirectionUp ||
                        code == android.view.KeyEvent.KEYCODE_DPAD_UP ||
                        code == android.view.KeyEvent.KEYCODE_CHANNEL_UP
                val isDown =
                    event.key == Key.DirectionDown ||
                        code == android.view.KeyEvent.KEYCODE_DPAD_DOWN ||
                        code == android.view.KeyEvent.KEYCODE_CHANNEL_DOWN
                when {
                    isBack && guiding && focusInChannels -> {
                        focusSelectedCategory()
                        true
                    }
                    isBack && guiding -> {
                        guideVisible = false
                        true
                    }
                    isBack && !guiding -> {
                        onBack()
                        true
                    }
                    isSelect && !guiding -> {
                        showGuide()
                        true
                    }
                    isMenu && guiding -> {
                        toggleFavForCurrent()
                        true
                    }
                    isMenu && !guiding -> {
                        showGuide()
                        true
                    }
                    // Pantalla completa: zapping instantáneo con DPAD ▲/▼
                    isUp && !guiding -> {
                        zapChannel(-1)
                        true
                    }
                    isDown && !guiding -> {
                        zapChannel(1)
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
                    val catW = if (DeviceUi.isTouchBuild) 200.dp else 168.dp
                    val chW = if (DeviceUi.isTouchBuild) 340.dp else 292.dp
                    Column(
                        modifier = Modifier
                            .width(catW)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xB3050810))
                            .padding(vertical = 4.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.brand_logo_pill),
                            contentDescription = "SEÑAL",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .height(22.dp)
                                .widthIn(max = 110.dp)
                        )
                        // Column (no Lazy): todas las categorías son focusables → no se traba en Bolivia.
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(catScroll),
                        ) {
                            if (loadingCats && categories.isEmpty()) {
                                Text(
                                    "Cargando…",
                                    color = TextMuted,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            categories.forEach { category ->
                                val label = category.label()
                                val catRequester = categoryFocusRequesters[label]
                                val active = label == (focusedCategory ?: selected)
                                var catFocused by remember(label) { mutableStateOf(false) }
                                val highlighted = active || catFocused
                                SenalClickable(
                                    onClick = {
                                        focusedCategory = label
                                        selected = label
                                    },
                                    shape = RoundedCornerShape(0.dp),
                                    containerColor = when {
                                        highlighted -> BrandOrange.copy(alpha = 0.92f)
                                        else -> Color.Transparent
                                    },
                                    focusedContainerColor = BrandOrange.copy(alpha = 0.95f),
                                    pressedContainerColor = BrandOrange.copy(alpha = 0.95f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (catRequester != null) Modifier.focusRequester(catRequester)
                                            else Modifier
                                        )
                                        .onFocusChanged { state ->
                                            catFocused = state.isFocused
                                            if (state.isFocused) {
                                                focusInChannels = false
                                                focusedCategory = label
                                            }
                                        },
                                    onFocusedChange = { focused ->
                                        catFocused = focused
                                        if (focused) {
                                            focusInChannels = false
                                            focusedCategory = label
                                        }
                                    }
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = 12.dp,
                                                vertical = if (DeviceUi.isTouchBuild) 10.dp else 12.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier
                                                .size(if (highlighted) 8.dp else 6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (highlighted) Color.White else Color.White.copy(0.7f)
                                                )
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = label,
                                            color = if (highlighted) Color.White else TextPrimary,
                                            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = if (DeviceUi.isTouchBuild) 13.sp else 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    Column(
                        modifier = Modifier
                            .width(chW)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xB3181C24))
                            .padding(6.dp)
                    ) {
                        FavTipsBanner(Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
                        Spacer(Modifier.height(4.dp))
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
                            else -> {
                                val exitToCategory = (focusedCategory ?: selected)?.let {
                                    categoryFocusRequesters[it]
                                }
                                LazyColumn(
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    contentPadding = PaddingValues(bottom = 12.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (exitToCategory != null) {
                                                Modifier.focusProperties {
                                                    left = exitToCategory
                                                    // Algunas TV mandan "Back" espacial como salir a la izquierda.
                                                }
                                            } else Modifier
                                        )
                                        .onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                            val code = event.key.keyCode.toInt()
                                            val isLeft =
                                                event.key == Key.DirectionLeft ||
                                                    code == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                                            if (isLeft) {
                                                focusSelectedCategory()
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                ) {
                                    items(
                                        channels,
                                        key = { it.resolveId() },
                                        contentType = { "ch" },
                                    ) { channel ->
                                        val id = channel.resolveId()
                                        val isOnAir = id == playing?.resolveId()
                                        GuideChannelRow(
                                            item = channel,
                                            selected = isOnAir,
                                            focusRequester = if (id == restoreChannelId) playingFocus else null,
                                            onFocused = {
                                                focusInChannels = true
                                                focusedChannelId = id
                                            },
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
                                text = playing?.resolveTitle() ?: "SEÑAL · EN VIVO",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    !guideReady -> "Preparando guía…"
                                    playError != null -> playError!!
                                    !allowPlayback -> "Guía lista…"
                                    buffering -> "Sintonizando…"
                                    else -> "Tu ventana al mundo · SELECT cierra guía"
                                },
                                color = if (playError != null) Color(0xFFFF8A80) else BrandOrangeHot,
                                fontSize = 11.sp,
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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(0.dp))
            .background(Color(0xE6050810))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelMark(channel, size = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val num = channel?.resolveNumber()
            Text(
                text = buildString {
                    if (num != null) append("$num ")
                    append(channel?.resolveTitle() ?: "SEÑAL · EN VIVO")
                },
                color = BrandOrangeHot,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    error != null -> error
                    buffering -> "Sintonizando…"
                    else -> channel?.resolveNow()?.ifBlank { "No información" } ?: "No información"
                },
                color = if (error != null) Color(0xFFFF8A80) else Color.White.copy(0.9f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = "Next :${channel?.resolveNext()?.ifBlank { "No información" } ?: "No información"}",
                color = Color.White.copy(0.7f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            FavTipLine(menu = true)
            Spacer(Modifier.height(4.dp))
            FavTipLine(menu = false)
        }
    }
}

@Composable
private fun FavTipsBanner(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC0A0E16))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        FavTipLine(menu = true)
        Spacer(Modifier.height(4.dp))
        FavTipLine(menu = false)
    }
}

@Composable
private fun FavTipLine(menu: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (menu) BrandOrange else LiveGreen),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (menu) "≡" else "OK",
                color = Color.White,
                fontSize = if (menu) 11.sp else 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (menu) {
                "MENU Agregar / Eliminar "
            } else {
                "Mantenga presionada OK 2s Agregar/Eliminar "
            },
            color = Color.White.copy(0.9f),
            fontSize = 10.sp
        )
        Text("FAV", color = BrandOrangeHot, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChannelMark(channel: CatalogItem?, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val initials = channelInitials(channel)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val logo = channel?.resolveLogo()
        if (!logo.isNullOrBlank()) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
            )
        } else {
            Text(initials, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
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
    val title = remember(item.resolveId()) { item.resolveTitle() }
    val epg = remember(item.resolveId()) { item.resolveNow().ifBlank { "No información" } }
    val logo = remember(item.resolveId()) { item.resolveLogo() }
    val numberLabel = remember(item.resolveId()) { (item.resolveNumber() ?: "·").toString() }

    SenalClickable(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = RoundedCornerShape(4.dp),
        containerColor = when {
            focused -> BrandOrange.copy(alpha = 0.92f)
            selected -> BrandOrange.copy(alpha = 0.28f)
            else -> Color.Transparent
        },
        focusedContainerColor = BrandOrange,
        pressedContainerColor = BrandOrange,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                val now = it.isFocused
                if (focused != now) focused = now
                if (now) onFocused()
            },
        onFocusedChange = { now ->
            if (focused != now) focused = now
            if (now) onFocused()
        }
    ) {
        val rowPadV = if (DeviceUi.isTouchBuild) 12.dp else 6.dp
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = rowPadV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected || focused) {
                SignalBars(
                    color = if (focused) Color.White else BrandOrangeHot,
                    modifier = Modifier
                        .width(28.dp)
                        .height(16.dp)
                )
            } else {
                Text(
                    text = numberLabel,
                    color = Color.White.copy(0.9f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(28.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                // Logos solo en fila enfocada/seleccionada → menos trabajo al pasar D-pad.
                if ((focused || selected) && !logo.isNullOrBlank()) {
                    AsyncImage(
                        model = logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                    )
                } else {
                    Text(
                        channelInitials(item),
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
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

@Composable
private fun SignalBars(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gaps = 3.dp.toPx()
        val w = (size.width - gaps * 2) / 3f
        val heights = listOf(0.45f, 0.7f, 1f)
        heights.forEachIndexed { i, hFrac ->
            val h = size.height * hFrac
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(i * (w + gaps), size.height - h),
                size = androidx.compose.ui.geometry.Size(w, h)
            )
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

private data class CachedCatPage(
    val items: List<CatalogItem>,
    val hasMore: Boolean
)

private fun trimCache(cache: LinkedHashMap<String, CachedCatPage>, max: Int = 24) {
    while (cache.size > max) {
        val oldest = cache.keys.firstOrNull() ?: break
        cache.remove(oldest)
    }
}