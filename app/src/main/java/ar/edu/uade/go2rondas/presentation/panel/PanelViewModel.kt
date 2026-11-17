package ar.edu.uade.go2rondas.presentation.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.domain.model.CheckpointResult
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.MissionRecord
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.usecase.GetSiteConfigUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveMissionDetailUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveMissionHistoryUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveRobotSnapshotUseCase
import ar.edu.uade.go2rondas.domain.usecase.SendCommandUseCase
import ar.edu.uade.go2rondas.presentation.common.CommandFeedback
import ar.edu.uade.go2rondas.presentation.common.report
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Qué hojas o diálogos tiene abiertos el operador en el Panel. */
data class PanelDialogs(
    val controlsOpen: Boolean = false,
    val confirmingCancel: Boolean = false
)

data class PanelData(
    val snapshot: RobotSnapshot,
    val site: SiteConfig,
    /** Resultados ya guardados de la ronda en curso, para pintar el plano. */
    val missionResults: List<CheckpointResult>,
    val dialogs: PanelDialogs,
    /** Última ronda guardada, para ofrecer su resumen cuando el robot vuelve a la base. */
    val lastMission: MissionRecord?
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PanelViewModel @Inject constructor(
    observeRobotSnapshot: ObserveRobotSnapshotUseCase,
    getSiteConfig: GetSiteConfigUseCase,
    observeMissionDetail: ObserveMissionDetailUseCase,
    observeMissionHistory: ObserveMissionHistoryUseCase,
    private val sendCommand: SendCommandUseCase
) : ViewModel() {

    private val snapshots: Flow<RobotSnapshot> = observeRobotSnapshot()
    private val dialogs = MutableStateFlow(PanelDialogs())

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
        combine(
            snapshots,
            flow { emit(getSiteConfig()) },
            missionResults,
            dialogs,
            observeMissionHistory().map { it.firstOrNull() }
        ) { snapshot, site, results, open, last ->
            PanelData(snapshot, site, results, open, last)
        }
            .map<PanelData, UiState<PanelData>> { UiState.Success(it) }
            .catch { emit(UiState.Error(it.message ?: "Error")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _feedback = MutableSharedFlow<CommandFeedback>()
    val feedback: SharedFlow<CommandFeedback> = _feedback.asSharedFlow()

    fun onOpenControls() = dialogs.update { it.copy(controlsOpen = true) }
    fun onCloseControls() = dialogs.update { it.copy(controlsOpen = false) }

    fun onPause() = send(CommandType.PAUSE, Command::Pause)
    fun onResume() = send(CommandType.RESUME, Command::Resume)
    fun onReturnToBase() = send(CommandType.RETURN_TO_BASE, Command::ReturnToBase)
    fun onOperationalStop() = send(CommandType.OPERATIONAL_STOP, Command::OperationalStop)

    /** Cancelar pide confirmación (riesgo R2: orden accidental). */
    fun onRequestCancel() = dialogs.update { it.copy(confirmingCancel = true) }
    fun onDismissCancel() = dialogs.update { it.copy(confirmingCancel = false) }
    fun onConfirmCancel() {
        dialogs.update { PanelDialogs() }
        send(CommandType.CANCEL, Command::Cancel)
    }

    private fun send(type: CommandType, build: (String) -> Command) {
        dialogs.update { it.copy(controlsOpen = false) }
        viewModelScope.launch { _feedback.report(type, sendCommand(build)) }
    }
}
