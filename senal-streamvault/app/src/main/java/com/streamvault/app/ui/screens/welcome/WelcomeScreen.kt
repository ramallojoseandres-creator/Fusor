package com.streamvault.app.ui.screens.welcome

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.streamvault.app.BuildConfig
import com.streamvault.app.R
import com.streamvault.app.senal.SenalAuthClient
import com.streamvault.app.senal.SenalServerConfig
import com.streamvault.app.ui.components.shell.StatusPill
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.interaction.TvButton
import com.streamvault.app.ui.theme.SurfaceHighlight
import com.streamvault.data.preferences.PreferencesRepository
import com.streamvault.data.sync.SyncProgressBus
import com.streamvault.domain.model.ActiveLiveSource
import com.streamvault.domain.repository.CombinedM3uRepository
import com.streamvault.domain.repository.ProviderRepository
import com.streamvault.domain.sync.Section
import com.streamvault.domain.sync.SyncProgress
import com.streamvault.domain.usecase.M3uProviderSetupCommand
import com.streamvault.domain.usecase.ValidateAndAddProvider
import com.streamvault.domain.usecase.ValidateAndAddProviderResult
import com.streamvault.domain.usecase.XtreamProviderSetupCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val validateAndAddProvider: ValidateAndAddProvider,
    private val senalAuthClient: SenalAuthClient,
    private val preferencesRepository: PreferencesRepository,
    private val combinedM3uRepository: CombinedM3uRepository,
    syncProgressBus: SyncProgressBus,
) : ViewModel() {

    private val _hasProviders = MutableStateFlow<Boolean?>(null)
    val hasProviders: StateFlow<Boolean?> = _hasProviders.asStateFlow()

    private val _loginBusy = MutableStateFlow(false)
    val loginBusy: StateFlow<Boolean> = _loginBusy.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val acceptingProgress = MutableStateFlow(true)

    val syncProgress: StateFlow<SyncProgress?> =
        combine(syncProgressBus.flow, acceptingProgress) { progress, accept ->
            if (accept) progress else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            // Optional Xtream-only seed from local.properties — never auto-seed the public mega-lista.
            maybeSeedXtreamOnly()
            providerRepository.getProviders()
                .map { it.isNotEmpty() }
                .collect { _hasProviders.value = it }
        }
        viewModelScope.launch {
            _hasProviders.filterNotNull().first()
            acceptingProgress.value = false
        }
    }

    private suspend fun maybeSeedXtreamOnly() {
        if (providerRepository.getProviders().first().isNotEmpty()) return
        val xtreamServer = BuildConfig.XTREAM_DEV_SERVER
        val xtreamUser = BuildConfig.XTREAM_DEV_USERNAME
        val xtreamPass = BuildConfig.XTREAM_DEV_PASSWORD
        if (xtreamServer.isBlank() || xtreamUser.isBlank() || xtreamPass.isBlank()) return
        validateAndAddProvider.loginXtream(
            XtreamProviderSetupCommand(
                serverUrl = xtreamServer,
                username = xtreamUser,
                password = xtreamPass,
                name = BuildConfig.XTREAM_DEV_NAME.ifBlank { "Dev (seeded)" },
                xtreamFastSyncEnabled = true,
            ),
        )
    }

    /**
     * Login against the panel (access control), then sync only VPS `downloads/lista.m3u`.
     * Clients cannot attach arbitrary playlist URLs.
     */
    fun loginWithSenal(username: String, password: String) {
        if (_loginBusy.value) return
        viewModelScope.launch {
            _loginBusy.value = true
            _loginError.value = null
            acceptingProgress.value = true
            val result = senalAuthClient.login(username, password)
            result.fold(
                onSuccess = { session ->
                    val add = validateAndAddProvider.addM3u(
                        M3uProviderSetupCommand(
                            url = SenalServerConfig.listaUrl,
                            name = SenalServerConfig.listaName,
                        ),
                    )
                    when (add) {
                        is ValidateAndAddProviderResult.Success,
                        is ValidateAndAddProviderResult.SavedWithWarning -> {
                            // Force single server source — no picker after login.
                            if (!SenalServerConfig.allowClientPlaylistAdd) {
                                preferencesRepository.setShowLiveSourceSwitcher(false)
                                val providerId = when (add) {
                                    is ValidateAndAddProviderResult.Success -> add.provider.id
                                    is ValidateAndAddProviderResult.SavedWithWarning -> add.provider.id
                                    else -> null
                                }
                                if (providerId != null) {
                                    providerRepository.setActiveProvider(providerId)
                                    combinedM3uRepository.setActiveLiveSource(
                                        ActiveLiveSource.ProviderSource(providerId),
                                    )
                                }
                            }
                        }
                        is ValidateAndAddProviderResult.ValidationError -> {
                            _loginError.value = add.message
                            acceptingProgress.value = false
                        }
                        is ValidateAndAddProviderResult.Error -> {
                            _loginError.value = add.message
                            acceptingProgress.value = false
                        }
                    }
                },
                onFailure = {
                    _loginError.value = it.message ?: "No se pudo iniciar sesión"
                    acceptingProgress.value = false
                },
            )
            _loginBusy.value = false
        }
    }
}

