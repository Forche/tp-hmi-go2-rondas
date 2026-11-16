package ar.edu.uade.go2rondas.domain.repository

import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.MissionDetail
import ar.edu.uade.go2rondas.domain.model.MissionRecord
import kotlinx.coroutines.flow.Flow

/** Historial local de rondas y eventos. */
interface HistoryRepository {
    fun observeMissions(): Flow<List<MissionRecord>>
    fun observeMission(missionId: String): Flow<MissionDetail?>
    suspend fun recordEscalation(alert: Alert, missionId: String?)
}
