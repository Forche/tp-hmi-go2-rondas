package ar.edu.uade.go2rondas.presentation.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.domain.model.CheckpointResult
import ar.edu.uade.go2rondas.domain.model.CommandStatus
import ar.edu.uade.go2rondas.domain.model.HistoryEvent
import ar.edu.uade.go2rondas.domain.model.MissionDetail
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.titleRes

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
