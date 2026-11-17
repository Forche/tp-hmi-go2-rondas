package ar.edu.uade.go2rondas.presentation.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.domain.model.Alert
import ar.edu.uade.go2rondas.domain.model.AlertCode
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.Command
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.IncidentDecision
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.presentation.common.CommandFeedbackEffect
import ar.edu.uade.go2rondas.presentation.common.LocalSnackbarHostState
import ar.edu.uade.go2rondas.presentation.common.checkpointLabel
import ar.edu.uade.go2rondas.presentation.common.components.AlertCard
import ar.edu.uade.go2rondas.presentation.common.components.ButtonKind
import ar.edu.uade.go2rondas.presentation.common.components.CommandButton
import ar.edu.uade.go2rondas.presentation.common.components.CommandStatusPill
import ar.edu.uade.go2rondas.presentation.common.components.ErrorView
import ar.edu.uade.go2rondas.presentation.common.components.LoadingView
import ar.edu.uade.go2rondas.presentation.common.components.PhotoPlaceholder
import ar.edu.uade.go2rondas.presentation.common.components.RobotTopBar
import ar.edu.uade.go2rondas.presentation.common.components.TelemetryCard
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.rememberMessageShower
import ar.edu.uade.go2rondas.presentation.common.rememberNow
import ar.edu.uade.go2rondas.presentation.common.temperatureLimit
import ar.edu.uade.go2rondas.presentation.common.temperatureState

data class AlertDetailActions(
    val onAcknowledge: () -> Unit,
    val onDecide: (IncidentDecision) -> Unit,
    val onReturnToBase: () -> Unit,
    val onEscalate: () -> Unit
)

@Composable
fun AlertDetailScreen(
    onBack: () -> Unit,
    onBackToPanel: () -> Unit,
    viewModel: AlertDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AlertDetailUiEffect.BackToPanel -> onBackToPanel()
                AlertDetailUiEffect.Escalated -> snackbar.showSnackbar(context.getString(R.string.alert_escalated))
            }
        }
    }
    CommandFeedbackEffect(viewModel.feedback)
    val now = rememberNow()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RobotTopBar(
            title = stringResource(R.string.alert_title),
            snapshot = (state as? UiState.Success)?.data?.snapshot,
            now = now,
            onBack = onBack
        )
        when (val current = state) {
            UiState.Idle, UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(current.message)
            is UiState.Success -> AlertDetailBody(
                data = current.data,
                actions = AlertDetailActions(
                    onAcknowledge = viewModel::onAcknowledge,
                    onDecide = viewModel::onDecide,
                    onReturnToBase = viewModel::onReturnToBase,
                    onEscalate = viewModel::onEscalate
                )
            )
        }
    }
}

@Composable
private fun AlertDetailBody(data: AlertDetailData, actions: AlertDetailActions) {
    val alert = data.alert
    if (alert == null) {
        ErrorView(stringResource(R.string.alert_not_found))
        return
    }
    val snapshot = data.snapshot
    val availability = snapshot.availability
    val showMessage = rememberMessageShower()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AlertCard(alert, checkpointLabel(data.site, alert.checkpointId))
        if (alert.checkpointId != null) PhotoPlaceholder(stringResource(R.string.alert_photo))
        CurrentReading(data, alert)
        if (alert.requiresDecision) {
            Text(stringResource(R.string.alert_waiting_decision), style = MaterialTheme.typography.bodyMedium)
        }
        when {
            !alert.acknowledged -> {
                if (alert.requiresDecision) {
                    Text(
                        stringResource(R.string.alert_acknowledge_first),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CommandButton(
                    text = stringResource(R.string.command_acknowledge),
                    kind = ButtonKind.PRIMARY,
                    enabled = availability.isEnabled(CommandType.ACKNOWLEDGE_ALERT),
                    onClick = actions.onAcknowledge,
                    onDisabledClick = {
                        availability.reason(CommandType.ACKNOWLEDGE_ALERT)?.let { showMessage(it.labelRes()) }
                    }
                )
            }
            alert.requiresDecision -> {
                Text(stringResource(R.string.alert_how_continue), style = MaterialTheme.typography.titleMedium)
                val lastDecision = snapshot.lastCommand?.takeIf { it.command is Command.DecideIncident }
                lastDecision?.let { record ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CommandStatusPill(record)
                        record.rejectionReason?.let {
                            Text(
                                stringResource(R.string.rejection_reason, stringResource(it.labelRes())),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                alert.decisionOptions.forEachIndexed { index, decision ->
                    CommandButton(
                        text = decisionLabel(decision, alert),
                        kind = if (index == 0) ButtonKind.PRIMARY else ButtonKind.SECONDARY,
                        enabled = availability.isEnabled(CommandType.DECIDE_INCIDENT),
                        onClick = { actions.onDecide(decision) },
                        onDisabledClick = {
                            availability.reason(CommandType.DECIDE_INCIDENT)?.let { showMessage(it.labelRes()) }
                        }
                    )
                }
                CommandButton(
                    text = stringResource(R.string.command_return),
                    kind = ButtonKind.SECONDARY,
                    enabled = availability.isEnabled(CommandType.RETURN_TO_BASE),
                    onClick = actions.onReturnToBase,
                    onDisabledClick = {
                        availability.reason(CommandType.RETURN_TO_BASE)?.let { showMessage(it.labelRes()) }
                    }
                )
                if (IncidentDecision.SKIP in alert.decisionOptions) {
                    Text(
                        stringResource(R.string.alert_skip_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (alert.severity != AlertSeverity.INFO) {
            TextButton(onClick = actions.onEscalate) { Text(stringResource(R.string.alert_escalate)) }
        }
    }
}

/** Mientras el robot vigila el punto, la temperatura que manda se actualiza acá. */
@Composable
private fun CurrentReading(data: AlertDetailData, alert: Alert) {
    if (alert.code != AlertCode.TEMPERATURE_HIGH) return
    val reading = data.snapshot.telemetry?.environment?.takeIf { it.checkpointId == alert.checkpointId } ?: return
    TelemetryCard(
        label = stringResource(R.string.alert_current_reading, reading.checkpointId),
        value = stringResource(R.string.value_temperature, reading.temperatureC),
        detail = stringResource(
            R.string.value_temperature_detail,
            reading.checkpointId,
            data.site.temperatureLimit(reading.checkpointId) ?: 0.0
        ),
        state = temperatureState(reading, data.site, data.snapshot.connection.state == LinkState.DISCONNECTED)
    )
}

@Composable
private fun decisionLabel(decision: IncidentDecision, alert: Alert): String = when (decision) {
    IncidentDecision.CONTINUE -> stringResource(R.string.decision_continue)
    IncidentDecision.SKIP -> stringResource(R.string.decision_skip, alert.checkpointId.orEmpty())
    IncidentDecision.RETRY -> stringResource(R.string.decision_retry)
    IncidentDecision.WATCH -> stringResource(R.string.decision_watch)
}
