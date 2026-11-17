package ar.edu.uade.go2rondas.domain.usecase

import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.Robot
import ar.edu.uade.go2rondas.domain.repository.RobotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

data class NewAlert(val robot: Robot, val alert: Alert)

/**
 * Cada alerta sin reconocer que aparece por primera vez en el robot activo, una sola vez.
 * La lista de alertas llega completa cada vez que cambia; esto se queda sólo con las nuevas.
 */
class ObserveNewAlertsUseCase @Inject constructor(private val repository: RobotRepository) {
    operator fun invoke(): Flow<NewAlert> = flow {
        var robotId: String? = null
        val seen = mutableSetOf<String>()
        combine(repository.observeActiveRobot(), repository.observeAlerts()) { robot, alerts -> robot to alerts }
            .collect { (robot, alerts) ->
                if (robot?.id != robotId) {
                    robotId = robot?.id
                    seen.clear()
                }
                if (robot == null) return@collect
                alerts.forEach { alert ->
                    // Con la hora: si el servidor se reinicia, los ids vuelven a empezar.
                    if (seen.add("${alert.id}@${alert.timestampMillis}") && !alert.acknowledged) {
                        emit(NewAlert(robot, alert))
                    }
                }
            }
    }
}
