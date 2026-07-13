package com.senal.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.senal.tv.data.model.CatalogItem
import com.senal.tv.ui.theme.FocusRing
import com.senal.tv.ui.theme.Graphite
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import com.senal.tv.ui.theme.Violet
import com.senal.tv.util.formatDurationMinutes
import com.senal.tv.util.formatRating

@Composable
fun SenalBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF24153F), Graphite, Color(0xFF0A1514))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(420.dp)
                .blur(100.dp)
                .background(Violet.copy(alpha = 0.18f), RoundedCornerShape(50))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(380.dp)
                .blur(110.dp)
                .background(Teal.copy(alpha = 0.12f), RoundedCornerShape(50))
        )
        content()
    }
}

@Composable
fun BrandMark(compact: Boolean = false) {
    val typography = LocalSenalTypography.current
    Column {
        Text(
            text = "SEÑAL",
            style = if (compact) {
                typography.title.copy(letterSpacing = typography.brand.letterSpacing / 2)
            } else {
                typography.brand
            },
            color = TextPrimary
        )
        Text(
            text = "IPTV PREMIUM",
            style = typography.caption,
            color = Teal
        )
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
    val focusScale by animateFloatAsState(if (focused) 1.05f else 1f, label = "btnScale")
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .scale(focusScale)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Violet else GraphiteCard,
            focusedContainerColor = if (primary) Violet.copy(alpha = 0.95f) else Color(0xFF23232E),
            pressedContainerColor = Teal
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Violet, elevation = 12.dp)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = label, style = LocalSenalTypography.current.button, color = TextPrimary)
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
        CircularProgressIndicator(color = Teal, trackColor = Violet.copy(alpha = 0.25f))
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
        Text(
            text = message,
            color = Color(0xFFFFC9CF),
            style = LocalSenalTypography.current.body
        )
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
    val focusScale by animateFloatAsState(if (focused) 1.08f else 1f, label = "posterScale")
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(170.dp)
            .scale(focusScale)
            .onFocusChanged { focused = it.isFocused }
            .shadow(if (focused) 18.dp else 6.dp, RoundedCornerShape(20.dp)),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = GraphiteCard,
            focusedContainerColor = GraphiteCard
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, FocusRing),
                shape = RoundedCornerShape(20.dp)
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
    val focusScale by animateFloatAsState(if (focused) 1.03f else 1f, label = "chScale")
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .scale(focusScale)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = GraphiteCard,
            focusedContainerColor = Color(0xFF211B33)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Teal),
                shape = RoundedCornerShape(18.dp)
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (item.resolveNumber() ?: "·").toString(),
                    style = LocalSenalTypography.current.caption,
                    color = Teal,
                    modifier = Modifier.width(42.dp)
                )
                AsyncImage(
                    model = item.resolveLogo(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF101016)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.resolveTitle(),
                        style = LocalSenalTypography.current.body,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.resolveNow().ifBlank { "EPG no disponible" },
                        style = LocalSenalTypography.current.caption,
                        color = TextPrimary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Siguiente: ${item.resolveNext().ifBlank { "—" }}",
                        style = LocalSenalTypography.current.caption,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    )
}

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
