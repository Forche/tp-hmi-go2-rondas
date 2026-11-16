package ar.edu.uade.go2rondas.domain.usecase

import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.AlertCode
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.Battery
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandRecord
import ar.edu.uade.go2rondas.domain.model.CommandStatus
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.ConnectionStatus
import ar.edu.uade.go2rondas.domain.model.DisabledReason
import ar.edu.uade.go2rondas.domain.model.IncidentDecision
import ar.edu.uade.go2rondas.domain.model.Level
import ar.edu.uade.go2rondas.domain.model.LinkQuality
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.MissionProgress
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.Motion
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.domain.model.PlanPoint
import ar.edu.uade.go2rondas.domain.model.RobotLocation
import ar.edu.uade.go2rondas.domain.model.Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateCommandAvailabilityUseCaseTest {

    private val evaluate = EvaluateCommandAvailabilityUseCase()
    private val connected = ConnectionStatus(LinkState.CONNECTED, 0L, 85)

    private fun telemetry(
        state: OperationalState,
        battery: Int = 92,
        onMission: Boolean = state != OperationalState.AT_BASE
    ) = Telemetry(
        robotId = "go2-01",
        sequence = 1,
        timestampMillis = 0,
        link = LinkQuality(85, -61),
        battery = Battery(battery, charging = false, estimatedMinutes = 180),
        state = state,
        mission = if (onMission) MissionProgress("m-1", MissionType.FULL_ROUND, "CP3", 3, 2, 8, 360) else null,
        location = RobotLocation(Level.GROUND_FLOOR, "CP2", "CP3", PlanPoint(0f, 0f), 180, 0.93),
        motion = Motion(0.4, moving = true),
        activeAlerts = 0,
        environment = null
    )

    private fun alert(severity: AlertSeverity, acknowledged: Boolean, requiresDecision: Boolean = false) = Alert(
        id = "a-1",
        timestampMillis = 0,
        severity = severity,
        code = AlertCode.OBSTACLE,
        checkpointId = "CP3",
        measuredValue = null,
        threshold = null,
        requiresDecision = requiresDecision,
        decisionOptions = listOf(IncidentDecision.SKIP, IncidentDecision.RETRY),
        acknowledged = acknowledged
    )

    private fun pendingPause() = CommandRecord(Command.Pause("c-1"), CommandStatus.PENDING, sentAtMillis = 0)

    @Test
    fun `start is enabled at base with enough battery and connection`() {
        val result = evaluate(telemetry(OperationalState.AT_BASE), connected, emptyList(), null, 40)
        assertTrue(result.isEnabled(CommandType.START_MISSION))
    }

    @Test
    fun `start is disabled when battery is under the minimum`() {
        val result = evaluate(telemetry(OperationalState.AT_BASE, battery = 32), connected, emptyList(), null, 40)
        assertEquals(DisabledReason.BATTERY_TOO_LOW, result.reason(CommandType.START_MISSION))
    }

    @Test
    fun `start is disabled without connection`() {
        val result = evaluate(telemetry(OperationalState.AT_BASE), ConnectionStatus.Disconnected, emptyList(), null, 40)
        assertEquals(DisabledReason.NOT_CONNECTED, result.reason(CommandType.START_MISSION))
    }

    @Test
    fun `start is disabled with an unacknowledged critical alert`() {
        val alerts = listOf(alert(AlertSeverity.CRITICAL, acknowledged = false))
        val result = evaluate(telemetry(OperationalState.AT_BASE), connected, alerts, null, 40)
        assertEquals(DisabledReason.UNACKNOWLEDGED_CRITICAL_ALERT, result.reason(CommandType.START_MISSION))
    }

    @Test
    fun `a pending command blocks other commands but not the operational stop`() {
        val result = evaluate(telemetry(OperationalState.ON_MISSION), connected, emptyList(), pendingPause(), 40)
        assertEquals(DisabledReason.COMMAND_PENDING, result.reason(CommandType.RETURN_TO_BASE))
        assertTrue(result.isEnabled(CommandType.OPERATIONAL_STOP))
    }

    @Test
    fun `pause is only enabled during a mission`() {
        val result = evaluate(telemetry(OperationalState.AT_BASE), connected, emptyList(), null, 40)
        assertEquals(DisabledReason.NOT_ON_MISSION, result.reason(CommandType.PAUSE))
    }

    @Test
    fun `incident decision requires the alert to be acknowledged first`() {
        val awaiting = telemetry(OperationalState.AWAITING_DECISION)
        val notAcknowledged = listOf(alert(AlertSeverity.WARNING, acknowledged = false, requiresDecision = true))
        val acknowledged = listOf(alert(AlertSeverity.WARNING, acknowledged = true, requiresDecision = true))

        assertEquals(
            DisabledReason.ALERT_NOT_ACKNOWLEDGED,
            evaluate(awaiting, connected, notAcknowledged, null, 40).reason(CommandType.DECIDE_INCIDENT)
        )
        assertTrue(evaluate(awaiting, connected, acknowledged, null, 40).isEnabled(CommandType.DECIDE_INCIDENT))
    }

    @Test
    fun `without telemetry no command is enabled`() {
        val result = evaluate(null, connected, emptyList(), null, 40)
        assertFalse(CommandType.entries.any { result.isEnabled(it) })
    }
}
