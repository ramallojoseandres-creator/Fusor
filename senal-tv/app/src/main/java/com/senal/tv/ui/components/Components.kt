package com.senal.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.FocusWhite
import com.senal.tv.ui.theme.Graphite
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.util.formatDurationMinutes
import com.senal.tv.util.formatRating

@Composable
fun SenalBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1208), Graphite, Color(0xFF050508))
                )
            )
    ) { content() }
}

@Composable
fun BrandMark(compact: Boolean = false) {
    val typography = LocalSenalTypography.current
    Column {
        Text(
            text = "SEÑAL",
            style = if (compact) typography.title.copy(letterSpacing = typography.brand.letterSpacing / 2)
            else typography.brand,
            color = BrandOrange
        )
        Text(text = "IPTV PREMIUM", style = typography.caption, color = TextMuted)
    }
}

@Composable
fun FocusableButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.08f else 1f,
        animationSpec = tween(180),
        label = "btnScale"
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) BrandOrange else GraphiteCard,
            focusedContainerColor = if (primary) BrandOrangeHotSafe else Color(0xFF23232E),
            pressedContainerColor = BrandOrange
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, if (primary) FocusWhite else BrandOrange),
                shape = RoundedCornerShape(14.dp)
            )
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = BrandOrange, elevation = 10.dp)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = LocalSenalTypography.current.button,
                    color = if (primary) Color.Black else TextPrimary
                )
            }
        }
    )
}

private val BrandOrangeHotSafe = Color(0xFFFF6A00)

@Composable
fun ColorTile(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 78.dp
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f,
        animationSpec = tween(160),
        label = "tileScale"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .shadow(if (focused) 16.dp else 4.dp, RoundedCornerShape(16.dp)),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color,
            focusedContainerColor = color
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, FocusWhite),
                shape = RoundedCornerShape(16.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = label,
                    style = LocalSenalTypography.current.button,
                    color = FocusWhite
                )
            }
        }
    )
}

@Composable
fun LoadingPulse(label: String = "Cargando…") {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(color = BrandOrange, trackColor = BrandOrange.copy(alpha = 0.2f))
        Text(text = label, style = LocalSenalTypography.current.subtitle, color = TextMuted)
    }
}

@Composable
fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33FF6B7A))
            .border(1.dp, Color(0x66FF6B7A), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(text = message, color = Color(0xFFFFC9CF), style = LocalSenalTypography.current.body)
    }
}

@Composable
fun PosterCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showMeta: Boolean = true
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.1f else 1f,
        animationSpec = tween(180),
        label = "posterScale"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(170.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .shadow(if (focused) 20.dp else 6.dp, RoundedCornerShape(16.dp)),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = GraphiteCard,
            focusedContainerColor = GraphiteCard
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, FocusWhite),
                shape = RoundedCornerShape(16.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Column {
                AsyncImage(
                    model = item.resolvePoster(),
                    contentDescription = item.resolveTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(Color(0xFF22222C))
                )
                if (showMeta) {
                    Text(
                        text = item.resolveTitle(),
                        style = LocalSenalTypography.current.body,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)
                    )
                    Text(
                        text = listOfNotNull(
                            item.year?.toString(),
                            formatDurationMinutes(item.resolveDurationMinutes()),
                            formatRating(item.rating)
                        ).joinToString(" · "),
                        style = LocalSenalTypography.current.caption,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = 12.dp,
                            top = 4.dp
                        )
                    )
                }
            }
        }
    )
}

@Composable
fun ChannelCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.03f else 1f,
        animationSpec = tween(150),
        label = "chScale"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (focused) FocusWhite else GraphiteCard,
            focusedContainerColor = FocusWhite
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, BrandOrange),
                shape = RoundedCornerShape(14.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            val titleColor = if (focused) Color.Black else TextPrimary
            val muted = if (focused) Color.Black.copy(alpha = 0.65f) else TextMuted
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (item.resolveNumber() ?: "·").toString(),
                    style = LocalSenalTypography.current.caption,
                    color = if (focused) BrandOrange else Teal,
                    modifier = Modifier.width(42.dp)
                )
                AsyncImage(
                    model = item.resolveLogo(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101016)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.resolveTitle(),
                        style = LocalSenalTypography.current.body,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.resolveNow().ifBlank { "EPG no disponible" },
                        style = LocalSenalTypography.current.caption,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier
                        .size(8.dp)
                        .background(Color(0xFF22C55E), Circleish)
                )
            }
        }
    )
}

private val Circleish = RoundedCornerShape(50)

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text = title, style = LocalSenalTypography.current.title, color = TextPrimary)
        if (!subtitle.isNullOrBlank()) {
            Text(text = subtitle, style = LocalSenalTypography.current.subtitle, color = TextMuted)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = LocalSenalTypography.current.subtitle, color = TextMuted)
    }
}
