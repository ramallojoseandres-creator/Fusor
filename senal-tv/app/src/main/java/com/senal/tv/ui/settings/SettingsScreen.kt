package com.senal.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.senal.tv.AppContainer
import com.senal.tv.BuildConfig
import com.senal.tv.data.local.AppSettings
import com.senal.tv.ui.components.FocusableButton
import com.senal.tv.ui.components.SectionHeader
import com.senal.tv.ui.theme.BrandOrange
import com.senal.tv.ui.theme.GraphiteCard
import com.senal.tv.ui.theme.LocalSenalTypography
import com.senal.tv.ui.theme.Teal
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

    // BACK en subpantallas de ajustes → menú de ajustes (luego el home maneja el siguiente).
    BackHandler(enabled = mode != SettingsMode.Menu) {
        error = null
        status = null
        mode = SettingsMode.Menu
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader("AJUSTES", "Cuenta · $userLabel")

        error?.let {
            Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF8A80), style = LocalSenalTypography.current.body)
        }
        status?.let {
            Text(it, color = Teal, style = LocalSenalTypography.current.body)
        }

        when (mode) {
            SettingsMode.Menu -> {
                FocusableButton(
                    label = "Cambiar contraseña",
                    onClick = {
                        error = null; status = null
                        mode = SettingsMode.ChangePassword
                    }
                )

                val lockLabel = when {
                    settings.adultsLocked && !adultsSession ->
                        "Adultos: bloqueados · Desactivar / desbloquear"
                    settings.adultsLocked && adultsSession ->
                        "Adultos: desbloqueados (sesión) · Volver a bloquear / desactivar"
                    else ->
                        "Bloquear contenido de adultos (con clave)"
                }
                FocusableButton(
                    label = lockLabel,
                    onClick = {
                        error = null; status = null
                        mode = if (settings.adultsLocked) SettingsMode.AdultUnlock else SettingsMode.AdultEnable
                    },
                    primary = false
                )

                FocusableButton(
                    label = "Actualizar lista desde servidor",
                    onClick = {
                        scope.launch {
                            error = null
                            status = "Descargando catálogo (manual)…"
                            runCatching { container.playlistSync.refreshFromServer() }
                                .onSuccess { r ->
                                    status = if (r.error != null) {
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
                    primary = false
                )

                FocusableButton(
                    label = "Probar lista Duartegame (pública)",
                    onClick = {
                        scope.launch {
                            error = null
                            status = "Descargando lista de prueba…"
                            runCatching {
                                container.playlistSync.loadFromRemoteM3u()
                            }.onSuccess { r ->
                                if (r.error != null) {
                                    error = r.error
                                    status = null
                                } else {
                                    container.catalogRepository.clearMemory()
                                    status = "Lista de prueba lista · ${r.channels} canales · abre EN VIVO"
                                }
                            }.onFailure {
                                error = it.message ?: "No se pudo cargar la lista de prueba"
                                status = null
                            }
                        }
                    },
                    primary = false
                )

                if (isAdmin) {
                    Spacer(Modifier.height(8.dp))
                    Text("Admin", style = LocalSenalTypography.current.caption, color = BrandOrange)
                    Text(
                        "Auth: ${BuildConfig.API_BASE_URL}",
                        style = LocalSenalTypography.current.caption,
                        color = TextMuted
                    )
                    Text(
                        "Catálogo: 1ª descarga desde servidor → guardada en disco",
                        style = LocalSenalTypography.current.caption,
                        color = Teal
                    )
                    val syncHint = settings.playlistSyncedAt.takeIf { it > 0 }?.let {
                        java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(it))
                    } ?: "nunca"
                    Text(
                        "Última sync: $syncHint · ${settings.playlistChannelCount} ch",
                        style = LocalSenalTypography.current.caption,
                        color = TextMuted
                    )
                    Text(
                        "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = LocalSenalTypography.current.caption,
                        color = TextMuted
                    )
                }

                Spacer(Modifier.height(16.dp))
                FocusableButton(
                    label = "Cerrar sesión",
                    onClick = {
                        scope.launch {
                            container.adultsUnlockedSession.value = false
                            container.authRepository.logout()
                            container.catalogRepository.clearMemory()
                            onLogout()
                        }
                    }
                )
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
    localError?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF8A80)) }

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
    localError?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF8A80)) }

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
    localError?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFFF8A80)) }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("←", "0", " ")
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    when (key) {
                        " " -> Spacer(Modifier.width(72.dp))
                        "←" -> FocusableButton(
                            label = "Borrar",
                            onClick = onDelete,
                            primary = false,
                            modifier = Modifier.width(110.dp)
                        )
                        else -> FocusableButton(
                            label = key,
                            onClick = { onDigit(key) },
                            primary = false,
                            modifier = Modifier.width(72.dp)
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
    Column(modifier = modifier.fillMaxWidth()) {
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
                .background(GraphiteCard, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text("····", color = TextMuted, style = LocalSenalTypography.current.body)
                }
                inner()
            }
        )
    }
}
