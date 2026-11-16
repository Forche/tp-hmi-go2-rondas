package ar.edu.uade.go2rondas.data.source

import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.RobotEvent
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.model.Telemetry
import kotlinx.coroutines.flow.Flow

/**
 * Una fuente por tipo de robot (simulador local, servidor MuJoCo). El repositorio elige cuál usar
 * según el robot seleccionado. Sólo emite lo que efectivamente llega por el enlace.
 */
interface RobotDataSource {
    val telemetry: Flow<Telemetry>
    val alerts: Flow<List<Alert>>
    val events: Flow<RobotEvent>

    fun start()
    fun stop()
    suspend fun getSiteConfig(): SiteConfig

    /** Suspende hasta que el robot responde. Si la orden se pierde, no vuelve nunca: el timeout lo pone el repositorio. */
    suspend fun send(command: Command): CommandOutcome
}
