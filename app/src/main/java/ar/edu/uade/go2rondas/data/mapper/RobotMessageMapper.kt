package ar.edu.uade.go2rondas.data.mapper

import ar.edu.uade.go2rondas.data.remote.dto.AlertDto
import ar.edu.uade.go2rondas.data.remote.dto.CommandDto
import ar.edu.uade.go2rondas.data.remote.dto.CommandParamsDto
import ar.edu.uade.go2rondas.data.remote.dto.CommandResponseDto
import ar.edu.uade.go2rondas.data.remote.dto.EventDto
import ar.edu.uade.go2rondas.data.remote.dto.ReadingDto
import ar.edu.uade.go2rondas.data.remote.dto.TelemetryDto
import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.AlertCode
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.Battery
import ar.edu.uade.go2rondas.domain.model.CheckpointResult
import ar.edu.uade.go2rondas.domain.model.CheckpointStatus
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
import ar.edu.uade.go2rondas.domain.model.PlanPoint
import ar.edu.uade.go2rondas.domain.model.RejectionReason
import ar.edu.uade.go2rondas.domain.model.RobotEvent
import ar.edu.uade.go2rondas.domain.model.RobotLocation
import ar.edu.uade.go2rondas.domain.model.Telemetry

/** Un valor que no está en el enum devuelve null: un servidor más nuevo no rompe la app. */
private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
    enumValues<T>().firstOrNull { it.name == this }

/** Devuelve null si el mensaje trae un estado desconocido. */
fun TelemetryDto.toDomain(): Telemetry? {
    val state = state.toEnumOrNull<OperationalState>() ?: return null
    return Telemetry(
        robotId = robotId,
        sequence = seq,
        timestampMillis = timestamp,
        link = LinkQuality(connection.latencyMs, connection.rssiDbm),
        battery = Battery(battery.percent, battery.charging, battery.estimatedMinutes),
        state = state,
        mission = mission?.let { m ->
            m.type.toEnumOrNull<MissionType>()?.let { type ->
                MissionProgress(m.missionId, type, m.currentCheckpointId, m.currentPosition, m.completed, m.total, m.elapsedSeconds)
            }
        },
        location = RobotLocation(
            level = location.level.toEnumOrNull<Level>() ?: Level.GROUND_FLOOR,
            fromCheckpointId = location.fromCheckpointId,
            toCheckpointId = location.toCheckpointId,
            position = PlanPoint(location.x, location.y),
            headingDeg = location.headingDeg,
            confidence = location.confidence
        ),
        motion = Motion(motion.speedMps, motion.moving),
        activeAlerts = activeAlerts,
        environment = environment?.toDomain()
    )
}

fun ReadingDto.toDomain() = EnvironmentReading(checkpointId, temperatureC, humidityPct, noiseDba, noiseBaselineDba)

fun AlertDto.toDomain(): Alert? {
    val level = severity.toEnumOrNull<AlertSeverity>() ?: return null
    val alertCode = code.toEnumOrNull<AlertCode>() ?: return null
    return Alert(
        id = id,
        timestampMillis = timestamp,
        severity = level,
        code = alertCode,
        checkpointId = checkpointId,
        measuredValue = measured,
        threshold = threshold,
        requiresDecision = requiresDecision,
        decisionOptions = options.mapNotNull { it.toEnumOrNull<IncidentDecision>() },
        acknowledged = acknowledged,
        photoId = photoId
    )
}

/** Devuelve null si el evento está incompleto o es de un tipo que la app no conoce. */
fun EventDto.toDomain(): RobotEvent? = when (kind) {
    "MISSION_STARTED" -> {
        val type = missionType?.toEnumOrNull<MissionType>()
        if (missionId != null && type != null && total != null) RobotEvent.MissionStarted(timestamp, missionId, type, total) else null
    }
    "CHECKPOINT_RESULT" -> {
        val checkpointStatus = status?.toEnumOrNull<CheckpointStatus>()
        if (missionId != null && checkpointId != null && checkpointStatus != null) {
            RobotEvent.CheckpointInspected(
                timestamp,
                CheckpointResult(missionId, checkpointId, checkpointStatus, reading?.toDomain(), timestamp)
            )
        } else null
    }
    "ALERT" -> alert?.toDomain()?.let { RobotEvent.AlertRaised(timestamp, missionId, it) }
    "MISSION_FINISHED" -> {
        val missionResult = result?.toEnumOrNull<MissionResult>()
        if (missionId != null && missionResult != null && completed != null && total != null) {
            RobotEvent.MissionFinished(timestamp, missionId, missionResult, completed, total)
        } else null
    }
    else -> null
}

fun CommandResponseDto.toOutcome(): CommandOutcome = when (status) {
    "ACCEPTED" -> CommandOutcome.Confirmed
    else -> CommandOutcome.Rejected(reason?.toEnumOrNull<RejectionReason>() ?: RejectionReason.INVALID_STATE)
}

fun Command.toDto(robotId: String): CommandDto {
    val (name, params) = when (this) {
        is Command.StartMission -> "START_MISSION" to CommandParamsDto(mission = mission.name, checkpointId = checkpointId)
        is Command.Pause -> "PAUSE" to CommandParamsDto()
        is Command.Resume -> "RESUME" to CommandParamsDto()
        is Command.Cancel -> "CANCEL" to CommandParamsDto()
        is Command.ReturnToBase -> "RETURN_TO_BASE" to CommandParamsDto()
        is Command.OperationalStop -> "OPERATIONAL_STOP" to CommandParamsDto()
        is Command.AcknowledgeAlert -> "ACK_ALERT" to CommandParamsDto(alertId = alertId)
        is Command.DecideIncident -> "INCIDENT_DECISION" to CommandParamsDto(alertId = alertId, decision = decision.name)
    }
    return CommandDto(commandId = id, robotId = robotId, name = name, params = params)
}
