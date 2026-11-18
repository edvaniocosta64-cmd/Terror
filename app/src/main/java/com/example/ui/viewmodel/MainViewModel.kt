package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.HorrorAudioEngine
import com.example.data.GameSave
import com.example.data.GameSettings
import com.example.data.QualityPreference
import com.example.data.SaveRepository
import com.example.data.SettingsRepository
import com.example.data.TargetFpsMode
import com.example.engine3d.AutoQualityController
import com.example.engine3d.HorrorGameEngine
import com.example.engine3d.PerformanceTelemetry
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SavesUiState {
    data object Loading : SavesUiState
    data class Success(val saves: List<GameSave>) : SavesUiState
    data class Error(val message: String) : SavesUiState
}

enum class AppScreen {
    MENU,
    GAME,
    SAVES,
    SETTINGS,
}

class MainViewModel(
    private val saveRepository: SaveRepository,
    private val settingsRepository: SettingsRepository,
    private val currentUserId: String,
) : ViewModel() {

    val audioEngine = HorrorAudioEngine()
    val gameEngine = HorrorGameEngine(audioEngine)
    val performanceManager = AutoQualityController()

    private val _currentScreen = MutableStateFlow(AppScreen.MENU)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _telemetry = MutableStateFlow(PerformanceTelemetry())
    val telemetry: StateFlow<PerformanceTelemetry> = _telemetry.asStateFlow()

    private val _saveBanner = MutableStateFlow<String?>(null)
    val saveBanner: StateFlow<String?> = _saveBanner.asStateFlow()

    val settings: StateFlow<GameSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = GameSettings(),
        )

    val savesState: StateFlow<SavesUiState> = saveRepository.observeSaves(currentUserId)
        .map<List<GameSave>, SavesUiState> { SavesUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w("MainVM", "Failed to observe saves", root)
            emit(SavesUiState.Error(root.message ?: "Erro ao carregar salvamentos."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SavesUiState.Loading,
        )

    init {
        audioEngine.setMenuMode(true)
        audioEngine.setPaused(false)
        audioEngine.startAmbient()
        viewModelScope.launch {
            settings.collect { s ->
                audioEngine.updateVolumes(s.masterVolume, s.ambientVolume, s.sfxVolume)
                performanceManager.configurePreference(s.targetFpsMode, s.qualityPreference)
            }
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun startNewStory() {
        gameEngine.resetNewStory()
        audioEngine.setMenuMode(false)
        audioEngine.setPaused(false)
        audioEngine.startAmbient()
        _currentScreen.value = AppScreen.GAME
    }

    fun continueFromSave(save: GameSave) {
        gameEngine.restoreFromSave(save)
        audioEngine.setMenuMode(false)
        audioEngine.setPaused(false)
        audioEngine.startAmbient()
        _currentScreen.value = AppScreen.GAME
    }

    fun updateTelemetry(newTelemetry: PerformanceTelemetry) {
        _telemetry.value = newTelemetry
    }

    fun saveToSlot(slotId: String, slotName: String) {
        viewModelScope.launch {
            val payload = gameEngine.toGameSave(currentUserId, slotId, slotName)
            val result = saveRepository.saveGame(currentUserId, slotId, payload)
            _saveBanner.value = if (result.isSuccess) {
                "Jogo salvo em $slotName!"
            } else {
                "Falha ao salvar: ${result.exceptionOrNull()?.localizedMessage ?: "verifique sua conexão"}"
            }
        }
    }

    fun deleteSlot(slotId: String) {
        viewModelScope.launch {
            val result = saveRepository.deleteSave(currentUserId, slotId)
            if (result.isFailure) {
                _saveBanner.value = "Erro ao apagar salvamento."
            }
        }
    }

    fun clearSaveBanner() {
        _saveBanner.value = null
    }

    fun setTargetFps(mode: TargetFpsMode) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(targetFpsMode = mode) }
        }
    }

    fun setQualityPreference(pref: QualityPreference) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(qualityPreference = pref) }
        }
    }

    fun setCameraSensitivity(value: Float) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(cameraSensitivity = value) }
        }
    }

    fun setMasterVolume(value: Float) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(masterVolume = value) }
        }
    }

    fun setAmbientVolume(value: Float) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(ambientVolume = value) }
        }
    }

    fun setSfxVolume(value: Float) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(sfxVolume = value) }
        }
    }

    fun setShowFpsHud(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showFpsHud = show) }
        }
    }

    fun setInvertY(invert: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(invertY = invert) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
    }
}
