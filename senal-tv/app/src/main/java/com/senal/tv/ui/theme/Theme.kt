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
 * Palette sampled from the SEÑAL intro (splash.mp4):
 * deep navy void + cyan signal rings + silver highlight.
 */
val Graphite = Color(0xFF000810)
val GraphiteElevated = Color(0xFF0A1820)
val GraphiteCard = Color(0xFF102028)

/** Primary accent = ring cyan from intro. */
val BrandOrange = Color(0xFF1A9BC4)
val BrandOrangeHot = Color(0xFF3EC4E8)
val SignalCyan = BrandOrange
val SignalCyanHot = BrandOrangeHot
val Violet = Color(0xFF7EB8C8)
val Teal = Color(0xFF2AA8C0)
val TilePurple = Color(0xFF3A6A80)
val TileCyan = Color(0xFF3EC4E8)
val TileGreen = Color(0xFF4DB8A0)
val TileCoral = Color(0xFF6A9BB0)
val FocusWhite = Color(0xFFE8F4F8)
val TextPrimary = Color(0xFFE8F0F4)
val TextMuted = Color(0xFF8AA0AE)
val Danger = Color(0xFFFF8A80)

val SplashGradient = Brush.radialGradient(
    colors = listOf(Color(0xFF001828), Graphite, Color(0xFF000408))
)

data class SenalTypography(
    val brand: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 64.sp,
        letterSpacing = 8.sp,
        color = TextPrimary
    ),
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = TextPrimary
    ),
    val subtitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = TextMuted
    ),
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = TextPrimary
    ),
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = TextMuted
    ),
    val button: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = TextPrimary
    ),
    val tagline: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 2.sp,
        color = TextPrimary
    )
)

val LocalSenalTypography = staticCompositionLocalOf { SenalTypography() }

private val SenalDarkScheme = darkColorScheme(
    primary = BrandOrange,
    onPrimary = Color.Black,
    secondary = Teal,
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
