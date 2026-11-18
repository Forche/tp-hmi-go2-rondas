package ar.edu.uade.go2rondas.presentation.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.domain.model.LiveCameraState
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.usecase.ObserveLiveCameraUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveRobotSnapshotUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LiveCameraData(val snapshot: RobotSnapshot, val camera: LiveCameraState)

/** Mientras la pantalla está a la vista, el robot manda imágenes; al salir, deja de mandarlas. */
@HiltViewModel
class LiveCameraViewModel @Inject constructor(
    observeRobotSnapshot: ObserveRobotSnapshotUseCase,
    observeLiveCamera: ObserveLiveCameraUseCase
) : ViewModel() {

    val uiState: StateFlow<UiState<LiveCameraData>> =
        combine(observeRobotSnapshot(), observeLiveCamera()) { snapshot, camera -> LiveCameraData(snapshot, camera) }
            .map<LiveCameraData, UiState<LiveCameraData>> { UiState.Success(it) }
            .catch { emit(UiState.Error(it.message ?: "Error")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), UiState.Loading)
}
