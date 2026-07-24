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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.senal.tv.BuildConfig
import com.senal.tv.R
import com.senal.tv.data.repository.AuthRepository
import com.senal.tv.ui.theme.BrandTurquoise
import com.senal.tv.ui.theme.BrandTurquoiseHot
import com.senal.tv.ui.theme.LiveGreen
import com.senal.tv.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Login SEÑAL: fondo a sangre, marca héroe, campos finos, CTA turquesa.
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
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    LaunchedEffect(Unit) {
        userFocus.requestFocus()
        while (true) {
            clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(20_000)
        }
    }

    fun submit() {
        if (loading || success) return
        if (username.isBlank() || password.isBlank()) {
            error = "Introduce usuario y contraseña"
            return
        }
        scope.launch {
            loading = true
            error = null
            val result = authRepository.login(username, password)
            loading = false
            result.onSuccess {
                success = true
                delay(600)
                onLoggedIn()
            }.onFailure {
                val raw = it.message.orEmpty()
                error = when {
                    raw.contains("Unable to resolve", true) ||
                        raw.contains("Failed to connect", true) ||
                        raw.contains("timeout", true) ||
                        raw.contains("Connection", true) ||
                        raw.contains("UnknownHost", true) ->
                        "Sin conexión con el servidor"
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
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(0.55f),
                        0.35f to Color.Black.copy(0.78f),
                        0.7f to Color.Black.copy(0.88f),
                        1f to Color.Black.copy(0.94f),
                    )
                )
        )

        // Top bar: clock only (brand lives in the hero stack).
        Text(
            text = clock,
            color = Color.White.copy(0.9f),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 40.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.42f)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SEÑAL",
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 10.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Inicia sesión para ver en vivo",
                color = TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(36.dp))

            UnderlineField(
                value = username,
                onValueChange = { username = it; error = null },
                label = "Usuario",
                modifier = Modifier.focusRequester(userFocus),
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(22.dp))
            UnderlineField(
                value = password,
                onValueChange = { password = it; error = null },
                label = "Contraseña",
                isPassword = true,
                imeAction = ImeAction.Done,
                onDone = { submit() }
            )

            Spacer(Modifier.height(18.dp))
            AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                error?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            EnterButton(
                label = when {
                    success -> "Listo"
                    loading -> "Conectando…"
                    else -> "ENTRAR"
                },
                enabled = !loading && !success,
                success = success,
                onClick = { submit() }
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                color = Color.White.copy(0.35f),
                fontSize = 11.sp
            )
        }

        if (success) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✓",
                    color = LiveGreen,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun UnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = if (focused) BrandTurquoise else Color.White.copy(0.55f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(BrandTurquoise),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .border(
                    width = 0.dp,
                    color = Color.Transparent,
                    shape = RoundedCornerShape(0.dp)
                )
                .background(Color.Transparent)
                .padding(vertical = 10.dp),
            decorationBox = { inner ->
                Column {
                    Box(Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                label.lowercase().replaceFirstChar { it.uppercase() },
                                color = Color.White.copy(0.28f),
                                fontSize = 17.sp
                            )
                        }
                        inner()
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (focused) 2.dp else 1.dp)
                            .background(
                                if (focused) BrandTurquoise else Color.White.copy(0.35f)
                            )
                    )
                }
            }
        )
    }
}

@Composable
private fun EnterButton(
    label: String,
    enabled: Boolean,
    success: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val fill = when {
        success -> LiveGreen
        focused -> BrandTurquoiseHot
        else -> BrandTurquoise
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fill.copy(alpha = 0.92f),
            focusedContainerColor = BrandTurquoiseHot,
            disabledContainerColor = BrandTurquoise.copy(alpha = 0.45f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                letterSpacing = 2.sp
            )
        }
    }
}
