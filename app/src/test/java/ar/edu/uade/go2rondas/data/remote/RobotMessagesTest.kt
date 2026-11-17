package ar.edu.uade.go2rondas.data.remote

import ar.edu.uade.go2rondas.data.mapper.toDomain
import ar.edu.uade.go2rondas.data.mapper.toDto
import ar.edu.uade.go2rondas.data.mapper.toOutcome
import ar.edu.uade.go2rondas.data.remote.dto.AlertsDto
import ar.edu.uade.go2rondas.data.remote.dto.CommandDto
import ar.edu.uade.go2rondas.data.remote.dto.CommandResponseDto
import ar.edu.uade.go2rondas.data.remote.dto.EventDto
import ar.edu.uade.go2rondas.data.remote.dto.ServerMessage
import ar.edu.uade.go2rondas.data.remote.dto.TelemetryDto
import ar.edu.uade.go2rondas.domain.model.AlertCode
import ar.edu.uade.go2rondas.domain.model.CheckpointStatus
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandOutcome
import ar.edu.uade.go2rondas.domain.model.IncidentDecision
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.domain.model.RejectionReason
import ar.edu.uade.go2rondas.domain.model.RobotEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mensajes tal como los envía el servidor de MuJoCo (copiados de su salida). */
class RobotMessagesTest {

    private fun decode(text: String): ServerMessage = RobotJson.decodeFromString(ServerMessage.serializer(), text)

    @Test
    fun `telemetry is decoded and mapped to the domain`() {
        val message = decode(
            """{"type":"telemetry","robot_id":"go2-02","seq":8,"timestamp":1794917990081,
            "connection":{"latency_ms":85,"rssi_dbm":-61},
            "battery":{"percent":91,"charging":false,"est_minutes":126},"state":"ON_MISSION",
            "mission":{"mission_id":"m-1","type":"FULL_ROUND","current_cp":"CP2","current_position":2,
            "completed":1,"total":8,"elapsed_s":70},
            "location":{"level":"GROUND_FLOOR","from_cp":"CP1","to_cp":"CP2","x":20.2,"y":3.6,"heading_deg":0,"confidence":0.94},
            "motion":{"speed_mps":0.45,"moving":true},"active_alerts":0,
            "environment":{"checkpoint_id":"CP1","temperature_c":31.4,"humidity_pct":44.2,"noise_dba":66.3,"noise_baseline_dba":66.0}}"""
        )
        val telemetry = (message as TelemetryDto).toDomain()!!
        assertEquals(OperationalState.ON_MISSION, telemetry.state)
        assertEquals("CP2", telemetry.mission?.currentCheckpointId)
        assertEquals(20.2f, telemetry.location.position.x)
        assertEquals("CP1", telemetry.environment?.checkpointId)
    }

    @Test
    fun `events are mapped by kind`() {
        val result = decode(
            """{"type":"event","kind":"CHECKPOINT_RESULT","timestamp":1,"mission_id":"m-1",
            "checkpoint_id":"CP3","status":"SKIPPED","reading":null}"""
        )
        val event = (result as EventDto).toDomain() as RobotEvent.CheckpointInspected
        assertEquals(CheckpointStatus.SKIPPED, event.result.status)
        assertNull(event.result.reading)

        val started = (decode("""{"type":"event","kind":"MISSION_STARTED","timestamp":1,"mission_id":"m-1","mission_type":"FULL_ROUND","total":8}""") as EventDto).toDomain()
        assertEquals(MissionType.FULL_ROUND, (started as RobotEvent.MissionStarted).type)
    }

    @Test
    fun `unknown values are ignored instead of crashing`() {
        val unknownKind = decode("""{"type":"event","kind":"SOMETHING_NEW","timestamp":1}""") as EventDto
        assertNull(unknownKind.toDomain())
        val alerts = decode(
            """{"type":"alerts","alerts":[
            {"id":"a-1","timestamp":1,"severity":"WARNING","code":"OBSTACLE","checkpoint_id":"CP3",
            "requires_decision":true,"options":["SKIP","RETRY"],"acknowledged":false},
            {"id":"a-2","timestamp":1,"severity":"WARNING","code":"NEW_CODE","requires_decision":false,"acknowledged":false}]}"""
        ) as AlertsDto
        val mapped = alerts.alerts.mapNotNull { it.toDomain() }
        assertEquals(listOf(AlertCode.OBSTACLE), mapped.map { it.code })
        assertEquals(listOf(IncidentDecision.SKIP, IncidentDecision.RETRY), mapped.first().decisionOptions)
    }

    @Test
    fun `command responses become outcomes`() {
        val rejected = decode("""{"type":"command_response","command_id":"c-1","status":"REJECTED","reason":"OBSTACLE_PERSISTS","responded_at":1}""")
        assertEquals(CommandOutcome.Rejected(RejectionReason.OBSTACLE_PERSISTS), (rejected as CommandResponseDto).toOutcome())
        val accepted = decode("""{"type":"command_response","command_id":"c-2","status":"ACCEPTED","reason":null}""")
        assertEquals(CommandOutcome.Confirmed, (accepted as CommandResponseDto).toOutcome())
    }

    @Test
    fun `commands are encoded with only the params they need`() {
        val text = RobotJson.encodeToString(CommandDto.serializer(), Command.DecideIncident("c-9", "a-1", IncidentDecision.SKIP).toDto("go2-02"))
        assertTrue(""""type":"command"""" in text)
        assertTrue(""""name":"INCIDENT_DECISION"""" in text)
        assertTrue(""""params":{"alert_id":"a-1","decision":"SKIP"}""" in text)
        assertFalse("mission" in text)
    }
}
