package ar.edu.uade.go2rondas.domain.usecase

import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.CommandAvailability
import ar.edu.uade.go2rondas.domain.model.CommandRecord
import ar.edu.uade.go2rondas.domain.model.CommandStatus
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.ConnectionStatus
import ar.edu.uade.go2rondas.domain.model.DisabledReason
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.domain.model.Telemetry
import javax.inject.Inject

/**
 * Condiciones de habilitación de cada orden (tabla de comandos del documento, sección 6).
 * Para cada orden devuelve el primer motivo que la deshabilita, o ninguno.
 */
class EvaluateCommandAvailabilityUseCase @Inject constructor() {

    operator fun invoke(
        telemetry: Telemetry?,
        connection: ConnectionStatus,
        alerts: List<Alert>,
        lastCommand: CommandRecord?,
        batteryMinToStartPct: Int
    ): CommandAvailability {
        if (telemetry == null) return CommandAvailability.NoneEnabled

        val state = telemetry.state
        val pending = lastCommand?.status == CommandStatus.PENDING
        val connected = connection.state != LinkState.DISCONNECTED
        val hasMission = telemetry.mission != null
        val unacknowledged = alerts.filterNot { it.acknowledged }
        val unacknowledgedCritical = unacknowledged.any { it.severity == AlertSeverity.CRITICAL }
        val decisionAlert = alerts.firstOrNull { it.requiresDecision }

        fun firstReason(vararg checks: Pair<Boolean, DisabledReason>): DisabledReason? =
            checks.firstOrNull { (fails, _) -> fails }?.second

        val reasons = mapOf(
            CommandType.START_MISSION to firstReason(
                pending to DisabledReason.COMMAND_PENDING,
                !connected to DisabledReason.NOT_CONNECTED,
                (state != OperationalState.AT_BASE) to DisabledReason.NOT_AT_BASE,
                (telemetry.battery.percent < batteryMinToStartPct) to DisabledReason.BATTERY_TOO_LOW,
                unacknowledgedCritical to DisabledReason.UNACKNOWLEDGED_CRITICAL_ALERT
            ),
            CommandType.PAUSE to firstReason(
                pending to DisabledReason.COMMAND_PENDING,
                (state != OperationalState.ON_MISSION) to DisabledReason.NOT_ON_MISSION
            ),
            CommandType.RESUME to firstReason(
                pending to DisabledReason.COMMAND_PENDING,
                (state != OperationalState.PAUSED && state != OperationalState.STOPPED) to DisabledReason.NOT_PAUSED_OR_STOPPED,
                !hasMission to DisabledReason.NO_ACTIVE_MISSION,
                !connected to DisabledReason.NOT_CONNECTED,
                unacknowledgedCritical to DisabledReason.UNACKNOWLEDGED_CRITICAL_ALERT
            ),
            CommandType.CANCEL to firstReason(
                pending to DisabledReason.COMMAND_PENDING,
                !hasMission to DisabledReason.NO_ACTIVE_MISSION,
                (state == OperationalState.RETURNING) to DisabledReason.ALREADY_RETURNING
            ),
            CommandType.RETURN_TO_BASE to firstReason(
                pending to DisabledReason.COMMAND_PENDING,
                (state == OperationalState.AT_BASE) to DisabledReason.ALREADY_AT_BASE,
                (state == OperationalState.RETURNING) to DisabledReason.ALREADY_RETURNING
            ),
            // La detención no espera a otra orden pendiente: es la acción de seguridad.
            CommandType.OPERATIONAL_STOP to firstReason(
                (state == OperationalState.AT_BASE) to DisabledReason.ALREADY_AT_BASE,
                (state == OperationalState.STOPPED) to DisabledReason.ALREADY_STOPPED
            ),
            CommandType.ACKNOWLEDGE_ALERT to firstReason(
                pending to DisabledReason.COMMAND_PENDING,
                unacknowledged.isEmpty() to DisabledReason.NO_ALERTS_TO_ACKNOWLEDGE
            ),
            CommandType.DECIDE_INCIDENT to firstReason(
                pending to DisabledReason.COMMAND_PENDING,
                (state != OperationalState.AWAITING_DECISION || decisionAlert == null) to DisabledReason.NO_DECISION_REQUIRED,
                (decisionAlert?.acknowledged == false) to DisabledReason.ALERT_NOT_ACKNOWLEDGED
            )
        )
        return CommandAvailability(
            disabled = reasons.mapNotNull { (type, reason) -> reason?.let { type to it } }.toMap()
        )
    }
}
