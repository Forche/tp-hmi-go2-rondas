package ar.edu.uade.go2rondas.domain.model

/** Orden del operador al robot. El id permite que el robot ignore duplicados. */
sealed interface Command {
    val id: String
    val type: CommandType

    data class StartMission(
        override val id: String,
        val mission: MissionType,
        /** Sólo para la inspección puntual. */
        val checkpointId: String? = null
    ) : Command {
        override val type = CommandType.START_MISSION
    }

    data class Pause(override val id: String) : Command {
        override val type = CommandType.PAUSE
    }

    data class Resume(override val id: String) : Command {
        override val type = CommandType.RESUME
    }

    data class Cancel(override val id: String) : Command {
        override val type = CommandType.CANCEL
    }

    data class ReturnToBase(override val id: String) : Command {
        override val type = CommandType.RETURN_TO_BASE
    }

    data class OperationalStop(override val id: String) : Command {
        override val type = CommandType.OPERATIONAL_STOP
    }

    data class AcknowledgeAlert(override val id: String, val alertId: String) : Command {
        override val type = CommandType.ACKNOWLEDGE_ALERT
    }

    data class DecideIncident(
        override val id: String,
        val alertId: String,
        val decision: IncidentDecision
    ) : Command {
        override val type = CommandType.DECIDE_INCIDENT
    }
}

enum class CommandType {
    START_MISSION,
    PAUSE,
    RESUME,
    CANCEL,
    RETURN_TO_BASE,
    OPERATIONAL_STOP,
    ACKNOWLEDGE_ALERT,
    DECIDE_INCIDENT
}

/** Respuesta a una orden. Confirmada significa aceptada, no ejecutada. */
sealed interface CommandOutcome {
    data object Confirmed : CommandOutcome
    data class Rejected(val reason: RejectionReason) : CommandOutcome
    data object NoResponse : CommandOutcome
}

enum class RejectionReason {
    LOW_BATTERY,
    NOT_AT_BASE,
    NO_ACTIVE_MISSION,
    INVALID_STATE,
    ON_STAIRS,
    OBSTACLE_PERSISTS,
    ALERT_NOT_FOUND,
    SIMULATED_FAILURE
}

enum class CommandStatus { PENDING, CONFIRMED, REJECTED, NO_RESPONSE }

/** Última orden enviada y su estado visible. */
data class CommandRecord(
    val command: Command,
    val status: CommandStatus,
    val sentAtMillis: Long,
    val rejectionReason: RejectionReason? = null
)
