package com.senal.tv.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reproducción a pantalla completa.
 * ▲ / ▼ (sin guía) → canal anterior / siguiente
 * OK     → muestra/oculta guía (el stream NO se pausa)
 * SELECT sobre un canal en la guía → sintoniza y oculta la lista
 * BACK   → si guía abierta la cierra; si no, sale
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    container: AppContainer,
    item: CatalogItem,
    startPositionMs: Long,
    neighbors: List<CatalogItem> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf(item) }
    var requestKey by remember { mutableIntStateOf(0) }
    var reconnectAttempt by remember { mutableIntStateOf(0) }

    var guideVisible by remember { mutableStateOf(false) }
    var guideTick by remember { mutableIntStateOf(0) }
    var infoVisible by remember { mutableStateOf(true) }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var selectedCategory by remember {
        mutableStateOf(item.resolveCategory().ifBlank { null })
    }
    var guideChannels by remember { mutableStateOf(neighbors.ifEmpty { listOf(item) }) }
    /** Lista para ▲▼ fuera de la guía — solo cambia al CONFIRMAR canal con SELECT. */
    var zapList by remember { mutableStateOf(neighbors.ifEmpty { listOf(item) }) }

    val channelFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val guideVisibleRef = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var guideOpenTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(guideVisible) {
        guideVisibleRef.set(guideVisible)
        if (!guideVisible) {
            delay(40)
            runCatching { rootFocus.requestFocus() }
        }
    }

    // SELECT abre guía: categoría del canal en aire + foco/scroll en ese canal (no en categorías).
    LaunchedEffect(guideVisible, guideOpenTick, guideChannels, current.resolveId()) {
        if (!guideVisible) return@LaunchedEffect
        val id = current.resolveId()
        val idx = guideChannels.indexOfFirst { it.resolveId() == id }
        if (idx >= 0) {
            delay(60)
            runCatching { channelFocus.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { rootFocus.requestFocus() }
    }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setPreferredAudioLanguage("es"))
        }
    }
    // Buffers moderados: Fire Stick / boxes con poca RAM no aguantan 50s de buffer.
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(1_500, 22_000, 1_000, 1_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }
    val player = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
    }

    fun bumpGuideTimer() {
        guideTick++
    }

    fun openGuide() {
        // Abrir en la categoría del canal en aire — navegación parte de ese canal.
        val cat = current.resolveCategory().ifBlank { selectedCategory }
        if (!cat.isNullOrBlank()) selectedCategory = cat
        guideVisible = true
        guideOpenTick++
        bumpGuideTimer()
    }

    fun playNeighbor(delta: Int) {
        if (zapList.isEmpty()) return
        val idx = zapList.indexOfFirst { it.resolveId() == current.resolveId() }.let {
            if (it < 0) 0 else it
        }
        val nextIdx = (idx + delta + zapList.size) % zapList.size
        if (zapList[nextIdx].resolveId() == current.resolveId() && zapList.size == 1) return
        current = zapList[nextIdx]
        requestKey++
        infoVisible = true
    }

    fun selectChannel(ch: CatalogItem) {
        // Confirmar canal: sintoniza si cambió y oculta la guía. No pausa el vídeo.
        if (ch.resolveId() != current.resolveId()) {
            current = ch
            if (guideChannels.isNotEmpty()) zapList = guideChannels
            requestKey++
            infoVisible = true
        }
        guideVisible = false
    }

    LaunchedEffect(Unit) {
        val settings = container.settingsStore.settings.first()
        val hideAdults = settings.adultsLocked && !container.adultsUnlockedSession.value
        val cats = runCatching {
            container.catalogRepository.categories("live", hideAdults = hideAdults)
        }.getOrDefault(emptyList())
        categories = cats
        if (selectedCategory.isNullOrBlank() || cats.none { it.label() == selectedCategory }) {
            val fromItem = item.resolveCategory().ifBlank { null }
            selectedCategory = when {
                fromItem != null && cats.any { it.label() == fromItem } -> fromItem
                else -> CatalogRules.defaultCategory(cats)
            }
        }
    }

    // Solo recarga la lista visible de la guía — NO cambia el stream ni zapList.
    LaunchedEffect(selectedCategory, guideVisible) {
        if (!guideVisible) return@LaunchedEffect
        val page = runCatching {
            container.catalogRepository.page(
                type = "live",
                category = selectedCategory,
                page = 1,
                limit = 120
            )
        }.getOrNull()
        val list = page?.resolveItems().orEmpty().ifEmpty { page?.items.orEmpty() }
        if (list.isNotEmpty()) {
            guideChannels = list
        }
        bumpGuideTimer()
    }

    LaunchedEffect(guideVisible, guideTick) {
        if (!guideVisible) return@LaunchedEffect
        delay(8_000)
        guideVisible = false
    }

    LaunchedEffect(infoVisible, current.resolveId(), requestKey) {
        if (infoVisible) {
            delay(5_000)
            infoVisible = false
        }
    }

    LaunchedEffect(current, requestKey) {
        loading = true
        error = null
        fun startPlayback(url: String, headersIn: Map<String, String>) {
            val start = if (current.resolveId() == item.resolveId()) startPositionMs else C.TIME_UNSET
            val headers = headersIn.toMutableMap()
            current.userAgent?.takeIf { it.isNotBlank() }?.let {
                headers.putIfAbsent("User-Agent", it)
            }
            val mediaItem = MediaItem.fromUri(url)
            if (headers.isNotEmpty()) {
                val httpFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(12_000)
                    .setReadTimeoutMs(20_000)
                    .setDefaultRequestProperties(headers)
                val source = if (url.contains(".m3u8", ignoreCase = true)) {
                    HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
                } else {
                    DefaultMediaSourceFactory(httpFactory).createMediaSource(mediaItem)
                }
                player.setMediaSource(source, start)
            } else {
                player.setMediaItem(mediaItem, start)
            }
            player.prepare()
            player.play()
            scope.launch { container.libraryRepository.markHistory(current) }
        }
        runCatching { container.catalogRepository.playback(current.resolveId()) }
            .onSuccess { playback ->
                val url = playback.resolveUrl() ?: current.resolveStreamUrl()
                if (url.isNullOrBlank()) {
                    error = playback.error ?: "Sin URL de reproducción"
                    loading = false
                    return@onSuccess
                }
                startPlayback(url, playback.headers.orEmpty())
            }
            .onFailure {
                // Películas iptv-org / URLs directas en el CatalogItem
                val direct = current.resolveStreamUrl()
                if (!direct.isNullOrBlank()) {
                    startPlayback(direct, emptyMap())
                } else {
                    error = it.message ?: "Error al obtener reproducción"
                    loading = false
                }
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    loading = false
                    error = null
                    reconnectAttempt = 0
                }
            }

            override fun onPlayerError(errorEx: PlaybackException) {
                error = "Señal interrumpida. Reconectando…"
                scope.launch {
                    reconnectAttempt += 1
                    delay((1_000L * reconnectAttempt).coerceAtMost(8_000L))
                    player.prepare()
                    player.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) loading = false
            }
        }
        player.addListener(listener)
        onDispose {
            val pos = player.currentPosition
            val dur = player.duration.coerceAtLeast(0L)
            val snapshot = current
            scope.launch {
                container.libraryRepository.saveProgress(
                    contentId = snapshot.resolveId(),
                    type = snapshot.contentType(),
                    title = snapshot.resolveTitle(),
                    poster = snapshot.resolvePoster(),
                    positionMs = pos,
                    durationMs = dur
                )
            }
            player.removeListener(listener)
            player.release()
        }
    }


    BackHandler {
        if (guideVisible) guideVisible = false else onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Graphite)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val guiding = guideVisibleRef.get()
                // key.keyCode is Long in Compose; Android KeyEvent codes are Int
                val code = event.key.keyCode.toInt()
                when {
                    event.key == Key.DirectionUp || code == android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (guiding) {
                            bumpGuideTimer()
                            false
                        } else {
                            playNeighbor(-1)
                            true
                        }
                    }
                    event.key == Key.DirectionDown || code == android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (guiding) {
                            bumpGuideTimer()
                            false
                        } else {
                            playNeighbor(1)
                            true
                        }
                    }
                    event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter ||
                        code == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == android.view.KeyEvent.KEYCODE_ENTER -> {
                        if (!guiding) {
                            openGuide()
                            true
                        } else {
                            bumpGuideTimer()
                            false
                        }
                    }
                    // Botón Menú del Fire TV / Android TV → abrir/cerrar guía
                    code == android.view.KeyEvent.KEYCODE_MENU ||
                        code == android.view.KeyEvent.KEYCODE_TV_CONTENTS_MENU ||
                        event.key == Key.Menu -> {
                        if (guiding) guideVisible = false else openGuide()
                        true
                    }
                    else -> {
                        if (guiding) bumpGuideTimer()
                        false
                    }
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = player
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    // Keys handled by focused Compose parent (avoids stale closure bugs)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.player = player }
        )

        if (loading || buffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = BrandOrange
            )
        }

        // HUD inferior (mockup atv-02-reproductor)
        AnimatedVisibility(
            visible = (infoVisible && !guideVisible) || error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PlayerInfoHud(
                channel = current,
                error = error,
                buffering = buffering,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            )
        }

        AnimatedVisibility(
            visible = guideVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerGuideOverlay(
                categories = categories,
                selectedCategory = selectedCategory,
                channels = guideChannels,
                currentId = current.resolveId(),
                channelFocusRequester = channelFocus,
                onCategory = {
                    // Solo cambia la lista visible — el vídeo sigue con current.
                    selectedCategory = it
                    bumpGuideTimer()
                },
                onChannel = { selectChannel(it) },
                onFavorite = { ch ->
                    scope.launch {
                        container.libraryRepository.toggleFavorite(ch)
                        bumpGuideTimer()
                    }
                },
                onInteract = { bumpGuideTimer() }
            )
        }
    }
}

