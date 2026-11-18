package ar.edu.uade.go2rondas.data.repository

import android.database.sqlite.SQLiteException
import android.util.Log
import ar.edu.uade.go2rondas.core.coroutines.ApplicationScope
import ar.edu.uade.go2rondas.data.local.HistoryLocalDataSource
import ar.edu.uade.go2rondas.data.mapper.newMissionEntity
import ar.edu.uade.go2rondas.data.mapper.toEntity
import ar.edu.uade.go2rondas.data.remote.MujocoRobotDataSource
import ar.edu.uade.go2rondas.data.sim.SimulatedRobotDataSource
import ar.edu.uade.go2rondas.data.source.RobotDataSource
import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.CommandRecord
import ar.edu.uade.go2rondas.domain.model.CommandStatus
import ar.edu.uade.go2rondas.domain.model.ConnectionStatus
import ar.edu.uade.go2rondas.domain.model.HistoryEvent
import ar.edu.uade.go2rondas.domain.model.HmiTimings
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.MissionResult
import ar.edu.uade.go2rondas.domain.model.Robot
import ar.edu.uade.go2rondas.domain.model.RobotEvent
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
    private val mujoco: MujocoRobotDataSource,
    private val history: HistoryLocalDataSource,
    @param:ApplicationScope private val scope: CoroutineScope
) : RobotRepository {

    private val robots = listOf(
        Robot(id = SimulatedRobotDataSource.ROBOT_ID, name = "Go2-01", source = RobotSource.LOCAL_SIMULATOR),
        Robot(id = MujocoRobotDataSource.ROBOT_ID, name = "Go2-02", source = RobotSource.MUJOCO_SERVER, address = mujoco.address)
    )

    private val activeRobot = MutableStateFlow<Robot?>(null)
    private val telemetry = MutableStateFlow<Telemetry?>(null)
    private val lastReceivedAt = MutableStateFlow<Long?>(null)
    private val alerts = MutableStateFlow<List<Alert>>(emptyList())
    private val lastCommand = MutableStateFlow<CommandRecord?>(null)
    private var activeSource: RobotDataSource? = null
    private var collectJob: Job? = null

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

    init {
        scope.launch { recordConnectionChanges() }
    }

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
        // El simulador arranca de cero con cada proceso: una ronda abierta de antes ya no va a terminar.
        if (robot.source == RobotSource.LOCAL_SIMULATOR) {
            safely { history.closeOpenMissions(robot.id, System.currentTimeMillis(), MissionResult.ABORTED.name) }
        }
        source.start()
        collectJob = scope.launch {
            launch {
                source.telemetry.collect {
                    telemetry.value = it
                    lastReceivedAt.value = System.currentTimeMillis()
                }
            }
            launch { source.alerts.collect { alerts.value = it } }
            launch { source.events.collect { record(it, robot.id) } }
        }
    }

    private fun sourceFor(robot: Robot): RobotDataSource = when (robot.source) {
        RobotSource.LOCAL_SIMULATOR -> simulator
        RobotSource.MUJOCO_SERVER -> mujoco
    }

    override fun observeConnection(): Flow<ConnectionStatus> = connection

    override fun observeTelemetry(): Flow<Telemetry?> = telemetry.asStateFlow()

    override fun observeAlerts(): Flow<List<Alert>> = alerts.asStateFlow()

    override fun observeLastCommand(): Flow<CommandRecord?> = lastCommand.asStateFlow()

    /** El mapa lo pone el robot elegido: cada uno manda el que tiene cargado. */
    private val site: StateFlow<SiteConfig> =
        combine(activeRobot, simulator.site, mujoco.site) { robot, local, remote ->
            if (robot?.source == RobotSource.MUJOCO_SERVER) remote else local
        }.stateIn(scope, SharingStarted.Eagerly, simulator.site.value)

    override fun observeSiteConfig(): Flow<SiteConfig> = site

    override suspend fun getSiteConfig(): SiteConfig = site.value

    /**
     * Corre en el scope de la app: si el operador cambia de pantalla, la orden igual termina
     * en confirmada, rechazada o sin respuesta, y ese estado queda visible.
     */
    override suspend fun send(command: Command): CommandOutcome = scope.async {
        val sentAt = System.currentTimeMillis()
        lastCommand.value = CommandRecord(command, CommandStatus.PENDING, sentAt)
        val timeoutMillis = HmiTimings.COMMAND_TIMEOUT_SECONDS * 1_000L
        val source = activeSource
        val outcome = if (source == null) {
            delay(timeoutMillis)
            CommandOutcome.NoResponse
        } else {
            withTimeoutOrNull(timeoutMillis) { source.send(command) } ?: CommandOutcome.NoResponse
        }
        val record = CommandRecord(
            command = command,
            status = outcome.toStatus(),
            sentAtMillis = sentAt,
            rejectionReason = (outcome as? CommandOutcome.Rejected)?.reason
        )
        lastCommand.value = record
        saveEvent(
            HistoryEvent.CommandSent(sentAt, command.type, record.status, record.rejectionReason),
            telemetry.value?.mission?.missionId
        )
        outcome
    }.await()

    // ---------------------------------------------------------------- historial

    private suspend fun record(event: RobotEvent, robotId: String) = safely {
        when (event) {
            // La ronda queda asociada al sitio que tiene cargado el robot en ese momento.
            is RobotEvent.MissionStarted -> history.insertMission(
                newMissionEntity(event.missionId, robotId, site.value.siteId, event.type, event.total, event.timestampMillis)
            )
            is RobotEvent.CheckpointInspected -> history.insertCheckpointResult(event.result.toEntity())
            is RobotEvent.AlertRaised -> saveEvent(
                HistoryEvent.AlertRaised(
                    event.timestampMillis, event.alert.code, event.alert.severity, event.alert.checkpointId, event.alert.photoId
                ),
                event.missionId
            )
            is RobotEvent.MissionFinished -> {
                if (!history.finishMission(event.missionId, event.timestampMillis, event.result.name)) {
                    Log.w(TAG, "Fin de una ronda que no estaba registrada: ${event.missionId}")
                }
            }
        }
    }

    /** Registra el inicio y el fin de cada corte de conexión, con su duración. */
    private suspend fun recordConnectionChanges() {
        var lostAt: Long? = null
        connection.collect { status ->
            val missionId = telemetry.value?.mission?.missionId
            val lastUpdate = status.lastUpdateMillis
            if (status.state == LinkState.DISCONNECTED && lastUpdate != null && lostAt == null) {
                lostAt = lastUpdate
                saveEvent(HistoryEvent.ConnectionLost(lastUpdate), missionId)
            } else if (status.state != LinkState.DISCONNECTED) {
                lostAt?.let { since ->
                    val now = System.currentTimeMillis()
                    saveEvent(HistoryEvent.ConnectionRestored(now, (now - since) / 1_000), missionId)
                }
                lostAt = null
            }
        }
    }

    private suspend fun saveEvent(event: HistoryEvent, missionId: String?) = safely {
        history.insertEvent(event.toEntity(missionId))
    }

    /** Un error al guardar el historial no debe cortar la telemetría ni las órdenes. */
    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: SQLiteException) {
            Log.e(TAG, "No se pudo guardar en el historial", e)
        }
    }

    private fun connectionStatus(now: Long, lastAt: Long?, current: Telemetry?): ConnectionStatus {
        if (lastAt == null) return ConnectionStatus.Disconnected
        val age = now - lastAt
        val latency = current?.link?.latencyMs
        val state = when {
            age > HmiTimings.STALE_AFTER_SECONDS * 1_000L -> LinkState.DISCONNECTED
            age > DEGRADED_AGE_MILLIS || (latency ?: 0) > HmiTimings.DEGRADED_LATENCY_MS -> LinkState.DEGRADED
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
        const val TAG = "RobotRepository"
        const val DEGRADED_AGE_MILLIS = 2_500L
    }
}
