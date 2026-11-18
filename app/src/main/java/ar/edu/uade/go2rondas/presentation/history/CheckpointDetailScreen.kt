package ar.edu.uade.go2rondas.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import ar.edu.uade.go2rondas.core.theme.statusColors
import ar.edu.uade.go2rondas.domain.model.CheckpointStatus
import ar.edu.uade.go2rondas.presentation.common.checkpointLabel
import ar.edu.uade.go2rondas.presentation.common.components.ErrorView
import ar.edu.uade.go2rondas.presentation.common.components.LoadingView
import ar.edu.uade.go2rondas.presentation.common.components.InspectionPhoto
import ar.edu.uade.go2rondas.presentation.common.components.Pill
import ar.edu.uade.go2rondas.presentation.common.components.RobotTopBar
import ar.edu.uade.go2rondas.presentation.common.components.TelemetryCard
import ar.edu.uade.go2rondas.presentation.common.formatClock
import ar.edu.uade.go2rondas.presentation.common.humidityState
import ar.edu.uade.go2rondas.presentation.common.labelRes
import ar.edu.uade.go2rondas.presentation.common.nameRes
import ar.edu.uade.go2rondas.presentation.common.noiseState
import ar.edu.uade.go2rondas.presentation.common.rememberNow
import ar.edu.uade.go2rondas.presentation.common.temperatureLimit
import ar.edu.uade.go2rondas.presentation.common.temperatureState

/** Usa el mismo ViewModel que el resumen: el checkpoint llega como segundo argumento de la ruta. */
@Composable
fun CheckpointDetailScreen(
    onBack: () -> Unit,
    viewModel: MissionSummaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val photo by viewModel.checkpointPhoto.collectAsStateWithLifecycle()
    val now = rememberNow()
    val data = (state as? UiState.Success)?.data
    val checkpointId = viewModel.checkpointId
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RobotTopBar(
            title = data?.let { checkpointLabel(it.site, checkpointId) }.orEmpty(),
            snapshot = data?.snapshot,
            now = now,
            onBack = onBack
        )
        when (val current = state) {
            UiState.Idle, UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(current.message)
            is UiState.Success -> {
                val detail = current.data.detail
                val site = current.data.site
                val result = detail?.checkpoints?.lastOrNull { it.checkpointId == checkpointId }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InspectionPhoto(photo, result?.timestampMillis)
                    if (detail == null || result == null) {
                        Text(stringResource(R.string.checkpoint_not_inspected), style = MaterialTheme.typography.bodyMedium)
                        return@Column
                    }
                    val status = MaterialTheme.statusColors
                    val (container, content) = when (result.status) {
                        CheckpointStatus.OK -> status.okContainer to status.ok
                        CheckpointStatus.ANOMALY -> status.errorContainer to status.error
                        CheckpointStatus.SKIPPED -> status.staleContainer to status.stale
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(stringResource(result.status.labelRes()), container, content)
                        Text(
                            stringResource(
                                R.string.checkpoint_detail_taken,
                                formatClock(result.timestampMillis),
                                stringResource(detail.record.type.nameRes())
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val reading = result.reading
                    if (reading == null) {
                        Text(stringResource(R.string.checkpoint_not_inspected), style = MaterialTheme.typography.bodyMedium)
                        return@Column
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TelemetryCard(
                            label = stringResource(R.string.tel_temperature),
                            value = stringResource(R.string.value_temperature, reading.temperatureC),
                            detail = stringResource(
                                R.string.value_temperature_detail,
                                reading.checkpointId,
                                site.temperatureLimit(reading.checkpointId) ?: 0.0
                            ),
                            state = temperatureState(reading, site, stale = false),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryCard(
                            label = stringResource(R.string.tel_humidity),
                            value = stringResource(R.string.value_humidity, reading.humidityPct),
                            detail = stringResource(
                                R.string.value_humidity_detail,
                                site.thresholds.humidityLow.warning,
                                site.thresholds.humidityHigh.warning
                            ),
                            state = humidityState(reading, site.thresholds, stale = false),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TelemetryCard(
                            label = stringResource(R.string.tel_noise),
                            value = stringResource(R.string.value_noise, reading.noiseDba),
                            detail = stringResource(R.string.value_noise_detail, reading.noiseBaselineDba),
                            state = noiseState(reading, site.thresholds, stale = false),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
