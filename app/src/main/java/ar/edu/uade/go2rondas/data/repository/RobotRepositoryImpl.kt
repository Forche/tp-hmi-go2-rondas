package ar.edu.uade.go2rondas.data.repository

import ar.edu.uade.go2rondas.core.coroutines.ApplicationScope
import ar.edu.uade.go2rondas.data.sim.DataCenterSite
import ar.edu.uade.go2rondas.data.sim.SimulatedRobotDataSource
import ar.edu.uade.go2rondas.data.source.RobotDataSource
import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.CommandRecord
import ar.edu.uade.go2rondas.domain.model.CommandStatus
import ar.edu.uade.go2rondas.domain.model.ConnectionStatus
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.Robot
import ar.edu.uade.go2rondas.domain.model.RobotSource
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.model.Telemetry
import ar.edu.uade.go2rondas.domain.repository.RobotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RobotRepositoryImpl @Inject constructor(
    private val simulator: SimulatedRobotDataSource,
    @param:ApplicationScope private val scope: CoroutineScope
) : RobotRepository {

    private val robots = listOf(
        Robot(id = SimulatedRobotDataSource.ROBOT_ID, name = "Go2-01", source = RobotSource.LOCAL_SIMULATOR),
        Robot(id = "go2-02", name = "Go2-02", source = RobotSource.MUJOCO_SERVER, address = "192.168.0.20:8765")
    )

    private val activeRobot = MutableStateFlow<Robot?>(null)
    private val telemetry = MutableStateFlow<Telemetry?>(null)
    private val lastReceivedAt = MutableStateFlow<Long?>(null)
    private val alerts = MutableStateFlow<List<Alert>>(emptyList())
    private val lastCommand = MutableStateFlow<CommandRecord?>(null)
    private var activeSource: RobotDataSource? = null
    private var collectJob: Job? = null

    private val thresholds = DataCenterSite.config.thresholds

    private val clockTicks: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    /**
     * La app no recibe un aviso de "me desconecté": deduce el estado por la antigüedad del último dato.
     * Se calcula siempre (Eagerly) porque el historial registra los cortes aunque no haya pantalla abierta.
     */
    private val connection: StateFlow<ConnectionStatus> =
        combine(clockTicks, lastReceivedAt, telemetry) { now, lastAt, current -> connectionStatus(now, lastAt, current) }
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, ConnectionStatus.Disconnected)

    override fun getAvailableRobots(): List<Robot> = robots

    override fun observeActiveRobot(): Flow<Robot?> = activeRobot.asStateFlow()

    override suspend fun connect(robotId: String) {
        val robot = robots.firstOrNull { it.id == robotId } ?: return
        if (activeRobot.value?.id == robotId) return
        collectJob?.cancel()
        activeSource?.stop()
        telemetry.value = null
        lastReceivedAt.value = null
        alerts.value = emptyList()
        lastCommand.value = null
        activeRobot.value = robot

        val source = sourceFor(robot)
        activeSource = source
        if (source == null) return
        source.start()
        collectJob = scope.launch {
            launch {
                source.telemetry.collect {
                    telemetry.value = it
                    lastReceivedAt.value = System.currentTimeMillis()
                }
            }
            launch { source.alerts.collect { alerts.value = it } }
        }
    }

    // El cliente del servidor MuJoCo todavía no está implementado: ese robot queda sin conexión.
    private fun sourceFor(robot: Robot): RobotDataSource? = when (robot.source) {
        RobotSource.LOCAL_SIMULATOR -> simulator
        RobotSource.MUJOCO_SERVER -> null
    }

    override fun observeConnection(): Flow<ConnectionStatus> = connection

    override fun observeTelemetry(): Flow<Telemetry?> = telemetry.asStateFlow()

    override fun observeAlerts(): Flow<List<Alert>> = alerts.asStateFlow()

    override fun observeLastCommand(): Flow<CommandRecord?> = lastCommand.asStateFlow()

    override suspend fun getSiteConfig(): SiteConfig = activeSource?.getSiteConfig() ?: DataCenterSite.config

    /**
     * Corre en el scope de la app: si el operador cambia de pantalla, la orden igual termina
     * en confirmada, rechazada o sin respuesta, y ese estado queda visible.
     */
    override suspend fun send(command: Command): CommandOutcome = scope.async {
        val sentAt = System.currentTimeMillis()
        lastCommand.value = CommandRecord(command, CommandStatus.PENDING, sentAt)
        val timeoutMillis = thresholds.commandTimeoutSeconds * 1_000L
        val source = activeSource
        val outcome = if (source == null) {
            delay(timeoutMillis)
            CommandOutcome.NoResponse
        } else {
            withTimeoutOrNull(timeoutMillis) { source.send(command) } ?: CommandOutcome.NoResponse
        }
        lastCommand.value = CommandRecord(
            command = command,
            status = outcome.toStatus(),
            sentAtMillis = sentAt,
            rejectionReason = (outcome as? CommandOutcome.Rejected)?.reason
        )
        outcome
    }.await()

    private fun connectionStatus(now: Long, lastAt: Long?, current: Telemetry?): ConnectionStatus {
        if (lastAt == null) return ConnectionStatus.Disconnected
        val age = now - lastAt
        val latency = current?.link?.latencyMs
        val state = when {
            age > thresholds.staleAfterSeconds * 1_000L -> LinkState.DISCONNECTED
            age > DEGRADED_AGE_MILLIS || (latency ?: 0) > thresholds.degradedLatencyMs -> LinkState.DEGRADED
            else -> LinkState.CONNECTED
        }
        return ConnectionStatus(state, lastAt, latency.takeIf { state != LinkState.DISCONNECTED })
    }

    private fun CommandOutcome.toStatus() = when (this) {
        CommandOutcome.Confirmed -> CommandStatus.CONFIRMED
        is CommandOutcome.Rejected -> CommandStatus.REJECTED
        CommandOutcome.NoResponse -> CommandStatus.NO_RESPONSE
    }

    private companion object {
        const val DEGRADED_AGE_MILLIS = 2_500L
    }
}
