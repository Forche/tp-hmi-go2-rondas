package ar.edu.uade.go2rondas.presentation.history

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.domain.model.CheckpointResult
import ar.edu.uade.go2rondas.domain.model.CommandStatus
import ar.edu.uade.go2rondas.domain.model.HistoryEvent
import ar.edu.uade.go2rondas.domain.model.MissionDetail
import ar.edu.uade.go2rondas.domain.model.MissionRecord
import ar.edu.uade.go2rondas.domain.model.MissionResult
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.titleRes

/** Cierre del resumen: qué conviene hacer después de la ronda. Null mientras sigue en curso. */
@StringRes
fun MissionRecord.nextStepRes(): Int? = when (result) {
    null -> null
    MissionResult.COMPLETED -> when {
        anomalies > 0 -> R.string.summary_next_anomalies
        skipped > 0 -> R.string.summary_next_skipped
        else -> R.string.summary_next_ok
    }
    MissionResult.INTERRUPTED_LOW_BATTERY -> R.string.summary_next_battery
    MissionResult.CANCELLED -> R.string.summary_next_cancelled
    MissionResult.INTERRUPTED -> R.string.summary_next_interrupted
    MissionResult.ABORTED -> R.string.summary_next_aborted
}

/** Checkpoints que tenía que recorrer la ronda, en orden. La inspección puntual sale de sus resultados. */
fun SiteConfig.checkpointsOf(detail: MissionDetail): List<String> = when (detail.record.type) {
    MissionType.SPOT_CHECK -> detail.checkpoints.map(CheckpointResult::checkpointId).distinct()
    else -> missions.firstOrNull { it.type == detail.record.type }?.checkpointIds.orEmpty()
}

@Composable
fun eventText(event: HistoryEvent): String = when (event) {
    is HistoryEvent.AlertRaised -> {
        val title = stringResource(event.code.titleRes())
        val severity = stringResource(event.severity.labelRes())
        event.checkpointId?.let { stringResource(R.string.event_alert_at, title, severity, it) }
            ?: stringResource(R.string.event_alert, title, severity)
    }
    is HistoryEvent.CommandSent -> {
        val command = stringResource(event.type.labelRes())
        val reason = event.rejectionReason
        if (event.status == CommandStatus.REJECTED && reason != null) {
            stringResource(R.string.event_command_rejected, command, stringResource(reason.labelRes()))
        } else {
            stringResource(R.string.event_command, command, stringResource(event.status.labelRes()))
        }
    }
    is HistoryEvent.ConnectionLost -> stringResource(R.string.event_connection_lost)
    is HistoryEvent.ConnectionRestored -> stringResource(R.string.event_connection_restored, event.downtimeSeconds.toInt())
    is HistoryEvent.IncidentEscalated -> stringResource(R.string.event_escalated, stringResource(event.code.titleRes()))
}
