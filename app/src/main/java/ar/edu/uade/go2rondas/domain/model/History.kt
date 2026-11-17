package ar.edu.uade.go2rondas.domain.model

/** Ronda guardada en el historial local. */
data class MissionRecord(
    val id: String,
    val robotId: String,
    val siteId: String,
    val type: MissionType,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val result: MissionResult?,
    val completed: Int,
    val total: Int,
    val anomalies: Int,
    val skipped: Int
)

data class MissionDetail(
    val record: MissionRecord,
    val checkpoints: List<CheckpointResult>,
    val events: List<HistoryEvent>
)

/** Hechos que quedan registrados durante una ronda, además de los checkpoints. */
sealed interface HistoryEvent {
    val timestampMillis: Long

    data class AlertRaised(
        override val timestampMillis: Long,
        val code: AlertCode,
        val severity: AlertSeverity,
        val checkpointId: String?
    ) : HistoryEvent

    data class CommandSent(
        override val timestampMillis: Long,
        val type: CommandType,
        val status: CommandStatus,
        val rejectionReason: RejectionReason?
    ) : HistoryEvent

    data class ConnectionLost(override val timestampMillis: Long) : HistoryEvent

    data class ConnectionRestored(
        override val timestampMillis: Long,
        val downtimeSeconds: Long
    ) : HistoryEvent

    data class IncidentEscalated(
        override val timestampMillis: Long,
        val code: AlertCode,
        val checkpointId: String?
    ) : HistoryEvent
}
