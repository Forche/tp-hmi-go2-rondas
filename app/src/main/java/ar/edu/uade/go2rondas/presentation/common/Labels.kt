package ar.edu.uade.go2rondas.presentation.common

import androidx.annotation.StringRes
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.domain.model.AlertCode
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.CheckpointStatus
import ar.edu.uade.go2rondas.domain.model.CommandStatus
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.DisabledReason
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.MissionResult
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.domain.model.RejectionReason

// Cómo se muestra cada valor del dominio. El formato vive en presentación: el dominio no sabe
// cómo se ve en pantalla. (El proyecto de referencia de la cátedra lo pone en el dominio;
// acá se separa a propósito, sin crear un modelo de UI aparte.)

@StringRes
fun OperationalState.labelRes(): Int = when (this) {
    OperationalState.AT_BASE -> R.string.state_at_base
    OperationalState.ON_MISSION -> R.string.state_on_mission
    OperationalState.PAUSED -> R.string.state_paused
    OperationalState.AWAITING_DECISION -> R.string.state_awaiting_decision
    OperationalState.RETURNING -> R.string.state_returning
    OperationalState.STOPPED -> R.string.state_stopped
}

@StringRes
fun LinkState.labelRes(): Int = when (this) {
    LinkState.CONNECTED -> R.string.link_connected
    LinkState.DEGRADED -> R.string.link_degraded
    LinkState.DISCONNECTED -> R.string.link_disconnected
}

@StringRes
fun CommandType.labelRes(): Int = when (this) {
    CommandType.START_MISSION -> R.string.command_start
    CommandType.PAUSE -> R.string.command_pause
    CommandType.RESUME -> R.string.command_resume
    CommandType.CANCEL -> R.string.command_cancel
    CommandType.RETURN_TO_BASE -> R.string.command_return
    CommandType.OPERATIONAL_STOP -> R.string.command_stop
    CommandType.ACKNOWLEDGE_ALERT -> R.string.command_acknowledge
    CommandType.DECIDE_INCIDENT -> R.string.command_decide
}

@StringRes
fun CommandStatus.labelRes(): Int = when (this) {
    CommandStatus.PENDING -> R.string.command_status_pending
    CommandStatus.CONFIRMED -> R.string.command_status_confirmed
    CommandStatus.REJECTED -> R.string.command_status_rejected
    CommandStatus.NO_RESPONSE -> R.string.command_status_no_response
}

@StringRes
fun DisabledReason.labelRes(): Int = when (this) {
    DisabledReason.NO_TELEMETRY -> R.string.reason_no_telemetry
    DisabledReason.NOT_CONNECTED -> R.string.reason_not_connected
    DisabledReason.COMMAND_PENDING -> R.string.reason_command_pending
    DisabledReason.NOT_AT_BASE -> R.string.reason_not_at_base
    DisabledReason.BATTERY_TOO_LOW -> R.string.reason_battery_too_low
    DisabledReason.UNACKNOWLEDGED_CRITICAL_ALERT -> R.string.reason_unacknowledged_critical
    DisabledReason.NOT_ON_MISSION -> R.string.reason_not_on_mission
    DisabledReason.NOT_PAUSED_OR_STOPPED -> R.string.reason_not_paused
    DisabledReason.NO_ACTIVE_MISSION -> R.string.reason_no_active_mission
    DisabledReason.ALREADY_AT_BASE -> R.string.reason_already_at_base
    DisabledReason.ALREADY_RETURNING -> R.string.reason_already_returning
    DisabledReason.ALREADY_STOPPED -> R.string.reason_already_stopped
    DisabledReason.NO_ALERTS_TO_ACKNOWLEDGE -> R.string.reason_no_alerts
    DisabledReason.NO_DECISION_REQUIRED -> R.string.reason_no_decision
    DisabledReason.ALERT_NOT_ACKNOWLEDGED -> R.string.reason_alert_not_acknowledged
}

@StringRes
fun RejectionReason.labelRes(): Int = when (this) {
    RejectionReason.LOW_BATTERY -> R.string.rejection_low_battery
    RejectionReason.NOT_AT_BASE -> R.string.rejection_not_at_base
    RejectionReason.NO_ACTIVE_MISSION -> R.string.rejection_no_active_mission
    RejectionReason.INVALID_STATE -> R.string.rejection_invalid_state
    RejectionReason.ON_STAIRS -> R.string.rejection_on_stairs
    RejectionReason.OBSTACLE_PERSISTS -> R.string.rejection_obstacle_persists
    RejectionReason.ALERT_NOT_FOUND -> R.string.rejection_alert_not_found
    RejectionReason.SIMULATED_FAILURE -> R.string.rejection_simulated
}

@StringRes
fun AlertSeverity.labelRes(): Int = when (this) {
    AlertSeverity.INFO -> R.string.alert_severity_info
    AlertSeverity.WARNING -> R.string.alert_severity_warning
    AlertSeverity.CRITICAL -> R.string.alert_severity_critical
}

@StringRes
fun AlertCode.titleRes(): Int = when (this) {
    AlertCode.OBSTACLE -> R.string.alert_obstacle
    AlertCode.TEMPERATURE_HIGH -> R.string.alert_temperature_high
    AlertCode.HUMIDITY_OUT_OF_RANGE -> R.string.alert_humidity
    AlertCode.NOISE_HIGH -> R.string.alert_noise
    AlertCode.BATTERY_LOW -> R.string.alert_battery_low
    AlertCode.BATTERY_CRITICAL -> R.string.alert_battery_critical
    AlertCode.BATTERY_INSUFFICIENT_FOR_BASEMENT -> R.string.alert_battery_basement
}

@StringRes
fun CheckpointStatus.labelRes(): Int = when (this) {
    CheckpointStatus.OK -> R.string.checkpoint_ok
    CheckpointStatus.ANOMALY -> R.string.checkpoint_anomaly
    CheckpointStatus.SKIPPED -> R.string.checkpoint_skipped
}

@StringRes
fun MissionType.nameRes(): Int = when (this) {
    MissionType.FULL_ROUND -> R.string.mission_full_round
    MissionType.GROUND_FLOOR_ROUND -> R.string.mission_ground_floor_round
    MissionType.SPOT_CHECK -> R.string.mission_spot_check
}

@StringRes
fun MissionType.detailRes(): Int = when (this) {
    MissionType.FULL_ROUND -> R.string.mission_full_round_detail
    MissionType.GROUND_FLOOR_ROUND -> R.string.mission_ground_floor_round_detail
    MissionType.SPOT_CHECK -> R.string.mission_spot_check_detail
}

@StringRes
fun MissionResult?.labelRes(): Int = when (this) {
    MissionResult.COMPLETED -> R.string.result_completed
    MissionResult.INTERRUPTED_LOW_BATTERY -> R.string.result_interrupted
    MissionResult.CANCELLED -> R.string.result_cancelled
    MissionResult.ABORTED -> R.string.result_aborted
    null -> R.string.result_in_progress
}
