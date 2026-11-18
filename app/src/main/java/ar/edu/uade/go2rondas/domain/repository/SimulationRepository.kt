package ar.edu.uade.go2rondas.domain.repository

import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import ar.edu.uade.go2rondas.domain.model.SiteLoadResult
import kotlinx.coroutines.flow.Flow

/** Controles del modo demo. Sólo funcionan con el simulador local. */
interface SimulationRepository {
    fun observeAvailable(): Flow<Boolean>
    fun observeTimeScale(): Flow<Int>
    suspend fun setTimeScale(scale: Int)
    suspend fun trigger(scenario: SimulationScenario)

    /** Con las situaciones aleatorias activas, el simulador provoca escenarios al azar durante la ronda. */
    fun observeRandomSituations(): Flow<Boolean>
    suspend fun setRandomSituations(enabled: Boolean)

    /** Le carga al robot simulado el archivo de sitio indicado (uri de un documento elegido por el operador). */
    suspend fun loadSite(documentUri: String): SiteLoadResult

    /** Vuelve al sitio de ejemplo que trae la app. */
    suspend fun resetSite(): SiteLoadResult
}
