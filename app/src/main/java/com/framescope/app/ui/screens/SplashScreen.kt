package com.framescope.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.framescope.app.repository.SettingsRepository
import com.framescope.app.i18n.tr
import com.framescope.app.ui.components.SignatureMismatchDialog
import com.framescope.app.ui.components.UpdateDialog
import com.framescope.app.update.AppUpdateInfo
import com.framescope.app.update.DownloadState
import com.framescope.app.update.InstallResult
import com.framescope.app.update.UpdateInstaller
import com.framescope.app.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    val updateRepository: UpdateRepository,
    val updateInstaller: UpdateInstaller
) : ViewModel() {
    val isOnboardingCompleted = settingsRepository.isOnboardingCompleted
    val autoUpdateCheckEnabled = settingsRepository.autoUpdateCheckEnabled
    val downloadState = updateRepository.downloadState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            updateRepository.cleanupStaleUpdateApks()
        }
    }
}

@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()

    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    val autoUpdateEnabled by viewModel.autoUpdateCheckEnabled.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateInfoState by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var signatureErrorMessage by remember { mutableStateOf<String?>(null) }
    var pendingInstallApk by remember { mutableStateOf<File?>(null) }
    var waitingForInstallPermission by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun installDownloadedApk(apkFile: File) {
        pendingInstallApk = apkFile
        scope.launch {
            viewModel.updateInstaller.installApk(apkFile) { installResult ->
                when (installResult) {
                    is InstallResult.PermissionRequired -> {
                        waitingForInstallPermission = true
                        viewModel.updateInstaller.openUnknownAppSourcesSettings()
                    }
                    is InstallResult.SignatureMismatch -> {
                        signatureErrorMessage = installResult.errorMessage
                        updateInfoState = null
                    }
                    else -> Unit
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, pendingInstallApk, waitingForInstallPermission) {
        val observer = LifecycleEventObserver { _, event ->
            val apkFile = pendingInstallApk
            if (
                event == Lifecycle.Event.ON_RESUME &&
                waitingForInstallPermission &&
                apkFile != null &&
                viewModel.updateInstaller.canInstallPackages()
            ) {
                waitingForInstallPermission = false
                installDownloadedApk(apkFile)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun proceedToNextScreen() {
        if (isOnboardingCompleted) {
            onNavigateToDashboard()
        } else {
            onNavigateToOnboarding()
        }
    }

    LaunchedEffect(key1 = true) {
        if (autoUpdateEnabled) {
            isCheckingUpdates = true
            scope.launch(Dispatchers.Main) {
                val result = viewModel.updateRepository.checkForUpdates()
                isCheckingUpdates = false
                result.onSuccess { info ->
                    if (info.isUpdateAvailable) {
                        updateInfoState = info
                    } else {
                        delay(600)
                        proceedToNextScreen()
                    }
                }.onFailure {
                    delay(600)
                    proceedToNextScreen()
                }
            }
            delay(1500)
        } else {
            delay(1500)
            proceedToNextScreen()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = com.framescope.app.R.mipmap.ic_launcher),
                contentDescription = tr("App Logo"),
                modifier = Modifier.size(100.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = tr("FrameScope"),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            Text(
                text = tr("PERFORMANCE SUITE"),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
        }

        // Minimal Update Loader & Footer
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = isCheckingUpdates,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("Checking for updates..."),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = tr("Powered by Shizuku"),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 1.sp
            )
        }

    LaunchedEffect(Unit) {
        com.framescope.app.update.UpdateInstallerBus.installEvents.collect { result ->
            when (result) {
                is InstallResult.SignatureMismatch -> {
                    signatureErrorMessage = result.errorMessage
                    updateInfoState = null
                }
                is InstallResult.PermissionRequired -> {
                    viewModel.updateInstaller.openUnknownAppSourcesSettings()
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Completed) {
            val apkFile = (downloadState as DownloadState.Completed).apkFile
            installDownloadedApk(apkFile)
        }
    }

        // Update Dialog Over Splash
        updateInfoState?.let { info ->
            UpdateDialog(
                updateInfo = info,
                downloadState = downloadState,
                canInstallPackages = { viewModel.updateInstaller.canInstallPackages() },
                onRequestInstallPermission = {
                    viewModel.updateInstaller.openUnknownAppSourcesSettings()
                },
                onDownloadAndInstallClicked = {
                    viewModel.updateRepository.requestDownloadOrResume(info)
                },
                onCancelDownload = {
                    viewModel.updateRepository.resetDownloadState()
                },
                onRemindLaterClicked = {
                    updateInfoState = null
                    viewModel.updateRepository.resetDownloadState()
                    proceedToNextScreen()
                }
            )
        }

        // Signature Mismatch Dialog
        signatureErrorMessage?.let { msg ->
            SignatureMismatchDialog(
                errorMessage = msg,
                onUninstallClicked = {
                    scope.launch {
                        val targetVer = updateInfoState?.versionName ?: com.framescope.app.BuildConfig.VERSION_NAME
                        val apkFile = viewModel.updateRepository.getCachedApkIfValid(targetVer, updateInfoState?.sha256)
                        viewModel.updateInstaller.handleSignatureMismatch(apkFile, targetVer) {
                            signatureErrorMessage = null
                            updateInfoState = null
                            proceedToNextScreen()
                        }
                    }
                },
                onDismiss = {
                    signatureErrorMessage = null
                    proceedToNextScreen()
                }
            )
        }
    }
}
