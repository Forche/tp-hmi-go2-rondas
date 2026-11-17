package ar.edu.uade.go2rondas.presentation.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.core.navigation.Screen
import ar.edu.uade.go2rondas.domain.model.MissionDetail
import ar.edu.uade.go2rondas.domain.model.MissionRecord
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.usecase.ObserveSiteConfigUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveMissionDetailUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveMissionHistoryUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveRobotSnapshotUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryData(val snapshot: RobotSnapshot, val missions: List<MissionRecord>)

/** Lista de rondas: Room vuelve a emitir cada vez que se guarda algo, sin recargar a mano. */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeRobotSnapshot: ObserveRobotSnapshotUseCase,
    observeMissionHistory: ObserveMissionHistoryUseCase
) : ViewModel() {

    val uiState: StateFlow<UiState<HistoryData>> =
        combine(observeRobotSnapshot(), observeMissionHistory()) { snapshot, missions -> HistoryData(snapshot, missions) }
            .map<HistoryData, UiState<HistoryData>> { UiState.Success(it) }
            .catch { emit(UiState.Error(it.message ?: "Error")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}

data class MissionSummaryData(
    val snapshot: RobotSnapshot,
    val site: SiteConfig,
    /** null si la ronda no está en el historial. */
    val detail: MissionDetail?
)

/** Lo comparten el resumen de la ronda y el detalle de un checkpoint: los dos leen la misma ronda. */
@HiltViewModel
class MissionSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRobotSnapshot: ObserveRobotSnapshotUseCase,
    observeSiteConfig: ObserveSiteConfigUseCase,
    observeMissionDetail: ObserveMissionDetailUseCase
) : ViewModel() {

    private val missionId: String = checkNotNull(savedStateHandle[Screen.MissionSummary.ARG_MISSION_ID])
    val checkpointId: String? = savedStateHandle[Screen.CheckpointDetail.ARG_CHECKPOINT_ID]

    val uiState: StateFlow<UiState<MissionSummaryData>> =
        combine(observeRobotSnapshot(), observeSiteConfig(), observeMissionDetail(missionId)) { snapshot, site, detail ->
            MissionSummaryData(snapshot, site, detail)
        }
            .map<MissionSummaryData, UiState<MissionSummaryData>> { UiState.Success(it) }
            .catch { emit(UiState.Error(it.message ?: "Error")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}
