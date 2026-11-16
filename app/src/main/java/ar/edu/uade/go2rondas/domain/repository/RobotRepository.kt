package ar.edu.uade.go2rondas.domain.repository

import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.CommandRecord
import ar.edu.uade.go2rondas.domain.model.ConnectionStatus
import ar.edu.uade.go2rondas.domain.model.Robot
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.model.Telemetry
import kotlinx.coroutines.flow.Flow

/**
 * Robot activo: telemetría, alertas y órdenes. La UI no sabe si del otro lado
 * está el simulador local o un servidor remoto.
 */
interface RobotRepository {
    fun getAvailableRobots(): List<Robot>
    fun observeActiveRobot(): Flow<Robot?>
    suspend fun connect(robotId: String)

    fun observeConnection(): Flow<ConnectionStatus>
    fun observeTelemetry(): Flow<Telemetry?>
    fun observeAlerts(): Flow<List<Alert>>
    fun observeLastCommand(): Flow<CommandRecord?>

    suspend fun getSiteConfig(): SiteConfig

    /** Envía la orden y espera la respuesta; si no llega a tiempo devuelve [CommandOutcome.NoResponse]. */
    suspend fun send(command: Command): CommandOutcome
}
