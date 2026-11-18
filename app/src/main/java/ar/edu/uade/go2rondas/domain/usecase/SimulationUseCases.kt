package ar.edu.uade.go2rondas.domain.usecase

import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import ar.edu.uade.go2rondas.domain.model.SiteLoadResult
import ar.edu.uade.go2rondas.domain.repository.SimulationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSimulationAvailableUseCase @Inject constructor(private val repository: SimulationRepository) {
    operator fun invoke(): Flow<Boolean> = repository.observeAvailable()
}

class ObserveSimulationTimeScaleUseCase @Inject constructor(private val repository: SimulationRepository) {
    operator fun invoke(): Flow<Int> = repository.observeTimeScale()
}

class SetSimulationTimeScaleUseCase @Inject constructor(private val repository: SimulationRepository) {
    suspend operator fun invoke(scale: Int) = repository.setTimeScale(scale)
}

class TriggerSimulationScenarioUseCase @Inject constructor(private val repository: SimulationRepository) {
    suspend operator fun invoke(scenario: SimulationScenario) = repository.trigger(scenario)
}

class ObserveRandomSituationsUseCase @Inject constructor(private val repository: SimulationRepository) {
    operator fun invoke(): Flow<Boolean> = repository.observeRandomSituations()
}

class SetRandomSituationsUseCase @Inject constructor(private val repository: SimulationRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setRandomSituations(enabled)
}

class LoadSimulatorSiteUseCase @Inject constructor(private val repository: SimulationRepository) {
    suspend operator fun invoke(documentUri: String): SiteLoadResult = repository.loadSite(documentUri)
}

class ResetSimulatorSiteUseCase @Inject constructor(private val repository: SimulationRepository) {
    suspend operator fun invoke(): SiteLoadResult = repository.resetSite()
}
