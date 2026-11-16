package ar.edu.uade.go2rondas.data.local

import ar.edu.uade.go2rondas.data.local.dao.CheckpointResultDao
import ar.edu.uade.go2rondas.data.local.dao.EventDao
import ar.edu.uade.go2rondas.data.local.dao.MissionDao
import ar.edu.uade.go2rondas.data.local.entity.CheckpointResultEntity
import ar.edu.uade.go2rondas.data.local.entity.EventEntity
import ar.edu.uade.go2rondas.data.local.entity.MissionEntity
import ar.edu.uade.go2rondas.data.local.entity.MissionSummaryRow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuente local del historial. La usan dos repositorios: el del robot escribe lo que pasa
 * durante la ronda y el del historial lo lee.
 */
@Singleton
class HistoryLocalDataSource @Inject constructor(
    private val missionDao: MissionDao,
    private val checkpointResultDao: CheckpointResultDao,
    private val eventDao: EventDao
) {
    fun observeMissions(): Flow<List<MissionSummaryRow>> = missionDao.observeAll()

    fun observeMission(missionId: String): Flow<MissionSummaryRow?> = missionDao.observeById(missionId)

    fun observeCheckpoints(missionId: String): Flow<List<CheckpointResultEntity>> =
        checkpointResultDao.observeByMission(missionId)

    fun observeEvents(missionId: String): Flow<List<EventEntity>> = eventDao.observeByMission(missionId)

    suspend fun insertMission(mission: MissionEntity) = missionDao.insert(mission)

    /** Devuelve false si la ronda no existía (0 filas actualizadas). */
    suspend fun finishMission(missionId: String, endedAt: Long, result: String): Boolean =
        missionDao.finish(missionId, endedAt, result) > 0

    suspend fun insertCheckpointResult(result: CheckpointResultEntity) = checkpointResultDao.insert(result)

    suspend fun insertEvent(event: EventEntity) = eventDao.insert(event)
}
