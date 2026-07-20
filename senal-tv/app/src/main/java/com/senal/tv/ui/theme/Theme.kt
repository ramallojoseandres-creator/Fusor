package com.senal.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * SEÑAL ATV v2 — navy void + neon cyan / magenta / gold accents (mockup atv-00 / guía / player).
 */
val Graphite = Color(0xFF060A14)
val GraphiteElevated = Color(0xFF0C1424)
val GraphiteCard = Color(0xFF121C30)
val PanelGlass = Color(0xE60A1220)
val PanelBorder = Color(0x33FFFFFF)

/** Primary accent = SEÑAL teal (not Flujo Magis orange). */
val BrandOrange = Color(0xFF00E5C8)
val BrandOrangeHot = Color(0xFF5CFFE8)
val SignalCyan = BrandOrange
val SignalCyanHot = BrandOrangeHot
val NeonMagenta = Color(0xFFFF2D95)
val NeonPurple = Color(0xFF8B5CFF)
val NeonBlue = Color(0xFF3D9EFF)
val LiveYellow = Color(0xFFFFC107)
val LiveGreen = Color(0xFF39E56A)
val ChannelGold = Color(0xFFFFD54A)
val LiveRed = Color(0xFFE53935)
val Violet = NeonPurple
val Teal = Color(0xFF2AD4C8)
val TilePurple = NeonPurple
val TileCyan = BrandOrange
val TileGreen = LiveGreen
val TileCoral = NeonMagenta
val FocusWhite = Color(0xFFEAF6FF)
val TextPrimary = Color(0xFFEAF2FA)
val TextMuted = Color(0xFF8A9BB0)
val Danger = Color(0xFFFF8A80)

val SplashGradient = Brush.radialGradient(
    colors = listOf(Color(0xFF0A2038), Graphite, Color(0xFF020408))
)

val HomeAtmosphere = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0A1228),
        Color(0xFF060A14),
        Color(0xFF040810)
    )
)

data class SenalTypography(
    val brand: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        letterSpacing = 4.sp,
        color = TextPrimary
    ),
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    val subtitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = TextMuted
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = TextPrimary
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = TextMuted
    ),
    val button: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.3.sp,
        color = TextPrimary
    ),
    val tagline: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 1.5.sp,
        color = TextPrimary
    )
)

val LocalSenalTypography = staticCompositionLocalOf { SenalTypography() }

private val SenalDarkScheme = darkColorScheme(
    primary = BrandOrange,
    onPrimary = Color.Black,
    secondary = NeonMagenta,
    onSecondary = Graphite,
    background = Graphite,
    onBackground = TextPrimary,
    surface = GraphiteElevated,
    onSurface = TextPrimary,
    border = BrandOrange.copy(alpha = 0.45f)
)

@Composable
fun SenalTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSenalTypography provides SenalTypography()) {
        MaterialTheme(colorScheme = SenalDarkScheme, content = content)
    }
}
