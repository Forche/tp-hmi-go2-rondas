package ar.edu.uade.go2rondas.domain.model

/** Todo lo que se sabe del robot en un instante, con las órdenes que hoy se pueden enviar. */
data class RobotSnapshot(
    val robot: Robot?,
    val connection: ConnectionStatus,
    val telemetry: Telemetry?,
    val alerts: List<Alert>,
    val lastCommand: CommandRecord?,
    val availability: CommandAvailability
) {
    val unacknowledgedAlerts: List<Alert> get() = alerts.filterNot { it.acknowledged }

    /** Alerta que tiene al robot esperando una decisión del operador. */
    val pendingDecision: Alert? get() = alerts.firstOrNull { it.requiresDecision }
}

/** Qué órdenes están habilitadas y, para las que no, por qué. */
data class CommandAvailability(val disabled: Map<CommandType, DisabledReason>) {
    fun isEnabled(type: CommandType): Boolean = type !in disabled
    fun reason(type: CommandType): DisabledReason? = disabled[type]

    companion object {
        val NoneEnabled = CommandAvailability(CommandType.entries.associateWith { DisabledReason.NO_TELEMETRY })
    }
}

enum class DisabledReason {
    NO_TELEMETRY,
    NOT_CONNECTED,
    COMMAND_PENDING,
    NOT_AT_BASE,
    BATTERY_TOO_LOW,
    UNACKNOWLEDGED_CRITICAL_ALERT,
    NOT_ON_MISSION,
    NOT_PAUSED_OR_STOPPED,
    NO_ACTIVE_MISSION,
    ALREADY_AT_BASE,
    ALREADY_RETURNING,
    ALREADY_STOPPED,
    NO_ALERTS_TO_ACKNOWLEDGE,
    NO_DECISION_REQUIRED,
    ALERT_NOT_ACKNOWLEDGED
}
