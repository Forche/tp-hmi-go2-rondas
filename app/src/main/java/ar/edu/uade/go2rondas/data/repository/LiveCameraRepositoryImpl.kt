package ar.edu.uade.go2rondas.data.repository

import ar.edu.uade.go2rondas.data.remote.MujocoRobotDataSource
import ar.edu.uade.go2rondas.domain.model.LiveCameraState
import ar.edu.uade.go2rondas.domain.model.RobotSource
import ar.edu.uade.go2rondas.domain.repository.LiveCameraRepository
import ar.edu.uade.go2rondas.domain.repository.RobotRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Sólo el robot del servidor MuJoCo tiene cámara; el simulador de la app no. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveCameraRepositoryImpl @Inject constructor(
    private val robots: RobotRepository,
    private val mujoco: MujocoRobotDataSource
) : LiveCameraRepository {

    override fun observe(): Flow<LiveCameraState> = robots.observeActiveRobot().flatMapLatest { robot ->
        if (robot?.source == RobotSource.MUJOCO_SERVER) {
            mujoco.liveCamera().map { it ?: LiveCameraState.Waiting }
        } else {
            flowOf(LiveCameraState.NoCamera)
        }
    }
}
