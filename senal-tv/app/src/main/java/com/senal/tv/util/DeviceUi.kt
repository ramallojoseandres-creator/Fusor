package com.senal.tv.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.senal.tv.BuildConfig

/**
 * Perfil UI para Galaxy Tab A8 (SM-X200) y tablets similares.
 * Pantalla típica: 10.5" · 1920×1200 · 16:10 · táctil.
 */
object DeviceUi {
    val isTabletBuild: Boolean get() = BuildConfig.IS_TABLET

    /** Flujo hub primero: no autoplay al último canal. */
    val autoPlayLastChannelDefault: Boolean get() = false
}

data class TabletLayout(
    val isTablet: Boolean,
    val padH: Dp,
    val padV: Dp,
    val tileHeight: Dp,
    val sideWeight: Float,
    val channelRowHeight: Dp,
    val touchMin: Dp
)

@Composable
fun rememberTabletLayout(): TabletLayout {
    val cfg = LocalConfiguration.current
    val widest = maxOf(cfg.screenWidthDp, cfg.screenHeightDp)
    val isTabletScreen = DeviceUi.isTabletBuild || widest >= 900
    return if (isTabletScreen) {
        TabletLayout(
            isTablet = true,
            padH = 36.dp,
            padV = 20.dp,
            tileHeight = 100.dp,
            sideWeight = 0.36f,
            channelRowHeight = 56.dp,
            touchMin = 48.dp
        )
    } else {
        TabletLayout(
            isTablet = false,
            padH = 40.dp,
            padV = 18.dp,
            tileHeight = 88.dp,
            sideWeight = 0.34f,
            channelRowHeight = 46.dp,
            touchMin = 40.dp
        )
    }
}
