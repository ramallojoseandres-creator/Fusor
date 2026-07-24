package com.senal.tv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.common.MediaItem
import java.util.concurrent.TimeUnit

/**
 * Fábrica / administrador de ExoPlayer (Media3) optimizado para Android TV.
 *
 * - HW decode preferido ([DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER])
 * - Búferes vivos cortos (menos RAM en Fire Stick) vs VOD más profundos
 * - Reintentos silenciosos ante microcortes HLS/DASH
 */
@OptIn(UnstableApi::class)
object ExoPlayerManager {

    enum class Profile {
        /** Mini-player Home: silencioso, loop, búfer mínimo. */
        PREVIEW,
        /** Canal en vivo / zap: baja latencia relativa, reintentos agresivos. */
        LIVE,
        /** Película / serie: más buffer, seek cómodo. */
        VOD,
    }

    fun create(context: Context, profile: Profile): ExoPlayer {
        val appContext = context.applicationContext
        val renderersFactory = DefaultRenderersFactory(appContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        val trackSelector = DefaultTrackSelector(appContext).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage("es")
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true),
            )
        }

        val loadControl = when (profile) {
            Profile.PREVIEW -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 800,
                    /* maxBufferMs = */ 8_000,
                    /* bufferForPlaybackMs = */ 500,
                    /* bufferForPlaybackAfterRebufferMs = */ 800,
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            Profile.LIVE -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(1_500, 18_000, 1_000, 1_500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            Profile.VOD -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(2_500, 35_000, 1_500, 2_500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setLoadErrorHandlingPolicy(SilentRetryPolicy())

        return ExoPlayer.Builder(appContext)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                playWhenReady = true
                if (profile == Profile.PREVIEW) {
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                }
            }
    }

    /** Construye MediaSource HLS / DASH / progresivo con headers opcionales. */
    fun mediaSourceFor(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): MediaSource {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(headers)
        val item = MediaItem.fromUri(url)
        val lower = url.lowercase()
        return when {
            lower.contains(".mpd") || lower.contains("dash") ->
                DashMediaSource.Factory(http)
                    .setLoadErrorHandlingPolicy(SilentRetryPolicy())
                    .createMediaSource(item)
            lower.contains(".m3u8") || lower.contains("hls") || lower.contains("/live/") ->
                HlsMediaSource.Factory(http)
                    .setAllowChunklessPreparation(true)
                    .setLoadErrorHandlingPolicy(SilentRetryPolicy())
                    .createMediaSource(item)
            else ->
                DefaultMediaSourceFactory(http)
                    .setLoadErrorHandlingPolicy(SilentRetryPolicy())
                    .createMediaSource(item)
        }
    }

    fun playUrl(
        player: ExoPlayer,
        url: String,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long = C.TIME_UNSET,
    ) {
        player.setMediaSource(mediaSourceFor(url, headers), startPositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    fun formatBitrate(bitsPerSecond: Int?): String {
        val br = bitsPerSecond ?: 0
        if (br <= 0) return "—"
        val mb = br / 1_000_000f
        return if (mb >= 1f) String.format("%.1f Mb/s", mb) else String.format("%.0f Kb/s", br / 1000f)
    }

    /**
     * Reintentos silenciosos: no propaga errores fatales en microcortes;
     * backoff corto para segmentos HLS.
     */
    private class SilentRetryPolicy : DefaultLoadErrorHandlingPolicy(/* minimumLoadableRetryCount = */ 6) {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val ex = loadErrorInfo.exception
            // Microcortes de red / 5xx / timeouts → reintento rápido.
            if (ex is HttpDataSource.HttpDataSourceException ||
                ex is PlaybackException ||
                ex.cause is java.io.IOException
            ) {
                val n = loadErrorInfo.errorCount.coerceAtMost(6)
                return TimeUnit.MILLISECONDS.toMillis((250L * (1L shl (n - 1))).coerceAtMost(4_000L))
            }
            return super.getRetryDelayMsFor(loadErrorInfo)
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 8
    }
}
