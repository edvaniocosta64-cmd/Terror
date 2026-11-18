package com.example

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private fun getOrInitFirebaseAuth(context: Context): FirebaseAuth? {
    return try {
        val appCtx = context.applicationContext ?: context
        if (FirebaseApp.getApps(appCtx).isEmpty()) {
            FirebaseApp.initializeApp(appCtx)
        }
        if (FirebaseApp.getApps(appCtx).isNotEmpty()) {
            Firebase.auth
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }
}

class MainActivity : ComponentActivity() {

    private var detectedMaxRefreshHz: Int = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        enableEdgeToEdge()
        configureFullscreenMode()
        detectedMaxRefreshHz = requestHighestDisplayRefreshRate()

        setContent {
            ProjectDarknessTheme {
                AppNavigation(deviceMaxRefreshHz = detectedMaxRefreshHz)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureFullscreenMode()
        }
    }

    private fun configureFullscreenMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
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
    authOverride: FirebaseAuth? = null,
) {
    val context = LocalContext.current
    val auth = remember(context, authOverride) { authOverride ?: getOrInitFirebaseAuth(context) }
    val authFlow = remember(auth) { auth?.authStateFlow() ?: flowOf(null) }
    val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = auth?.currentUser)
    var offlineSession by remember { mutableStateOf(false) }

    val user = currentUser

    if (user == null && !offlineSession) {
        AuthScreen(
            onAuthSuccess = {},
            onContinueOffline = { offlineSession = true },
        )
    } else {
        val activeUserId = user?.uid ?: SaveRepository.OFFLINE_USER_ID
        val isOfflineMode = user == null
        key(activeUserId) {
            AuthenticatedAppRoot(
                currentUserId = activeUserId,
                userEmail = user?.email ?: "Jogador Offline",
                isOfflineMode = isOfflineMode,
                deviceMaxRefreshHz = deviceMaxRefreshHz,
                onSignOutCompleted = { offlineSession = false },
            )
        }
    }
}

@Composable
private fun AuthenticatedAppRoot(
    currentUserId: String,
    userEmail: String?,
    isOfflineMode: Boolean,
    deviceMaxRefreshHz: Int,
    onSignOutCompleted: () -> Unit,
) {
    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val saveRepo = if (isOfflineMode || FirebaseApp.getApps(app).isEmpty()) {
                    SaveRepository(
                        db = null,
                        auth = null,
                        localFilesDir = app.filesDir,
                    )
                } else {
                    val databaseId = app.getString(R.string.firestore_database_id)
                    SaveRepository(
                        db = FirebaseFirestore.getInstance(databaseId),
                        auth = Firebase.auth,
                        localFilesDir = app.filesDir,
                    )
                }
                MainViewModel(
                    saveRepository = saveRepo,
                    settingsRepository = SettingsRepository(app),
                    currentUserId = currentUserId,
                    appContext = app.applicationContext,
                )
            }
        },
    )

    LaunchedEffect(deviceMaxRefreshHz) {
        viewModel.performanceManager.updateDeviceMaxHz(deviceMaxRefreshHz)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.audioEngine.setPaused(true)
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.audioEngine.setPaused(false)
                    viewModel.audioEngine.startAmbient()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
            onSignOutCompleted = onSignOutCompleted,
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
                viewModel.audioEngine.setMenuMode(true)
                viewModel.audioEngine.setPaused(false)
                viewModel.audioEngine.startAmbient()
                viewModel.navigateTo(AppScreen.MENU)
            },
        )
    }
}
