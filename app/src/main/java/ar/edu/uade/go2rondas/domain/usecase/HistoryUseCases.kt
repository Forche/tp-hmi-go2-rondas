package ar.edu.uade.go2rondas.domain.usecase

import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.MissionDetail
import ar.edu.uade.go2rondas.domain.model.MissionRecord
import ar.edu.uade.go2rondas.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMissionHistoryUseCase @Inject constructor(private val repository: HistoryRepository) {
    operator fun invoke(): Flow<List<MissionRecord>> = repository.observeMissions()
}

class ObserveMissionDetailUseCase @Inject constructor(private val repository: HistoryRepository) {
    operator fun invoke(missionId: String): Flow<MissionDetail?> = repository.observeMission(missionId)
}

/** Escalar no es una orden al robot: queda registrado para el técnico de guardia. */
class EscalateIncidentUseCase @Inject constructor(private val repository: HistoryRepository) {
    suspend operator fun invoke(alert: Alert, missionId: String?) = repository.recordEscalation(alert, missionId)
}
