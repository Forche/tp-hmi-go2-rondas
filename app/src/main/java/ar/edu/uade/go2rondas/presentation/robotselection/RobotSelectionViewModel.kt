package ar.edu.uade.go2rondas.presentation.robotselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.go2rondas.domain.model.Robot
import ar.edu.uade.go2rondas.domain.usecase.ConnectRobotUseCase
import ar.edu.uade.go2rondas.domain.usecase.GetAvailableRobotsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RobotSelectionUiState(
    val robots: List<Robot> = emptyList(),
    val connectingRobotId: String? = null
)

/** Navegar es un evento de una sola vez: va por SharedFlow, no por el estado (al girar no se repite). */
sealed interface RobotSelectionUiEffect {
    data object OpenPanel : RobotSelectionUiEffect
}

@HiltViewModel
class RobotSelectionViewModel @Inject constructor(
    getAvailableRobots: GetAvailableRobotsUseCase,
    private val connectRobot: ConnectRobotUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RobotSelectionUiState(robots = getAvailableRobots()))
    val uiState: StateFlow<RobotSelectionUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<RobotSelectionUiEffect>()
    val effects: SharedFlow<RobotSelectionUiEffect> = _effects.asSharedFlow()

    fun onRobotSelected(robotId: String) {
        if (_uiState.value.connectingRobotId != null) return
        _uiState.update { it.copy(connectingRobotId = robotId) }
        viewModelScope.launch {
            connectRobot(robotId)
            _uiState.update { it.copy(connectingRobotId = null) }
            _effects.emit(RobotSelectionUiEffect.OpenPanel)
        }
    }
}
