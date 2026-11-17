package ar.edu.uade.go2rondas.data.sim

import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.AlertCode
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.Battery
import ar.edu.uade.go2rondas.domain.model.Checkpoint
import ar.edu.uade.go2rondas.domain.model.CheckpointResult
import ar.edu.uade.go2rondas.domain.model.CheckpointStatus
import ar.edu.uade.go2rondas.domain.model.CheckpointZone
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.EnvironmentReading
import ar.edu.uade.go2rondas.domain.model.IncidentDecision
import ar.edu.uade.go2rondas.domain.model.Level
import ar.edu.uade.go2rondas.domain.model.LinkQuality
import ar.edu.uade.go2rondas.domain.model.MissionProgress
import ar.edu.uade.go2rondas.domain.model.MissionResult
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.Motion
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.domain.model.OperationalState.AT_BASE
import ar.edu.uade.go2rondas.domain.model.OperationalState.AWAITING_DECISION
import ar.edu.uade.go2rondas.domain.model.OperationalState.ON_MISSION
import ar.edu.uade.go2rondas.domain.model.OperationalState.PAUSED
import ar.edu.uade.go2rondas.domain.model.OperationalState.RETURNING
import ar.edu.uade.go2rondas.domain.model.OperationalState.STOPPED
import ar.edu.uade.go2rondas.domain.model.RejectionReason
import ar.edu.uade.go2rondas.domain.model.RobotEvent
import ar.edu.uade.go2rondas.domain.model.RobotLocation
import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.data.site.SimulationProfile
import ar.edu.uade.go2rondas.domain.model.Telemetry
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Motor del robot simulado: estado, movimiento sobre el recorrido, inspecciones, batería y alertas.
 * Es sincrónico y no conoce corrutinas ni el enlace con la app; eso lo maneja [SimulatedRobotDataSource].
 * [tick] avanza el tiempo simulado y [handle] decide si acepta una orden.
 */
