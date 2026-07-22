package com.senal.tv.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.senal.tv.BuildConfig
import com.senal.tv.R
import com.senal.tv.data.repository.AuthRepository
import com.senal.tv.ui.components.ErrorMessage
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.BrandOrangeHot
import com.senal.tv.ui.theme.LiveGreen
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Login estilo Flujo: collage de fondo, campos pill blancos, foco teal SEÑAL.
 */
@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoggedIn: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val userFocus = remember { FocusRequester() }
    var clock by remember {
        mutableStateOf(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) to
                SimpleDateFormat("EEE. dd/MM/yyyy", Locale("es")).format(Date())
        )
    }

    LaunchedEffect(Unit) {
        userFocus.requestFocus()
        while (true) {
            clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) to
                SimpleDateFormat("EEE. dd/MM/yyyy", Locale("es")).format(Date())
            delay(15_000)
        }
    }

    fun submit() {
        if (loading || success) return
        if (username.isBlank() || password.isBlank()) {
            error = "Por favor ingrese la contraseña Y el usuario"
            return
        }
        scope.launch {
            loading = true
            error = null
            val result = authRepository.login(username, password)
            loading = false
            result.onSuccess {
                success = true
                delay(700)
                onLoggedIn()
            }.onFailure {
                val raw = it.message.orEmpty()
                error = when {
                    raw.contains("Unable to resolve", true) ||
                        raw.contains("Failed to connect", true) ||
                        raw.contains("timeout", true) ||
                        raw.contains("Connection", true) ||
                        raw.contains("UnknownHost", true) ->
                        "Reconectando… Comprueba el servidor (${com.senal.tv.ServerConfig.SERVER_IP})"
                    else -> raw.ifBlank { "No se pudo iniciar sesión" }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.mipmap.main_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 22.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.brand_logo_pill),
                        contentDescription = "SEÑAL",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(34.dp)
                            .widthIn(max = 140.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        BuildConfig.VERSION_NAME,
                        color = Color.White.copy(0.85f),
                        fontSize = 14.sp
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoginWifi(Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            clock.first,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            clock.second.replaceFirstChar { it.uppercase() },
                            color = Color.White.copy(0.75f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.42f)
                ) {
                    Text(
                        text = "Por favor ingrese la contraseña Y el usuario",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(18.dp))

                    FlujoPillField(
                        value = username,
                        onValueChange = { username = it; error = null },
                        placeholder = "USUARIO",
                        modifier = Modifier.focusRequester(userFocus),
                        imeAction = ImeAction.Next
                    )
                    Spacer(Modifier.height(12.dp))
                    FlujoPillField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        placeholder = "CONTRASEÑA",
                        isPassword = true,
                        imeAction = ImeAction.Done,
                        onDone = { submit() }
                    )
                    Spacer(Modifier.height(16.dp))

                    AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                        error?.let {
                            Column {
                                ErrorMessage(it)
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }

                    FlujoLoginButton(
                        label = when {
                            success -> "ÉXITO"
                            loading -> "CONECTANDO…"
                            else -> "INICIAR SESIÓN"
                        },
                        enabled = !loading && !success,
                        onClick = { submit() }
                    )
                }

                if (success) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(LiveGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Éxito", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FlujoPillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = Color(0xFF1A1A1A),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(BrandOrange),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .border(
                width = if (focused) 2.5.dp else 0.dp,
                color = if (focused) BrandOrange else Color.Transparent,
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 22.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        color = Color(0xFF9AA0A6),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                inner()
            }
        }
    )
}

@Composable
private fun FlujoLoginButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xCC2A2A2A),
            focusedContainerColor = BrandOrange.copy(alpha = 0.85f),
            disabledContainerColor = Color(0x882A2A2A)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, BrandOrangeHot),
                shape = RoundedCornerShape(50)
            )
        )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun LoginWifi(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White
        val cx = size.width / 2f
        val cy = size.height * 0.78f
        drawCircle(c, radius = size.minDimension * 0.08f, center = Offset(cx, cy))
        for (i in 1..3) {
            val r = size.minDimension * (0.18f + i * 0.18f)
            drawArc(
                color = c,
                startAngle = 220f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = Stroke(width = 1.8.dp.toPx())
            )
        }
    }
}
