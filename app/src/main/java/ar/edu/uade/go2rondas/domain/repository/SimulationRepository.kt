package ar.edu.uade.go2rondas.domain.repository

import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import kotlinx.coroutines.flow.Flow

/** Controles del modo demo. Sólo funcionan con el simulador local. */
interface SimulationRepository {
    fun observeAvailable(): Flow<Boolean>
    fun observeTimeScale(): Flow<Int>
    suspend fun setTimeScale(scale: Int)
    suspend fun trigger(scenario: SimulationScenario)
}
