package ar.edu.uade.go2rondas.data.sim

import ar.edu.uade.go2rondas.data.site.TestSites
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

    private val site = TestSites.load()
    private val simulator = RobotSimulator(site.config, site.simulation!!, "go2-01", Random(42)) { 0L }
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
    fun `a critical temperature is reported and the round goes on`() {
        startFullRound()
        simulator.trigger(SimulationScenario.HIGH_TEMPERATURE)
        runUntil { simulator.alerts().any { it.code == AlertCode.TEMPERATURE_HIGH } }

        val alert = simulator.alerts().first()
        assertEquals(AlertSeverity.CRITICAL, alert.severity)
        assertTrue(!alert.requiresDecision)
        runUntil { simulator.state == OperationalState.AT_BASE }
        assertEquals(MissionResult.COMPLETED, finished().result)
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
    fun `without battery to come back the robot does not go down to the basement`() {
        startFullRound()
        runUntil { inspected().size == 5 }
        simulator.trigger(SimulationScenario.LOW_BATTERY)
        runUntil { simulator.state == OperationalState.AT_BASE }

        assertTrue(AlertCode.BATTERY_INSUFFICIENT_FOR_BASEMENT in simulator.alerts().map { it.code })
        assertEquals(listOf("CP1", "CP2", "CP3", "CP4", "CP5"), inspected().map { it.checkpointId })
        assertEquals(MissionResult.INTERRUPTED_LOW_BATTERY, finished().result)
    }

    @Test
    fun `with enough battery the robot goes down to the basement`() {
        startFullRound()
        runUntil { simulator.state == OperationalState.AT_BASE }

        assertTrue(simulator.alerts().none { it.code == AlertCode.BATTERY_INSUFFICIENT_FOR_BASEMENT })
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

    @Test
    fun `water is reported and the robot goes around it without asking`() {
        startFullRound()
        simulator.trigger(SimulationScenario.WATER_LEAK)
        runUntil { simulator.alerts().any { it.code == AlertCode.WATER_DETECTED } }

        val alert = simulator.alerts().first()
        assertEquals(AlertSeverity.CRITICAL, alert.severity)
        assertTrue(!alert.requiresDecision)
        runUntil { simulator.state == OperationalState.AT_BASE }
        assertEquals(MissionResult.COMPLETED, finished().result)
    }

    @Test
    fun `an obstacle on the way is avoided without asking`() {
        startFullRound()
        simulator.trigger(SimulationScenario.OBSTACLE_PATH)
        runUntil { simulator.alerts().any { it.code == AlertCode.OBSTACLE_AVOIDED } }

        assertTrue(!simulator.alerts().first().requiresDecision)
        runUntil { simulator.state == OperationalState.AT_BASE }
        assertEquals(MissionResult.COMPLETED, finished().result)
        assertTrue(inspected().none { it.status == CheckpointStatus.SKIPPED })
    }

    @Test
    fun `smoke ends the round and sends the robot back`() {
        startFullRound()
        runUntil { inspected().size == 1 }
        simulator.trigger(SimulationScenario.SMOKE)
        runUntil { simulator.state == OperationalState.RETURNING }

        val alert = simulator.alerts().first()
        assertEquals(AlertCode.SMOKE_DETECTED, alert.code)
        assertEquals(AlertSeverity.CRITICAL, alert.severity)
        assertTrue(!alert.requiresDecision)
        runUntil { simulator.state == OperationalState.AT_BASE }
        assertEquals(MissionResult.INTERRUPTED, finished().result)
    }

    @Test
    fun `at x10 the robot still sees what is on the way`() {
        // Con el modo demo a x10 cada ciclo son 10 s simulados: el robot avanza 5 m entre dos ciclos.
        startFullRound()
        simulator.trigger(SimulationScenario.OBSTACLE_PATH)
        simulator.trigger(SimulationScenario.WATER_LEAK)
        repeat(60) { simulator.tick(10.0) }
        val codes = simulator.alerts().map { it.code }
        assertTrue(AlertCode.OBSTACLE_AVOIDED in codes)
        assertTrue(AlertCode.WATER_DETECTED in codes)
    }

    @Test
    fun `what was asked and did not show up does not carry over to the next round`() {
        startFullRound()
        runUntil { inspected().size == 1 }
        simulator.handle(Command.ReturnToBase("c-2"))
        simulator.trigger(SimulationScenario.SMOKE)  // vuelve hacia atrás: el humo no llega a aparecer
        runUntil { simulator.state == OperationalState.AT_BASE }

        events.clear()
        assertEquals(CommandOutcome.Confirmed, simulator.handle(Command.StartMission("c-3", MissionType.FULL_ROUND)))
        runUntil { inspected().size == 2 }
        assertTrue(simulator.alerts().none { it.code == AlertCode.SMOKE_DETECTED })
    }

    @Test
    fun `return to base interrupts the round and cancel cancels it`() {
        startFullRound()
        runUntil { inspected().size == 1 }
        assertEquals(CommandOutcome.Confirmed, simulator.handle(Command.ReturnToBase("c-2")))
        runUntil { simulator.state == OperationalState.AT_BASE }
        assertEquals(MissionResult.INTERRUPTED, finished().result)

        events.clear()
        startFullRound()
        runUntil { inspected().size == 1 }
        assertEquals(CommandOutcome.Confirmed, simulator.handle(Command.Cancel("c-3")))
        runUntil { simulator.state == OperationalState.AT_BASE }
        assertEquals(MissionResult.CANCELLED, finished().result)
    }

    @Test
    fun `humidity and noise out of range raise alerts`() {
        startFullRound()
        simulator.trigger(SimulationScenario.HUMIDITY_ANOMALY)
        simulator.trigger(SimulationScenario.NOISE_ANOMALY)
        runUntil { inspected().size == 1 }

        assertEquals(CheckpointStatus.ANOMALY, inspected().first().status)
        val codes = simulator.alerts().map { it.code }.toSet()
        assertTrue(codes.containsAll(setOf(AlertCode.HUMIDITY_OUT_OF_RANGE, AlertCode.NOISE_HIGH)))
    }

    @Test
    fun `random situations happen during the round and the ones that end it only once`() {
        val situations = RandomSituations(Random(3))
        val happened = (1..2_000).mapNotNull { situations.tick(10.0, "m-1") }
        assertTrue(happened.size > 100)
        assertTrue(happened.count { it == SimulationScenario.LOW_BATTERY } <= 1)
        assertTrue(happened.count { it == SimulationScenario.SMOKE } <= 1)
    }
}
