package ar.edu.uade.go2rondas.domain.usecase

import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.MissionDetail
import ar.edu.uade.go2rondas.domain.model.MissionRecord
import ar.edu.uade.go2rondas.domain.repository.HistoryRepository
import ar.edu.uade.go2rondas.domain.repository.RobotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Las rondas del robot elegido en el sitio que tiene cargado: cada robot tiene su historial y,
 * dentro de él, cada sitio el suyo. Así una ronda vieja nunca se dibuja sobre el plano de otro sitio.
 */
class ObserveMissionHistoryUseCase @Inject constructor(
    private val history: HistoryRepository,
    private val robots: RobotRepository
) {
    operator fun invoke(): Flow<List<MissionRecord>> =
        combine(robots.observeActiveRobot(), robots.observeSiteConfig(), history.observeMissions()) { robot, site, missions ->
            missions.filter { it.robotId == robot?.id && it.siteId == site.siteId }
        }
}

class ObserveMissionDetailUseCase @Inject constructor(private val repository: HistoryRepository) {
    operator fun invoke(missionId: String): Flow<MissionDetail?> = repository.observeMission(missionId)
}

/** Escalar no es una orden al robot: queda registrado para el técnico de guardia. */
class EscalateIncidentUseCase @Inject constructor(private val repository: HistoryRepository) {
    suspend operator fun invoke(alert: Alert, missionId: String?) = repository.recordEscalation(alert, missionId)
}
