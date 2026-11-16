package ar.edu.uade.go2rondas.domain.model

/** Eventos que el robot informa además de la telemetría periódica. */
sealed interface RobotEvent {
    val timestampMillis: Long

    data class MissionStarted(
        override val timestampMillis: Long,
        val missionId: String,
        val type: MissionType,
        val total: Int
    ) : RobotEvent

    data class CheckpointInspected(
        override val timestampMillis: Long,
        val result: CheckpointResult
    ) : RobotEvent

    data class AlertRaised(
        override val timestampMillis: Long,
        val missionId: String?,
        val alert: Alert
    ) : RobotEvent

    data class MissionFinished(
        override val timestampMillis: Long,
        val missionId: String,
        val result: MissionResult,
        val completed: Int,
        val total: Int
    ) : RobotEvent
}

data class CheckpointResult(
    val missionId: String,
    val checkpointId: String,
    val status: CheckpointStatus,
    val reading: EnvironmentReading?,
    val timestampMillis: Long
)

enum class CheckpointStatus { OK, ANOMALY, SKIPPED }

enum class MissionResult { COMPLETED, INTERRUPTED_LOW_BATTERY, CANCELLED }
