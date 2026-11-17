package ar.edu.uade.go2rondas.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import ar.edu.uade.go2rondas.domain.model.CheckpointStatus
import ar.edu.uade.go2rondas.domain.model.MissionDetail
import ar.edu.uade.go2rondas.domain.model.SiteConfig
import ar.edu.uade.go2rondas.presentation.common.components.CheckpointRow
import ar.edu.uade.go2rondas.presentation.common.components.CheckpointRowStatus
import ar.edu.uade.go2rondas.presentation.common.components.ErrorView
import ar.edu.uade.go2rondas.presentation.common.components.LoadingView
import ar.edu.uade.go2rondas.presentation.common.components.Pill
import ar.edu.uade.go2rondas.presentation.common.components.RobotTopBar
import ar.edu.uade.go2rondas.presentation.common.components.missionSummaryText
import ar.edu.uade.go2rondas.presentation.common.components.pillColors
import ar.edu.uade.go2rondas.presentation.common.formatClock
import ar.edu.uade.go2rondas.presentation.common.formatDateTime
import ar.edu.uade.go2rondas.presentation.common.formatElapsed
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.nameRes
import ar.edu.uade.go2rondas.presentation.common.readingDetail
import ar.edu.uade.go2rondas.presentation.common.rememberNow

@Composable
fun MissionSummaryScreen(
    onBack: () -> Unit,
    onOpenCheckpoint: (missionId: String, checkpointId: String) -> Unit,
    viewModel: MissionSummaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now = rememberNow()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RobotTopBar(
            title = stringResource(R.string.summary_title),
            snapshot = (state as? UiState.Success)?.data?.snapshot,
            now = now,
            onBack = onBack
        )
        when (val current = state) {
            UiState.Idle, UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(current.message)
            is UiState.Success -> {
                val detail = current.data.detail
                if (detail == null) ErrorView(stringResource(R.string.summary_not_found))
                else SummaryBody(detail, current.data.site, onOpenCheckpoint)
            }
        }
    }
}

@Composable
private fun SummaryBody(
    detail: MissionDetail,
    site: SiteConfig,
    onOpenCheckpoint: (String, String) -> Unit
) {
    val record = detail.record
    val results = detail.checkpoints.associateBy { it.checkpointId }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(record.type.nameRes()), style = MaterialTheme.typography.titleMedium)
                val (container, content) = record.result.pillColors()
                Pill(stringResource(record.result.labelRes()), container, content)
            }
            val ended = record.endedAtMillis
            Text(
                text = if (ended != null) {
                    stringResource(
                        R.string.summary_time_range,
                        formatDateTime(record.startedAtMillis),
                        formatClock(ended),
                        formatElapsed((ended - record.startedAtMillis) / 1_000)
                    )
                } else {
                    stringResource(R.string.summary_in_progress, formatDateTime(record.startedAtMillis))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(missionSummaryText(record, includeDate = false), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            record.nextStepRes()?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        }

        SectionTitle(stringResource(R.string.summary_checkpoints))
        Column {
            site.checkpointsOf(detail).forEach { id ->
                val checkpoint = site.checkpoint(id) ?: return@forEach
                val result = results[id]
                val status = when (result?.status) {
                    CheckpointStatus.OK -> CheckpointRowStatus.OK
                    CheckpointStatus.ANOMALY -> CheckpointRowStatus.ANOMALY
                    CheckpointStatus.SKIPPED -> CheckpointRowStatus.SKIPPED
                    null -> CheckpointRowStatus.PENDING
                }
                CheckpointRow(
                    number = checkpoint.number,
                    name = checkpoint.name,
                    detail = result?.reading?.let { readingDetail(it) } ?: stringResource(R.string.checkpoint_skipped_detail),
                    status = status,
                    onClick = result?.let { { onOpenCheckpoint(record.id, id) } }
                )
            }
        }

        SectionTitle(stringResource(R.string.summary_events))
        if (detail.events.isEmpty()) {
            Text(stringResource(R.string.summary_no_events), style = MaterialTheme.typography.bodySmall)
        }
        detail.events.forEach { event ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    formatClock(event.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(eventText(event), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
