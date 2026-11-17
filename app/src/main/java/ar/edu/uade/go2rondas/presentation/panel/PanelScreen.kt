package ar.edu.uade.go2rondas.presentation.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.domain.model.RobotSnapshot
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.presentation.common.CommandFeedbackEffect
import ar.edu.uade.go2rondas.presentation.common.batteryDetail
import ar.edu.uade.go2rondas.presentation.common.batteryState
import ar.edu.uade.go2rondas.presentation.common.checkpointStatuses
import ar.edu.uade.go2rondas.presentation.common.components.ButtonKind
import ar.edu.uade.go2rondas.presentation.common.components.CommandButton
import ar.edu.uade.go2rondas.presentation.common.components.CommandStatusPill
import ar.edu.uade.go2rondas.presentation.common.components.ErrorView
import ar.edu.uade.go2rondas.presentation.common.components.LoadingView
import ar.edu.uade.go2rondas.presentation.common.components.RobotTopBar
import ar.edu.uade.go2rondas.presentation.common.components.SiteMap
import ar.edu.uade.go2rondas.presentation.common.components.StatusChip
import ar.edu.uade.go2rondas.presentation.common.components.TelemetryCard
import ar.edu.uade.go2rondas.presentation.common.formatElapsed
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.linkState
import ar.edu.uade.go2rondas.presentation.common.rememberMessageShower
import ar.edu.uade.go2rondas.presentation.common.rememberNow
import ar.edu.uade.go2rondas.presentation.common.temperatureLimit
import ar.edu.uade.go2rondas.presentation.common.temperatureState

@Composable
fun PanelScreen(
    onOpenTelemetry: () -> Unit,
    onOpenMissions: () -> Unit,
    viewModel: PanelViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CommandFeedbackEffect(viewModel.feedback)
    PanelContent(
        state = state,
        now = rememberNow(),
        actions = PanelActions(
            onOpenTelemetry = onOpenTelemetry,
            onOpenMissions = onOpenMissions,
            onOpenControls = viewModel::onOpenControls,
            onCloseControls = viewModel::onCloseControls,
            onPause = viewModel::onPause,
            onResume = viewModel::onResume,
            onReturnToBase = viewModel::onReturnToBase,
            onOperationalStop = viewModel::onOperationalStop,
            onRequestCancel = viewModel::onRequestCancel,
            onConfirmCancel = viewModel::onConfirmCancel,
            onDismissCancel = viewModel::onDismissCancel
        )
    )
}

/** Callbacks de la pantalla agrupados, para no pasar una lista larga de parámetros sueltos. */
data class PanelActions(
    val onOpenTelemetry: () -> Unit,
    val onOpenMissions: () -> Unit,
    val onOpenControls: () -> Unit,
    val onCloseControls: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onReturnToBase: () -> Unit,
    val onOperationalStop: () -> Unit,
    val onRequestCancel: () -> Unit,
    val onConfirmCancel: () -> Unit,
    val onDismissCancel: () -> Unit
)

@Composable
fun PanelContent(
    state: UiState<PanelData>,
    now: Long,
    actions: PanelActions,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RobotTopBar(
            title = stringResource(R.string.panel_title),
            snapshot = (state as? UiState.Success)?.data?.snapshot,
            now = now
        )
        when (state) {
            UiState.Idle, UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(state.message)
            is UiState.Success -> {
                PanelBody(state.data, now, actions)
                if (state.data.dialogs.controlsOpen) ControlSheet(state.data.snapshot, actions)
                if (state.data.dialogs.confirmingCancel) CancelMissionDialog(actions)
            }
        }
    }
}

