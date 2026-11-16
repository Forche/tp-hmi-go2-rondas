package ar.edu.uade.go2rondas.data.repository

import ar.edu.uade.go2rondas.data.sim.SimulatedRobotDataSource
import ar.edu.uade.go2rondas.domain.model.RobotSource
import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import ar.edu.uade.go2rondas.domain.repository.RobotRepository
import ar.edu.uade.go2rondas.domain.repository.SimulationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimulationRepositoryImpl @Inject constructor(
    private val simulator: SimulatedRobotDataSource,
    private val robotRepository: RobotRepository
) : SimulationRepository {

    override fun observeAvailable(): Flow<Boolean> =
        robotRepository.observeActiveRobot().map { it?.source == RobotSource.LOCAL_SIMULATOR }

    override fun observeTimeScale(): Flow<Int> = simulator.timeScale

    override suspend fun setTimeScale(scale: Int) = simulator.setTimeScale(scale)

    override suspend fun trigger(scenario: SimulationScenario) = simulator.trigger(scenario)
}
