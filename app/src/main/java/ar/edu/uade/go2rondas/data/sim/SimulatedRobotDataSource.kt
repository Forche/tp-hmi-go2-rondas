package ar.edu.uade.go2rondas.data.sim

import ar.edu.uade.go2rondas.core.coroutines.ApplicationScope
import ar.edu.uade.go2rondas.data.site.InvalidSiteException
import ar.edu.uade.go2rondas.data.site.SiteDefinition
import ar.edu.uade.go2rondas.data.site.SiteFileStore
import ar.edu.uade.go2rondas.data.source.RobotDataSource
import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.Level
import ar.edu.uade.go2rondas.domain.model.LinkQuality
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.domain.model.RejectionReason
import ar.edu.uade.go2rondas.domain.model.RobotEvent
import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import ar.edu.uade.go2rondas.domain.model.SiteLoadResult
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.domain.model.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Robot simulado dentro del APK. Corre el [RobotSimulator] una vez por segundo y simula el enlace:
 * latencia de las respuestas, cortes de conexión (los eventos se guardan y se envían al reconectar)
 * y órdenes rechazadas o perdidas a pedido del modo demo.
 */
@Singleton
class SimulatedRobotDataSource @Inject constructor(
    @param:ApplicationScope private val scope: CoroutineScope,
    private val siteStore: SiteFileStore
) : RobotDataSource {

    private val mutex = Mutex()
    private val _site = MutableStateFlow(siteStore.loadSimulatorSite(ROBOT_ID))
    private var engine = newEngine(_site.value)

    private val _telemetry = MutableSharedFlow<Telemetry>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _alerts = MutableStateFlow<List<Alert>>(emptyList())
    private val _events = MutableSharedFlow<RobotEvent>(extraBufferCapacity = 64)
    private val _timeScale = MutableStateFlow(DEFAULT_TIME_SCALE)

    private val queuedEvents = mutableListOf<RobotEvent>()
    private val handledCommandIds = mutableSetOf<String>()
    private var linkDownUntil = 0L
    private var forcedNextCommand: SimulationScenario? = null
    private var loop: Job? = null

    override val telemetry: Flow<Telemetry> = _telemetry.asSharedFlow()
    override val alerts: Flow<List<Alert>> = _alerts.asStateFlow()
    override val events: Flow<RobotEvent> = _events.asSharedFlow()
    val timeScale: StateFlow<Int> = _timeScale.asStateFlow()
    override val site: StateFlow<SiteConfig> = _site.map { it.config }
        .stateIn(scope, SharingStarted.Eagerly, _site.value.config)

    override fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            publish()
            while (isActive) {
                delay(TICK_MILLIS)
                mutex.withLock {
                    engine.setLinkAvailable(isLinkUp())
                    engine.tick(TICK_MILLIS / 1000.0 * _timeScale.value)
                }
                publish()
            }
        }
    }

    override fun stop() {
        loop?.cancel()
        loop = null
    }

    override suspend fun send(command: Command): CommandOutcome {
        if (!isLinkUp()) awaitCancellation()
        val forced = mutex.withLock { forcedNextCommand.also { forcedNextCommand = null } }
        if (forced == SimulationScenario.IGNORE_NEXT_COMMAND) awaitCancellation()
        delay(Random.nextLong(MIN_LATENCY_MILLIS, MAX_LATENCY_MILLIS))
        if (!isLinkUp()) awaitCancellation()
        if (forced == SimulationScenario.REJECT_NEXT_COMMAND) {
            return CommandOutcome.Rejected(RejectionReason.SIMULATED_FAILURE)
        }
        val outcome = mutex.withLock {
            // Una orden repetida (mismo id) se confirma sin volver a ejecutarla.
            if (!handledCommandIds.add(command.id)) CommandOutcome.Confirmed else engine.handle(command)
        }
        publish()
        return outcome
    }

    suspend fun trigger(scenario: SimulationScenario) = mutex.withLock {
        when (scenario) {
            SimulationScenario.CONNECTION_LOSS -> linkDownUntil = System.currentTimeMillis() + LINK_DOWN_MILLIS
            SimulationScenario.REJECT_NEXT_COMMAND,
            SimulationScenario.IGNORE_NEXT_COMMAND -> forcedNextCommand = scenario
            else -> engine.trigger(scenario)
        }
    }

    /**
     * Carga otro sitio en el robot simulado, como se haría en la puesta en marcha de un robot real.
     * Sólo con el robot en la base; el robot arranca de cero sobre el nuevo mapa.
     * [load] lee y valida el archivo; si falla, el robot sigue con el sitio que tenía.
     */
    suspend fun loadSite(load: () -> SiteDefinition): SiteLoadResult = mutex.withLock {
        if (engine.state != OperationalState.AT_BASE) return SiteLoadResult.RobotBusy
        val definition = try {
            load()
        } catch (e: InvalidSiteException) {
            return SiteLoadResult.Invalid(e.message.orEmpty())
        }
        engine = newEngine(definition)
        queuedEvents.clear()
        _site.value = definition
        SiteLoadResult.Loaded(definition.config.name)
    }.also { publish() }

    private fun newEngine(definition: SiteDefinition) =
        RobotSimulator(definition.config, checkNotNull(definition.simulation), ROBOT_ID)

    fun setTimeScale(scale: Int) {
        _timeScale.value = scale.coerceIn(1, MAX_TIME_SCALE)
    }

    private fun isLinkUp() = System.currentTimeMillis() >= linkDownUntil

    private suspend fun publish() {
        val (telemetry, alerts, events) = mutex.withLock {
            queuedEvents += engine.drainEvents()
            if (!isLinkUp()) return
            val link = simulatedLink(engine.currentLevel())
            Triple(engine.telemetry(link), engine.alerts(), queuedEvents.toList().also { queuedEvents.clear() })
        }
        events.forEach { _events.emit(it) }
        _alerts.value = alerts
        _telemetry.emit(telemetry)
    }

    /** En el subsuelo la señal Wi-Fi es más débil (riesgo R3 del documento). */
    private fun simulatedLink(level: Level) = when (level) {
        Level.GROUND_FLOOR -> LinkQuality(latencyMs = Random.nextInt(70, 110), rssiDbm = -61)
        Level.BASEMENT -> LinkQuality(latencyMs = Random.nextInt(180, 260), rssiDbm = -74)
    }

    companion object {
        const val ROBOT_ID = "go2-01"
        const val DEFAULT_TIME_SCALE = 4
        const val MAX_TIME_SCALE = 10
        private const val TICK_MILLIS = 1_000L
        private const val MIN_LATENCY_MILLIS = 300L
        private const val MAX_LATENCY_MILLIS = 800L
        private const val LINK_DOWN_MILLIS = 60_000L
    }
}
