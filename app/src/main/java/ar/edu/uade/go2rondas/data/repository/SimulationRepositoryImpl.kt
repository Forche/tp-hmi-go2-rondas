package ar.edu.uade.go2rondas.data.repository

import android.net.Uri
import ar.edu.uade.go2rondas.data.sim.SimulatedRobotDataSource
import ar.edu.uade.go2rondas.data.site.SiteFileStore
import ar.edu.uade.go2rondas.domain.model.RobotSource
import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import ar.edu.uade.go2rondas.domain.model.SiteLoadResult
import ar.edu.uade.go2rondas.domain.repository.RobotRepository
import ar.edu.uade.go2rondas.domain.repository.SimulationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimulationRepositoryImpl @Inject constructor(
    private val simulator: SimulatedRobotDataSource,
    private val robotRepository: RobotRepository,
    private val siteStore: SiteFileStore
) : SimulationRepository {

    override fun observeAvailable(): Flow<Boolean> =
        robotRepository.observeActiveRobot().map { it?.source == RobotSource.LOCAL_SIMULATOR }

    override fun observeTimeScale(): Flow<Int> = simulator.timeScale

    override suspend fun setTimeScale(scale: Int) = simulator.setTimeScale(scale)

    override suspend fun trigger(scenario: SimulationScenario) = simulator.trigger(scenario)

    override suspend fun loadSite(documentUri: String): SiteLoadResult = withContext(Dispatchers.IO) {
        simulator.loadSite { siteStore.importSimulatorSite(SimulatedRobotDataSource.ROBOT_ID, Uri.parse(documentUri)) }
    }

    override suspend fun resetSite(): SiteLoadResult = withContext(Dispatchers.IO) {
        simulator.loadSite { siteStore.resetSimulatorSite(SimulatedRobotDataSource.ROBOT_ID) }
    }
}