@Composable
private fun PlayerGuideOverlay(
    categories: List<Category>,
    selectedCategory: String?,
    channels: List<CatalogItem>,
    currentId: String,
    channelFocusRequester: FocusRequester,
    onCategory: (String) -> Unit,
    onChannel: (CatalogItem) -> Unit,
    onFavorite: (CatalogItem) -> Unit,
    onInteract: () -> Unit
) {
    val catState = rememberLazyListState()
    val chState = rememberLazyListState()

    // Al abrir: categoría activa visible + canal en aire centrado (navegación desde ahí).
    LaunchedEffect(selectedCategory, categories) {
        val catIdx = categories.indexOfFirst { it.label() == selectedCategory }
        if (catIdx >= 0) runCatching { catState.scrollToItem(catIdx) }
    }
    LaunchedEffect(channels, currentId) {
        val idx = channels.indexOfFirst { it.resolveId() == currentId }
        if (idx >= 0) {
            runCatching { chState.scrollToItem(idx.coerceAtLeast(0)) }
            delay(80)
            runCatching { channelFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xF0000810), Color(0x99000810), Color(0x44000810), Color.Transparent)
                )
            )
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(560.dp)
                .background(Color(0xEE050810), RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            Text(
                text = "SELECT = ver · Mantener = favorito · Menú = guía",
                color = BrandOrangeHot,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyColumn(
                    state = catState,
                    modifier = Modifier
                        .width(200.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    item {
                        Text(
                            "CATEGORÍAS",
                            color = BrandOrangeHot,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    items(categories, key = { it.label() }) { cat ->
                        val active = cat.label() == selectedCategory
                        GuideRow(
                            label = cat.label(),
                            selected = active,
                            requestFocus = false,
                            onClick = {
                                onInteract()
                                onCategory(cat.label())
                            }
                        )
                    }
                }

                LazyColumn(
                    state = chState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    item {
                        Text(
                            "CANALES",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    items(channels, key = { it.resolveId() }) { ch ->
                        val isPlaying = ch.resolveId() == currentId
                        GuideRow(
                            label = ch.resolveTitle(),
                            selected = isPlaying,
                            // Foco externo en el canal en aire (desde SELECT al abrir guía).
                            focusRequester = if (isPlaying) channelFocusRequester else null,
                            onClick = {
                                onInteract()
                                onChannel(ch)
                            },
                            onLongClick = {
                                onInteract()
                                onFavorite(ch)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    requestFocus: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val localFr = remember { FocusRequester() }
    val fr = focusRequester ?: localFr
    LaunchedEffect(requestFocus, focusRequester) {
        if (requestFocus || focusRequester != null && selected) {
            // El padre ya pide foco al abrir; aquí solo si requestFocus legacy.
            if (requestFocus) runCatching { fr.requestFocus() }
        }
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(fr)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when {
                focused -> BrandOrange
                selected -> BrandOrange.copy(alpha = 0.28f)
                else -> Color.White.copy(alpha = 0.05f)
            },
            focusedContainerColor = BrandOrangeHot
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Text(
            text = label,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            fontSize = 14.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun PlayerInfoHud(
    channel: CatalogItem,
    error: String?,
    buffering: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = remember(channel.resolveId()) {
        0.28f + (abs(channel.resolveId().hashCode()) % 55) / 100f
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE6080C14))
            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val initials = channel.resolveTitle().trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .let { parts ->
                when {
                    parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
                    else -> channel.resolveTitle().take(2).uppercase().ifBlank { "S" }
                }
            }
        val markColor = listOf(
            Color(0xFF39E56A), Color(0xFF4AA8FF), Color(0xFFE53935),
            Color(0xFF2AD4C8), Color(0xFFFF9800)
        )[abs(channel.resolveId().hashCode()) % 5]
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(markColor),
            contentAlignment = Alignment.Center
        ) {
            val logo = channel.resolveLogo()
            if (!logo.isNullOrBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(initials, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                channel.resolveNumber()?.let { num ->
                    Text(
                        text = "$num",
                        color = ChannelGold,
                        fontWeight = FontWeight.Black,
                        fontSize = 26.sp
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = channel.resolveTitle(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
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
                    else -> channel.resolveNow().ifBlank {
                        channel.resolveCategory().ifBlank { "Programación en vivo" }
                    }
                },
                color = if (error != null) Color(0xFFFFB4BC) else TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(0.15f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0.08f, 0.95f))
                        .fillMaxHeight()
                        .background(ChannelGold)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("★ Favorito", color = Color.White.copy(0.9f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Guía del canal",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
