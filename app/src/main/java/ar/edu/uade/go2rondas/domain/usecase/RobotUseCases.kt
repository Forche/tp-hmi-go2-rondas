package ar.edu.uade.go2rondas.domain.usecase

import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.Robot
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.repository.RobotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject

class GetAvailableRobotsUseCase @Inject constructor(private val repository: RobotRepository) {
    operator fun invoke(): List<Robot> = repository.getAvailableRobots()
}

class ConnectRobotUseCase @Inject constructor(private val repository: RobotRepository) {
    suspend operator fun invoke(robotId: String) = repository.connect(robotId)
}

class GetSiteConfigUseCase @Inject constructor(private val repository: RobotRepository) {
    suspend operator fun invoke(): SiteConfig = repository.getSiteConfig()
}

/** Estado completo del robot activo, con las órdenes habilitadas ya calculadas. */
class ObserveRobotSnapshotUseCase @Inject constructor(
    private val repository: RobotRepository,
    private val evaluateAvailability: EvaluateCommandAvailabilityUseCase
) {
    operator fun invoke(): Flow<RobotSnapshot> = combine(
        repository.observeActiveRobot(),
        repository.observeConnection(),
        repository.observeTelemetry(),
        repository.observeAlerts(),
        repository.observeLastCommand()
    ) { robot, connection, telemetry, alerts, lastCommand ->
        val thresholds = repository.getSiteConfig().thresholds
        RobotSnapshot(
            robot = robot,
            connection = connection,
            telemetry = telemetry,
            alerts = alerts,
            lastCommand = lastCommand,
            availability = evaluateAvailability(
                telemetry = telemetry,
                connection = connection,
                alerts = alerts,
                lastCommand = lastCommand,
                batteryMinToStartPct = thresholds.batteryMinToStartPct
            )
        )
    }
}

/**
 * Le pone un id único a la orden y la envía. Uso: `sendCommand { id -> Command.Pause(id) }`
 * o, más corto, `sendCommand(Command::Pause)`.
 */
class SendCommandUseCase @Inject constructor(private val repository: RobotRepository) {
    suspend operator fun invoke(build: (id: String) -> Command): CommandOutcome =
        repository.send(build(UUID.randomUUID().toString()))
}