@Composable
private fun PanelBody(data: PanelData, now: Long, actions: PanelActions) {
    val snapshot = data.snapshot
    val telemetry = snapshot.telemetry
    val stale = snapshot.connection.state == LinkState.DISCONNECTED
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusRow(snapshot, now)
        LastCommand(snapshot)
        MissionProgress(snapshot, data.site)
        SiteMap(
            site = data.site,
            checkpointStatus = checkpointStatuses(data.missionResults, telemetry?.mission),
            robotPosition = telemetry?.location?.position,
            robotStale = stale
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val cardModifier = Modifier.weight(1f)
            if (telemetry != null) {
                TelemetryCard(
                    label = stringResource(R.string.tel_battery),
                    value = stringResource(R.string.battery_percent, telemetry.battery.percent),
                    detail = batteryDetail(telemetry.battery),
                    state = batteryState(telemetry.battery.percent, data.site.thresholds, stale),
                    modifier = cardModifier
                )
            }
            val reading = telemetry?.environment
            if (reading != null) {
                TelemetryCard(
                    label = stringResource(R.string.tel_temperature),
                    value = stringResource(R.string.value_temperature, reading.temperatureC),
                    detail = stringResource(
                        R.string.value_temperature_detail,
                        reading.checkpointId,
                        data.site.temperatureLimit(reading.checkpointId) ?: 0.0
                    ),
                    state = temperatureState(reading, data.site, stale),
                    modifier = cardModifier
                )
            } else if (telemetry != null) {
                TelemetryCard(
                    label = stringResource(R.string.tel_connectivity),
                    value = snapshot.connection.latencyMs?.let { stringResource(R.string.value_latency, it) }
                        ?: stringResource(R.string.value_dash),
                    detail = stringResource(R.string.value_rssi, telemetry.link.rssiDbm),
                    state = linkState(snapshot.connection.state),
                    modifier = cardModifier
                )
            }
        }
        TextButton(onClick = actions.onOpenTelemetry) {
            Text(stringResource(R.string.panel_see_telemetry) + "  ›")
        }
        PanelButtons(snapshot, actions)
    }
}

/** Acciones principales según el estado; el resto está en la hoja de controles. */
@Composable
private fun PanelButtons(snapshot: RobotSnapshot, actions: PanelActions) {
    val state = snapshot.telemetry?.state ?: return
    val availability = snapshot.availability
    val showMessage = rememberMessageShower()
    if (state == OperationalState.AT_BASE) {
        CommandButton(
            text = stringResource(R.string.panel_choose_mission),
            kind = ButtonKind.PRIMARY,
            enabled = true,
            onClick = actions.onOpenMissions
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val pauseOrResume = if (state == OperationalState.PAUSED || state == OperationalState.STOPPED) {
            CommandType.RESUME to actions.onResume
        } else {
            CommandType.PAUSE to actions.onPause
        }
        val (type, onClick) = pauseOrResume
        CommandButton(
            text = stringResource(type.labelRes()),
            kind = ButtonKind.SECONDARY,
            enabled = availability.isEnabled(type),
            onClick = onClick,
            onDisabledClick = { availability.reason(type)?.let { showMessage(it.labelRes()) } },
            modifier = Modifier.weight(1f)
        )
        CommandButton(
            text = stringResource(R.string.panel_controls),
            kind = ButtonKind.PRIMARY,
            enabled = true,
            onClick = actions.onOpenControls,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LastCommand(snapshot: RobotSnapshot) {
    val record = snapshot.lastCommand ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CommandStatusPill(record)
        record.rejectionReason?.let { reason ->
            Text(
                stringResource(R.string.rejection_reason, stringResource(reason.labelRes())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusRow(snapshot: RobotSnapshot, now: Long) {
    val connected = snapshot.connection.state != LinkState.DISCONNECTED
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusChip(snapshot.telemetry?.state, connected && snapshot.telemetry != null)
        snapshot.connection.lastUpdateMillis?.let { last ->
            val seconds = ((now - last) / 1_000).coerceAtLeast(0).toInt()
            Text(
                stringResource(if (connected) R.string.updated_seconds_ago else R.string.last_data_seconds_ago, seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MissionProgress(snapshot: RobotSnapshot, site: SiteConfig) {
    val telemetry = snapshot.telemetry
    val mission = telemetry?.mission
    if (mission == null) {
        val state = telemetry?.state
        Text(
            text = if (state != null && state != OperationalState.AT_BASE) stringResource(state.labelRes())
            else stringResource(R.string.panel_no_mission),
            style = MaterialTheme.typography.labelLarge
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val title = if (telemetry.state == OperationalState.RETURNING) {
                stringResource(R.string.panel_returning)
            } else {
                stringResource(
                    R.string.panel_progress,
                    mission.currentPosition,
                    mission.total,
                    mission.currentCheckpointId?.let { site.checkpoint(it)?.name }.orEmpty()
                )
            }
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                formatElapsed(mission.elapsedSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { mission.completed / mission.total.toFloat() },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
