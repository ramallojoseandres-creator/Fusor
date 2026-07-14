package com.senal.tv.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.senal.tv.AppContainer
import com.senal.tv.data.local.AppSettings
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.Graphite
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.ui.theme.Violet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


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
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())

    var loading by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf(item) }
    var aspectMode by remember { mutableIntStateOf(0) }
    var speed by remember { mutableFloatStateOf(settings.playbackSpeed) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var reconnectAttempt by remember { mutableIntStateOf(0) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var requestKey by remember { mutableIntStateOf(0) }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setPreferredAudioLanguage("es"))
        }
    }

    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_500, 50_000, 1_500, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    val player = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                playWhenReady = true
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
    }

    fun playNeighbor(delta: Int) {
        if (neighbors.isEmpty()) return
        val idx = neighbors.indexOfFirst { it.resolveId() == current.resolveId() }
        if (idx < 0) return
        val nextIdx = (idx + delta).coerceIn(0, neighbors.lastIndex)
        if (nextIdx == idx) return
        current = neighbors[nextIdx]
        requestKey++
    }

    fun cycleAspect(playerView: PlayerView?) {
        aspectMode = (aspectMode + 1) % 3
        playerView?.resizeMode = when (aspectMode) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun cycleAudio() {
        val audioGroups = player.currentTracks.groups.filter {
            it.type == C.TRACK_TYPE_AUDIO && it.length > 0
        }
        if (audioGroups.isEmpty()) return
        val currentIndex = audioGroups.indexOfFirst { group ->
            (0 until group.length).any { group.isTrackSelected(it) }
        }.coerceAtLeast(0)
        val next = audioGroups[(currentIndex + 1) % audioGroups.size]
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(next.mediaTrackGroup, 0))
            .build()
    }

    fun cycleSubtitles() {
        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        if (disabled || textGroups.isEmpty()) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .apply {
                    textGroups.firstOrNull()?.let {
                        setOverrideForType(TrackSelectionOverride(it.mediaTrackGroup, 0))
                    }
                }
                .build()
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    }

    LaunchedEffect(current, requestKey) {
        loading = true
        error = null
        overlayVisible = true
        runCatching { container.catalogRepository.playback(current.resolveId()) }
            .onSuccess { playback ->
                val url = playback.resolveUrl()
                    ?: current.resolveStreamUrl()
                if (url.isNullOrBlank()) {
                    error = playback.error ?: "Sin URL de reproducción"
                    loading = false
                    return@onSuccess
                }
                val start = if (current.resolveId() == item.resolveId()) startPositionMs else C.TIME_UNSET
                val headers = playback.headers.orEmpty().toMutableMap()
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
                container.libraryRepository.markHistory(current)
            }
            .onFailure {
                error = it.message ?: "Error al obtener reproducción"
                loading = false
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

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // HUD (CH±, título, categoría, botones): visible 5s al iniciar / al mostrar, luego se oculta.
    LaunchedEffect(overlayVisible, current.resolveId(), requestKey) {
        if (overlayVisible) {
            delay(5_000)
            overlayVisible = false
        }
    }

    LaunchedEffect(speed) {
        player.setPlaybackSpeed(speed)
    }

    BackHandler {
        if (overlayVisible) onBack() else overlayVisible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Graphite)
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
                    playerViewRef = this
                    setOnClickListener { overlayVisible = !overlayVisible }
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

        AnimatedVisibility(
            visible = overlayVisible || error != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xCC0B0B0F), Color.Transparent, Color(0xDD0B0B0F))
                        )
                    )
                    .padding(28.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.TopStart)) {
                    Text("SEÑAL", style = LocalSenalTypography.current.caption, color = BrandOrange)
                    Text(current.resolveTitle(), style = LocalSenalTypography.current.title, color = TextPrimary)
                    Text(
                        text = "AHORA  ·  ${current.resolveNow().ifBlank { current.resolveCategory() }}",
                        style = LocalSenalTypography.current.subtitle
                    )
                    if (current.resolveNext().isNotBlank()) {
                        Text(
                            "Siguiente: ${current.resolveNext()}",
                            style = LocalSenalTypography.current.caption,
                            color = TextMuted
                        )
                    }
                    error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = Color(0xFFFFB4BC), style = LocalSenalTypography.current.body)
                    }
                }

                // Side zap list (reference HUD)
                if (neighbors.size > 1) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(280.dp)
                            .background(Color(0xAA050508), RoundedCornerShape(16.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val idx = neighbors.indexOfFirst { it.resolveId() == current.resolveId() }
                        neighbors
                            .drop((idx - 2).coerceAtLeast(0))
                            .take(5)
                            .forEach { ch ->
                                val selected = ch.resolveId() == current.resolveId()
                                FocusableButton(
                                    label = ch.resolveTitle(),
                                    onClick = {
                                        current = ch
                                        requestKey++
                                        overlayVisible = true
                                    },
                                    primary = selected,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FocusableButton(label = "Volver", onClick = onBack, primary = false)
                    FocusableButton(
                        label = "Favorito",
                        onClick = {
                            scope.launch { container.libraryRepository.toggleFavorite(current) }
                            overlayVisible = true
                        },
                        primary = false
                    )
                    FocusableButton(label = "CH −", onClick = { playNeighbor(-1) }, primary = false)
                    FocusableButton(label = "CH +", onClick = { playNeighbor(1) }, primary = false)
                    FocusableButton(
                        label = "Calidad",
                        onClick = {
                            val params = trackSelector.parameters
                            trackSelector.setParameters(
                                params.buildUpon()
                                    .setForceHighestSupportedBitrate(!params.forceHighestSupportedBitrate)
                                    .build()
                            )
                            overlayVisible = true
                        },
                        primary = false
                    )
                    FocusableButton(label = "Audio", onClick = {
                        cycleAudio()
                        overlayVisible = true
                    }, primary = false)
                    FocusableButton(label = "Subs", onClick = {
                        cycleSubtitles()
                        overlayVisible = true
                    }, primary = false)
                    FocusableButton(label = "Aspecto", onClick = {
                        cycleAspect(playerViewRef)
                        overlayVisible = true
                    }, primary = false)
                    FocusableButton(label = "Velocidad", onClick = {
                        speed = when (speed) {
                            1f -> 1.25f
                            1.25f -> 1.5f
                            else -> 1f
                        }
                        overlayVisible = true
                    }, primary = false)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = if (durationMs > 0) {
                                "${formatTime(positionMs)} / ${formatTime(durationMs)}"
                            } else {
                                "EN VIVO"
                            },
                            style = LocalSenalTypography.current.caption,
                            color = Violet
                        )
                        Text("${speed}x", style = LocalSenalTypography.current.caption, color = Teal)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
