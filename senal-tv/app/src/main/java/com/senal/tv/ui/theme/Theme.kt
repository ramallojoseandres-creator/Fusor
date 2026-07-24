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

/** SEÑAL dark canvas — cool charcoal, not Flujo black/orange. */
val Graphite = Color(0xFF06080C)
val GraphiteElevated = Color(0xFF10151C)
val GraphiteCard = Color(0xFF161C24)

/** Primary: signal teal. Distinct from Flujo orange (#DC4800). */
val BrandAccent = Color(0xFF2EE6C5)
val BrandAccentHot = Color(0xFF5CF0D4)
val BrandBlue = Color(0xFF3D8BFF)
val FocusRing = Color(0xFFE8F1FF)
val FocusWhite = Color(0xFFFFFFFF)
val TextPrimary = Color(0xFFF4F7FB)
val TextMuted = Color(0xFF93A0B0)
val Danger = Color(0xFFFF5C6C)
val Success = Color(0xFF2EE6C5)

/** Tile accents for home sections (cool spectrum, no Flujo rainbow). */
val TileLive = Color(0xFF1F6FEB)
val TileMovies = Color(0xFF2EE6C5)
val TileSeries = Color(0xFF5B8DEF)
val TileFavorites = Color(0xFF3D8BFF)
val TileSearch = Color(0xFF7AD7C8)

@Deprecated("Use BrandAccent — kept only as compile alias during migration")
val BrandOrange = BrandAccent
@Deprecated("Use BrandAccentHot")
val BrandOrangeHot = BrandAccentHot
@Deprecated("Use BrandBlue")
val Violet = BrandBlue
@Deprecated("Use TileFavorites")
val TilePurple = TileFavorites
val Teal = BrandAccent
val TileCyan = BrandAccentHot
val TileGreen = Color(0xFF3DDC97)
val TileCoral = Danger

val SplashGradient = Brush.radialGradient(
    colors = listOf(Color(0xFF0C1A22), Graphite, Color(0xFF04060A))
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
    primary = BrandAccent,
    onPrimary = Color.Black,
    secondary = BrandBlue,
    onSecondary = Graphite,
    background = Graphite,
    onBackground = TextPrimary,
    surface = GraphiteElevated,
    onSurface = TextPrimary,
    border = BrandAccent.copy(alpha = 0.45f)
)

@Composable
fun SenalTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSenalTypography provides SenalTypography()) {
        MaterialTheme(colorScheme = SenalDarkScheme, content = content)
    }
}
