package ar.edu.uade.go2rondas.data.mapper

import ar.edu.uade.go2rondas.data.local.entity.CheckpointResultEntity
import ar.edu.uade.go2rondas.data.local.entity.EventEntity
import ar.edu.uade.go2rondas.data.local.entity.MissionEntity
import ar.edu.uade.go2rondas.data.local.entity.MissionSummaryRow
import ar.edu.uade.go2rondas.domain.model.AlertCode
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.CheckpointResult
import ar.edu.uade.go2rondas.domain.model.CheckpointStatus
import ar.edu.uade.go2rondas.domain.model.CommandStatus
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.EnvironmentReading
import ar.edu.uade.go2rondas.domain.model.HistoryEvent
import ar.edu.uade.go2rondas.domain.model.MissionRecord
import ar.edu.uade.go2rondas.domain.model.MissionResult
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.RejectionReason

private object EventKind {
    const val ALERT = "ALERT"
    const val COMMAND = "COMMAND"
    const val CONNECTION_LOST = "CONNECTION_LOST"
    const val CONNECTION_RESTORED = "CONNECTION_RESTORED"
    const val ESCALATED = "ESCALATED"
}

fun MissionSummaryRow.toDomain() = MissionRecord(
    id = mission.id,
    robotId = mission.robotId,
    siteId = mission.siteId,
    type = MissionType.valueOf(mission.type),
    startedAtMillis = mission.startedAt,
    endedAtMillis = mission.endedAt,
    result = mission.result?.let(MissionResult::valueOf),
    completed = inspected,
    total = mission.total,
    anomalies = anomalies,
    skipped = skipped
)

fun newMissionEntity(id: String, robotId: String, siteId: String, type: MissionType, total: Int, startedAt: Long) =
    MissionEntity(id = id, robotId = robotId, siteId = siteId, type = type.name, total = total, startedAt = startedAt)

fun CheckpointResultEntity.toDomain() = CheckpointResult(
    missionId = missionId,
    checkpointId = checkpointId,
    status = CheckpointStatus.valueOf(status),
    reading = temperatureC?.let { temperature ->
        EnvironmentReading(
            checkpointId = checkpointId,
            temperatureC = temperature,
            humidityPct = humidityPct ?: 0.0,
            noiseDba = noiseDba ?: 0.0,
            noiseBaselineDba = noiseBaselineDba ?: 0.0
        )
    },
    timestampMillis = timestamp
)

fun CheckpointResult.toEntity() = CheckpointResultEntity(
    missionId = missionId,
    checkpointId = checkpointId,
    status = status.name,
    temperatureC = reading?.temperatureC,
    humidityPct = reading?.humidityPct,
    noiseDba = reading?.noiseDba,
    noiseBaselineDba = reading?.noiseBaselineDba,
    timestamp = timestampMillis
)

fun HistoryEvent.toEntity(missionId: String?): EventEntity = when (this) {
    is HistoryEvent.AlertRaised -> EventEntity(
        missionId = missionId, timestamp = timestampMillis, kind = EventKind.ALERT,
        alertCode = code.name, severity = severity.name, checkpointId = checkpointId
    )
    is HistoryEvent.CommandSent -> EventEntity(
        missionId = missionId, timestamp = timestampMillis, kind = EventKind.COMMAND,
        commandType = type.name, commandStatus = status.name, rejectionReason = rejectionReason?.name
    )
    is HistoryEvent.ConnectionLost -> EventEntity(
        missionId = missionId, timestamp = timestampMillis, kind = EventKind.CONNECTION_LOST
    )
    is HistoryEvent.ConnectionRestored -> EventEntity(
        missionId = missionId, timestamp = timestampMillis, kind = EventKind.CONNECTION_RESTORED,
        downtimeSeconds = downtimeSeconds
    )
    is HistoryEvent.IncidentEscalated -> EventEntity(
        missionId = missionId, timestamp = timestampMillis, kind = EventKind.ESCALATED,
        alertCode = code.name, checkpointId = checkpointId
    )
}

/** Devuelve null si la fila está incompleta (no debería pasar, pero no rompe la pantalla). */
fun EventEntity.toDomain(): HistoryEvent? = when (kind) {
    EventKind.ALERT -> {
        val code = alertCode?.let(AlertCode::valueOf)
        val level = severity?.let(AlertSeverity::valueOf)
        if (code != null && level != null) HistoryEvent.AlertRaised(timestamp, code, level, checkpointId) else null
    }
    EventKind.COMMAND -> {
        val type = commandType?.let(CommandType::valueOf)
        val status = commandStatus?.let(CommandStatus::valueOf)
        if (type != null && status != null) {
            HistoryEvent.CommandSent(timestamp, type, status, rejectionReason?.let(RejectionReason::valueOf))
        } else null
    }
    EventKind.CONNECTION_LOST -> HistoryEvent.ConnectionLost(timestamp)
    EventKind.CONNECTION_RESTORED -> HistoryEvent.ConnectionRestored(timestamp, downtimeSeconds ?: 0)
    EventKind.ESCALATED -> alertCode?.let { HistoryEvent.IncidentEscalated(timestamp, AlertCode.valueOf(it), checkpointId) }
    else -> null
}
