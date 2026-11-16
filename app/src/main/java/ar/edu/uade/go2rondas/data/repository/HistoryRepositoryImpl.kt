package ar.edu.uade.go2rondas.data.repository

import ar.edu.uade.go2rondas.data.local.HistoryLocalDataSource
import ar.edu.uade.go2rondas.data.mapper.toDomain
import ar.edu.uade.go2rondas.data.mapper.toEntity
import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.HistoryEvent
import ar.edu.uade.go2rondas.domain.model.MissionDetail
import ar.edu.uade.go2rondas.domain.model.MissionRecord
import ar.edu.uade.go2rondas.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val local: HistoryLocalDataSource
) : HistoryRepository {

    override fun observeMissions(): Flow<List<MissionRecord>> =
        local.observeMissions().map { rows -> rows.map { it.toDomain() } }

    override fun observeMission(missionId: String): Flow<MissionDetail?> = combine(
        local.observeMission(missionId),
        local.observeCheckpoints(missionId),
        local.observeEvents(missionId)
    ) { row, checkpoints, events ->
        row?.let {
            MissionDetail(
                record = it.toDomain(),
                checkpoints = checkpoints.map { result -> result.toDomain() },
                events = events.mapNotNull { event -> event.toDomain() }
            )
        }
    }

    override suspend fun recordEscalation(alert: Alert, missionId: String?) {
        val event = HistoryEvent.IncidentEscalated(System.currentTimeMillis(), alert.code, alert.checkpointId)
        local.insertEvent(event.toEntity(missionId))
    }
}
