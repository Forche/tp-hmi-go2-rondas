package ar.edu.uade.go2rondas.presentation.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import ar.edu.uade.go2rondas.domain.usecase.ObserveRobotSnapshotUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveSimulationAvailableUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveSimulationTimeScaleUseCase
import ar.edu.uade.go2rondas.domain.usecase.SetSimulationTimeScaleUseCase
import ar.edu.uade.go2rondas.domain.usecase.TriggerSimulationScenarioUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SimulatorData(
    val snapshot: RobotSnapshot,
    val available: Boolean,
    val timeScale: Int
)

sealed interface SimulatorUiEffect {
    data class Triggered(val scenario: SimulationScenario) : SimulatorUiEffect
}

@HiltViewModel
class SimulatorViewModel @Inject constructor(
    observeRobotSnapshot: ObserveRobotSnapshotUseCase,
    observeSimulationAvailable: ObserveSimulationAvailableUseCase,
    observeTimeScale: ObserveSimulationTimeScaleUseCase,
    private val setTimeScale: SetSimulationTimeScaleUseCase,
    private val triggerScenario: TriggerSimulationScenarioUseCase
) : ViewModel() {

    val uiState: StateFlow<UiState<SimulatorData>> =
        combine(observeRobotSnapshot(), observeSimulationAvailable(), observeTimeScale()) { snapshot, available, scale ->
            SimulatorData(snapshot, available, scale)
        }
            .map<SimulatorData, UiState<SimulatorData>> { UiState.Success(it) }
            .catch { emit(UiState.Error(it.message ?: "Error")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _effects = MutableSharedFlow<SimulatorUiEffect>()
    val effects: SharedFlow<SimulatorUiEffect> = _effects.asSharedFlow()

    fun onTimeScaleSelected(scale: Int) {
        viewModelScope.launch { setTimeScale(scale) }
    }

    fun onTrigger(scenario: SimulationScenario) {
        viewModelScope.launch {
            triggerScenario(scenario)
            _effects.emit(SimulatorUiEffect.Triggered(scenario))
        }
    }
}
