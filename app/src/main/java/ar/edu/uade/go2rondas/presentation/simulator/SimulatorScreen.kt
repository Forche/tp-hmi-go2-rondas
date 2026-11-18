package ar.edu.uade.go2rondas.presentation.simulator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.uade.go2rondas.R
import ar.edu.uade.go2rondas.core.model.UiState
import ar.edu.uade.go2rondas.domain.model.SimulationScenario
import ar.edu.uade.go2rondas.domain.model.SiteLoadResult
import ar.edu.uade.go2rondas.presentation.common.LocalSnackbarHostState
import ar.edu.uade.go2rondas.presentation.common.components.ErrorView
import ar.edu.uade.go2rondas.presentation.common.components.LoadingView
import ar.edu.uade.go2rondas.presentation.common.components.RobotTopBar
import ar.edu.uade.go2rondas.presentation.common.rememberNow

@StringRes
private fun SimulationScenario.titleRes(): Int = when (this) {
    SimulationScenario.LOW_BATTERY -> R.string.scenario_low_battery
    SimulationScenario.CONNECTION_LOSS -> R.string.scenario_connection_loss
    SimulationScenario.OBSTACLE -> R.string.scenario_obstacle
    SimulationScenario.HIGH_TEMPERATURE -> R.string.scenario_high_temperature
    SimulationScenario.WATER_LEAK -> R.string.scenario_water
    SimulationScenario.SMOKE -> R.string.scenario_smoke
    SimulationScenario.HUMIDITY_ANOMALY -> R.string.scenario_humidity
    SimulationScenario.NOISE_ANOMALY -> R.string.scenario_noise
    SimulationScenario.REJECT_NEXT_COMMAND -> R.string.scenario_reject
    SimulationScenario.IGNORE_NEXT_COMMAND -> R.string.scenario_ignore
}

@StringRes
private fun SimulationScenario.detailRes(): Int = when (this) {
    SimulationScenario.LOW_BATTERY -> R.string.scenario_low_battery_detail
    SimulationScenario.CONNECTION_LOSS -> R.string.scenario_connection_loss_detail
    SimulationScenario.OBSTACLE -> R.string.scenario_obstacle_detail
    SimulationScenario.HIGH_TEMPERATURE -> R.string.scenario_high_temperature_detail
    SimulationScenario.WATER_LEAK -> R.string.scenario_water_detail
    SimulationScenario.SMOKE -> R.string.scenario_smoke_detail
    SimulationScenario.HUMIDITY_ANOMALY -> R.string.scenario_humidity_detail
    SimulationScenario.NOISE_ANOMALY -> R.string.scenario_noise_detail
    SimulationScenario.REJECT_NEXT_COMMAND -> R.string.scenario_reject_detail
    SimulationScenario.IGNORE_NEXT_COMMAND -> R.string.scenario_ignore_detail
}

private val TimeScales = listOf(1, 4, 10)

/** Tipos que ofrece el selector de documentos: según de dónde venga, un .json puede llegar con cualquiera. */
private val SITE_FILE_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")

@Composable
fun SimulatorScreen(onBack: () -> Unit, viewModel: SimulatorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SimulatorUiEffect.Triggered -> snackbar.showSnackbar(
                    context.getString(R.string.simulator_triggered, context.getString(effect.scenario.titleRes()))
                )
                is SimulatorUiEffect.SiteLoaded -> snackbar.showSnackbar(
                    when (val result = effect.result) {
                        is SiteLoadResult.Loaded -> context.getString(R.string.site_loaded, result.siteName)
                        is SiteLoadResult.Invalid -> context.getString(R.string.site_invalid, result.reason)
                        SiteLoadResult.RobotBusy -> context.getString(R.string.site_robot_busy)
                    }
                )
            }
        }
    }
    // El archivo se lee y se copia al elegirlo: no hace falta conservar el permiso sobre el documento.
    val pickSiteFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onSiteFileChosen(it.toString()) }
    }
    val now = rememberNow()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RobotTopBar(
            title = stringResource(R.string.simulator_title),
            snapshot = (state as? UiState.Success)?.data?.snapshot,
            now = now,
            onBack = onBack
        )
        when (val current = state) {
            UiState.Idle, UiState.Loading -> LoadingView()
            is UiState.Error -> ErrorView(current.message)
            is UiState.Success -> SimulatorBody(
                data = current.data,
                onTimeScaleSelected = viewModel::onTimeScaleSelected,
                onTrigger = viewModel::onTrigger,
                onRandomChanged = viewModel::onRandomSituationsChanged,
                onLoadSite = { pickSiteFile.launch(SITE_FILE_TYPES) },
                onResetSite = viewModel::onResetSite
            )
        }
    }
}

@Composable
private fun SimulatorBody(
    data: SimulatorData,
    onTimeScaleSelected: (Int) -> Unit,
    onTrigger: (SimulationScenario) -> Unit,
    onRandomChanged: (Boolean) -> Unit,
    onLoadSite: () -> Unit,
    onResetSite: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!data.available) {
            Text(stringResource(R.string.simulator_unavailable), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Text(stringResource(R.string.simulator_intro), style = MaterialTheme.typography.bodyMedium)
        Label(stringResource(R.string.simulator_speed))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeScales.forEach { scale ->
                FilterChip(
                    selected = data.timeScale == scale,
                    onClick = { onTimeScaleSelected(scale) },
                    label = { Text(stringResource(R.string.simulator_speed_value, scale)) }
                )
            }
        }
        SiteCard(data.siteName, onLoadSite, onResetSite)
        Label(stringResource(R.string.simulator_scenarios))
        RandomCard(data.randomSituations, onRandomChanged)
        SimulationScenario.entries.forEach { scenario ->
            ScenarioCard(scenario, onTrigger = { onTrigger(scenario) })
        }
    }
}

/** Sitio cargado en el robot simulado: se cambia cargándole otro archivo, sin recompilar la app. */
@Composable
private fun SiteCard(siteName: String, onLoadSite: () -> Unit, onResetSite: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.site_current),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(siteName, style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.site_load_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLoadSite) { Text(stringResource(R.string.site_load)) }
            TextButton(onClick = onResetSite) { Text(stringResource(R.string.site_reset)) }
        }
    }
}

/** Modo aleatorio: el simulador provoca escenarios al azar durante la ronda. */
@Composable
private fun RandomCard(enabled: Boolean, onChanged: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.simulator_random), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.simulator_random_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = onChanged)
    }
}

@Composable
private fun ScenarioCard(scenario: SimulationScenario, onTrigger: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(scenario.titleRes()), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(scenario.detailRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onTrigger) { Text(stringResource(R.string.simulator_trigger)) }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
