package com.senal.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import coil.compose.AsyncImage
import com.senal.tv.R
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

/** FLUJO focus scale — matches focus_scale_anim.xml (1.13). */
const val FOCUS_SCALE = 1.13f
const val FOCUS_SCALE_SOFT = 1.05f

@Composable
fun rememberFlujoFocusModifier(focused: Boolean, big: Boolean = true): Modifier {
    val target = if (focused) {
        if (big) FOCUS_SCALE else FOCUS_SCALE_SOFT
    } else {
        1f
    }
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "flujoFocus"
    )
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "flujoGlow"
    )
    return Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = if (focused) 24f else 0f
        }
        .drawBehind {
            if (glow > 0f) {
                val pad = size.width * 0.02f
                drawRoundRect(
                    color = BrandOrange.copy(alpha = 0.35f * glow),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width + pad * 2, size.height + pad * 2),
                    topLeft = Offset(-pad, -pad)
                )
            }
        }
}

@Composable
fun SenalBackground(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.mipmap.main_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )
        content()
    }
}

@Composable
fun BrandMark(compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.mipmap.logo_new),
            contentDescription = "SEÑAL",
            modifier = Modifier.size(if (compact) 36.dp else 56.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "SEÑAL",
                style = if (compact) {
                    LocalSenalTypography.current.title
                } else {
                    LocalSenalTypography.current.brand
                },
                color = BrandOrange
            )
            if (!compact) {
                Text(
                    text = "IPTV PREMIUM",
                    style = LocalSenalTypography.current.caption,
                    color = TextMuted
                )
            }
        }
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
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(rememberFlujoFocusModifier(focused, big = false))
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) BrandOrange else GraphiteCard,
            focusedContainerColor = if (primary) Color(0xFFFF6A00) else Color(0xFF23232E),
            pressedContainerColor = BrandOrange
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, FocusWhite),
                shape = RoundedCornerShape(14.dp)
            )
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = BrandOrange, elevation = 14.dp)
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

/**
 * FLUJO-style horizontal category column (FocusColumView).
 * Uses real tile art + 1.13 spring zoom.
 */
@Composable
fun FocusColumnTile(
    label: String,
    normalRes: Int,
    focusedRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .then(rememberFlujoFocusModifier(focused, big = true))
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = if (focused) focusedRes else normalRes,
                    contentDescription = label,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = label,
                    style = LocalSenalTypography.current.button,
                    color = FocusWhite,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    )
}

@Composable
fun ColorTile(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 78.dp
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(rememberFlujoFocusModifier(focused, big = true))
            .onFocusChanged { focused = it.isFocused },
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
                Text(text = label, style = LocalSenalTypography.current.button, color = FocusWhite)
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
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(170.dp)
            .then(rememberFlujoFocusModifier(focused, big = true))
            .onFocusChanged { focused = it.isFocused },
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
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(rememberFlujoFocusModifier(focused, big = false))
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
                        .background(Color(0xFF22C55E), RoundedCornerShape(50))
                )
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

@Composable
fun PulseBorder(active: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val pulse = remember { Animatable(0.4f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            pulse.animateTo(1f, tween(700))
            pulse.animateTo(0.4f, tween(700))
        }
    }
    Box(
        modifier = modifier.border(
            width = 2.dp,
            color = BrandOrange.copy(alpha = if (active) pulse.value else 0f),
            shape = RoundedCornerShape(14.dp)
        )
    ) { content() }
}
