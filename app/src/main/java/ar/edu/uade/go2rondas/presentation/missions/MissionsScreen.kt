package ar.edu.uade.go2rondas.presentation.missions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.core.theme.statusColors
import ar.edu.uade.go2rondas.domain.model.AlertSeverity
import ar.edu.uade.go2rondas.domain.model.CommandStatus
import ar.edu.uade.go2rondas.domain.model.CommandType
import ar.edu.uade.go2rondas.domain.model.LinkState
import ar.edu.uade.go2rondas.domain.model.MissionType
import ar.edu.uade.go2rondas.domain.model.OperationalState
import ar.edu.uade.go2rondas.presentation.common.CommandFeedbackEffect
import ar.edu.uade.go2rondas.presentation.common.components.ButtonKind
import ar.edu.uade.go2rondas.presentation.common.components.CheckpointRowStatus
import ar.edu.uade.go2rondas.presentation.common.components.CommandButton
import ar.edu.uade.go2rondas.presentation.common.components.ErrorView
import ar.edu.uade.go2rondas.presentation.common.components.LoadingView
import ar.edu.uade.go2rondas.presentation.common.components.RobotTopBar
import ar.edu.uade.go2rondas.presentation.common.components.SelectableCard
import ar.edu.uade.go2rondas.presentation.common.components.SiteMap
import ar.edu.uade.go2rondas.presentation.common.detailRes
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.nameRes
import ar.edu.uade.go2rondas.presentation.common.rememberMessageShower
import ar.edu.uade.go2rondas.presentation.common.rememberNow

@Composable
fun MissionsScreen(
    onMissionStarted: () -> Unit,
    viewModel: MissionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MissionsUiEffect.MissionStarted -> onMissionStarted()
            }
        }
    }
    CommandFeedbackEffect(viewModel.feedback)
    MissionsContent(
        state = state,
        now = rememberNow(),
        onMissionSelected = viewModel::onMissionSelected,
        onSpotCheckpointSelected = viewModel::onSpotCheckpointSelected,
        onContinue = viewModel::onContinue,
        onDismissConfirmation = viewModel::onDismissConfirmation,
        onStartMission = viewModel::onStartMission
    )
}

@Composable
fun MissionsContent(
    state: UiState<MissionsData>,
    now: Long,
    onMissionSelected: (MissionType) -> Unit,
    onSpotCheckpointSelected: (String) -> Unit,
    onContinue: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onStartMission: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RobotTopBar(
            title = stringResource(R.string.missions_title),
            snapshot = (state as? UiState.Success)?.data?.snapshot,
            now = now
        )
        when (state) {
            UiState.Idle, UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(state.message)
            is UiState.Success -> {
                MissionsBody(state.data, onMissionSelected, onSpotCheckpointSelected, onContinue)
                if (state.data.selection.confirming) {
                    ConfirmationSheet(state.data, onDismissConfirmation, onStartMission)
                }
            }
        }
    }
}

@Composable
private fun MissionsBody(
    data: MissionsData,
    onMissionSelected: (MissionType) -> Unit,
    onSpotCheckpointSelected: (String) -> Unit,
    onContinue: () -> Unit
) {
    val missionCheckpoints = data.checkpointIds.toSet()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.missions_choose), style = MaterialTheme.typography.titleMedium)
        // Sólo las misiones que trae el sitio cargado en el robot (el chequeo puntual está siempre).
        data.site.missions.map { it.type }.forEach { type ->
            SelectableCard(
                title = stringResource(type.nameRes()),
                detail = stringResource(type.detailRes()),
                selected = data.selection.type == type,
                onClick = { onMissionSelected(type) }
            )
        }
        if (data.selection.type == MissionType.SPOT_CHECK) {
            Text(
                stringResource(R.string.missions_spot_checkpoint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(0.dp)) {
                items(data.site.checkpoints, key = { it.id }) { checkpoint ->
                    FilterChip(
                        selected = data.selection.spotCheckpointId == checkpoint.id,
                        onClick = { onSpotCheckpointSelected(checkpoint.id) },
                        label = { Text(stringResource(R.string.checkpoint_label, checkpoint.id, checkpoint.name)) }
                    )
                }
            }
        }
        Text(
            stringResource(R.string.missions_route),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SiteMap(
            site = data.site,
            checkpointStatus = { id -> if (id in missionCheckpoints) CheckpointRowStatus.CURRENT else CheckpointRowStatus.PENDING },
            robotPosition = data.snapshot.telemetry?.location?.position,
            robotStale = data.snapshot.connection.state == LinkState.DISCONNECTED
        )
        CommandButton(
            text = stringResource(R.string.missions_continue),
            kind = ButtonKind.PRIMARY,
            enabled = true,
            onClick = onContinue
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmationSheet(data: MissionsData, onDismiss: () -> Unit, onStart: () -> Unit) {
    val snapshot = data.snapshot
    val telemetry = snapshot.telemetry
    val thresholds = data.site.thresholds
    val connection = snapshot.connection
    val showMessage = rememberMessageShower()
    val checks = listOf(
        stringResource(R.string.check_at_base) to (telemetry?.state == OperationalState.AT_BASE),
        (connection.latencyMs?.let { stringResource(R.string.check_connected_latency, it) }
            ?: stringResource(R.string.check_connected)) to (connection.state != LinkState.DISCONNECTED),
        stringResource(R.string.check_battery, telemetry?.battery?.percent ?: 0, thresholds.batteryMinToStartPct) to
            ((telemetry?.battery?.percent ?: 0) >= thresholds.batteryMinToStartPct),
        stringResource(R.string.check_no_critical) to
            snapshot.unacknowledgedAlerts.none { it.severity == AlertSeverity.CRITICAL },
        stringResource(R.string.check_no_pending) to (snapshot.lastCommand?.status != CommandStatus.PENDING)
    )
    val availability = snapshot.availability
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.confirm_title, stringResource(data.selection.type.nameRes())),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(R.string.confirm_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            checks.forEach { (label, ok) -> CheckRow(label, ok) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CommandButton(
                    text = stringResource(R.string.action_cancel),
                    kind = ButtonKind.SECONDARY,
                    enabled = true,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                CommandButton(
                    text = stringResource(R.string.command_start),
                    kind = ButtonKind.PRIMARY,
                    enabled = availability.isEnabled(CommandType.START_MISSION),
                    onClick = onStart,
                    onDisabledClick = { availability.reason(CommandType.START_MISSION)?.let { showMessage(it.labelRes()) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean) {
    val status = MaterialTheme.statusColors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painterResource(if (ok) R.drawable.ic_check_circle else R.drawable.ic_cancel_circle),
            contentDescription = null,
            tint = if (ok) status.ok else status.error,
            modifier = Modifier.size(20.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
