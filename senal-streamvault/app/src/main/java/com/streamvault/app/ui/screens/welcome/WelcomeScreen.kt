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
            maybeSeedDevProvider()
            providerRepository.getProviders()
                .map { it.isNotEmpty() }
                .collect { _hasProviders.value = it }
        }
        viewModelScope.launch {
            _hasProviders.filterNotNull().first()
            acceptingProgress.value = false
        }
    }

    private suspend fun maybeSeedDevProvider() {
        if (providerRepository.getProviders().first().isNotEmpty()) return

        val xtreamServer = BuildConfig.XTREAM_DEV_SERVER
        val xtreamUser = BuildConfig.XTREAM_DEV_USERNAME
        val xtreamPass = BuildConfig.XTREAM_DEV_PASSWORD
        if (xtreamServer.isNotBlank() && xtreamUser.isNotBlank() && xtreamPass.isNotBlank()) {
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
    }

    /**
     * Login al panel → descarga automática de /playlist.m3u con Bearer JWT.
     */
    fun loginWithSenal(username: String, password: String) {
        if (_loginBusy.value) return
        viewModelScope.launch {
            _loginBusy.value = true
            _loginError.value = null
            acceptingProgress.value = true
            val result = senalAuthClient.login(username, password)
            result.fold(
                onSuccess = { login ->
                    val token = login.token
                    if (token.isNullOrBlank()) {
                        _loginError.value = "El servidor no devolvió token"
                        acceptingProgress.value = false
                        _loginBusy.value = false
                        return@fold
                    }
                    val headers = "Authorization: Bearer $token"
                    val add = validateAndAddProvider.addM3u(
                        M3uProviderSetupCommand(
                            url = SenalServerConfig.playlistUrl,
                            name = SenalServerConfig.playlistName,
                            httpHeaders = headers,
                        ),
                    )
                    when (add) {
                        is ValidateAndAddProviderResult.Success,
                        is ValidateAndAddProviderResult.SavedWithWarning -> {
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
    startupReady: Boolean = true,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedSetup = onNavigateToSetup
    @Suppress("UNUSED_PARAMETER")
    val unusedStartupReady = startupReady
    val hasProviders by viewModel.hasProviders.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val loginBusy by viewModel.loginBusy.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    LaunchedEffect(hasProviders, startupReady) {
        when {
            hasProviders == true && startupReady -> onNavigateToHome()
            else -> Unit
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF07111F),
                        Color(0xFF0B1728),
                        Color(0xFF101C30),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (hasProviders) {
            null -> CircularProgressIndicator(color = AppColors.Brand)
            true -> WelcomeLoadingCard(syncProgress = syncProgress)
            false -> WelcomeLoginCard(
                loginBusy = loginBusy,
                loginError = loginError,
                syncProgress = syncProgress,
                onLogin = viewModel::loginWithSenal,
            )
        }
    }
}

@Composable
private fun WelcomeLoadingCard(
    syncProgress: SyncProgress?,
    modifier: Modifier = Modifier,
) {
    val subtitle = when (syncProgress?.section) {
        Section.LIVE -> stringResource(R.string.sync_section_live)
        Section.VOD -> stringResource(R.string.sync_section_vod)
        Section.SERIES -> stringResource(R.string.sync_section_series)
        null -> stringResource(R.string.welcome_loading_subtitle)
    }
    Surface(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(28.dp),
        colors = SurfaceDefaults.colors(containerColor = AppColors.Surface.copy(alpha = 0.9f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusPill(
                label = stringResource(R.string.app_name),
                containerColor = AppColors.BrandMuted,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.welcome_loading_title),
                style = MaterialTheme.typography.headlineMedium,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
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
private fun WelcomeLoginCard(
    loginBusy: Boolean,
    loginError: String?,
    syncProgress: SyncProgress?,
    onLogin: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AppColors.Brand,
        unfocusedBorderColor = AppColors.TextSecondary.copy(alpha = 0.35f),
        focusedLabelColor = AppColors.Brand,
        unfocusedLabelColor = AppColors.TextSecondary,
        cursorColor = AppColors.Brand,
        focusedTextColor = AppColors.TextPrimary,
        unfocusedTextColor = AppColors.TextPrimary,
    )

    Surface(
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .padding(24.dp),
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
                text = stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.headlineMedium,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loginBusy,
                singleLine = true,
                label = { androidx.compose.material3.Text(stringResource(R.string.welcome_username_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                colors = fieldColors,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loginBusy,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { androidx.compose.material3.Text(stringResource(R.string.welcome_password_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                colors = fieldColors,
            )
            if (!loginError.isNullOrBlank()) {
                Text(
                    text = loginError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFF8A80),
                    textAlign = TextAlign.Center,
                )
            }
            if (loginBusy) {
                if (syncProgress != null && syncProgress.total > 0) {
                    LinearProgressIndicator(
                        progress = { syncProgress.current.toFloat() / syncProgress.total.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.Brand,
                        trackColor = AppColors.BrandMuted,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.Brand,
                        trackColor = AppColors.BrandMuted,
                    )
                }
                Text(
                    text = stringResource(R.string.welcome_loading_subtitle),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextSecondary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvButton(
                    onClick = { onLogin(username, password) },
                    enabled = !loginBusy && username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.welcome_login))
                }
            }
            Text(
                text = stringResource(R.string.welcome_version),
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary.copy(alpha = 0.7f),
            )
        }
    }
}
