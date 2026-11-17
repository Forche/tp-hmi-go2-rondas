package ar.edu.uade.go2rondas.presentation.panel

import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.data.site.TestSites
import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.AlertCode
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.Battery
import ar.edu.uade.go2rondas.domain.model.CommandAvailability
import ar.edu.uade.go2rondas.domain.model.ConnectionStatus
import ar.edu.uade.go2rondas.domain.model.Level
import ar.edu.uade.go2rondas.domain.model.LinkQuality
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.MissionProgress
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.Motion
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.domain.model.PlanPoint
import ar.edu.uade.go2rondas.domain.model.RobotLocation
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.Telemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextActionTest {

    private val site = TestSites.load().config

    private fun snapshot(
        state: OperationalState,
        battery: Int = 92,
        moving: Boolean = true,
        link: LinkState = LinkState.CONNECTED
    ) = RobotSnapshot(
        robot = null,
        connection = ConnectionStatus(link, 0L, 85),
        telemetry = Telemetry(
            robotId = "go2-01",
            sequence = 1,
            timestampMillis = 0,
            link = LinkQuality(85, -61),
            battery = Battery(battery, charging = false, estimatedMinutes = 180),
            state = state,
            mission = if (state == OperationalState.AT_BASE) null
            else MissionProgress("m-1", MissionType.FULL_ROUND, "CP3", 3, 2, 8, 360),
            location = RobotLocation(Level.GROUND_FLOOR, "CP2", "CP3", PlanPoint(0f, 0f), 180, 0.93),
            motion = Motion(if (moving) 0.4 else 0.0, moving = moving),
            activeAlerts = 0,
            environment = null
        ),
        alerts = emptyList(),
        lastCommand = null,
        availability = CommandAvailability(emptyMap())
    )

    @Test
    fun `on a round it names where the robot goes and says no action is needed`() {
        assertEquals(NextAction(R.string.next_on_mission, "CP3 · Pasillo frío"), nextAction(snapshot(OperationalState.ON_MISSION), site))
        assertEquals(
            NextAction(R.string.next_measuring, "CP3 · Pasillo frío"),
            nextAction(snapshot(OperationalState.ON_MISSION, moving = false), site)
        )
    }

    @Test
    fun `states that need the operator say what to do`() {
        assertEquals(R.string.next_decision, nextAction(snapshot(OperationalState.AWAITING_DECISION), site)?.text)
        assertEquals(R.string.next_stopped, nextAction(snapshot(OperationalState.STOPPED), site)?.text)
        assertEquals(R.string.next_low_battery, nextAction(snapshot(OperationalState.ON_MISSION, battery = 25), site)?.text)
    }

    @Test
    fun `at base it tells whether the robot can leave`() {
        assertEquals(NextAction(R.string.next_ready), nextAction(snapshot(OperationalState.AT_BASE), site))
        assertEquals(NextAction(R.string.next_charging, number = 40), nextAction(snapshot(OperationalState.AT_BASE, battery = 30), site))
    }

    @Test
    fun `at base an unacknowledged critical alert comes before leaving`() {
        val critical = Alert(
            id = "a-1",
            timestampMillis = 0,
            severity = AlertSeverity.CRITICAL,
            code = AlertCode.BATTERY_CRITICAL,
            checkpointId = null,
            measuredValue = 14.0,
            threshold = 15.0,
            requiresDecision = false,
            decisionOptions = emptyList(),
            acknowledged = false
        )
        val atBase = snapshot(OperationalState.AT_BASE).copy(alerts = listOf(critical))
        assertEquals(NextAction(R.string.next_ack_critical), nextAction(atBase, site))
    }

    @Test
    fun `without link it explains what the robot does on its own`() {
        assertEquals(
            R.string.next_disconnected,
            nextAction(snapshot(OperationalState.ON_MISSION, link = LinkState.DISCONNECTED), site)?.text
        )
    }

    @Test
    fun `without telemetry there is nothing to say yet`() {
        assertNull(nextAction(snapshot(OperationalState.AT_BASE).copy(telemetry = null), site))
    }
}
