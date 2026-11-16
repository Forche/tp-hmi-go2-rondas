package ar.edu.uade.go2rondas.presentation.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.domain.model.CheckpointResult
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.usecase.GetSiteConfigUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveMissionDetailUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveRobotSnapshotUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PanelData(
    val snapshot: RobotSnapshot,
    val site: SiteConfig,
    /** Resultados ya guardados de la ronda en curso, para pintar el plano. */
    val missionResults: List<CheckpointResult>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PanelViewModel @Inject constructor(
    observeRobotSnapshot: ObserveRobotSnapshotUseCase,
    getSiteConfig: GetSiteConfigUseCase,
    observeMissionDetail: ObserveMissionDetailUseCase
) : ViewModel() {

    private val snapshots: Flow<RobotSnapshot> = observeRobotSnapshot()

    // flatMapLatest: cuando cambia la ronda, deja de observar la anterior y pasa a la nueva.
    private val missionResults: Flow<List<CheckpointResult>> = snapshots
        .map { it.telemetry?.mission?.missionId }
        .distinctUntilChanged()
        .flatMapLatest { missionId ->
            if (missionId == null) flowOf(emptyList())
            else observeMissionDetail(missionId).map { it?.checkpoints.orEmpty() }
        }

    /** El estado se deriva de los flujos del robot y de Room; el ViewModel no lo asigna a mano. */
    val uiState: StateFlow<UiState<PanelData>> =
        combine(snapshots, flow { emit(getSiteConfig()) }, missionResults) { snapshot, site, results ->
            PanelData(snapshot, site, results)
        }
            .map<PanelData, UiState<PanelData>> { UiState.Success(it) }
            .catch { emit(UiState.Error(it.message ?: "Error")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}
