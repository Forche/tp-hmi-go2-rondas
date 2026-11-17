package ar.edu.uade.go2rondas.presentation.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.usecase.GetSiteConfigUseCase
import ar.edu.uade.go2rondas.domain.usecase.ObserveRobotSnapshotUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AlertsData(val snapshot: RobotSnapshot, val site: SiteConfig)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    observeRobotSnapshot: ObserveRobotSnapshotUseCase,
    getSiteConfig: GetSiteConfigUseCase
) : ViewModel() {

    val uiState: StateFlow<UiState<AlertsData>> =
        combine(observeRobotSnapshot(), flow { emit(getSiteConfig()) }) { snapshot, site -> AlertsData(snapshot, site) }
            .map<AlertsData, UiState<AlertsData>> { UiState.Success(it) }
            .catch { emit(UiState.Error(it.message ?: "Error")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)
}
