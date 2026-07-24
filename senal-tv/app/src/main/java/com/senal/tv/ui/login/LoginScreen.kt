package com.senal.tv.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.senal.tv.BuildConfig
import com.senal.tv.data.repository.AuthRepository
import com.senal.tv.ui.components.BrandMark
import com.senal.tv.ui.components.ErrorMessage
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.LoadingPulse
import com.senal.tv.ui.components.SenalBackground
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoggedIn: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var serverStatus by remember { mutableStateOf("Comprobando servidor…") }
    var serverOk by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    val userFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        authRepository.health()
            .onSuccess { health ->
                serverOk = health.isHealthy()
                val version = health.version?.takeIf { it.isNotBlank() }?.let { " v$it" }.orEmpty()
                serverStatus = if (health.isHealthy()) {
                    "Servidor SEÑAL listo$version"
                } else {
                    health.message ?: health.error ?: "Servidor con avisos$version"
                }
            }
            .onFailure {
                serverOk = false
                serverStatus = it.message ?: "Sin conexión con el VPS"
            }
    }

    LaunchedEffect(Unit) {
        delay(64)
        runCatching { userFocus.requestFocus() }
    }

    fun submit() {
        if (loading) return
        if (username.isBlank() || password.isBlank()) {
            error = "Introduce usuario y contraseña"
            return
        }
        scope.launch {
            loading = true
            error = null
            if (serverOk == false) {
                authRepository.health().onSuccess {
                    serverOk = it.isHealthy()
                    serverStatus = "Servidor SEÑAL listo" +
                        (it.version?.let { v -> " v$v" }.orEmpty())
                }
            }
            val result = authRepository.login(username, password)
            loading = false
            result.onSuccess { onLoggedIn() }
                .onFailure { error = it.message ?: "No se pudo iniciar sesión" }
        }
    }

    SenalBackground {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(540.dp)
                    .background(GraphiteCard.copy(alpha = 0.94f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 36.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandMark()
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Acceso TV · panel SEÑAL",
                    style = LocalSenalTypography.current.subtitle
                )
                Spacer(Modifier.height(14.dp))
                ServerStatusRow(label = serverStatus, ok = serverOk)
                Spacer(Modifier.height(22.dp))

                TvTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    label = "Usuario",
                    focusRequester = userFocus,
                    imeAction = ImeAction.Next,
                    enabled = !loading
                )
                Spacer(Modifier.height(14.dp))
                TvTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "Contraseña",
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onDone = { submit() },
                    enabled = !loading
                )
                Spacer(Modifier.height(18.dp))

                AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
                    error?.let {
                        Column {
                            ErrorMessage(it)
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                }

                AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingPulse("Conectando con SEÑAL…")
                        Spacer(Modifier.height(18.dp))
                    }
                }

                FocusableButton(
                    label = if (loading) "Conectando…" else "Entrar",
                    onClick = { submit() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "JWT + X-Device-Id · ${BuildConfig.VERSION_NAME}",
                    style = LocalSenalTypography.current.caption,
                    color = TextMuted.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun ServerStatusRow(label: String, ok: Boolean?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorField, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = when (ok) {
                        true -> Teal
                        false -> BrandOrange
                        null -> TextMuted
                    },
                    shape = CircleShape
                )
        )
        Text(
            text = label,
            style = LocalSenalTypography.current.caption,
            color = TextPrimary
        )
    }
}

@Composable
private fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = LocalSenalTypography.current.caption, color = BrandOrange)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = LocalSenalTypography.current.body.copy(color = TextPrimary),
            cursorBrush = SolidColor(Teal),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .fillMaxWidth()
                .background(ColorField, RoundedCornerShape(16.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(text = label, color = TextMuted, style = LocalSenalTypography.current.body)
                    }
                    inner()
                }
            }
        )
    }
}

private val ColorField = androidx.compose.ui.graphics.Color(0xFF101018)
