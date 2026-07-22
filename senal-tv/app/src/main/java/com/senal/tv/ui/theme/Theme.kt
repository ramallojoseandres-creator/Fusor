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
 * SEÑAL — identidad Flujo-style en negro profundo + azul + turquesa.
 */
val Graphite = Color(0xFF000000)
val GraphiteElevated = Color(0xFF121212)
val GraphiteCard = Color(0xFF1A1A1A)
val PanelGlass = Color(0xE6121212)
val PanelBorder = Color(0x33FFFFFF)

/** Turquesa — foco, CTAs activos, textos clave. */
val BrandTurquoise = Color(0xFF00E5C8)
val BrandTurquoiseHot = Color(0xFF5CFFE8)

/** Azul — elementos principales, carga, estados. */
val BrandBlue = Color(0xFF1E6FFF)
val BrandBlueDeep = Color(0xFF0A2A6B)
val BrandBlueSoft = Color(0xFF3D9EFF)

/** Alias legacy (código existente usa BrandOrange = acento principal). */
val BrandOrange = BrandTurquoise
val BrandOrangeHot = BrandTurquoiseHot
val SignalCyan = BrandTurquoise
val SignalCyanHot = BrandTurquoiseHot
val NeonMagenta = Color(0xFF3D9EFF)
val NeonPurple = Color(0xFF1E6FFF)
val NeonBlue = BrandBlueSoft
val LiveYellow = Color(0xFFFFC107)
val LiveGreen = Color(0xFF39E56A)
val ChannelGold = Color(0xFFFFD54A)
val LiveRed = Color(0xFFE53935)
val Violet = BrandBlue
val Teal = BrandTurquoise
val TilePurple = BrandBlue
val TileCyan = BrandTurquoise
val TileGreen = BrandBlueSoft
val TileCoral = BrandBlueDeep
val FocusWhite = Color(0xFFEAF6FF)
val TextPrimary = Color(0xFFF2F7FA)
val TextMuted = Color(0xFF8A9BB0)
val Danger = Color(0xFFFF8A80)

val SplashGradient = Brush.radialGradient(
    colors = listOf(BrandBlueDeep, Graphite, Color(0xFF000000))
)

val HomeAtmosphere = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF121212),
        Color(0xFF000000),
        Color(0xFF000000)
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
        color = BrandTurquoise
    )
)

val LocalSenalTypography = staticCompositionLocalOf { SenalTypography() }

private val SenalDarkScheme = darkColorScheme(
    primary = BrandTurquoise,
    onPrimary = Color.Black,
    secondary = BrandBlue,
    onSecondary = TextPrimary,
    background = Graphite,
    onBackground = TextPrimary,
    surface = GraphiteElevated,
    onSurface = TextPrimary,
    border = BrandTurquoise.copy(alpha = 0.55f)
)

@Composable
fun SenalTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSenalTypography provides SenalTypography()) {
        MaterialTheme(colorScheme = SenalDarkScheme, content = content)
    }
}
