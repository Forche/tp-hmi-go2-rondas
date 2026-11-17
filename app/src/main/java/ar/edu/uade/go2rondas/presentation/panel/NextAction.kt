package ar.edu.uade.go2rondas.presentation.panel

import androidx.annotation.StringRes
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.SiteConfig

/**
 * Qué está haciendo el robot y qué tiene que hacer el operador, en una frase: la información que
 * va primero en el Panel. [checkpoint] es el punto en curso ("CP3 · Pasillo frío"), si hace falta.
 */
data class NextAction(@param:StringRes val text: Int, val checkpoint: String? = null, val number: Int? = null)

fun nextAction(snapshot: RobotSnapshot, site: SiteConfig): NextAction? {
    val telemetry = snapshot.telemetry ?: return null
    if (snapshot.connection.state == LinkState.DISCONNECTED) return NextAction(R.string.next_disconnected)
    val thresholds = site.thresholds
    val lowBattery = telemetry.battery.percent < thresholds.batteryWarningPct
    val checkpoint = telemetry.mission?.currentCheckpointId?.let { id ->
        site.checkpoint(id)?.let { "${it.id} · ${it.name}" } ?: id
    }
    return when (telemetry.state) {
        OperationalState.AT_BASE -> when {
            // Lo mismo que bloquea "Iniciar": así el texto nunca promete una acción deshabilitada.
            snapshot.unacknowledgedAlerts.any { it.severity == AlertSeverity.CRITICAL } -> NextAction(R.string.next_ack_critical)
            telemetry.battery.percent < thresholds.batteryMinToStartPct ->
                NextAction(R.string.next_charging, number = thresholds.batteryMinToStartPct)
            else -> NextAction(R.string.next_ready)
        }
        OperationalState.ON_MISSION -> when {
            lowBattery -> NextAction(R.string.next_low_battery)
            checkpoint == null -> NextAction(R.string.next_on_mission_generic)
            telemetry.motion.moving -> NextAction(R.string.next_on_mission, checkpoint)
            else -> NextAction(R.string.next_measuring, checkpoint)
        }
        OperationalState.AWAITING_DECISION -> NextAction(R.string.next_decision)
        OperationalState.PAUSED -> if (lowBattery) NextAction(R.string.next_low_battery) else NextAction(R.string.next_paused)
        OperationalState.STOPPED -> NextAction(R.string.next_stopped)
        OperationalState.RETURNING ->
            if (lowBattery) NextAction(R.string.next_returning_battery) else NextAction(R.string.next_returning)
    }
}
