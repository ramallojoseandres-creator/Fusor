package com.senal.tv.ui.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import com.senal.tv.AppContainer
import com.senal.tv.BuildConfig
import com.senal.tv.data.local.AppSettings
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.focus.senalFocusable
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.BrandOrangeHot
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LiveGreen
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.NeonBlue
import com.senal.tv.ui.theme.NeonMagenta
import com.senal.tv.ui.theme.NeonPurple
import com.senal.tv.ui.theme.TextMuted
import com.senal.tv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onLogout: () -> Unit
) {
    val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
    val adultsSession by container.adultsUnlockedSession.collectAsState()
    val scope = rememberCoroutineScope()
    val isAdmin = container.tokenStore.isAdmin
    val userLabel = container.tokenStore.cachedUsername.orEmpty().ifBlank { "usuario" }

    var mode by remember { mutableStateOf(SettingsMode.Menu) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = mode != SettingsMode.Menu) {
        error = null
        status = null
        mode = SettingsMode.Menu
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "AJUSTES",
            color = TextPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            letterSpacing = 1.sp
        )
        Text(
            "Cuenta · $userLabel · v${BuildConfig.VERSION_NAME}",
            color = TextMuted,
            fontSize = 13.sp
        )

        error?.let {
            Text(it, color = Color(0xFFFF8A80), style = LocalSenalTypography.current.body)
        }
        status?.let {
            Text(it, color = BrandOrangeHot, style = LocalSenalTypography.current.body)
        }

        when (mode) {
            SettingsMode.Menu -> {
                val adultLabel = when {
                    settings.adultsLocked && !adultsSession -> "Adultos"
                    settings.adultsLocked && adultsSession -> "Adultos ON"
                    else -> "Adultos"
                }
                val tiles = listOf(
                    SettingsTile("App", "Actualizar", BrandOrangeHot) {
                        scope.launch {
                            error = null
                            status = "Buscando APK en el servidor…"
                            when (val st = container.appUpdater.check()) {
                                is com.senal.tv.update.UpdateStatus.Available -> {
                                    status = "Descargando v${st.remoteName}…"
                                    container.appUpdater.downloadAndInstall(st) { p ->
                                        status = "Descargando… ${(p * 100).toInt()}%"
                                    }.onSuccess {
                                        status = "Instala la actualización cuando te lo pida el sistema"
                                    }.onFailure {
                                        error = it.message ?: "No se pudo descargar el APK"
                                        status = null
                                    }
                                }
                                is com.senal.tv.update.UpdateStatus.UpToDate -> {
                                    status = "Ya tienes la última · v${st.localName}"
                                }
                                is com.senal.tv.update.UpdateStatus.Unavailable -> {
                                    error = st.reason
                                    status = null
                                }
                            }
                        }
                    },
                    SettingsTile("Actualizar", "Lista VPS", BrandOrange) {
                        scope.launch {
                            error = null
                            status = "Descargando catálogo…"
                            runCatching { container.playlistSync.refreshFromServer() }
                                .onSuccess { r ->
                                    status = if (r.error != null && r.channels <= 0) {
                                        r.error
                                    } else {
                                        "Lista actualizada · ${r.channels} canales"
                                    }
                                }
                                .onFailure {
                                    error = it.message ?: "No se pudo actualizar la lista"
                                    status = null
                                }
                        }
                    },
                    SettingsTile(adultLabel, "Parental", NeonMagenta) {
                        error = null; status = null
                        mode = if (settings.adultsLocked) SettingsMode.AdultUnlock else SettingsMode.AdultEnable
                    },
                    SettingsTile("Usuario", userLabel.take(12), NeonBlue) {
                        error = null; status = null
                        mode = SettingsMode.ChangePassword
                    },
                    SettingsTile("EPG", "Guía", LiveGreen) {
                        status = "EPG desde lista local"
                    },
                    SettingsTile("Caché", "Disco", NeonPurple) {
                        status = "Catálogo en caché local · ${settings.playlistChannelCount} ch"
                    },
                    SettingsTile("Salir", "Sesión", Color(0xFFE53935)) {
                        scope.launch {
                            container.adultsUnlockedSession.value = false
                            container.authRepository.logout()
                            container.catalogRepository.clearMemory()
                            onLogout()
                        }
                    }
                )

                tiles.chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { tile ->
                            SettingsGridTile(
                                title = tile.title,
                                subtitle = tile.subtitle,
                                accent = tile.accent,
                                onClick = tile.onClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (isAdmin) {
                    Spacer(Modifier.height(8.dp))
                    Text("Admin", color = BrandOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val syncHint = settings.playlistSyncedAt.takeIf { it > 0 }?.let {
                        java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(it))
                    } ?: "nunca"
                    Text(
                        "Última sync: $syncHint · ${settings.playlistChannelCount} ch · v${BuildConfig.VERSION_NAME}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }

            SettingsMode.ChangePassword -> ChangePasswordPanel(
                onCancel = { mode = SettingsMode.Menu },
                onSubmit = { current, next ->
                    scope.launch {
                        error = null
                        status = null
                        container.authRepository.changePassword(current, next)
                            .onSuccess {
                                status = "Contraseña actualizada"
                                mode = SettingsMode.Menu
                            }
                            .onFailure {
                                error = it.message ?: "No se pudo cambiar la contraseña"
                            }
                    }
                }
            )

            SettingsMode.AdultEnable -> AdultPinPanel(
                title = "Crear clave de adultos (4–8 dígitos)",
                confirmLabel = "Activar bloqueo",
                needConfirm = true,
                onCancel = { mode = SettingsMode.Menu },
                onSubmit = { pin, _ ->
                    scope.launch {
                        runCatching {
                            container.settingsStore.enableAdultLock(pin)
                            container.adultsUnlockedSession.value = false
                            status = "Contenido de adultos bloqueado"
                            mode = SettingsMode.Menu
                        }.onFailure {
                            error = it.message ?: "No se pudo activar el bloqueo"
                        }
                    }
                }
            )

            SettingsMode.AdultUnlock -> AdultLockedActions(
                settings = settings,
                onCancel = { mode = SettingsMode.Menu },
                onSessionUnlock = { pin ->
                    if (container.settingsStore.verifyPin(pin, settings.adultPinHash)) {
                        container.adultsUnlockedSession.value = true
                        status = "Adultos visibles en esta sesión"
                        mode = SettingsMode.Menu
                        true
                    } else {
                        error = "Clave incorrecta"
                        false
                    }
                },
                onDisable = { pin ->
                    scope.launch {
                        val ok = container.settingsStore.disableAdultLock(pin)
                        if (ok) {
                            container.adultsUnlockedSession.value = false
                            status = "Bloqueo de adultos desactivado"
                            mode = SettingsMode.Menu
                        } else {
                            error = "Clave incorrecta"
                        }
                    }
                },
                onRelockSession = {
                    container.adultsUnlockedSession.value = false
                    status = "Adultos bloqueados de nuevo"
                    mode = SettingsMode.Menu
                }
            )
        }
    }
}

private data class SettingsTile(
    val title: String,
    val subtitle: String,
    val accent: Color,
    val onClick: () -> Unit
)

@Composable
private fun SettingsGridTile(
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(110.dp)
            .senalFocusable(focused = focused, scaleFocused = 1.06f, cornerRadius = 14.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(0.55f), accent.copy(0.28f), Color(0xFF0A1018))
                    )
                )
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) BrandOrangeHot else Color.White.copy(0.12f),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(12.dp)
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                }
                Column {
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        color = Color.White.copy(0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private enum class SettingsMode { Menu, ChangePassword, AdultEnable, AdultUnlock }

@Composable
private fun ChangePasswordPanel(
    onCancel: () -> Unit,
    onSubmit: (current: String, next: String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    Text("Cambiar contraseña de cuenta", style = LocalSenalTypography.current.title, color = TextPrimary)
    SettingsField("Contraseña actual", current, { current = it }, password = true, modifier = Modifier.focusRequester(focus))
    SettingsField("Nueva contraseña", next, { next = it }, password = true)
    SettingsField("Repetir nueva", confirm, { confirm = it }, password = true)
    localError?.let { Text(it, color = Color(0xFFFF8A80)) }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusableButton(
            label = "Guardar",
            onClick = {
                when {
                    current.isBlank() || next.isBlank() -> localError = "Completa ambos campos"
                    next != confirm -> localError = "La confirmación no coincide"
                    else -> onSubmit(current, next)
                }
            }
        )
        FocusableButton(label = "Cancelar", onClick = onCancel, primary = false)
    }
}

@Composable
private fun AdultPinPanel(
    title: String,
    confirmLabel: String,
    needConfirm: Boolean,
    onCancel: () -> Unit,
    onSubmit: (pin: String, confirm: String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Text(title, style = LocalSenalTypography.current.title, color = TextPrimary)
    Text("Usa el mando numérico o escribe dígitos", style = LocalSenalTypography.current.caption, color = TextMuted)
    SettingsField("Clave", pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it }, password = true, number = true)
    if (needConfirm) {
        SettingsField("Repetir clave", confirm, { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it }, password = true, number = true)
    }
    localError?.let { Text(it, color = Color(0xFFFF8A80)) }

    DigitPad(
        onDigit = { d ->
            if (pin.length < 8) pin += d
        },
        onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusableButton(
            label = confirmLabel,
            onClick = {
                when {
                    pin.length !in 4..8 -> localError = "La clave debe tener 4–8 dígitos"
                    needConfirm && pin != confirm -> localError = "Las claves no coinciden"
                    else -> onSubmit(pin, confirm)
                }
            }
        )
        FocusableButton(label = "Cancelar", onClick = onCancel, primary = false)
    }
}

@Composable
private fun AdultLockedActions(
    settings: AppSettings,
    onCancel: () -> Unit,
    onSessionUnlock: (String) -> Boolean,
    onDisable: (String) -> Unit,
    onRelockSession: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Text("Control parental · adultos", style = LocalSenalTypography.current.title, color = TextPrimary)
    SettingsField("Clave de adultos", pin, { if (it.length <= 8 && it.all(Char::isDigit)) pin = it }, password = true, number = true)
    localError?.let { Text(it, color = Color(0xFFFF8A80)) }
    DigitPad(
        onDigit = { d -> if (pin.length < 8) pin += d },
        onDelete = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
    )

    if (settings.adultsLocked) {
        FocusableButton(
            label = "Desbloquear solo esta sesión",
            onClick = {
                if (!onSessionUnlock(pin)) localError = "Clave incorrecta"
            }
        )
        FocusableButton(
            label = "Desactivar bloqueo (permanente)",
            onClick = {
                if (pin.length !in 4..8) localError = "Introduce la clave"
                else onDisable(pin)
            },
            primary = false
        )
    }
    FocusableButton(label = "Volver a bloquear sesión", onClick = onRelockSession, primary = false)
    FocusableButton(label = "Cancelar", onClick = onCancel, primary = false)
}

@Composable
private fun DigitPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("←", "0", " ")
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    when (key) {
                        " " -> Spacer(Modifier.width(48.dp))
                        "←" -> FocusableButton(
                            label = "Borrar",
                            onClick = onDelete,
                            primary = false,
                            compact = true,
                            modifier = Modifier.width(88.dp)
                        )
                        else -> FocusableButton(
                            label = key,
                            onClick = { onDigit(key) },
                            primary = false,
                            compact = true,
                            modifier = Modifier.width(48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    number: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = LocalSenalTypography.current.caption, color = BrandOrange)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalSenalTypography.current.body.copy(color = TextPrimary),
            cursorBrush = SolidColor(BrandOrange),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    number -> KeyboardType.NumberPassword
                    password -> KeyboardType.Password
                    else -> KeyboardType.Text
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .background(GraphiteCard, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text("····", color = TextMuted, style = LocalSenalTypography.current.body)
                }
                inner()
            }
        )
    }
}
