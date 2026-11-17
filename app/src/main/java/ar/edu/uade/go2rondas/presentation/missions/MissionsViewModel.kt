package ar.edu.uade.go2rondas.presentation.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.usecase.ObserveSiteConfigUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveRobotSnapshotUseCase
import ar.edu.uade.go2rondas.domain.usecase.SendCommandUseCase
import ar.edu.uade.go2rondas.presentation.common.CommandFeedback
import ar.edu.uade.go2rondas.presentation.common.report
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lo que eligió el operador en esta pantalla (convive con los datos del robot). */
data class MissionSelection(
    val type: MissionType = MissionType.FULL_ROUND,
    val spotCheckpointId: String = "CP3",
    val confirming: Boolean = false
)

data class MissionsData(
    val snapshot: RobotSnapshot,
    val site: SiteConfig,
    val selection: MissionSelection
) {
    /** Checkpoints que recorre la misión elegida. */
    val checkpointIds: List<String>
        get() = when (selection.type) {
            MissionType.SPOT_CHECK -> listOf(selection.spotCheckpointId)
            else -> site.missions.firstOrNull { it.type == selection.type }?.checkpointIds.orEmpty()
        }
}

sealed interface MissionsUiEffect {
    data object MissionStarted : MissionsUiEffect
}

@HiltViewModel
class MissionsViewModel @Inject constructor(
    observeRobotSnapshot: ObserveRobotSnapshotUseCase,
    observeSiteConfig: ObserveSiteConfigUseCase,
    private val sendCommand: SendCommandUseCase
) : ViewModel() {

    private val selection = MutableStateFlow(MissionSelection())

    val uiState: StateFlow<UiState<MissionsData>> =
        combine(observeRobotSnapshot(), observeSiteConfig(), selection) { snapshot, site, selected ->
            MissionsData(snapshot, site, selected)
        }
            .map<MissionsData, UiState<MissionsData>> { UiState.Success(it) }
            .catch { emit(UiState.Error(it.message ?: "Error")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _effects = MutableSharedFlow<MissionsUiEffect>()
    val effects: SharedFlow<MissionsUiEffect> = _effects.asSharedFlow()

    private val _feedback = MutableSharedFlow<CommandFeedback>()
    val feedback: SharedFlow<CommandFeedback> = _feedback.asSharedFlow()

    fun onMissionSelected(type: MissionType) = selection.update { it.copy(type = type) }

    fun onSpotCheckpointSelected(checkpointId: String) = selection.update { it.copy(spotCheckpointId = checkpointId) }

    fun onContinue() = selection.update { it.copy(confirming = true) }

    fun onDismissConfirmation() = selection.update { it.copy(confirming = false) }

    fun onStartMission() {
        val chosen = selection.value
        selection.update { it.copy(confirming = false) }
        viewModelScope.launch {
            val outcome = sendCommand { id ->
                Command.StartMission(
                    id = id,
                    mission = chosen.type,
                    checkpointId = chosen.spotCheckpointId.takeIf { chosen.type == MissionType.SPOT_CHECK }
                )
            }
            if (outcome == CommandOutcome.Confirmed) _effects.emit(MissionsUiEffect.MissionStarted)
            else _feedback.report(CommandType.START_MISSION, outcome)
        }
    }
}