class RobotSimulator(
    private val site: SiteConfig,
    private val simulation: SimulationProfile,
    private val robotId: String,
    private val random: Random = Random.Default,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val route = RoutePath(site.route)
    private val thresholds = site.thresholds

    var state: OperationalState = AT_BASE
        private set
    var battery = 92.0
        private set

    private var distance = 0.0
    private var speed = 0.0
    private var forward = true
    private var mission: SimMission? = null
    private val alerts = ArrayDeque<Alert>()
    private var lastReading: EnvironmentReading? = null
    private var sequence = 0L
    private var nextAlertNumber = 1
    private val pendingEvents = mutableListOf<RobotEvent>()

    private var inspectionRemaining = 0.0
    private var obstacleArmed = false
    private var obstacleAt: Double? = null
    private var highTemperatureArmed = false
    private var drainMultiplier = 1.0
    private var lowBatteryAlerted = false
    private var criticalBatteryAlerted = false
    private var watchReadingsLeft = 0
    private var watchTimer = 0.0
    private var stoppedWhileReturning = false
    private var linkAvailable = true
    private var secondsWithoutLink = 0.0

    private class Target(val checkpoint: Checkpoint, val distance: Double)

    private class SimMission(val id: String, val type: MissionType, val targets: List<Target>) {
        var nextIndex = 0
        var inspected = 0
        var skipped = 0
        var elapsedSeconds = 0.0
        var cancelled = false
        var lowBatteryDuringMission = false
        var lastCheckpointId: String? = null
        val current: Target? get() = targets.getOrNull(nextIndex)
        val allDone: Boolean get() = nextIndex >= targets.size
    }

    // ---------------------------------------------------------------- tiempo

    fun tick(dt: Double) {
        mission?.let { if (state != AT_BASE) it.elapsedSeconds += dt }
        updateLink(dt)
        when (state) {
            AT_BASE -> charge(dt)
            ON_MISSION -> advanceMission(dt)
            RETURNING -> {
                moveTowards(0.0, dt)
                if (distance <= 0.0) arriveAtBase()
            }
            AWAITING_DECISION -> {
                speed = 0.0
                tickWatch(dt)
            }
            PAUSED, STOPPED -> speed = 0.0
        }
        drainBattery(dt)
        checkBattery()
    }

    /** El robot sigue funcionando sin enlace; esto sólo activa su regla de seguridad (riesgo R3). */
    fun setLinkAvailable(available: Boolean) {
        linkAvailable = available
        if (available) secondsWithoutLink = 0.0
    }

    private fun updateLink(dt: Double) {
        if (linkAvailable) return
        secondsWithoutLink += dt
        if (secondsWithoutLink >= MAX_SECONDS_WITHOUT_LINK && mission != null && state != RETURNING && state != AT_BASE) {
            resolveDecisions()
            state = RETURNING
        }
    }

    private fun charge(dt: Double) {
        speed = 0.0
        drainMultiplier = 1.0
        battery = min(100.0, battery + CHARGE_RATE * dt)
        if (battery > thresholds.batteryWarningPct + 5) lowBatteryAlerted = false
        if (battery > thresholds.batteryCriticalPct + 5) criticalBatteryAlerted = false
    }

    private fun advanceMission(dt: Double) {
        val m = mission ?: return
        if (inspectionRemaining > 0) {
            speed = 0.0
            inspectionRemaining -= dt
            if (inspectionRemaining <= 0) completeInspection(m)
            return
        }
        val target = m.current ?: run { state = RETURNING; return }

        if (obstacleArmed && obstacleAt == null) {
            val gap = target.distance - distance
            if (gap > 1.5) {
                obstacleAt = distance + min(3.0, gap / 2)
                obstacleArmed = false
            }
        }
        val obstacle = obstacleAt
        val stopAt = if (obstacle != null) min(target.distance, obstacle - OBSTACLE_GAP) else target.distance
        moveTowards(stopAt, dt)
        if (obstacle != null && distance >= stopAt - EPSILON && stopAt < target.distance) {
            raiseObstacle(target)
            return
        }
        if (distance >= target.distance - EPSILON) {
            speed = 0.0
            inspectionRemaining = INSPECTION_SECONDS
        }
    }

    private fun moveTowards(target: Double, dt: Double) {
        val delta = target - distance
        if (abs(delta) < EPSILON) {
            speed = 0.0
            return
        }
        forward = delta > 0
        val step = SPEED_MPS * dt
        distance = if (abs(delta) <= step) target else distance + step * (if (forward) 1 else -1)
        speed = SPEED_MPS - random.nextDouble(0.0, 0.08)
    }

    private fun arriveAtBase() {
        distance = 0.0
        speed = 0.0
        inspectionRemaining = 0.0
        obstacleAt = null
        state = AT_BASE
        resolveDecisions()
        mission?.let { finish(it) }
        mission = null
    }

    private fun finish(m: SimMission) {
        val result = when {
            m.cancelled -> MissionResult.CANCELLED
            m.allDone -> MissionResult.COMPLETED
            m.lowBatteryDuringMission -> MissionResult.INTERRUPTED_LOW_BATTERY
            else -> MissionResult.CANCELLED
        }
        emit(RobotEvent.MissionFinished(clock(), m.id, result, m.inspected, m.targets.size))
    }

    // ---------------------------------------------------------------- inspección

    private fun completeInspection(m: SimMission) {
        val target = m.current ?: return
        val checkpoint = target.checkpoint
        val reading = measure(checkpoint)
        lastReading = reading
        val issues = evaluate(checkpoint, reading)
        val status = if (issues.isEmpty()) CheckpointStatus.OK else CheckpointStatus.ANOMALY
        m.inspected++
        m.lastCheckpointId = checkpoint.id
        emit(RobotEvent.CheckpointInspected(clock(), CheckpointResult(m.id, checkpoint.id, status, reading, clock())))

        var waitsForDecision = false
        issues.forEach { issue ->
            val needsDecision = issue.code == AlertCode.TEMPERATURE_HIGH && issue.severity == AlertSeverity.CRITICAL
            if (needsDecision) waitsForDecision = true
            raiseAlert(
                severity = issue.severity,
                code = issue.code,
                checkpointId = checkpoint.id,
                measured = issue.value,
                threshold = issue.threshold,
                options = if (needsDecision) listOf(IncidentDecision.CONTINUE, IncidentDecision.WATCH) else emptyList()
            )
        }
        m.nextIndex++
        state = when {
            waitsForDecision -> AWAITING_DECISION
            // Regla R3: sin enlace termina el checkpoint y espera en un lugar seguro.
            !linkAvailable -> PAUSED
            m.allDone -> RETURNING
            else -> ON_MISSION
        }
    }

    private class Issue(val code: AlertCode, val severity: AlertSeverity, val value: Double, val threshold: Double)

    private fun evaluate(checkpoint: Checkpoint, reading: EnvironmentReading): List<Issue> {
        val issues = mutableListOf<Issue>()
        thresholds.temperature[checkpoint.zone]?.let { limits ->
            when {
                reading.temperatureC >= limits.critical ->
                    issues += Issue(AlertCode.TEMPERATURE_HIGH, AlertSeverity.CRITICAL, reading.temperatureC, limits.critical)
                reading.temperatureC >= limits.warning ->
                    issues += Issue(AlertCode.TEMPERATURE_HIGH, AlertSeverity.WARNING, reading.temperatureC, limits.warning)
            }
        }
        val low = thresholds.humidityLow
        val high = thresholds.humidityHigh
        when {
            reading.humidityPct <= low.critical || reading.humidityPct >= high.critical ->
                issues += Issue(AlertCode.HUMIDITY_OUT_OF_RANGE, AlertSeverity.CRITICAL, reading.humidityPct, if (reading.humidityPct < 50) low.critical else high.critical)
            reading.humidityPct <= low.warning || reading.humidityPct >= high.warning ->
                issues += Issue(AlertCode.HUMIDITY_OUT_OF_RANGE, AlertSeverity.WARNING, reading.humidityPct, if (reading.humidityPct < 50) low.warning else high.warning)
        }
        val noiseExcess = reading.noiseDba - reading.noiseBaselineDba
        when {
            noiseExcess >= thresholds.noiseAboveBaseline.critical ->
                issues += Issue(AlertCode.NOISE_HIGH, AlertSeverity.CRITICAL, reading.noiseDba, reading.noiseBaselineDba + thresholds.noiseAboveBaseline.critical)
            noiseExcess >= thresholds.noiseAboveBaseline.warning ->
                issues += Issue(AlertCode.NOISE_HIGH, AlertSeverity.WARNING, reading.noiseDba, reading.noiseBaselineDba + thresholds.noiseAboveBaseline.warning)
        }
        return issues
    }

    private fun measure(checkpoint: Checkpoint): EnvironmentReading {
        // El archivo de sitio ya garantiza que hay lecturas típicas para cada zona con checkpoints.
        val typical = simulation.typicalReadings.getValue(checkpoint.zone)
        val temperature = typical.temperatureC
        val noiseBaseline = typical.noiseBaselineDba
        val forcedTemperature = if (highTemperatureArmed) {
            highTemperatureArmed = false
            (thresholds.temperature[checkpoint.zone]?.critical ?: 35.0) + 1.1
        } else null
        return EnvironmentReading(
            checkpointId = checkpoint.id,
            temperatureC = round1(forcedTemperature ?: (temperature + random.nextDouble(-0.6, 0.6))),
            humidityPct = round1(simulation.humidityPct + random.nextDouble(-4.0, 4.0)),
            noiseDba = round1(noiseBaseline + random.nextDouble(-1.5, 1.5)),
            noiseBaselineDba = noiseBaseline
        )
    }

    /** Vigilancia del punto: repite la medición cada [WATCH_INTERVAL_SECONDS] mientras espera la decisión. */
    private fun tickWatch(dt: Double) {
        if (watchReadingsLeft <= 0) return
        watchTimer -= dt
        if (watchTimer > 0) return
        lastReading = lastReading?.let { it.copy(temperatureC = round1(it.temperatureC + random.nextDouble(-0.3, 0.3))) }
        watchReadingsLeft--
        watchTimer = WATCH_INTERVAL_SECONDS
    }

    private fun raiseObstacle(target: Target) {
        speed = 0.0
        state = AWAITING_DECISION
        raiseAlert(
            severity = AlertSeverity.WARNING,
            code = AlertCode.OBSTACLE,
            checkpointId = target.checkpoint.id,
            measured = null,
            threshold = null,
            options = listOf(IncidentDecision.SKIP, IncidentDecision.RETRY)
        )
    }

    // ---------------------------------------------------------------- batería

    private fun drainBattery(dt: Double) {
        if (state == AT_BASE) return
        val rate = if (speed > 0) MOVING_DRAIN_PER_SECOND else IDLE_DRAIN_PER_SECOND
        battery = max(0.0, battery - rate * dt * drainMultiplier)
    }

    private fun checkBattery() {
        if (state == AT_BASE) return
        if (battery < thresholds.batteryWarningPct && !lowBatteryAlerted) {
            lowBatteryAlerted = true
            mission?.lowBatteryDuringMission = true
            raiseAlert(AlertSeverity.WARNING, AlertCode.BATTERY_LOW, null, battery, thresholds.batteryWarningPct.toDouble())
        }
        if (battery < thresholds.batteryCriticalPct && !criticalBatteryAlerted) {
            criticalBatteryAlerted = true
            mission?.lowBatteryDuringMission = true
            raiseAlert(AlertSeverity.CRITICAL, AlertCode.BATTERY_CRITICAL, null, battery, thresholds.batteryCriticalPct.toDouble())
            if (state != RETURNING) {
                resolveDecisions()
                inspectionRemaining = 0.0
                state = RETURNING
            }
        }
    }

    // ---------------------------------------------------------------- órdenes

    fun handle(command: Command): CommandOutcome = when (command) {
        is Command.StartMission -> start(command)
        is Command.Pause -> when {
            state != ON_MISSION -> rejected(RejectionReason.INVALID_STATE)
            isOnStairs() -> rejected(RejectionReason.ON_STAIRS)
            else -> confirmed { state = PAUSED; speed = 0.0 }
        }
        is Command.Resume -> when (state) {
            PAUSED, STOPPED -> confirmed { state = stateAfterResume() }
            else -> rejected(RejectionReason.INVALID_STATE)
        }
        is Command.Cancel -> when {
            mission == null -> rejected(RejectionReason.NO_ACTIVE_MISSION)
            state == RETURNING -> rejected(RejectionReason.INVALID_STATE)
            else -> confirmed {
                mission?.cancelled = true
                goBack()
            }
        }
        is Command.ReturnToBase -> when (state) {
            AT_BASE, RETURNING -> rejected(RejectionReason.INVALID_STATE)
            else -> confirmed { goBack() }
        }
        is Command.OperationalStop -> when (state) {
            AT_BASE, STOPPED -> rejected(RejectionReason.INVALID_STATE)
            else -> confirmed {
                stoppedWhileReturning = state == RETURNING
                state = STOPPED
                speed = 0.0
            }
        }
        is Command.AcknowledgeAlert -> {
            val index = alerts.indexOfFirst { it.id == command.alertId }
            if (index < 0) rejected(RejectionReason.ALERT_NOT_FOUND)
            else confirmed { alerts[index] = alerts[index].copy(acknowledged = true) }
        }
        is Command.DecideIncident -> decide(command)
    }

    private fun start(command: Command.StartMission): CommandOutcome {
        if (state != AT_BASE) return rejected(RejectionReason.NOT_AT_BASE)
        if (battery < thresholds.batteryMinToStartPct) return rejected(RejectionReason.LOW_BATTERY)
        val ids = when (command.mission) {
            MissionType.SPOT_CHECK -> listOfNotNull(command.checkpointId)
            else -> site.missions.firstOrNull { it.type == command.mission }?.checkpointIds.orEmpty()
        }
        val targets = ids.mapNotNull { site.checkpoint(it) }
            .map { Target(it, route.distanceOf(it.position)) }
            .sortedBy { it.distance }
        if (targets.isEmpty()) return rejected(RejectionReason.INVALID_STATE)
        return confirmed {
            val m = SimMission("m-${clock()}", command.mission, targets)
            mission = m
            obstacleAt = null
            inspectionRemaining = 0.0
            state = ON_MISSION
            emit(RobotEvent.MissionStarted(clock(), m.id, m.type, targets.size))
        }
    }

    private fun decide(command: Command.DecideIncident): CommandOutcome {
        val m = mission ?: return rejected(RejectionReason.NO_ACTIVE_MISSION)
        val alert = alerts.firstOrNull { it.id == command.alertId && it.requiresDecision }
            ?: return rejected(RejectionReason.ALERT_NOT_FOUND)
        if (state != AWAITING_DECISION || command.decision !in alert.decisionOptions) {
            return rejected(RejectionReason.INVALID_STATE)
        }
        return when (command.decision) {
            IncidentDecision.RETRY -> rejected(RejectionReason.OBSTACLE_PERSISTS)
            IncidentDecision.SKIP -> confirmed {
                val target = m.current ?: return@confirmed
                m.skipped++
                m.lastCheckpointId = target.checkpoint.id
                emit(
                    RobotEvent.CheckpointInspected(
                        clock(),
                        CheckpointResult(m.id, target.checkpoint.id, CheckpointStatus.SKIPPED, null, clock())
                    )
                )
                // Rodea el obstáculo y sigue después del checkpoint salteado.
                distance = target.distance
                obstacleAt = null
                m.nextIndex++
                resolve(alert.id)
                state = if (m.allDone) RETURNING else ON_MISSION
            }
            IncidentDecision.CONTINUE -> confirmed {
                watchReadingsLeft = 0
                resolve(alert.id)
                state = if (m.allDone) RETURNING else ON_MISSION
            }
            IncidentDecision.WATCH -> confirmed {
                watchReadingsLeft = WATCH_READINGS
                watchTimer = WATCH_INTERVAL_SECONDS
            }
        }
    }

    private fun goBack() {
        resolveDecisions()
        inspectionRemaining = 0.0
        obstacleAt = null
        stoppedWhileReturning = false
        state = RETURNING
    }

    private fun stateAfterResume(): OperationalState {
        val m = mission
        return when {
            stoppedWhileReturning || m == null -> RETURNING
            alerts.any { it.requiresDecision } -> AWAITING_DECISION
            m.allDone -> RETURNING
            else -> ON_MISSION
        }.also { stoppedWhileReturning = false }
    }

    private fun isOnStairs(): Boolean = site.isOnStairs(route.pointAt(distance))

    // ---------------------------------------------------------------- escenarios

    fun trigger(scenario: SimulationScenario) {
        when (scenario) {
            SimulationScenario.LOW_BATTERY -> {
                battery = min(battery, thresholds.batteryWarningPct + 1.0)
                drainMultiplier = LOW_BATTERY_DRAIN_MULTIPLIER
            }
            SimulationScenario.OBSTACLE -> obstacleArmed = true
            SimulationScenario.HIGH_TEMPERATURE -> highTemperatureArmed = true
            // El corte de enlace, el rechazo y la falta de respuesta los simula la fuente de datos.
            SimulationScenario.CONNECTION_LOSS,
            SimulationScenario.REJECT_NEXT_COMMAND,
            SimulationScenario.IGNORE_NEXT_COMMAND -> Unit
        }
    }

    // ---------------------------------------------------------------- salida

    fun drainEvents(): List<RobotEvent> = pendingEvents.toList().also { pendingEvents.clear() }

    fun alerts(): List<Alert> = alerts.toList()

    fun currentLevel(): Level = site.levelAt(if (state == AT_BASE) site.base else route.pointAt(distance))

    fun telemetry(link: LinkQuality): Telemetry {
        sequence++
        val m = mission
        val point = if (state == AT_BASE) site.base else route.pointAt(distance)
        val decisionAlert = alerts.firstOrNull { it.requiresDecision }
        val currentId = decisionAlert?.checkpointId ?: m?.current?.checkpoint?.id ?: m?.lastCheckpointId
        return Telemetry(
            robotId = robotId,
            sequence = sequence,
            timestampMillis = clock(),
            link = link,
            battery = Battery(
                percent = battery.roundToInt(),
                charging = state == AT_BASE && battery < 100,
                estimatedMinutes = (battery / (MOVING_DRAIN_PER_SECOND * 60)).roundToInt()
            ),
            state = state,
            mission = m?.let {
                val position = it.targets.indexOfFirst { t -> t.checkpoint.id == currentId } + 1
                MissionProgress(
                    missionId = it.id,
                    type = it.type,
                    currentCheckpointId = currentId,
                    currentPosition = if (position > 0) position else it.targets.size,
                    completed = it.inspected + it.skipped,
                    total = it.targets.size,
                    elapsedSeconds = it.elapsedSeconds.toLong()
                )
            },
            location = RobotLocation(
                level = site.levelAt(point),
                fromCheckpointId = m?.lastCheckpointId,
                toCheckpointId = if (state == RETURNING) null else m?.current?.checkpoint?.id,
                position = point,
                headingDeg = route.headingAt(distance, forward),
                confidence = if (isOnStairs()) 0.81 else round2(0.91 + random.nextDouble(0.0, 0.05))
            ),
            motion = Motion(speedMps = round2(speed), moving = speed > 0),
            activeAlerts = alerts.count { !it.acknowledged },
            environment = lastReading
        )
    }

    // ---------------------------------------------------------------- auxiliares

    private fun raiseAlert(
        severity: AlertSeverity,
        code: AlertCode,
        checkpointId: String?,
        measured: Double?,
        threshold: Double?,
        options: List<IncidentDecision> = emptyList()
    ) {
        val alert = Alert(
            id = "a-${nextAlertNumber++}",
            timestampMillis = clock(),
            severity = severity,
            code = code,
            checkpointId = checkpointId,
            measuredValue = measured?.let(::round1),
            threshold = threshold,
            requiresDecision = options.isNotEmpty(),
            decisionOptions = options,
            acknowledged = false
        )
        alerts.addFirst(alert)
        while (alerts.size > MAX_ALERTS) alerts.removeLast()
        emit(RobotEvent.AlertRaised(clock(), mission?.id, alert))
    }

    private fun resolve(alertId: String) {
        val index = alerts.indexOfFirst { it.id == alertId }
        if (index >= 0) alerts[index] = alerts[index].copy(requiresDecision = false, acknowledged = true)
    }

    private fun resolveDecisions() {
        alerts.filter { it.requiresDecision }.forEach { resolve(it.id) }
        watchReadingsLeft = 0
    }

    private fun emit(event: RobotEvent) {
        pendingEvents += event
    }

    private inline fun confirmed(action: () -> Unit): CommandOutcome {
        action()
        return CommandOutcome.Confirmed
    }

    private fun rejected(reason: RejectionReason) = CommandOutcome.Rejected(reason)

    private fun round1(value: Double) = (value * 10).roundToInt() / 10.0
    private fun round2(value: Double) = (value * 100).roundToInt() / 100.0

    private companion object {
        const val EPSILON = 1e-6
        const val SPEED_MPS = 0.5
        const val INSPECTION_SECONDS = 4.0
        const val OBSTACLE_GAP = 0.6
        const val CHARGE_RATE = 0.25
        const val MOVING_DRAIN_PER_SECOND = 0.012
        const val IDLE_DRAIN_PER_SECOND = 0.004
        const val LOW_BATTERY_DRAIN_MULTIPLIER = 15.0
        const val WATCH_READINGS = 3
        const val WATCH_INTERVAL_SECONDS = 10.0
        const val MAX_SECONDS_WITHOUT_LINK = 300.0
        const val MAX_ALERTS = 30
    }
}
