package com.example

import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.SaveRepository
import com.example.data.SettingsRepository
import com.example.ui.AuthScreen
import com.example.ui.GameScreen
import com.example.ui.MainMenuScreen
import com.example.ui.SaveSlotsScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.ProjectDarknessTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {

    private var detectedMaxRefreshHz: Int = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        detectedMaxRefreshHz = requestHighestDisplayRefreshRate()

        setContent {
            ProjectDarknessTheme {
                AppNavigation(deviceMaxRefreshHz = detectedMaxRefreshHz)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun requestHighestDisplayRefreshRate(): Int {
        return try {
            val activeDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                windowManager.defaultDisplay
            }
            val modes = activeDisplay?.supportedModes ?: return 60
            val bestMode = modes.maxByOrNull { it.refreshRate } ?: return 60
            val params = window.attributes
            params.preferredDisplayModeId = bestMode.modeId
            window.attributes = params
            bestMode.refreshRate.toInt().coerceIn(30, 144)
        } catch (_: Throwable) {
            60
        }
    }
}

@Composable
fun AppNavigation(
    deviceMaxRefreshHz: Int,
    auth: FirebaseAuth = Firebase.auth,
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedAppRoot(
                currentUserId = user.uid,
                userEmail = user.email,
                deviceMaxRefreshHz = deviceMaxRefreshHz,
            )
        }
    }
}

@Composable
private fun AuthenticatedAppRoot(
    currentUserId: String,
    userEmail: String?,
    deviceMaxRefreshHz: Int,
) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MainViewModel(
                    saveRepository = SaveRepository(db),
                    settingsRepository = SettingsRepository(app),
                    currentUserId = currentUserId,
                )
            }
        },
    )

    LaunchedEffect(deviceMaxRefreshHz) {
        viewModel.performanceManager.updateDeviceMaxHz(deviceMaxRefreshHz)
    }

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val savesState by viewModel.savesState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val saveBanner by viewModel.saveBanner.collectAsStateWithLifecycle()

    // Track whether Settings was opened from Pause Menu or Main Menu
    val openedFromGame = remember { mutableStateOf(false) }

    when (currentScreen) {
        AppScreen.MENU -> MainMenuScreen(
            savesState = savesState,
            userEmail = userEmail,
            onNewGame = {
                openedFromGame.value = false
                viewModel.startNewStory()
            },
            onContinueSave = { save ->
                openedFromGame.value = false
                viewModel.continueFromSave(save)
            },
            onOpenSaves = { viewModel.navigateTo(AppScreen.SAVES) },
            onOpenSettings = {
                openedFromGame.value = false
                viewModel.navigateTo(AppScreen.SETTINGS)
            },
        )

        AppScreen.SAVES -> SaveSlotsScreen(
            savesState = savesState,
            onLoadSave = { save ->
                openedFromGame.value = false
                viewModel.continueFromSave(save)
            },
            onDeleteSave = { slotId -> viewModel.deleteSlot(slotId) },
            onBack = { viewModel.navigateTo(AppScreen.MENU) },
        )

        AppScreen.SETTINGS -> SettingsScreen(
            settings = settings,
            deviceMaxRefreshHz = deviceMaxRefreshHz,
            onSelectFps = viewModel::setTargetFps,
            onSelectQuality = viewModel::setQualityPreference,
            onSensitivityChange = viewModel::setCameraSensitivity,
            onMasterVolumeChange = viewModel::setMasterVolume,
            onAmbientVolumeChange = viewModel::setAmbientVolume,
            onSfxVolumeChange = viewModel::setSfxVolume,
            onShowFpsChange = viewModel::setShowFpsHud,
            onInvertYChange = viewModel::setInvertY,
            onBack = {
                if (openedFromGame.value) {
                    viewModel.gameEngine.isPaused = false
                    viewModel.navigateTo(AppScreen.GAME)
                } else {
                    viewModel.navigateTo(AppScreen.MENU)
                }
            },
        )

        AppScreen.GAME -> GameScreen(
            gameEngine = viewModel.gameEngine,
            performanceManager = viewModel.performanceManager,
            settings = settings,
            telemetry = telemetry,
            saveBanner = saveBanner,
            onTelemetryUpdate = viewModel::updateTelemetry,
            onSaveSlot = { slotId, slotName -> viewModel.saveToSlot(slotId, slotName) },
            onClearSaveBanner = viewModel::clearSaveBanner,
            onOpenSettings = {
                openedFromGame.value = true
                viewModel.navigateTo(AppScreen.SETTINGS)
            },
            onExitToMenu = {
                openedFromGame.value = false
                viewModel.audioEngine.setPaused(true)
                viewModel.navigateTo(AppScreen.MENU)
            },
        )
    }
}
