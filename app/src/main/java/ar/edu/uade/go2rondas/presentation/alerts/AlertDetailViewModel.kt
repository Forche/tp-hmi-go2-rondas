package ar.edu.uade.go2rondas.presentation.alerts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.core.navigation.Screen
import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.IncidentDecision
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.usecase.EscalateIncidentUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObservePhotoUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveSiteConfigUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveRobotSnapshotUseCase
import ar.edu.uade.go2rondas.domain.usecase.SendCommandUseCase
import ar.edu.uade.go2rondas.presentation.common.CommandFeedback
import ar.edu.uade.go2rondas.presentation.common.report
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertDetailData(
    val snapshot: RobotSnapshot,
    val site: SiteConfig,
    /** null si la alerta ya no está en la sesión del robot. */
    val alert: Alert?,
    /** Archivo de la foto de la cámara del robot, si la mandó. */
    val photoPath: String? = null
)

sealed interface AlertDetailUiEffect {
    data object BackToPanel : AlertDetailUiEffect
    data object Escalated : AlertDetailUiEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlertDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRobotSnapshot: ObserveRobotSnapshotUseCase,
    observeSiteConfig: ObserveSiteConfigUseCase,
    observePhoto: ObservePhotoUseCase,
    private val sendCommand: SendCommandUseCase,
    private val escalateIncident: EscalateIncidentUseCase
) : ViewModel() {

    // El id llega como argumento de la ruta; SavedStateHandle lo conserva si el proceso muere.
    private val alertId: String = checkNotNull(savedStateHandle[Screen.AlertDetail.ARG_ALERT_ID])

    private val alertPhoto = observeRobotSnapshot()
        .map { snapshot -> snapshot.alerts.firstOrNull { it.id == alertId } }
        .distinctUntilChanged { a, b -> a?.photoId == b?.photoId }
        .flatMapLatest { observePhoto.ofAlert(it) }

    val uiState: StateFlow<UiState<AlertDetailData>> =
        combine(observeRobotSnapshot(), observeSiteConfig(), alertPhoto) { snapshot, site, photo ->
            AlertDetailData(snapshot, site, snapshot.alerts.firstOrNull { it.id == alertId }, photo)
        }
            .map<AlertDetailData, UiState<AlertDetailData>> { UiState.Success(it) }
            .catch { emit(UiState.Error(it.message ?: "Error")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _effects = MutableSharedFlow<AlertDetailUiEffect>()
    val effects: SharedFlow<AlertDetailUiEffect> = _effects.asSharedFlow()

    private val _feedback = MutableSharedFlow<CommandFeedback>()
    val feedback: SharedFlow<CommandFeedback> = _feedback.asSharedFlow()

    fun onAcknowledge() {
        viewModelScope.launch {
            _feedback.report(CommandType.ACKNOWLEDGE_ALERT, sendCommand { Command.AcknowledgeAlert(it, alertId) })
        }
    }

    fun onDecide(decision: IncidentDecision) {
        viewModelScope.launch {
            val outcome = sendCommand { Command.DecideIncident(it, alertId, decision) }
            // Vigilar deja al robot en el punto: el operador sigue en esta pantalla viendo las lecturas.
            if (outcome == CommandOutcome.Confirmed && decision != IncidentDecision.WATCH) {
                _effects.emit(AlertDetailUiEffect.BackToPanel)
            } else {
                _feedback.report(CommandType.DECIDE_INCIDENT, outcome)
            }
        }
    }

    fun onReturnToBase() {
        viewModelScope.launch {
            val outcome = sendCommand(Command::ReturnToBase)
            if (outcome == CommandOutcome.Confirmed) _effects.emit(AlertDetailUiEffect.BackToPanel)
            else _feedback.report(CommandType.RETURN_TO_BASE, outcome)
        }
    }

    fun onEscalate() {
        val data = (uiState.value as? UiState.Success)?.data ?: return
        val alert = data.alert ?: return
        viewModelScope.launch {
            escalateIncident(alert, data.snapshot.telemetry?.mission?.missionId)
            _effects.emit(AlertDetailUiEffect.Escalated)
        }
    }
}
