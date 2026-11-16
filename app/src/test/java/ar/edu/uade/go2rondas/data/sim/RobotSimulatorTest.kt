package ar.edu.uade.go2rondas.data.sim

import ar.edu.uade.go2rondas.domain.model.AlertCode
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.CheckpointStatus
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.IncidentDecision
import ar.edu.uade.go2rondas.domain.model.LinkQuality
import ar.edu.uade.go2rondas.domain.model.MissionResult
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.domain.model.RejectionReason
import ar.edu.uade.go2rondas.domain.model.RobotEvent
import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RobotSimulatorTest {

    private val simulator = RobotSimulator(DataCenterSite.config, "go2-01", Random(42)) { 0L }
    private val events = mutableListOf<RobotEvent>()

    private fun runUntil(maxSeconds: Int = 3_000, condition: () -> Boolean) {
        repeat(maxSeconds) {
            if (condition()) return
            simulator.tick(1.0)
            events += simulator.drainEvents()
        }
        throw AssertionError("La condición no se cumplió en $maxSeconds s simulados")
    }

    private fun startFullRound() {
        assertEquals(CommandOutcome.Confirmed, simulator.handle(Command.StartMission("c-1", MissionType.FULL_ROUND)))
        events += simulator.drainEvents()
    }

    private fun finished() = events.filterIsInstance<RobotEvent.MissionFinished>().single()
    private fun inspected() = events.filterIsInstance<RobotEvent.CheckpointInspected>().map { it.result }

    @Test
    fun `a full round inspects the eight checkpoints and returns to base`() {
        startFullRound()
        runUntil { simulator.state == OperationalState.AT_BASE }

        assertEquals(listOf("CP1", "CP2", "CP3", "CP4", "CP5", "CP6", "CP7", "CP8"), inspected().map { it.checkpointId })
        assertTrue(inspected().all { it.status == CheckpointStatus.OK })
        assertEquals(MissionResult.COMPLETED, finished().result)
        assertEquals(8, finished().completed)
    }

    @Test
    fun `an obstacle waits for a decision and can be skipped`() {
        startFullRound()
        simulator.trigger(SimulationScenario.OBSTACLE)
        runUntil { simulator.state == OperationalState.AWAITING_DECISION }

        val alert = simulator.alerts().first()
        assertEquals(AlertCode.OBSTACLE, alert.code)
        assertEquals(
            CommandOutcome.Rejected(RejectionReason.OBSTACLE_PERSISTS),
            simulator.handle(Command.DecideIncident("c-2", alert.id, IncidentDecision.RETRY))
        )
        assertEquals(CommandOutcome.Confirmed, simulator.handle(Command.DecideIncident("c-3", alert.id, IncidentDecision.SKIP)))
        events += simulator.drainEvents()
        runUntil { simulator.state == OperationalState.AT_BASE }

        assertEquals(CheckpointStatus.SKIPPED, inspected().first { it.checkpointId == alert.checkpointId }.status)
        assertEquals(8, inspected().size)
        assertEquals(MissionResult.COMPLETED, finished().result)
    }

    @Test
    fun `a critical temperature stops the round until the operator decides`() {
        startFullRound()
        simulator.trigger(SimulationScenario.HIGH_TEMPERATURE)
        runUntil { simulator.state == OperationalState.AWAITING_DECISION }

        val alert = simulator.alerts().first()
        assertEquals(AlertCode.TEMPERATURE_HIGH, alert.code)
        assertEquals(AlertSeverity.CRITICAL, alert.severity)
        assertEquals(CommandOutcome.Confirmed, simulator.handle(Command.DecideIncident("c-2", alert.id, IncidentDecision.CONTINUE)))
        assertEquals(OperationalState.ON_MISSION, simulator.state)
    }

    @Test
    fun `low battery warns and a critical level sends the robot back`() {
        startFullRound()
        runUntil { simulator.state == OperationalState.ON_MISSION && inspected().isNotEmpty() }
        simulator.trigger(SimulationScenario.LOW_BATTERY)
        runUntil { simulator.state == OperationalState.AT_BASE }

        val codes = simulator.alerts().map { it.code }
        assertTrue(AlertCode.BATTERY_LOW in codes)
        assertTrue(AlertCode.BATTERY_CRITICAL in codes)
        assertEquals(MissionResult.INTERRUPTED_LOW_BATTERY, finished().result)
    }

    @Test
    fun `without link the robot finishes the checkpoint and waits`() {
        startFullRound()
        simulator.setLinkAvailable(false)
        runUntil { simulator.state == OperationalState.PAUSED }
        assertEquals(1, inspected().size)
    }

    @Test
    fun `commands that do not apply are rejected`() {
        assertEquals(CommandOutcome.Rejected(RejectionReason.INVALID_STATE), simulator.handle(Command.Pause("c-1")))
        assertEquals(CommandOutcome.Rejected(RejectionReason.NO_ACTIVE_MISSION), simulator.handle(Command.Cancel("c-2")))
        startFullRound()
        assertEquals(CommandOutcome.Rejected(RejectionReason.NOT_AT_BASE), simulator.handle(Command.StartMission("c-3", MissionType.FULL_ROUND)))
    }

    @Test
    fun `telemetry reports progress over the mission`() {
        startFullRound()
        runUntil { inspected().size == 2 }
        val telemetry = simulator.telemetry(LinkQuality(80, -61))
        val mission = telemetry.mission!!
        assertEquals(2, mission.completed)
        assertEquals(8, mission.total)
        assertEquals("CP3", mission.currentCheckpointId)
        assertEquals(3, mission.currentPosition)
    }
}