@Composable
fun WelcomeScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToSetup: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    // onNavigateToSetup kept for nav graph compatibility; clients cannot add playlists.
    @Suppress("UNUSED_PARAMETER")
    val unusedSetup = onNavigateToSetup
    val hasProviders by viewModel.hasProviders.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val loginBusy by viewModel.loginBusy.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    LaunchedEffect(hasProviders) {
        when (hasProviders) {
            true -> onNavigateToHome()
            false -> Unit
            null -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.22f),
                            AppColors.HeroTop,
                            AppColors.HeroBottom,
                        ),
                    ),
                ),
        )

        when (hasProviders) {
            false -> WelcomeStartCard(
                loginBusy = loginBusy,
                loginError = loginError,
                onLogin = viewModel::loginWithSenal,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )

            else -> WelcomeLoadingCard(
                syncProgress = syncProgress,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            )
        }
    }
}

@Composable
private fun WelcomeLoadingCard(
    syncProgress: SyncProgress?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = SurfaceDefaults.colors(containerColor = AppColors.Surface.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val pillLabel = if (syncProgress != null) {
                stringResource(sectionLabelRes(syncProgress.section))
            } else {
                stringResource(R.string.app_name)
            }
            val pillColor = if (syncProgress != null) {
                sectionColor(syncProgress.section)
            } else {
                AppColors.BrandMuted
            }
            StatusPill(label = pillLabel, containerColor = pillColor)
            Spacer(modifier = Modifier.height(18.dp))
            if (syncProgress == null) {
                CircularProgressIndicator(color = AppColors.Brand)
                Spacer(modifier = Modifier.height(18.dp))
            }
            Text(
                text = stringResource(R.string.welcome_loading_title),
                style = MaterialTheme.typography.titleLarge,
                color = AppColors.TextPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            val subtitle = if (syncProgress != null && syncProgress.currentLabel.isNotBlank()) {
                syncProgress.currentLabel
            } else {
                "Sincronizando lista.m3u del servidor…"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (syncProgress != null) {
                Spacer(modifier = Modifier.height(14.dp))
                if (syncProgress.total > 0) {
                    LinearProgressIndicator(
                        progress = { syncProgress.current.toFloat() / syncProgress.total.toFloat() },
                        modifier = Modifier.width(260.dp),
                        color = AppColors.Brand,
                        trackColor = AppColors.BrandMuted,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.width(260.dp),
                        color = AppColors.Brand,
                        trackColor = AppColors.BrandMuted,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.sync_items_indexed_format,
                        syncProgress.itemsIndexed,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStartCard(
    loginBusy: Boolean,
    loginError: String?,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AppColors.TextPrimary,
        unfocusedTextColor = AppColors.TextPrimary,
        focusedBorderColor = AppColors.Brand,
        unfocusedBorderColor = SurfaceHighlight,
        focusedLabelColor = AppColors.Brand,
        unfocusedLabelColor = AppColors.TextTertiary,
        cursorColor = AppColors.Brand,
    )

    Surface(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = SurfaceDefaults.colors(containerColor = AppColors.Surface.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusPill(
                label = stringResource(R.string.app_name),
                containerColor = AppColors.BrandMuted,
            )
            Text(
                text = "Inicia sesión en SEÑAL",
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = SenalServerConfig.baseUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextTertiary,
            )
            Text(
                text = "Tras el login se carga solo downloads/lista.m3u del VPS (la pone el administrador).",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loginBusy,
                singleLine = true,
                label = { androidx.compose.material3.Text("Usuario") },
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loginBusy,
                singleLine = true,
                label = { androidx.compose.material3.Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            )
            loginError?.let {
                Text(text = it, color = AppColors.Live, style = MaterialTheme.typography.bodyMedium)
            }
            if (loginBusy) {
                CircularProgressIndicator(color = AppColors.Brand)
            }
            TvButton(
                onClick = { onLogin(username, password) },
                enabled = !loginBusy && username.isNotBlank() && password.isNotBlank(),
            ) {
                Text("Entrar a SEÑAL")
            }
        }
    }
}

private fun sectionLabelRes(section: Section): Int = when (section) {
    Section.LIVE -> R.string.sync_section_live
    Section.VOD -> R.string.sync_section_vod
    Section.SERIES -> R.string.sync_section_series
}

private fun sectionColor(section: Section): Color = when (section) {
    Section.LIVE -> AppColors.Brand
    Section.VOD -> AppColors.Success
    Section.SERIES -> AppColors.Warning
}
